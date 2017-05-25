package br.ufpe.cin.tan.test

import br.ufpe.cin.tan.commit.change.gherkin.GherkinManager
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.commit.change.stepdef.ChangedStepdefFile
import br.ufpe.cin.tan.commit.change.unit.ChangedUnitTestFile
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.analysis.itask.ITest
import gherkin.ast.Background
import gherkin.ast.Scenario
import gherkin.ast.ScenarioOutline
import gherkin.ast.Step
import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.Util

/***
 * Provides the common logic for test code parsers and the base method to compute task interfaces by
 * Template Method pattern.
 */
@Slf4j
abstract class TestCodeAbstractParser {

    String repositoryPath
    String stepsFilePath

    List<StepRegex> regexList
    Set methods //keys: name, args, path
    List<String> projectFiles
    List<String> viewFiles

    protected Set notFoundViews
    protected Set compilationErrors
    Set matchStepErrors
    Set<AcceptanceTest> foundAcceptanceTests
    Set codeFromViewAnalysis
    int visitCallCounter

    GherkinManager gherkinManager

    /***
     * Initializes fields used to link step declaration and code.
     *
     * @param repositoryPath It could be a URL or a local path.
     */
    TestCodeAbstractParser(String repositoryPath, GherkinManager gherkinManager) {
        this.repositoryPath = repositoryPath
        this.gherkinManager = gherkinManager
        stepsFilePath = repositoryPath + File.separator + Util.STEPS_FILES_RELATIVE_PATH
        regexList = []
        methods = [] as Set
        projectFiles = []
        viewFiles = []
        notFoundViews = [] as Set
        compilationErrors = [] as Set
        matchStepErrors = [] as Set
        foundAcceptanceTests = []
        codeFromViewAnalysis = [] as Set
    }

    private organizeMatchStepErrors(Set removedSteps) {
        def result = [] as Set
        def errors = matchStepErrors - removedSteps
        def files = errors*.path.unique()
        files.each { file ->
            def index = file.indexOf(repositoryPath)
            def name = index >= 0 ? file.substring(index) - (repositoryPath + File.separator) : file
            def texts = (matchStepErrors.findAll{it.path == file}*.text)?.unique()?.sort()
            result += [path:name, text:texts, size:texts.size()]
        }
        result
    }

    private organizeCompilationErrors() {
        compilationErrors += gherkinManager.compilationErrors
        def result = [] as Set
        def files = compilationErrors*.path.unique()
        files.each { file ->
            def msgs = compilationErrors.findAll { it.path == file }*.msg
            result += [path: file, msgs: msgs.unique()]
        }
        result
    }

    /***
     * Matches step declaration and code
     */
    private List<AcceptanceTest> extractAcceptanceTest(List<ChangedGherkinFile> gherkinFiles) {
        List<AcceptanceTest> acceptanceTests = []
        gherkinFiles?.each { gherkinFile ->
            /* finds step code of background from a Gherkin file */
            List<StepCode> backgroundCode = []
            Background background = (Background) gherkinFile.feature.children.find{ it instanceof Background }
            if (background) {
                backgroundCode = findCodeForSteps(background.steps, gherkinFile.path)
            }

            /* finds step code of changed scenario definitions from a Gherkin file */
            gherkinFile?.changedScenarioDefinitions?.each { definition ->
                def test = configureAcceptanceTest(definition, gherkinFile.path) //groovy dynamic method dispatch
                if (test) {
                    if (!backgroundCode.empty) {
                        test.stepCodes = (test.stepCodes + backgroundCode).unique()
                    }
                    acceptanceTests += test
                }
            }
        }

        foundAcceptanceTests = acceptanceTests
        log.info "Number of implemented acceptance tests: ${acceptanceTests.size()}"
        acceptanceTests?.each { log.info it.toString() }
        acceptanceTests
    }

    def configureProperties() {
        projectFiles = Util.findFilesFromDirectoryByLanguage(repositoryPath)
        configureRegexList() // Updates regex list used to match step definition and step code
        configureMethodsList()
        def views = Util.findFilesFromDirectory(repositoryPath + File.separator + Util.VIEWS_FILES_RELATIVE_PATH)
        viewFiles = views.findAll { file -> Util.VALID_VIEW_FILES.any { file.endsWith(it) } }
        viewFiles += (views - viewFiles).findAll{
            it.count(".")==1 && (it.endsWith(ConstantData.ERB_EXTENSION) || it.endsWith(ConstantData.HAML_EXTENSION) || it.endsWith(".slim"))
        }
    }

    private configureRegexList() {
        regexList = []
        def files = Util.findFilesFromDirectoryByLanguage(stepsFilePath)
        files.each { regexList += doExtractStepsRegex(it) }
    }

    private configureMethodsList() {
        methods = []
        def filesForSearchMethods = []
        Util.VALID_FOLDERS.each { folder ->
            filesForSearchMethods += Util.findFilesFromDirectoryByLanguage(repositoryPath + File.separator + folder)
        }
        filesForSearchMethods.each { methods += doExtractMethodDefinitions(it) }
    }

    private List<FileToAnalyse> identifyMethodsPerFileToVisitByStepCalls(List<StepCall> stepCalls) {
        List<FileToAnalyse> files = []
        stepCalls?.unique { it.text }?.each { stepCall ->
            List<FileToAnalyse> stepCode = findStepCode(stepCall, true)
            if (stepCode && !stepCode.empty) files += stepCode
        }
        identifyMethodsPerFile(files)
    }

    List<FileToAnalyse> findStepCode(StepCall call, boolean extractArgs) {
        def calledSteps = []  //path, line, args
        List<FileToAnalyse> result = []

        /* find step declaration */
        def stepCodeMatch = regexList?.findAll { call.text ==~ it.value }
        if (stepCodeMatch && stepCodeMatch.size() > 0) { //step code was found
            def args = []
            if (extractArgs) args = extractArgsFromStepText(call.text, stepCodeMatch.get(0).value)

            if (stepCodeMatch.size() == 1) {
                stepCodeMatch = stepCodeMatch.get(0)
                calledSteps += [path: stepCodeMatch.path, line: stepCodeMatch.line, args: args]
            } else {
                log.warn "There are many implementations for step code (findStepCode(stepText,path,line)): ${call.text}; " +
                        "${call.path} (${call.line})"
                stepCodeMatch?.each {
                    log.warn it.toString()
                    calledSteps += [path: it.path, line: it.line, args: args]
                }
            }
        } else {
            log.warn "Step code was not found: ${call.text}; ${call.path} (${call.line})"
            def text = call.text.replaceAll(/".+"/,"\"\"").replaceAll(/'.+'/,"\'\'")
            matchStepErrors += [path: call.path, text: text]
        }

        /* organizes step declarations in files */
        def files = (calledSteps*.path)?.flatten()?.unique()
        files?.each { file ->
            def codes = calledSteps.findAll { it.path == file }
            if (codes) {
                def methodsToAnalyse = []
                codes.each { code ->
                    methodsToAnalyse += new MethodToAnalyse(line: code.line, args: code.args)
                }
                result += new FileToAnalyse(path: file, methods: methodsToAnalyse.unique())
            }
        }
        result
    }

    private static identifyMethodsPerFile(List<FileToAnalyse> filesToAnalyse) {
        List<FileToAnalyse> result = []
        def files = filesToAnalyse*.path?.unique()
        files?.each { file ->
            def entries = filesToAnalyse?.findAll { it.path == file }
            if (entries) {
                def methodsToAnalyse = entries*.methods.flatten()
                result += new FileToAnalyse(path: file, methods: methodsToAnalyse)
            }
        }
        result
    }

    private static updateStepFiles(List<FileToAnalyse> oldFiles, List<FileToAnalyse> newFiles) {
        List<FileToAnalyse> result = []
        newFiles?.each { newFile ->
            def visited = oldFiles?.findAll { it.path == newFile.path }
            if (!visited) result += newFile
            else {
                def visitedMethods = visited*.methods.flatten()
                def newMethods = newFile.methods - visitedMethods
                if (!newMethods.empty) result += new FileToAnalyse(path: newFile.path, methods: newMethods)
            }
        }
        result
    }

    /***
     * Organizes StepCode objects by codeParse attribute.
     *
     * @param stepCodes list of related stepCodes
     * @return a list of path (step code path) and lines (start line of step codes) pairs
     */
    static List<FileToAnalyse> identifyMethodsPerFileToVisit(List<StepCode> stepCodes) {
        List<FileToAnalyse> result = []
        def files = (stepCodes*.codePath)?.flatten()?.unique()
        files?.each { file ->
            def codes = stepCodes.findAll { it.codePath == file }
            List<MethodToAnalyse> methods = []
            codes?.each {
                methods += new MethodToAnalyse(line: it.line, args: it.args)
            }
            if (!methods.empty) result += new FileToAnalyse(path: file, methods: methods.unique())

        }
        result
    }

    static List<FileToAnalyse> collapseFilesToVisit(List<FileToAnalyse> files1, List<FileToAnalyse> files2) {
        List<FileToAnalyse> result = []
        def sum = files1 + files2
        def files = sum*.path.unique()
        files?.each { file ->
            def entries = sum.findAll { it.path == file }
            def methodsToAnalyse = entries*.methods.flatten().unique()
            result += new FileToAnalyse(path: file, methods: methodsToAnalyse)
        }
        result
    }

    /***
     * Identifies methods to visit from a list of method calls, considering the visit history.
     * The methods of interest are defined by test code.
     *
     * @param lastCalledMethods list of map objects identifying called methods by 'name', 'type' and 'file'.
     * @param allVisitedFiles A collection all visited files identified by 'path' and visited 'methods'.
     * @return a list of methods grouped by path.
     */
    static listFilesToVisit(lastCalledMethods, allVisitedFiles) {
        def validCalledMethods = lastCalledMethods.findAll { it.file != null }
        def methods = listTestMethodsToVisit(validCalledMethods)
        def filesToVisit = []
        methods.each { file ->
            def match = allVisitedFiles?.find { it.path == file.path }
            if (match != null) {
                filesToVisit += [path: file.path, methods: file.methods - match.methods]
            } else {
                filesToVisit += [path: file.path, methods: file.methods]
            }
        }
        return filesToVisit
    }

    /***
     * Identifies methods to visit from a list of method calls. The methods of interest are defined by test code.
     *
     * @param lastCalledMethods list of map objects identifying called methods by 'name', 'type' and 'file'.
     * @return a list of methods grouped by path.
     */
    static listTestMethodsToVisit(lastCalledMethods) {
        def testFiles = []
        def calledTestMethods = lastCalledMethods?.findAll { it.file != null && Util.isTestFile(it.file) }?.unique()
        calledTestMethods*.file.unique().each { path ->
            def methods = calledTestMethods.findAll { it.file == path }*.name
            testFiles += [path: path, methods: methods]
        }
        return testFiles
    }

    static updateVisitedFiles(List allVisitedFiles, List filesToVisit) {
        def allFiles = allVisitedFiles + filesToVisit
        def paths = (allFiles*.path)?.unique()
        def result = []
        paths?.each { path ->
            def methods = (allFiles?.findAll { it.path == path }*.methods)?.flatten()?.unique()
            if (methods != null && !methods.isEmpty()) result += [path: path, methods: methods]
        }
        return result
    }

    static List<String> extractArgsFromStepText(String text, String regex) {
        List<String> args = []
        def matcher = (text =~ /${regex}/)
        if (matcher) {
            def counter = matcher.groupCount()
            for (int i = 1; i <= counter; i++) {
                def arg = matcher[0][i]
                if (arg) args += arg
            }
        }
        args
    }

    /***
     * Finds step code of a scenario definition.
     * This method cannot be private. Otherwise, it throws groovy.lang.MissingMethodException.
     *
     * @param scenarioDefinition scenario definition of interest
     * @param scenarioDefinitionPath path of Gherkin file that contains the scenario definition
     * @return AcceptanceTest object that represents a match between scenario definition and implementation.
     */
    AcceptanceTest configureAcceptanceTest(Scenario scenarioDefinition, String scenarioDefinitionPath) {
        AcceptanceTest test = null
        List<StepCode> codes = findCodeForSteps(scenarioDefinition.steps, scenarioDefinitionPath)
        if (!codes?.empty) test = new AcceptanceTest(gherkinFilePath: scenarioDefinitionPath, scenarioDefinition: scenarioDefinition, stepCodes: codes)
        test
    }

    AcceptanceTest configureAcceptanceTest(Background background, String scenarioDefinitionPath) {
        null
    }

    AcceptanceTest configureAcceptanceTest(ScenarioOutline scenarioDefinition, String scenarioDefinitionPath) {
        AcceptanceTest test = null
        List<Step> stepsToAnalyse = []
        List<String> argName = scenarioDefinition.examples.tableHeader*.cells.flatten()*.value
        scenarioDefinition.steps.each { step ->
            if (!(step.text.contains("<") && step.text.contains(">"))) {
                stepsToAnalyse += step
            } else {
                def argsInLine = scenarioDefinition.examples.tableBody.get(0).cells.value
                argsInLine.each { argsLine ->
                    def transformedText = new String(step.text)
                    for (int i = 0; i < argName.size(); i++) {
                        def searchExpression = "<${argName.get(i)}>"
                        if (transformedText.contains(searchExpression)) {
                            transformedText = transformedText.replaceFirst(searchExpression, argsLine.get(i))
                        }
                    }
                    stepsToAnalyse += new Step(step.location, step.keyword, transformedText, step.argument)
                }
            }
        }

        List<StepCode> codes = findCodeForSteps(stepsToAnalyse, scenarioDefinitionPath)
        if (!codes?.empty) test = new AcceptanceTest(gherkinFilePath: scenarioDefinitionPath, scenarioDefinition: scenarioDefinition, stepCodes: codes)
        test
    }

    List<StepCode> findCodeForStep(step, String path, boolean extractArgs) {
        List<StepCode> code = []
        def stepCodeMatch = regexList?.findAll { step.text ==~ it.value }
        if (stepCodeMatch && stepCodeMatch.size() > 0) { //step code was found
            def args = []
            if (extractArgs) args = extractArgsFromStepText(step.text, stepCodeMatch.get(0).value)
            if (stepCodeMatch.size() == 1 as int) {
                stepCodeMatch = stepCodeMatch.get(0)
                code += new StepCode(step: step, codePath: stepCodeMatch.path, line: stepCodeMatch.line, args: args)
            } else {
                log.warn "There are many implementations for step code: ${step.text}; $path (${step.location.line})"
                stepCodeMatch?.each {
                    log.warn it.toString()
                    code += new StepCode(step: step, codePath: it.path, line: it.line, args: args)
                }
            }
        } else {
            log.warn "Step code was not found: ${step.text}; $path (${step.location.line})"
            def text = step.text.replaceAll(/".+"/,"\"\"").replaceAll(/'.+'/,"\'\'")
            matchStepErrors += [path: path, text: text]
        }
        code
    }

    List<StepCode> findCodeForSteps(List steps, String path) {
        List<StepCode> codes = []
        steps?.each { step ->
            def code = findCodeForStep(step, path, true)
            if (code && !code.empty) codes += code
        }
        codes
    }

    def findCodeForStepIndependentFromAcceptanceTest(StepDefinition step) {
        def result = []
        def stepCodeMatch = regexList?.findAll { step.regex == it.value }
        if (stepCodeMatch && stepCodeMatch.size() > 0) { //step code was found
            if (stepCodeMatch.size() == 1 as int) {
                stepCodeMatch = stepCodeMatch.get(0)
                result += [path: stepCodeMatch.path, line: stepCodeMatch.line]
            } else {
                log.warn "There are many implementations for step code: ${step.value}; ${step.path} (${step.line})"
                stepCodeMatch?.each {
                    log.warn it.toString()
                    result += [path: it.path, line: it.line]
                }
            }
        } else {
            log.warn "Step code was not found: ${step.value}; ${step.path} (${step.line})"
            def text = step.value.replaceAll(/".+"/,"\"\"").replaceAll(/'.+'/,"\'\'")
            matchStepErrors += [path: step.path, text: text]
        }
        result
    }

    def findCodeForStepsIndependentFromAcceptanceTest(List<ChangedStepdefFile> stepFiles) {
        List<FileToAnalyse> result = []
        stepFiles?.each { file ->
            def partialResult = []
            file.changedStepDefinitions?.each { step ->
                def code = findCodeForStepIndependentFromAcceptanceTest(step) //path, line
                if (code && !code.empty) partialResult += code
            }
            if (!partialResult.empty) {
                def resumedResult = [path: partialResult?.first()?.path, lines: partialResult*.line?.unique()?.sort()]
                def methodsToAnalyse = []
                resumedResult.lines.each { methodsToAnalyse += new MethodToAnalyse(line: it, args: []) }
                result += new FileToAnalyse(path: resumedResult.path, methods: methodsToAnalyse)
            }
        }
        result
    }

    /***
     * Template method to compute test-based task interface for done tasks (evaluation study).
     *
     * @param gherkinFiles list of changed gherkin files
     * @return task interface
     */
    ITest computeInterfaceForDoneTask(List<ChangedGherkinFile> gherkinFiles, List<ChangedStepdefFile> stepFiles,
                                      Set removedSteps) {
        configureProperties()
        List<AcceptanceTest> acceptanceTests = extractAcceptanceTest(gherkinFiles)
        List<StepCode> stepCodes1 = acceptanceTests*.stepCodes?.flatten()?.unique()
        List<FileToAnalyse> files1 = identifyMethodsPerFileToVisit(stepCodes1)
        List<FileToAnalyse> files2 = findCodeForStepsIndependentFromAcceptanceTest(stepFiles)
        List<FileToAnalyse> filesToAnalyse = collapseFilesToVisit(files1, files2)
        computeInterface(filesToAnalyse, removedSteps)
    }

    /***
     * Template method to compute test-based task interface for new tasks.
     *
     * @param gherkinFiles list of changed gherkin files
     * @return task interface
     */
    ITest computeInterfaceForTodoTask(List<ChangedGherkinFile> gherkinFiles) {
        List<AcceptanceTest> acceptanceTests = extractAcceptanceTest(gherkinFiles)
        List<StepCode> stepCodes = acceptanceTests*.stepCodes?.flatten()?.unique()
        List<FileToAnalyse> filesToAnalyse = identifyMethodsPerFileToVisit(stepCodes)
        computeInterface(filesToAnalyse, null)
    }

    private ITest computeInterface(List<FileToAnalyse> filesToAnalyse, Set removedSteps) {
        def interfaces = []
        List<StepCall> calledSteps = [] //keys:text, path, line

        filesToAnalyse?.eachWithIndex { stepDefFile, index ->
            log.info stepDefFile.toString()

            /* first level: To identify method calls from step body. */
            TestCodeVisitor testCodeVisitor = parseStepBody(stepDefFile)

            /* second level: To visit methods until there is no more method calls of methods defined in test code. */
            def visitedFiles = []
            def filesToParse = listFilesToVisit(testCodeVisitor.taskInterface.methods, visitedFiles)

            while (!filesToParse.empty) {
                /* copies methods from task interface */
                def backupCalledMethods = testCodeVisitor.taskInterface.methods

                /* visits each file */
                filesToParse.each { f ->
                    visitFile(f, testCodeVisitor)
                }

                /* computes methods to visit based on visit history */
                visitedFiles = updateVisitedFiles(visitedFiles, filesToParse)
                def lastCalledMethods = testCodeVisitor.taskInterface.methods - backupCalledMethods
                filesToParse = listFilesToVisit(lastCalledMethods, visitedFiles)
            }

            /* updates called steps */
            calledSteps += testCodeVisitor.calledSteps

            /* searches for view files */
            if(!testCodeVisitor?.taskInterface?.calledPageMethods?.empty) {
                log.info "calledPageMethods:"
                testCodeVisitor?.taskInterface?.calledPageMethods?.each { log.info it.toString() }
                findAllPages(testCodeVisitor)
            }

            /* updates task interface */
            interfaces += testCodeVisitor.taskInterface

            visitCallCounter += testCodeVisitor.visitCallCounter
        }

        /* identifies more step definitions to analyse */
        log.info "calledSteps: ${calledSteps.size()}"
        calledSteps.each{ log.info it.toString() }

        List<FileToAnalyse> newStepsToAnalyse = identifyMethodsPerFileToVisitByStepCalls(calledSteps)
        newStepsToAnalyse = updateStepFiles(filesToAnalyse, newStepsToAnalyse)
        if (!newStepsToAnalyse.empty) interfaces += computeInterface(newStepsToAnalyse, removedSteps)

        /* collapses step code interfaces to define the interface for the whole task */
        def itest = ITest.collapseInterfaces(interfaces)
        itest.matchStepErrors = organizeMatchStepErrors(removedSteps)
        itest.compilationErrors = organizeCompilationErrors()
        itest.notFoundViews = notFoundViews.sort()
        itest.foundAcceptanceTests = foundAcceptanceTests
        itest.codeFromViewAnalysis = this.getCodeFromViewAnalysis()
        itest.visitCallCounter = visitCallCounter
        itest
    }

    /***
     * Updates a visitor's task interface by identifying related view files.
     *
     * @param visitor
     */
    abstract void findAllPages(TestCodeVisitor visitor)

    /***
     * Finds all regex expression in a source code file.
     *
     * @param path file path
     * @return list of StepRegex objects identifying the file and its regexs
     */
    abstract List<StepRegex> doExtractStepsRegex(String path)

    /***
     * Finds all step definition in a source code file.
     *
     * @param path file path
     * @param file content
     * @return list of StepDefinition objects
     */
    abstract List<StepDefinition> doExtractStepDefinitions(String path, String content)

    /***
     * Finds all methods declaration in source code file.
     * @param path file path
     */
    abstract Set doExtractMethodDefinitions(String path)

    /***
     * Visits a step body and method calls inside it. The result is stored as a field of the returned visitor.
     *
     * @param file List of map objects that identifies files by 'path' and 'lines'.
     * @return visitor to visit method bodies
     */
    abstract TestCodeVisitor parseStepBody(FileToAnalyse file) //keys: path, lines

    /***
     * Visits selected method bodies from a source code file searching for other method calls. The result is stored as a
     * field of the input visitor.
     *
     * @param file a map object that identifies a file by 'path' and 'methods'. A method is identified by its name.
     * @param visitor visitor to visit method bodies
     */
    abstract visitFile(file, TestCodeVisitor visitor)

    abstract TestCodeVisitor parseUnitBody(ChangedUnitTestFile file)

    abstract ChangedUnitTestFile doExtractUnitTest(String path, String content, List<Integer> changedLines)

    abstract String getClassForFile(String path)

    abstract boolean hasCompilationError(String path)

}
package br.ufpe.cin.tan.test

import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.commit.change.gherkin.GherkinManager
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.commit.change.stepdef.ChangedStepdefFile
import br.ufpe.cin.tan.commit.change.unit.ChangedUnitTestFile
import br.ufpe.cin.tan.test.ruby.routes.RoutesManager
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.Util
import gherkin.ast.Background
import gherkin.ast.Scenario
import gherkin.ast.ScenarioOutline
import gherkin.ast.Step
import groovy.util.logging.Slf4j

/***
 * Provides the common logic for test code parsers and the base method to compute task interfaces by
 * Template Method pattern.
 */
@Slf4j
abstract class TestCodeAbstractAnalyser {

    RoutesManager routesManager
    String repositoryPath
    String stepsFilePath

    List<StepRegex> regexList
    Set methods //keys: name, args, path
    List<String> projectFiles
    List<String> viewFiles

    AnalysisData analysisData
    protected Set notFoundViews
    protected Set compilationErrors
    GherkinManager gherkinManager
    Set codeFromViewAnalysis

    /***
     * Initializes fields used to link step declaration and code.
     *
     * @param repositoryPath It could be a URL or a local path.
     */
    TestCodeAbstractAnalyser(String repositoryPath, GherkinManager gherkinManager) {
        this.repositoryPath = repositoryPath
        this.gherkinManager = gherkinManager
        configureStepsFilePath()
        regexList = []
        methods = [] as Set
        projectFiles = []
        viewFiles = []
        notFoundViews = [] as Set
        compilationErrors = [] as Set
        codeFromViewAnalysis = [] as Set
        analysisData = new AnalysisData()
    }

    /***
     * Updates a visitor's task interface by identifying related view files.
     *
     * @param visitor
     */
    abstract void findAllPages(TestCodeVisitorInterface visitor)

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
    abstract TestCodeVisitorInterface parseStepBody(FileToAnalyse file) //keys: path, lines

    /***
     * Visits selected method bodies from a source code file searching for other method calls. The result is stored as a
     * field of the input visitor.
     *
     * @param file a map object that identifies a file by 'path' and 'methods'. A method is identified by its name.
     * @param visitor visitor to visit method bodies
     */
    abstract visitFile(file, TestCodeVisitorInterface visitor)

    abstract TestCodeVisitorInterface parseUnitBody(ChangedUnitTestFile file)

    abstract ChangedUnitTestFile doExtractUnitTest(String path, String content, List<Integer> changedLines)

    abstract String getClassForFile(String path)

    abstract boolean hasCompilationError(String path)

    def configureProperties() {
        analysisData.trace = []
        projectFiles = Util.findFilesFromDirectoryByLanguage(repositoryPath)
        configureRegexList() // Updates regex list used to match step definition and step code
        configureMethodsList()
        def views = Util.findFilesFromDirectory(repositoryPath + File.separator + Util.VIEWS_FILES_RELATIVE_PATH)
        viewFiles = views.findAll { Util.isViewFile(it) }
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
        List<FileToAnalyse> files2 = findCodeForStepsIndependentFromAcceptanceTest(stepFiles, acceptanceTests)
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

    List<FileToAnalyse> findCodeForStepOrganizedByFile(StepCall call, boolean extractArgs) {
        def calledSteps = []  //path, line, args, parentType
        List<FileToAnalyse> result = []

        /* find step declaration */
        def stepCodeMatch = regexList?.findAll { call.text ==~ it.value }
        def match = null
        if (!stepCodeMatch.empty) match = stepCodeMatch.first() //we consider only the first match
        if (stepCodeMatch.size() > 1) {
            log.warn "There are many implementations for step code: ${call.text}; ${call.path} (${call.line})"
            stepCodeMatch.each { log.info it.toString() }
            analysisData.multipleStepMatches += [path: call.path, text: call.text]
        }
        if (match) { //step code was found
            def args = []
            if (extractArgs) args = extractArgsFromStepText(call.text, match.value)
            calledSteps += [path: match.path, line: match.line, args: args, parentType: call.parentType]
        } else {
            log.warn "Step code was not found: ${call.text}; ${call.path} (${call.line})"
            def text = call.text.replaceAll(/".+"/,"\"\"").replaceAll(/'.+'/,"\'\'")
            analysisData.matchStepErrors += [path: call.path, text: text]
        }

        /* organizes step declarations in files */
        def files = (calledSteps*.path)?.flatten()?.unique()
        files?.each { file ->
            def codes = calledSteps.findAll { it.path == file }
            if (codes) {
                def methodsToAnalyse = []
                codes.each { code ->
                    methodsToAnalyse += new MethodToAnalyse(line: code.line, args: code.args, type: code.parentType)
                }
                result += new FileToAnalyse(path: file, methods: methodsToAnalyse.unique())
            }
        }
        result
    }

    List<StepCode> findCodeForStep(step, String path, boolean extractArgs, StepCode last) {
        List<StepCode> code = []
        def stepCodeMatch = regexList?.findAll { step.text ==~ it.value }
        def match = null
        if (!stepCodeMatch.empty) match = stepCodeMatch.first() //we consider only the first match
        if (stepCodeMatch.size() > 1) {
            log.warn "There are many implementations for step code: ${step.text}; $path (${step.location.line})"
            stepCodeMatch.each { log.info it.toString() }
            analysisData.multipleStepMatches += [path: path, text: step.text]
        }
        if (match) { //step code was found
            def args = []
            if (extractArgs) args = extractArgsFromStepText(step.text, match.value)
            def keyword = step.keyword
            if (keyword == ConstantData.GENERIC_STEP) {
                keyword = match.keyword
                analysisData.genericStepKeyword += [path: path, text: step.text]
            }
            if (last && (keyword in [ConstantData.AND_STEP_EN, ConstantData.BUT_STEP_EN])) keyword = last.type
            code += new StepCode(step: step, codePath: match.path, line: match.line, args: args, type: keyword)
        } else {
            log.warn "Step code was not found: ${step.text}; $path (${step.location.line})"
            def text = step.text.replaceAll(/".+"/,"\"\"").replaceAll(/'.+'/,"\'\'")
            analysisData.matchStepErrors += [path: path, text: text]
        }
        code
    }

    List<StepCode> findCodeForSteps(List steps, String path) {
        List<StepCode> codes = []
        for (int i = 0; i < steps.size(); i++) {
            def previousStep = null
            if (i > 0) previousStep = codes.last()
            def code = findCodeForStep(steps.get(i), path, true, previousStep)
            if (code && !code.empty) codes += code
            else {
                codes = []
                break
            }
        }
        codes
    }

    List findCodeForStepIndependentFromAcceptanceTest(StepDefinition step, List<AcceptanceTest> acceptanceTests) {
        def result = []
        def stepCodeMatch = regexList?.findAll { step.regex == it.value }
        def match = null
        if (!stepCodeMatch.empty) match = stepCodeMatch.first() //we consider only the first match
        if (stepCodeMatch.size() > 1) {
            log.warn "There are many implementations for step code: ${step.value}; ${step.path} (${step.line})"
            analysisData.multipleStepMatches += [path: step.path, text: step.value]
        }
        if (match) { //step code was found
            def type = findStepType(step, acceptanceTests)
            result += [path: match.path, line: match.line, keyword: type, text: match.value]
        } else {
            log.warn "Step code was not found: ${step.value}; ${step.path} (${step.line})"
            def text = step.value.replaceAll(/".+"/,"\"\"").replaceAll(/'.+'/,"\'\'")
            analysisData.matchStepErrors += [path: step.path, text: text]
        }
        result
    }

    List findCodeForStepsIndependentFromAcceptanceTest(List<ChangedStepdefFile> stepFiles, List<AcceptanceTest> acceptanceTests) {
        def values = []
        List<FileToAnalyse> result = []
        stepFiles?.each { file ->
            def partialResult = []
            file.changedStepDefinitions?.each { step ->
                def code = findCodeForStepIndependentFromAcceptanceTest(step, acceptanceTests) //path, line, keyword, text
                if (code && !code.empty) partialResult += code
            }
            if (!partialResult.empty) {
                def methodsToAnalyse = []
                if (Util.WHEN_FILTER) partialResult = partialResult?.findAll { it.keyword != ConstantData.THEN_STEP_EN }
                partialResult?.each {
                    methodsToAnalyse += new MethodToAnalyse(line: it.line, args: [], type: it.keyword)
                }
                if (!partialResult.empty) {
                    result += new FileToAnalyse(path: partialResult?.first()?.path, methods: methodsToAnalyse)
                }
            }
            values += partialResult
        }

        analysisData.foundStepDefs = values
        log.info "Number of implemented step definitions: ${values.size()}"
        def info = ""
        values?.each { info += "${it.text} (${it.path}: ${it.line})\n" }
        log.info info.toString()

        result
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

    /***
     * Identifies methods to visit from a list of method calls, considering the visit history.
     * The methods of interest are defined by test code.
     *
     * @param lastCalledMethods list of map objects identifying called methods by 'name', 'type', 'file' and 'step'.
     * @param allVisitedFiles A collection all visited files identified by 'path' and visited 'methods'.
     * @return a list of methods grouped by path.
     */
    def listFilesToParse(lastCalledMethods, allVisitedFiles) {
        def validCalledMethods = lastCalledMethods.findAll { it.file != null && it.type != "StepCall" }
        def methods = groupMethodsToVisitByFile(validCalledMethods)
        def filesToVisit = []
        methods.each { file ->
            def match = allVisitedFiles?.find { it.path == file.path }
            if (match != null) {
                filesToVisit += [path: file.path, methods: file.methods - match.methods]
            } else {
                filesToVisit += [path: file.path, methods: file.methods]
            }
        }
        updateTraceMethod(filesToVisit)
        return filesToVisit
    }

    /***
     * Identifies methods to visit from a list of method calls. The methods of interest are defined by test code.
     *
     * @param methodsList list of map objects identifying called methods by 'name', 'type' and 'file'.
     * @return a list of methods grouped by path.
     */
    static groupMethodsToVisitByFile(methodsList) {
        def testFiles = []
        def calledTestMethods = methodsList?.findAll { it.file != null && Util.isTestFile(it.file) }?.unique()
        calledTestMethods*.file.unique().each { path ->
            def methodsInPath = calledTestMethods.findAll { it.file == path }
            def methods = methodsInPath.collect { [name: it.name, step: it.step] }
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

    def updateTrace(FileToAnalyse file){
        analysisData.trace += [path: file.path, methods: file.methods*.line]
    }

    def updateTrace(List<FileToAnalyse> files){
        files.each{ updateTrace(it) }
    }

    private static findStepType(StepDefinition stepDefinition, List<AcceptanceTest> acceptanceTests) {
        def result = stepDefinition.keyword
        def stepCodeList = (acceptanceTests*.stepCodes.flatten())
        def match = stepCodeList.find { it.step.text ==~ stepDefinition.regex }
        if (match) { //Step (gherkin) and step definition (ruby) were both changed
            result = match.type
        }
        result
    }

    private updateTraceMethod(List files){
        files.each { file -> analysisData.trace += [path: file.path, methods: file.methods] }
    }

    private consolidateTrace(){
        def aux = []
        def files = analysisData.trace*.path
        def groupsByFile = analysisData.trace.groupBy({ t -> t.path })
        files.each{ file ->
            def methods = groupsByFile[file].collect{ it.methods }.flatten().unique()
            def path = file-repositoryPath
            def index = path.indexOf(File.separator+File.separator)+1
            aux += [path: path.substring(index), methods:methods]
        }
        analysisData.trace = aux.sort { it.path }
    }

    private ITest computeInterface(List<FileToAnalyse> filesToAnalyse, Set removedSteps) {
        def interfaces = []
        List<StepCall> calledSteps = []

        updateTrace(filesToAnalyse)
        filesToAnalyse?.eachWithIndex { stepDefFile, index ->
            log.info stepDefFile.toString()

            /* first level: To identify method calls from step body. */
            TestCodeVisitorInterface testCodeVisitor = parseStepBody(stepDefFile)

            /* second level: To visit methods until there is no more method calls of methods defined in test code. */
            def visitedFiles = []
            def filesToParse = listFilesToParse(testCodeVisitor.taskInterface.methods, visitedFiles) //[path: file.path, methods: [name:, step:]]
            log.info "Files to parse: ${filesToParse.size()}"
            filesToParse.each {
                log.info "path: ${it?.path}"
                log.info "methods: ${it?.methods?.size()}"
                it.methods.each { m ->
                    log.info m.toString()
                }
            }

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
                filesToParse = listFilesToParse(lastCalledMethods, visitedFiles)
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

            analysisData.visitCallCounter += testCodeVisitor.visitCallCounter
            def canonicalPath = Util.getRepositoriesCanonicalPath()
            analysisData.lostVisitCall += testCodeVisitor.lostVisitCall.collect {
                [path: it.path - canonicalPath, line: it.line]
            }
            analysisData.testCode += testCodeVisitor.methodBodies
        }

        /* identifies more step definitions to analyse */
        log.info "calledSteps: ${calledSteps.size()}"
        calledSteps.each{ log.info it.toString() }

        List<FileToAnalyse> newStepsToAnalyse = identifyMethodsPerFileToVisitByStepCalls(calledSteps)
        newStepsToAnalyse = updateStepFiles(filesToAnalyse, newStepsToAnalyse)
        if (!newStepsToAnalyse.empty) interfaces += computeInterface(newStepsToAnalyse, removedSteps)

        /* collapses step code interfaces to define the interface for the whole task */
        fillITest(interfaces, removedSteps)
    }

    private fillITest(List<ITest> interfaces, Set removedSteps) {
        def itest = ITest.collapseInterfaces(interfaces)
        itest.matchStepErrors = organizeMatchStepErrors(removedSteps)
        itest.multipleStepMatches = organizeMultipleStepMatches(removedSteps)
        itest.genericStepKeyword = analysisData.genericStepKeyword
        itest.compilationErrors = organizeCompilationErrors()
        itest.codeFromViewAnalysis = this.getCodeFromViewAnalysis()
        itest.notFoundViews = notFoundViews.sort()
        itest.foundAcceptanceTests = analysisData.foundAcceptanceTests
        itest.foundStepDefs = analysisData.foundStepDefs
        itest.visitCallCounter = analysisData.visitCallCounter
        itest.lostVisitCall = analysisData.lostVisitCall
        itest.code += analysisData.testCode
        itest.trace = consolidateTrace()
        itest
    }

    private organizeMatchStepErrors(Set removedSteps) {
        def result = [] as Set
        def errors = analysisData.matchStepErrors - removedSteps
        def files = errors*.path.unique()
        files.each { file ->
            def index = file.indexOf(repositoryPath)
            def name = index >= 0 ? file.substring(index) - (repositoryPath + File.separator) : file
            def texts = (analysisData.matchStepErrors.findAll { it.path == file }*.text)?.unique()?.sort()
            result += [path:name, text:texts, size:texts.size()]
        }
        result
    }

    private organizeMultipleStepMatches(Set removedSteps) {
        def intersection = analysisData.multipleStepMatches.findAll { it in removedSteps }
        analysisData.multipleStepMatches - intersection
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

        analysisData.foundAcceptanceTests = acceptanceTests
        log.info "Number of implemented acceptance tests: ${acceptanceTests.size()}"
        acceptanceTests?.each { log.info it.toString() }
        acceptanceTests
    }

    private configureRegexList() {
        regexList = []
        def files = Util.findFilesFromDirectoryByLanguage(stepsFilePath)
        files.each { regexList += doExtractStepsRegex(it) }
    }

    private configureMethodsList() {
        methods = [] as Set
        def filesForSearchMethods = []
        Util.VALID_FOLDERS.each { folder ->
            filesForSearchMethods += Util.findFilesFromDirectoryByLanguage(repositoryPath + File.separator + folder)
        }
        filesForSearchMethods.each { methods += doExtractMethodDefinitions(it) }
    }

    private List<FileToAnalyse> identifyMethodsPerFileToVisitByStepCalls(List<StepCall> stepCalls) {
        List<FileToAnalyse> files = []
        stepCalls?.unique { it.text }?.each { stepCall ->
            List<FileToAnalyse> stepCode = findCodeForStepOrganizedByFile(stepCall, true)
            if (stepCode && !stepCode.empty) files += stepCode
        }
        identifyMethodsPerFile(files)
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
     * Organizes StepCode objects by codePath attribute.
     *
     * @param stepCodes list of related stepCodes
     * @return a list of path (step code path) and lines (start line of step codes) pairs
     */
    private static List<FileToAnalyse> identifyMethodsPerFileToVisit(List<StepCode> stepCodes) {
        List<FileToAnalyse> result = []
        def files = (stepCodes*.codePath)?.flatten()?.unique()
        files?.each { file ->
            def codes = []
            if (Util.WHEN_FILTER) codes = stepCodes.findAll {
                it.codePath == file && it.type != ConstantData.THEN_STEP_EN
            }
            else codes = stepCodes.findAll { it.codePath == file }
            List<MethodToAnalyse> methods = []
            codes?.each {
                methods += new MethodToAnalyse(line: it.line, args: it.args, type: it.type)
            }
            if (!methods.empty) result += new FileToAnalyse(path: file, methods: methods.unique())

        }
        result
    }

    private static List<FileToAnalyse> collapseFilesToVisit(List<FileToAnalyse> files1, List<FileToAnalyse> files2) {
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

    private static List<String> extractArgsFromStepText(String text, String regex) {
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

    private configureStepsFilePath() {
        stepsFilePath = repositoryPath + File.separator + Util.STEPS_FILES_RELATIVE_PATH
        def directory = new File(stepsFilePath)
        def files = []
        if (directory.exists()) {
            files = Util.findFilesFromDirectoryByLanguage(stepsFilePath)
        }
        if (files.empty) {
            log.warn "Default folder of step definitions does not exists or it is empty: '${stepsFilePath}'"
            def subfolders = Util.findFoldersFromDirectory(repositoryPath + File.separator + ConstantData.DEFAULT_GHERKIN_FOLDER)
            def stepDefsFolder = subfolders.find { it.endsWith("step_definitions") }
            if (stepDefsFolder && !stepDefsFolder.equals(stepsFilePath)) {
                log.warn "Default folder of step definitions is empty."
                def stepDefFiles = Util.findFilesFromDirectoryByLanguage(stepDefsFolder)
                if (!stepDefFiles.empty) {
                    def index = stepDefsFolder.indexOf(repositoryPath)
                    stepsFilePath = stepDefsFolder.substring(index)
                    log.warn "We fix the folder of step definitions. The right one is: '${stepsFilePath}'"
                }
            }

        }
    }

}
package testCodeAnalyser


import gherkin.ast.ScenarioDefinition
import taskAnalyser.GherkinFile
import taskAnalyser.TaskInterface
import util.Util


/***
 * Provides the common logic for test code parsers and the base method to compute task interfaces by
 * Template Method pattern.
 */
abstract class TestCodeAbstractParser {

    String repositoryPath
    String stepsFilePath

    List<StepRegex> regexList
    Set methods
    List<String> projectFiles
    List<String> viewFiles

    /***
     * Initializes fields used to link step declaration and code.
     *
     * @param repositoryPath It could be a URL or a local path.
     */
    TestCodeAbstractParser(String repositoryPath){
        this.repositoryPath = repositoryPath
        stepsFilePath = repositoryPath + Util.STEPS_FILES_RELATIVE_PATH
        regexList = []
        methods = [] as Set
        projectFiles = []
        viewFiles = []
    }

    /***
     * Matches step declaration and code
     */
    def List<StepCode> extractStepCode(List<GherkinFile> gherkinFiles){
        List<AcceptanceTest> acceptanceTests = []
        gherkinFiles?.each { gherkinFile ->
            /* finds step code for changed scenario definitions from a Gherkin file */
            gherkinFile.changedScenarioDefinitions?.each { definition ->
                def test = findStepCode(definition, gherkinFile.path)
                if(test) acceptanceTests += test
            }
        }
        acceptanceTests?.each { println it }

        return acceptanceTests*.stepCodes.flatten().unique()
    }

    def configureProperties(){
        projectFiles = Util.findFilesFromDirectoryByLanguage(repositoryPath)
        configureRegexList() // Updates regex list used to match step definition and step code
        configureMethodsList()
        viewFiles = Util.findFilesFromDirectory(repositoryPath+Util.VIEWS_FILES_RELATIVE_PATH)
    }

    private configureRegexList(){
        regexList = []
        def files = Util.findFilesFromDirectoryByLanguage(stepsFilePath)
        files.each{ regexList += doExtractStepsRegex(it) }
    }

    private configureMethodsList(){
        methods = []
        projectFiles = Util.findFilesFromDirectoryByLanguage(repositoryPath)
        projectFiles.each{ methods += doExtractMethodDefinitions(it) }
    }

    /***
     * Finds step code of a scenario definition.
     * This method cannot be private. Otherwise, it throws groovy.lang.MissingMethodException.
     *
     * @param scenarioDefinition scenario definition of interest
     * @param scenarioDefinitionPath path of Gherkin file that contains the scenario definition
     * @return AcceptanceTest object that represents a match between scenario definition and implementation.
     */
    AcceptanceTest findStepCode(ScenarioDefinition scenarioDefinition, String scenarioDefinitionPath) {
        List<StepCode> codes = []
        scenarioDefinition?.steps?.each { step ->
            def stepCodeMatch = regexList?.findAll{ step.text ==~ it.value }
            if(stepCodeMatch && stepCodeMatch.size()==1){ //step code was found
                stepCodeMatch = stepCodeMatch.get(0)
                StepCode stepCode = new StepCode(step:step, codePath:stepCodeMatch.path, line:stepCodeMatch.line)
                codes += stepCode
            } else println "step code was not found!"
        }

        if(codes.isEmpty()){
            return null
        }
        else{
            return new AcceptanceTest(gherkinFilePath:scenarioDefinitionPath, scenarioDefinition:scenarioDefinition, stepCodes:codes)
        }
    }

    /***
     * Organizes StepCode objects by codeParse attribute.
     *
     * @param stepCodes list of related stepCodes
     * @return a list of path (step code path) and lines (start line of step codes) pairs
     */
    static identifyMethodsPerFileToVisit(List<StepCode> stepCodes){
        def result = []
        def files = (stepCodes*.codePath)?.flatten()?.unique()
        files.each{ file ->
            def codes = stepCodes.findAll{ it.codePath == file }
            if(codes){
                result += [path: file, lines: (codes*.line)?.flatten()?.unique()?.sort()]
            }
        }
        return result
    }

    /***
     * Identifies methods to visit from a list of method calls, considering the visit history.
     * The methods of interest are defined by test code.
     *
     * @param lastCalledMethods list of map objects identifying called methods by 'name', 'type' and 'file'.
     * @param allVisitedFiles A collection all visited files identified by 'path' and visited 'methods'.
     * @return a list of methods grouped by path.
     */
    static listFilesToVisit(def lastCalledMethods, def allVisitedFiles){
        def methods = listTestMethodsToVisit(lastCalledMethods)

        def filesToVisit = []
        methods.each{ file ->
            def match = allVisitedFiles?.find{ it.path == file.path }
            if(match != null) {
                filesToVisit += [path:file.path, methods:file.methods-match.methods]
            }
            else {
                filesToVisit += [path:file.path, methods:file.methods]
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
    static listTestMethodsToVisit(def lastCalledMethods){
        def testFiles = []
        def calledTestMethods = lastCalledMethods?.findAll{ it.file!=null && Util.isTestCode(it.file) }?.unique()
        calledTestMethods*.file.unique().each{ path ->
            def methods = calledTestMethods.findAll{ it.file == path }*.name
            testFiles += [path:path, methods:methods]
        }
        return testFiles
    }

    static updateVisitedFiles(List allVisitedFiles, List filesToVisit){
        def allFiles = allVisitedFiles + filesToVisit
        def paths = (allFiles*.path)?.unique()
        def result = []
        paths?.each{ path ->
            def methods = (allFiles?.findAll{ it.path == path}*.methods)?.flatten()?.unique()
            if(methods!=null && !methods.isEmpty()) result += [path:path, methods:methods]
        }
        return result
    }


    /***
     * Template method to compute test-based task interface for done tasks (evaluation study).
     *
     * @param gherkinFiles list of changed gherkin files
     * @return task interface
     */
    TaskInterface computeInterfaceForDoneTask(List<GherkinFile> gherkinFiles){
        configureProperties()
        computeInterfaceForTodoTask(gherkinFiles)
    }

    /***
     * Template method to compute test-based task interface for new tasks.
     *
     * @param gherkinFiles list of changed gherkin files
     * @return task interface
     */
    TaskInterface computeInterfaceForTodoTask(List<GherkinFile> gherkinFiles){
        List<StepCode> stepCodes = extractStepCode(gherkinFiles)

        /* Identifies files to parse. The files are identified by path and lines of interest. */
        def filesToParse = identifyMethodsPerFileToVisit(stepCodes)

        /* parses step nodes and records method calls */
        def interfaces = []

        /* visits each step body and each method called from there */
        filesToParse.eachWithIndex{ stepCode, index ->

            /* first level: To identify method calls from step body. */
            TestCodeVisitor testCodeVisitor = parseStepBody(stepCode)

            /* second level: To visit methods until there is no more method calls of methods defined in test code. */
            def visitedFiles = []
            def filesToVisit = listFilesToVisit(testCodeVisitor.taskInterface.methods, visitedFiles)

            while (!filesToVisit.isEmpty()) {
                /* copies methods from task interface */
                def backupCalledMethods = testCodeVisitor.taskInterface.methods

                /* visits each file */
                filesToVisit.each { f -> visitFile(f, testCodeVisitor) }

                /* computes methods to visit based on visit history */
                visitedFiles = updateVisitedFiles(visitedFiles, filesToVisit)
                def lastCalledMethods = testCodeVisitor.taskInterface.methods - backupCalledMethods
                filesToVisit = listFilesToVisit(lastCalledMethods, visitedFiles)
            }

            /* searches for view files */
            findAllPages(testCodeVisitor)

            /* updates task interface */
            interfaces += testCodeVisitor.taskInterface
        }

        /* collapses step code interfaces to define the interface for the whole task */
        TaskInterface.colapseInterfaces(interfaces)
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
     * @return map identifying the file and its regexs
     */
    abstract List<StepRegex> doExtractStepsRegex(String path)

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
    abstract TestCodeVisitor parseStepBody(def file) //keys: path, lines

    /***
     * Visits selected method bodies from a source code file searching for other method calls. The result is stored as a
     * field of the input visitor.
     *
     * @param file a map object that identifies a file by 'path' and 'methods'. A method is identified by its name.
     * @param visitor visitor to visit method bodies
     */
    abstract visitFile(def file, TestCodeVisitor visitor)

}
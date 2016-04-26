package taskAnalyser.task

import commitAnalyser.Commit
import groovy.time.TimeCategory
import groovy.util.logging.Slf4j
import util.Util

/***
 * Represents a done task, that is, a task that contains production and test code. The code is published in a public
 * Git repository in GitHub. The task is used to evaluation study, to validate test-based task interfaces by comparing
 * them with real interfaces.
 */
@Slf4j
class DoneTask extends Task {

    String repositoryIndex
    List<Commit> commits
    List<GherkinFile> changedGherkinFiles
    List<StepDefinitionFile> changedStepDefinitions
    List<UnitFile> changedUnitFiles

    DoneTask(String repositoryUrl, String id, List<String> shas){
        super(repositoryUrl, true, id)
        changedGherkinFiles = []
        changedStepDefinitions = []
        changedUnitFiles = []

        // retrieves commits code
        commits = gitRepository.searchCommitsBySha(testCodeParser, *shas)

        // identifies changed gherkin files and scenario definitions
        List<Commit> commitsChangedGherkinFile = commits?.findAll{ it.gherkinChanges && !it.gherkinChanges.isEmpty() }
        changedGherkinFiles = identifyChangedGherkinContent(commitsChangedGherkinFile)

        // identifies changed step definitions
        List<Commit> commitsStepsChange = this.commits?.findAll{ it.stepChanges && !it.stepChanges.isEmpty()}
        changedStepDefinitions = identifyChangedStepContent(commitsStepsChange)
    }

    DoneTask(String repositoryIndex, String repositoryUrl, String id, List<Commit> commits) {
        super(repositoryUrl, true, id)
        this.repositoryIndex = repositoryIndex
        changedGherkinFiles = []
        changedStepDefinitions = []
        changedUnitFiles = []

        // retrieves commits code
        this.commits = gitRepository.searchCommitsBySha(testCodeParser, *(commits*.hash))

        // identifies changed gherkin files and scenario definitions
        List<Commit> commitsChangedGherkinFile = this.commits?.findAll{ it.gherkinChanges && !it.gherkinChanges.isEmpty() }
        changedGherkinFiles = identifyChangedGherkinContent(commitsChangedGherkinFile)

        // identifies changed step definitions
        List<Commit> commitsChangedStepFile = this.commits?.findAll{ it.stepChanges && !it.stepChanges.isEmpty()}
        changedStepDefinitions = identifyChangedStepContent(commitsChangedStepFile)
    }

    private static List<GherkinFile> identifyChangedGherkinContent(List<Commit> commitsChangedGherkinFile){
        List<GherkinFile> gherkinFiles = []
        commitsChangedGherkinFile?.each{ commit -> //commits sorted by date
            commit.gherkinChanges?.each{ file ->
                if(!file.changedScenarioDefinitions.isEmpty()){
                    def index = gherkinFiles.findIndexOf{ it.path == file.path }
                    GherkinFile foundFile
                    if(index >= 0) foundFile = gherkinFiles.get(index)

                    //another previous commit changed the same gherkin file
                    if(foundFile){
                        def equalDefs = foundFile.changedScenarioDefinitions.findAll{ it.name in file.changedScenarioDefinitions*.name }
                        def oldDefs = foundFile.changedScenarioDefinitions - equalDefs

                        if(!oldDefs.isEmpty()) { //previous commit changed other(s) scenario definition(s)
                            file.changedScenarioDefinitions += oldDefs
                        }
                        gherkinFiles.set(index, file)

                    } else{
                        gherkinFiles += file
                    }
                }
            }
        }
        gherkinFiles
    }

    private static List<StepDefinitionFile> identifyChangedStepContent(List<Commit> commitsChangedStepFile){
        List<StepDefinitionFile> stepFiles = []
        commitsChangedStepFile?.each{ commit -> //commits sorted by date
            commit.stepChanges?.each{ file ->
                if(!file.changedStepDefinitions.isEmpty()){
                    def index = stepFiles.findIndexOf{ it.path == file.path }
                    StepDefinitionFile foundFile
                    if(index >= 0) foundFile = stepFiles.get(index)

                    //another previous commit changed the same step definition file
                    if(foundFile){
                        def equalDefs = foundFile.changedStepDefinitions.findAll{ it.value in file.changedStepDefinitions*.value }
                        def oldDefs = foundFile.changedStepDefinitions - equalDefs

                        if(!oldDefs.isEmpty()) {  //previous commit changed other(s) step definition(s)
                            file.changedStepDefinitions += oldDefs
                        }
                        stepFiles.set(index, file)
                    } else{
                        stepFiles += file
                    }
                }
            }
        }
        stepFiles
    }

    private TaskInterface identifyProductionChangedFiles(){
        /* Identifies all project files */
        def currentFiles = Util.findFilesFromDirectory(gitRepository.localPath).collect {
            it - (Util.getRepositoriesCanonicalPath()+gitRepository.name+File.separator)
        }

        /* Identifies all changed files by task */
        def filenames = commits*.coreChanges*.path?.flatten()?.unique()?.sort()

        /* Identifies all changed files that exist when the task was finished. It is necessary because a task might
        remove a file and other (concurrent) task might add it back.*/
        def validFiles = filenames.findAll{ it in currentFiles }

        /* Constructs task interface object */
        organizeProductionFiles(validFiles)
    }

    private TaskInterface organizeProductionFiles(def productionFiles){
        def taskInterface = new TaskInterface()
        productionFiles.each{ file ->
            def path = gitRepository.name+File.separator+file
            if(path.contains(Util.VIEWS_FILES_RELATIVE_PATH)){
                def index = path.lastIndexOf(File.separator)
                taskInterface.classes += [name:path.substring(index+1), file:path]
            } else {
                taskInterface.classes += [name: testCodeParser.getClassForFile(path), file:path]
            }
        }
        taskInterface
    }

    @Override
    List<GherkinFile> getAcceptanceTests() {
        changedGherkinFiles
    }

    /***
     * Computes task interface based in acceptance test code.
     * @return task interface
     */
    @Override
    TaskInterface computeTestBasedInterface(){
        def taskInterface = new TaskInterface()
        if(!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            return taskInterface
        }

        log.info "TASK ID: $id"
        log.info "COMMITS: ${this.commits*.hash}"
        log.info "COMMITS CHANGED GERKIN FILE: ${this.commits?.findAll{it.gherkinChanges && !it.gherkinChanges.isEmpty()}*.hash}"
        log.info "COMMITS CHANGED STEP DEFINITION FILE: ${this.commits?.findAll{it.stepChanges && !it.stepChanges.isEmpty()}*.hash}"

        if(!changedGherkinFiles.empty || !changedStepDefinitions.empty) {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(commits?.last()?.hash)

            // computes task interface based on the production code exercised by tests
            taskInterface = testCodeParser.computeInterfaceForDoneTask(changedGherkinFiles, changedStepDefinitions)

            // resets repository to last version
            gitRepository.reset()
        }

        taskInterface
    }

    @Override
    String computeTextBasedInterface() {
        def text = ""

        if(!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            return text
        }

        if(!changedGherkinFiles.empty || !changedStepDefinitions.empty) {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(commits?.last()?.hash)

            //computes task text based in gherkin scenarios
            text = super.computeTextBasedInterface()

            // resets repository to last version
            gitRepository.reset()
        }
        text
    }

    TaskInterface computeRealInterface(){
        def taskInterface = new TaskInterface()

        if(!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            return taskInterface
        }

        // resets repository to the state of the last commit to extract changes
        gitRepository.reset(commits?.last()?.hash)

        //computes real interface
        taskInterface = identifyProductionChangedFiles()

        // resets repository to last version
        gitRepository.reset()

        taskInterface
    }

    def computeInterfaces(){
        TaskInterface itest = new TaskInterface()
        String itext = ""
        TaskInterface ireal = new TaskInterface()

        if(!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            return [itest:itest, itext:itext, ireal:ireal]
        }

        log.info "TASK ID: $id"
        log.info "COMMITS: ${this.commits*.hash}"
        log.info "COMMITS CHANGED GERKIN FILE: ${this.commits?.findAll{it.gherkinChanges && !it.gherkinChanges.isEmpty()}*.hash}"
        log.info "COMMITS CHANGED STEP DEFINITION FILE: ${this.commits?.findAll{it.stepChanges && !it.stepChanges.isEmpty()}*.hash}"

        if(!changedGherkinFiles.empty || !changedStepDefinitions.empty) {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(commits?.last()?.hash)

            // computes task interface based on the production code exercised by tests
            itest = testCodeParser.computeInterfaceForDoneTask(changedGherkinFiles, changedStepDefinitions)

            //computes task text based in gherkin scenarios
            itext = super.computeTextBasedInterface()

            //computes real interface
            ireal = identifyProductionChangedFiles()

            // resets repository to last version
            gitRepository.reset()
        }

        [itest:itest, itext:itext, ireal:ireal]
    }

    def getCommitsQuantity(){
        commits.size()
    }

    def getDays(){
        def size = commits.size()
        if(size<2) size
        else{
            use(TimeCategory) {
                def last = new Date(commits.last().date*1000).clearTime()
                def first = new Date(commits.first().date*1000).clearTime()
                (last - first).days + 1
            }
        }
    }

    def getGherkinTestQuantity(){
        def values = changedGherkinFiles*.changedScenarioDefinitions*.size().flatten()
        if(values.empty) 0
        else values.sum()
    }

    def getStepDefQuantity(){
        def values = changedStepDefinitions*.changedStepDefinitions*.size().flatten()
        if(values.empty) 0
        else values.sum()
    }

    def getRenamedFiles(){
        commits*.renameChanges?.flatten()?.unique()?.sort()
    }

}

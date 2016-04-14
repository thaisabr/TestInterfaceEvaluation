package taskAnalyser

import commitAnalyser.Commit
import groovy.time.TimeCategory
import groovy.util.logging.Slf4j
import util.Util
import util.ruby.RubyUtil

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
    List<UnitFile> changedUnitFiles
    List<StepDefinitionFile> changedStepDefinitions

    DoneTask(String repositoryUrl, String id, List<String> shas){
        super(repositoryUrl, true, id)
        changedGherkinFiles = []
        changedUnitFiles = []
        changedStepDefinitions = []

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
        changedUnitFiles = []
        changedStepDefinitions = []

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
        def allFiles = Util.findFilesFromDirectory(gitRepository.localPath).collect{
            it - (Util.getRepositoriesCanonicalPath()+gitRepository.name+File.separator)
        }

        def allChanges = commits?.sort{it.date}*.codeChanges?.flatten()
        def files = [] as Set
        def filenames = allChanges*.filename?.unique()
        filenames.each{ file ->
            def changes = allChanges.findAll{ it.filename == file }
            if(changes?.last()?.filename in allFiles) files += file
        }
        organizeProductionFiles(files)
    }

    private TaskInterface organizeProductionFiles(def files){
        def taskInterface = new TaskInterface()
        def productionFiles = Util.findAllProductionFiles(files)
        productionFiles.each{ file ->
            def path = gitRepository.name+File.separator+file
            if(path.contains(Util.VIEWS_FILES_RELATIVE_PATH)){
                def index = path.lastIndexOf(File.separator)
                taskInterface.classes += [name:path.substring(index+1), file:path]
            } else {
                taskInterface.classes += [name:RubyUtil.getClassName(path), file:path]
            }
        }
        taskInterface
    }

    /***
     * Computes task interface based in unit test code.
     * It can be seen as a future refinement for task interface.
     * @return task interface
     */
    @Deprecated
    TaskInterface computeUnitTestBasedInterface(){
        log.info "TASK ID: $id"
        def interfaces = []

        /* identifies changed unit test files */
        List<Commit> commitsChangedRspecFile = commits.findAll{ !it.unitChanges.isEmpty() }

        commitsChangedRspecFile?.each{ commit ->
            /* resets repository to the state of the commit to extract changes */
            gitRepository.reset(commit.hash)

            /* translates changed lines in unit test files to changed unit tests */
            List<UnitFile> changes = gitRepository.identifyChangedUnitTestContent(commit)

            if(!changes.isEmpty()){
                changedUnitFiles += changes

                /* computes task interface based on the production code exercised by tests */
                interfaces += testCodeParser.computeInterfaceForDoneTaskByUnitTest(changes)
            }
            else{
                log.info "No changes in unit test!\n"
            }
        }

        /* resets repository to last version */
        gitRepository.reset()

        /* collapses step code interfaces to define the interface for the whole task */
        TaskInterface.colapseInterfaces(interfaces)
    }

    /***
     * (TO VALIDATE)
     * Computes task interface based in unit test code.
     * It can be seen as a future refinement for task interface.
     * Changes interpretation are based in the checkout of the last commit of the task. It could introduce error and
     * after validation it should be removed.
     * @return task interface
     */
    @Deprecated
    TaskInterface computeUnitTestBasedInterfaceVersion2(){
        log.info "TASK ID: $id; LAST COMMIT: ${commits?.last()?.hash}"
        TaskInterface taskInterface = new TaskInterface()

        /* identifies changed unit test files */
        List<Commit> commitsChangedRspecFile = commits.findAll{ !it.unitChanges.isEmpty() }

        if(!commitsChangedRspecFile.isEmpty()){
            /* resets repository to the state of the last commit to extract changes */
            gitRepository.reset(commits?.last()?.hash)
            changedUnitFiles = gitRepository.identifyChangedUnitTestContent(commitsChangedRspecFile)

            /* computes task interface based on the production code exercised by tests */
            taskInterface = testCodeParser.computeInterfaceForDoneTaskByUnitTest(changedUnitFiles)

            /* resets repository to last version */
            gitRepository.reset()
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
        if(commits && !commits.empty){
            gitRepository.reset(commits?.last()?.hash)
            taskInterface = identifyProductionChangedFiles()
            gitRepository.reset()
        }
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

    def getAcceptanceTestQuantity(){
        changedGherkinFiles.size()
    }

    def getStepDefQuantity(){
        changedStepDefinitions.size()
    }

}

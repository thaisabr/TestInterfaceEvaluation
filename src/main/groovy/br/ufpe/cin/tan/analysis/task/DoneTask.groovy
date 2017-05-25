package br.ufpe.cin.tan.analysis.task

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.analysis.itask.IReal
import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.commit.Commit
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.commit.change.stepdef.StepdefManager
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.commit.change.stepdef.ChangedStepdefFile
import br.ufpe.cin.tan.commit.change.unit.ChangedUnitTestFile
import gherkin.ast.Background
import gherkin.ast.Feature
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.util.logging.Slf4j
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.revwalk.RevCommit
import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.exception.CloningRepositoryException
import br.ufpe.cin.tan.util.ruby.RubyUtil

/***
 * Represents a done task, that is, a task that contains production and test code. The code is published in a public
 * Git repository in GitHub. The task is used to evaluation study, to validate test-based task interfaces by comparing
 * them with real interfaces.
 */
@Slf4j
class DoneTask extends Task {

    List<Commit> commits
    List<ChangedGherkinFile> changedGherkinFiles
    List<ChangedStepdefFile> changedStepDefinitions
    List<ChangedUnitTestFile> changedUnitFiles
    def removedFiles
    def renamedFiles
    int developers
    List dates
    int days
    String commitMessage
    def hashes

    DoneTask(String repositoryUrl, String id, List<String> shas) throws CloningRepositoryException {
        super(repositoryUrl, id)
        init(shas)
    }

    DoneTask(String repositoryUrl, String id, List<String> shas, boolean basic) throws CloningRepositoryException {
        super(repositoryUrl, id)
        if (basic) basicInit(shas)
        else init(shas)
    }

    private basicInit(List<String> shas) {
        changedGherkinFiles = []
        changedStepDefinitions = []
        changedUnitFiles = []
        commits = gitRepository.searchCommitsBySha(testCodeParser, *shas)
        hashes = commits*.hash
        developers = commits*.author?.flatten()?.unique()?.size()
        extractCommitMessages()
        extractDates()
        extractDays()
        renamedFiles = commits*.renameChanges?.flatten()?.unique()?.sort()
        extractRemovedFiles()
    }

    private String extractCommitMessages() {
        String msgs = commits*.message?.flatten()?.toString()
        if (msgs.length() > 1000) {
            commitMessage = msgs.toString().substring(0, 999) + " [TOO_LONG]"
        } else commitMessage = msgs
    }

    private init(List<String> shas) {
        basicInit(shas)

        if(!commits.empty) {
            RevCommit lastCommit = gitRepository.searchAllRevCommitsBySha(commits?.last()?.hash)?.first()

            // identifies changed gherkin files and scenario definitions
            List<Commit> commitsChangedGherkinFile = commits?.findAll {
                it.gherkinChanges && !it.gherkinChanges.isEmpty()
            }
            registryChangedGherkinContent(commitsChangedGherkinFile, lastCommit)

            // identifies changed step definitions
            List<Commit> commitsStepsChange = this.commits?.findAll { it.stepChanges && !it.stepChanges.isEmpty() }
            registryChangedStepContent(commitsStepsChange, lastCommit)
        } else {
            log.error "The task has no commits! Searched commits: "
            shas.each{ log.error it.toString() }
        }
    }

    private registryChangedStepContent(List<Commit> commitsChangedStepFile, RevCommit lastCommit){
        def stepDefinitions = identifyChangedStepContent(commitsChangedStepFile)
        def notFoundSteps = []
        def notFoundFiles = []
        List<ChangedStepdefFile> finalStepDefinitionsFilesSet = []

        stepDefinitions?.each { stepFile ->
            def content = gitRepository.extractFileContent(lastCommit, stepFile.path)
            List<StepDefinition> stepDefs = StepdefManager.parseStepDefinitionFile(stepFile.path, content, lastCommit.name, testCodeParser)
            if(stepDefs){
                def regexes = stepDefs*.regex
                if (!regexes.empty){
                    def initialSet = stepFile.changedStepDefinitions
                    def valid = initialSet.findAll { it.regex in regexes }
                    def finalSteps = stepDefs?.findAll{ it.regex in valid*.regex }
                    def invalid = initialSet - valid
                    if(!invalid?.empty) notFoundSteps += [file:stepFile.path, steps:invalid*.regex]
                    if(!valid?.empty) {
                        def newStepDefFile = new ChangedStepdefFile(path: stepFile.path, changedStepDefinitions: finalSteps)
                        finalStepDefinitionsFilesSet += newStepDefFile
                    }
                }
            } else {
                notFoundFiles += stepFile.path
            }
        }

        if(!notFoundFiles.empty){
            def text = "No long valid step definition files (task ${id}):\n"
            notFoundFiles?.each{ text += it+"\n" }
            log.warn text
        }

        if(!notFoundSteps.empty){
            def text = "No long valid step definitions (task ${id}):\n"
            notFoundSteps?.each{ n ->
                text += "File: ${n.file}\nSteps:\n"
                n.steps?.each{ text += it+"\n" }
            }
            log.warn text
        }

        changedStepDefinitions = finalStepDefinitionsFilesSet
    }

    private registryChangedGherkinContent(List<Commit> commitsChangedGherkinFile, RevCommit lastCommit){
        def gherkinFiles = identifyChangedGherkinContent(commitsChangedGherkinFile)
        def notFoundScenarios = []
        def notFoundFiles = []
        List<ChangedGherkinFile> finalGherkinFilesSet = []

        gherkinFiles?.each { gherkinFile ->
            def result = gitRepository.parseGherkinFile(gherkinFile.path, lastCommit.name)
            Feature feature = result.feature
            if(feature){
                def currentScenarios = feature?.children?.findAll{ !(it instanceof Background) }*.name
                if(currentScenarios && !currentScenarios.empty){
                    def initialSet = gherkinFile.changedScenarioDefinitions.findAll{ !(it instanceof Background) }
                    def valid = initialSet.findAll { it.name in currentScenarios }
                    def validNames = valid*.name
                    def finalScenarios = feature?.children?.findAll{ !(it instanceof Background) && it.name in validNames && !it.steps.empty }
                    def invalid = initialSet - valid
                    if(!invalid?.empty) notFoundScenarios += [file:gherkinFile.path, scenarios:invalid*.name]
                    if(!valid?.empty) {
                        def newGherkinFile = new ChangedGherkinFile(path: gherkinFile.path, feature: feature,
                                changedScenarioDefinitions: finalScenarios, featureFileText: result.content)
                        finalGherkinFilesSet += newGherkinFile
                    }
                }
            } else {
                notFoundFiles += gherkinFile.path
            }
        }

        if(!notFoundFiles.empty){
            def text = "No long valid gherkin files (task ${id}):\n"
            notFoundFiles?.each{ text += it+"\n" }
            log.warn text
        }

        if(!notFoundScenarios.empty){
            def text = "No long valid scenarios (task ${id}):\n"
            notFoundScenarios?.each{ n ->
                text += "File: ${n.file}\nScenarios:\n"
                n.scenarios?.each{ text += it+"\n" }
            }
            log.warn text
        }

        changedGherkinFiles = finalGherkinFilesSet
    }

    private static List<ChangedGherkinFile> identifyChangedGherkinContent(List<Commit> commitsChangedGherkinFile) {
        List<ChangedGherkinFile> gherkinFiles = []
        commitsChangedGherkinFile?.each { commit -> //commits sorted by date
            commit.gherkinChanges?.each { file ->
                if (!file.changedScenarioDefinitions.empty) {
                    def index = gherkinFiles.findIndexOf { it.path == file.path }
                    ChangedGherkinFile foundFile
                    if (index >= 0) foundFile = gherkinFiles.get(index)

                    //another previous commit changed the same gherkin file
                    if (foundFile) {
                        def equalDefs = foundFile.changedScenarioDefinitions.findAll {
                            it.name in file.changedScenarioDefinitions*.name
                        }
                        def oldDefs = foundFile.changedScenarioDefinitions - equalDefs

                        if (!oldDefs.isEmpty()) { //previous commit changed other(s) scenario definition(s)
                            file.changedScenarioDefinitions += oldDefs
                        }
                        gherkinFiles.set(index, file)

                    } else {
                        gherkinFiles += file
                    }
                }
            }
        }
        gherkinFiles
    }

    private static List<ChangedStepdefFile> identifyChangedStepContent(List<Commit> commitsChangedStepFile) {
        List<ChangedStepdefFile> stepFiles = []
        commitsChangedStepFile?.each { commit -> //commits sorted by date
            commit.stepChanges?.each { file ->
                if (!file.changedStepDefinitions.isEmpty()) {
                    def index = stepFiles.findIndexOf { it.path == file.path }
                    ChangedStepdefFile foundFile
                    if (index >= 0) foundFile = stepFiles.get(index)

                    //another previous commit changed the same step definition file
                    if (foundFile) {
                        def equalDefs = foundFile.changedStepDefinitions.findAll {
                            it.value in file.changedStepDefinitions*.value
                        }
                        def oldDefs = foundFile.changedStepDefinitions - equalDefs

                        if (!oldDefs.isEmpty()) {  //previous commit changed other(s) step definition(s)
                            file.changedStepDefinitions += oldDefs
                        }
                        stepFiles.set(index, file)
                    } else {
                        stepFiles += file
                    }
                }
            }
        }
        stepFiles
    }

    private IReal identifyProductionChangedFiles() {
        /* Identifies all project files */
        def canonicalPath = Util.getRepositoriesCanonicalPath()
        def currentFiles = Util.findFilesFromDirectory(gitRepository.localPath).collect {
            it - (canonicalPath + gitRepository.name + File.separator)
        }

        /* Identifies all changed files by task */
        def filenames = commits*.coreChanges*.path?.flatten()?.unique()?.sort()

        /* Identifies all changed files that exist when the task was finished. It is necessary because a task might
        remove a file and other (concurrent) task might add it back.*/
        def validFiles = filenames.findAll { it in currentFiles }

        /* Constructs task interface object */
        organizeProductionFiles(validFiles)
    }

    private IReal organizeProductionFiles(productionFiles) {
        def taskInterface = new IReal()

        //filtering result to only identify view and/or controller files
        productionFiles = Util.filterFiles(productionFiles)

        productionFiles?.each { file ->
            def path = gitRepository.name + File.separator + file
            if (path.contains(Util.VIEWS_FILES_RELATIVE_PATH)) {
                def index = path.lastIndexOf(File.separator)
                taskInterface.classes += [name: path.substring(index + 1), file: path]
            } else {
                taskInterface.classes += [name: testCodeParser.getClassForFile(path), file: path]
            }
        }
        taskInterface
    }

    private extractDates() {
        def devDates = commits*.date?.flatten()?.sort()
        if (devDates) devDates = devDates.collect { new Date(it * 1000).format('dd-MM-yyyy') }.unique()
        else devDates = []
        dates = devDates
    }

    private extractDays() {
        def size = commits.size()
        if (size < 2) days = size
        else {
            use(TimeCategory) {
                def last = new Date(commits.last().date * 1000).clearTime()
                def first = new Date(commits.first().date * 1000).clearTime()
                days = (last - first).days + 1
            }
        }
    }

    private extractRemovedFiles() {
        def changes = commits*.coreChanges?.flatten()?.findAll { it.type == DiffEntry.ChangeType.DELETE }
        removedFiles = changes?.collect { gitRepository.name + File.separator + it.path }?.unique()?.sort()
    }

    private showTaskInfo(){
        log.info "TASK ID: $id"
        log.info "COMMITS: ${this.commits*.hash}"
        log.info "COMMITS CHANGED GHERKIN FILE: ${this.commits?.findAll { it.gherkinChanges && !it.gherkinChanges.isEmpty() }*.hash}"
        log.info "COMMITS CHANGED STEP DEFINITION FILE: ${this.commits?.findAll { it.stepChanges && !it.stepChanges.isEmpty() }*.hash}"
    }

    private registryCompilationErrors(AnalysedTask task){
        def finalErrorSet = []
        def errors = task.itest.compilationErrors

        def gherkinErrors = errors.findAll{ Util.isGherkinFile(it.path) }
        gherkinErrors?.each{ error ->
            def result = gitRepository.parseGherkinFile(error.path, commits?.last()?.hash)
            if(!result.feature) finalErrorSet += error
        }

        def rubyErrors = errors - gherkinErrors
        rubyErrors?.each{ error ->
            def hasError = testCodeParser.hasCompilationError(error.path)
            if(hasError) finalErrorSet += error
        }

        def formatedResult = []
        finalErrorSet.each{ error ->
            def file = error.path
            def index = error.path.indexOf(gitRepository.localPath)
            def name = index >= 0 ? file.substring(index) - (gitRepository.localPath + File.separator) : file
            formatedResult += [path: name, msgs:error.msgs]
        }

        def itest = new ITest()
        itest.collapseInterfaces(task.itest)
        itest.compilationErrors = formatedResult
        task.itest = itest
    }

    private registryCompilationErrors(ITest iTest){
        def finalErrorSet = []
        def errors = iTest.compilationErrors
        errors?.each{ error ->
            def result = gitRepository.parseGherkinFile(error.path, commits?.last()?.hash)
            if(!result.feature) finalErrorSet += error
        }
        iTest.compilationErrors = finalErrorSet
    }

    @Override
    List<ChangedGherkinFile> getAcceptanceTests() {
        changedGherkinFiles
    }

    /***
     * Computes task interface based in acceptance test code.
     * @return task interface
     */
    @Override
    ITest computeTestBasedInterface() {
        TimeDuration timestamp = null
        def taskInterface = new ITest()
        if (!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            return taskInterface
        }
        showTaskInfo()

        try {
            if (!changedGherkinFiles.empty || !changedStepDefinitions.empty) {
                // resets repository to the state of the last commit to extract changes
                gitRepository.reset(commits?.last()?.hash)

                def initTime = new Date()
                // computes task interface based on the production code exercised by tests
                taskInterface = testCodeParser.computeInterfaceForDoneTask(changedGherkinFiles, changedStepDefinitions, gitRepository.removedSteps)
                def endTime = new Date()
                use(TimeCategory) {
                    timestamp = endTime - initTime
                }
                taskInterface.timestamp = timestamp

                registryCompilationErrors(taskInterface)

                // resets repository to last version
                gitRepository.reset()
            }
        } catch (Exception ex) {
            log.error "Error while computing test-based task interface."
            log.error ex.message
            ex.stackTrace.each{ log.error it.toString() }
        }

        taskInterface
    }

    @Override
    String computeTextBasedInterface() {
        def text = ""

        if (!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            return text
        }

        try {
            if (!changedGherkinFiles.empty || !changedStepDefinitions.empty) {
                // resets repository to the state of the last commit to extract changes
                gitRepository.reset(commits?.last()?.hash)

                //computes task text based in gherkin scenarios
                text = super.computeTextBasedInterface()

                // resets repository to last version
                gitRepository.reset()
            }
        } catch (Exception ex) {
            log.error "Error while computing text-based task interface."
            log.error ex.message
            ex.stackTrace.each{ log.error it.toString() }
        }
        text
    }

    IReal computeRealInterface() {
        TimeDuration timestamp = null
        def taskInterface = new IReal()

        if (!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            return taskInterface
        }

        try {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(commits?.last()?.hash)

            def initTime = new Date()
            //computes real interface
            taskInterface = identifyProductionChangedFiles()
            def endTime = new Date()
            use(TimeCategory) {
                timestamp = endTime - initTime
            }
            taskInterface.timestamp = timestamp

            // resets repository to last version
            gitRepository.reset()
        } catch (Exception ex) {
            log.error "Error while computing real task interface."
            log.error ex.message
            ex.stackTrace.each{ log.error it.toString() }
        }

        taskInterface
    }

    AnalysedTask computeInterfaces() {
        def analysedTask = new AnalysedTask(this)
        TimeDuration timestamp = null

        if (!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            analysedTask
        }
        showTaskInfo()

        if (!changedGherkinFiles.empty || !changedStepDefinitions.empty) {
            try {
                // resets repository to the state of the last commit to extract changes
                gitRepository.reset(commits?.last()?.hash)

                def initTime = new Date()
                // computes task interface based on the production code exercised by tests
                analysedTask.itest = testCodeParser.computeInterfaceForDoneTask(changedGherkinFiles, changedStepDefinitions, gitRepository.removedSteps)
                def endTime = new Date()
                use(TimeCategory) {
                    timestamp = endTime - initTime
                }
                analysedTask.itest.timestamp = timestamp
                registryCompilationErrors(analysedTask)

                //computes task text based in gherkin scenarios
                analysedTask.itext = super.computeTextBasedInterface()

                initTime = new Date()
                //computes real interface
                analysedTask.ireal = identifyProductionChangedFiles()
                endTime = new Date()
                use(TimeCategory) {
                    timestamp = endTime - initTime
                }
                analysedTask.ireal.timestamp = timestamp

                //it is only necessary in the evaluation study
                analysedTask.configureGems(gitRepository.localPath)

                // resets repository to last version
                gitRepository.reset()
            } catch(Exception ex){
                log.error "Error while computing task interfaces."
                log.error ex.message
                ex.stackTrace.each{ log.error it.toString() }
            }
        }

        analysedTask
    }

    def getCommitsQuantity() {
        commits.size()
    }

    def getGherkinTestQuantity() {
        def values = changedGherkinFiles*.changedScenarioDefinitions*.size().flatten()
        if (values.empty) 0
        else values.sum()
    }

    def getStepDefQuantity() {
        def values = changedStepDefinitions*.changedStepDefinitions*.size().flatten()
        if (values.empty) 0
        else values.sum()
    }

    /* When this method is called, there is no information about test code.
    That is, it is only checked if the task has some gherkin scenario or step definition changed by any commit.
    The test code is found when the task interface is computed, during another phase. This means this result may include
    scenarios that are not implemented. */
    def hasTest(){
        if(getGherkinTestQuantity()==0 /*&& getStepDefQuantity()==0*/) false
        else true
    }

}

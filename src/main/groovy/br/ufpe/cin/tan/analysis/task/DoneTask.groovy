package br.ufpe.cin.tan.analysis.task

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.analysis.itask.IReal
import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.analysis.itask.TaskInterface
import br.ufpe.cin.tan.commit.Commit
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.commit.change.stepdef.ChangedStepdefFile
import br.ufpe.cin.tan.commit.change.stepdef.StepdefManager
import br.ufpe.cin.tan.commit.change.unit.ChangedUnitTestFile
import br.ufpe.cin.tan.exception.CloningRepositoryException
import br.ufpe.cin.tan.test.ruby.routes.RoutesManager
import br.ufpe.cin.tan.util.Util
import gherkin.ast.Background
import gherkin.ast.Feature
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.util.logging.Slf4j
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.revwalk.RevCommit

/***
 * Represents a done task, that is, a task that contains production and test code. The code is published in a public
 * Git repository in GitHub. The task is used to evaluation study, to validate test-based task interfaces by comparing
 * them with real interfaces.
 */
@Slf4j
class DoneTask extends Task {

    RoutesManager routesManager
    static Random random = new Random()
    RevCommit lastCommit
    String lastHash
    List<Commit> commits
    List<Commit> commitsChangedGherkinFile
    List<Commit> commitsStepsChange
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
    String pathSufix
    int msgLimit

    DoneTask(String repositoryUrl, int id, List<String> shas) throws CloningRepositoryException {
        super(repositoryUrl, id)
        configureInitialTestData(shas)
    }

    DoneTask(String repositoryUrl, int id, List<String> shas, String last) throws CloningRepositoryException {
        super(repositoryUrl, id)
        lastHash = last
        configureInitialTestData(shas)
    }

    DoneTask(String repositoryUrl, int id, List<String> shas, boolean basic) throws CloningRepositoryException {
        super(repositoryUrl, id)
        if (basic) configureBasicData(shas)
        else configureInitialTestData(shas)
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
        def taskInterface = new ITest()
        if (hasNoCommits() || !hasTest()) return taskInterface
        showTaskInfo()

        try {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(lastHash)

            // computes task interface based on the production code exercised by tests
            def initTime = new Date()
            taskInterface = testCodeAnalyser.computeInterfaceForDoneTask(changedGherkinFiles, changedStepDefinitions, gitRepository.removedSteps)
            configureTimestamp(initTime, taskInterface)

            registryCompilationErrors(taskInterface)

            // resets repository to last version
            gitRepository.reset()

        } catch (Exception ex) {
            log.error "Error while computing test-based task interface."
            registryErrorMessage(ex)
        }

        taskInterface
    }

    @Override
    String computeTextBasedInterface() {
        def text = ""
        if (hasNoCommits() || !hasTest()) return text

        try {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(lastHash)

            //computes task text based in gherkin scenarios
            text = super.computeTextBasedInterface()

            // resets repository to last version
            gitRepository.reset()
        } catch (Exception ex) {
            log.error "Error while computing text-based task interface."
            registryErrorMessage(ex)
        }
        text
    }

    IReal computeRealInterface() {
        def taskInterface = new IReal()
        if (hasNoCommits()) return taskInterface

        try {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(lastHash)

            //computes real interface
            def initTime = new Date()
            taskInterface = identifyProductionChangedFiles()
            configureTimestamp(initTime, taskInterface)

            // resets repository to last version
            gitRepository.reset()
        } catch (Exception ex) {
            log.error "Error while computing real task interface."
            registryErrorMessage(ex)
        }

        taskInterface
    }

    List computeRandomInterface(int repetitions) {
        def randomInterfaces = []
        try {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(lastHash)

            //candidate files
            def currentFiles = Util.findAllProductionFiles(identifyAllProjectFiles())

            // resets repository to last version
            gitRepository.reset()

            (1..repetitions).each {
                def randomInterface = [] as Set

                //decide interface size
                def maxSize = currentFiles.size()
                def low = 1
                int high = maxSize + 1
                def randomSize = random.nextInt(high - low) + low

                //decide interface content
                while (randomInterface.size() < randomSize) {
                    def index = random.nextInt(maxSize)
                    randomInterface += currentFiles.getAt(index)
                }

                randomInterfaces.add(Util.filterFiles(randomInterface))
            }
        } catch (Exception ex) {
            log.error "Error while computing random interface."
            registryErrorMessage(ex)
        }
        randomInterfaces
    }

    List computeRandomInterfaceSortingEachFile(int repetitions) {
        def randomInterfaces = []
        try {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(lastHash)

            //candidate files
            def currentFiles = Util.findAllProductionFiles(identifyAllProjectFiles())

            // resets repository to last version
            gitRepository.reset()

            (1..repetitions).each {
                def randomInterface = [] as Set
                //decide if a file will be included or not: 0 - no, 1 - yes
                currentFiles.each { file ->
                    def low = 0
                    int high = 1 + 1
                    def randomDecision = random.nextInt(high - low) + low
                    if (randomDecision == 1) randomInterface += file
                }
                randomInterfaces.add(Util.filterFiles(randomInterface))
            }
        } catch (Exception ex) {
            log.error "Error while computing random interface."
            registryErrorMessage(ex)
        }
        randomInterfaces
    }

    AnalysedTask computeInterfaces() {
        def analysedTask = new AnalysedTask(this)
        if (hasNoCommits() || !hasTest()) return analysedTask
        showTaskInfo()

        try {
            // resets repository to the state of the last commit to extract changes
            gitRepository.reset(lastHash)

            // computes task interface based on the production code exercised by tests
            def initTime = new Date()
            analysedTask.itest = testCodeAnalyser.computeInterfaceForDoneTask(changedGherkinFiles,
                    changedStepDefinitions, gitRepository.removedSteps)
            configureTimestamp(initTime, analysedTask.itest)
            registryCompilationErrors(analysedTask)

            //computes task text based in gherkin scenarios
            analysedTask.itext = super.computeTextBasedInterface()

            //computes real interface
            initTime = new Date()
            analysedTask.ireal = identifyChangedFiles()
            configureTimestamp(initTime, analysedTask.ireal)

            //it is only necessary in the evaluation study
            analysedTask.configureGems(gitRepository.localPath)

            // resets repository to last version
            gitRepository.reset()
        } catch (Exception ex) {
            log.error "Error while computing task interfaces."
            registryErrorMessage(ex)
        }

        analysedTask
    }

    def getCommitsQuantity() {
        commits.size()
    }

    def getGherkinTestQuantity() {
        def values = changedGherkinFiles*.changedScenarioDefinitions*.size().flatten()
        if (values.empty) 0 else values.sum()
    }

    def getStepDefQuantity() {
        def values = changedStepDefinitions*.changedStepDefinitions*.size().flatten()
        if (values.empty) 0 else values.sum()
    }

    /* When this method is called, there is no information about test code.
    That is, it is only checked if the task has some gherkin scenario or step definition changed by any commit.
    The test code is found when the task interface is computed, during another phase. This means this result may include
    scenarios that are not implemented. */

    def hasTest() {
        if (getGherkinTestQuantity() == 0 /*&& getStepDefQuantity()==0*/) false
        else true
    }

    boolean hasMergeCommit() {
        def r = commits.findAll { it.merge }
        if (r.empty) false
        else true
    }

    private boolean hasNoCommits() {
        def isEmpty = false
        if (!commits || commits.empty) {
            log.warn "TASK ID: $id; NO COMMITS!"
            isEmpty = true
        }
        isEmpty
    }

    private void configureTimestamp(Date initTime, TaskInterface taskInterface) {
        TimeDuration timestamp = null
        def endTime = new Date()
        use(TimeCategory) {
            timestamp = endTime - initTime
        }
        taskInterface.timestamp = timestamp
    }

    private static registryErrorMessage(Exception ex) {
        log.error ex.message
        ex.stackTrace.each { log.error it.toString() }
    }

    private configureLastCommit() throws Exception {
        if (lastHash) {
            def revcommits = gitRepository.searchAllRevCommitsBySha(lastHash)
            if (revcommits) lastCommit = revcommits.first()
            else throw new Exception("Error while configuring last commit '$lastHash'")
        } else if (!commits.empty) {
            //the commits list is sorted by date, so the last commit is the newest one
            lastCommit = gitRepository.searchAllRevCommitsBySha(commits?.last()?.hash)?.first()
            lastHash = lastCommit.name
        }
        else throw new Exception("Error while configuring last commit. Does the task contain commits?")
    }

    private configureBasicData(List<String> shas) {
        msgLimit = 1000
        pathSufix = gitRepository.name + File.separator
        changedGherkinFiles = []
        changedStepDefinitions = []
        changedUnitFiles = []
        commits = gitRepository.searchCommitsBySha(testCodeAnalyser, *shas)
        commitsChangedGherkinFile = []
        commitsStepsChange = []
        configureLastCommit()
        routesManager = new RoutesManager(testCodeAnalyser.repositoryPath, lastHash)
        testCodeAnalyser.routesManager = routesManager
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
        if (msgs.length() > msgLimit) {
            commitMessage = msgs.toString().substring(0, msgLimit - 1) + " [TOO_LONG]"
        } else commitMessage = msgs
    }

    private configureInitialTestData(List<String> shas) {
        configureBasicData(shas)

        if (!commits.empty) {
            // identifies changed gherkin files and scenario definitions
            commitsChangedGherkinFile = commits?.findAll { it.gherkinChanges && !it.gherkinChanges.isEmpty() }
            extractGherkinChanges()

            // identifies changed step definitions
            commitsStepsChange = this.commits?.findAll { it.stepChanges && !it.stepChanges.isEmpty() }
            extractStepDefinitionChanges()
        } else {
            log.error "The task has no commits! Searched commits: "
            shas.each { log.error it.toString() }
        }
    }

    private extractStepDefinitionChanges() {
        def stepDefinitions = identifyChangedStepDefinitionFiles()
        def notFoundSteps = []
        def notFoundFiles = []
        List<ChangedStepdefFile> finalStepDefinitionsFilesSet = []

        stepDefinitions?.each { stepFile ->
            def content = gitRepository.extractFileContent(lastCommit, stepFile.path)
            StepdefManager stepdefManager = new StepdefManager(testCodeAnalyser)
            List<StepDefinition> stepDefs = stepdefManager.parseStepDefinitionFile(stepFile.path, content, lastCommit.name)
            if (stepDefs) {
                def regexes = stepDefs*.regex
                if (!regexes.empty) {
                    def initialSet = stepFile.changedStepDefinitions
                    def valid = initialSet.findAll { it.regex in regexes }
                    def finalSteps = stepDefs?.findAll { it.regex in valid*.regex }
                    def invalid = initialSet - valid
                    if (!invalid?.empty) notFoundSteps += [file: stepFile.path, steps: invalid*.regex]
                    if (!valid?.empty) {
                        def newStepDefFile = new ChangedStepdefFile(path: stepFile.path, changedStepDefinitions: finalSteps)
                        finalStepDefinitionsFilesSet += newStepDefFile
                    }
                }
            } else {
                notFoundFiles += stepFile.path
            }
        }

        if (!notFoundFiles.empty) {
            def text = "No long valid step definition files (task ${id}):\n"
            notFoundFiles?.each { text += it + "\n" }
            log.warn text
        }

        if (!notFoundSteps.empty) {
            def text = "No long valid step definitions (task ${id}):\n"
            notFoundSteps?.each { n ->
                text += "File: ${n.file}\nSteps:\n"
                n.steps?.each { text += it + "\n" }
            }
            log.warn text
        }

        changedStepDefinitions = finalStepDefinitionsFilesSet
    }

    private extractGherkinChanges() {
        def gherkinFiles = identifyChangedGherkinFiles()
        def notFoundScenarios = []
        def notFoundFiles = []
        List<ChangedGherkinFile> finalGherkinFilesSet = []

        gherkinFiles?.each { gherkinFile ->
            def result = gitRepository.parseGherkinFile(gherkinFile.path, lastCommit.name)
            Feature feature = result.feature
            if (feature) {
                def currentScenarios = feature?.children?.findAll { !(it instanceof Background) }*.name
                if (currentScenarios && !currentScenarios.empty) {
                    def initialSet = gherkinFile.changedScenarioDefinitions.findAll { !(it instanceof Background) }
                    def valid = initialSet.findAll { it.name in currentScenarios }
                    def validNames = valid*.name
                    def finalScenarios = feature?.children?.findAll {
                        !(it instanceof Background) && it.name in validNames && !it.steps.empty
                    }
                    def invalid = initialSet - valid
                    if (!invalid?.empty) notFoundScenarios += [file: gherkinFile.path, scenarios: invalid*.name]
                    if (!valid?.empty) {
                        def newGherkinFile = new ChangedGherkinFile(path: gherkinFile.path, feature: feature,
                                changedScenarioDefinitions: finalScenarios, featureFileText: result.content)
                        finalGherkinFilesSet += newGherkinFile
                    }
                }
            } else {
                notFoundFiles += gherkinFile.path
            }
        }

        if (!notFoundFiles.empty) {
            def text = "No long valid gherkin files (task ${id}):\n"
            notFoundFiles?.each { text += it + "\n" }
            log.warn text
        }

        if (!notFoundScenarios.empty) {
            def text = "No long valid scenarios (task ${id}):\n"
            notFoundScenarios?.each { n ->
                text += "File: ${n.file}\nScenarios:\n"
                n.scenarios?.each { text += it + "\n" }
            }
            log.warn text
        }

        changedGherkinFiles = finalGherkinFilesSet
    }

    private List<ChangedGherkinFile> identifyChangedGherkinFiles() {
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

    private List<ChangedStepdefFile> identifyChangedStepDefinitionFiles() {
        List<ChangedStepdefFile> stepFiles = []
        commitsStepsChange?.each { commit -> //commits sorted by date
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

    private List<String> identifyAllProjectFiles() {
        def canonicalPath = Util.getRepositoriesCanonicalPath()
        def currentFiles = Util.findFilesFromDirectory(gitRepository.localPath).collect {
            it - (canonicalPath + gitRepository.name + File.separator)
        }
        currentFiles
    }

    private IReal identifyProductionChangedFiles() {
        def currentFiles = identifyAllProjectFiles()

        /* Identifies all changed files by task */
        def filenames = commits*.coreChanges*.path?.flatten()?.unique()?.sort()

        /* Identifies all changed files that exist when the task was finished. It is necessary because a task might
        remove a file and other (concurrent) task might add it back.*/
        def validFiles = filenames.findAll { it in currentFiles }

        /* Constructs task interface object */
        organizeProductionFiles(validFiles)
    }

    /* calcula IReal como sendo o conjunto dos arquivos adicionados, alterados ou removidos pela tarefa, sem filtros. */

    private IReal identifyChangedFiles() {
        def files = commits*.files?.flatten()?.unique()

        /* Constructs task interface object */
        organizeProductionFiles(files)
    }

    private IReal organizeProductionFiles(productionFiles) {
        def taskInterface = new IReal()

        //filtering result to only identify view and/or controller files
        productionFiles = Util.filterFiles(productionFiles)

        productionFiles?.each { file ->
            def path = pathSufix + file
            if (path.contains(Util.VIEWS_FILES_RELATIVE_PATH)) {
                def index = path.lastIndexOf(File.separator)
                taskInterface.classes += [name: path.substring(index + 1), file: path]
            } else {
                def aux = testCodeAnalyser.getClassForFile(path)
                taskInterface.classes += [name: aux, file: path]
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
                def devDates = commits*.date?.flatten()?.sort()
                if (devDates) {
                    devDates = devDates.collect { new Date(it * 1000).clearTime() }.unique()
                    def last = devDates.last()
                    def first = devDates.first()
                    days = (last - first).days + 1
                } else days = 0
            }
        }
    }

    private extractRemovedFiles() {
        def changes = commits*.coreChanges?.flatten()?.findAll { it.type == DiffEntry.ChangeType.DELETE }
        removedFiles = changes?.collect { it.path }?.unique()?.sort()
    }

    private showTaskInfo() {
        log.info "TASK ID: $id"
        log.info "COMMITS: ${commits*.hash}"
        log.info "NEWEST COMMIT: ${lastCommit.name}"
        log.info "COMMITS CHANGED GHERKIN FILE: ${commitsChangedGherkinFile*.hash}"
        log.info "COMMITS CHANGED STEP DEFINITION FILE: ${commitsStepsChange*.hash}"
    }

    private registryCompilationErrors(AnalysedTask task) {
        ITest temp = task.itest
        registryCompilationErrors(temp)
        task.itest = temp
    }

    private registryCompilationErrors(ITest itest) {
        def finalErrorSet = []
        def errors = itest.compilationErrors

        def gherkinErrors = errors.findAll { Util.isGherkinFile(it.path) }
        gherkinErrors?.each { error ->
            def result = gitRepository.parseGherkinFile(error.path, lastHash)
            if (!result.feature) finalErrorSet += error
        }

        def rubyErrors = errors - gherkinErrors
        rubyErrors?.each { error ->
            def hasError = testCodeAnalyser.hasCompilationError(error.path)
            if (hasError) finalErrorSet += error
        }

        def formatedResult = []
        finalErrorSet.each { error ->
            def file = error.path
            def index = error.path.indexOf(gitRepository.localPath)
            def name = index >= 0 ? file.substring(index) - (gitRepository.localPath + File.separator) : file
            formatedResult += [path: name, msgs: error.msgs]
        }

        itest.compilationErrors = formatedResult
    }
}

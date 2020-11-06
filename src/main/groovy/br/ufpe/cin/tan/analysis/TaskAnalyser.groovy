package br.ufpe.cin.tan.analysis

import br.ufpe.cin.tan.analysis.data.TaskImporter
import br.ufpe.cin.tan.analysis.data.csvExporter.*
import br.ufpe.cin.tan.analysis.task.DoneTask
import br.ufpe.cin.tan.test.AcceptanceTest
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util
import groovy.util.logging.Slf4j

@Slf4j
class TaskAnalyser {

    File file
    TaskImporter taskImporter

    /* after task analysis */
    List<AnalysedTask> validTasks
    List<AnalysedTask> invalidTasks
    List<AnalysedTask> relevantTasks

    /* analysis strategy */
    int taskLimit
    boolean incrementalAnalysis

    /* Output files*/
    String evaluationFile
    String filteredFile
    String similarityRelevantFile
    String similarityValidFile
    String testFile
    String relevantTasksFile
    String relevantTasksDetailsFile
    String relevantTasksControllerFile
    String validTasksFile
    String validTasksDetailsFile
    String validTasksControllerFile
    String invalidTasksFile

    TaskAnalyser(String tasksFile) {
        this(tasksFile, 0)
    }

    TaskAnalyser(String tasksFile, int taskLimit) {
        this.taskLimit = taskLimit
        file = new File(tasksFile)
        log.info "<  Analysing tasks from '${file.path}'  >"
        taskImporter = new TaskImporter(file)
        decideAnalysisStrategy()
        configureOutputFiles()
        validTasks = []
        invalidTasks = []
        relevantTasks = []
        log.info "<  Restrict gherkin changes: '${Util.RESTRICT_GHERKIN_CHANGES}'  >"
        log.info "<  Filter when steps: '${Util.WHEN_FILTER}'  >"
        log.info "<  Analyse views: '${Util.VIEW_ANALYSIS}'  >"
    }

    TaskAnalyser(TaskImporter taskImporter, String tasksFile, int taskLimit) {
        this.taskLimit = taskLimit
        file = new File(tasksFile)
        log.info "<  Analysing tasks from '${file.path}'  >"
        this.taskImporter = taskImporter
        this.taskImporter.printInfo()
        decideAnalysisStrategy()
        configureOutputFiles()
        validTasks = []
        invalidTasks = []
        relevantTasks = []
        log.info "<  Restrict gherkin changes: '${Util.RESTRICT_GHERKIN_CHANGES}'  >"
    }

    def analyseAll() {
        analysePrecisionAndRecall()
        //analyseSimilarity()
    }

    def analysePrecisionAndRecall() {
        if (incrementalAnalysis) generateResultPartially()
        else generateResult()
        //we wanna tasks that are valid for controller filtering
        relevantTasks = filterRelevantTasksByTestsAndEmptyItest()
        exportTasks()
        exportAllDetailedInfo()
        //filterResult() //no novo estudo não filtramos resultados por controller
    }

    def getIrrelevantImportedTasksId() {
        taskImporter.bigTasks?.collect { it[1] as int } + taskImporter.notPtImportedTasks?.collect { it[1] as int }
        +taskImporter.falsePtTasks?.collect { it.id }
    }

    def getInvalidTasksId() {
        invalidTasks?.collect { it.doneTask.id }
    }

    def getAnalyzedTasks() {
        (validTasks + invalidTasks + relevantTasks).unique().collect { it.doneTask.id }.sort()
    }

    def filterRelevantTasksByTestsAndEmptyItest() {
        def entries = organizeTests()
        def selected = entries.collect { it.task }
        def filtered = relevantTasks?.findAll { (it.doneTask.id in selected) && !it.itestIsEmpty() }?.sort {
            it.doneTask.id
        }
        def excluded = relevantTasks - filtered
        log.info "Relevant tasks that do not satisfy all selection criteria (${excluded.size()}): ${excluded*.doneTask.id}"
        filtered
    }

    def backupOutputCsv(int index) {
        CsvUtil.copy(invalidTasksFile,
                invalidTasksFile - ConstantData.CSV_FILE_EXTENSION + index + ConstantData.CSV_FILE_EXTENSION)
    }

    private exportInvalidTasks() {
        if (invalidTasks.empty) log.info "There is no invalid tasks to save!"
        else {
            EvaluationExporter evaluationExporter = new EvaluationExporter(invalidTasksFile, invalidTasks, false)
            evaluationExporter.save()
            log.info "Invalid tasks were saved in ${invalidTasksFile}."
        }
    }

    private organizeTests() {
        def result = []
        relevantTasks.each { task ->
            def tests = extractTests(task)
            result += [task: task.doneTask.id, tests: tests]
        }
        result
    }

    private static extractTests(AnalysedTask task) {
        def scenarios = []
        Set<AcceptanceTest> tests = task.itest.foundAcceptanceTests
        tests.each { test ->
            def lines = test.scenarioDefinition*.location.line
            scenarios += [file: test.gherkinFilePath, lines: lines.sort()]
        }
        scenarios
    }

    private configureOutputFiles() {
        def projectFolder = ConstantData.DEFAULT_EVALUATION_FOLDER + File.separator + (file.name - ConstantData.CSV_FILE_EXTENSION)

        File folder = new File(projectFolder)
        if (!folder.exists()) folder.mkdir()

        evaluationFile = folder.path + File.separator + file.name

        def name = evaluationFile - ConstantData.CSV_FILE_EXTENSION
        filteredFile = name + ConstantData.FILTERED_FILE_SUFIX
        similarityRelevantFile = name + "-relevant" + ConstantData.SIMILARITY_FILE_SUFIX
        similarityValidFile = name + "-valid" + ConstantData.SIMILARITY_FILE_SUFIX
        testFile = name + ConstantData.TEST_EXECUTION_FILE_SUFIX
        relevantTasksFile = name + ConstantData.RELEVANT_TASKS_FILE_SUFIX
        relevantTasksDetailsFile = name + ConstantData.RELEVANT_TASKS_DETAILS_FILE_SUFIX
        relevantTasksControllerFile = relevantTasksFile - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        validTasksDetailsFile = name + ConstantData.VALID_TASKS_DETAILS_FILE_SUFIX
        validTasksFile = name + ConstantData.VALID_TASKS_FILE_SUFIX
        validTasksControllerFile = validTasksFile - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        invalidTasksFile = name + ConstantData.INVALID_TASKS_FILE_SUFIX
    }

    private configureDefaultTaskLimit() {
        if (taskLimit <= 0) {
            taskLimit = ConstantData.DEFAULT_TASK_LIMIT
            String message = "Because no task limit was defined, the default value '${taskLimit}' will be used." +
                    "\nIt is necessary for incremental analysis works fine."
            log.warn message
        } else log.info "TASK LIMIT: $taskLimit"
    }

    private decideAnalysisStrategy() {
        if (taskImporter.importedTasks.size() > 200) {
            incrementalAnalysis = true
            configureDefaultTaskLimit()
        } else {
            incrementalAnalysis = false
        }
        log.info "< Analysis strategy: Task limit = $taskLimit (value <=0 implies all tasks); incremental analysis = $incrementalAnalysis >"
    }

    private generateResultPartially() {
        def allTasksToAnalyse = taskImporter.ptImportedTasks.size()
        def groups = allTasksToAnalyse.intdiv(100)
        def remainder = allTasksToAnalyse % 100

        log.info "Tasks to analyse: ${allTasksToAnalyse}"
        log.info "Groups of 100 units: ${groups} + Remainder: ${remainder}"

        def i = 0, j = 100, analysedGroups = 0, counter = 0
        while (analysedGroups < groups && relevantTasks.size() < taskLimit) {
            counter++
            taskImporter.extractPtTasks(i, j)
            i = j
            j += 100
            analysedGroups++
            printPartialDataAnalysis(counter)
            analyseLimitedTasks()
        }

        if (remainder > 0 && relevantTasks.size() < taskLimit) {
            log.info "Last try to find relevant tasks!"
            taskImporter.extractPtTasks(i, i + remainder)
            printPartialDataAnalysis(++counter)
            analyseLimitedTasks()
        }
    }

    private printPartialDataAnalysis(round) {
        def candidatesSize = taskImporter.candidateTasks.size()
        def falsePtTasksSize = taskImporter.falsePtTasks.size()
        log.info "Extracted tasks at round $round: ${candidatesSize + falsePtTasksSize}"
        log.info "Candidate tasks (have production code and candidate gherkin scenarios): ${candidatesSize}"
        log.info "Seem to have test but actually do not (do not have candidate gherkin scenarios): ${falsePtTasksSize}"
        log.info "Valid tasks so far: ${validTasks.size()}"
        log.info "Relevant tasks so far: ${relevantTasks.size()}"
    }

    private analyseLimitedTasks() {
        def counter = 0
        for (int j = 0; j < taskImporter.candidateTasks.size() && relevantTasks.size() < taskLimit; j++) {
            counter++
            def candidate = taskImporter.candidateTasks.get(j)
            analyse(candidate)
        }
        log.info "(analyseLimitedTasks) Task interfaces were computed for ${counter} tasks!"
        log.info "(analyseLimitedTasks) (From candidates) Invalid tasks: ${invalidTasks.size()}"
        log.info "(analyseLimitedTasks) (From candidates) Valid tasks: ${validTasks.size()}"
        log.info "(analyseLimitedTasks) (From candidates) Relevant tasks (valid but with no empty ITest): ${relevantTasks.size()}"
    }

    private analyse(DoneTask task) {
        def analysedTask = task.computeInterfaces()
        if (analysedTask.isValid()) {
            validTasks += analysedTask
            if (analysedTask.isRelevant()) relevantTasks += analysedTask
        }
        else invalidTasks += analysedTask
    }

    private analyseAllTasks() {
        taskImporter.candidateTasks.each { analyse(it) }
        log.info "Task interfaces were computed for ${taskImporter.candidateTasks.size()} tasks!"
        log.info "(From candidates) Invalid tasks: ${invalidTasks.size()}"
        log.info "(From candidates) Valid tasks: ${validTasks.size()}"
        log.info "(From candidates) Relevant tasks (valid but with no empty ITest): ${relevantTasks.size()}"
    }

    private generateResult() {
        taskImporter.extractPtTasks()
        log.info "Candidate tasks (have production code and candidate gherkin scenarios): ${taskImporter.candidateTasks.size()}"
        log.info "Seem to have test but actually do not (do not have candidate gherkin scenarios): ${taskImporter.falsePtTasks.size()}"

        if (taskLimit > 0) analyseLimitedTasks()
        else analyseAllTasks()
    }

    private exportValidAndRelevantTasks() {
        if (relevantTasksFile.empty) log.info "There is no relevant tasks to save!"
        else {
            def relevantTaskExporter = new ValidTaskExporter(relevantTasksFile, relevantTasks)
            relevantTaskExporter.save()
            EvaluationExporter evaluationExporter = new EvaluationExporter(relevantTasksDetailsFile, relevantTasks)
            evaluationExporter.save()
            organizeResultForTestExecution()
            log.info "Relevant tasks were saved in ${relevantTasksFile}, ${relevantTasksDetailsFile} and ${testFile}."
        }

        if (validTasksFile.empty) log.info "There is no valid tasks to save!"
        else {
            def relevantTaskExporter = new ValidTaskExporter(validTasksFile, validTasks)
            relevantTaskExporter.save()
            EvaluationExporter evaluationExporter = new EvaluationExporter(validTasksDetailsFile, validTasks)
            evaluationExporter.save()
            log.info "Valid tasks were saved in ${validTasksDetailsFile}."
        }
    }

    private exportTasks() {
        exportValidAndRelevantTasks()
        exportInvalidTasks()
    }

    private exportAllDetailedInfo() {
        if (!validTasks.empty) {
            EvaluationExporter evaluationExporter = new EvaluationExporter(evaluationFile, validTasks)
            evaluationExporter.save()
        }
    }

    private analyseSimilarity() {
        if (!relevantTasksFile.empty) {
            log.info "<  Analysing similarity among tasks from '$relevantTasksFile'  >"
            SimilarityExporter similarityExporter = new SimilarityExporter(relevantTasksFile, similarityRelevantFile)
            similarityExporter.save()
            log.info "The results were saved!"
        }
        if (!validTasksFile.empty) {
            log.info "<  Analysing similarity among tasks from '$validTasksFile'  >"
            SimilarityExporter similarityExporter = new SimilarityExporter(validTasksFile, similarityValidFile)
            similarityExporter.save()
            log.info "The results were saved!"
        }

    }

    /* filter results to only consider controller files (via csv) - TEMPORARY CODE */

    private filterResult() {
        ControllerFilterExporter controllerFilterExporter = new ControllerFilterExporter(relevantTasksFile,
                relevantTasksControllerFile)
        controllerFilterExporter.save()
        controllerFilterExporter = new ControllerFilterExporter(validTasksFile, validTasksControllerFile)
        controllerFilterExporter.save()
    }

    private organizeResultForTestExecution() {
        TestExecutionExporter testExecutionExporter = new TestExecutionExporter(testFile, relevantTasks)
        testExecutionExporter.save()
    }

}
package taskAnalyser

import groovy.util.logging.Slf4j
import taskAnalyser.output.ControllerFilterExporter
import taskAnalyser.output.EvaluationExporter
import taskAnalyser.output.EvaluationOrganizerExporter
import taskAnalyser.output.SimilarityExporter
import taskAnalyser.output.TestExecutionExporter
import taskAnalyser.task.AnalysedTask
import taskAnalyser.task.AnalysisResult
import taskAnalyser.task.DoneTask
import util.ConstantData
import util.CsvUtil

@Slf4j
class TaskAnalyser {

    File file
    String evaluationFile
    String organizedFile
    String filteredFile
    String similarityFile
    String similarityOrganizedFile
    String testFile
    List<DoneTask> tasks
    AnalysisResult analysisResult

    TaskAnalyser(String tasksFile) {
        file = new File(tasksFile)
        evaluationFile = ConstantData.DEFAULT_EVALUATION_FOLDER + File.separator + file.name
        def name = evaluationFile - ConstantData.CSV_FILE_EXTENSION
        organizedFile = name + ConstantData.ORGANIZED_FILE_SUFIX
        filteredFile = name + ConstantData.FILTERED_FILE_SUFIX
        similarityFile = name + ConstantData.SIMILARITY_FILE_SUFIX
        similarityOrganizedFile = name + ConstantData.SIMILARITY_ORGANIZED_FILE_SUFIX
        testFile = name + ConstantData.TEST_EXECUTION_FILE_SUFIX
        tasks = []
    }

    private computeTaskData() {
        List<AnalysedTask> analysedTasks = []
        def gherkinTasks = []
        def stepTasks = []

        tasks?.each { task ->
            def analysedTask = task.computeInterfaces()
            if (!task.changedStepDefinitions.empty) stepTasks += task.id
            if (!task.changedGherkinFiles.empty) gherkinTasks += task.id
            analysedTasks += analysedTask
        }

        def stepCounter = stepTasks.unique().size()
        def gherkinCounter = gherkinTasks.unique().size()
        def testsCounter = (stepTasks + gherkinTasks).unique().size()

        log.info "Number of tasks that contain step definitions: $stepCounter"
        log.info "Number of tasks that changed Gherkin files: $gherkinCounter"
        log.info "Number of tasks that contain tests: $testsCounter"

        analysisResult = new AnalysisResult(stepCounter:stepCounter, gherkinCounter:gherkinCounter,
                testsCounter:testsCounter, validTasks:analysedTasks, url: tasks?.first()?.testCodeParser?.repositoryPath,
                allTasks: tasks.size())
    }

    private generateResult() {
        log.info "<  Analysing tasks from '${file.path}'  >"
        extractProductionAndTestTasks()
        if(tasks && !tasks.empty) {
            computeTaskData()
            EvaluationExporter evaluationExporter = new EvaluationExporter(evaluationFile, analysisResult)
            evaluationExporter.save()
        }
        log.info "The results were saved!"
    }

    def analyseAll() {
        generateResult()
        organizeResultForTestExecution()
        filterResult() //TEMPORARY CODE
        organizeResultForSimilarityAnalysis()
        analyseSimilarity()
    }

    def analysePrecisionAndRecall() {
        generateResult()
        organizeResultForTestExecution()
        filterResult() //TEMPORARY CODE
        organizeResult()
    }

    /***
     * Extracts all tasks in a CSV file that changed production and test files.
     * @filename cvs file organized by 7 columns: "index","repository_url","task_id","commits_hash",
     *           "changed_production_files","changed_test_files","commits_message".
     * @return a list of tasks.
     */
    private extractProductionAndTestTasks() {
        List<String[]> entries = CsvUtil.read(file.path)?.unique { it[2] } //bug: input csv can contain duplicated values
        entries.remove(0)

        List<String[]> relevantEntries = entries.findAll { ((it[4] as int)>0 && (it[5] as int)>0)  ||
                ((it[4] as int)>50 && (it[5] as int)==0)} //avoiding the exclusion of corrupted tasks at the entry csv
        def invalid = entries.size() - relevantEntries.size()
        List<DoneTask> tasks = []
        def tasksThatSeemsToHaveTest = []

        try {
            relevantEntries.each { entry ->
                def hashes = entry[3].tokenize(',[]')*.trim()
                def task = new DoneTask(entry[1], entry[2], hashes)
                if(task.hasTest()) tasks += task
                else tasksThatSeemsToHaveTest += entry[2]
            }
        } catch (Exception ex) {
            log.error ex.message
            ex.stackTrace.each{ log.error it.toString() }
            this.tasks = []
        }

        log.info "Number of invalid tasks: ${invalid}"
        log.info "Number of extracted valid tasks: ${tasks.size()}"

        /* Tasks that had changed test code but when the task is concluded, its Gherkin scenarios or step code definitions
        * were removed by other tasks*/
        log.info "Tasks that seem to have test but actually do not: ${tasksThatSeemsToHaveTest.size()}"
        tasksThatSeemsToHaveTest.each{ log.info it }

        this.tasks = tasks.sort { it.id }
    }

    def organizeResult() {
        EvaluationOrganizerExporter evaluationOrganizerExporter = new EvaluationOrganizerExporter(evaluationFile,
                organizedFile, null)
        evaluationOrganizerExporter.save()
    }

    def organizeResultForSimilarityAnalysis() {
        log.info "<  Organizing tasks from '$evaluationFile'  >"
        EvaluationOrganizerExporter evaluationOrganizerExporter = new EvaluationOrganizerExporter(evaluationFile,
                organizedFile, filteredFile)
        evaluationOrganizerExporter.save()
        log.info "The results were saved!"
    }

    def analyseSimilarity() {
        log.info "<  Analysing similarity among tasks from '$filteredFile'  >"
        SimilarityExporter similarityExporter = new SimilarityExporter(filteredFile, similarityFile, similarityOrganizedFile)
        similarityExporter.save()
        log.info "The results were saved!"

        log.info "<  Organizing tasks from '$similarityFile'  >"
        similarityExporter.saveOrganized()
        log.info "The results were saved!"
    }

    /* filter results to only consider controller files (via csv) - TEMPORARY CODE */
    def filterResult() {
        ControllerFilterExporter controllerFilterExporter = new ControllerFilterExporter(evaluationFile)
        controllerFilterExporter.save()
    }

    def organizeResultForTestExecution(){
        TestExecutionExporter testExecutionExporter = new TestExecutionExporter(testFile, analysisResult)
        testExecutionExporter.save()
    }

}

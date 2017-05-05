package taskAnalyser

import groovy.util.logging.Slf4j
import taskAnalyser.output.ControllerFilterExporter
import taskAnalyser.output.EvaluationExporter
import taskAnalyser.output.RelevantTaskExporter
import taskAnalyser.output.SimilarityExporter
import taskAnalyser.output.TestExecutionExporter
import taskAnalyser.task.AnalysedTask
import taskAnalyser.task.DoneTask
import util.ConstantData
import util.CsvUtil

@Slf4j
class TaskAnalyser {

    static int URL_INDEX = 1
    static int TASK_INDEX = 2
    static int COMMITS_SIZE_INDEX = 3
    static int HASHES_INDEX = 4
    static int PROD_FILES_INDEX = 5
    static int TEST_FILES_INDEX = 6

    int taskLimit

    File file
    String evaluationFile
    String organizedFile
    String filteredFile
    String similarityFile
    String similarityOrganizedFile
    String testFile
    String relevantTasksFile
    String relevantTasksDetailsFile
    String url

    /* before task analysis */
    List<String> ptCandidateTasks //tasks extracted from the input CSV file that seems that have production and test code
    int allInputTasks
    int notPtTasks //tasks extracted from the input CSV file that do not have production and test code
    List<String> falsePtTasks //tasks that have production and test code, but not gherkin scenarios
    List<DoneTask> candidateTasks //tasks extracted from the input CSV file that changed production code and gherkin scenarios

    /* after task analysis */
    List<AnalysedTask> analysedTasks
    RelevantTaskExporter relevantTaskExporter

    TaskAnalyser(String tasksFile, int taskLimit){
        this(tasksFile)
        this.taskLimit = taskLimit
    }

    TaskAnalyser(String tasksFile) {
        file = new File(tasksFile)
        def projectFolder = ConstantData.DEFAULT_EVALUATION_FOLDER + File.separator + (file.name - ConstantData.CSV_FILE_EXTENSION)
        File folder = new File(projectFolder)
        if(!folder.exists()) folder.mkdir()
        evaluationFile = folder.path + File.separator + file.name
        def name = evaluationFile - ConstantData.CSV_FILE_EXTENSION
        organizedFile = name + ConstantData.ORGANIZED_FILE_SUFIX
        filteredFile = name + ConstantData.FILTERED_FILE_SUFIX
        similarityFile = name + ConstantData.SIMILARITY_FILE_SUFIX
        similarityOrganizedFile = name + ConstantData.SIMILARITY_ORGANIZED_FILE_SUFIX
        testFile = name + ConstantData.TEST_EXECUTION_FILE_SUFIX
        relevantTasksFile = name + ConstantData.RELEVANT_TASKS_FILE_SUFIX
        relevantTasksDetailsFile = name + ConstantData.RELEVANT_TASKS_DETAILS_FILE_SUFIX
        reset()
    }

    private reset(){
        allInputTasks = 0
        notPtTasks = 0
        ptCandidateTasks = []
        falsePtTasks = []
        candidateTasks = []
        analysedTasks = []
        url = ""
    }

    /***
     * Extracts all tasks in a CSV file that changed production and test files.
     */
    private extractProductionAndTestTasks() {
        List<String[]> entries = CsvUtil.read(file.path)?.unique { it[TASK_INDEX] } //bug: input csv can contain duplicated values
        entries.remove(0)

        allInputTasks = entries.size()
        if(allInputTasks>0) url = entries.first()[URL_INDEX]

        List<String[]> ptEntries = entries.findAll { ((it[PROD_FILES_INDEX] as int)>0 && (it[TEST_FILES_INDEX] as int)>0) }
        notPtTasks = entries.size() - ptEntries.size()

        List<DoneTask> tasks = []
        try {
            ptEntries.each { entry ->
                def hashes = entry[HASHES_INDEX].tokenize(',[]')*.trim()
                def task = new DoneTask(entry[URL_INDEX], entry[TASK_INDEX], hashes)
                if(task.hasTest()) tasks += task
                else falsePtTasks += entry[TASK_INDEX]
            }
        } catch (Exception ex) {
            log.error "Error while extracting tasks from CSV file."
            ex.stackTrace.each{ log.error it.toString() }
            candidateTasks = []
        }
        candidateTasks = tasks.sort { it.id }
    }

    private analyseLimitedTasks(){
        if(candidateTasks && !candidateTasks.empty) {
            for(int j=0; j<candidateTasks.size() && analysedTasks.size()<taskLimit; j++){
                def candidate = candidateTasks.get(j)
                def analysedTask = candidate.computeInterfaces()
                if(analysedTask.isValid()) analysedTasks += analysedTask
            }
        }
    }

    private analyseAllTasks() {
        if(candidateTasks && !candidateTasks.empty) {
            candidateTasks.each { analysedTasks += it.computeInterfaces() }
        }
    }

    private generateResult() {
        reset()

        log.info "<  Analysing tasks from '${file.path}'  >"
        extractProductionAndTestTasks()
        log.info "All tasks extracted from '${file.path}': ${allInputTasks}"
        log.info "Invalid tasks (do not have production and test code): ${notPtTasks}"
        log.info "Candidate tasks (have production code and candidate gherkin scenarios): ${candidateTasks.size()}"
        log.info "Seem to have test but actually do not (do not have candidate gherkin scenarios): ${falsePtTasks.size()}"

        if(taskLimit>0) analyseLimitedTasks()
        else analyseAllTasks()

        log.info "Task interfaces were computed for ${candidateTasks.size()} tasks!"
    }

    private exportRelevantTasks(){
        if(!analysedTasks.empty){
            relevantTaskExporter = new RelevantTaskExporter(relevantTasksFile, analysedTasks)
            relevantTaskExporter.save()
            def tasks = relevantTaskExporter.relevantTasks + relevantTaskExporter.emptyITestTasks
            EvaluationExporter evaluationExporter = new EvaluationExporter(relevantTasksDetailsFile, tasks)
            evaluationExporter.save()
            organizeResultForTestExecution()
        }
    }

    private exportAllDetailedInfo(){
        if(!analysedTasks.empty){
            EvaluationExporter evaluationExporter = new EvaluationExporter(evaluationFile, analysedTasks)
            evaluationExporter.save()
        }
    }

    private analyseSimilarity() {
        log.info "<  Analysing similarity among tasks from '$relevantTasksFile'  >"
        SimilarityExporter similarityExporter = new SimilarityExporter(relevantTasksFile, similarityFile)
        similarityExporter.save()
        log.info "The results were saved!"
    }

    /* filter results to only consider controller files (via csv) - TEMPORARY CODE */
    private filterResult() {
        ControllerFilterExporter controllerFilterExporter = new ControllerFilterExporter(relevantTasksFile)
        controllerFilterExporter.save()
    }

    private organizeResultForTestExecution(){
        TestExecutionExporter testExecutionExporter = new TestExecutionExporter(testFile, relevantTaskExporter.relevantTasks)
        testExecutionExporter.save()
    }

    private commonSteps(){
        generateResult()
        exportRelevantTasks()
        exportAllDetailedInfo()
        filterResult() //TEMPORARY CODE
    }

    def analyseAll() {
        commonSteps()
        analyseSimilarity()
    }

    def analysePrecisionAndRecall() {
        commonSteps()
    }
}

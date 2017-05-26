package br.ufpe.cin.tan.analysis

import br.ufpe.cin.tan.analysis.task.DoneTask
import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.analysis.data.ControllerFilterExporter
import br.ufpe.cin.tan.analysis.data.EvaluationExporter
import br.ufpe.cin.tan.analysis.data.RelevantTaskExporter
import br.ufpe.cin.tan.analysis.data.SimilarityExporter
import br.ufpe.cin.tan.analysis.data.TestExecutionExporter
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil

@Slf4j
class TaskAnalyser {

    /* before task analysis */
    List<String[]> importedTasks
    List<String[]> ptImportedTasks
    List<String[]> notPtImportedTasks
    //tasks extracted from the input CSV file that do not have production and test code
    List<DoneTask> falsePtTasks //tasks that have production and test code, but not gherkin scenarios
    List<DoneTask> candidateTasks //tasks extracted from the input CSV file that changed production code and gherkin scenarios

    /* after task analysis */
    List<AnalysedTask> analysedTasks
    List<AnalysedTask> invalidTasks
    RelevantTaskExporter relevantTaskExporter

    /* analysis strategy */
    int taskLimit
    boolean incrementalAnalysis

    /* Output files*/
    File file
    String evaluationFile
    String organizedFile
    String filteredFile
    String similarityFile
    String similarityOrganizedFile
    String testFile
    String relevantTasksFile
    String relevantTasksDetailsFile
    String invalidTasksFile
    String url

    int URL_INDEX = 0
    int TASK_INDEX = 1
    int HASHES_INDEX = 3
    int PROD_FILES_INDEX = 4
    int TEST_FILES_INDEX = 5

    TaskAnalyser(String tasksFile){
        this(tasksFile, 0)
    }

    TaskAnalyser(String tasksFile, int taskLimit) {
        this.taskLimit = taskLimit
        file = new File(tasksFile)

        log.info "<  Analysing tasks from '${file.path}'  >"
        importTasksFromCsv()
        log.info "All tasks extracted from '${file.path}': ${importedTasks.size()}"

        decideAnalysisStrategy()
        configureOutputFiles()
    }

    def analyseAll() {
        analysePrecisionAndRecall()
        analyseSimilarity()
    }

    def analysePrecisionAndRecall() {
        if(incrementalAnalysis) generateIncrementalResult()
        else generateResult()
        exportTasks()
        exportAllDetailedInfo()
        filterResult() //TEMPORARY CODE
    }

    private configureOutputFiles(){
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
        invalidTasksFile = name + ConstantData.INVALID_TASKS_FILE_SUFIX
    }

    private configureDefaultTaskLimit(){
        if(taskLimit <= 0) {
            taskLimit = 10
            String message = "Because no task limit was defined, the default value '${taskLimit}' will be used." +
                    "\nIt is necessary for incremental analysis works fine."
            log.warn message
        } else log.info "TASK LIMIT: $taskLimit"
    }

    private decideAnalysisStrategy(){
        if(importedTasks.size()>200) {
            incrementalAnalysis = true
            configureDefaultTaskLimit()
        }
        else incrementalAnalysis = false
    }

    private resetTasks(){
        ptImportedTasks = []
        notPtImportedTasks = []
        falsePtTasks = []
        candidateTasks = []
        analysedTasks = []
        invalidTasks = []
        url = ""
    }

    private extractAllPtTasks(List<String[]> ptEntries){
        try {
            ptEntries.each { entry ->
                def hashes = entry[HASHES_INDEX].tokenize(',[]')*.trim()
                def task = new DoneTask(entry[URL_INDEX], entry[TASK_INDEX], hashes)
                if(task.hasTest()) candidateTasks += task
                else falsePtTasks += task
            }
        } catch (Exception ex) {
            log.error "Error while extracting tasks from CSV file."
            ex.stackTrace.each{ log.error it.toString() }
            candidateTasks = []
        }
        candidateTasks = candidateTasks.sort { it.id }
    }

    private extractPtTasksPartially(List<String[]> ptEntries) {
        def value = ptEntries.size()
        def groups = value.intdiv(100)
        def r = value%100
        log.info "Tasks to analyse: ${value}"
        log.info "Groups of 100 units: ${groups} + Remainder: ${r}"

        def i = 0, j = 100, analysedGroups = 0
        while(analysedGroups<groups && analysedTasks.size()<taskLimit) {
            candidateTasks = []
            List<String[]> entries = ptEntries.subList(i, j)
            extractAllPtTasks(entries)
            i=j
            j+=100
            analysedGroups++
            printPartialDataAnalysis()
            analyseLimitedTasks()
        }

        if(r>0 && analysedTasks.size()<taskLimit){
            log.info "Last try to find valid tasks!"
            candidateTasks = []
            List<String[]> entries = ptEntries.subList(i, i+r)
            extractAllPtTasks(entries)
            printPartialDataAnalysis()
            analyseLimitedTasks()
        }

    }

    private printPartialDataAnalysis(){
        log.info "Candidate tasks (have production code and candidate gherkin scenarios) until now: ${candidateTasks.size()}"
        log.info "Seem to have test but actually do not (do not have candidate gherkin scenarios): ${falsePtTasks.size()}"
    }

    private importTasksFromCsv(){
        List<String[]> entries = CsvUtil.read(file.path)?.unique { it[TASK_INDEX] } //bug: input csv can contain duplicated values
        entries.remove(0)
        importedTasks = entries
        if(importedTasks.size()>0) url = entries.first()[URL_INDEX]
    }

    private analyseLimitedTasks(){
        def counter = 0
        if(candidateTasks && !candidateTasks.empty) {
            for(int j=0; j<candidateTasks.size() && analysedTasks.size()<taskLimit; j++){
                counter++
                def candidate = candidateTasks.get(j)
                def analysedTask = candidate.computeInterfaces()
                if(analysedTask.isValid()) analysedTasks += analysedTask
                else invalidTasks += analysedTask
            }
        }
        log.info "Task interfaces were computed for ${counter} tasks!"
    }

    private analyseAllTasks() {
        if(candidateTasks && !candidateTasks.empty) {
            candidateTasks.each {
                def analysedTask = it.computeInterfaces()
                if(analysedTask.isValid()) analysedTasks += analysedTask
                else invalidTasks += analysedTask
            }
        }
        log.info "Task interfaces were computed for ${candidateTasks.size()} tasks!"
    }

    private generateResult() {
        filterPtTasks()
        extractAllPtTasks(ptImportedTasks)
        log.info "Candidate tasks (have production code and candidate gherkin scenarios): ${candidateTasks.size()}"
        log.info "Seem to have test but actually do not (do not have candidate gherkin scenarios): ${falsePtTasks.size()}"

        if(taskLimit>0) analyseLimitedTasks()
        else analyseAllTasks()
    }

    private filterPtTasks(){
        resetTasks()
        ptImportedTasks = importedTasks.findAll { ((it[PROD_FILES_INDEX] as int)>0 && (it[TEST_FILES_INDEX] as int)>0) }
        notPtImportedTasks = importedTasks - ptImportedTasks
        log.info "Invalid tasks (do not have production and test code): ${notPtImportedTasks.size()}"
    }

    private generateIncrementalResult() {
        filterPtTasks()
        extractPtTasksPartially(ptImportedTasks)
    }

    private exportInvalidTasks(){
        if(invalidTasks.empty) log.info "There is no invalid tasks to save!"
        else {
            EvaluationExporter evaluationExporter = new EvaluationExporter(invalidTasksFile, invalidTasks, false)
            evaluationExporter.save()
            log.info "Invalid tasks were saved in ${invalidTasksFile}."
        }
    }

    private exportRelevantTasks(){
        if(analysedTasks.empty) log.info "There is no valid tasks to save!"
        else {
            relevantTaskExporter = new RelevantTaskExporter(relevantTasksFile, analysedTasks)
            relevantTaskExporter.save()
            def tasks = relevantTaskExporter.relevantTasks + relevantTaskExporter.emptyITestTasks
            EvaluationExporter evaluationExporter = new EvaluationExporter(relevantTasksDetailsFile, tasks)
            evaluationExporter.save()
            organizeResultForTestExecution()
            log.info "Valid tasks were saved in ${relevantTasksFile}, ${relevantTasksDetailsFile} and ${testFile}."
        }
    }

    private exportTasks(){
        exportRelevantTasks()
        exportInvalidTasks()
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

}
package br.ufpe.cin.tan.analysis

import br.ufpe.cin.tan.analysis.data.TaskImporter
import br.ufpe.cin.tan.analysis.task.DoneTask
import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.analysis.data.ControllerFilterExporter
import br.ufpe.cin.tan.analysis.data.EvaluationExporter
import br.ufpe.cin.tan.analysis.data.RelevantTaskExporter
import br.ufpe.cin.tan.analysis.data.SimilarityExporter
import br.ufpe.cin.tan.analysis.data.TestExecutionExporter
import br.ufpe.cin.tan.util.ConstantData

@Slf4j
class TaskAnalyser {

    File file
    TaskImporter taskImporter

    /* after task analysis */
    List<AnalysedTask> selectedTasks
    List<AnalysedTask> invalidTasks

    /* analysis strategy */
    int taskLimit
    boolean incrementalAnalysis

    /* Output files*/
    String evaluationFile
    String organizedFile
    String filteredFile
    String similarityFile
    String similarityOrganizedFile
    String testFile
    String relevantTasksFile
    String relevantTasksDetailsFile
    String invalidTasksFile

    RelevantTaskExporter relevantTaskExporter

    TaskAnalyser(String tasksFile){
        this(tasksFile, 0)
    }

    TaskAnalyser(String tasksFile, int taskLimit) {
        this.taskLimit = taskLimit
        file = new File(tasksFile)
        log.info "<  Analysing tasks from '${file.path}'  >"
        taskImporter = new TaskImporter(file)
        decideAnalysisStrategy()
        configureOutputFiles()
        selectedTasks = []
        invalidTasks = []
    }

    def analyseAll() {
        analysePrecisionAndRecall()
        analyseSimilarity()
    }

    def analysePrecisionAndRecall() {
        if(incrementalAnalysis) extractPtTasksPartially()
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
        if(taskImporter.importedTasks.size()>200) {
            incrementalAnalysis = true
            configureDefaultTaskLimit()
        }
        else incrementalAnalysis = false
    }

    private extractPtTasksPartially() {
        def allTasksToAnalyse = taskImporter.ptImportedTasks.size()
        def groups = allTasksToAnalyse.intdiv(100)
        def remainder = allTasksToAnalyse%100

        log.info "Tasks to analyse: ${allTasksToAnalyse}"
        log.info "Groups of 100 units: ${groups} + Remainder: ${remainder}"

        def i = 0, j = 100, analysedGroups = 0, counter = 0
        while(analysedGroups<groups && selectedTasks.size()<taskLimit) {
            counter++
            taskImporter.extractPtTasks(i, j)
            i=j
            j+=100
            analysedGroups++
            printPartialDataAnalysis(counter)
            analyseLimitedTasks()
        }

        if(remainder>0 && selectedTasks.size()<taskLimit){
            log.info "Last try to find valid tasks!"
            taskImporter.extractPtTasks(i, i+remainder)
            printPartialDataAnalysis(++counter)
            analyseLimitedTasks()
        }
    }

    private printPartialDataAnalysis(round){
        def candidatesSize = taskImporter.candidateTasks.size()
        def falsePtTasksSize = taskImporter.falsePtTasks.size()
        log.info "Extracted tasks at round $round: ${candidatesSize + falsePtTasksSize}"
        log.info "Candidate tasks (have production code and candidate gherkin scenarios): ${candidatesSize}"
        log.info "Seem to have test but actually do not (do not have candidate gherkin scenarios): ${falsePtTasksSize}"
        log.info "Selected tasks so far: ${selectedTasks.size()}"
    }

    private analyseLimitedTasks(){
        def counter = 0
        for(int j=0; j<taskImporter.candidateTasks.size() && selectedTasks.size()<taskLimit; j++){
            counter++
            def candidate = taskImporter.candidateTasks.get(j)
            analyse(candidate)
        }
        log.info "Task interfaces were computed for ${counter} tasks!"
    }

    private analyse(DoneTask task){
        def analysedTask = task.computeInterfaces()
        if(analysedTask.isValid()) selectedTasks += analysedTask
        else invalidTasks += analysedTask
    }

    private analyseAllTasks() {
        taskImporter.candidateTasks.each { analyse(it) }
        log.info "Task interfaces were computed for ${taskImporter.candidateTasks.size()} tasks!"
    }

    private generateResult() {
        taskImporter.extractPtTasks()
        log.info "Candidate tasks (have production code and candidate gherkin scenarios): ${taskImporter.candidateTasks.size()}"
        log.info "Seem to have test but actually do not (do not have candidate gherkin scenarios): ${taskImporter.falsePtTasks.size()}"

        if(taskLimit>0) analyseLimitedTasks()
        else analyseAllTasks()
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
        if(selectedTasks.empty) log.info "There is no valid tasks to save!"
        else {
            relevantTaskExporter = new RelevantTaskExporter(relevantTasksFile, selectedTasks)
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
        if(!selectedTasks.empty){
            EvaluationExporter evaluationExporter = new EvaluationExporter(evaluationFile, selectedTasks)
            evaluationExporter.save()
        }
    }

    private analyseSimilarity() {
        if(selectedTasks.empty) return
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
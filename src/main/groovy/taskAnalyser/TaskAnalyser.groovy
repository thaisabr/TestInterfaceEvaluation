package taskAnalyser

import groovy.util.logging.Slf4j
import taskAnalyser.task.AnalysedTask
import taskAnalyser.task.AnalysisResult
import taskAnalyser.task.DoneTask
import util.ConstantData
import util.Util

@Slf4j
class TaskAnalyser {

    String path
    File file
    List<DoneTask> tasks
    int allTasks

    TaskAnalyser(String tasksFile) {
        path = tasksFile
        file = new File(tasksFile)
        tasks = []
    }

    private AnalysisResult computeTaskData() {
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

        new AnalysisResult(stepCounter:stepCounter, gherkinCounter:gherkinCounter, testsCounter:testsCounter,
                validTasks:analysedTasks, url: tasks?.first()?.testCodeParser?.repositoryPath)
    }

    private generateResultForProject(String tasksFile, String evaluationFile) {
        def r1 = DataManager.extractProductionAndTestTasks(tasksFile)
        tasks = r1.tasks
        allTasks = r1.allTasksQuantity
        if(tasks && !tasks.empty) {
            def analysisResult = computeTaskData()
            DataManager.saveAllResult(evaluationFile, allTasks, analysisResult)
        }
    }

    private analyseAllForProject(String tasksFile) {
        File file = new File(tasksFile)
        def evaluationFile = ConstantData.DEFAULT_EVALUATION_FOLDER + File.separator + file.name
        def name = evaluationFile - ConstantData.CSV_FILE_EXTENSION
        def organizedFile = name + ConstantData.ORGANIZED_FILE_SUFIX
        def filteredFile = name + ConstantData.FILTERED_FILE_SUFIX
        def similarityFile = name + ConstantData.SIMILARITY_FILE_SUFIX
        def similarityOrganizedFile = name + ConstantData.SIMILARITY_ORGANIZED_FILE_SUFIX

        log.info "<  Analysing tasks from '$tasksFile'  >"
        generateResultForProject(tasksFile, evaluationFile)
        log.info "The results were saved!"

        log.info "<  Organizing tasks from '$evaluationFile'  >"
        DataManager.organizeResultForSimilarityAnalysis(evaluationFile, organizedFile, filteredFile)
        log.info "The results were saved!"

        log.info "<  Analysing similarity among tasks from '$filteredFile'  >"
        DataManager.analyseSimilarity(filteredFile, similarityFile)
        log.info "The results were saved!"

        log.info "<  Organizing tasks from '$similarityFile'  >"
        DataManager.organizeSimilarityResult(similarityFile, similarityOrganizedFile)
        log.info "The results were saved!"
    }

    private analysePrecisionAndRecallForProject(String tasksFile) {
        File file = new File(tasksFile)
        def evaluationFile = ConstantData.DEFAULT_EVALUATION_FOLDER + File.separator + file.name
        def organizedFile = evaluationFile - ConstantData.CSV_FILE_EXTENSION + ConstantData.ORGANIZED_FILE_SUFIX
        generateResultForProject(tasksFile, evaluationFile)
        DataManager.organizeResult(evaluationFile, organizedFile)
    }

    def analyseAll(){
        if(file.isDirectory()){
            def cvsFiles = Util.findFilesFromDirectory(path).findAll { it.endsWith(ConstantData.CSV_FILE_EXTENSION) }
            cvsFiles?.each { analyseAllForProject(it) }
        } else analyseAllForProject(path)

    }

    def analysePrecisionAndRecall(){
        if(file.isDirectory()){
            def cvsFiles = Util.findFilesFromDirectory(path).findAll { it.endsWith(ConstantData.CSV_FILE_EXTENSION) }
            cvsFiles?.each { analysePrecisionAndRecallForProject(it) }
        } else analysePrecisionAndRecallForProject(path)
    }

}

package taskAnalyser

import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.util.logging.Slf4j
import taskAnalyser.task.DoneTask
import util.ConstantData
import util.Util

@Slf4j
class TaskAnalyser {

    String path
    File file
    List<DoneTask> tasks

    TaskAnalyser(String tasksFile) {
        path = tasksFile
        file = new File(tasksFile)
        tasks = []
    }

    private computeTaskData() {
        log.info "Number of tasks: ${tasks.size()}"
        def stepCounter = 0
        def gherkinCounter = 0
        def result = []

        tasks?.each { task ->
            TimeDuration timestamp
            def initTime = new Date()
            def interfaces = task.computeInterfaces()
            def endTime = new Date()
            use(TimeCategory) {
                timestamp = endTime - initTime
            }
            def stepCalls = interfaces.itest.methods?.findAll { it.type == "StepCall" }?.unique()?.size()
            def methods = interfaces.itest.methods?.findAll { it.type == "Object" }?.unique()
            def methodsIdentity = ""
            if (!methods.empty) methodsIdentity = methods*.name
            if (!task.changedStepDefinitions.empty) stepCounter++
            if (!task.changedGherkinFiles.empty) gherkinCounter++

            result += [task: task, itest: interfaces.itest, ireal: interfaces.ireal, methods: methodsIdentity, stepCalls: stepCalls,
                       text: interfaces.itext, timestamp:timestamp]
        }

        log.info "Number of tasks that contains step definitions: $stepCounter"
        log.info "Number of tasks that changed Gherkin files: $gherkinCounter"

        [stepCounter: stepCounter, gherkinCounter: gherkinCounter, data: result]
    }

    private generateResultForProject(String tasksFile, String evaluationFile) {
        def r1 = DataManager.extractProductionAndTestTasks(tasksFile)
        tasks = r1.tasks
        def r2 = computeTaskData()
        def url = r1.tasks?.first()?.testCodeParser?.repositoryPath
        DataManager.saveAllResult(evaluationFile, url, r1.allTasksQuantity, tasks.size(), r2.stepCounter, r2.gherkinCounter, r2.data)
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

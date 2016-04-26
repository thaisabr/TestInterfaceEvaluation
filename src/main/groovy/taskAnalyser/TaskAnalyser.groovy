package taskAnalyser

import groovy.util.logging.Slf4j
import taskAnalyser.task.DoneTask
import util.ConstantData
import util.Util

@Slf4j
class TaskAnalyser {

    private static computeTaskData(List<DoneTask> tasks){
        log.info "Number of tasks: ${tasks.size()}"
        def stepCounter = 0
        def gherkinCounter = 0
        def result = []

        tasks?.each{ task ->
            def interfaces = task.computeInterfaces()
            def stepCalls = interfaces.itest.methods?.findAll{ it.type == "StepCall"}?.unique()?.size()
            def methods = interfaces.itest.methods?.findAll{ it.type == "Object"}?.unique()
            def methodsIdentity = ""
            if(!methods.empty) methodsIdentity = methods*.name
            if(!task.changedStepDefinitions.empty) stepCounter++
            if(!task.changedGherkinFiles.empty) gherkinCounter++

            result += [task:task, itest:interfaces.itest, ireal:interfaces.ireal, methods:methodsIdentity, stepCalls:stepCalls,
                       text:interfaces.itext]
        }

        log.info "Number of tasks that contains step definitions: $stepCounter"
        log.info "Number of tasks that changed Gherkin files: $gherkinCounter"

        [stepCounter:stepCounter, gherkinCounter:gherkinCounter, data:result]
    }

    private static generateResultForProject(String allTasksFile, String evaluationFile){
        def result1 = DataManager.extractProductionAndTestTasks(allTasksFile)
        def result2 = computeTaskData(result1.relevantTasks)
        DataManager.saveAllResult(evaluationFile, result1.allTasksQuantity, result1.relevantTasks.size(), result2.stepCounter,
                result2.gherkinCounter, result2.data)
    }

    static analyseAllForProject(String allTasksFile){
        File file = new File(allTasksFile)
        def evaluationFile = ConstantData.DEFAULT_EVALUATION_FOLDER+File.separator+file.name
        def name = evaluationFile - ConstantData.CSV_FILE_EXTENSION
        def organizedFile = name + ConstantData.ORGANIZED_FILE_SUFIX
        def filteredFile = name + ConstantData.FILTERED_FILE_SUFIX
        def similarityFile = name + ConstantData.SIMILARITY_FILE_SUFIX
        def similarityOrganizedFile = name + ConstantData.SIMILARITY_ORGANIZED_FILE_SUFIX

        log.info "<  Analysing tasks from '$allTasksFile'  >"
        generateResultForProject(allTasksFile, evaluationFile)
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

    static analyseAllForMultipleProjects(def folder){
        def cvsFiles = Util.findFilesFromDirectory(folder).findAll{ it.endsWith(ConstantData.CSV_FILE_EXTENSION)}
        cvsFiles?.each{
            analyseAllForProject(it)
        }
    }

    static analysePrecisionAndRecallForProject(String allTasksFile){
        File file = new File(allTasksFile)
        def evaluationFile = ConstantData.DEFAULT_EVALUATION_FOLDER+File.separator+file.name
        def organizedFile = evaluationFile - ConstantData.CSV_FILE_EXTENSION + ConstantData.ORGANIZED_FILE_SUFIX
        generateResultForProject(allTasksFile, evaluationFile)
        DataManager.organizeResult(evaluationFile, organizedFile)
    }

    static analysePrecisionAndRecallForMultipleProjects(String folder){
        def cvsFiles = Util.findFilesFromDirectory(folder).findAll{ it.endsWith(ConstantData.CSV_FILE_EXTENSION)}
        cvsFiles?.each{
            analysePrecisionAndRecallForProject(it)
        }
    }

}

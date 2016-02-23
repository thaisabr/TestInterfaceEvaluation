package taskAnalyser

import au.com.bytecode.opencsv.CSVWriter
import evaluation.TaskInterfaceEvaluator
import groovy.util.logging.Slf4j
import util.Util

@Slf4j
class TaskAnalyser {

    static analyse(){
        List<DoneTask> tasks = TaskSearchManager.extractProductionAndTestTasksFromCSV()
        log.info "Number of tasks: ${tasks.size()}"

        def gherkinCounter = 0
        def testBasedInterfaces = []
        def relevantTasks = tasks.findAll{ !it.changedGherkinFiles.empty || !it.changedStepDefinitions.empty }
        relevantTasks?.each{ task ->
            def taskInterface = task.computeTestBasedInterface()
            def stepCalls = taskInterface.methods?.findAll{ it.type == "StepCall"}?.unique()?.size()
            def methods = taskInterface.methods?.findAll{ it.type == "Object"}?.unique()
            if(!methods.empty) testBasedInterfaces += [task:task, itest:taskInterface, ireal:task.computeRealInterface(), methods:methods*.name, stepCalls:stepCalls]
            else testBasedInterfaces += [task:task, itest:taskInterface, ireal:task.computeRealInterface(), methods:"", stepCalls:stepCalls]
            if(!task.changedGherkinFiles.empty){ gherkinCounter++ }
        }

        log.info "Number of tasks that contains acceptance tests: ${testBasedInterfaces.size()}"
        log.info "Number of tasks that changed Gherkin files: $gherkinCounter"

        exportResult(Util.DEFAULT_EVALUATION_FILE, tasks.size(), gherkinCounter, testBasedInterfaces)
    }

    static analyse(String filename){
        List<DoneTask> tasks = TaskSearchManager.extractProductionAndTestTasksFromCSV(filename)
        log.info "Number of tasks: ${tasks.size()}"

        def gherkinCounter = 0
        def testBasedInterfaces = []
        def relevantTasks = tasks.findAll{ !it.changedGherkinFiles.empty || !it.changedStepDefinitions.empty }
        relevantTasks?.each{ task ->
            def taskInterface = task.computeTestBasedInterface()
            def stepCalls = taskInterface.methods?.findAll{ it.type == "StepCall"}?.unique()?.size()
            def methods = taskInterface.methods?.findAll{ it.type == "Object"}?.unique()
            if(!methods.empty) testBasedInterfaces += [task:task, itest:taskInterface, ireal:task.computeRealInterface(), methods:methods*.name,  stepCalls:stepCalls]
            else testBasedInterfaces += [task:task, itest:taskInterface, ireal:task.computeRealInterface(), methods:"",  stepCalls:stepCalls]
            if(!task.changedGherkinFiles.empty){ gherkinCounter++ }
        }

        log.info "Number of tasks that contains acceptance tests: ${testBasedInterfaces.size()}"
        log.info "Number of tasks that changed Gherkin files: $gherkinCounter"

        File file = new File(filename)
        def outputFile = Util.DEFAULT_EVALUATION_FOLDER+File.separator+file.name
        exportResult(outputFile, tasks.size(), gherkinCounter, testBasedInterfaces)
    }

    static exportResult(def allTasksCounter, def tasksCounter,  def taskInterfaces){
        exportResult(Util.DEFAULT_EVALUATION_FILE, allTasksCounter, tasksCounter, taskInterfaces)
    }

    static exportResult(def filename, def allTasksCounter, def tasksCounter, def taskInterfaces){
        CSVWriter writer = new CSVWriter(new FileWriter(filename))
        writer.writeNext("Number of tasks: $allTasksCounter")
        writer.writeNext("Number of tasks that changed Gherkin files: $tasksCounter")
        writer.writeNext("Number of tasks that contains acceptance tests: ${taskInterfaces?.size()}")
        String[] header = ["Task","Date","#Devs","Commit_Message","ITest","IReal","Precision","Recall", "Methods_Unknown_Type", "#Step_Call"]
        writer.writeNext(header)

        taskInterfaces?.each{ entry ->
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(entry.itest, entry.ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(entry.itest, entry.ireal)
            def dates =  entry?.task?.commits*.date?.flatten()?.sort()
            if(dates) dates = dates.collect{ new Date(it*1000).format('dd-MM-yyyy') }.unique()
            else dates = []
            def devs = entry?.task?.commits*.author?.flatten()?.unique()?.size()
            def msgs = entry?.task?.commits*.message?.flatten()
            String[] line = [entry.task.id, dates, devs, msgs, entry.itest, entry.ireal, precision, recall, entry.methods, entry.stepCalls]
            writer.writeNext(line)
        }

        writer.close()
        log.info "The results were saved!"
    }

}

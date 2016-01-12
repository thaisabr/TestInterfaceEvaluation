package taskAnalyser

import au.com.bytecode.opencsv.CSVWriter
import evaluation.TaskInterfaceEvaluator
import groovy.util.logging.Slf4j
import util.Util

@Slf4j
class Main {

    static analyseTasks(){
        List<DoneTask> tasks = TaskSearchManager.extractProductionAndTestTasksFromCSV()
        log.info "Number of tasks: ${tasks.size()}"

        /* RUBY: TEST INTERFACE BASED ON ACCEPTANCE TEST CODE */
        def gherkinCounter = 0
        def nonEmptyInterfaces = []
        tasks.each{ task ->
            def taskInterface = task.computeTestBasedInterface()
            if(!task.changedGherkinFiles.isEmpty()){
                gherkinCounter++
                if(taskInterface.toString() != ""){
                    nonEmptyInterfaces += [task:task, itest:taskInterface, ireal:task.computeRealInterface()]
                }
            }
        }

        log.info "\nnumber of tasks that changed Gherkin files: $gherkinCounter"
        log.info "number of non empty task interfaces: ${nonEmptyInterfaces.size()}"

        exportResult(Util.DEFAULT_EVALUATION_FILE, nonEmptyInterfaces, gherkinCounter, nonEmptyInterfaces.size())
    }

    static analyseTasks(String filename){
        List<DoneTask> tasks = TaskSearchManager.extractProductionAndTestTasksFromCSV(filename)
        log.info "Number of tasks: ${tasks.size()}"

        /* RUBY: TEST INTERFACE BASED ON ACCEPTANCE TEST CODE */
        def gherkinCounter = 0
        def nonEmptyInterfaces = []
        tasks.each{ task ->
            def taskInterface = task.computeTestBasedInterface()
            if(!task.changedGherkinFiles.isEmpty()){
                gherkinCounter++
                if(taskInterface.toString() != ""){
                    nonEmptyInterfaces += [task:task, itest:taskInterface, ireal:task.computeRealInterface()]
                }
            }
        }

        log.info "\nnumber of tasks that changed Gherkin files: $gherkinCounter"
        log.info "number of non empty task interfaces: ${nonEmptyInterfaces.size()}"

        File file = new File(filename)
        def outputFile = Util.DEFAULT_EVALUATION_FOLDER+File.separator+file.name
        exportResult(outputFile, nonEmptyInterfaces, gherkinCounter, nonEmptyInterfaces.size())
    }

    static exportResult(def taskInterfaces, def tasksCounter, def noEmptyInterfacesCounter){
        exportResult(Util.DEFAULT_EVALUATION_FILE, taskInterfaces, tasksCounter, noEmptyInterfacesCounter)
    }

    static exportResult(def filename, def taskInterfaces, def tasksCounter, def noEmptyInterfacesCounter){
        CSVWriter writer = new CSVWriter(new FileWriter(filename))
        writer.writeNext("number of tasks that changed Gherkin files: $tasksCounter")
        writer.writeNext("number of non empty task interfaces: $noEmptyInterfacesCounter")
        String[] header = ["Task","Date","ITest","IReal","Precision","Recall"]
        writer.writeNext(header)

        taskInterfaces.each{ entry ->
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(entry.itest, entry.ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(entry.itest, entry.ireal)
            def dates =  entry?.task?.commits*.date?.flatten()?.sort()
            if(dates) dates = dates.collect{ new Date(it*1000) }
            else dates = []
            String[] line = [entry.task.id, dates, entry.itest, entry.ireal, precision, recall]
            writer.writeNext(line)

            log.info "\nTask id: ${entry.task.id}"
            log.info "ITEST:"
            log.info "${entry.itest}\n"
            log.info "IREAL:"
            log.info "${entry.ireal}\n"
            log.info "Files precision: $precision"
            log.info "Files recall: $recall"

            /*println "changed test files: "
            println entry.task.commits.collect{ commit -> commit.testChanges*.filename }?.flatten()?.unique()
            println "changed production files: "
            println entry.task.commits.collect{ commit -> commit.productionChanges*.filename }?.flatten()?.unique()*/
        }

        writer.close()
    }

    public static void main(String[] args){
        /*def cvsFiles = Util.findFilesFromDirectory("tasks")
        cvsFiles?.each{
            log.info "<  Analysing tasks from '$it'  >"
            analyseTasks(it)
        }*/

        analyseTasks()
    }

}

package taskAnalyser

import au.com.bytecode.opencsv.CSVWriter
import evaluation.TaskInterfaceEvaluator
import groovy.util.logging.Slf4j
import util.Util

@Slf4j
class Main {

    static exportResult(def taskInterfaces, def tasksCounter, def noEmptyInterfacesCounter){
        CSVWriter writer = new CSVWriter(new FileWriter(Util.DEFAULT_EVALUATION_FILE))
        writer.writeNext("number of tasks that changed Gherkin files: $tasksCounter")
        writer.writeNext("number of non empty task interfaces: $noEmptyInterfacesCounter")
        String[] header = ["Task","ITest","IReal","Precision","Recall"]
        writer.writeNext(header)

        taskInterfaces.each{ entry ->
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(entry.itest, entry.ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(entry.itest, entry.ireal)
            String[] line = [entry.task.id, entry.itest, entry.ireal, precision, recall]
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
        /********************************************* RUBY ***********************************************************/
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

        exportResult(nonEmptyInterfaces, gherkinCounter, nonEmptyInterfaces.size())

    }

}

package taskAnalyser

import au.com.bytecode.opencsv.CSVWriter
import evaluation.TaskInterfaceEvaluator
import util.Util

class Main {

    static exportResult(def taskInterfaces){
        CSVWriter writer = new CSVWriter(new FileWriter(Util.DEFAULT_EVALUATION_FILE))
        String[] header = ["Task","ITest","IReal","Precision","Recal"]
        writer.writeNext(header)

        taskInterfaces.each{ entry ->
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(entry.itest, entry.ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(entry.itest, entry.ireal)
            String[] line = [entry.task.id, entry.itest, entry.ireal, precision, recall]
            writer.writeNext(line)

            println "\nTask id: ${entry.task.id}"
            println "ITEST:"
            println "${entry.itest}\n"
            println "IREAL:"
            println "${entry.ireal}\n"
            println "Files precision: $precision"
            println "Files recall: $recall"
        }

        writer.close()
    }

    public static void main(String[] args){
        /********************************************* RUBY ***********************************************************/
        List<DoneTask> tasks = TaskSearchManager.extractProductionAndTestTasksFromCSV()
        println "number of tasks: ${tasks.size()}"

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

        println "\nnumber of tasks that changed Gherkin files: $gherkinCounter"
        println "number of non empty task interfaces: ${nonEmptyInterfaces.size()}"

        exportResult(nonEmptyInterfaces)

    }

}

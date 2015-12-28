package taskAnalyser

import evaluation.TaskInterfaceEvaluator

class Main {

    static printInterfaces(def taskInterfaces){
        taskInterfaces.each{ entry ->
            println "\nTask id: ${entry.task.id}"
            println "ITEST:"
            println "${entry.itest}\n"
            println "IREAL:"
            println "${entry.ireal}\n"
            println "Files precision: ${TaskInterfaceEvaluator.calculateFilesPrecision(entry.itest, entry.ireal)}"
            println "Files recall: ${TaskInterfaceEvaluator.calculateFilesRecall(entry.itest, entry.ireal)}"
        }
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

        printInterfaces(nonEmptyInterfaces)

    }

}

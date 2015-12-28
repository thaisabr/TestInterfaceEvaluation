package taskAnalyser

class Main {

    static printInterfaces(def taskInterfaces){
        taskInterfaces.each{ entry ->
            println "Task id: ${entry.task.id}"
            println "${entry.interface}\n"
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
                if(taskInterface.toString() != "") nonEmptyInterfaces += [task:task, interface:taskInterface]
            }
        }
        println "number of tasks that changed Gherkin files: $gherkinCounter"
        println "number of non empty task interfaces: ${nonEmptyInterfaces.size()}"
        printInterfaces(nonEmptyInterfaces)

    }

}

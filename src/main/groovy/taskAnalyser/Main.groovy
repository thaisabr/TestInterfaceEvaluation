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


        /* RUBY: TEST INTERFACE BASED ON UNIT TEST CODE */
        def unitCounter = 0
        def nonEmptyUnitTestInterfaces = []
        tasks.each{ task ->
            def taskInterface = task.computeUnitTestBasedInterface()
            if(!task.changedUnitFiles.isEmpty()){
                unitCounter++
                if(taskInterface.toString() != "") nonEmptyUnitTestInterfaces += [task:task, interface:taskInterface]
            }
        }
        println "number of tasks that changed unit test files: $unitCounter"
        println "number of non empty task interfaces: ${nonEmptyUnitTestInterfaces.size()}"
        printInterfaces(nonEmptyUnitTestInterfaces)


        /* RUBY: JOIN TEST INTERFACES (ACCEPTANCE TEST AND UNIT TEST) */
        def combinedInterfaces = []
        nonEmptyInterfaces.each{ acceptanceInterface ->
            def entry = nonEmptyUnitTestInterfaces.find{ it.task.id == acceptanceInterface.task.id}
            if(entry){
                def interfaces = [entry.interface, acceptanceInterface.interface]
                combinedInterfaces += TaskInterface.colapseInterfaces(interfaces)
            }
        }
        println "number of tasks with acceptance and unit test based interfaces: ${combinedInterfaces.size()}"
        printInterfaces(combinedInterfaces)
    }

}

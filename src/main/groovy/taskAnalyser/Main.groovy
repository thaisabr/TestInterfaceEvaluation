package taskAnalyser

class Main {

    public static void main(String[] args){
        List<Task> tasks = TaskSearchManager.extractProductionAndTestTasks()
        println "number of tasks: ${tasks.size()}"

        def tasksChangingAcceptanceTest = TaskSearchManager.extractAcceptanceTestsForTasks(tasks)
        println "number of tasks that changed acceptance test: ${tasksChangingAcceptanceTest.size()}"
        tasksChangingAcceptanceTest.each{ t ->
            println "task id: ${t.id}"
            println "scenarios: "
            t.scenarios.each{ scen ->
                println scen
            }
        }

    }

}

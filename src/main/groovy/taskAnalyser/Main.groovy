package taskAnalyser

import util.Util


class Main {

    public static void main(String[] args){
        def tasks = TaskSearchManager.extractProductionAndTestTasks(Util.TASKS_FILE)
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

package taskAnalyser

class Main {

    public static void main(String[] args){
        List<Task> tasks = TaskSearchManager.extractProductionAndTestTasks()
        println "number of tasks: ${tasks.size()}"

        def tasksChangingGherkinFile = TaskSearchManager.findAllTasksChangingGherkinFile(tasks)
        println "number of tasks that changed Gherkin files: ${tasksChangingGherkinFile.size()}"
        tasksChangingGherkinFile.each{ t ->
            println "task id: ${t.id}"
            t.changedGherkinFiles.each{ gherkinFile ->
                println "Gherkin file: ${gherkinFile.path}"
                println "Feature: ${gherkinFile.feature.name}"
                println "Changed scenario definitions: "
                gherkinFile.changedScenarioDefinitions.each{ definition ->
                    println "Scenario (line ${definition.location.line}): ${definition.name}"
                }
            }

        }

    }

}

package taskAnalyser

class Main {

    public static void main(String[] args){
        List<Task> tasks = TaskSearchManager.extractProductionAndTestTasksFromCSV()
        println "number of tasks: ${tasks.size()}"

        def counter = 0

        tasks.each{ task ->
            task.computeTestBasedInterface()
            if(!task.changedGherkinFiles.isEmpty()) counter++
        }

        println "number of tasks that changed Gherkin files: $counter"

    }

}

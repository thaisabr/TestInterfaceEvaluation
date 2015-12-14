package taskAnalyser

class Main {

    public static void main(String[] args){

        /* RUBY */
        List<DoneTask> tasks = TaskSearchManager.extractProductionAndTestTasksFromCSV()
        println "number of tasks: ${tasks.size()}"

        def counter = 0
        tasks.each{ task ->
            def taskInterface = task.computeTestBasedInterface()
            if(!task.changedGherkinFiles.isEmpty()){
                counter++
                println taskInterface
            }
        }

        println "number of tasks that changed Gherkin files: $counter"
    }

}

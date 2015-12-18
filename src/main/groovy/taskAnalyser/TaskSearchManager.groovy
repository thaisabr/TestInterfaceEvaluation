package taskAnalyser

import au.com.bytecode.opencsv.CSVReader
import commitAnalyser.Commit
import util.Util

class TaskSearchManager {

    private static List<String[]> readCSV(){
        CSVReader reader = new CSVReader(new FileReader(Util.TASKS_FILE))
        List<String[]> entries = reader.readAll()
        reader.close()
        entries.remove(0) //ignore header
        return entries
    }

    /***
     * Extracts all tasks in a CSV file that changed production and test files.
     * @return a list of tasks.
     */
    static List<DoneTask> extractProductionAndTestTasksFromCSV(){
        List<String[]> entries = readCSV()

        //"index","repository_url","task_id","commits_hash","changed_production_files","changed_test_files","commits_message"
        List<String[]> relevantEntries = entries.findAll{ !it[4].equals("[]") && !it[5].equals("[]") }

        List<DoneTask> tasks = []
        relevantEntries.each { entry ->
            List<Commit> commits = []
            def hashes = entry[3].tokenize(',[]')*.trim()
            hashes.each { commits += new Commit(hash: it) }
            tasks += new DoneTask(entry[0], entry[1], entry[2], commits)
        }
        return tasks
    }

}

package taskAnalyser

import au.com.bytecode.opencsv.CSVReader
import commitAnalyser.Commit
import util.Util

class TaskSearchManager {

    static List<String[]> readCSV(String filename){
        CSVReader reader = new CSVReader(new FileReader(filename))
        List<String[]> entries = reader.readAll()
        reader.close()
        entries.remove(0) //ignore header
        return entries
    }

    static List<String[]> readCSV(){
        readCSV(Util.TASKS_FILE)
    }

    /***
     * Extracts all tasks in a CSV file that changed production and test files.
     * @filename cvs file.
     * @return a list of tasks.
     */
    static List<DoneTask> extractProductionAndTestTasksFromCSV(String filename){
        List<String[]> entries = readCSV(filename)

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

    static List<DoneTask> extractProductionAndTestTasksFromCSV(){
        extractProductionAndTestTasksFromCSV(Util.TASKS_FILE)
    }

}

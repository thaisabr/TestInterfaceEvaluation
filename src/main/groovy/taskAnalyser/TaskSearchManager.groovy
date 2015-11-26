package taskAnalyser

import au.com.bytecode.opencsv.CSVReader
import commitAnalyser.Commit
import commitAnalyser.GitRepositoryManager
import util.Util

class TaskSearchManager {

    private static List<String[]> readCSV(){
        CSVReader reader = new CSVReader(new FileReader(Util.TASKS_FILE))
        List<String[]> entries = reader.readAll()
        reader.close()
        entries.remove(0) //ignore header
        return entries
    }

    public static List<Task> extractProductionAndTestTasksFromCSV(){
        List<String[]> entries = readCSV()

        //"index","repository_url","task_id","commits_hash","changed_production_files","changed_test_files","commits_message"
        List<String[]> relevantEntries = entries.findAll{ !it[4].equals("[]") && !it[5].equals("[]") }

        List<Task> tasks = []
        relevantEntries.each { entry ->
            List<Commit> commits = []
            def hashes = entry[3].tokenize(',[]')*.trim()
            hashes.each { commits += new Commit(hash: it) }
            tasks += new Task(repositoryIndex: entry[0], repositoryUrl: entry[1], id: entry[2], commits: commits,
                    changedGherkinFiles:[], gitRepository:GitRepositoryManager.getRepository(entry[1]))
        }
        return tasks
    }

}

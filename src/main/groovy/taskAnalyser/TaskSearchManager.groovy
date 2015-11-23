package taskAnalyser

import au.com.bytecode.opencsv.CSVReader
import commitAnalyser.Commit
import commitAnalyser.GitRepository
import util.Util

class TaskSearchManager {

    private static List<Task> extractProductionAndTestTasks(String csvPath){
        List<Task> tasks = []
        CSVReader reader = new CSVReader(new FileReader(csvPath))
        List<String[]> entries = reader.readAll()
        reader.close()
        entries.remove(0) //ignore header

        //"index","repository_url","task_id","commits_hash","changed_production_files","changed_test_files","commits_message"
        List<String[]> relevantEntries = entries.findAll{ !it[4].equals("[]") && !it[5].equals("[]") }

        relevantEntries.each { entry ->
            List<Commit> commits = []
            def hashes = entry[3].tokenize(',[]')*.trim()
            hashes.each { commits += new Commit(hash: it) }
            tasks += new Task(repositoryIndex: entry[0], repositoryUrl: entry[1], id: entry[2], commits: commits,
                        productionFiles: entry[4].tokenize(',[]')*.trim(), testFiles: entry[5].tokenize(',[]')*.trim())
        }
        return tasks
    }

    private static List<GitRepository> downloadRepositories(List<Task> tasks){
        List<GitRepository> repositories = []
        def urls = tasks*.repositoryUrl.unique()
        urls?.each{ url ->
            repositories += new GitRepository(url)
        }
        return repositories
    }

    private static List<Commit> identifyChangedGherkinFiles(Task task){
        List<Commit> commitsChangedGherkinFile = []
        task.commits.each{ commit ->
            commit.gherkinChanges = commit.testChanges.findAll{ it.filename.endsWith(Util.FEATURE_FILENAME_EXTENSION) }
            if( !commit.gherkinChanges.isEmpty()){
                commitsChangedGherkinFile += commit
            }
        }
        return commitsChangedGherkinFile
    }

    private static List<Task> extractAcceptanceTestsForTasks(List<Task> tasks){
        List<GitRepository> repositories = downloadRepositories(tasks)

        tasks?.each{ task ->
            def gitRepository = repositories.find{ (it.url - Util.GIT_EXTENSION).equals(task.repositoryUrl) }
            task.commits = gitRepository.searchBySha(*(task.commits*.hash))
            List<Commit> commitsChangedGherkinFile = identifyChangedGherkinFiles(task)
            if(commitsChangedGherkinFile.isEmpty()){
                task.scenarios = []
            }
            else{
                task.scenarios = gitRepository.extractScenariosFromCommit(commitsChangedGherkinFile)
            }

        }

        return tasks?.findAll{ !it.scenarios.isEmpty() }
    }

}

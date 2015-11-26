package taskAnalyser

import commitAnalyser.Commit
import commitAnalyser.GitRepository
import util.Util


class Task {

    String repositoryIndex
    String repositoryUrl
    String id
    List<Commit> commits
    List<GherkinFile> changedGherkinFiles
    GitRepository gitRepository

    private List<GherkinFile> identifyChangedGherkinFiles(){
        List<GherkinFile> changedGherkinFiles = []
        List<Commit> commitsChangedGherkinFile = []

        commits.each{ commit ->
            commit.gherkinChanges = commit.testChanges.findAll{ it.filename.endsWith(Util.FEATURE_FILENAME_EXTENSION) }
            if( !commit.gherkinChanges.isEmpty()){
                commitsChangedGherkinFile += commit
            }
        }

        if(!commitsChangedGherkinFile.isEmpty()){
            changedGherkinFiles = gitRepository.identifyChangedGherkinContent(commitsChangedGherkinFile)
        }

        return changedGherkinFiles
    }

    def TaskInterface computeTestBasedInterface(){
        commits = gitRepository.searchBySha(*(commits*.hash))

        //identify changed gherkin files and scenario definitions
        changedGherkinFiles = identifyChangedGherkinFiles()
        if(!changedGherkinFiles.isEmpty()) {
            println "Task id: $id"
            changedGherkinFiles.each{
                println it.toString()
            }
        }

        //for each changed scenario definition, identify steps implementation
        //for each step implementation, extract production method call

        new TaskInterface()
    }

    def TaskInterface computeRealInterface(){
        def files = commits*.files?.flatten().unique()
        def productionFiles = Util.findAllProductionFiles(files)
        new TaskInterface(changedFiles: productionFiles)
    }

}

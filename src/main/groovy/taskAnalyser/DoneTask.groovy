package taskAnalyser

import commitAnalyser.Commit
import util.Util

/***
 * Represents a done task, that is, a task that contains production and test code. The code is published in a public
 * Git repository in GitHub. The task is used to evaluation study, to validate test-based task interfaces by comparing
 * them with real interfaces.
 */
class DoneTask extends Task {

    String repositoryIndex
    List<Commit> commits
    List<GherkinFile> changedGherkinFiles

    DoneTask(String repositoryIndex, String repositoryUrl, String id, List<Commit> commits) {
        super(repositoryUrl, true, id)
        this.repositoryIndex = repositoryIndex
        this.commits = commits
        this.changedGherkinFiles = []
    }

    @Override
    TaskInterface computeTestBasedInterface(){
        TaskInterface taskInterface = new TaskInterface()

        /* retrieves commits code */
        commits = gitRepository.searchBySha(*(commits*.hash))

        /* identifies changed gherkin files and scenario definitions */
        List<Commit> commitsChangedGherkinFile = []
        commits.each{ commit ->
            commit.gherkinChanges = commit.testChanges.findAll{ it.filename.endsWith(Util.FEATURE_FILENAME_EXTENSION) }
            if( !commit.gherkinChanges.isEmpty()){
                commitsChangedGherkinFile += commit
            }
        }
        if(!commitsChangedGherkinFile.isEmpty()){
            /* resets repository to the state of the last commit to extract changes */
            gitRepository.reset(commits?.last()?.hash)
            changedGherkinFiles = gitRepository.identifyChangedGherkinContent(commitsChangedGherkinFile)

            /* computes task interface based on the production code exercised by tests */
            println "Task id: $id"
            taskInterface = testCodeParser.computeInterfaceForDoneTask(changedGherkinFiles)

            /* resets repository to last version */
            gitRepository.reset()
        }

        return taskInterface
    }

    /* TERMINAR / CORRIGIR
    TaskInterface computeRealInterface(){
        def taskInterface = new TaskInterface()
        if(commits){
            def files = commits*.codeChanges*.filename*.flatten()?.unique()
            def productionFiles = Util.findAllProductionFiles(files)
            taskInterface.files = productionFiles
        }
        return taskInterface
    }*/

}

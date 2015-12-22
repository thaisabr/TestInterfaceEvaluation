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
    List<UnitFile> changedUnitFiles

    DoneTask(String repositoryIndex, String repositoryUrl, String id, List<Commit> commits) {
        super(repositoryUrl, true, id)

        this.repositoryIndex = repositoryIndex
        this.changedGherkinFiles = []
        this.changedUnitFiles = []

        /* retrieves commits code */
        this.commits = gitRepository.searchBySha(*(commits*.hash))
    }

    TaskInterface computeUnitTestBasedInterface(){
        println "TASK ID: $id"
        def interfaces = []

        /* identifies changed unit test files */
        List<Commit> commitsChangedRspecFile = commits.findAll{ !it.unitChanges.isEmpty() }

        commitsChangedRspecFile?.each{ commit ->
            println "\nCommit: ${commit.hash}"

            /* resets repository to the state of the commit to extract changes */
            gitRepository.reset(commit.hash)

            /* translates changed lines in unit test files to changed unit tests */
            List<UnitFile> changes = gitRepository.identifyChangedUnitTestContent(commit)

            if(!changes.isEmpty()){
                changedUnitFiles += changes

                /* computes task interface based on the production code exercised by tests */
                interfaces += testCodeParser.computeInterfaceForDoneTaskByUnitTest(changes)
            }
            else{
                println "No changes in unit test!\n"
            }

            /* resets repository to last version */
            gitRepository.reset()
        }

        /* collapses step code interfaces to define the interface for the whole task */
        TaskInterface.colapseInterfaces(interfaces)
    }

    @Override
    TaskInterface computeTestBasedInterface(){
        println "TASK ID: $id"
        def interfaces = []

        /* identifies changed gherkin files and scenario definitions */
        List<Commit> commitsChangedGherkinFile = commits.findAll{ !it.gherkinChanges.isEmpty() }

        commitsChangedGherkinFile?.each{ commit ->
            println "\nCommit: ${commit.hash}"

            /* resets repository to the state of the commit to extract changes */
            gitRepository.reset(commit.hash)

            /* translates changed lines in Gherkin files to changed acceptance tests */
            List<GherkinFile> changes = gitRepository.identifyChangedGherkinContent(commit)

            if(!changes.isEmpty()){
                changedGherkinFiles += changes

                /* computes task interface based on the production code exercised by tests */
                interfaces += testCodeParser.computeInterfaceForDoneTask(changes)
            }
            else{
                println "No changes in acceptance tests!\n"
            }

            /* resets repository to last version */
            gitRepository.reset()
        }

        /* collapses step code interfaces to define the interface for the whole task */
        TaskInterface.colapseInterfaces(interfaces)
    }

    TaskInterface computeRealInterface(){
        def taskInterface = new TaskInterface()
        if(commits){
            def files = commits*.codeChanges*.filename*.flatten()?.unique()
            def productionFiles = Util.findAllProductionFiles(files)
            productionFiles.each{ file ->
                taskInterface.classes += [name:"", file:file]
            }
        }
        return taskInterface
    }

}

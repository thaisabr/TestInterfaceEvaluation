package commitAnalyser

import taskAnalyser.task.GherkinFile
import taskAnalyser.task.StepDefinitionFile
import taskAnalyser.task.UnitFile

/***
 * Represents a git commit.
 */
class Commit {

    String hash
    String message
    String author
    long date

    List<CoreChange> coreChanges //code changes in production code only
    List<GherkinFile> gherkinChanges //code changes in feature files only
    List<StepDefinitionFile> stepChanges //code changes in step definition files only
    List<UnitFile> unitChanges //code changes in unit test files only
    List<RenamingChange> renameChanges

    @Override
    public String toString(){
        def paths = (coreChanges*.path + unitChanges*.path + gherkinChanges*.path + stepChanges*.path)?.flatten()
         "$hash*${new Date(date*1000)}*$author*$message*${paths.toListString()}"
    }

}

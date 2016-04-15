package commitAnalyser

import taskAnalyser.task.GherkinFile
import taskAnalyser.task.StepDefinitionFile

/***
 * Represents a git commit.
 */
class Commit {

    String hash
    String message
    String author
    long date

    List<CodeChange> codeChanges //all code changes
    List<CodeChange> productionChanges //code changes in production code only
    List<CodeChange> testChanges //code changes in test code only
    List<CodeChange> unitChanges //code changes in unit test files only
    List<GherkinFile> gherkinChanges //code changes in feature files only
    List<StepDefinitionFile> stepChanges //code changes in step definition files only

    public String toString(){
         "$hash*${new Date(date*1000)}*$author*$message*${(codeChanges*.filename).toListString()}"
    }

}

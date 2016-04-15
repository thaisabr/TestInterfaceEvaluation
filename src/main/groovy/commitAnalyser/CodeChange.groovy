package commitAnalyser

import taskAnalyser.task.GherkinFile
import taskAnalyser.task.StepDefinitionFile

/***
 * Represents a code change by a commit.
 */
class CodeChange {

    String filename
    def type //add file, remove file, change file (add, change or remove lines), copy file or renaming file
    List<Integer> lines //it is not valid for changes in gherkin file or step definition file

    GherkinFile gherkinFile //to refactoring in the future
    StepDefinitionFile stepFile //to refactoring in the future

    @Override
    public String toString(){
        "name: $filename; change: $type; lines: $lines"
    }

}

package commitAnalyser

/***
 * Represents a git commit.
 */
class Commit {

    String hash
    String message
    List<CodeChange> codeChanges //all code changes
    List<CodeChange> productionChanges //code changes in production code only
    List<CodeChange> testChanges //code changes in test code only
    List<CodeChange> gherkinChanges //code changes in feature files only
    List<CodeChange> unitChanges //code changes in unit test files only
    String author
    long date

    public String toString(){
         "$hash*${new Date(date*1000)}*$author*$message*${(codeChanges*.filename).toListString()}"
    }

}

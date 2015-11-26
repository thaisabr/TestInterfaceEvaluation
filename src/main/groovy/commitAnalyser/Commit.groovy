package commitAnalyser

/***
 * Represents a git commit.
 */
class Commit {

    String hash
    String message
    List<CodeChange> codeChanges
    List<CodeChange> productionChanges
    List<CodeChange> testChanges
    List<CodeChange> gherkinChanges
    String author
    long date

    public String toString(){
         "$hash*${new Date(date*1000)}*$author*$message*${(codeChanges*.filename).toListString()}"
    }

}

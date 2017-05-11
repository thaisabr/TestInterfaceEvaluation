package br.ufpe.cin.tan.test

/***
 * Represents a file to be parsed and the methods that must be analysed to compute test-based task interface.
 */
class FileToAnalyse {

    /***
     * The file location.
     */
    String path

    /***
     * Methods to be analysed.
     */
    List<MethodToAnalyse> methods

    @Override
    String toString() {
        def text = "File to analyse: $path\n"
        methods.sort { it.line }.each { text += it.toString() + "\n" }
        text
    }

}

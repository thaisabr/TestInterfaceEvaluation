package br.ufpe.cin.tan.test

/***
 * Represents a step definition call made by other step definition.
 */
class StepCall {

    /***
     * The called expression. It is necessary to identify the called step definition.
     */
    String text

    /***
     * The file that made the call.
     */
    String path

    /***
     * The location of the call described by a line at path.
     */
    int line

    String parentType

    @Override
    String toString() {
        "Step call in '${parentType}' step definition at $path ($line): $text"
    }

}

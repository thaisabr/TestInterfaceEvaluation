package br.ufpe.cin.tan.test

/***
 * Represents a method to be analysed in order to compute test-based task interfaces.
 */
class MethodToAnalyse {

    /***
     * Method location described by file's line number.
     */
    int line

    /***
     * Method arguments value.
     */
    List<String> args = []

    String type

    @Override
    String toString() {
        "method at line: ${line}; args:$args; type: ${type}"
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        MethodToAnalyse that = (MethodToAnalyse) o

        if (line != that.line) return false
        if (args != that.args) return false

        return true
    }

    int hashCode() {
        int result
        result = line
        result = 31 * result + args.hashCode()
        return result
    }

}

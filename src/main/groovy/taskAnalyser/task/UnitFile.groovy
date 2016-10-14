package taskAnalyser.task

import commitAnalyser.CodeChange

/***
 * Represents a changed unit test file.
 */
class UnitFile implements CodeChange {

    /***
     * Gherkin file path (local path)
     */
    String path

    /***
     * All test unit file exercises a specific production class
     */
    def productionClass //keywords: name, path

    /***
     * Node list of unit tests that was changed by the commit. The assumption is only the test code changed
     * by the commit is really related to the task the commit represents. As consequence, only this information is used
     * to compute test-based task interfaces.
     */
    def tests //keys: name, path, lines

    @Override
    String toString() {
        def text = "Unit file: ${path}\nChanged tests:\n"
        tests.each { t ->
            text += "${t.name} (lines ${t.lines})\n"
        }
        return text
    }
}

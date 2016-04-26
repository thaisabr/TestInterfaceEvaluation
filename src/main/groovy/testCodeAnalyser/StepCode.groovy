package testCodeAnalyser

import gherkin.ast.Step
import util.Util


/***
 * Represents a match result among a Gherkin step and its implementation code.
 */
class StepCode {

    /***
     * Node that represents a step definition in a Gherkin file.
     */
    Step step

    /***
     * Location of the step implementation.
     */
    String codePath

    /***
     * Start line of step implementation.
     */
    int line

    /**
     * Argument values.
     */
    List<String> args

    @Override
    String toString() {
        def location = codePath - Util.getRepositoriesCanonicalPath()
        "${step.text}; location: $location ($line); args: $args"
    }
}

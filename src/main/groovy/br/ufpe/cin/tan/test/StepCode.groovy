package br.ufpe.cin.tan.test

import br.ufpe.cin.tan.util.Util
import gherkin.ast.Step

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

    /**
     * Real keyword.
     * */
    String type

    @Override
    String toString() {
        def location = codePath - Util.getRepositoriesCanonicalPath()
        "${type}: ${step.text}; location: $location ($line); args: $args"
    }

    void setType(String type) {
        if (type.endsWith(" ")) this.type = type
        else this.type = type + " "
    }

}

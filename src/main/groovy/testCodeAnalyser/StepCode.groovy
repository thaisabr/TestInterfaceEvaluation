package testCodeAnalyser

import gherkin.ast.Step

import java.text.Normalizer

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

    @Override
    String toString() {
        def header = "C:${File.separator}Users${File.separator}Thais${File.separator}Documents${File.separator}GitHub${File.separator}" +
                "TestInterfaceEvaluation${File.separator}repositories${File.separator}"
        def location = Normalizer.normalize(codePath, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "") - header
        return "${step.text} >>> $location (${line})"
        //return "${step.text} >>> $codePath (${line})"
    }
}

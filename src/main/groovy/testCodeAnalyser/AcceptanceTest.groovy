package testCodeAnalyser

import gherkin.ast.ScenarioDefinition
import util.Util

/***
 * Represents a match result among a Gherkin scenario definition and its implementation code.
 */
class AcceptanceTest {

    String gherkinFilePath
    ScenarioDefinition scenarioDefinition
    List<StepCode> stepCodes

    @Override
    String toString() {
        def text = "<Acceptance test>\n"
        text += "Location: $gherkinFilePath\n"
        text += "Scenario definition: (${scenarioDefinition.location.line-1}) ${scenarioDefinition.name}\n"
        text += "Steps code: \n"
        stepCodes.each{ step ->
            def location =  step.codePath - Util.getRepositoriesCanonicalPath()
            text += "(${step.line}) $location\n"
        }
        return text
    }
}

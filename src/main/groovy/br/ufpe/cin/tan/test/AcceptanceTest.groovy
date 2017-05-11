package br.ufpe.cin.tan.test

import gherkin.ast.ScenarioDefinition
import groovy.util.logging.Slf4j

/***
 * Represents a match result among a Gherkin scenario definition and its implementation code.
 */
@Slf4j
class AcceptanceTest {

    String gherkinFilePath
    ScenarioDefinition scenarioDefinition
    List<StepCode> stepCodes

    @Override
    String toString() {
        def text = "<Acceptance test>\n"
        text += "Location: $gherkinFilePath\n"
        text += "${scenarioDefinition.keyword}: (${scenarioDefinition.location.line}) ${scenarioDefinition.name}\n"
        text += "Steps:\n"
        scenarioDefinition.steps.each { step ->
            text += "(${step.location.line}) ${step.keyword} ${step.text}\n"
        }
        text += "Steps code:\n"
        stepCodes.each { step ->
            text += step.toString() + "\n"
        }
        return text
    }

}

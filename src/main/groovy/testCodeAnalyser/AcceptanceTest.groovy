package testCodeAnalyser

import gherkin.ast.ScenarioDefinition

import java.text.Normalizer

/***
 * Represents a match result among a Gherkin scenario definition and its implementation code.
 */
class AcceptanceTest {

    String gherkinFilePath
    ScenarioDefinition scenarioDefinition
    List<StepCode> stepCodes

    @Override
    String toString() {
        def header = "C:${File.separator}Users${File.separator}Thais${File.separator}Documents${File.separator}GitHub${File.separator}" +
                "TestInterfaceEvaluation${File.separator}repositories${File.separator}"

        def text = "<Acceptance test>\n"
        text += "Location: $gherkinFilePath\n"
        text += "Scenario definition: (${scenarioDefinition.location.line}) ${scenarioDefinition.name}\n"
        text += "Steps code: \n"
        stepCodes.each{
            def location =  Normalizer.normalize(it.codePath, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "") - header
            text += "(${it.line}) $location\n"
            //text += "(${it.line}) ${it.codePath}\n"
        }
        return text
    }
}

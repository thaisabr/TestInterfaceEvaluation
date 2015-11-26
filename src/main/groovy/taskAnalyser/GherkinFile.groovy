package taskAnalyser

import gherkin.ast.Feature
import gherkin.ast.ScenarioDefinition

/***
 * Represents a changed Gherkin File.
 */
class GherkinFile {

    String commitHash
    String path //local path
    Feature feature
    List<ScenarioDefinition> changedScenarioDefinitions

    @Override
    String toString() {
        def text = "Gherkin file: ${path}\nFeature: ${feature.name}\nChanged scenario definitions:\n"
        changedScenarioDefinitions.each{ definition ->
            text += "Scenario (line ${definition.location.line}): ${definition.name}\n"
        }
        return text
    }
}

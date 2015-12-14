package taskAnalyser

import gherkin.ast.Feature
import gherkin.ast.ScenarioDefinition

/***
 * Represents a changed Gherkin File.
 */
class GherkinFile {

    /***
     * Identifies the commit responsible for the code change
     */
    String commitHash

    /***
    * Gherkin file path (local path)
     */
    String path

    /***
     * Node that represents a feature in a gherkin file and its all elements
     */
    Feature feature

    /***
     * Node list of scenario definitions that was changed by the commit. The assumption is only the test code changed
     * by the commit is really related to the task the commit represents. As consequence, only this information is used
     * to compute test-based task interfaces.
     */
    List<ScenarioDefinition> changedScenarioDefinitions = []

    @Override
    String toString() {
        def text = "Gherkin file: ${path}\nFeature: ${feature.name}\nChanged scenario definitions:\n"
        changedScenarioDefinitions.each{ definition ->
            text += "Scenario (line ${definition.location.line}): ${definition.name}\n"
        }
        return text
    }
}

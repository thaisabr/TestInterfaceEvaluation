package taskAnalyser.task

import commitAnalyser.CodeChange
import gherkin.ast.Feature
import gherkin.ast.ScenarioDefinition

/***
 * Represents a changed Gherkin File.
 */
class GherkinFile implements CodeChange {

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

    /***
     * Textual information to compute text based interfaces for tasks.
     */
    List<String> changedScenarioDefinitionsText = []

    /***
     * Textual information about feature and background. They are common for all scenario definition in a Gherkin file.
     */
    String baseText

    @Override
    String toString() {
        def text = "Gherkin file: ${path}\nFeature: ${feature.name}\n"
        if(feature.background){
            text += "Background (line ${feature.background.location.line}):${feature.background.name}\n"
            feature.background.steps.each{
                text += "${it.keyword}: ${it.text}\n"
            }
        }
        text += "Changed scenario definitions:\n"
        changedScenarioDefinitions.each{ definition ->
            text += "${definition.keyword} (line ${definition.location.line}): ${definition.name}\n"
        }
        return text
    }

    def getText(){
        baseText + "\n" + (changedScenarioDefinitionsText.join("\n"))
    }

}

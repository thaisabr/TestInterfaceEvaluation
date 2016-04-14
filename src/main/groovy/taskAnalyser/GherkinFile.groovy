package taskAnalyser

import gherkin.ast.Feature
import gherkin.ast.ScenarioDefinition
import gherkin.ast.ScenarioOutline
import groovy.util.logging.Slf4j

/***
 * Represents a changed Gherkin File.
 */
@Slf4j
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

    /***
     * Textual information to compute text based interfaces for tasks.
     */
    List<String> changedScenarioDefinitionsText = []
    String baseText

    @Override
    String toString() {
        def text = "Gherkin file: ${path}\nFeature: ${feature.name}\nBackground (line ${feature.background.location.line}):${feature.background.name}\n"
        feature.background.steps.each{
            text += "${it.keyword}: ${it.text}\n"
        }
        text += "\nChanged scenario definitions:\n"
        changedScenarioDefinitions.each{ definition ->
            text += "${definition.keyword} (line ${definition.location.line}): ${definition.name}\n"
        }
        return text
    }

    def getText(){
        baseText + "\n" + (changedScenarioDefinitionsText.join("\n"))
    }

    /*
    String extractBackgroundArgs(){
        def text = ""
        def args = []
        if(feature.background) {
            feature.background.steps.each { step ->
                text += "${step.keyword}: ${step.text}"
                step.argument.rows.each{ row ->
                    args += row.cells.value
                }
            }
        }
        if(!text.empty) [text:text, args:args]
        else []
    }


    static extractArgs(ScenarioDefinition definition){
        def text = ""
        def args = []
        definition?.steps?.each{ step ->
            text = "${step.keyword}: ${step.text}"
            if(step.argument) {
                step.argument.rows.each{ row ->
                    args += row.cells.value
                }
            }
        }
        if(!text.empty) [text:text, args:args]
        else []
    }

    static extractArgs(ScenarioOutline scenario){
        def args = []
        scenario.examples.each { example ->
            args += example.tableHeader.cells*.value
            args += example.tableBody.cells*.value
        }
        args
    }*/

}

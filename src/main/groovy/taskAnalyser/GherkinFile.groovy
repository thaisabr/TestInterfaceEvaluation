package taskAnalyser

import gherkin.ast.Feature
import gherkin.ast.ScenarioDefinition


class GherkinFile {

    String commitHash
    String path
    Feature feature
    List<ScenarioDefinition> changedScenarioDefinitions

}

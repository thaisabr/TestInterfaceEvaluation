package commitAnalyser

import gherkin.Parser
import gherkin.ParserException
import gherkin.ast.Feature
import gherkin.ast.ScenarioDefinition
import groovy.util.logging.Slf4j
import org.eclipse.jgit.revwalk.RevCommit
import taskAnalyser.task.GherkinFile
import taskAnalyser.task.StepDefinition

@Slf4j
class GherkinManager {

    static Set compilationErrors = [] as Set

    static Feature parseGherkinFile(String content, String filename, String sha) {
        Feature feature = null
        if (!content || content == "") {
            log.warn "Problem to parse Gherkin file '$filename'. Reason: The commit deleted it."
            compilationErrors += [path: filename, msg: "Commit $sha deleted it."]
        } else {
            try {
                Parser<Feature> parser = new Parser<>()
                feature = parser.parse(content)
            } catch (ParserException ex) {
                log.warn "Problem to parse Gherkin file '$filename' (commit $sha). ${ex.class}: ${ex.message}."
                compilationErrors += [path: filename, msg: ex.class]
            }
        }
        feature
    }

    static boolean equals(ScenarioDefinition sd1, ScenarioDefinition sd2) {
        def result = true
        for (int i = 0; i < sd1.steps.size(); i++) {
            def step1 = sd1.steps.get(i)
            def step2 = sd2.steps.get(i)
            if (step1.text != step2.text || step1.keyword != step2.keyword) {
                result = false
                break
            }
        }
        result
    }

    static boolean equals(StepDefinition sd1, StepDefinition sd2) {
        def result = true
        for (int i = 0; i < sd1.body.size(); i++) {
            def step1 = sd1.body.get(i)
            def step2 = sd2.body.get(i)
            if (step1 != step2) {
                result = false
                break
            }
        }
        result
    }

    static extractCommonText(locations, Feature feature, lines) {
        def text = ""
        if (!feature) return text
        def featureLocation = feature.location.line
        def featureIndex = locations.indexOf(featureLocation)

        if (featureIndex < locations.size() - 1) {
            //excludes tag of next scenario definition
            int max = locations.get(featureIndex + 1) - 1 as int
            def scenDef = feature.scenarioDefinitions?.first()
            if (!scenDef?.tags?.empty) max--

            for (int i = featureLocation - 1; i < max; i++) {
                text += lines.get(i).trim() + "\n"
            }

        } else {
            for (int i = featureLocation - 1; i < lines.size(); i++) {
                text += lines.get(i).trim() + "\n"
            }
        }
        text
    }

    static extractTextFromGherkin(Feature feature, GherkinFile gherkinFile) {
        def locations = feature.scenarioDefinitions*.location*.line.flatten().sort()
        def lines = gherkinFile.featureFileText.readLines()

        gherkinFile.baseText = extractCommonText(locations, feature, lines)
        gherkinFile.changedScenarioDefinitionsText = []
        List<ScenarioDefinition> scenDefinitions = gherkinFile.changedScenarioDefinitions
        scenDefinitions.each { change ->
            def text = ""
            def initialLine = change.location.line
            if (!change.tags.empty) text += change.tags*.name.flatten().join(" ") + "\n"
            def index = locations.indexOf(initialLine)

            if (index < locations.size() - 1) {
                //excludes tag of next scenario definition
                int max = locations.get(index + 1) - 1 as int
                def scenDef = scenDefinitions.find { it.location.line == max + 1 }
                if (!scenDef?.tags?.empty) max--

                //extracts all text until it reaches the next scenario definition
                for (int i = initialLine - 1; i < max; i++) {
                    text += lines.get(i).trim() + "\n"
                }
            } else { //the scenario definition is the last one
                for (int i = initialLine - 1; i < lines.size(); i++) {
                    text += lines.get(i).trim() + "\n"
                }
            }
            gherkinFile.changedScenarioDefinitionsText += text.replaceAll("(?m)^\\s", "")
        }
    }

    /***
     * Identifies scenarios definitions at added gherkin files (features) by the first commit of the repository.
     * It is used only when dealing with done tasks.
     */
    static GherkinFile extractGherkinAdds(RevCommit commit, String content, String path) {
        GherkinFile changedGherkinFile = null
        def newFeature = parseGherkinFile(content, path, commit.name)
        if(newFeature && newFeature.scenarioDefinitions && !newFeature.scenarioDefinitions.empty){
            changedGherkinFile = new GherkinFile(path: path, feature: newFeature, changedScenarioDefinitions: newFeature.scenarioDefinitions)
        }
        changedGherkinFile
    }

}

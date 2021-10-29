package br.ufpe.cin.tan.analysis.task

import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.exception.CloningRepositoryException
import br.ufpe.cin.tan.util.RegexUtil
import br.ufpe.cin.tan.util.Util
import gherkin.AstBuilder
import gherkin.Parser
import gherkin.ast.Background
import gherkin.ast.Feature
import gherkin.ast.GherkinDocument
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.util.logging.Slf4j

import java.util.regex.Matcher

/***
 * Represents a new task, that is, a task that contains test code but the production code is not done yet. The task is
 * used to compute test-based task interfaces and prevent code conflicts.
 */
@Slf4j
class TodoTask extends Task {

    List<ChangedGherkinFile> testDescription

    /***
     * @param rootDirectory repository path. It can be a URL or local folder.
     * @param id task code to identify it.
     * @param scenarios a list of map objects that identifies a Gherkin file and its scenarios that
     *        are related to the task, by keywords 'path' and 'lines' respectively.
     */
    TodoTask(String rootDirectory, int id, scenarios) throws CloningRepositoryException {
        super(rootDirectory, id)
        testCodeAnalyser.configureProperties()
        findAllRelatedGherkinFile(scenarios)
    }

    TodoTask(String language, String gemsPath, String frameworkPath, String rootDirectory, int id, scenarios)
            throws CloningRepositoryException {
        super(language, gemsPath, frameworkPath, rootDirectory, id)
        testCodeAnalyser.configureProperties()
        findAllRelatedGherkinFile(scenarios)
    }

    @Override
    ITest computeTestBasedInterface() {
        def taskInterface = null
        TimeDuration timestamp = null

        if (!testDescription.empty) {
            log.info "Task id: $id"
            def initTime = new Date()
            taskInterface = testCodeAnalyser.computeInterfaceForTodoTask(testDescription)
            def endTime = new Date()
            use(TimeCategory) {
                timestamp = endTime - initTime
            }
            taskInterface.timestamp = timestamp
        }

        taskInterface
    }

    @Override
    List<ChangedGherkinFile> getAcceptanceTests() {
        testDescription
    }

    private configureFeaturePath(String entry) {
        String featureName = ""
        if (entry.contains(Util.GHERKIN_FILES_RELATIVE_PATH)) {
            def index = entry.indexOf(Util.GHERKIN_FILES_RELATIVE_PATH)
            featureName = entry.substring(index).replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
        testCodeAnalyser.repositoryPath + File.separator + featureName
    }

    private findAllRelatedGherkinFile(scenarios) {
        List<ChangedGherkinFile> gherkinFiles = []
        Parser<GherkinDocument> parser = new Parser<>(new AstBuilder())
        scenarios?.each { scenario ->
            try {
                def path = configureFeaturePath(scenario.path)
                def reader = new FileReader(path)
                Feature feature = parser.parse(reader)?.feature
                reader.close()
                def scenarioDefinitions = feature?.children?.findAll {
                    it.location.line in scenario.lines && !(it instanceof Background)
                }
                if (scenarioDefinitions) {
                    gherkinFiles += new ChangedGherkinFile(path: scenario.path, feature: feature,
                            changedScenarioDefinitions: scenarioDefinitions)
                }

            } catch (FileNotFoundException ex) {
                log.warn "Problem to parse Gherkin file: ${ex.message}"
            }
        }

        testDescription = gherkinFiles
    }

}

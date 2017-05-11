package br.ufpe.cin.tan.analysis.task

import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import gherkin.Parser
import gherkin.ast.Feature
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.exception.CloningRepositoryException

/***
 * Represents a new task, that is, a task that contains test code but the production code is not done yet. The task is
 * used to compute test-based task interfaces and prevent code conflicts.
 */
@Slf4j
class TodoTask extends Task {

    List<ChangedGherkinFile> testDescription

    /***
     *
     * @param rootDirectory repository path. It can be a URL or local folder.
     * @param isRemote if true, means it is a remote repository (the first parameter is a URL)
     * @param id task code to identify it.
     * @param scenarios a list of map objects that identifies a Gherkin file and its scenarios that
     *        are related to the task, by keywords 'path' and 'lines' respectively.
     */
    TodoTask(String rootDirectory, boolean isRemote, String id, scenarios) throws CloningRepositoryException {
        super(rootDirectory, id)

        testCodeParser.configureProperties()

        if (isRemote) testDescription = findAllRelatedGherkinFile(gitRepository.localPath, scenarios)
        else testDescription = findAllRelatedGherkinFile(rootDirectory, scenarios)
    }

    private static List<ChangedGherkinFile> findAllRelatedGherkinFile(String rootDirectory, scenarios) {
        Parser<Feature> featureParser = new Parser<>()
        List<ChangedGherkinFile> gherkinFiles = []

        scenarios.each { scenario ->
            try {
                def path = rootDirectory + File.separator + Util.GHERKIN_FILES_RELATIVE_PATH + File.separator + scenario.path
                def reader = new FileReader(path)
                Feature feature = featureParser.parse(reader)
                reader.close()
                def scenarioDefinitions = feature?.scenarioDefinitions?.findAll { it.location.line in scenario.lines }
                if (scenarioDefinitions) {
                    gherkinFiles += new ChangedGherkinFile(path: scenario.path, feature: feature, changedScenarioDefinitions: scenarioDefinitions)
                }

            } catch (FileNotFoundException ex) {
                log.warn "Problem to parse Gherkin file: ${ex.message}"
            }
        }

        return gherkinFiles
    }

    @Override
    ITest computeTestBasedInterface() {
        def taskInterface = null
        TimeDuration timestamp = null

        if (!testDescription.empty) {
            log.info "Task id: $id"
            def initTime = new Date()
            taskInterface = testCodeParser.computeInterfaceForTodoTask(testDescription)
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

}

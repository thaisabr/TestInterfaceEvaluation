package taskAnalyser

import gherkin.Parser
import gherkin.ast.Feature
import util.Util

/***
 * Represents a new task, that is, a task that contains test code but the production code is not done yet. The task is
 * used to compute test-based task interfaces and prevent code conflicts.
 */
class TodoTask extends Task {

    List<GherkinFile> testDescription

    /***
     *
     * @param rootDirectory repository path. It can be a URL or local folder.
     * @param isRemote if true, means it is a remote repository (the first parameter is a URL)
     * @param id task code to identify it.
     * @param scenarios a list of map objects that identifies a Gherkin file and its scenarios that
     *        are related to the task, by keywords 'path' and 'lines' respectively.
     */
    TodoTask(String rootDirectory, boolean isRemote, String id, def scenarios){
        super(rootDirectory, isRemote, id)

        testCodeParser.configureProperties()

        if(isRemote) testDescription = findAllRelatedGherkinFile(gitRepository.localPath, scenarios)
        else testDescription = findAllRelatedGherkinFile(rootDirectory, scenarios)
    }

    private static List<GherkinFile> findAllRelatedGherkinFile(String rootDirectory, def scenarios){
        Parser<Feature> featureParser = new Parser<>()
        List<GherkinFile> gherkinFiles = []

        scenarios.each{ scenario ->
            try{
                def path = rootDirectory+Util.GHERKIN_FILES_RELATIVE_PATH+File.separator+scenario.path
                def reader = new FileReader(path)
                Feature feature = featureParser.parse(reader)
                reader.close()
                def scenarioDefinitions = feature?.scenarioDefinitions?.findAll{ it.location.line in scenario.lines }
                if(scenarioDefinitions){
                    gherkinFiles += new GherkinFile(commitHash:null, path:scenario.path,
                            feature:feature, changedScenarioDefinitions:scenarioDefinitions)
                }

            } catch(FileNotFoundException ex){
                println "Problem to parse Gherkin file: ${ex.message}"
            }
        }

        return gherkinFiles
    }

    @Override
    TaskInterface computeTestBasedInterface() {
        if(!testDescription.isEmpty()) {
            println "Task id: $id"
            testCodeParser.computeInterfaceForTodoTask(testDescription)
        }
        else return null
    }

}

package gherkinParser

import gherkin.formatter.JSONFormatter
import gherkin.formatter.JSONPrettyFormatter
import gherkin.parser.Parser
import gherkin.util.FixJava
import groovy.json.JsonSlurper
import util.Util

class ParserGherkinJson {

    private static void parse(String featurePath, String jsonPath) {
        String gherkin = FixJava.readReader(new InputStreamReader( new FileInputStream(featurePath), "UTF-8"))
        StringBuilder json = new StringBuilder()
        JSONFormatter formatter = new JSONPrettyFormatter(json)
        Parser parser = new Parser(formatter)
        parser.parse(gherkin, featurePath, 0)
        formatter.done()
        formatter.close()
        generateJson(json, jsonPath)
    }

    private static void generateJson(StringBuilder json, String jsonPath){
        FileWriter file = new FileWriter(jsonPath)
        file.write(json.toString())
        file.flush()
        file.close()
    }

    /***
     * Parses a Gherkin file to recovery information about all its scenarios.
     * @param filePath path of gherkin file
     * @return all scenarios defined at the input file
     */
    static List<Scenario> extractScenarios(String filePath){
        List<Scenario> scenarios = []
        def jsonPath = Util.getJsonFileName(filePath)
        try {
            parse(filePath, jsonPath)

            FileReader fileReader = new FileReader(new File(jsonPath))
            def slurper = new JsonSlurper()
            def jsonContent = slurper.parse(fileReader)
            jsonContent = jsonContent.elements[0].findAll{ it.type == "scenario" } //other type = scenario output (improve it)

            //more information could be extracted by parsing steps code
            jsonContent?.each{
                scenarios += new Scenario(file:filePath, name:it.name, line:it.line)
            }

            fileReader.close()
        } catch (FileNotFoundException e){
            println "Error while parsing Gherkin file: ${e.message}"
        }

        return scenarios
    }

    /***
     * Parses a Gherkin file to recovery information about some of its scenarios.
     * @param filePath path of gherkin file
     * @param lines first line of each scenario
     * @return the scenarios of interest
     */
    static List<Scenario> extractScenariosByLine(String filePath, int... lines){
        return extractScenarios(filePath).findAll { it.line in lines }
    }

}

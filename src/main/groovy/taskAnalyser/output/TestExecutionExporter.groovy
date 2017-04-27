package taskAnalyser.output

import taskAnalyser.task.AnalysisResult
import testCodeAnalyser.AcceptanceTest
import util.CsvUtil


class TestExecutionExporter {

    String testFile
    AnalysisResult analysisResult

    TestExecutionExporter(String testFile, AnalysisResult analysisResult){
        this.testFile = testFile
        this.analysisResult = analysisResult
    }

    private static extractTests(task){
        def scenarios = ""
        Set<AcceptanceTest> tests = task.itest.foundAcceptanceTests
        tests.each{ test ->
            def lines = test.scenarioDefinition*.location.line
            scenarios += test.gherkinFilePath + "(" + lines.join(",") + ")" + ";"
        }
        if(scenarios.size()>1) scenarios = scenarios.substring(0, scenarios.size()-1)
        scenarios
    }

    private static extractGems(task){
        def rails = ""
        def coverage = ""
        if(task.gems.size()>0) {
            rails = task.gems.first().replaceAll(/[^\.\d]/,"")
            def gems = task.gems.subList(1, task.gems.size()).findAll{ it == "coveralls" || it == "simplecov"}
            if(!gems.empty) coverage = gems.join(",")
        }
        [rails:rails, coverage:coverage]
    }

    def save(){
        List<String[]> content = []

        def tasks = analysisResult.validTasks.findAll{ it.itest.foundAcceptanceTests.size() > 0 }
        if(tasks.empty) return

        String[] url = [tasks.first().doneTask.gitRepository.url]
        content +=  url
        String[] header = ["TASK", "RAILS", "COVERAGE", "TESTS"]
        content += header

        tasks.each { task ->
            def scenarios = extractTests(task)
            def gems = extractGems(task)
            String[] line = [task.doneTask.id, gems.rails, gems.coverage, scenarios]
            content += line
        }
        CsvUtil.write(testFile, content)
    }
}

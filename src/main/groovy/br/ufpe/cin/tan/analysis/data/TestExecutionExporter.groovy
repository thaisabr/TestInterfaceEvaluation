package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.test.AcceptanceTest
import br.ufpe.cin.tan.util.CsvUtil

class TestExecutionExporter {

    String testFile
    List<AnalysedTask> tasks

    TestExecutionExporter(String testFile, List<AnalysedTask> tasks){
        this.testFile = testFile
        this.tasks = tasks
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

    private static extractCoverageGems(AnalysedTask task){
        def coverage = ""
        if(!task.coverageGems.empty) coverage = task.coverageGems.join(",")
        coverage
    }

    def save(){
        List<String[]> content = []

        if(!tasks || tasks.empty) return

        def url = tasks.first().doneTask.gitRepository.url
        content += ["Repository", url] as String[]
        String[] header = ["TASK", "HASH", "RUBY", "RAILS", "COVERAGE", "TESTS"]
        content += header

        tasks.each { task ->
            def scenarios = extractTests(task)
            def coverage = extractCoverageGems(task)
            String[] line = [task.doneTask.id, task.doneTask.commits.last().hash, task.ruby, task.rails, coverage, scenarios]
            content += line
        }
        CsvUtil.write(testFile, content)
    }
}

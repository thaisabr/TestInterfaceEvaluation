package br.ufpe.cin.tan.main

import br.ufpe.cin.tan.analysis.TaskAnalyser
import br.ufpe.cin.tan.analysis.data.csvExporter.AggregatedStatisticsExporter
import br.ufpe.cin.tan.util.Util
import groovy.util.logging.Slf4j

@Slf4j
class Main {

    int limit

    Main(taskLimit) {
        this.limit = taskLimit
    }

    static void main(String[] args) {
        int limit = -1
        if (args) limit = Integer.parseInt(args[0])
        Main mainObj = new Main(limit)
        if (Util.RUNNING_ALL_CONFIGURATIONS) {
            mainObj.runAllAnalysisConfig()
        } else {
            if (Util.RANDOM_BASELINE) mainObj.runAnalysisAndRandomResult()
            else mainObj.runAnalysis()
        }
    }

    private runAnalysis() {
        def analysers = []
        if (Util.MULTIPLE_TASK_FILES) { // analyse a set of csv files
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each {
                def taskAnalyser = new TaskAnalyser(it, limit)
                taskAnalyser.analyseAll()
                analysers += taskAnalyser
            }
            AggregatedStatisticsExporter statisticsExporter = new AggregatedStatisticsExporter(analysers)
            statisticsExporter.generateAggregatedStatistics()
        } else { //analyse a csv file
            def taskAnalyser = new TaskAnalyser(Util.TASKS_FILE, limit)
            taskAnalyser.analyseAll()
        }
    }

    private runAnalysisAndRandomResult() {
        def analysers = []
        if (Util.MULTIPLE_TASK_FILES) { // analyse a set of csv files
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each {
                def taskAnalyser = new TaskAnalyser(it, limit)
                taskAnalyser.analyseAll()
                taskAnalyser.generateRandomResult()
                analysers += taskAnalyser
            }
            AggregatedStatisticsExporter statisticsExporter = new AggregatedStatisticsExporter(analysers)
            statisticsExporter.generateAggregatedStatistics()
        } else { //analyse a csv file
            def taskAnalyser = new TaskAnalyser(Util.TASKS_FILE, limit)
            taskAnalyser.analyseAll()
            taskAnalyser.generateRandomResult()
        }
    }

    private runAllAnalysisConfig() {
        //added
        Util.setRunningConfiguration(false, true, "output_added")
        runAnalysis()

        //added_when
        Util.setRunningConfiguration(true, true, "output_added_when")
        runAnalysis()

        //all
        Util.setRunningConfiguration(false, false, "output_all")
        if (Util.RANDOM_BASELINE) runAnalysisAndRandomResult()
        else runAnalysis()

        //all_when
        Util.setRunningConfiguration(true, false, "output_all_when")
        runAnalysis()

    }
}

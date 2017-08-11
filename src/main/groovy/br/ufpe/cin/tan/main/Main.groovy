package br.ufpe.cin.tan.main

import br.ufpe.cin.tan.analysis.TaskAnalyser
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
        } else mainObj.runAnalysis()
    }

    private runAnalysis() {
        if (Util.MULTIPLE_TASK_FILES) { // analyse a set of csv files
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each { new TaskAnalyser(it, limit).analyseAll() }
        } else new TaskAnalyser(Util.TASKS_FILE, limit).analyseAll() //analyse a csv file
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
        runAnalysis()

        //all_when
        Util.setRunningConfiguration(true, false, "output_all_when")
        runAnalysis()

    }
}

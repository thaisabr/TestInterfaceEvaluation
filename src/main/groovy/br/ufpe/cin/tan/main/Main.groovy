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

    def analyseTasks() {
        if (limit > 0) analyseLimitedTask()
        else analyseAll()
    }

    static void main(String[] args) {
        int limit = -1
        if (args) limit = Integer.parseInt(args[0])
        Main obj = new Main(limit)
        obj.analyseTasks()
    }

    private analyseTask(String file) {
        TaskAnalyser analyser = new TaskAnalyser(file)
        analyser.analyseAll()
    }

    private analyseTaskLimited(String file) {
        TaskAnalyser analyser = new TaskAnalyser(file, limit)
        analyser.analyseAll()
    }

    private analyseAll() {
        if (Util.MULTIPLE_TASK_FILES) { // analyse a set of csv files
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each { analyseTask(it) }
        } else analyseTask(Util.TASKS_FILE) //analyse a csv file
    }

    private analyseLimitedTask() {
        if (Util.MULTIPLE_TASK_FILES) { // analyse a set of csv files
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each { analyseTaskLimited(it) }
        } else analyseTaskLimited(Util.TASKS_FILE) //analyse a csv file
    }

}

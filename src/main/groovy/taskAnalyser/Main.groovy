package taskAnalyser

import groovy.util.logging.Slf4j
import util.Util

@Slf4j
class Main {

    private static analyseTask(String file){
        TaskAnalyser analyser = new TaskAnalyser(file)
        analyser.analyseAll()
    }

    private static analyseTaskLimited(String file, int limit){
        TaskAnalyser analyser = new TaskAnalyser(file, limit)
        analyser.analyseAll()
    }

    private static analyseAll(){
        if(Util.MULTIPLE_TASK_FILES){ // analyse a set of csv files
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each { analyseTask(it) }
        }
        else analyseTask(Util.TASKS_FILE) //analyse a csv file
    }

    private static analyseTaskLimited(int limit){
        if(Util.MULTIPLE_TASK_FILES){ // analyse a set of csv files
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each { analyseTaskLimited(it, limit) }
        }
        else analyseTaskLimited(Util.TASKS_FILE, limit) //analyse a csv file
    }

    static analyseTasks(int taskLimit) {
        if (taskLimit > 0) analyseTaskLimited(taskLimit)
        else analyseAll()
    }

    static void main(String[] args) {
        int taskLimit = -1
        if(args) taskLimit = Integer.parseInt(args[0])
        analyseTasks(taskLimit)
    }

}

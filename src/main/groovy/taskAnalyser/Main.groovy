package taskAnalyser

import groovy.util.logging.Slf4j
import util.Util

@Slf4j
class Main {

    static analyseAll(){
        /* analyse a set of csv files */
        if(Util.MULTIPLE_TASK_FILES){
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each {
                TaskAnalyser analyser = new TaskAnalyser(it)
                analyser.analyseAll()
                //analyser.analysePrecisionAndRecall()
            }
        } else {
            /* analyse a csv file */
            TaskAnalyser analyser = new TaskAnalyser(Util.TASKS_FILE)
            analyser.analyseAll()
            //analyser.analysePrecisionAndRecall()
        }
    }

    static analyseLimited(int limit){
        /* analyse a set of csv files */
        if(Util.MULTIPLE_TASK_FILES){
            def cvsFiles = Util.findTaskFiles()
            cvsFiles?.each {
                TaskAnalyser analyser = new TaskAnalyser(it, limit)
                analyser.analyseAll()
                //analyser.analysePrecisionAndRecall()
            }
        } else {
            /* analyse a csv file */
            TaskAnalyser analyser = new TaskAnalyser(Util.TASKS_FILE, limit)
            analyser.analyseAll()
            //analyser.analysePrecisionAndRecall()
        }
    }

    static void main(String[] args) {
        int taskLimit = -1
        if(args) taskLimit = Integer.parseInt(args[0])

        if(taskLimit>0) analyseLimited(taskLimit)
        else analyseAll()
    }

}

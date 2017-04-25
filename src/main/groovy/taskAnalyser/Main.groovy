package taskAnalyser

import groovy.util.logging.Slf4j
import util.Util

@Slf4j
class Main {

    static void main(String[] args) {

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

}

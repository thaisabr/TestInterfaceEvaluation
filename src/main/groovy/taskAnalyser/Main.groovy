package taskAnalyser

import groovy.util.logging.Slf4j
import util.*

@Slf4j
class Main {

    public static void main(String[] args) {

        /* analyse a csv file */
        TaskAnalyser analyser1 = new TaskAnalyser(Util.TASKS_FILE)
        analyser1.analyseAll()
        //analyser1.analysePrecisionAndRecall()

        /* analyse a set of csv files */
        TaskAnalyser analyser2 = new TaskAnalyser(ConstantData.DEFAULT_TASKS_FOLDER)
        //analyser2.analyseAll()
       // analyser2.analysePrecisionAndRecall()

    }

}

package taskAnalyser

import groovy.util.logging.Slf4j
import util.ConstantData
import util.Util

@Slf4j
class Main {

    public static void main(String[] args){

        TaskAnalyser.analyseAllForProject(Util.TASKS_FILE)

        TaskAnalyser.analyseAllForMultipleProjects(ConstantData.DEFAULT_TASKS_FOLDER)

    }

}

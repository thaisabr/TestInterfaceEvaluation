package taskAnalyser

import groovy.util.logging.Slf4j
import util.Util

@Slf4j
class Main {

    public static void main(String[] args){
        /*def cvsFiles = Util.findFilesFromDirectory("tasks")
        cvsFiles?.each{
            log.info "<  Analysing tasks from '$it'  >"
            TaskAnalyser.analyse(it)
        }*/

        TaskAnalyser.analyse()
    }

}

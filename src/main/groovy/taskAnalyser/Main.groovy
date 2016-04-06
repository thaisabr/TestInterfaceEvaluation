package taskAnalyser

import groovy.util.logging.Slf4j
import util.OutputManager
import util.Util

@Slf4j
class Main {

    static analyseMultipleProjects(){
        def cvsFiles = Util.findFilesFromDirectory("tasks")
        cvsFiles?.each{
            log.info "<  Analysing tasks from '$it'  >"
            TaskAnalyser.analyseGherkinInterface(it) //TaskAnalyser.analyseInterface(it)
        }

        cvsFiles = Util.findFilesFromDirectory("output")?.findAll{ it.endsWith(".csv") }
        cvsFiles?.each{
            log.info "<  Organizing tasks from '$it'  >"
            OutputManager.organizeResult(it)
        }

        cvsFiles = Util.findFilesFromDirectory("output")?.findAll{ it.endsWith("-organized.csv") }
        cvsFiles?.each{
            log.info "<  Analysing similarity among tasks from '$it'  >"
            OutputManager.analyseSimilarity(it)
        }
    }

    static analyseProject(){
        TaskAnalyser.analyseGherkinInterface() //TaskAnalyser.analyseInterface()
        OutputManager.organizeResult("output${File.separator}evaluation_result.csv")
        OutputManager.analyseSimilarity("output${File.separator}evaluation_result-organized.csv")
    }

    public static void main(String[] args){
        //analyseMultipleProjects()
        analyseProject()
    }

}

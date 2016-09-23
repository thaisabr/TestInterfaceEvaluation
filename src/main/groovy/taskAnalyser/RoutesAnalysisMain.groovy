package taskAnalyser

import au.com.bytecode.opencsv.CSVWriter
import commitAnalyser.Commit
import groovy.util.logging.Slf4j
import taskAnalyser.task.RailsTask
import util.ConstantData
import util.Util

@Slf4j
class RoutesAnalysisMain {

    static computeTaskData(List<RailsTask> tasks, String filename){
        log.info "Tasks: ${tasks.size()}"
        List<String[]> result = []

        tasks?.each{ task ->
            def r = task.routeFileChanged()
            String[] value = [task.id, r.changedByTask, r.changedByOtherTask]
            if(r.changedByOtherTask) value[2] = r.commits
            result += value
        }

        File file = new File(filename)
        def outputFile = ConstantData.DEFAULT_EVALUATION_FOLDER+File.separator+file.name-ConstantData.CSV_FILE_EXTENSION+
                "_routes" + ConstantData.CSV_FILE_EXTENSION
        CSVWriter writer = new CSVWriter(new FileWriter(outputFile))
        String[] text = ["Tasks:", tasks.size()]
        writer.writeNext(text)
        text = ["TASK_ID", "CHANGE BY ITSELF", "CHANGE BY OTHERS"]
        writer.writeNext(text)
        writer.writeAll(result)
        writer.close()
    }

    static analyseAllForMultipleProjects(def folder){
        def cvsFiles = Util.findFilesFromDirectory(folder).findAll{ it.endsWith(ConstantData.CSV_FILE_EXTENSION)}
        cvsFiles?.each{
            def result1 = DataManager.extractProductionAndTestTasks(it)
            computeTaskData(result1.relevantTasks, it)
        }
    }

    static extractProductionAndTestTasks(String filename){
        List<String[]> entries = DataManager.readInputCSV(filename)
        List<String[]> relevantEntries = entries.findAll{ !it[4].equals("[]") && !it[5].equals("[]") }
        List<RailsTask> tasks = []

        relevantEntries.each { entry ->
            if(entry[2].size()>4){
                def hashes = entry[3].tokenize(',[]')*.trim()
                tasks += new RailsTask(entry[1], entry[2], hashes)
            }
        }

        [relevantTasks:tasks.sort{it.id}, allTasksQuantity:entries.size()]
    }

    public static void main(String[] args){
        def result1 = extractProductionAndTestTasks(Util.TASKS_FILE)
        computeTaskData(result1.relevantTasks, Util.TASKS_FILE)
        //analyseAllForMultipleProjects(ConstantData.DEFAULT_TASKS_FOLDER)
    }

}

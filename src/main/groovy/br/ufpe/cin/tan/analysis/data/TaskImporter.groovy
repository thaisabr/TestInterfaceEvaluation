package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.analysis.task.DoneTask
import br.ufpe.cin.tan.util.CsvUtil
import groovy.util.logging.Slf4j


@Slf4j
class TaskImporter {

    int URL_INDEX = 0
    int TASK_INDEX = 1
    int COMMITS_INDEX = 2
    int HASHES_INDEX = 3
    int PROD_FILES_INDEX = 4
    int TEST_FILES_INDEX = 5
    int MAX_TASK_SIZE = 80

    File file
    String url
    List<String[]> importedTasks
    List<String[]> ptImportedTasks
    List<String[]> bigTasks
    List<String[]> notPtImportedTasks //tasks extracted from the input CSV file that do not have production and test code
    List<DoneTask> falsePtTasks //tasks that have production and test code, but not gherkin scenarios
    List<DoneTask> candidateTasks //tasks extracted from the input CSV file that changed production code and gherkin scenarios

    TaskImporter(File file){
        this.file = file
        importTasksFromCsv()
        updateTasks()
        if(importedTasks.size()>0) url = importedTasks.first()[URL_INDEX]
        else url = ""
        log.info "All tasks imported from '${file.path}': ${importedTasks.size()}"
        log.info "Big tasks (more than ${MAX_TASK_SIZE} commits): ${bigTasks.size()}"
        log.info "Invalid imported tasks (do not have production and test code or big tasks): ${notPtImportedTasks.size()}"
        log.info "Relevant imported tasks: ${ptImportedTasks.size()}"
    }

    def extractPtTasks(){
        falsePtTasks = []
        List<DoneTask> doneTasks = []
        try {
            ptImportedTasks.each { entry ->
                def hashes = entry[HASHES_INDEX].tokenize(',[]')*.trim()
                def task = new DoneTask(entry[URL_INDEX], entry[TASK_INDEX], hashes)
                if(task.hasTest()) doneTasks += task
                else falsePtTasks += task
            }
        } catch (Exception ex) {
            log.error "Error while extracting tasks from CSV file."
            ex.stackTrace.each{ log.error it.toString() }
            doneTasks = []
        }
        candidateTasks = doneTasks.sort{ it.id }
    }

    def extractPtTasks(int begin, int end){
        falsePtTasks = []
        List<DoneTask> doneTasks = []
        List<String[]> entries = ptImportedTasks.subList(begin, end)
        try {
            entries.each { entry ->
                def hashes = entry[HASHES_INDEX].tokenize(',[]')*.trim()
                def task = new DoneTask(entry[URL_INDEX], entry[TASK_INDEX], hashes)
                if(task.hasTest()) doneTasks += task
                else falsePtTasks += task
            }
        } catch (Exception ex) {
            log.error "Error while extracting tasks from CSV file."
            ex.stackTrace.each{ log.error it.toString() }
            doneTasks = []
        }
        candidateTasks = doneTasks.sort { it.id }
    }

    private importTasksFromCsv() {
        List<String[]> entries = CsvUtil.read(file.path)?.unique { it[TASK_INDEX] }
        entries.remove(0)
        importedTasks = entries
    }

    private updateTasks(){
        falsePtTasks = []
        candidateTasks = []
        bigTasks = importedTasks.findAll { (it[COMMITS_INDEX] as int) > MAX_TASK_SIZE }
        def validTasks = importedTasks - bigTasks
        ptImportedTasks = validTasks.findAll {
            ((it[PROD_FILES_INDEX] as int) > 0 && (it[TEST_FILES_INDEX] as int) > 0)
        }
        notPtImportedTasks = importedTasks - ptImportedTasks
    }

}

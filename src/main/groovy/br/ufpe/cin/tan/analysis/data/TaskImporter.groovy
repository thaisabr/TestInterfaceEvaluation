package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.analysis.task.DoneTask
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util
import groovy.util.logging.Slf4j


@Slf4j
class TaskImporter {

    final int URL_INDEX = 0
    final int TASK_INDEX = 1
    final int COMMITS_INDEX = 2
    final int HASHES_INDEX = 3
    final int PROD_FILES_INDEX = 4
    final int TEST_FILES_INDEX = 5
    final int LAST_COMMIT = 6

    File file
    String url
    List<String[]> importedTasks
    List<String[]> ptImportedTasks
    List<String[]> bigTasks

    //tasks extracted from the input CSV file that do not have production and test code
    List<String[]> notPtImportedTasks

    //tasks that have production and test code, but not gherkin scenarios
    List<DoneTask> falsePtTasks

    //tasks extracted from the input CSV file that changed production code and gherkin scenarios
    List<DoneTask> candidateTasks

    TaskImporter(File file) {
        this.file = file
        importTasksFromCsv()
        updateTasks()
        if (importedTasks.size() > 0) url = importedTasks.first()[URL_INDEX]
        else url = ""
        printInfo()
    }

    def printInfo() {
        log.info "All tasks imported from '${file.path}': ${importedTasks.size()}"
        log.info "Big tasks (more than ${Util.TASK_MAX_SIZE} commits): ${bigTasks.size()}"
        log.info "Invalid imported tasks (do not have production and test code or big tasks): ${notPtImportedTasks.size()}"
        log.info "Relevant imported tasks: ${ptImportedTasks.size()}"
    }

    def extractPtTasks() {
        extractTasks(ptImportedTasks)
    }

    def extractPtTasks(int begin, int end) {
        List<String[]> entries = ptImportedTasks.subList(begin, end)
        extractTasks(entries)
    }

    //Useful to IRandom
    def extractPtTasks(boolean basic) {
        extractTasks(ptImportedTasks, basic)
    }

    //Useful to IRandom
    def extractPtTasks(int begin, int end, boolean basic) {
        List<String[]> entries = ptImportedTasks.subList(begin, end)
        extractTasks(entries, basic)
    }

    //Useful to IRandom
    private extractTasks(List<String[]> entries, boolean basic) {
        falsePtTasks = []
        List<DoneTask> doneTasks = []
        try {
            entries.each { entry ->
                def hashes = entry[HASHES_INDEX].tokenize(',[]')*.trim()
                def task = new DoneTask(entry[URL_INDEX], entry[TASK_INDEX] as int, hashes, basic)
                doneTasks += task
            }
        } catch (Exception ex) {
            log.error "Error while extracting tasks from CSV file.\nError message: ${ex.message}"
            ex.stackTrace.each { log.error it.toString() }
            doneTasks = []
        }
        candidateTasks = doneTasks.sort { it.id }
        exportCandidateTasks()
    }

    private extractTasks(List<String[]> entries) {
        falsePtTasks = []
        List<DoneTask> doneTasks = []
        try {
            entries.each { entry ->
                def hashes = entry[HASHES_INDEX].tokenize(',[]')*.trim()
                def task = new DoneTask(entry[URL_INDEX], entry[TASK_INDEX] as int, hashes, entry[LAST_COMMIT])
                if (task.hasTest()) doneTasks += task
                else falsePtTasks += task
            }
        } catch (Exception ex) {
            log.error "Error while extracting tasks from CSV file.\nError message: ${ex.message}"
            ex.stackTrace.each { log.error it.toString() }
            doneTasks = []
        }
        candidateTasks = doneTasks.sort { it.id }
        exportCandidateTasks()
    }

    private exportCandidateTasks() {
        def tasksOfInterest = candidateTasks.collect { it.id }.sort()
        def originalFile = file.path - ConstantData.CSV_FILE_EXTENSION + "_original${ConstantData.CSV_FILE_EXTENSION}"
        File originalFileManager = new File(originalFile)
        if (!originalFileManager.exists()) originalFile = file.path
        def candidatesFile = file.path - ConstantData.CSV_FILE_EXTENSION + "_candidates${ConstantData.CSV_FILE_EXTENSION}"
        List<String[]> entries = CsvUtil.read(originalFile)?.unique { it[TASK_INDEX] }
        if (entries.empty) return
        def first = entries.get(0)
        entries.remove(0)
        def finalTasks = entries.findAll { (it[TASK_INDEX] as int) in tasksOfInterest }.sort { it[TASK_INDEX] as int }
        File file = new File(candidatesFile)
        if (file.exists()) {
            List<String[]> previous = CsvUtil.read(candidatesFile)?.unique { it[TASK_INDEX] }
            previous.remove(0)
            finalTasks = (finalTasks + previous).unique()
            finalTasks = finalTasks.sort { it[TASK_INDEX] as int }
        }
        List<String[]> content = []
        content += first
        content += finalTasks
        CsvUtil.write(candidatesFile, content)
    }

    private importTasksFromCsv() {
        List<String[]> entries = CsvUtil.read(file.path)?.unique { it[TASK_INDEX] }
        entries.remove(0)
        importedTasks = entries.sort { it[TASK_INDEX] as int }
    }

    private updateTasks() {
        falsePtTasks = []
        candidateTasks = []
        bigTasks = importedTasks.findAll { (it[COMMITS_INDEX] as int) > Util.TASK_MAX_SIZE }
        def validTasks = importedTasks - bigTasks
        ptImportedTasks = validTasks.findAll {
            ((it[PROD_FILES_INDEX] as int) > 0 && (it[TEST_FILES_INDEX] as int) > 0)
        }
        notPtImportedTasks = importedTasks - ptImportedTasks
    }

}

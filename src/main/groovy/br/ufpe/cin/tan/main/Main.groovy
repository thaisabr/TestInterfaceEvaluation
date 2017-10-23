package br.ufpe.cin.tan.main

import br.ufpe.cin.tan.analysis.TaskAnalyser
import br.ufpe.cin.tan.analysis.data.csvExporter.AggregatedStatisticsExporter
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util
import groovy.util.logging.Slf4j

@Slf4j
class Main {

    List<String> folders
    int limit

    Main(taskLimit) {
        this.limit = taskLimit
        folders = ["output_added_when", "output_added", "output_all_when", "output_all"]
    }

    static void main(String[] args) {
        int limit = -1
        if (args) limit = Integer.parseInt(args[0])
        Main mainObj = new Main(limit)

        if (Util.RUNNING_ALL_CONFIGURATIONS) {
            if (Util.MULTIPLE_TASK_FILES) mainObj.runAllConfigsForMultipleFiles()
            else mainObj.runAllConfig(Util.TASKS_FILE)
        } else {
            if (Util.MULTIPLE_TASK_FILES) mainObj.runAnalysisForMultipleFiles()
            else {
                def analyser = mainObj.runAnalysis(Util.TASKS_FILE)
                if (Util.RANDOM_BASELINE) analyser.generateRandomResult()
            }
        }
    }

    private TaskAnalyser runAnalysis(String inputTasksFile) {
        def taskAnalyser = new TaskAnalyser(inputTasksFile, limit)
        taskAnalyser.analyseAll()
        taskAnalyser
    }

    private runAnalysisForMultipleFiles() {
        def cvsFiles = Util.findTaskFiles()
        cvsFiles?.each {
            def analyser = runAnalysis(it)
            if (Util.RANDOM_BASELINE) analyser.generateRandomResult()
        }
        AggregatedStatisticsExporter statisticsExporter = new AggregatedStatisticsExporter(ConstantData.DEFAULT_EVALUATION_FOLDER)
        statisticsExporter.generateAggregatedStatistics()
    }

    private runAllConfigsForMultipleFiles() {
        def cvsFiles = Util.findTaskFiles()
        cvsFiles?.each {
            runAllConfig(it)
        }
        generateAgreggatedStatistics()
    }

    private generateAgreggatedStatistics() {
        def generate = folders.any { folder ->
            File file = new File(folder)
            !file.exists()
        }
        if (!generate) {
            AggregatedStatisticsExporter statisticsExporter = new AggregatedStatisticsExporter(folders[0])
            statisticsExporter.generateAggregatedStatistics()
            statisticsExporter = new AggregatedStatisticsExporter(folders[1])
            statisticsExporter.generateAggregatedStatistics()
            statisticsExporter = new AggregatedStatisticsExporter(folders[2])
            statisticsExporter.generateAggregatedStatistics()
            statisticsExporter = new AggregatedStatisticsExporter(folders[3])
            statisticsExporter.generateAggregatedStatistics()
        }
    }

    private runAllConfig(String inputTasksFile) {
        CsvUtil.copy(inputTasksFile, inputTasksFile - ConstantData.CSV_FILE_EXTENSION + "_original${ConstantData.CSV_FILE_EXTENSION}")
        int i = 1
        def found = false
        def invalid = false
        def randomAnalyser = null
        while (!found && !invalid) {
            //added_when
            Util.setRunningConfiguration(true, true, "output_added_when")
            log.info "< Running added_when configuration... >"
            def addedWhenAnalyser = runAnalysis(inputTasksFile)
            def addedWhenTasks = addedWhenAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAddedWhen = addedWhenTasks?.collect { it.doneTask.id }?.sort()
            def relevantButNotSelected = ((addedWhenAnalyser.validTasks - addedWhenAnalyser.relevantTasks) +
                    (addedWhenAnalyser.relevantTasks - addedWhenTasks)).collect { it.doneTask.id }
            def discarded = (addedWhenAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    addedWhenAnalyser.invalidTasksId)?.unique()?.sort()
            log.info "Relevant tasks: ${addedWhenAnalyser.relevantTasks.size()}"
            log.info "Relevant tasks (different tests and no empty ITest: ${addedWhenTasks.size()}"
            if (addedWhenTasks.empty) {
                invalid = true
                log.info "There is no tasks that satisfy added_when configuration."
                continue
            } else if (limit > 0 && idsAddedWhen.size() < limit && discarded.size() > 0) {
                log.info "Relevant tasks until added_when configuration: ${idsAddedWhen.size()}; We want ${limit}"
                log.info "Discarded tasks by added_when configuration (${discarded.size()}): ${discarded}"
                addedWhenAnalyser.backupOutputCsv(i++)
                reconfigureInputFile(discarded, inputTasksFile)
                continue
            } else if (discarded.size() > 0) {
                addedWhenAnalyser.backupOutputCsv(i++)
                reconfigureInputFile(discarded, inputTasksFile)
            }

            //added
            Util.setRunningConfiguration(false, true, "output_added")
            log.info "< Running added configuration... >"
            def addedAnalyser = runAnalysis(inputTasksFile)
            def addedTasks = addedAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAdded = addedTasks?.collect { it.doneTask.id }?.sort()
            def intersection1 = idsAdded.intersect(idsAddedWhen)
            relevantButNotSelected = ((addedAnalyser.validTasks - addedAnalyser.relevantTasks) +
                    (addedAnalyser.relevantTasks - addedTasks)).collect { it.doneTask.id }
            discarded = (addedAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    addedAnalyser.invalidTasksId)?.unique()?.sort()
            if (addedTasks.empty) {
                invalid = true
                log.info "There is no tasks that satisfy added configuration."
                continue
            } else if (limit > 0 && intersection1.size() < limit && discarded.size() > 0) {
                log.info "Relevant tasks until added configuration: ${intersection1.size()}; We want ${limit}"
                log.info "Discarded tasks by added configuration (${discarded.size()}): ${discarded}"
                addedAnalyser.backupOutputCsv(i++)
                reconfigureInputFile(discarded, inputTasksFile)
                continue
            } else if (discarded.size() > 0) {
                addedAnalyser.backupOutputCsv(i++)
                reconfigureInputFile(discarded, inputTasksFile)
            }

            //all_when
            Util.setRunningConfiguration(true, false, "output_all_when")
            log.info "< Running changed_when configuration... >"
            def allWhenAnalyser = runAnalysis(inputTasksFile)
            def allWhenTasks = allWhenAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAllWhen = allWhenTasks?.collect { it.doneTask.id }?.sort()
            def intersection2 = idsAllWhen.intersect(intersection1)
            relevantButNotSelected = ((allWhenAnalyser.validTasks - allWhenAnalyser.relevantTasks) +
                    (allWhenAnalyser.relevantTasks - allWhenTasks)).collect { it.doneTask.id }
            discarded = (allWhenAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    allWhenAnalyser.invalidTasksId)?.unique()?.sort()
            if (allWhenTasks.empty) {
                invalid = true
                log.info "There is no tasks that satisfy all_when configuration."
                continue
            } else if (limit > 0 && intersection2.size() < limit && discarded.size() > 0) {
                log.info "Relevant tasks until all_when configuration: ${intersection2.size()}; We want ${limit}"
                log.info "Discarded tasks by all_when configuration (${discarded.size()}): ${discarded}"
                allWhenAnalyser.backupOutputCsv(i++)
                reconfigureInputFile(discarded, inputTasksFile)
                continue
            } else if (discarded.size() > 0) {
                allWhenAnalyser.backupOutputCsv(i++)
                reconfigureInputFile(discarded, inputTasksFile)
            }

            //all
            Util.setRunningConfiguration(false, false, "output_all")
            log.info "< Running changed configuration... >"
            def allAnalyser = runAnalysis(inputTasksFile)
            def allTasks = allAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAll = allTasks?.collect { it.doneTask.id }?.sort()
            def intersection3 = idsAll.intersect(intersection2)
            relevantButNotSelected = ((allAnalyser.validTasks - allAnalyser.relevantTasks) +
                    (allAnalyser.relevantTasks - allTasks)).collect { it.doneTask.id }
            discarded = (allAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    allAnalyser.invalidTasksId)?.unique()?.sort()
            if (allTasks.empty) {
                invalid = true
                log.info "There is no tasks that satisfy all configuration."
                continue
            } else if (limit > 0) {
                if (intersection1.size() < limit || intersection2.size() < limit || intersection3.size() < limit) {
                    log.info "Relevant tasks until all configuration: ${intersection3.size()}; We want ${limit}"
                    intersection3.each { log.info it.toString() }
                    if (discarded.size() > 0) {
                        log.info "Discarded tasks by all configuration (${discarded.size()}): ${discarded}"
                        allAnalyser.backupOutputCsv(i++)
                        reconfigureInputFile(discarded, inputTasksFile)
                    } else invalid = true
                } else {
                    log.info "The analysis is finished. Relevant tasks: ${intersection3.size()}; We want ${limit}"
                    found = true
                }
            } else { //unlimited tasks
                log.info "Relevant tasks: ${intersection3.size()}; We want all unlimited!"
                if (discarded.size() > 0) log.info "Discarded tasks (${discarded.size()}): ${discarded}"
                if (intersection3.size() > 0) found = true
                else invalid = true
            }
            randomAnalyser = allAnalyser
        }
        if (found && Util.RANDOM_BASELINE && randomAnalyser) {
            randomAnalyser.generateRandomResult()
        }
    }

    private static reconfigureInputFile(def irrelevant, def inputTasksFile) {
        List<String[]> input = CsvUtil.read(inputTasksFile)
        def tasks = input.subList(1, input.size()).sort { it[1] }
        def ids = tasks.collect { it[1] as int }
        def candidateIds = ids - irrelevant
        def candidates = tasks.findAll { (it[1] as int) in candidateIds }
        List<String[]> lines = []
        lines += input.get(0)
        lines += candidates

        log.info "Number of entry tasks: ${ids.size()}"
        log.info "Number of irrelevant tasks: ${irrelevant.size()}"
        log.info "Number of candidate tasks: ${candidateIds.size()}"

        CsvUtil.write(inputTasksFile, lines)
    }

}

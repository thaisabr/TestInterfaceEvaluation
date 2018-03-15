package br.ufpe.cin.tan.main

import br.ufpe.cin.tan.analysis.TaskAnalyser
import br.ufpe.cin.tan.analysis.data.csvExporter.AggregatedStatisticsExporter
import br.ufpe.cin.tan.analysis.data.csvExporter.ResultOrganizerExporter
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util
import groovy.util.logging.Slf4j

@Slf4j
class Main {

    List<String> folders
    int limit

    Set analyzedTasks
    def selectedTasks
    def discardedTasks
    def addedWhenTasks
    def addedTasks
    def allWhenTasks
    def allTasks

    Main(taskLimit) {
        this.limit = taskLimit
        folders = ["output_added_when", "output_added", "output_all_when", "output_all"]
        initTasks()
    }

    private initTasks() {
        analyzedTasks = [] as Set
        selectedTasks = []
        discardedTasks = []
        addedWhenTasks = []
        addedTasks = []
        allWhenTasks = []
        allTasks = []
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
                mainObj.runAnalysis(Util.TASKS_FILE)
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
            runAnalysis(it)
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
        initTasks()
        CsvUtil.copy(inputTasksFile, inputTasksFile - ConstantData.CSV_FILE_EXTENSION + "_original${ConstantData.CSV_FILE_EXTENSION}")
        int i = 1
        def found = false
        def invalid = false

        log.info "< Running all config from: $inputTasksFile  >"
        while (!found && !invalid) {
            //added_when
            Util.setRunningConfiguration(true, true, "output_added_when")
            log.info "< Running added_when configuration... >"
            def addedWhenAnalyser = runAnalysis(inputTasksFile)
            analyzedTasks += addedWhenAnalyser.analyzedTasks
            def addedWhenTasks = addedWhenAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAddedWhen = addedWhenTasks?.collect { it.doneTask.id }?.sort()
            this.addedWhenTasks += idsAddedWhen
            def relevantButNotSelected = ((addedWhenAnalyser.validTasks - addedWhenAnalyser.relevantTasks) +
                    (addedWhenAnalyser.relevantTasks - addedWhenTasks)).collect { it.doneTask.id }
            def discarded = (addedWhenAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    addedWhenAnalyser.invalidTasksId)?.unique()?.sort()
            discardedTasks += discarded
            if (addedWhenTasks.empty) {
                invalid = true
                continue
            } else if (limit > 0 && idsAddedWhen.size() < limit && discarded.size() > 0) {
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
            analyzedTasks += addedAnalyser.analyzedTasks
            def addedTasks = addedAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAdded = addedTasks?.collect { it.doneTask.id }?.sort()
            this.addedTasks += idsAdded
            def intersection1 = idsAdded.intersect(idsAddedWhen)
            relevantButNotSelected = ((addedAnalyser.validTasks - addedAnalyser.relevantTasks) +
                    (addedAnalyser.relevantTasks - addedTasks)).collect { it.doneTask.id }
            discarded = (addedAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    addedAnalyser.invalidTasksId)?.unique()?.sort()
            discardedTasks += discarded
            if (addedTasks.empty) {
                invalid = true
                continue
            } else if (limit > 0 && intersection1.size() < limit && discarded.size() > 0) {
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
            analyzedTasks += allWhenAnalyser.analyzedTasks
            def allWhenTasks = allWhenAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAllWhen = allWhenTasks?.collect { it.doneTask.id }?.sort()
            this.allWhenTasks += idsAllWhen
            def intersection2 = idsAllWhen.intersect(intersection1)
            relevantButNotSelected = ((allWhenAnalyser.validTasks - allWhenAnalyser.relevantTasks) +
                    (allWhenAnalyser.relevantTasks - allWhenTasks)).collect { it.doneTask.id }
            discarded = (allWhenAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    allWhenAnalyser.invalidTasksId)?.unique()?.sort()
            discardedTasks += discarded
            if (allWhenTasks.empty) {
                invalid = true
                continue
            } else if (limit > 0 && intersection2.size() < limit && discarded.size() > 0) {
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
            analyzedTasks += allAnalyser.analyzedTasks
            def allTasks = allAnalyser.filterRelevantTasksByTestsAndEmptyItest()
            def idsAll = allTasks?.collect { it.doneTask.id }?.sort()
            this.allTasks += idsAll
            def intersection3 = idsAll.intersect(intersection2)
            relevantButNotSelected = ((allAnalyser.validTasks - allAnalyser.relevantTasks) +
                    (allAnalyser.relevantTasks - allTasks)).collect { it.doneTask.id }
            discarded = (allAnalyser.irrelevantImportedTasksId + relevantButNotSelected +
                    allAnalyser.invalidTasksId)?.unique()?.sort()
            discardedTasks += discarded

            if (allTasks.empty) {
                invalid = true
                continue
            } else if (limit > 0) {
                if (intersection1.size() < limit || intersection2.size() < limit || intersection3.size() < limit) {
                    if (discarded.size() > 0) {
                        allAnalyser.backupOutputCsv(i++)
                        reconfigureInputFile(discarded, inputTasksFile)
                    } else { //we do not reach the limit, but there is no more tasks
                        invalid = true
                        log.info "The analysis is finished. Selected tasks: ${intersection3.size()}; We want ${limit}"
                        selectedTasks = intersection3
                    }
                } else {
                    log.info "The analysis is finished. Selected tasks: ${intersection3.size()}; We want ${limit}"
                    found = true
                    selectedTasks = intersection3
                }
            } else { //unlimited tasks
                log.info "The analysis is finished. Selected tasks: ${intersection3.size()}; We want all unlimited!"
                if (intersection3.size() > 0) {
                    selectedTasks = intersection3
                    found = true
                }
                else invalid = true
            }
        }//close-while

        /* Organize overview of final result */
        updateAnalyzedTasks()

        /* Organize results into folders (output_added, output_added_when, output_all, output_all_when) */
        organizeResults(inputTasksFile)
    }

    private organizeResults(String taskFile) {
        folders.each { folder ->
            ResultOrganizerExporter resultOrganizerExporter = new ResultOrganizerExporter(folder, taskFile, selectedTasks)
            resultOrganizerExporter.organize()
        }
    }

    private static reconfigureInputFile(def irrelevant, def inputTasksFile) {
        List<String[]> input = CsvUtil.read(inputTasksFile)
        def tasks = input.subList(1, input.size()).sort { it[1] }
        def ids = tasks.collect { it[1] as Integer }
        def candidateIds = ids - irrelevant
        def candidates = tasks.findAll { (it[1] as Integer) in candidateIds }
        List<String[]> lines = []
        lines += input.get(0)
        lines += candidates

        log.info "Number of entry tasks: ${ids.size()}"
        log.info "Number of irrelevant tasks: ${irrelevant.size()}"
        log.info "Number of candidate tasks: ${candidateIds.size()}"

        CsvUtil.write(inputTasksFile, lines)
    }

    private updateAnalyzedTasks() {
        addedWhenTasks = addedWhenTasks.unique()
        addedTasks = addedTasks.unique()
        allWhenTasks = allWhenTasks.unique()
        allTasks = allTasks.unique()
        discardedTasks = discardedTasks.unique().sort()
        analyzedTasks = analyzedTasks.sort()
        def analyzedAndDiscarded = analyzedTasks.intersect(discardedTasks)

        log.info "Number of valid tasks for added_when configuration (${addedWhenTasks.size()}): $addedWhenTasks"
        log.info "Number of valid tasks for added configuration (${addedTasks.size()}): $addedTasks"
        log.info "Number of valid tasks for all_when configuration (${allWhenTasks.size()}): $allWhenTasks"
        log.info "Number of valid tasks for all configuration (${allTasks.size()}): $allTasks"
        log.info "Number of selected tasks (${selectedTasks.size()}): $selectedTasks"
        log.info "Number of analyzed tasks (we compute interfaces) (${analyzedTasks.size()}): $analyzedTasks"
        log.info "Number of both analyzed and discarded tasks (${analyzedAndDiscarded.size()}): $analyzedAndDiscarded"
        log.info "Number of discarded tasks (${discardedTasks.size()}): $discardedTasks"
    }

}

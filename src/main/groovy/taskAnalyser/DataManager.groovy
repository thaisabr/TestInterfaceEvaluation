package taskAnalyser

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import evaluation.TaskInterfaceEvaluator
import groovy.util.logging.Slf4j
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.eclipse.jgit.diff.DiffEntry
import similarityAnalyser.test.TestSimilarityAnalyser
import similarityAnalyser.text.TextualSimilarityAnalyser
import taskAnalyser.task.DoneTask
import util.ConstantData
import util.Util

@Slf4j
class DataManager {

    static
    final String[] HEADER = ["Task", "Date", "#Days", "#Commits", "Commit_Message", "#Devs", "#Gherkin_Tests", "#Impl_Gherkin_Tests", "#StepDef",
                             "Methods_Unknown_Type", "#Step_Call", "Step_Match_Errors", "#Step_Match_Error", "AST_Errors",
                             "#AST_Errors", "Gherkin_AST_Errors", "#Gherkin_AST_Errors", "Steps_AST_Errors",
                             "#Steps_AST_Errors", "Renamed_Files", "Deleted_Files", "NotFound_Views", "#Views", "#ITest",
                             "#IReal", "ITest", "IReal", "Precision", "Recall", "Hashes", "Timestamp", "Rails", "Simplecov", "FactoryGirl"]
    static final int RECALL_INDEX = HEADER.size() - 6
    static final int PRECISION_INDEX = RECALL_INDEX - 1
    static final int IREAL_INDEX = PRECISION_INDEX - 1
    static final int ITEST_INDEX = IREAL_INDEX - 1
    static final int STEP_MATCH_ERROR_INDEX = 11
    static final int AST_ERROR_INDEX = 13
    static final int INITIAL_TEXT_SIZE = 6

    private static List<String[]> readAllResult(String filename) {
        List<String[]> entries = []
        try {
            CSVReader reader = new CSVReader(new FileReader(filename))
            entries = reader.readAll()
            reader.close()
        } catch (Exception ex) {
            log.error ex.message
        }
        entries
    }

    private static computePairs(set) {
        def result = [] as Set
        if (!set || set.empty || set.size() == 1) return set
        set.eachWithIndex { v, k ->
            def next = set.drop(k + 1)
            result.add([task: v, pairs: next])
        }
        result
    }

    private static extractTaskText(filename, taskId) {
        def text = ""
        File file = new File("${filename - ConstantData.FILTERED_FILE_SUFIX}_text_${taskId}.txt")
        if (file.exists()) {
            file.withReader("utf-8") { reader ->
                text = reader.text
            }
        }
        text
    }

    private static writeHeaderAllResult(CSVWriter writer, url, allTasks, relevantTasks, stepTasks, gherkinTasks, testsTask) {
        String[] text = ["Repository", url]
        writer.writeNext(text)
        text = ["Tasks", allTasks]
        writer.writeNext(text)
        text = ["Tasks that have production and test code", relevantTasks]
        writer.writeNext(text)
        text = ["Tasks that have changed step definitions", stepTasks]
        writer.writeNext(text)
        text = ["Tasks that have changed Gherkin files", gherkinTasks]
        writer.writeNext(text)
        text = ["Tasks that have test", testsTask]
        writer.writeNext(text)
        writer.writeNext(HEADER)
    }

    private static writeITextFile(filename, entry) {
        def text = entry.text
        if (text && !text.empty) {
            File file = new File("${filename - ConstantData.CSV_FILE_EXTENSION}_text_${entry.task.id}.txt")
            file.withWriter("utf-8") { out ->
                out.write(text)
                out.write("\n-----------------------------------------------------------\n")
                entry.trace.each{ out.write(it + "\n") }
            }
        }
    }

    private static extractDates(entry) {
        def dates = entry?.task?.commits*.date?.flatten()?.sort()
        if (dates) dates = dates.collect { new Date(it * 1000).format('dd-MM-yyyy') }.unique()
        else dates = []
        dates
    }

    private static String extractMessages(entry) {
        String msgs = entry?.task?.commits*.message?.flatten()?.toString()
        if (msgs.length() > 1000) msgs = msgs.toString().substring(0, 999) + " [TOO_LONG]"
        msgs
    }

    private static extractStepErrors(entry) {
        def stepErrors = entry.itest.matchStepErrors
        def stepErrorsQuantity = 0
        def text = ""
        if (stepErrors.empty) text = ""
        else {
            stepErrorsQuantity = stepErrors*.size.flatten().sum()
            stepErrors.each{ error ->
                text += "[path:${error.path}, size:${error.size}], "
            }
            text = text.substring(0, text.size()-2)
        }
        [text: text, quantity: stepErrorsQuantity]
    }

    private static extractCompilationErrors(entry) {
        def compilationErrors = entry.itest.compilationErrors
        def compErrorsQuantity = 0
        def gherkinQuantity = 0
        def stepsQuantity = 0
        def gherkin = ""
        def steps = ""
        if (compilationErrors.empty) compilationErrors = ""
        else {
            compErrorsQuantity = compilationErrors*.msgs.flatten().size()
            gherkin = compilationErrors.findAll{ Util.isGherkinCode(it.path) }
            gherkinQuantity = gherkin.size()
            if(gherkin.empty) gherkin = ""
            steps = compilationErrors.findAll{ Util.isStepDefinitionCode(it.path) }
            stepsQuantity = steps.size()
            if(steps.empty) steps = ""
            compilationErrors = compilationErrors.toString()
        }

        [text: compilationErrors, quantity: compErrorsQuantity, gherkin:gherkin, quantityGherkin:gherkinQuantity,
         steps:steps, stepsQuantity:stepsQuantity]
    }

    private static extractRemovedFiles(entry) {
        def changes = entry.task.commits*.coreChanges?.flatten()?.findAll { it.type == DiffEntry.ChangeType.DELETE }
        def result = changes?.collect { entry.task.gitRepository.name + File.separator + it.path }?.unique()?.sort()
        if (result?.empty) result = ""
        result
    }

    private static writeResult(List<String[]> entries, writer) {
        entries.each { entry ->
            def itest = "no", ireal = "no"
            if (entry[IREAL_INDEX].empty) ireal = "yes"
            if (entry[ITEST_INDEX].empty) itest = "yes"
            String[] headers = entry + [itest, ireal]
            writer.writeNext(headers)
        }
    }

    private static saveResultForSimilarityAnalysis(String filename, String[] header, List<String[]> entries) {
        def writer = new CSVWriter(new FileWriter(filename))
        String[] text = ["Tasks (the ones that have Gherkin test)", entries.size()]
        writer.writeNext(text)
        writer.writeNext(header)
        writeResult(entries, writer)
        writer.close()
    }

    private static writeHeaderOrganizedResult(CSVWriter writer, previousAnalysisData) {
        previousAnalysisData.subList(0, INITIAL_TEXT_SIZE).each { data ->
            String[] value = data.findAll { !it.allWhitespace }
            writer.writeNext(value)
        }
    }

    private
    static organizeAllResult(String evaluationFile, String organizedFile, String similarityFile, boolean similarityAnalysis) {
        if (!evaluationFile || evaluationFile.empty || !(new File(evaluationFile).exists())) return
        List<String[]> entries = readAllResult(evaluationFile)
        if (entries.size() <= INITIAL_TEXT_SIZE) return

        CSVWriter writer = new CSVWriter(new FileWriter(organizedFile))
        writeHeaderOrganizedResult(writer, entries)

        String[] resultHeader1 = entries.get(INITIAL_TEXT_SIZE).findAll { !it.allWhitespace }
        String[] resultHeader2 = resultHeader1 + ["Empty_ITest", "Empty_IReal"]
        entries = entries.subList(INITIAL_TEXT_SIZE+1, entries.size())

        def emptyIReal = entries.findAll { it[IREAL_INDEX].empty }
        entries -= emptyIReal
        def sizeNoEmptyIRealTasks = entries.size()
        def stepMatchError = entries.findAll{ (it[STEP_MATCH_ERROR_INDEX] as int) > 0 }
        def astError = entries.findAll{ (it[AST_ERROR_INDEX] as int) > 0 }
        def basicError = (astError + stepMatchError).unique()
        entries -= basicError
        def hasGherkinTest = entries.findAll { (it[6] as int) > 0 }
        def hasStepTest = entries.findAll { (it[7] as int) > 0 }
        def validTasks = (hasGherkinTest + hasStepTest).unique()
        def emptyITest = validTasks.findAll { it[ITEST_INDEX].empty }
        def relevantTasks = validTasks - emptyITest
        def zeroPrecisionAndRecall = relevantTasks.findAll { it[PRECISION_INDEX] == "0.0" && it[RECALL_INDEX] == "0.0" }
        def others = relevantTasks - zeroPrecisionAndRecall

        String[] text = ["Tasks that have no empty IReal", sizeNoEmptyIRealTasks]
        writer.writeNext(text)
        text = ["Tasks that have AST error", astError.size()]
        writer.writeNext(text)
        text = ["Tasks that have step match error", stepMatchError.size()]
        writer.writeNext(text)
        text = ["Tasks that have any error", basicError.size()]
        writer.writeNext(text)
        text = ["Valid tasks (ones that have acceptance test, no empty IReal, no AST error and no match step error)", validTasks.size()]
        writer.writeNext(text)
        text = ["Tasks that have acceptance test and empty ITest", emptyITest.size()]
        writer.writeNext(text)
        text = ["Tasks that have test and no empty ITest", relevantTasks.size()]
        writer.writeNext(text)
        text = ["Tasks that have no empty ITest and precision e recall 0.0", zeroPrecisionAndRecall.size()]
        writer.writeNext(text)

        if(entries.empty){
            text = ["Precision mean", ""]
            writer.writeNext(text)
            text = ["Precision median", ""]
            writer.writeNext(text)
            text = ["Precision standard deviation", ""]
            writer.writeNext(text)
            text = ["Recall mean", ""]
            writer.writeNext(text)
            text = ["Recall median", ""]
            writer.writeNext(text)
            text = ["Recall standard deviation", ""]
            writer.writeNext(text)
        } else {
            double[] precisionValues = entries.collect { it[PRECISION_INDEX] as double }
            def itestStatistics = new DescriptiveStatistics(precisionValues)
            double[] recallValues = entries.collect { it[RECALL_INDEX] as double }
            def irealStatistics = new DescriptiveStatistics(recallValues)

            text = ["Precision mean", itestStatistics.mean]
            writer.writeNext(text)
            text = ["Precision median", itestStatistics.getPercentile(50)]
            writer.writeNext(text)
            text = ["Precision standard deviation", itestStatistics.standardDeviation]
            writer.writeNext(text)
            text = ["Recall mean", irealStatistics.mean]
            writer.writeNext(text)
            text = ["Recall median", irealStatistics.getPercentile(50)]
            writer.writeNext(text)
            text = ["Recall standard deviation", irealStatistics.standardDeviation]
            writer.writeNext(text)
        }

        writer.writeNext(resultHeader2)
        writeResult(emptyITest, writer)
        writeResult(zeroPrecisionAndRecall, writer)
        writeResult(others, writer)
        writeResult(basicError, writer)
        writer.close()

        if (similarityAnalysis) {
            def tasks = entries.findAll { !it[IREAL_INDEX].empty && (it[6] as int) > 0 }
            saveResultForSimilarityAnalysis(similarityFile, resultHeader1, tasks)
        }

    }

    static List<String[]> readInputCSV(String filename) {
        List<String[]> entries = readAllResult(filename)
        entries.remove(0) //ignore header
        entries.unique { it[2] } //bug: input csv can contain duplicated values; task id is used to identify them.
    }

    /***
     * Extracts all tasks in a CSV file that changed production and test files.
     * @filename cvs file organized by 7 columns: "index","repository_url","task_id","commits_hash",
     *           "changed_production_files","changed_test_files","commits_message".
     * @return a list of tasks.
     */
    static extractProductionAndTestTasks(String filename) {
        List<String[]> entries = readInputCSV(filename)
        List<String[]> relevantEntries = entries.findAll { ((it[4] as int)>0 && (it[5] as int)>0)  ||
                ((it[4] as int)>50 && (it[5] as int)==0)} //avoiding the exclusion of corrupted tasks at the entry csv
        def invalid = entries.size() - relevantEntries.size()
        List<DoneTask> tasks = []
        def tasksThatSeemsToHaveTest = []

        try {
            relevantEntries.each { entry ->
                if (entry[2].size() > 4) {
                    def hashes = entry[3].tokenize(',[]')*.trim()
                    def task = new DoneTask(entry[1], entry[2], hashes)
                    if(task.hasTest()) tasks += task
                    else tasksThatSeemsToHaveTest += entry[2]
                }
            }
        } catch (Exception ex) {
            log.error ex.message
            return [tasks: [], allTasksQuantity: 0]
        }

        log.info "Number of invalid tasks: ${invalid}"
        log.info "Number of extracted valid tasks: ${tasks.size()}"

        /* Tasks that had changed test code but when the task is concluded, its Gherkin scenarios or step code definitions
        * were removed by other tasks*/
        log.info "Tasks that seem to have test but actually do not: ${tasksThatSeemsToHaveTest.size()}"
        tasksThatSeemsToHaveTest.each{ log.info it }

        [tasks: tasks.sort { it.id }, allTasksQuantity: entries.size()]
    }

    static saveAllResult(filename, url, allTasksCounter, relevantTasksCounter, stepDefTasksCounter,
                         gherkinTasksCounter, testsCounter, taskData) {
        CSVWriter writer = new CSVWriter(new FileWriter(filename))
        writeHeaderAllResult(writer, url, allTasksCounter, relevantTasksCounter, stepDefTasksCounter, gherkinTasksCounter, testsCounter)

        def saveText = false
        if (taskData && taskData.size() > 1) saveText = true

        taskData?.each { entry ->
            def itestSize = entry.itest.findAllFiles().size()
            def irealSize = entry.ireal.findAllFiles().size()
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(entry.itest, entry.ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(entry.itest, entry.ireal)
            def dates = extractDates(entry)
            def devs = entry?.task?.commits*.author?.flatten()?.unique()?.size()
            def msgs = extractMessages(entry)
            def stepErrors = extractStepErrors(entry)
            def compilationErrors = extractCompilationErrors(entry)
            def renames = entry.task.renamedFiles
            def removes = extractRemovedFiles(entry)
            if (renames.empty) renames = ""
            def views = entry.itest.notFoundViews
            if (views.empty) views = ""
            String[] line = [entry.task.id, dates, entry.task.days, entry.task.commitsQuantity, msgs, devs,
                             entry.task.gherkinTestQuantity, entry.itest.foundAcceptanceTests.size(),
                             entry.task.stepDefQuantity, entry.methods, entry.stepCalls,
                             stepErrors.text, stepErrors.quantity, compilationErrors.text, compilationErrors.quantity,
                             compilationErrors.gherkin, compilationErrors.quantityGherkin, compilationErrors.steps,
                             compilationErrors.stepsQuantity, renames, removes, views, views.size(), itestSize,
                             irealSize, entry.itest, entry.ireal, precision, recall, entry.task.commits*.hash,
                             entry.timestamp, entry.rails, entry.simplecov, entry.factorygirl]

            writer.writeNext(line)
            if (saveText) writeITextFile(filename, entry) //dealing with long textual description of a task
        }

        writer.close()
    }

    static organizeResult(String evaluationFile, String organizedFile) {
        organizeAllResult(evaluationFile, organizedFile, null, false)
    }

    static organizeResultForSimilarityAnalysis(String evaluationFile, String organizedFile, String similarityFile) {
        organizeAllResult(evaluationFile, organizedFile, similarityFile, true)
    }

    static analyseSimilarity(String filteredFile, String similarityFile) {
        if (!filteredFile || filteredFile.empty || !(new File(filteredFile).exists())) return
        List<String[]> entries = readAllResult(filteredFile)
        if (entries.size() <= 4) return

        CSVWriter writer = new CSVWriter(new FileWriter(similarityFile))
        writer.writeNext(entries.get(0))

        String[] resultHeader = ["Task_A", "Task_B", "Text", "Test_Jaccard", "Real_Jaccard", "Test_Cosine", "Real_Cosine"]
        writer.writeNext(resultHeader)

        def allTasks = entries.subList(2, entries.size())
        if (allTasks.size() <= 1) return
        def taskPairs = computePairs(allTasks)
        List<String[]> lines = []
        taskPairs?.each { item ->
            def task = item.task
            def taskText = extractTaskText(filteredFile, task[0])
            def itest1 = task[ITEST_INDEX].split(", ") as List
            def ireal1 = task[IREAL_INDEX].split(", ") as List

            item.pairs?.each { other ->
                log.info "Similarity between tasks ${task[0]} and ${other[0]}"

                def otherText = extractTaskText(filteredFile, other[0])
                def textualSimilarityAnalyser = new TextualSimilarityAnalyser()
                def textSimilarity = textualSimilarityAnalyser.calculateSimilarity(taskText, otherText)
                log.info "Textual similarity result: $textSimilarity"

                def itest2 = other[ITEST_INDEX].split(", ") as List
                def ireal2 = other[IREAL_INDEX].split(", ") as List
                def testSimJaccard = TestSimilarityAnalyser.calculateSimilarityByJaccard(itest1, itest2)
                def testSimCosine = TestSimilarityAnalyser.calculateSimilarityByCosine(itest1, itest2)
                log.info "Test similarity (jaccard index): $testSimJaccard"
                log.info "Test similarity (cosine): $testSimCosine"

                def realSimJaccard = TestSimilarityAnalyser.calculateSimilarityByJaccard(ireal1, ireal2)
                def realSimCosine = TestSimilarityAnalyser.calculateSimilarityByCosine(ireal1, ireal2)
                log.info "Real similarity (jaccard index): $realSimJaccard"
                log.info "Real similarity (cosine): $realSimCosine"

                String[] line = [task[0], other[0], textSimilarity, testSimJaccard, realSimJaccard, testSimCosine, realSimCosine]
                lines += line
            }
        }

        writer.writeAll(lines)
        writer.close()
    }

    static organizeSimilarityResult(String similarityFile, String organizedFile) {
        if (!similarityFile || similarityFile.empty || !(new File(similarityFile).exists())) return
        List<String[]> entries = readAllResult(similarityFile)
        if (entries.size() <= 2) return

        CSVWriter writer = new CSVWriter(new FileWriter(organizedFile))
        writer.writeNext(entries.get(0))
        String[] resultHeader1 = entries.get(1).findAll { !it.allWhitespace }
        entries = entries.subList(2, entries.size())

        //Positions: 2-text; 3-test; 4-real
        def zeroReal = entries.findAll { it[4] == "0.0" }.sort { it[3] as BigDecimal }
        entries = entries - zeroReal
        def oneReal = entries.findAll { it[4] == "1.0" }.sort { -(it[3] as BigDecimal) }
        entries = entries - oneReal
        def others = entries.sort { -(it[4] as BigDecimal) }.sort { -(it[3] as BigDecimal) }

        writer.writeNext(resultHeader1)
        writer.writeAll(zeroReal)
        writer.writeAll(oneReal)
        writer.writeAll(others)
        writer.close()
    }

}

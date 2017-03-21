package taskAnalyser

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import evaluation.TaskInterfaceEvaluator
import groovy.util.logging.Slf4j
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import similarityAnalyser.test.TestSimilarityAnalyser
import similarityAnalyser.text.TextualSimilarityAnalyser
import taskAnalyser.task.AnalysedTask
import taskAnalyser.task.AnalysisResult
import taskAnalyser.task.DoneTask
import util.ConstantData
import util.RegexUtil
import util.Util

import java.util.regex.Matcher

@Slf4j
class DataManager {

    static
    final String[] HEADER = ["Task", "Date", "#Days", "#Commits", "Commit_Message", "#Devs", "#Gherkin_Tests", "#Impl_Gherkin_Tests", "#StepDef",
                             "Methods_Unknown_Type", "#Step_Call", "Step_Match_Errors", "#Step_Match_Error", "AST_Errors",
                             "#AST_Errors", "Gherkin_AST_Errors", "#Gherkin_AST_Errors", "Steps_AST_Errors",
                             "#Steps_AST_Errors", "Renamed_Files", "Deleted_Files", "NotFound_Views", "#Views", "#ITest",
                             "#IReal", "ITest", "IReal", "Precision", "Recall", "Hashes", "Timestamp", "Rails", "Simplecov",
                             "FactoryGirl", "#Visit_Call", "#Views_ITest", "#Code_View_Analysis", "Code_View_Analysis"]
    static final int RECALL_INDEX = HEADER.size() - 10
    static final int PRECISION_INDEX = RECALL_INDEX - 1
    static final int IREAL_INDEX = PRECISION_INDEX - 1
    static final int ITEST_INDEX = IREAL_INDEX - 1
    static final int STEP_MATCH_ERROR_INDEX = 12
    static final int AST_ERROR_INDEX = 14
    static final int INITIAL_TEXT_SIZE = 6
    static final int GHERKIN_TEST_INDEX = 6
    static final int STEP_DEF_INDEX = GHERKIN_TEST_INDEX + 2

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

    private static writeITextFile(String filename, AnalysedTask analysedTask) {
        if (analysedTask.itext && !analysedTask.itext.empty) {
            File file = new File("${filename - ConstantData.CSV_FILE_EXTENSION}_text_${analysedTask.doneTask.id}.txt")
            file.withWriter("utf-8") { out ->
                out.write(analysedTask.itext)
                out.write("\n-----------------------------------------------------------\n")
                analysedTask.trace.each{ out.write(it + "\n") }
            }
        }
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
    static void organizeAllResult(String evaluationFile, String organizedFile, String similarityFile, boolean similarityAnalysis) {
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
        def hasGherkinTest = entries.findAll { (it[GHERKIN_TEST_INDEX] as int) > 0 }
        def hasStepTest = entries.findAll { (it[STEP_DEF_INDEX] as int) > 0 }
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

    static saveAllResult(String filename, int allTasksCounter, AnalysisResult result) {
        CSVWriter writer = new CSVWriter(new FileWriter(filename))
        writeHeaderAllResult(writer, result.url, allTasksCounter, result.validTasks.size(),
                result.stepCounter, result.gherkinCounter, result.testsCounter)

        def saveText = false
        if (result.validTasks && result.validTasks.size() > 1) saveText = true

        result.validTasks?.each { task ->
            def itestFiles = task.itestFiles()
            def itestSize = itestFiles.size()
            def irealFiles = task.irealFiles()
            def irealSize = irealFiles.size()
            def precision = task.precision()
            def recall = task.recall()
            def dates = task.dates
            def devs = task.developers
            def msgs = task.commitMsg
            def renames = task.renamedFiles
            def removes = task.removedFiles
            if (renames.empty) renames = ""
            def views = task.notFoundViews()
            if (views.empty) views = ""
            def filesFromViewAnalysis = task.filesFromViewAnalysis()
            def viewFileFromITest = task.itestViewFiles().size()
            String[] line = [task.doneTask.id, dates, task.doneTask.days,
                             task.doneTask.commitsQuantity, msgs, devs,
                             task.doneTask.gherkinTestQuantity, task.itest.foundAcceptanceTests.size(),
                             task.doneTask.stepDefQuantity, task.methods, task.stepCalls,
                             task.stepMatchErrorsText, task.stepMatchErrors, task.compilationErrorsText,
                             task.compilationErrors, task.gherkinCompilationErrorsText,
                             task.gherkinCompilationErrors, task.stepDefCompilationErrorsText,
                             task.stepDefCompilationErrors, renames, removes, views, views.size(), itestSize,
                             irealSize, itestFiles, irealFiles, precision, recall, task.doneTask.hashes,
                             task.itest.timestamp, task.rails, task.simplecov, task.factorygirl,
                             task.itest.visitCallCounter, viewFileFromITest, filesFromViewAnalysis.size(), filesFromViewAnalysis]

            writer.writeNext(line)
            if (saveText) writeITextFile(filename, task) //dealing with long textual description of a task
        }

        writer.close()
    }

    static organizeResult(String evaluationFile, String organizedFile) {
        organizeAllResult(evaluationFile, organizedFile, null, false)
        filterResult(evaluationFile) //TEMPORARY CODE
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

    /* filter results to only consider controller files (via csv) - TEMPORARY CODE */
    static void filterResult(String evaluationFile) {
        if (!evaluationFile || evaluationFile.empty || !(new File(evaluationFile).exists())) return
        List<String[]> entries = readAllResult(evaluationFile)
        if (entries.size() <= INITIAL_TEXT_SIZE) return

        def controllerFile = evaluationFile - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        CSVWriter writer = new CSVWriter(new FileWriter(controllerFile))
        writeHeaderOrganizedResult(writer, entries)
        String[] resultHeader = entries.get(INITIAL_TEXT_SIZE).findAll { !it.allWhitespace }
        writer.writeNext(resultHeader)

        entries = entries.subList(INITIAL_TEXT_SIZE + 1, entries.size())
        entries?.each { entry ->
            def originalItest = entry[ITEST_INDEX].replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"/").split(",").flatten() as Set
            def itest = originalItest.findAll { Util.isControllerFile(it) }
            def originalIReal = entry[IREAL_INDEX].replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"/").split(",").flatten() as Set
            def ireal = originalIReal.findAll { Util.isControllerFile(it) }
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(itest, ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(itest, ireal)

            String[] line = entry
            line[ITEST_INDEX-2] = itest.size()
            line[ITEST_INDEX-1] = ireal.size()
            line[ITEST_INDEX] = itest
            line[IREAL_INDEX] = ireal
            line[PRECISION_INDEX] = precision
            line[RECALL_INDEX] = recall
            line[resultHeader.size()-3] = 0
            writer.writeNext(line)
        }

        writer.close()

        def controllerOrgFile = controllerFile - ConstantData.CONTROLLER_FILE_SUFIX + ConstantData.CONTROLLER_ORGANIZED_FILE_SUFIX
        organizeAllResult(controllerFile, controllerOrgFile, null, false)
    }

}

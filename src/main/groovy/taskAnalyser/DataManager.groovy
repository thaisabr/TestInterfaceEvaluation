package taskAnalyser

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import commitAnalyser.Commit
import evaluation.TaskInterfaceEvaluator
import groovy.util.logging.Slf4j
import similarityAnalyser.test.TestSimilarityAnalyser
import similarityAnalyser.text.TextualSimilarityAnalyser
import util.ConstantData
import util.Util

@Slf4j
class DataManager {

    private static computePairs(def set){
        def result = [] as Set
        if(!set || set.empty || set.size()==1) return set
        set.eachWithIndex{ v, k ->
            def next = set.drop(k+1)
            result.add([task: v, pairs: next]) //next.each{ n -> result.add([v, n]) }
        }
        result
    }

    private static extractTaskText(def filename, def taskId){
        def text = ""
        File file = new File("${filename- ConstantData.FILTERED_FILE_SUFIX}_text_${taskId}.txt")
        if(file.exists()){
            file.withReader("utf-8") { reader ->
                text = reader.readLines().join(System.lineSeparator())
            }
        }
        text
    }

    private static writeHeaderAllResult(CSVWriter writer, def allTasks, def relevantTasks, def stepTasks, def gherkinTasks){
        writer.writeNext("Number of tasks: $allTasks")
        writer.writeNext("Number of tasks that have production and test code: $relevantTasks")
        writer.writeNext("Number of tasks that have changed step definitions: $stepTasks")
        writer.writeNext("Number of tasks that have changed Gherkin files: $gherkinTasks")
        String[] header = ["Task", "Date", "#Days", "#Commits", "Commit_Message", "#Devs", "#Gherkin_Tests", "#StepDef",
                           "Methods_Unknown_Type", "#Step_Call", "Step_Match_Errors", "#Step_Match_Error", "AST_Errors",
                           "#AST_Errors", "ITest", "IReal", "Precision", "Recall"] //18 elements
        writer.writeNext(header)
    }

    private static writeITextFile(def filename, def entry){
        if(entry.text && !entry.text.empty){
            File file = new File("${filename-ConstantData.CSV_FILE_EXTENSION}_text_${entry.task.id}.txt")
            file.withWriter("utf-8") { out ->
                out.write(entry.text)
            }
        }
    }

    private static extractDates(def entry){
        def dates =  entry?.task?.commits*.date?.flatten()?.sort()
        if(dates) dates = dates.collect{ new Date(it*1000).format('dd-MM-yyyy') }.unique()
        else dates = []
        dates
    }

    private static extractMessages(def entry){
        def msgs = entry?.task?.commits*.message?.flatten()
        if(msgs.size()>1000) msgs = msgs.toString().substring(0,999)+" [TOO_LONG]"
        msgs
    }

    private static extractStepErrors(def entry){
        def stepErrors = entry.itest.matchStepErrors
        def stepErrorsQuantity = 0
        if(stepErrors.empty) stepErrors = ""
        else{
            stepErrorsQuantity = stepErrors*.lines.flatten().size()
            stepErrors = stepErrors.toString()
        }
        [text: stepErrors, quantity:stepErrorsQuantity]
    }

    private static extractCompilationErrors(def entry){
        def compilationErrors = entry.itest.compilationErrors
        def compErrorsQuantity = 0
        if(compilationErrors.empty) compilationErrors = ""
        else{
            compErrorsQuantity = compilationErrors*.msgs.flatten().size()
            compilationErrors = compilationErrors.toString()
        }
        [text: compilationErrors, quantity:compErrorsQuantity]
    }

    private static writeResult(List<String[]> entries, def writer){
        entries.each{ entry ->
            def itest = "no", ireal = "no"
            if(entry[15].empty) ireal = "yes"
            if(entry[14].empty) itest = "yes"
            String[] headers = entry + [itest, ireal]
            writer.writeNext(headers)
        }
    }

    private static saveResultForSimilarityAnalysis(String filename, String[] header, List<String[]> entries){
        def writer = new CSVWriter(new FileWriter(filename))
        writer.writeNext("Number of tasks (the ones that have Gherkin test): ${entries.size()}")
        writer.writeNext(header)
        writeResult(entries, writer)
        writer.close()
    }

    private static List<String[]> readInputCSV(String filename){
        List<String[]> entries = []
        try{
            CSVReader reader = new CSVReader(new FileReader(filename))
            entries = reader.readAll()
            reader.close()
            entries.remove(0) //ignore header
        } catch (Exception ex){
            log.error ex.message
        }
        entries.unique{ it[2] } //bug: input csv can contain duplicated values; task id is used to identify them.
    }

    private static writeHeaderOrganizedResult(CSVWriter writer, def previousAnalysisData){
        previousAnalysisData.subList(0,4).each{ data ->
            String[] value = data.findAll{!it.allWhitespace}
            writer.writeNext(value)
        }
    }

    private static organizeAllResult(String evaluationFile, String organizedFile, String similarityFile, boolean similarityAnalysis){
        log.info "Organizing results..."
        if(!evaluationFile || evaluationFile.empty) return
        List<String[]> entries = readAllResult(evaluationFile)
        if(entries.size() <= 4) return

        CSVWriter writer = new CSVWriter(new FileWriter(organizedFile))
        writeHeaderOrganizedResult(writer, entries)

        String[] resultHeader1 = entries.get(4).findAll{!it.allWhitespace}
        String[] resultHeader2 = resultHeader1 + ["Empty_ITest", "Empty_IReal"]
        entries = entries.subList(5,entries.size())

        def emptyIReal = entries.findAll{ it[15].empty }
        entries = entries - emptyIReal
        def hasGherkinTest = entries.findAll{ (it[6] as int)>0 }
        def hasStepTest = entries.findAll{ (it[7] as int)>0 }
        def noTest = entries - (hasGherkinTest+hasStepTest).unique()
        def validTasks = entries - noTest
        def emptyITest = validTasks.findAll{ it[14].empty }
        def others = validTasks - emptyITest

        writer.writeNext("Number of valid tasks (ones that have acceptance test and no empty IReal): ${validTasks.size()}")
        writer.writeNext("Number of tasks that have acceptance test and empty ITest: ${emptyITest.size()}")
        writer.writeNext("Number of tasks that have test and no empty ITest: ${others.size()}")
        writer.writeNext(resultHeader2)
        writeResult(emptyITest, writer)
        writeResult(others, writer)
        writer.close()

        if(similarityAnalysis) {
            def tasks = entries.findAll{ !it[15].empty && (it[6] as int)>0 }
            saveResultForSimilarityAnalysis(similarityFile, resultHeader1, tasks)
        }

    }

    /***
     * Extracts all tasks in a CSV file that changed production and test files.
     * @filename cvs file organized by 7 columns: "index","repository_url","task_id","commits_hash",
     *           "changed_production_files","changed_test_files","commits_message".
     * @return a list of tasks.
     */
    static extractProductionAndTestTasks(String filename){
        List<String[]> entries = readInputCSV(filename)
        List<String[]> relevantEntries = entries.findAll{ !it[4].equals("[]") && !it[5].equals("[]") }
        List<DoneTask> tasks = []
        relevantEntries.each { entry ->
            if(entry[2].size()>4){
                List<Commit> commits = []
                def hashes = entry[3].tokenize(',[]')*.trim()
                hashes.each { commits += new Commit(hash: it) }
                tasks += new DoneTask(entry[0], entry[1], entry[2], commits)
            }
        }
        [relevantTasks:tasks, allTasksQuantity:entries.size()]
    }

    static extractProductionAndTestTasks(){
        extractProductionAndTestTasks(Util.TASKS_FILE)
    }

    static saveAllResult(def filename, def allTasksCounter, def relevantTasksCounter, def stepDefTasksCounter,
                         def gherkinTasksCounter, def taskData){
        CSVWriter writer = new CSVWriter(new FileWriter(filename))
        writeHeaderAllResult(writer, allTasksCounter, relevantTasksCounter, stepDefTasksCounter, gherkinTasksCounter)

        taskData?.each{ entry ->
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(entry.itest, entry.ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(entry.itest, entry.ireal)
            def dates =  extractDates(entry)
            def devs = entry?.task?.commits*.author?.flatten()?.unique()?.size()
            def msgs = extractMessages(entry)
            def stepErrors = extractStepErrors(entry)
            def compilationErrors = extractCompilationErrors(entry)
            String[] line = [entry.task.id, dates, entry.task.days, entry.task.commitsQuantity, msgs, devs,
                             entry.task.gherkinTestQuantity, entry.task.stepDefQuantity, entry.methods, entry.stepCalls,
                             stepErrors.text, stepErrors.quantity, compilationErrors.text, compilationErrors.quantity,
                             entry.itest, entry.ireal, precision, recall]
            writer.writeNext(line)
            writeITextFile(filename, entry) //dealing with long textual description of a task
        }

        writer.close()
    }

    static List<String[]> readAllResult(String filename){
        List<String[]> entries = []
        try{
            CSVReader reader = new CSVReader(new FileReader(filename))
            entries = reader.readAll()
            reader.close()
        } catch (Exception ex){
            log.error ex.message
        }
        entries
    }

    static organizeResult(String evaluationFile, String organizedFile){
        organizeAllResult(evaluationFile, organizedFile, null, false)
    }

    static organizeResultForSimilarityAnalysis(String evaluationFile, String organizedFile, String similarityFile){
        organizeAllResult(evaluationFile, organizedFile, similarityFile, true)
    }


    static analyseSimilarity(String filteredFile, String similarityFile){
        log.info "Checking similarity among tasks..."
        if(!filteredFile || filteredFile.empty) return
        List<String[]> entries = readAllResult(filteredFile)
        if(entries.size() <= 4) return

        CSVWriter writer = new CSVWriter(new FileWriter(similarityFile))
        writer.writeNext(entries.get(0))

        String[] resultHeader = ["Task_A", "Task_B", "Text", "Test", "Real" ]
        writer.writeNext(resultHeader)

        def allTasks = entries.subList(2,entries.size())
        if(allTasks.size()<=1) return
        def taskPairs = computePairs(allTasks)
        List<String[]> lines = []
        taskPairs?.each { item ->
            def task = item.task
            def taskText = extractTaskText(filteredFile, task[0])
            def itest1 = task[14].split(", ") as List
            def ireal1 = task[15].split(", ") as List

            item.pairs?.each { other ->
                log.info "Similarity between tasks ${task[0]} and ${other[0]}"

                def otherText = extractTaskText(filteredFile, other[0]) //other[10]
                def textualSimilarityAnalyser = new TextualSimilarityAnalyser()
                def textSimilarity = textualSimilarityAnalyser.calculateSimilarity(taskText, otherText)
                log.info "Textual similarity result: $textSimilarity"

                def itest2 = other[14].split(", ") as List
                def ireal2 = other[15].split(", ") as List
                def testSimilarity = TestSimilarityAnalyser.calculateSimilarityByJaccard(itest1, itest2)
                def cosine = TestSimilarityAnalyser.calculateSimilarityByCosine(itest1, itest2)
                log.info "Test similarity (jaccard index): $testSimilarity"
                log.info "Test similarity (cosine): $cosine"

                def realSimilarity = TestSimilarityAnalyser.calculateSimilarityByJaccard(ireal1, ireal2)
                cosine = TestSimilarityAnalyser.calculateSimilarityByCosine(ireal1, ireal2)
                log.info "Real similarity (jaccard index): $realSimilarity"
                log.info "Real similarity (cosine): $cosine"

                String[] line = [task[0], other[0], textSimilarity, testSimilarity, realSimilarity]
                lines += line
            }
        }

        writer.writeAll(lines)
        writer.close()
    }

}

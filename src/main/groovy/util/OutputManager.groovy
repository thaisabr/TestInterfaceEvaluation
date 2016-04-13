package util

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import groovy.util.logging.Slf4j
import similarityAnalyser.test.TestSimilarityAnalyser
import similarityAnalyser.text.TextualSimilarityAnalyser

@Slf4j
class OutputManager {

    static List<String[]> readOutputCSV(String filename){
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

    static organizeResult(){
        organizeResult(Util.DEFAULT_EVALUATION_FILE)
    }

    static organizeResult(String filename){
        log.info "Organizing results..."
        if(!filename || filename.empty) return
        List<String[]> entries = readOutputCSV(filename)
        if(entries.size() <= 4) return

        CSVWriter writer = new CSVWriter(new FileWriter(filename-".csv"+"-organized.csv"))

        def previousAnalysisData = entries.subList(0,3)
        previousAnalysisData.each{ data ->
            String[] value = data.findAll{!it.allWhitespace}
            writer.writeNext(value)
        }

        String[] resultHeader = entries.get(3).findAll{!it.allWhitespace} + ["Empty_ITest", "Empty_IReal"]
        /* ["Valid_IReal", "Excluded_IReal", "New_Precision", "New_Recall"]*/
        entries = entries.subList(4,entries.size())

        def emptyITest = entries.findAll{ it[4].empty }
        writer.writeNext("Number of tasks with empty ITest: ${emptyITest.size()}")

        def emptyIReal = entries.findAll{ it[5].empty }
        writer.writeNext("Number of tasks with empty IReal: ${emptyIReal.size()}")

        def emptyITestAndIReal = entries.findAll{ it[4].empty && it[5].empty }
        writer.writeNext("Number of tasks with empty ITest and IReal: ${emptyITestAndIReal.size()}")

        writer.writeNext("Number of valid tasks: ${(entries-(emptyITestAndIReal+emptyIReal+emptyITest)).size()}")
        writer.writeNext(resultHeader)

        /*writeResult(emptyITestAndIReal, writer)
        writeResult(emptyIReal-emptyITestAndIReal, writer)
        writeResult(emptyITest-emptyITestAndIReal, writer)
        writeResult(entries-emptyITestAndIReal-emptyIReal-emptyITest, writer)*/
        writeResult(entries-emptyIReal,writer)

        writer.close()
        log.info "The results were saved!"
    }

    static writeResult(List<String[]> entries, def writer){
        entries.each{ entry ->
            def itest = "no", ireal = "no"
            if(entry[5].empty) ireal = "yes"
            if(entry[4].empty) itest = "yes"

            String[] headers = entry + [itest, ireal]
            writer.writeNext(headers)
        }
    }

    static List<String> identifyUnknownMethods(){
        List<String[]> entries = readOutputCSV(Util.DEFAULT_EVALUATION_FILE)
        def data = entries.subList(4,entries.size())
        data = data.findAll{ !it[8].empty }
        data = data.collect{ it[8]?.substring(1,it[8]?.length()-1)?.split(",") }?.flatten()
        data = data*.trim()?.unique()?.sort()
        println "Number of methods: ${data.size()}"
        data.each{ println it.toString() }
        data
    }

    static identifyValidIRealFiles(String filename){
        if(!filename || filename.empty) return
        List<String[]> entries = readOutputCSV(filename)
        if(entries.size() <= 4) return

        CSVWriter writer = new CSVWriter(new FileWriter("output${File.separator}IReal.csv"))
        String[] resultHeader = ["Task", "IReal", "Valid files", "Invalid files"]
        writer.writeNext(resultHeader)

        entries = entries?.subList(8,entries.size())?.findAll{ !it[5].empty }
        entries.each{ entry ->
            def files = entry[5].split(",")*.trim()?.unique()?.sort()
            def valid = Util.findAllProductionFiles(files)
            def invalid = files - valid
            String[] content = [entry[0],files, valid, invalid]
            writer.writeNext(content)
        }
    }

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
        File file = new File("${filename-"-organized.csv"}_text_${taskId}.txt")
        file.withReader { reader ->
            text = reader.readLines().join(System.lineSeparator())
        }
        text
    }

    static analyseSimilarity(){
        analyseSimilarity(Util.DEFAULT_EVALUATION_ORGANIZED_FILE)
    }

    static analyseSimilarity(String filename){
        log.info "Checking similarity among tasks..."
        if(!filename || filename.empty) return
        List<String[]> entries = readOutputCSV(filename)
        if(entries.size() <= 4) return

        CSVWriter writer = new CSVWriter(new FileWriter(filename-"-organized.csv"+"-similarity.csv"))
        writer.writeNext(entries.get(0))
        writer.writeNext(entries.get(1))

        String[] resultHeader = ["Task_A", "Task_B", "Text", "Test", "Real" ]
        writer.writeNext(resultHeader)

        def allTasks = entries.subList(8,entries.size())
        if(allTasks.size()<=1) return
        def taskPairs = computePairs(allTasks)
        List<String[]> lines = []
        taskPairs?.each { item ->
            def task = item.task
            def taskText = extractTaskText(filename, task[0])
            def itest1 = task[4].split(", ") as List
            def ireal1 = task[5].split(", ") as List

            item.pairs?.each { other ->
                log.info "Similarity between tasks ${task[0]} and ${other[0]}"

                def otherText = extractTaskText(filename, other[0]) //other[10]
                def textualSimilarityAnalyser = new TextualSimilarityAnalyser()
                def textSimilarity = textualSimilarityAnalyser.calculateSimilarity(taskText, otherText)
                log.info "Textual similarity result: $textSimilarity"

                def itest2 = other[4].split(", ") as List
                def ireal2 = other[5].split(", ") as List
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
        log.info "The results were saved!"
    }
}

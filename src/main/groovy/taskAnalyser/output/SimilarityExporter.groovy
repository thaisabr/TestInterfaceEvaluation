package taskAnalyser.output

import groovy.util.logging.Slf4j
import similarityAnalyser.test.TestSimilarityAnalyser
import similarityAnalyser.text.TextualSimilarityAnalyser
import util.ConstantData
import util.CsvUtil

@Slf4j
class SimilarityExporter {

    String filteredFile
    String similarityFile

    SimilarityExporter(String filteredFile, String similarityFile){
        this.filteredFile = filteredFile
        this.similarityFile = similarityFile
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

    private extractTaskText(taskId) {
        def text = ""
        File file = new File("${filteredFile - ConstantData.FILTERED_FILE_SUFIX}_text_${taskId}.txt")
        if (file.exists()) {
            file.withReader("utf-8") { reader ->
                text = reader.text
            }
        }
        text
    }

    def save() {
        if (!filteredFile || filteredFile.empty || !(new File(filteredFile).exists())) return
        List<String[]> entries = CsvUtil.read(filteredFile)
        if (entries.size() <= RelevantTaskExporter.INITIAL_TEXT_SIZE) return

        List<String[]> content = []
        content += entries.get(0)
        String[] resultHeader = ["Task_A", "Task_B", "Text", "Test_Jaccard", "Real_Jaccard", "Test_Cosine", "Real_Cosine"]
        content += resultHeader

        def allTasks = entries.subList(RelevantTaskExporter.INITIAL_TEXT_SIZE, entries.size())
        if (allTasks.size() <= 1) return
        def taskPairs = computePairs(allTasks)
        taskPairs?.each { item ->
            def task = item.task
            def taskText = extractTaskText(task[0])
            def itest1 = task[RelevantTaskExporter.ITEST_INDEX].split(", ") as List
            def ireal1 = task[RelevantTaskExporter.IREAL_INDEX].split(", ") as List

            item.pairs?.each { other ->
                def otherText = extractTaskText(other[0])
                def textualSimilarityAnalyser = new TextualSimilarityAnalyser()
                def textSimilarity = textualSimilarityAnalyser.calculateSimilarity(taskText, otherText)
                def itest2 = other[RelevantTaskExporter.ITEST_INDEX].split(", ") as List
                def ireal2 = other[RelevantTaskExporter.IREAL_INDEX].split(", ") as List
                def testSimJaccard = TestSimilarityAnalyser.calculateSimilarityByJaccard(itest1, itest2)
                def testSimCosine = TestSimilarityAnalyser.calculateSimilarityByCosine(itest1, itest2)
                def realSimJaccard = TestSimilarityAnalyser.calculateSimilarityByJaccard(ireal1, ireal2)
                def realSimCosine = TestSimilarityAnalyser.calculateSimilarityByCosine(ireal1, ireal2)

                String[] line = [task[0], other[0], textSimilarity, testSimJaccard, realSimJaccard, testSimCosine, realSimCosine]
                content += line
            }
        }

        CsvUtil.write(similarityFile, content)
    }
}

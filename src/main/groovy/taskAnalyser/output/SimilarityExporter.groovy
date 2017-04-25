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
    String organizedFile

    SimilarityExporter(String filteredFile, String similarityFile, String organizedFile){
        this.filteredFile = filteredFile
        this.similarityFile = similarityFile
        this.organizedFile = organizedFile
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
        if (entries.size() <= 4) return

        List<String[]> content = []
        content += entries.get(0)
        String[] resultHeader = ["Task_A", "Task_B", "Text", "Test_Jaccard", "Real_Jaccard", "Test_Cosine", "Real_Cosine"]
        content += resultHeader

        def allTasks = entries.subList(2, entries.size())
        if (allTasks.size() <= 1) return
        def taskPairs = computePairs(allTasks)
        taskPairs?.each { item ->
            def task = item.task
            def taskText = extractTaskText(task[0])
            def itest1 = task[EvaluationExporter.ITEST_INDEX].split(", ") as List
            def ireal1 = task[EvaluationExporter.IREAL_INDEX].split(", ") as List

            item.pairs?.each { other ->
                log.info "Similarity between tasks ${task[0]} and ${other[0]}"

                def otherText = extractTaskText(other[0])
                def textualSimilarityAnalyser = new TextualSimilarityAnalyser()
                def textSimilarity = textualSimilarityAnalyser.calculateSimilarity(taskText, otherText)
                log.info "Textual similarity result: $textSimilarity"

                def itest2 = other[EvaluationExporter.ITEST_INDEX].split(", ") as List
                def ireal2 = other[EvaluationExporter.IREAL_INDEX].split(", ") as List
                def testSimJaccard = TestSimilarityAnalyser.calculateSimilarityByJaccard(itest1, itest2)
                def testSimCosine = TestSimilarityAnalyser.calculateSimilarityByCosine(itest1, itest2)
                log.info "Test similarity (jaccard index): $testSimJaccard"
                log.info "Test similarity (cosine): $testSimCosine"

                def realSimJaccard = TestSimilarityAnalyser.calculateSimilarityByJaccard(ireal1, ireal2)
                def realSimCosine = TestSimilarityAnalyser.calculateSimilarityByCosine(ireal1, ireal2)
                log.info "Real similarity (jaccard index): $realSimJaccard"
                log.info "Real similarity (cosine): $realSimCosine"

                String[] line = [task[0], other[0], textSimilarity, testSimJaccard, realSimJaccard, testSimCosine, realSimCosine]
                content += line
            }
        }

        CsvUtil.write(similarityFile, content)
    }

    def saveOrganized() {
        if (!similarityFile || similarityFile.empty || !(new File(similarityFile).exists())) return
        List<String[]> entries = CsvUtil.read(similarityFile)
        if (entries.size() <= 2) return

        List<String[]> content = []
        content += entries.get(0)
        String[] resultHeader1 = entries.get(1).findAll { !it.allWhitespace }
        content += resultHeader1

        entries = entries.subList(2, entries.size())
        //Positions: 2-text; 3-test; 4-real
        def zeroReal = entries.findAll { it[4] == "0.0" }.sort { it[3] as BigDecimal }
        content += zeroReal
        entries = entries - zeroReal

        def oneReal = entries.findAll { it[4] == "1.0" }.sort { -(it[3] as BigDecimal) }
        content += oneReal
        entries = entries - oneReal

        def others = entries.sort { -(it[4] as BigDecimal) }.sort { -(it[3] as BigDecimal) }
        content += others

        CsvUtil.write(organizedFile, content)
    }

}

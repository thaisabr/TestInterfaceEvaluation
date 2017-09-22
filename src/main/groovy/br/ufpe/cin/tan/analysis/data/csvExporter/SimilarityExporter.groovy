package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.similarity.test.TestSimilarityAnalyser
import br.ufpe.cin.tan.similarity.text.TextualSimilarityAnalyser
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import groovy.util.logging.Slf4j

import java.util.regex.Matcher

@Slf4j
class SimilarityExporter {

    String analysedTasksFile
    String similarityFile

    SimilarityExporter(String analysedTasksFile, String similarityFile) {
        this.analysedTasksFile = analysedTasksFile
        this.similarityFile = similarityFile
    }

    def save() {
        if (!analysedTasksFile || analysedTasksFile.empty || !(new File(analysedTasksFile).exists())) return
        List<String[]> entries = CsvUtil.read(analysedTasksFile)
        if (entries.size() <= ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER) return

        List<String[]> content = []
        content += entries.get(0)
        String[] resultHeader = ["Task_A", "Task_B", "Text", "Test_Jaccard", "Real_Jaccard", "Test_Cosine", "Real_Cosine"]
        content += resultHeader

        def allTasks = entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
        if (allTasks.size() <= 1) return
        def taskPairs = ExporterUtil.computeTaskPairs(allTasks)
        List<String[]> data = []
        taskPairs?.each { item ->
            def task = item.task
            def taskText = extractTaskText(task[0])
            def itest1 = task[ExporterUtil.ITEST_INDEX_SHORT_HEADER]?.replace(File.separator, Matcher.quoteReplacement(File.separator))
                    ?.substring(1, task[ExporterUtil.ITEST_INDEX_SHORT_HEADER].size() - 1)?.split(", ") as List
            def ireal1 = task[ExporterUtil.IREAL_INDEX_SHORT_HEADER]?.replace(File.separator, Matcher.quoteReplacement(File.separator))
                    ?.substring(1, task[ExporterUtil.IREAL_INDEX_SHORT_HEADER].size() - 1)?.split(", ") as List

            item.pairs?.each { other ->
                def otherText = extractTaskText(other[0])
                def textualSimilarityAnalyser = new TextualSimilarityAnalyser()
                def textSimilarity = textualSimilarityAnalyser.calculateSimilarity(taskText, otherText)

                def itest2 = other[ExporterUtil.ITEST_INDEX_SHORT_HEADER]
                        ?.replace(File.separator, Matcher.quoteReplacement(File.separator))
                        ?.substring(1, other[ExporterUtil.ITEST_INDEX_SHORT_HEADER].size() - 1)?.split(", ") as List
                def ireal2 = other[ExporterUtil.IREAL_INDEX_SHORT_HEADER]
                        ?.replace(File.separator, Matcher.quoteReplacement(File.separator))
                        ?.substring(1, other[ExporterUtil.IREAL_INDEX_SHORT_HEADER].size() - 1)?.split(", ") as List

                def similarityAnalyser = new TestSimilarityAnalyser(itest1, itest2)
                def testSimJaccard = similarityAnalyser.calculateSimilarityByJaccard()
                def testSimCosine = similarityAnalyser.calculateSimilarityByCosine()

                similarityAnalyser = new TestSimilarityAnalyser(ireal1, ireal2)
                def realSimJaccard = similarityAnalyser.calculateSimilarityByJaccard()
                def realSimCosine = similarityAnalyser.calculateSimilarityByCosine()

                data += [task[0], other[0], textSimilarity, testSimJaccard, realSimJaccard, testSimCosine, realSimCosine] as String[]
            }
        }

        def textSimilarity = data.collect { it[2] as double } as double[]
        def dataRealJaccard = data.collect { it[4] as double } as double[]
        def dataRealCosine = data.collect { it[6] as double } as double[]
        def correlationJaccard = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity, dataRealJaccard)
        def correlationCosine = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity, dataRealCosine)
        content += ["Correlation Jaccard Text-Real", correlationJaccard.toString()] as String[]
        content += ["Correlation Cosine Text-Real", correlationCosine.toString()] as String[]
        content += data
        CsvUtil.write(similarityFile, content)
    }

    private extractTaskText(taskId) {
        def text = ""
        def filename = analysedTasksFile - ConstantData.RELEVANT_TASKS_FILE_SUFIX
        def index = filename.lastIndexOf(File.separator)
        if (index >= 0) filename = "${filename.substring(0, index)}${File.separator}text${File.separator}${taskId}.txt"
        File file = new File(filename)
        if (file.exists()) {
            file.withReader("utf-8") { reader ->
                text = reader.text
            }
        } else log.warn "Text file '${filename}' not found!"
        text
    }

}

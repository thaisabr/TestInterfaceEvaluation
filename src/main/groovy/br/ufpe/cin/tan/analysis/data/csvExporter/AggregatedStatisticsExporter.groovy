package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.TaskAnalyser
import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil

class AggregatedStatisticsExporter {

    List<TaskAnalyser> analysers
    String aggregatedStatisticsFile
    def relevantSimilarityFiles
    def validSimilarityFiles
    def relevantTasksFiles
    def relevantTasksControllerFiles
    def validTasksFiles
    def validTasksControllerFiles
    def correlationJaccard
    def correlationCosine
    def correlationITestPrecision
    def correlationITestRecall

    AggregatedStatisticsExporter(List<TaskAnalyser> analysers) {
        this.analysers = analysers
        aggregatedStatisticsFile = "${ConstantData.DEFAULT_EVALUATION_FOLDER}${File.separator}aggregated.csv"
    }

    def generateAggregatedStatistics() {
        List<String[]> content = []
        content += ["Relevant similarity files"] as String[]
        relevantSimilarityFiles = analysers.collect { it.similarityRelevantFile }
        content += aggregatedCorrelationTextReal(relevantSimilarityFiles)

        content += ["Valid similarity files"] as String[]
        validSimilarityFiles = analysers.collect { it.similarityValidFile }
        content += aggregatedCorrelationTextReal(validSimilarityFiles)

        content += ["Relevant tasks files"] as String[]
        relevantTasksFiles = analysers.collect { it.relevantTasksFile }
        content += aggregatedCorrelationTestsPrecisionRecall(relevantTasksFiles)

        content += ["Relevant controller tasks files"] as String[]
        relevantTasksControllerFiles = analysers.collect { it.relevantTasksControllerFile }
        content += aggregatedCorrelationTestsPrecisionRecall(relevantTasksControllerFiles)

        content += ["Valid tasks files"] as String[]
        validTasksFiles = analysers.collect { it.validTasksFile }
        content += aggregatedCorrelationTestsPrecisionRecall(validTasksFiles)

        content += ["Valid controller tasks files"] as String[]
        validTasksControllerFiles = analysers.collect { it.validTasksControllerFile }
        content += aggregatedCorrelationTestsPrecisionRecall(validTasksControllerFiles)
        CsvUtil.write(aggregatedStatisticsFile, content)
    }

    private aggregatedCorrelationTextReal(List<String> files) {
        List<String[]> content = []
        def textSimilarity = []
        def dataRealJaccard = []
        def dataRealCosine = []

        files?.each { file ->
            List<String[]> entries = CsvUtil.read(file)
            if (entries.size() > SimilarityExporter.INITIAL_TEXT_SIZE) {
                def data = entries.subList(SimilarityExporter.INITIAL_TEXT_SIZE, entries.size())
                textSimilarity += data.collect { it[SimilarityExporter.TEXT_SIMILARITY_INDEX] as double }
                dataRealJaccard += data.collect { it[SimilarityExporter.REAL_JACCARD_INDEX] as double }
                dataRealCosine += data.collect { it[SimilarityExporter.REAL_COSINE_INDEX] as double }
            }
        }

        textSimilarity = textSimilarity.flatten()
        dataRealJaccard = dataRealJaccard.flatten()
        dataRealCosine = dataRealCosine.flatten()

        correlationJaccard = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity as double[], dataRealJaccard as double[])
        correlationCosine = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity as double[], dataRealCosine as double[])
        content += ["Correlation Jaccard Text-Real", correlationJaccard.toString()] as String[]
        content += ["Correlation Cosine Text-Real", correlationCosine.toString()] as String[]
        content
    }

    private aggregatedCorrelationTestsPrecisionRecall(List<String> files) {
        List<String[]> content = []
        def tests = []
        def precisionValues = []
        def recallValues = []

        files?.each { file ->
            List<String[]> entries = CsvUtil.read(file)
            if (entries.size() > ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER) {
                def data = entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
                tests += data.collect { it[ExporterUtil.IMPLEMENTED_GHERKIN_TESTS] as double }
                precisionValues += data.collect { it[ExporterUtil.PRECISION_INDEX_SHORT_HEADER] as double }
                recallValues += data.collect { it[ExporterUtil.RECALL_INDEX_SHORT_HEADER] as double }
            }
        }

        tests = tests.flatten()
        precisionValues = precisionValues.flatten()
        recallValues = recallValues.flatten()

        correlationITestPrecision = TaskInterfaceEvaluator.calculateCorrelation(tests as double[], precisionValues as double[])
        correlationITestRecall = TaskInterfaceEvaluator.calculateCorrelation(tests as double[], recallValues as double[])
        content += ["Correlation #ITest-Precision", correlationITestPrecision.toString()] as String[]
        content += ["Correlation #ITest-Recall", correlationITestRecall.toString()] as String[]
        content
    }

}

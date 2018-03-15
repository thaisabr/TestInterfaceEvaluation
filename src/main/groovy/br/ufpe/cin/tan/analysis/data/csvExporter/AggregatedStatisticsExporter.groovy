package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util

class AggregatedStatisticsExporter {

    String aggregatedStatisticsFile
    def relevantSimilarityFiles
    def validSimilarityFiles
    def relevantTasksFiles
    def relevantTasksControllerFiles
    def validTasksFiles
    def validTasksControllerFiles
    def correlationJaccard
    def correlationCosine
    def correlationTestsPrecision
    def correlationTestsRecall
    def correlationITestPrecision
    def correlationITestRecall
    def correlationTestsF2
    def correlationITestF2
    def correlationTestsFP
    def correlationTestsFN
    def correlationITestFP
    def correlationITestFN

    AggregatedStatisticsExporter(String folder) {
        aggregatedStatisticsFile = "${folder}${File.separator}aggregated.csv"
        def output = Util.findFilesFromDirectory(folder).findAll {
            it.contains("${File.separator}${ConstantData.SELECTED_TASKS_BY_CONFIGS_FOLDER}${File.separator}")
        }
        relevantSimilarityFiles = output.findAll { it.endsWith("-relevant" + ConstantData.SIMILARITY_FILE_SUFIX) }
        validSimilarityFiles = output.findAll { it.endsWith("-valid" + ConstantData.SIMILARITY_FILE_SUFIX) }
        relevantTasksFiles = output.findAll { it.endsWith(ConstantData.RELEVANT_TASKS_FILE_SUFIX) }
        relevantTasksControllerFiles = output.findAll { it.endsWith("-relevant" + ConstantData.CONTROLLER_FILE_SUFIX) }
        validTasksFiles = output.findAll { it.endsWith(ConstantData.VALID_TASKS_FILE_SUFIX) }
        validTasksControllerFiles = output.findAll { it.endsWith("-valid" + ConstantData.CONTROLLER_FILE_SUFIX) }
    }

    def generateAggregatedStatistics() {
        List<String[]> content = []
        content += ["Relevant similarity files"] as String[]
        content += aggregatedCorrelationTextReal(relevantSimilarityFiles)
        content += ["Valid similarity files"] as String[]
        content += aggregatedCorrelationTextReal(validSimilarityFiles)
        content += ["Relevant tasks files"] as String[]
        content += aggregatedCorrelationTestsPrecisionRecall(relevantTasksFiles)
        content += ["Relevant controller tasks files"] as String[]
        content += aggregatedCorrelationTestsPrecisionRecall(relevantTasksControllerFiles)
        content += ["Valid tasks files"] as String[]
        content += aggregatedCorrelationTestsPrecisionRecall(validTasksFiles)
        content += ["Valid controller tasks files"] as String[]
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
        def itestSize = []
        def fpValues = []
        def fnValues = []
        def f2Values = []

        files?.each { file ->
            List<String[]> entries = CsvUtil.read(file)
            if (entries.size() > ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER) {
                def data = entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
                tests += data.collect { it[ExporterUtil.IMPLEMENTED_GHERKIN_TESTS] as double }
                itestSize += data.collect { it[ExporterUtil.ITEST_SIZE_INDEX_SHORT_HEADER] as double }
                precisionValues += data.collect { it[ExporterUtil.PRECISION_INDEX_SHORT_HEADER] as double }
                recallValues += data.collect { it[ExporterUtil.RECALL_INDEX_SHORT_HEADER] as double }
                fpValues += data.collect { it[ExporterUtil.FP_NUMBER_INDEX] as double }
                fnValues += data.collect { it[ExporterUtil.FN_NUMBER_INDEX] as double }
                f2Values += data.collect { it[ExporterUtil.F2_INDEX] as double }
            }
        }

        tests = tests.flatten()
        itestSize = itestSize.flatten()
        precisionValues = precisionValues.flatten()
        recallValues = recallValues.flatten()
        fpValues = fpValues.flatten()
        fnValues = fnValues.flatten()
        f2Values = f2Values.flatten()

        def measure1, measure2
        if (Util.SIMILARITY_ANALYSIS) {
            measure1 = "Jaccard"
            measure2 = "Cosine"
        } else {
            measure1 = "Precision"
            measure2 = "Recall"
        }
        def text1 = "Correlation #Test-$measure1"
        def text2 = "Correlation #Test-$measure2"
        def text3 = "Correlation #ITest-$measure1"
        def text4 = "Correlation #ITest-$measure2"
        def text5 = "Correlation #Test-F2"
        def text6 = "Correlation #ITest-F2"
        def text7 = "Correlation #Test-FP"
        def text8 = "Correlation #Test-FN"
        def text9 = "Correlation #ITest-FP"
        def text10 = "Correlation #ITest-FN"

        correlationTestsPrecision = TaskInterfaceEvaluator.calculateCorrelation(tests as double[], precisionValues as double[])
        correlationTestsRecall = TaskInterfaceEvaluator.calculateCorrelation(tests as double[], recallValues as double[])
        content += [text1, correlationTestsPrecision.toString()] as String[]
        content += [text2, correlationTestsRecall.toString()] as String[]
        correlationITestPrecision = TaskInterfaceEvaluator.calculateCorrelation(itestSize as double[], precisionValues as double[])
        correlationITestRecall = TaskInterfaceEvaluator.calculateCorrelation(itestSize as double[], recallValues as double[])
        content += [text3, correlationITestPrecision.toString()] as String[]
        content += [text4, correlationITestRecall.toString()] as String[]
        correlationTestsF2 = TaskInterfaceEvaluator.calculateCorrelation(tests as double[], f2Values as double[])
        correlationITestF2 = TaskInterfaceEvaluator.calculateCorrelation(itestSize as double[], f2Values as double[])
        content += [text5, correlationTestsF2.toString()] as String[]
        content += [text6, correlationITestF2.toString()] as String[]
        correlationTestsFP = TaskInterfaceEvaluator.calculateCorrelation(tests as double[], fpValues as double[])
        correlationTestsFN = TaskInterfaceEvaluator.calculateCorrelation(tests as double[], fnValues as double[])
        content += [text7, correlationTestsFP.toString()] as String[]
        content += [text8, correlationTestsFN.toString()] as String[]
        correlationITestFP = TaskInterfaceEvaluator.calculateCorrelation(itestSize as double[], fpValues as double[])
        correlationITestFN = TaskInterfaceEvaluator.calculateCorrelation(itestSize as double[], fnValues as double[])
        content += [text9, correlationITestFP.toString()] as String[]
        content += [text10, correlationITestFN.toString()] as String[]
        content
    }

}

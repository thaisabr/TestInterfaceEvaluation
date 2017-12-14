package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.similarity.test.TestSimilarityAnalyser
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util

class ControllerFilterRandomExporter {

    String file
    String controllerFile
    List<String[]> entries

    ControllerFilterRandomExporter(String file) {
        this.file = file
        this.controllerFile = file - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        entries = CsvUtil.read(file)
    }

    private generateMainHeader() {
        List<String[]> values = extractData()
        double[] precisionValues = values.collect { it[5] as double }
        double[] recallValues = values.collect { it[6] as double }
        List<String[]> content = []
        content += ExporterUtil.generateStatistics(precisionValues, recallValues)
        content += entries.get(ExporterUtil.INITIAL_TEXT_SIZE_NO_CORRELATION_SHORT_HEADER - 1)
        content
    }

    private extractData() {
        entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_NO_CORRELATION_SHORT_HEADER, entries.size())
    }

    def save() {
        if (!file || file.empty || !(new File(file).exists()) || entries.size() <= 1) return
        List<String[]> data = []
        data += entries.get(0)
        List<String[]> content = []
        List<String[]> values = extractData()
        values?.each { content += configureLine(it) }
        List<String[]> header = generateMainHeader()
        data += header
        data += content
        CsvUtil.write(controllerFile, data)
    }

    private static String[] configureLine(String[] value) {
        def originalIRandom = ExporterUtil.configureITask(value, 3)
        def irandom = ExporterUtil.findControllers(originalIRandom)
        def originalIReal = ExporterUtil.configureITask(value, 4)
        def ireal = ExporterUtil.findControllers(originalIReal)
        def precision, recall

        if (Util.SIMILARITY_ANALYSIS) {
            def similarityAnalyser = new TestSimilarityAnalyser(irandom, ireal)
            precision = similarityAnalyser.calculateSimilarityByJaccard()
            recall = similarityAnalyser.calculateSimilarityByCosine()
        } else {
            precision = TaskInterfaceEvaluator.calculateFilesPrecision(irandom, ireal)
            recall = TaskInterfaceEvaluator.calculateFilesRecall(irandom, ireal)
        }

        def falsePositives = irandom - ireal
        def falseNegatives = ireal - irandom
        def hits = irandom.intersect(ireal)

        String[] line = value
        line[1] = irandom.size()
        line[2] = ireal.size()
        line[3] = irandom
        line[4] = ireal
        line[5] = precision
        line[6] = recall
        line[7] = falsePositives.size()
        line[8] = falseNegatives.size()
        line[9] = falsePositives
        line[10] = falseNegatives
        line[11] = hits.size()
        line[12] = hits
        line
    }

}

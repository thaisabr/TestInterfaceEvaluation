package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil

class ControllerFilterRandomExporter {

    String file
    String controllerFile
    String controllerOrgFile
    List<String[]> entries

    ControllerFilterRandomExporter(String file) {
        this.file = file
        this.controllerFile = file - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        this.controllerOrgFile = controllerFile - ConstantData.CONTROLLER_FILE_SUFIX + ConstantData.CONTROLLER_ORGANIZED_FILE_SUFIX
        entries = CsvUtil.read(file)
    }

    private generateMainHeader() {
        List<String[]> values = extractData()
        double[] precisionValues = values.collect { it[5] as double }
        double[] recallValues = values.collect { it[6] as double }
        List<String[]> content = []
        content += ExporterUtil.generateStatistics(precisionValues, recallValues)
        content += entries.get(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER - 1)
        content
    }

    private extractData() {
        entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
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
        def precision = TaskInterfaceEvaluator.calculateFilesPrecision(irandom, ireal)
        def recall = TaskInterfaceEvaluator.calculateFilesRecall(irandom, ireal)
        def diff1 = irandom - ireal
        def diff2 = ireal - irandom
        def hits = irandom.intersect(ireal)

        String[] line = value
        line[1] = irandom.size()
        line[2] = ireal.size()
        line[3] = irandom
        line[4] = ireal
        line[5] = precision
        line[6] = recall
        line[7] = diff1.size()
        line[8] = diff2.size()
        line[9] = diff1
        line[10] = diff2
        line[11] = hits.size()
        line[12] = hits
        line
    }

}

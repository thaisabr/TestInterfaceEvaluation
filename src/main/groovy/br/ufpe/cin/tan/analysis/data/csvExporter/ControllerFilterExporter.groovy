package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil

class ControllerFilterExporter {

    String file
    String controllerFile
    List<String[]> entries

    ControllerFilterExporter(String file) {
        this.file = file
        this.controllerFile = file - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        entries = CsvUtil.read(file)
    }

    private generateMainHeader() {
        List<String[]> values = extractData()
        double[] tests = values.collect { it[ExporterUtil.ITEST_SIZE_INDEX_SHORT_HEADER] as double }
        double[] precisionValues = values.collect { it[ExporterUtil.PRECISION_INDEX_SHORT_HEADER] as double }
        double[] recallValues = values.collect { it[ExporterUtil.RECALL_INDEX_SHORT_HEADER] as double }
        List<String[]> content = []
        content += ExporterUtil.generateStatistics(precisionValues, recallValues, tests)
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
        values?.each { content += ExporterUtil.configureLine(it) }
        List<String[]> header = generateMainHeader()
        data += header
        data += content
        CsvUtil.write(controllerFile, data)
    }

}

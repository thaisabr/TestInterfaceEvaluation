package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil

class ControllerFilterExporter {

    String file
    String controllerFile
    String controllerOrgFile
    List<String[]> entries

    ControllerFilterExporter(String file){
        this.file = file
        this.controllerFile = file - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        this.controllerOrgFile = controllerFile - ConstantData.CONTROLLER_FILE_SUFIX + ConstantData.CONTROLLER_ORGANIZED_FILE_SUFIX
        entries = CsvUtil.read(file)
    }

    private generateMainHeader() {
        List<String[]> content = []
        def lines = entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
        double[] precisionValues = lines.collect { it[ExporterUtil.PRECISION_INDEX_SHORT_HEADER] as double }
        double[] recallValues = lines.collect { it[ExporterUtil.RECALL_INDEX_SHORT_HEADER] as double }
        content += ExporterUtil.generateStatistics(precisionValues, recallValues)
        content += entries.get(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER-1)
        content
    }

    def save() {
        if (!file || file.empty || !(new File(file).exists()) || entries.size() <= 1) return
        List<String[]> data = []
        data += entries.get(0)
        List<String[]> content = []
        List<String[]> values = entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
        values?.each { content += ExporterUtil.configureLine(it) }
        List<String[]> header = generateMainHeader()
        data += header
        data += content
        CsvUtil.write(controllerFile, data)
    }

}

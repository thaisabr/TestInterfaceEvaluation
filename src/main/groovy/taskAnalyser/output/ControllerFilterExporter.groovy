package taskAnalyser.output

import evaluation.TaskInterfaceEvaluator
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import util.ConstantData
import util.CsvUtil
import util.RegexUtil
import util.Util


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
        def lines = entries.subList(RelevantTaskExporter.INITIAL_TEXT_SIZE, entries.size())
        double[] precisionValues = lines.collect { it[RelevantTaskExporter.PRECISION_INDEX] as double }
        def itestStatistics = new DescriptiveStatistics(precisionValues)
        double[] recallValues = lines.collect { it[RelevantTaskExporter.RECALL_INDEX] as double }
        def irealStatistics = new DescriptiveStatistics(recallValues)
        content += ["Precision mean (RT)", itestStatistics.mean] as String[]
        content += ["Precision median (RT)", itestStatistics.getPercentile(50)] as String[]
        content += ["Precision standard deviation (RT)", itestStatistics.standardDeviation] as String[]
        content += ["Recall mean (RT)", irealStatistics.mean] as String[]
        content += ["Recall median (RT)", irealStatistics.getPercentile(50)] as String[]
        content += ["Recall standard deviation (RT)", irealStatistics.standardDeviation] as String[]
        content += entries.get(RelevantTaskExporter.INITIAL_TEXT_SIZE-1)
        content
    }

    def save() {
        if (!file || file.empty || !(new File(file).exists()) || entries.size() <= 1) return

        List<String[]> data = []
        data += entries.get(0)

        List<String[]> content = []
        def entries = entries.subList(RelevantTaskExporter.INITIAL_TEXT_SIZE, entries.size())
        entries?.each { entry ->
            def originalItest = entry[RelevantTaskExporter.ITEST_INDEX].replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"/")
                    .substring(1,entry[RelevantTaskExporter.ITEST_INDEX].size()-1)
                    .split(",")
                    .flatten()
                    .collect{ it.trim() } as Set
            def itest = originalItest.findAll { Util.isControllerFile(it) }
            def originalIReal = entry[RelevantTaskExporter.IREAL_INDEX].replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"/")
                    .substring(1,entry[RelevantTaskExporter.IREAL_INDEX].size()-1)
                    .split(",")
                    .flatten()
                    .collect{ it.trim() } as Set
            def ireal = originalIReal.findAll { Util.isControllerFile(it) }
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(itest, ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(itest, ireal)

            String[] line = entry
            line[RelevantTaskExporter.ITEST_SIZE_INDEX] = itest.size()
            line[RelevantTaskExporter.IREAL_SIZE_INDEX] = ireal.size()
            line[RelevantTaskExporter.ITEST_INDEX] = itest
            line[RelevantTaskExporter.IREAL_INDEX] = ireal
            line[RelevantTaskExporter.PRECISION_INDEX] = precision
            line[RelevantTaskExporter.RECALL_INDEX] = recall
            line[RelevantTaskExporter.ITEST_VIEWS_SIZE_INDEX] = 0
            content += line
        }

        List<String[]> header = generateMainHeader()
        data += header
        data += content
        CsvUtil.write(controllerFile, data)
    }

}

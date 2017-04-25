package taskAnalyser.output

import au.com.bytecode.opencsv.CSVWriter
import evaluation.TaskInterfaceEvaluator
import util.ConstantData
import util.CsvUtil
import util.RegexUtil
import util.Util


class ControllerFilterExporter {

    String evaluationFile
    String controllerFile
    String controllerOrgFile
    List<String[]> entries

    ControllerFilterExporter(String evaluationFile){
        this.evaluationFile = evaluationFile
        this.controllerFile = evaluationFile - ConstantData.CSV_FILE_EXTENSION + ConstantData.CONTROLLER_FILE_SUFIX
        this.controllerOrgFile = controllerFile - ConstantData.CONTROLLER_FILE_SUFIX + ConstantData.CONTROLLER_ORGANIZED_FILE_SUFIX
        entries = CsvUtil.read(evaluationFile)
    }

    private generateMainHeader() {
        List<String[]> content = []
        entries.subList(0, EvaluationExporter.INITIAL_TEXT_SIZE).each { data ->
            String[] value = data.findAll { !it.allWhitespace }
            content += value
        }
        content
    }

    def save() {
        if (!evaluationFile || evaluationFile.empty || !(new File(evaluationFile).exists())) return
        if (entries.size() <= EvaluationExporter.INITIAL_TEXT_SIZE) return

        List<String[]> content = []
        content += generateMainHeader()
        String[] resultHeader = entries.get(EvaluationExporter.INITIAL_TEXT_SIZE).findAll { !it.allWhitespace }
        content += resultHeader

        def entries = entries.subList(EvaluationExporter.INITIAL_TEXT_SIZE + 1, entries.size())
        entries?.each { entry ->
            def originalItest = entry[EvaluationExporter.ITEST_INDEX].replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"/")
                    .substring(1,entry[EvaluationExporter.ITEST_INDEX].size()-1)
                    .split(",")
                    .flatten()
                    .collect{ it.trim() } as Set
            def itest = originalItest.findAll { Util.isControllerFile(it) }
            def originalIReal = entry[EvaluationExporter.IREAL_INDEX].replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"/")
                    .substring(1,entry[EvaluationExporter.IREAL_INDEX].size()-1)
                    .split(",")
                    .flatten()
                    .collect{ it.trim() } as Set
            def ireal = originalIReal.findAll { Util.isControllerFile(it) }
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(itest, ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(itest, ireal)

            String[] line = entry
            line[EvaluationExporter.ITEST_INDEX-2] = itest.size()
            line[EvaluationExporter.ITEST_INDEX-1] = ireal.size()
            line[EvaluationExporter.ITEST_INDEX] = itest
            line[EvaluationExporter.IREAL_INDEX] = ireal
            line[EvaluationExporter.PRECISION_INDEX] = precision
            line[EvaluationExporter.RECALL_INDEX] = recall
            line[resultHeader.size()-3] = 0
            content += line
        }

        CsvUtil.write(controllerFile, content)

        EvaluationOrganizerExporter evaluationOrganizerExporter = new EvaluationOrganizerExporter(controllerFile, controllerOrgFile, null)
        evaluationOrganizerExporter.save()
    }

}

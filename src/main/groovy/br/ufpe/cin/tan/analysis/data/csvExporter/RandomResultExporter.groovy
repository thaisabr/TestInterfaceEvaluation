package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.util.CsvUtil

class RandomResultExporter {

    String filename
    String folder
    String url
    List<AnalysedTask> tasks

    RandomResultExporter(String filename, List<AnalysedTask> tasks) {
        this.filename = filename
        def index = filename.lastIndexOf(File.separator)
        folder = filename.substring(0, index)
        if (!tasks || tasks.empty) {
            this.tasks = []
            url = ""
        } else {
            this.tasks = tasks
            url = tasks.first().doneTask.gitRepository.url
        }
    }

    def save() {
        if (!tasks || tasks.empty) return
        List<String[]> content = []
        content += ["Repository", url] as String[]
        content += generateNumeralData()
        content += ["TASK", "#IRandom", "#IReal", "IRandom", "IReal", "PRECISION", "RECALL", "#IRandom-IReal",
                    "#IReal-IRandom", "IRandom-IReal", "IReal-IRandom", "#Hits", "Hits"] as String[]
        tasks?.each { content += it.parseRandomResultToArray() }
        CsvUtil.write(filename, content)
    }

    private generateNumeralData() {
        if (!tasks || tasks.empty) return []
        double[] precisionValues = tasks.collect { it.randomPrecision() }
        double[] recallValues = tasks.collect { it.randomRecall() }
        ExporterUtil.generateStatistics(precisionValues, recallValues)
    }

}

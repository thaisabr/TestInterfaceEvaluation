package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util

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

        def measure1, measure2
        if (Util.SIMILARITY_ANALYSIS) {
            measure1 = "Jaccard"
            measure2 = "Cosine"
        } else {
            measure1 = "Precision"
            measure2 = "Recall"
        }

        content += ["TASK", "#IRandom", "#IReal", "IRandom", "IReal", measure1, measure2, "#FP",
                    "#FN", "FP", "FN", "#Hits", "Hits"] as String[]
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

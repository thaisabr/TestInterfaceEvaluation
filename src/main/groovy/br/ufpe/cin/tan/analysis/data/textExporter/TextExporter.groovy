package br.ufpe.cin.tan.analysis.data.textExporter

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.ConstantData

abstract class TextExporter {
    String folderName
    List<AnalysedTask> tasks
    String filePrefix

    TextExporter(String folderName, List<AnalysedTask> tasks) {
        this.folderName = folderName
        this.tasks = tasks
        configureFilePrefix()
    }

    def save() {
        tasks?.each { writeFile(it) }
    }

    abstract void writeFile(AnalysedTask task)

    private configureFilePrefix() {
        def folder = new File(folderName + File.separator + ConstantData.DEFAULT_TEXT_FOLDER)
        if (!folder.exists()) folder.mkdir()
        filePrefix = "${folder}${File.separator}"
    }

}

package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.ConstantData

class TestCodeExporter {

    String folderName
    List<AnalysedTask> tasks
    String filePrefix

    TestCodeExporter(String folderName, List<AnalysedTask> tasks){
        this.folderName = folderName
        this.tasks = tasks
        configureFilePrefix()
    }

    def save(){
        tasks?.each{ writeTestCodeFile(it) }
    }

    private configureFilePrefix(){
        def folder = new File(folderName + File.separator+ ConstantData.DEFAULT_TEXT_FOLDER)
        if(!folder.exists()) folder.mkdir()
        filePrefix = "${folder}${File.separator}"
    }

    private writeTestCodeFile(AnalysedTask analysedTask) {
        if(!analysedTask) return
        def name = "${filePrefix}${analysedTask.doneTask.id}_testcode${ConstantData.TEXT_EXTENSION}"
        if (analysedTask.itext && !analysedTask.itext.empty) {
            File file = new File(name)
            file.withWriter("utf-8") { out ->
                analysedTask.itest.code.each{ line ->
                    out.write(line + "\n")
                }
            }
        }
    }

}

package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.ConstantData


class ITextExporter {

    String folderName
    List<AnalysedTask> tasks
    String textFilePrefix

    ITextExporter(String folderName, List<AnalysedTask> tasks){
        this.folderName = folderName
        this.tasks = tasks
        configureTextFilePrefix()
    }

    private configureTextFilePrefix(){
        def folder = new File(folderName + File.separator+ ConstantData.DEFAULT_TEXT_FOLDER)
        if(!folder.exists()) folder.mkdir()
        textFilePrefix = "${folder}${File.separator}"
    }

    private writeITextFile(AnalysedTask analysedTask) {
        def name = "${textFilePrefix}${analysedTask.doneTask.id}${ConstantData.TEXT_EXTENSION}"
        if (analysedTask.itext && !analysedTask.itext.empty) {
            File file = new File(name)
            file.withWriter("utf-8") { out ->
                out.write(analysedTask.itext)
                out.write("\n-----------------------------------------------------------\n")
                analysedTask.trace.each{ out.write(it + "\n") }
            }
        }
    }

    def save(){
        tasks.each{
            writeITextFile(it)
        }
    }

}

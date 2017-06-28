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

    def save(){
        tasks?.each{ writeITextFile(it) }
    }

    private configureTextFilePrefix(){
        def folder = new File(folderName + File.separator+ ConstantData.DEFAULT_TEXT_FOLDER)
        if(!folder.exists()) folder.mkdir()
        textFilePrefix = "${folder}${File.separator}"
    }

    private writeITextFile(AnalysedTask analysedTask) {
        if(!analysedTask) return
        def name = "${textFilePrefix}${analysedTask.doneTask.id}${ConstantData.TEXT_EXTENSION}"
        if (analysedTask.itext && !analysedTask.itext.empty) {
            File file = new File(name)
            file.withWriter("utf-8") { out ->
                out.write(analysedTask.itext)
                writeTraceFile(analysedTask)
                writeITestDetailed(analysedTask)
            }
        }
    }

    private writeTraceFile(AnalysedTask analysedTask){
        if(!analysedTask) return
        def name = "${textFilePrefix}${analysedTask.doneTask.id}_trace${ConstantData.TEXT_EXTENSION}"
        if (analysedTask.itext && !analysedTask.itext.empty) {
            File file = new File(name)
            file.withWriter("utf-8") { out ->
                analysedTask.trace.each{ out.write(it.toString() + "\n") }
            }
        }
    }

    private writeITestDetailed(AnalysedTask analysedTask){
        if(!analysedTask) return
        def name = "${textFilePrefix}${analysedTask.doneTask.id}_detailed_itest${ConstantData.TEXT_EXTENSION}"
        if (analysedTask.itext && !analysedTask.itext.empty) {
            File file = new File(name)
            file.withWriter("utf-8") { out ->
                analysedTask.itest.each{ out.write(it.toStringDetailed() + "\n") }
            }
        }
    }

}

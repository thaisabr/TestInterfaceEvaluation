package br.ufpe.cin.tan.analysis.data.textExporter

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.ConstantData

class ITextExporter extends TextExporter {

    ITextExporter(String folderName, List<AnalysedTask> tasks){
        super(folderName, tasks)
    }

    @Override
    void writeFile(AnalysedTask analysedTask) {
        if(!analysedTask) return
        def name = "${filePrefix}${analysedTask.doneTask.id}${ConstantData.TEXT_EXTENSION}"
        if (analysedTask.itext && !analysedTask.itext.empty) {
            File file = new File(name)
            file.withWriter("utf-8") { out ->
                out.write(analysedTask.itext)
            }
        }
    }

}

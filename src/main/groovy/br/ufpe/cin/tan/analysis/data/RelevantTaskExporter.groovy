package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.CsvUtil

/**
 * Filter and export analysed tasks that contain acceptance test and no empty IReal.
 * According to the configuration.properties file, the usage of coverage gems (in case of Rails projects) is also considered.
 */
class RelevantTaskExporter {

    String filename
    String url
    List<AnalysedTask> tasks
    List<AnalysedTask> relevantTasks
    List<AnalysedTask> emptyITestTasks

    RelevantTaskExporter(String filename, List<AnalysedTask> tasks){
        this.filename = filename
        initTasks(tasks)
    }

    def saveText(List<AnalysedTask> tasks){
        def index = filename.lastIndexOf(File.separator)
        def folder = filename.substring(0,index)
        ITextExporter iTextExporter = new ITextExporter(folder, tasks)
        iTextExporter.save()
    }

    def save(){
        def tasksToSave = relevantTasks + emptyITestTasks
        if(!tasksToSave || tasksToSave.empty) return
        /*if (tasksToSave.size() > 1)*/ saveText(tasksToSave)
        List<String[]> content = []
        content += ["Repository", url] as String[]
        content += generateNumeralData()
        content += ExporterUtil.SHORT_HEADER
        tasksToSave?.each { content += it.parseToArray() }
        CsvUtil.write(filename, content)
    }

    private generateNumeralData(){
        def tasksToSave = relevantTasks + emptyITestTasks
        if(!tasksToSave || tasksToSave.empty) return []
        double[] precisionValues = tasksToSave.collect { it.precision() }
        double[] recallValues = tasksToSave.collect { it.recall() }
        ExporterUtil.generateStatistics(precisionValues, recallValues)
    }

    private initTasks(List<AnalysedTask> tasks){
        if(!tasks || tasks.empty) {
            this.tasks = []
            url = ""
        }
        else {
            this.tasks = tasks.findAll{ it.isValid() }
            url = tasks.first().doneTask.gitRepository.url
        }
        filterTasksByAcceptanceTests()
    }

    private filterTasksByAcceptanceTests(){
        relevantTasks = tasks.findAll{ it.itestFiles().size() > 0 }?.sort{ -it.itest.foundAcceptanceTests.size() }
        emptyITestTasks = (tasks - relevantTasks)?.sort{ -it.itest.foundAcceptanceTests.size() }
    }

}

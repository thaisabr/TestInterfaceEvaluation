package br.ufpe.cin.tan.analysis.data

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.CsvUtil

/**
 * Filter and export analysed tasks that contain acceptance test and no empty IReal.
 * According to the configuration.properties file, the usage of coverage gems (in case of Rails projects) is also considered.
 */
class RelevantTaskExporter {

    static String[] HEADER = ["TASK", "#DAYS", "#DEVS", "#COMMITS", "HASHES", "#GHERKIN_TESTS", "#ITest", "#IReal",
                              "ITest", "IReal", "PRECISION", "RECALL", "RAILS", "#visit_call", "#ITest_views",
                              "#view_analysis_code", "view_analysis_code", "methods_no_origin", "renamed_files",
                              "deleted_files", "noFound_views", "#noFound_views", "TIMESTAMP"]

    public static final int RECALL_INDEX = HEADER.size() - 12
    public static final int PRECISION_INDEX = RECALL_INDEX - 1
    public static final int IREAL_INDEX = PRECISION_INDEX - 1
    public static final int ITEST_INDEX = IREAL_INDEX - 1
    public static final int ITEST_SIZE_INDEX = ITEST_INDEX - 2
    public static final int IREAL_SIZE_INDEX = IREAL_INDEX - 2
    public static final int INITIAL_TEXT_SIZE = 8
    public static final int ITEST_VIEWS_SIZE_INDEX = 15

    String filename
    String url
    List<AnalysedTask> tasks
    List<AnalysedTask> relevantTasks
    List<AnalysedTask> emptyITestTasks

    RelevantTaskExporter(String filename, List<AnalysedTask> tasks){
        this.filename = filename
        initTasks(tasks)
    }

    private generateNumeralData(){
        def tasksToSave = relevantTasks + emptyITestTasks
        if(!tasksToSave || tasksToSave.empty) return

        List<String[]> content = []
        double[] precisionValues = tasksToSave.collect { it.precision() }
        def itestStatistics = new DescriptiveStatistics(precisionValues)
        double[] recallValues = tasksToSave.collect { it.recall() }
        def irealStatistics = new DescriptiveStatistics(recallValues)
        content += ["Precision mean (RT)", itestStatistics.mean] as String[]
        content += ["Precision median (RT)", itestStatistics.getPercentile(50)] as String[]
        content += ["Precision standard deviation (RT)", itestStatistics.standardDeviation] as String[]
        content += ["Recall mean (RT)", irealStatistics.mean] as String[]
        content += ["Recall median (RT)", irealStatistics.getPercentile(50)] as String[]
        content += ["Recall standard deviation (RT)", irealStatistics.standardDeviation] as String[]
        content
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

    def save(){
        def tasksToSave = relevantTasks + emptyITestTasks
        if(!tasksToSave || tasksToSave.empty) return

        def saveText = false
        if (tasksToSave.size() > 1) saveText = true

        List<String[]> content = []
        content += ["Repository", url] as String[]
        content += generateNumeralData()
        content += HEADER

        tasksToSave?.each { task ->
            def gherkinTestsSize = task.itest.foundAcceptanceTests.size()
            def itest = task.itestFiles()
            def itestSize = itest.size()
            def ireal = task.irealFiles()
            def irealSize = ireal.size()
            def precision = task.precision()
            def recall = task.recall()
            def devs = task.developers
            def renames = task.renamedFiles
            def removes = task.removedFiles
            if (renames.empty) renames = ""
            def views = task.notFoundViews()
            if (views.empty) views = ""
            def filesFromViewAnalysis = task.filesFromViewAnalysis()
            def viewFileFromITest = task.itestViewFiles().size()
            String[] line = [task.doneTask.id, task.doneTask.days, devs, task.doneTask.commitsQuantity,
                             task.doneTask.hashes, gherkinTestsSize, itestSize, irealSize,
                             itest, ireal, precision, recall, task.rails, task.itest.visitCallCounter, viewFileFromITest,
                             filesFromViewAnalysis.size(), filesFromViewAnalysis, task.methods, renames, removes, views, views.size(),
                             task.itest.timestamp]

            content += line
        }

        CsvUtil.write(filename, content)

        if (saveText) {
            def index = filename.lastIndexOf(File.separator)
            def folder = filename.substring(0,index)
            ITextExporter iTextExporter = new ITextExporter(folder, tasks)
            iTextExporter.save()
        }
    }
}

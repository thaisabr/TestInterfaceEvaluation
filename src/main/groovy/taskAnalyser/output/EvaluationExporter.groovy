package taskAnalyser.output

import taskAnalyser.task.AnalysedTask
import taskAnalyser.task.AnalysisResult
import util.ConstantData
import util.CsvUtil

class EvaluationExporter {

    File file
    AnalysisResult analysisResult
    String textFilePrefix

    public static String[] HEADER = ["Task", "Date", "#Days", "#Commits", "Commit_Message", "#Devs", "#Gherkin_Tests",
                              "#Impl_Gherkin_Tests", "#StepDef", "Methods_Unknown_Type", "#Step_Call", "Step_Match_Errors",
                              "#Step_Match_Error", "AST_Errors", "#AST_Errors", "Gherkin_AST_Errors", "#Gherkin_AST_Errors",
                              "Steps_AST_Errors", "#Steps_AST_Errors", "Renamed_Files", "Deleted_Files", "NotFound_Views",
                              "#Views", "#ITest", "#IReal", "ITest", "IReal", "Precision", "Recall", "Hashes", "Timestamp",
                              "Rails", "Gems", "#Visit_Call", "#Views_ITest", "#Code_View_Analysis", "Code_View_Analysis"]

    public static final int RECALL_INDEX = HEADER.size() - 9
    public static final int PRECISION_INDEX = RECALL_INDEX - 1
    public static final int IREAL_INDEX = PRECISION_INDEX - 1
    public static final int ITEST_INDEX = IREAL_INDEX - 1
    public static final int ITEST_SIZE_INDEX = ITEST_INDEX - 2
    public static final int IREAL_SIZE_INDEX = IREAL_INDEX - 2
    public static final int STEP_MATCH_ERROR_INDEX = 12
    public static final int AST_ERROR_INDEX = 14
    public static final int INITIAL_TEXT_SIZE = 6
    public static final int GHERKIN_TEST_INDEX = 7
    public static final int STEP_DEF_INDEX = GHERKIN_TEST_INDEX + 1


    EvaluationExporter(String evaluationFile, AnalysisResult analysisResult){
        this.file = new File(evaluationFile)
        this.analysisResult = analysisResult
        configureTextFilePrefix()
    }

    private generateHeader() {
        List<String[]> header = []
        String[] text = ["Repository", analysisResult.url]
        header += text
        text = ["Tasks", analysisResult.allTasks]
        header += text
        text = ["P&T code", analysisResult.validTasks.size()]
        header += text
        text = ["Changed stepdef", analysisResult.stepCounter]
        header += text
        text = ["Changed Gherkin", analysisResult.gherkinCounter]
        header += text
        text = ["Have test", analysisResult.testsCounter]
        header += text
        header += HEADER
        header
    }

    private configureTextFilePrefix(){
        def folder = new File(ConstantData.DEFAULT_TEXT_FOLDER)
        if(!folder.exists()) folder.mkdir()
        def i = file.path.lastIndexOf(File.separator)
        def name = file.path.substring(i+1, file.path.size()) - ConstantData.CSV_FILE_EXTENSION
        textFilePrefix = "${ConstantData.DEFAULT_TEXT_FOLDER}${File.separator}${name}_text_"
    }

    private writeITextFile(AnalysedTask analysedTask) {
        def name = "${textFilePrefix}${analysedTask.doneTask.id}.txt"
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
        List<String[]> content = []
        content += generateHeader()

        def saveText = false
        if (analysisResult.validTasks && analysisResult.validTasks.size() > 1) saveText = true

        analysisResult.validTasks?.each { task ->
            def itestFiles = task.itestFiles()
            def itestSize = itestFiles.size()
            def irealFiles = task.irealFiles()
            def irealSize = irealFiles.size()
            def precision = task.precision()
            def recall = task.recall()
            def dates = task.dates
            def devs = task.developers
            def msgs = task.commitMsg
            def renames = task.renamedFiles
            def removes = task.removedFiles
            if (renames.empty) renames = ""
            def views = task.notFoundViews()
            if (views.empty) views = ""
            def filesFromViewAnalysis = task.filesFromViewAnalysis()
            def viewFileFromITest = task.itestViewFiles().size()
            def rails = ""
            def gems = []
            if(task.gems.size()>0) {
                rails = task.gems.first().replaceAll(/[^\.\d]/,"")
                gems = task.gems.subList(1, task.gems.size())
            }
            String[] line = [task.doneTask.id, dates, task.doneTask.days,
                             task.doneTask.commitsQuantity, msgs, devs,
                             task.doneTask.gherkinTestQuantity, task.itest.foundAcceptanceTests.size(),
                             task.doneTask.stepDefQuantity, task.methods, task.stepCalls,
                             task.stepMatchErrorsText, task.stepMatchErrors, task.compilationErrorsText,
                             task.compilationErrors, task.gherkinCompilationErrorsText,
                             task.gherkinCompilationErrors, task.stepDefCompilationErrorsText,
                             task.stepDefCompilationErrors, renames, removes, views, views.size(), itestSize,
                             irealSize, itestFiles, irealFiles, precision, recall, task.doneTask.hashes,
                             task.itest.timestamp, rails, gems, task.itest.visitCallCounter, viewFileFromITest,
                             filesFromViewAnalysis.size(), filesFromViewAnalysis]

            content += line
            if (saveText) writeITextFile(task) //dealing with long textual description of a task
        }

        CsvUtil.write(file.path, content)
    }
}

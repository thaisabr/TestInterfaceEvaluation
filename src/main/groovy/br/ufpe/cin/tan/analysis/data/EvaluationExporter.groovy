package br.ufpe.cin.tan.analysis.data

import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.CsvUtil

@Slf4j
class EvaluationExporter {

    File file
    List<AnalysedTask> tasks
    String url
    int stepCounter
    int gherkinCounter
    List<AnalysedTask> emptyIReal
    List<AnalysedTask> hasGherkinTest
    List<AnalysedTask> stepMatchError
    List<AnalysedTask> gherkinCompilationErrors
    List<AnalysedTask> compilationErrors
    List<AnalysedTask> stepDefCompilationErrors
    List<AnalysedTask> invalidTasks
    List<AnalysedTask> validTasks
    List<AnalysedTask> emptyITest
    List<AnalysedTask> zeroPrecisionAndRecall
    List<AnalysedTask> others
    List<String[]> initialData

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
    public static final int GHERKIN_TEST_INDEX = 7
    public static final int STEP_DEF_INDEX = GHERKIN_TEST_INDEX + 1
    public static final int INITIAL_TEXT_SIZE = 15


    EvaluationExporter(String evaluationFile, List<AnalysedTask> tasks){
        this.file = new File(evaluationFile)
        this.tasks = tasks
        if(tasks && !tasks.empty) url = tasks.first().doneTask.gitRepository.url
        else url = ""
        filter()
        generateHeader()
    }

    private filter() {
        emptyIReal = tasks.findAll{ it.irealFiles().empty }
        tasks -= emptyIReal
        stepCounter = tasks.findAll{ !it.doneTask.changedStepDefinitions.empty }.size()
        gherkinCounter = tasks.findAll{ !it.doneTask.changedGherkinFiles.empty }.size()
        hasGherkinTest = tasks.findAll{ !it.itest.foundAcceptanceTests.empty }
        stepMatchError = tasks.findAll{ it.stepMatchErrors>0 }
        compilationErrors = tasks.findAll{ it.compilationErrors>0 }
        gherkinCompilationErrors = tasks.findAll{ it.gherkinCompilationErrors>0 }
        stepDefCompilationErrors = tasks.findAll{ it.stepDefCompilationErrors>0 }
        invalidTasks = ((tasks - hasGherkinTest) + stepMatchError + compilationErrors).unique()
        validTasks = tasks - invalidTasks
        emptyITest = validTasks.findAll{ it.itestFiles().empty }
        def noEmptyITest = validTasks - emptyITest
        zeroPrecisionAndRecall = noEmptyITest.findAll{ it.precision()==0 && it.recall()==0 }
        others = noEmptyITest - zeroPrecisionAndRecall
    }

    private generateHeader() {
        initialData = []
        initialData += ["Repository", url] as String[]
        initialData += ["No-empty IReal", tasks.size()] as String[]
        initialData += ["Compilation errors", compilationErrors.size()] as String[]
        initialData += ["Compilation errors of Gherkin files", gherkinCompilationErrors.size()] as String[]
        initialData += ["Compilation errors of StepDef files", stepDefCompilationErrors.size()] as String[]
        initialData += ["Step match error", stepMatchError.size()] as String[]
        initialData += ["Changed stepdef", stepCounter] as String[]
        initialData += ["Changed Gherkin", gherkinCounter] as String[]
        initialData += ["Implemented Gherkin scenarios", hasGherkinTest.size()] as String[]
        initialData += ["All invalid tasks", invalidTasks.size()] as String[]
        initialData += ["All valid (Gherkin scenario, no empty IReal, no error)", validTasks.size()] as String[]
        initialData += ["Valid, but empty ITest", emptyITest.size()] as String[]
        initialData += ["Valid, no empty ITest, but zero precision-recall", zeroPrecisionAndRecall.size()] as String[]
        initialData += ["Valid, no empty ITest, no zero precision-recall", others.size()] as String[]
        initialData += HEADER
    }

    def save(){
        if(!tasks || tasks.empty) return
        List<String[]> content = initialData
        def saveText = false

        tasks?.each { task ->
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
        }

        CsvUtil.write(file.path, content)
    }
}

package br.ufpe.cin.tan.analysis.data

import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.analysis.AnalysedTask
import br.ufpe.cin.tan.util.CsvUtil

@Slf4j
class EvaluationExporter {

    File file
    String url

    List<AnalysedTask> tasks
    List<AnalysedTask> emptyIReal
    List<AnalysedTask> hasGherkinTest
    List<AnalysedTask> stepMatchError
    List<AnalysedTask> gherkinCompilationErrors
    List<AnalysedTask> compilationErrors
    List<AnalysedTask> stepDefCompilationErrors
    List<AnalysedTask> unitCompilationErrors
    List<AnalysedTask> invalidTasks
    List<AnalysedTask> validTasks
    List<AnalysedTask> emptyITest
    List<AnalysedTask> zeroPrecisionAndRecall
    List<AnalysedTask> others
    List<String[]> initialData

    boolean filterEmptyIReal

    int stepCounter
    int gherkinCounter

    String[] MAIN_HEADER = ["Task", "Date", "#Days", "#Commits", "Commit_Message", "#Devs", "#Gherkin_Tests",
                            "#Impl_Gherkin_Tests", "#StepDef", "Methods_Unknown_Type", "#Step_Call", "Step_Match_Errors",
                            "#Step_Match_Error", "AST_Errors", "#AST_Errors", "Gherkin_AST_Errors", "#Gherkin_AST_Errors",
                            "Steps_AST_Errors", "#Steps_AST_Errors", "Renamed_Files", "Deleted_Files", "NotFound_Views",
                            "#Views", "#ITest", "#IReal", "ITest", "IReal", "Precision", "Recall", "Hashes", "Timestamp",
                            "Rails", "Gems", "#Visit_Call", "Lost_visit_call", "#Views_ITest", "#Code_View_Analysis",
                            "Code_View_Analysis", "Has_Merge"]

    EvaluationExporter(String evaluationFile, List<AnalysedTask> tasks){
        this(evaluationFile, tasks, true)
    }

    EvaluationExporter(String evaluationFile, List<AnalysedTask> tasks, boolean toFilter){
        file = new File(evaluationFile)
        this.tasks = tasks
        filterEmptyIReal = toFilter
        if(tasks && !tasks.empty) url = tasks.first().doneTask.gitRepository.url
        else url = ""
        initializeValues()
        generateSummaryData()
    }

    def save(){
        if(!tasks || tasks.empty) return
        List<String[]> content = initialData
        tasks?.each { content += it.parseAllToArray() }
        CsvUtil.write(file.path, content)
    }

    private initializeValues() {
        validTasks = tasks.findAll{ it.isValid() }

        def invalid = tasks - validTasks
        emptyIReal = invalid.findAll{ it.irealIsEmpty() }
        if(filterEmptyIReal) tasks -= emptyIReal

        stepCounter = tasks.findAll{ it.hasChangedStepDefs() }.size()
        gherkinCounter = tasks.findAll{ it.hasChangedGherkinDefs()}.size()
        hasGherkinTest = tasks.findAll{ it.hasImplementedAcceptanceTests() }
        stepMatchError = tasks.findAll{ it.hasStepMatchError() }
        compilationErrors = tasks.findAll{ it.hasCompilationError() }
        gherkinCompilationErrors = tasks.findAll{ it.hasGherkinCompilationError() }
        stepDefCompilationErrors = tasks.findAll{ it.hasStepDefCompilationError() }
        unitCompilationErrors = tasks.findAll { it.hasUnitCompilationError() }

        if(filterEmptyIReal) invalidTasks = invalid
        else invalidTasks = (invalid + emptyIReal).unique()
        emptyITest = validTasks.findAll{ it.itestIsEmpty() }
        def noEmptyITest = validTasks - emptyITest
        int zero = 0
        zeroPrecisionAndRecall = noEmptyITest.findAll{ it.precision()==zero && it.recall()==zero }
        others = noEmptyITest - zeroPrecisionAndRecall
    }

    private generateSummaryData() {
        initialData = []
        initialData += ["Repository", url] as String[]
        initialData += ["Tasks with empty IReal", emptyIReal.size()] as String[]
        if(filterEmptyIReal) initialData += ["Tasks with no-empty IReal", tasks.size()] as String[]
        else initialData += ["Tasks with no-empty IReal", (tasks-emptyIReal).size()] as String[]
        initialData += ["Tasks with relevant AST error", compilationErrors.size()] as String[]
        initialData += ["Tasks with AST error of Gherkin files", gherkinCompilationErrors.size()] as String[]
        initialData += ["Tasks with AST error of StepDef files", stepDefCompilationErrors.size()] as String[]
        def productionErrors = compilationErrors.size() - (gherkinCompilationErrors.size() + stepDefCompilationErrors.size())
        initialData += ["Tasks with AST error of production files", productionErrors] as String[]
        initialData += ["Tasks with AST error of unit test files", unitCompilationErrors.size()] as String[]
        initialData += ["Tasks with step match error", stepMatchError.size()] as String[]
        initialData += ["Tasks with changed stepdef", stepCounter] as String[]
        initialData += ["Tasks with changed Gherkin", gherkinCounter] as String[]
        initialData += ["Tasks with implemented Gherkin scenarios", hasGherkinTest.size()] as String[]
        initialData += ["All invalid tasks", invalidTasks.size()] as String[]
        initialData += ["All valid tasks (Gherkin scenario, no empty IReal, no error)", validTasks.size()] as String[]
        initialData += ["Valid tasks, but empty ITest", emptyITest.size()] as String[]
        initialData += ["Valid tasks, no empty ITest, but zero precision-recall", zeroPrecisionAndRecall.size()] as String[]
        initialData += ["Valid tasks, no empty ITest, no zero precision-recall", others.size()] as String[]
        initialData += MAIN_HEADER
    }

}

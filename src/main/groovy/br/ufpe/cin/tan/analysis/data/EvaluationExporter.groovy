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
                            "Rails", "Gems", "#Visit_Call", "#Views_ITest", "#Code_View_Analysis", "Code_View_Analysis"]

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
        emptyIReal = tasks.findAll{ it.irealFiles().empty }
        if(filterEmptyIReal) tasks -= emptyIReal
        stepCounter = tasks.findAll{ !it.doneTask.changedStepDefinitions.empty }.size()
        gherkinCounter = tasks.findAll{ !it.doneTask.changedGherkinFiles.empty }.size()
        hasGherkinTest = tasks.findAll{ !it.itest.foundAcceptanceTests.empty }
        stepMatchError = tasks.findAll{ it.stepMatchErrors>0 }
        compilationErrors = tasks.findAll{ it.compilationErrors>0 }
        gherkinCompilationErrors = tasks.findAll{ it.gherkinCompilationErrors>0 }
        stepDefCompilationErrors = tasks.findAll{ it.stepDefCompilationErrors>0 }
        def invalid = ((tasks - hasGherkinTest) + stepMatchError + compilationErrors).unique()
        if(filterEmptyIReal) invalidTasks = invalid
        else invalidTasks = (invalid + emptyIReal).unique()
        validTasks = tasks - invalidTasks
        emptyITest = validTasks.findAll{ it.itestFiles().empty }
        def noEmptyITest = validTasks - emptyITest
        zeroPrecisionAndRecall = noEmptyITest.findAll{ it.precision()==0 && it.recall()==0 }
        others = noEmptyITest - zeroPrecisionAndRecall
    }

    private generateSummaryData() {
        initialData = []
        initialData += ["Repository", url] as String[]
        initialData += ["Empty IReal", emptyIReal.size()] as String[]
        if(filterEmptyIReal) initialData += ["No-empty IReal", tasks.size()] as String[]
        else initialData += ["No-empty IReal", (tasks-emptyIReal).size()] as String[]
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
        initialData += MAIN_HEADER
    }

}

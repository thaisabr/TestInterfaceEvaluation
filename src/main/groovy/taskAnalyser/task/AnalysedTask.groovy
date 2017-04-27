package taskAnalyser.task

import evaluation.TaskInterfaceEvaluator
import util.Util

class AnalysedTask {

    DoneTask doneTask
    TaskInterface itest
    TaskInterface ireal
    List<String> methods
    int stepCalls
    String itext
    Set<String> trace
    String stepMatchErrorsText
    int stepMatchErrors
    String compilationErrorsText
    int compilationErrors
    String gherkinCompilationErrorsText
    int gherkinCompilationErrors
    String stepDefCompilationErrorsText
    int stepDefCompilationErrors
    List<String> gems

    AnalysedTask(DoneTask doneTask){
        this.doneTask = doneTask
        this.itest = new TaskInterface()
        this.ireal = new TaskInterface()
        this.itext = ""
        this.gems = []
    }

    private void extractStepMatchErrors() {
        def stepErrors = itest.matchStepErrors
        def stepErrorsQuantity = 0
        def text = ""
        if (stepErrors.empty) text = ""
        else {
            stepErrorsQuantity = stepErrors*.size.flatten().sum()
            stepErrors.each{ error ->
                text += "[path:${error.path}, size:${error.size}], "
            }
            text = text.substring(0, text.size()-2)
        }
        this.stepMatchErrorsText = text
        this.stepMatchErrors = stepErrorsQuantity
    }

    private void extractCompilationErrors() {
        def compilationErrors = itest.compilationErrors
        def compErrorsQuantity = 0
        def gherkinQuantity = 0
        def stepsQuantity = 0
        def gherkin = ""
        def steps = ""
        if (compilationErrors.empty) compilationErrors = ""
        else {
            compErrorsQuantity = compilationErrors*.msgs.flatten().size()
            gherkin = compilationErrors.findAll{ Util.isGherkinFile(it.path) }
            gherkinQuantity = gherkin.size()
            if(gherkin.empty) gherkin = ""
            steps = compilationErrors.findAll{ Util.isStepDefinitionFile(it.path) }
            stepsQuantity = steps.size()
            if(steps.empty) steps = ""
            compilationErrors = compilationErrors.toString()
        }

        this.compilationErrorsText = compilationErrors
        this.compilationErrors = compErrorsQuantity
        this.gherkinCompilationErrorsText = gherkin
        this.gherkinCompilationErrors = gherkinQuantity
        this.stepDefCompilationErrorsText = steps
        this.stepDefCompilationErrors = stepsQuantity
    }

    void setItest(TaskInterface itest){
        this.itest = itest
        this.stepCalls = itest?.methods?.findAll { it.type == "StepCall" }?.unique()?.size()
        this.methods = itest?.methods?.findAll { it.type == "Object" }?.unique()*.name
        this.trace = itest?.findAllFiles()
        this.extractStepMatchErrors()
        this.extractCompilationErrors()
    }

    int getDevelopers(){
        doneTask?.developers
    }

    def getRenamedFiles(){
        doneTask.renamedFiles
    }

    def irealFiles(){
        ireal.findFilteredFiles()
    }

    def itestFiles(){
        itest.findFilteredFiles()
    }

    def itestViewFiles() {
        itestFiles().findAll{ Util.isViewFile(it) }
    }

    def filesFromViewAnalysis(){
        itest.codeFromViewAnalysis
    }

    double precision(){
        TaskInterfaceEvaluator.calculateFilesPrecision(itest, ireal)
    }

    double recall(){
        TaskInterfaceEvaluator.calculateFilesRecall(itest, ireal)
    }

    def getDates(){
        doneTask.dates
    }

    def getCommitMsg() {
        doneTask.commitMessage
    }

    def getRemovedFiles() {
        doneTask.removedFiles
    }

    def notFoundViews(){
        itest.notFoundViews
    }

}

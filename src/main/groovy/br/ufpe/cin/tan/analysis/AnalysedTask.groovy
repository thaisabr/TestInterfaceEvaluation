package br.ufpe.cin.tan.analysis

import br.ufpe.cin.tan.analysis.itask.IReal
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.analysis.task.DoneTask
import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.util.Util

class AnalysedTask {

    DoneTask doneTask
    ITest itest
    IReal ireal
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
        this.itest = new ITest()
        this.ireal = new IReal()
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

    void setItest(ITest itest){
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

    def satisfiesGemsFilter(){
        if(Util.COVERAGE_GEMS.empty) true
        else {
            if(Util.COVERAGE_GEMS.intersect(gems).size() > 0) true
            else false
        }
    }

    def hasImplementedAcceptanceTests(){
        if(itest.foundAcceptanceTests.size()>0) true
        else false
    }

    def isValid(){
        compilationErrors==0 && stepMatchErrors==0 && satisfiesGemsFilter() && hasImplementedAcceptanceTests() && !irealFiles().empty
    }

}

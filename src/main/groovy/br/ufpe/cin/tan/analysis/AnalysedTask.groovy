package br.ufpe.cin.tan.analysis

import br.ufpe.cin.tan.analysis.itask.IReal
import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.analysis.task.DoneTask
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.similarity.test.TestSimilarityAnalyser
import br.ufpe.cin.tan.test.AcceptanceTest
import br.ufpe.cin.tan.test.TestCodeAbstractAnalyser
import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.util.ruby.RubyUtil
import groovy.util.logging.Slf4j

@Slf4j
class AnalysedTask {

    DoneTask doneTask
    ITest itest
    IReal ireal
    List<String> methods
    int stepCalls
    String itext
    String stepMatchErrorsText
    int stepMatchErrors
    String compilationErrorsText
    int compilationErrors
    String gherkinCompilationErrorsText
    int gherkinCompilationErrors
    String stepDefCompilationErrorsText
    int stepDefCompilationErrors
    String unitCompilationErrorsText
    int unitCompilationErrors
    List<String> gems
    List<String> coverageGems
    String rails
    String ruby

    AnalysedTask(DoneTask doneTask) {
        this.doneTask = doneTask
        this.itest = new ITest()
        this.ireal = new IReal()
        this.itext = ""
        this.gems = []
        this.coverageGems = []
        this.rails = ""
        this.ruby = ""
    }

    void setItest(ITest itest) {
        this.itest = itest
        this.stepCalls = itest?.methods?.findAll { it.type == "StepCall" }?.unique()?.size()
        this.methods = itest?.methods?.findAll { it.type == "Object" }?.unique()*.name
        this.extractStepMatchErrorText()
        this.extractCompilationErrorText()
    }

    Set getTrace() {
        itest.trace
    }

    boolean isRelevant() {
        if (itestIsEmpty()) false
        else true
    }

    int getDevelopers() {
        doneTask?.developers
    }

    def getRenamedFiles() {
        doneTask.renamedFiles
    }

    def hasChangedStepDefs() {
        !doneTask.changedStepDefinitions.empty
    }

    def hasStepMatchError() {
        if (stepMatchErrors > 0) true
        else false
    }

    def hasCompilationError() {
        if (compilationErrors > 0) true
        else false
    }

    def hasGherkinCompilationError() {
        if (gherkinCompilationErrors > 0) true
        else false
    }

    def hasStepDefCompilationError() {
        if (stepDefCompilationErrors > 0) true
        else false
    }

    def hasUnitCompilationError() {
        if (unitCompilationErrors > 0) true
        else false
    }

    def hasChangedGherkinDefs() {
        !doneTask.changedGherkinFiles.empty
    }

    def hasMergeCommit() {
        doneTask.hasMergeCommit()
    }

    def irealFiles() {
        ireal.findFilteredFiles()
    }

    def irealHasControllers() {
        def controllers = irealFiles().findAll { Util.isControllerFile(it) }
        !controllers.empty
    }

    def irealIsEmpty() {
        ireal.findFilteredFiles().empty
    }

    def itestFiles() {
        itest.findFilteredFiles()
    }

    def itestHasControllers() {
        def controllers = itestFiles().findAll { Util.isControllerFile(it) }
        !controllers.empty
    }

    def itestIsEmpty() {
        itestFiles().empty
    }

    def itestViewFiles() {
        itestFiles().findAll { Util.isViewFile(it) }
    }

    def filesFromViewAnalysis() {
        itest.codeFromViewAnalysis
    }

    double precision() {
        if (Util.SIMILARITY_ANALYSIS) {
            def similarityAnalyser = new TestSimilarityAnalyser(itest.findFilteredFiles(), ireal.findFilteredFiles())
            similarityAnalyser.calculateSimilarityByJaccard()
        } else {
            TaskInterfaceEvaluator.calculateFilesPrecision(itest, ireal)
        }
    }

    double recall() {
        if (Util.SIMILARITY_ANALYSIS) {
            def similarityAnalyser = new TestSimilarityAnalyser(itest.findFilteredFiles(), ireal.findFilteredFiles())
            similarityAnalyser.calculateSimilarityByCosine()
        } else {
            TaskInterfaceEvaluator.calculateFilesRecall(itest, ireal)
        }
    }

    double f2Measure() {
        def p = precision()
        def r = recall()
        def denominator = 4 * p + r
        if (denominator == 0) 0
        else 5 * ((p * r) / denominator)
    }

    def getDates() {
        doneTask.dates
    }

    def getCommitMsg() {
        doneTask.commitMessage
    }

    def getRemovedFiles() {
        doneTask.removedFiles
    }

    def notFoundViews() {
        itest.notFoundViews
    }

    def satisfiesGemsFilter() {
        if (Util.COVERAGE_GEMS.empty) true
        else {
            if (Util.COVERAGE_GEMS.intersect(gems).size() > 0) true
            else false
        }
    }

    Set<AcceptanceTest> getAcceptanceTests() {
        itest.foundAcceptanceTests
    }

    def hasImplementedAcceptanceTests() {
        if (itest.foundAcceptanceTests.size() > 0) true
        else false
    }

    def isValid() {
        int zero = 0
        compilationErrors == zero && stepMatchErrors == zero && satisfiesGemsFilter() && hasImplementedAcceptanceTests() &&
                !irealFiles().empty
    }

    def configureGems(String path) {
        def result = RubyUtil.checkRailsVersionAndGems(path) //[rails, ruby, gems]
        gems = result.gems
        rails = result.rails
        ruby = result.ruby
        if (gems.size() > 0) {
            coverageGems = gems.findAll { it == "coveralls" || it == "simplecov" }
        }
    }

    /**
     * Represents an analysed task as an array in order to export content to CSV files.
     * Task information is organized as follows: id, dates, #days, #commits, commit message, #developers,
     * #(gherkin tests), #(implemented gherkin tests), #(stepdefs), #(implemented stepdefs), unknown methods,
     * #(step calls), step match errors, #(step match errors), AST errors, #(AST errors), gherkin AST errors,
     * #(gherkin AST errors), step AST errors, #(step AST errors), renamed files, deleted files, not found views,
     * #views, #ITest, #IReal, ITest, IReal, precision, recall, hashes, timestamp, rails version, gems,
     * #(calls to visit), #(views in ITest), #(files accessed by view analysis), files accessed by view analysis.
     * Complete version with 38 fields.
     * */
    def parseAllToArray() {
        def itestFiles = this.itestFiles()
        def itestSize = itestFiles.size()
        def irealFiles = this.irealFiles()
        def irealSize = irealFiles.size()
        def renames = renamedFiles
        if (renames.empty) renames = ""
        def views = notFoundViews()
        if (views.empty) views = ""
        def filesFromViewAnalysis = filesFromViewAnalysis()
        def viewFileFromITest = itestViewFiles().size()
        String[] array = [doneTask.id, dates, doneTask.days, doneTask.commitsQuantity, commitMsg, developers,
                          doneTask.gherkinTestQuantity, itest.foundAcceptanceTests.size(), doneTask.stepDefQuantity,
                          itest.foundStepDefs.size(), configureUnknownMethods(), stepCalls, stepMatchErrorsText, stepMatchErrors,
                          compilationErrorsText, compilationErrors, gherkinCompilationErrorsText, gherkinCompilationErrors,
                          stepDefCompilationErrorsText, stepDefCompilationErrors, renames, removedFiles, views,
                          views.size(), itestSize, irealSize, itestFiles, irealFiles, precision(), recall(),
                          doneTask.hashes, itest.timestamp, rails, gems, itest.visitCallCounter, itest.lostVisitCall,
                          viewFileFromITest, filesFromViewAnalysis.size(), filesFromViewAnalysis, hasMergeCommit(), f2Measure()]
        array
    }

    /**
     * Represents an analysed task as an array in order to export content to CSV files.
     * Task information is organized as follows: id, dates, dates, #developers, #commits, hashes,
     * #(implemented gherkin tests), #(implemented stepdefs), #ITest, #IReal, ITest, IReal, precision, recall,
     * rails version, #(calls to visit), #(views in ITest), #(files accessed by view analysis), files accessed by view
     * analysis, unknown methods, renamed files, deleted files, views, #views, timestamp.
     * Partial version with 24 fields.
     * */
    def parseToArray() {
        def itestFiles = this.itestFiles()
        def itestSize = itestFiles.size()
        def irealFiles = this.irealFiles()
        def irealSize = irealFiles.size()
        def renames = renamedFiles
        if (renames.empty) renames = ""
        def views = notFoundViews()
        if (views.empty) views = ""
        def filesFromViewAnalysis = filesFromViewAnalysis()
        def viewFileFromITest = itestViewFiles().size()
        def falsePositives = itestFiles - irealFiles
        def falseNegatives = irealFiles - itestFiles
        def hits = itestFiles.intersect(irealFiles)
        String[] line = [doneTask.id, dates, developers, doneTask.commitsQuantity, doneTask.hashes,
                         itest.foundAcceptanceTests.size(), itest.foundStepDefs.size(), itestSize, irealSize, itestFiles,
                         irealFiles, precision(), recall(), rails, itest.visitCallCounter, itest.lostVisitCall,
                         viewFileFromITest, filesFromViewAnalysis.size(), filesFromViewAnalysis, configureUnknownMethods(), renames,
                         removedFiles, views, views.size(), itest.timestamp, hasMergeCommit(), falsePositives.size(),
                         falseNegatives.size(), falsePositives, falseNegatives, hits.size(), hits, f2Measure()]
        line
    }

    def parseCoverageGemsToString() {
        def coverage = ""
        if (!coverageGems.empty) coverage = coverageGems.join(",")
        coverage
    }

    private void extractStepMatchErrorText() {
        def stepErrors = itest.matchStepErrors
        def stepErrorsQuantity = 0
        def text = ""
        if (stepErrors.empty) text = ""
        else {
            stepErrorsQuantity = stepErrors*.size.flatten().sum()
            stepErrors.each { error ->
                text += "[path:${error.path}, size:${error.size}], "
            }
            text = text.substring(0, text.size() - 2)
        }
        this.stepMatchErrorsText = text
        this.stepMatchErrors = stepErrorsQuantity
    }

    private void extractCompilationErrorText() {
        def compilationErrors = itest.compilationErrors
        def compErrorsQuantity = 0
        def gherkinQuantity = 0
        def stepsQuantity = 0
        def unitQuantity = 0
        def gherkin = ""
        def steps = ""
        def unit = ""
        if (compilationErrors.empty) compilationErrors = ""
        else {
            gherkin = compilationErrors.findAll { Util.isGherkinFile(it.path) }
            gherkinQuantity = gherkin.size()
            if (gherkin.empty) gherkin = ""
            steps = compilationErrors.findAll { Util.isStepDefinitionFile(it.path) }
            stepsQuantity = steps.size()
            if (steps.empty) steps = ""
            unit = compilationErrors.findAll { Util.isUnitTestFile(it.path) }
            unitQuantity = unit.size()
            if (unit.empty) unit = ""
            compilationErrors -= unit
            compErrorsQuantity = compilationErrors*.msgs.flatten().size()
            compilationErrors = compilationErrors.toString()
        }

        this.compilationErrorsText = compilationErrors
        this.compilationErrors = compErrorsQuantity
        this.gherkinCompilationErrorsText = gherkin
        this.gherkinCompilationErrors = gherkinQuantity
        this.stepDefCompilationErrorsText = steps
        this.stepDefCompilationErrors = stepsQuantity
        this.unitCompilationErrorsText = unit
        this.unitCompilationErrors = unitQuantity
    }

    private configureUnknownMethods() {
        if (TestCodeAbstractAnalyser.apiMethods == null || TestCodeAbstractAnalyser.apiMethods.empty) return methods
        def unknownMethods = []
        methods.each { m ->
            def obj = TestCodeAbstractAnalyser.apiMethods.find { it.name == m }
            if (!obj) unknownMethods += m
        }
        return unknownMethods
    }

}

package taskAnalyser.output

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import util.CsvUtil

class EvaluationOrganizerExporter {

    String evaluationFile
    String organizedFile
    String similarityFile
    boolean similarityAnalysis

    String[] resultHeader1
    String[] resultHeader2

    int sizeNoEmptyIRealTasks
    List<String[]> entries
    List<String[]> stepMatchError
    List<String[]> astError
    List<String[]> basicError
    List<String[]> hasGherkinTest
    List<String[]> hasStepTest
    List<String[]> hasTests
    List<String[]> invalidTasks
    List<String[]> emptyITest
    List<String[]> relevantTasks
    List<String[]> zeroPrecisionAndRecall
    List<String[]> others
    List<String[]> tasksWithText

    EvaluationOrganizerExporter(String evalFile, String orgFile, String simFile) {
        this.evaluationFile = evalFile
        this.organizedFile = orgFile
        this.similarityFile = simFile
        if(simFile) similarityAnalysis = true
        else similarityAnalysis = false
        fillEntries()
    }

    private saveResultForSimilarityAnalysis() {
        List<String[]> content = []
        String[] header = ["Tasks (the ones that have Gherkin test)", tasksWithText.size()]
        content += header
        content += resultHeader1
        content += tasksWithText
        CsvUtil.write(similarityFile, content)
    }

    private configureMainHeader() {
        List<String[]> content = []
        entries.subList(0, EvaluationExporter.INITIAL_TEXT_SIZE).each { data ->
            String[] value = data.findAll { !it.allWhitespace }
            content += value
        }
        content
    }

    private static configureBooleanColumn(List<String[]> entries) {
        List<String[]> content = []
        entries.each { entry ->
            def itest = "no", ireal = "no"
            if (entry[EvaluationExporter.IREAL_SIZE_INDEX] == "0") ireal = "yes"
            if (entry[EvaluationExporter.ITEST_SIZE_INDEX] == "0") itest = "yes"
            String[] headers = entry + [itest, ireal]
            content += headers
        }
        content
    }

    private fillEntries(){
        entries = CsvUtil.read(evaluationFile)
        resultHeader1 = entries.get(EvaluationExporter.INITIAL_TEXT_SIZE).findAll { !it.allWhitespace }
        resultHeader2 = resultHeader1 + ["Empty_ITest", "Empty_IReal"]

        def entries = entries.subList(EvaluationExporter.INITIAL_TEXT_SIZE+1, entries.size())
        def emptyIReal = entries.findAll { it[EvaluationExporter.IREAL_SIZE_INDEX] == "0" }
        entries -= emptyIReal
        sizeNoEmptyIRealTasks = entries.size()
        stepMatchError = entries.findAll{ (it[EvaluationExporter.STEP_MATCH_ERROR_INDEX] as int) > 0 }
        astError = entries.findAll{ (it[EvaluationExporter.AST_ERROR_INDEX] as int) > 0 }
        basicError = (astError + stepMatchError).unique()
        entries -= basicError
        hasGherkinTest = entries.findAll { (it[EvaluationExporter.GHERKIN_TEST_INDEX] as int) > 0 }
        hasStepTest = entries.findAll { (it[EvaluationExporter.STEP_DEF_INDEX] as int) > 0 }
        hasTests = (hasGherkinTest + hasStepTest).unique()
        invalidTasks = entries - hasTests
        emptyITest = hasTests.findAll { it[EvaluationExporter.ITEST_SIZE_INDEX] == "0" }
        relevantTasks = hasTests - emptyITest
        zeroPrecisionAndRecall = relevantTasks.findAll {
            it[EvaluationExporter.PRECISION_INDEX] == "0.0" && it[EvaluationExporter.RECALL_INDEX] == "0.0"
        }
        others = relevantTasks - zeroPrecisionAndRecall
        tasksWithText = entries.findAll {
            !it[EvaluationExporter.IREAL_INDEX].empty && (it[EvaluationExporter.GHERKIN_TEST_INDEX] as int) > 0
        }
    }

    private generateContent() {
        List<String[]> content = []
        if (!evaluationFile || evaluationFile.empty || !(new File(evaluationFile).exists())) return
        if (entries.size() <= EvaluationExporter.INITIAL_TEXT_SIZE) return

        content += ["No-empty IReal", sizeNoEmptyIRealTasks] as String[]
        content += ["AST error", astError.size()] as String[]
        content += ["Step match error", stepMatchError.size()] as String[]
        content += ["Any error", basicError.size()] as String[]
        content += ["No tests", invalidTasks.size()] as String[]
        content += ["Valid (test, no empty IReal, no error)", hasTests.size()] as String[]
        content += ["Test & empty ITest", emptyITest.size()] as String[]
        content += ["All relevant (test & no-empty ITest)", relevantTasks.size()] as String[]
        content += ["Relevant & zero precision-recall", zeroPrecisionAndRecall.size()] as String[]

        def aux = entries.subList(EvaluationExporter.INITIAL_TEXT_SIZE+1, entries.size())
        if(aux.empty){
            content += ["Precision mean (RT)", ""] as String[]
            content += ["Precision median (RT)", ""] as String[]
            content += ["Precision standard deviation (RT)", ""] as String[]
            content += ["Recall mean (RT)", ""] as String[]
            content += ["Recall median (RT)", ""] as String[]
            content += ["Recall standard deviation (RT)", ""] as String[]
        } else {
            double[] precisionValues = relevantTasks.collect { it[EvaluationExporter.PRECISION_INDEX] as double }
            def itestStatistics = new DescriptiveStatistics(precisionValues)
            double[] recallValues = relevantTasks.collect { it[EvaluationExporter.RECALL_INDEX] as double }
            def irealStatistics = new DescriptiveStatistics(recallValues)
            content += ["Precision mean (RT)", itestStatistics.mean] as String[]
            content += ["Precision median (RT)", itestStatistics.getPercentile(50)] as String[]
            content += ["Precision standard deviation (RT)", itestStatistics.standardDeviation] as String[]
            content += ["Recall mean (RT)", irealStatistics.mean] as String[]
            content += ["Recall median (RT)", irealStatistics.getPercentile(50)] as String[]
            content += ["Recall standard deviation (RT)", irealStatistics.standardDeviation] as String[]
        }
        content
    }

    def save(){
        List<String[]> content = configureMainHeader()
        content += generateContent()
        content += resultHeader2
        content += configureBooleanColumn(basicError)
        content += configureBooleanColumn(invalidTasks)
        content += configureBooleanColumn(emptyITest)
        content += configureBooleanColumn(zeroPrecisionAndRecall)
        content += configureBooleanColumn(others)
        CsvUtil.write(organizedFile, content)
        if (similarityAnalysis) saveResultForSimilarityAnalysis()
    }
}

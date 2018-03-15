package br.ufpe.cin.tan.analysis.data.csvExporter

import br.ufpe.cin.tan.analysis.data.ExporterUtil
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.CsvUtil
import br.ufpe.cin.tan.util.Util

class ResultOrganizerExporter {

    def projectFolder
    def outputFolder
    def selectedTasks
    def validTasks

    def relevantSimilarityFiles
    def relevantTasksFiles
    def relevantTasksControllerFiles
    def relevantTasksDetailedFiles
    def testFiles
    def invalidTasksFiles
    def validSimilarityFiles
    def validTasksFiles
    def validTasksControllerFiles
    def validTasksDetailedFiles

    ResultOrganizerExporter(String folder, String taskFile, def selectedTasks) {
        File file = new File(taskFile)
        this.projectFolder = folder + File.separator + (file.name - ConstantData.CSV_FILE_EXTENSION)
        this.selectedTasks = selectedTasks
        this.outputFolder = ConstantData.SELECTED_TASKS_BY_CONFIGS_FOLDER
        def resultsFolder = Util.findFilesFromDirectory(projectFolder)

        /* INITIAL TEXT SIZE: 4 */
        relevantSimilarityFiles = resultsFolder.findAll {
            it.endsWith("-relevant" + ConstantData.SIMILARITY_FILE_SUFIX)
        }
        validSimilarityFiles = resultsFolder.findAll { it.endsWith("-valid" + ConstantData.SIMILARITY_FILE_SUFIX) }

        /* INITIAL TEXT SIZE: 4 */
        testFiles = resultsFolder.findAll { it.endsWith("-tests.csv") }

        /* INITIAL TEXT SIZE: 13 */
        relevantTasksFiles = resultsFolder.findAll { it.endsWith(ConstantData.RELEVANT_TASKS_FILE_SUFIX) }
        relevantTasksControllerFiles = resultsFolder.findAll {
            it.endsWith("-relevant" + ConstantData.CONTROLLER_FILE_SUFIX)
        }
        validTasksFiles = resultsFolder.findAll { it.endsWith(ConstantData.VALID_TASKS_FILE_SUFIX) }
        validTasksControllerFiles = resultsFolder.findAll { it.endsWith("-valid" + ConstantData.CONTROLLER_FILE_SUFIX) }

        /* INITIAL TEXT SIZE: 19 */
        relevantTasksDetailedFiles = resultsFolder.findAll {
            it.endsWith(ConstantData.RELEVANT_TASKS_DETAILS_FILE_SUFIX)
        }
        validTasksDetailedFiles = resultsFolder.findAll { it.endsWith(ConstantData.VALID_TASKS_DETAILS_FILE_SUFIX) }

        /* INITIAL TEXT SIZE: 19 */
        invalidTasksFiles = resultsFolder.findAll { it ==~ /.+-invalid.*\.csv/ }

        extractValidTasks()
    }

    def organize() {
        def relevantFiles = relevantTasksFiles + relevantTasksControllerFiles
        relevantFiles.each { extractEvaluationDataFromRelevantTasks(it) }

        relevantSimilarityFiles.each { extractSimilarityDataFromRelevantTasks(it) }
        relevantTasksDetailedFiles.each { extractDataFromRelevantDetailedTasks(it) }
        testFiles.each { extractTestData(it) }

        organizeInvalidTasks()

        def validFiles = validTasksFiles + validTasksControllerFiles
        validFiles.each { extractEvaluationDataFromValidTasks(it) }
        validSimilarityFiles.each { extractSimilarityDataFromValidTasks(it) }
        validTasksDetailedFiles.each { extractDataFromValidDetailedTasks(it) }
    }

    private configureFileName(String file) {
        def index = file.lastIndexOf(File.separator)
        def folder = file.substring(0, index)
        def name = file.substring(index, file.size())
        File fileObj = new File(folder + File.separator + outputFolder)
        if (!fileObj.exists()) fileObj.mkdir()
        fileObj.path + File.separator + name
    }

    /*private static configureInvalidFileName(String file){
        def index = file.lastIndexOf(File.separator)
        def folder = file.substring(0, index)
        def name = file.substring(index, file.size())
        def index2 = name.indexOf(ConstantData.CSV_FILE_EXTENSION)
        def finalName = name.substring(0, index2)
        folder + File.separator + finalName + "-final" + ConstantData.INVALID_TASKS_FILE_SUFIX
        //file
    }*/

    private extractSimilarityDataFromRelevantTasks(String file) {
        List<String[]> content = []
        List<String[]> entries = CsvUtil.read(file)
        if (entries.size() > SimilarityExporter.INITIAL_TEXT_SIZE) {
            def header = entries.subList(0, SimilarityExporter.INITIAL_TEXT_SIZE)
            List<String[]> newHeader = []
            newHeader += header.get(0)
            def data = entries.subList(SimilarityExporter.INITIAL_TEXT_SIZE, entries.size())
            def tasks = data.findAll {
                ((it[0] as int) in selectedTasks) && ((it[1] as int) in selectedTasks)
            }
            if (!tasks.empty) {
                def textSimilarity = tasks.collect { it[SimilarityExporter.TEXT_SIMILARITY_INDEX] as double }
                def dataRealJaccard = tasks.collect { it[SimilarityExporter.REAL_JACCARD_INDEX] as double }
                def dataRealCosine = tasks.collect { it[SimilarityExporter.REAL_COSINE_INDEX] as double }
                def correlationJaccard = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity as double[], dataRealJaccard as double[])
                def correlationCosine = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity as double[], dataRealCosine as double[])
                header[1][1] = correlationJaccard
                header[2][1] = correlationCosine
                newHeader += header.get(1)
                newHeader += header.get(2)
                newHeader += header.get(3)
            }
            content += newHeader
            content += tasks
            def newName = configureFileName(file)
            CsvUtil.write(newName, content)
        }
    }

    private extractEvaluationDataFromRelevantTasks(String file) {
        List<String[]> content = []
        List<String[]> entries = CsvUtil.read(file)
        if (entries.size() > ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER) {
            def header = entries.subList(0, ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER)
            List<String[]> newHeader = []
            newHeader += header.get(0)
            def data = entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
            def tasks = data.findAll { (it[0] as int) in selectedTasks }
            if (!tasks.empty) {
                double[] tests = tasks.collect { it[ExporterUtil.IMPLEMENTED_GHERKIN_TESTS] as double }
                double[] precisionValues = tasks.collect { it[ExporterUtil.PRECISION_INDEX_SHORT_HEADER] as double }
                double[] recallValues = tasks.collect { it[ExporterUtil.RECALL_INDEX_SHORT_HEADER] as double }
                double[] f2Values = tasks.collect { it[ExporterUtil.F2_INDEX] as double }
                def statistics = ExporterUtil.generateStatistics(precisionValues, recallValues, tests, f2Values)
                newHeader += statistics
            }
            content += newHeader
            content += header.get(12)
            content += tasks
            def newName = configureFileName(file)
            CsvUtil.write(newName, content)
        }
    }

    private extractTestData(String file) {
        List<String[]> content = []
        List<String[]> entries = CsvUtil.read(file)
        if (entries.size() > TestExecutionExporter.INITIAL_TEXT_SIZE) {
            def header = entries.subList(0, TestExecutionExporter.INITIAL_TEXT_SIZE)
            def data = entries.subList(TestExecutionExporter.INITIAL_TEXT_SIZE, entries.size())
            def tasks = data.findAll { (it[0] as int) in selectedTasks }
            content += header
            content += tasks
            def newName = configureFileName(file)
            CsvUtil.write(newName, content)
        }
    }

    private extractDataFromRelevantDetailedTasks(String file) {
        List<String[]> content = []
        List<String[]> entries = CsvUtil.read(file)
        def initialTextSize = 19
        if (entries.size() > initialTextSize) {
            def header = entries.subList(0, initialTextSize)
            def data = entries.subList(initialTextSize, entries.size())
            def tasks = data.findAll { (it[0] as int) in selectedTasks }
            content += header.get(0)
            content += header.get(initialTextSize - 1)
            content += tasks
            def newName = configureFileName(file)
            CsvUtil.write(newName, content)
        }
    }

    private organizeInvalidTasks() {
        if (!invalidTasksFiles || invalidTasksFiles.empty) return
        def initialTextSize = 19
        List<String[]> content = []
        def counter = 0
        List<String[]> header = []
        def newName = ""
        List<String[]> tasks = []
        invalidTasksFiles.each { file ->
            List<String[]> entries = CsvUtil.read(file)
            if (entries.size() > initialTextSize) {
                if (counter < 1) {
                    header = entries.subList(0, initialTextSize)
                    content += header.first()
                    content += header.last()
                    newName = file //configureInvalidFileName(file)
                }
                tasks += entries.subList(initialTextSize, entries.size())
                new File(file).delete()
                counter++
            }
        }

        def finalTasks = tasks.unique { it[0] as int }.sort { it[0] as int }
        content += ["All invalid tasks", finalTasks.size()] as String[]
        content += finalTasks
        if (!finalTasks.empty) CsvUtil.write(newName, content)
    }

    private extractValidTasks() {
        validTasks = []
        validTasksFiles.each { file ->
            def lines = CsvUtil.read(file)
            def tasks = lines.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, lines.size()).collect {
                it[0] as int
            }
            validTasks += tasks
        }
        validTasks = validTasks.sort().unique()
    }

    private extractSimilarityDataFromValidTasks(String file) {
        List<String[]> content = []
        List<String[]> entries = CsvUtil.read(file)
        if (entries.size() > SimilarityExporter.INITIAL_TEXT_SIZE) {
            def header = entries.subList(0, SimilarityExporter.INITIAL_TEXT_SIZE)
            List<String[]> newHeader = []
            newHeader += header.get(0)
            def data = entries.subList(SimilarityExporter.INITIAL_TEXT_SIZE, entries.size())
            def tasks = data.findAll {
                ((it[0] as int) in validTasks) && ((it[1] as int) in validTasks)
            }
            if (!tasks.empty) {
                def textSimilarity = tasks.collect { it[SimilarityExporter.TEXT_SIMILARITY_INDEX] as double }
                def dataRealJaccard = tasks.collect { it[SimilarityExporter.REAL_JACCARD_INDEX] as double }
                def dataRealCosine = tasks.collect { it[SimilarityExporter.REAL_COSINE_INDEX] as double }
                def correlationJaccard = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity as double[], dataRealJaccard as double[])
                def correlationCosine = TaskInterfaceEvaluator.calculateCorrelation(textSimilarity as double[], dataRealCosine as double[])
                header[1][1] = correlationJaccard
                header[2][1] = correlationCosine
                newHeader += header.get(1)
                newHeader += header.get(2)
                newHeader += header.get(3)
            }
            content += newHeader
            content += tasks
            def newName = configureFileName(file)
            CsvUtil.write(newName, content)
        }
    }

    private extractEvaluationDataFromValidTasks(String file) {
        List<String[]> content = []
        List<String[]> entries = CsvUtil.read(file)
        if (entries.size() > ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER) {
            def header = entries.subList(0, ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER)
            List<String[]> newHeader = []
            newHeader += header.get(0)
            def data = entries.subList(ExporterUtil.INITIAL_TEXT_SIZE_SHORT_HEADER, entries.size())
            def tasks = data.findAll { (it[0] as int) in validTasks }
            if (!tasks.empty) {
                double[] tests = tasks.collect { it[ExporterUtil.IMPLEMENTED_GHERKIN_TESTS] as double }
                double[] precisionValues = tasks.collect { it[ExporterUtil.PRECISION_INDEX_SHORT_HEADER] as double }
                double[] recallValues = tasks.collect { it[ExporterUtil.RECALL_INDEX_SHORT_HEADER] as double }
                double[] f2Values = tasks.collect { it[ExporterUtil.F2_INDEX] as double }
                def statistics = ExporterUtil.generateStatistics(precisionValues, recallValues, tests, f2Values)
                newHeader += statistics
            }
            content += newHeader
            content += header.get(12)
            content += tasks
            def newName = configureFileName(file)
            CsvUtil.write(newName, content)
        }
    }

    private extractDataFromValidDetailedTasks(String file) {
        List<String[]> content = []
        List<String[]> entries = CsvUtil.read(file)
        def initialTextSize = 19
        if (entries.size() > initialTextSize) {
            def header = entries.subList(0, initialTextSize)
            def data = entries.subList(initialTextSize, entries.size())
            def tasks = data.findAll { (it[0] as int) in validTasks }
            content += header.get(0)
            content += header.get(initialTextSize - 1)
            content += tasks
            def newName = configureFileName(file)
            CsvUtil.write(newName, content)
        }
    }

}

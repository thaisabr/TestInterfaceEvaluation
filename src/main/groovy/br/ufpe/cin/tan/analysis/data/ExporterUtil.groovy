package br.ufpe.cin.tan.analysis.data

import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import br.ufpe.cin.tan.util.RegexUtil
import br.ufpe.cin.tan.util.Util
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics


class ExporterUtil {

    public static String[] SHORT_HEADER = ["TASK", "#DAYS", "#DEVS", "#COMMITS", "HASHES", "#GHERKIN_TESTS", "#ITest", "#IReal",
                                    "ITest", "IReal", "PRECISION", "RECALL", "RAILS", "#visit_call", "#ITest_views",
                                    "#view_analysis_code", "view_analysis_code", "methods_no_origin", "renamed_files",
                                    "deleted_files", "noFound_views", "#noFound_views", "TIMESTAMP"]

    public static final int RECALL_INDEX_SHORT_HEADER = SHORT_HEADER.size() - 12
    public static final int PRECISION_INDEX_SHORT_HEADER = RECALL_INDEX_SHORT_HEADER - 1
    public static final int IREAL_INDEX_SHORT_HEADER = PRECISION_INDEX_SHORT_HEADER - 1
    public static final int ITEST_INDEX_SHORT_HEADER = IREAL_INDEX_SHORT_HEADER - 1
    public static final int ITEST_SIZE_INDEX_SHORT_HEADER = ITEST_INDEX_SHORT_HEADER - 2
    public static final int IREAL_SIZE_INDEX_SHORT_HEADER = IREAL_INDEX_SHORT_HEADER - 2
    public static final int INITIAL_TEXT_SIZE_SHORT_HEADER = 8
    public static final int ITEST_VIEWS_SIZE_INDEX_SHORT_HEADER = 15

    static generateStatistics(double[] precisionValues, double[] recallValues){
        if(!precisionValues || precisionValues.size()==0 || !recallValues || recallValues.size()==0) return []
        List<String[]> content = []
        def precisionStats = new DescriptiveStatistics(precisionValues)
        def recallStats = new DescriptiveStatistics(recallValues)
        content += ["Precision mean (RT)", precisionStats.mean] as String[]
        content += ["Precision median (RT)", precisionStats.getPercentile(50.0)] as String[]
        content += ["Precision standard deviation (RT)", precisionStats.standardDeviation] as String[]
        content += ["Recall mean (RT)", recallStats.mean] as String[]
        content += ["Recall median (RT)", recallStats.getPercentile(50.0)] as String[]
        content += ["Recall standard deviation (RT)", recallStats.standardDeviation] as String[]
        content
    }

    static String[] configureLine(String[] value){
        def originalItest = configureITask(value, ITEST_INDEX_SHORT_HEADER)
        def itest = findControllers(originalItest)
        def originalIReal = configureITask(value, IREAL_INDEX_SHORT_HEADER)
        def ireal = findControllers(originalIReal)
        def precision = TaskInterfaceEvaluator.calculateFilesPrecision(itest, ireal)
        def recall = TaskInterfaceEvaluator.calculateFilesRecall(itest, ireal)

        String[] line = value
        line[ITEST_SIZE_INDEX_SHORT_HEADER] = itest.size()
        line[IREAL_SIZE_INDEX_SHORT_HEADER] = ireal.size()
        line[ITEST_INDEX_SHORT_HEADER] = itest
        line[IREAL_INDEX_SHORT_HEADER] = ireal
        line[PRECISION_INDEX_SHORT_HEADER] = precision
        line[RECALL_INDEX_SHORT_HEADER] = recall
        line[ITEST_VIEWS_SIZE_INDEX_SHORT_HEADER] = 0
        line
    }

    private static Set<String> findControllers(Set<String> set){
        set.findAll { Util.isControllerFile(it) }
    }

    private static Set configureITask(String[] value, int index){
        def originalItest = value[index].replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"/")
                .substring(1,value[index].size()-1)
                .split(",")
                .flatten()
                .collect{ it.trim() } as Set
        originalItest
    }

}

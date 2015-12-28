package evaluation

import taskAnalyser.TaskInterface

class TaskInterfaceEvaluator {

    private static calculateTruePositives(Set set1, Set set2){
        (set1.intersect(set2)).size()
    }

    private static calculateFalsePositives(Set set1, Set set2){
        (set1-set2).size()
    }

    private static calculateFalseNegatives(Set set1, Set set2){
        (set2-set1).size()
    }

    static getCommonFiles(TaskInterface ITest, TaskInterface IReal){
        ITest.findAllFiles().intersect(IReal.findAllFiles())
    }

    /***
     * Calculates precision of test based task interface considering files only.
     *
     * @param ITest task interface based in test code
     * @param IReal task interface computed after task is done
     * @return value between 0 and 1
     */
    static double calculateFilesPrecision(TaskInterface ITest, TaskInterface IReal){
        double result = 0
        def testFiles = ITest.findAllFiles()
        if(!testFiles.isEmpty()){
            def truePositives = calculateTruePositives(testFiles,IReal.findAllFiles())
            //println "truePositives in precision: $truePositives"
            //println "total in precision: ${testFiles.size()}"
            if(truePositives>0) result = (double) truePositives/testFiles.size()
        }
        return result
    }

    /***
     * Calculates recall of test based task interface considering files only.
     *
     * @param ITest ITest task interface based in test code
     * @param IReal task interface computed after task is done
     * @return value between 0 and 1
     */
    static double calculateFilesRecall(TaskInterface ITest, TaskInterface IReal){
        double result = 0
        def realFiles = IReal.findAllFiles()
        if(!realFiles.isEmpty()){
            def truePositives = calculateTruePositives(ITest.findAllFiles(),realFiles)
            //println "truePositives in recall: $truePositives"
            //println "total in recall: ${IReal.files.size()}"
            if(truePositives>0) result = (double) truePositives/realFiles.size()
        }
        return result
    }

}

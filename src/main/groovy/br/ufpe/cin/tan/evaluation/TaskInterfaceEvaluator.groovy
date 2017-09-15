package br.ufpe.cin.tan.evaluation

import br.ufpe.cin.tan.analysis.itask.TaskInterface

class TaskInterfaceEvaluator {

    /***
     * Calculates precision of test based task interface considering files only.
     *
     * @param iTest task interface based in test code
     * @param iReal task interface computed after task is done
     * @return value between 0 and 1
     */
    static double calculateFilesPrecision(TaskInterface iTest, TaskInterface iReal) {
        double result = 0
        if (invalidInput(iTest, iReal)) return result
        def testFiles = iTest.findFilteredFiles()
        def truePositives = calculateTruePositives(testFiles, iReal.findFilteredFiles())
        if (truePositives > 0) result = (double) truePositives / testFiles.size()
        result
    }

    /***
     * Calculates recall of test based task interface considering files only.
     *
     * @param iTest ITest task interface based in test code
     * @param iReal task interface computed after task is done
     * @return value between 0 and 1
     */
    static double calculateFilesRecall(TaskInterface iTest, TaskInterface iReal) {
        double result = 0
        if (invalidInput(iTest, iReal)) return result
        def realFiles = iReal.findFilteredFiles()
        def truePositives = calculateTruePositives(iTest.findFilteredFiles(), realFiles)
        if (truePositives > 0) result = (double) truePositives / realFiles.size()
        result
    }

    static double calculateFilesPrecision(Set iTest, Set iReal) {
        double result = 0
        if (invalidInput(iTest, iReal)) return result
        def truePositives = calculateTruePositives(iTest, iReal)
        if (truePositives > 0) result = (double) truePositives / iTest.size()
        result
    }

    static double calculateFilesRecall(Set iTest, Set iReal) {
        double result = 0
        if (invalidInput(iTest, iReal)) return result
        def truePositives = calculateTruePositives(iTest, iReal)
        if (truePositives > 0) result = (double) truePositives / iReal.size()
        result
    }

    private static calculateTruePositives(Set set1, Set set2) {
        (set1.intersect(set2)).size()
    }

    private static invalidInput(iTest, iReal) {
        if (!iTest || iTest.empty || !iReal || iReal.empty) true
        else false
    }

}

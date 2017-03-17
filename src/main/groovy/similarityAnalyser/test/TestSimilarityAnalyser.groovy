package similarityAnalyser.test

import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import taskAnalyser.task.TaskInterface

class TestSimilarityAnalyser {

    private static RealVector toRealVector(Collection terms, Collection set) {
        RealVector vector = new ArrayRealVector(terms.size())
        int i = 0
        terms.each { term ->
            int value = set.contains(term) ? 1 : 0
            vector.setEntry(i++, value)
        }
        vector
    }

    private static double getCosineSimilarity(RealVector v1, RealVector v2) {
        v1.dotProduct(v2) / (v1.norm * v2.norm)
    }

    static double calculateSimilarityByCosine(Collection set1, Collection set2) {
        if (!set1 || set1.empty || !set2 || set2.empty) return 0.0
        def terms = (set1 + set2).sort()
        RealVector v1 = toRealVector(terms, set1.sort())
        RealVector v2 = toRealVector(terms, set2.sort())
        getCosineSimilarity(v1, v2)
    }

    static double calculateSimilarityByJaccard(Collection set1, Collection set2) {
        if (!set1 || !set2 || set1.isEmpty() || set2.isEmpty()) return 0.0
        def intersection = set1.intersect(set2).size()
        def divisor = set1.size() + set2.size() - intersection
        if (divisor == 0) 0.0
        else intersection / divisor
    }

    static calculateSimilarityByJaccard(TaskInterface interface1, TaskInterface interface2) {
        calculateSimilarityByJaccard(interface1.findAllProdFiles(), interface2.findAllProdFiles())
    }

    static calculateSimilarityByCosine(TaskInterface interface1, TaskInterface interface2) {
        calculateSimilarityByCosine(interface1.findAllProdFiles(), interface2.findAllProdFiles())
    }

}

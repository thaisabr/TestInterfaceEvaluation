package br.ufpe.cin.tan.similarity.test

import br.ufpe.cin.tan.analysis.itask.ITest
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector

class TestSimilarityAnalyser {

    Collection c1
    Collection c2
    Collection terms

    TestSimilarityAnalyser(Collection c1, Collection c2) {
        this.c1 = c1
        this.c2 = c2
        terms = (c1 + c2).unique().sort()
    }

    TestSimilarityAnalyser(ITest interface1, ITest interface2) {
        this.c1 = interface1.getFiles()
        this.c2 = interface2.getFiles()
        terms = (c1 + c2).sort()
    }

    double calculateSimilarityByJaccard() {
        if (!c1 || !c2 || c1.isEmpty() || c2.isEmpty()) return 0.0
        def intersection = c1.intersect(c2).size()
        def divisor = c1.size() + c2.size() - intersection
        if (divisor == 0) 0.0
        else intersection / divisor
    }

    double calculateSimilarityByCosine() {
        if (!c1 || c1.empty || !c2 || c2.empty) return 0.0
        RealVector v1 = toRealVector(c1.sort())
        RealVector v2 = toRealVector(c2.sort())
        getCosineSimilarity(v1, v2)
    }

    double calculateSimilarityBySorensen() {
        def jaccard = calculateSimilarityByJaccard()
        (2 * jaccard) / (1 + jaccard)
    }

    private RealVector toRealVector(Collection set) {
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

}

package br.ufpe.cin.tan.similarity.text

import br.ufpe.cin.tan.analysis.task.Task
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.Terms
import org.apache.lucene.index.TermsEnum
import org.apache.lucene.util.BytesRef

class TextualSimilarityAnalyser {
    IndexManager indexManager
    IndexReader reader
    Set terms

    double calculateSimilarity(Task task1, Task task2) {
        if (!task1 || !task2) return 0
        calculate(task1, task2)
    }

    double calculateSimilarity(String task1, String task2) {
        if (task1 == "" || task2 == "") return 0
        calculate(task1, task2)
    }

    private calculate(task1, task2) {
        configureIndexManager(task1, task2)
        reader = DirectoryReader.open(indexManager.indexDirectory)
        calculateFreqVectorSimilarity()
    }

    private configureIndexManager(Task task1, Task task2) {
        indexManager = new IndexManager()
        indexManager.index(task1.acceptanceTests)
        indexManager.index(task2.acceptanceTests)
        terms = [] as Set
    }

    private configureIndexManager(String task1, String task2) {
        indexManager = new IndexManager()
        indexManager.index(task1)
        indexManager.index(task2)
        terms = [] as Set
    }

    private getTermFrequencies(int docId) {
        def frequencies = [:]
        Terms vector = reader.getTermVector(docId, "content")
        if (!vector) return frequencies
        TermsEnum termsEnum = vector.iterator()
        BytesRef text
        while ((text = termsEnum?.next()) != null) {
            String term = text.utf8ToString()
            int freq = (int) termsEnum.totalTermFreq()
            frequencies += [(term): freq]
            terms += term
        }
        frequencies
    }

    private RealVector toRealVector(Map map) {
        RealVector vector = new ArrayRealVector(terms.size())
        int i = 0
        terms.each { term ->
            int value = map.containsKey(term) ? map.get(term) : 0
            vector.setEntry(i++, value)
        }
        return (RealVector) vector.mapDivide(vector.getL1Norm())
    }

    private static double getCosineSimilarity(RealVector v1, RealVector v2) {
        v1.dotProduct(v2) / (v1.norm * v2.norm)
    }

    private calculateFreqVectorSimilarity() {
        def freqVectorTask1 = getTermFrequencies(0).sort()
        def freqVectorTask2 = getTermFrequencies(1).sort()
        RealVector v1 = toRealVector(freqVectorTask1)
        RealVector v2 = toRealVector(freqVectorTask2)
        getCosineSimilarity(v1, v2)
    }

}

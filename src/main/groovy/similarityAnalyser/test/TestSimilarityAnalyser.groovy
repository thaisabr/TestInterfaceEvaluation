package similarityAnalyser.test

import taskAnalyser.TaskInterface


class TestSimilarityAnalyser {

    static calculateSimilarity(Collection set1, Collection set2){ //Jaccard Index
        if(!set1 || !set2 || set1.isEmpty() || set2.isEmpty()) 0
        def intersection = set1.intersect(set2).size()
        def divisor = set1.size()+set2.size()-intersection
        if(divisor==0) 0
        else intersection/divisor
    }

    static calculateSimilarity(TaskInterface interface1, TaskInterface interface2){
        calculateSimilarity(interface1.findAllFiles(), interface2.findAllFiles())
    }

}

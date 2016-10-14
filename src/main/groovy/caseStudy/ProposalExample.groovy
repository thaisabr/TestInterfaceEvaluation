package caseStudy

import groovy.util.logging.Slf4j
import similarityAnalyser.test.TestSimilarityAnalyser
import taskAnalyser.task.TodoTask

@Slf4j
class ProposalExample {

    public static void main(String[] args) {
        def repositoryUrl = "repositories\\rgms"

        def featureAdamPath1 = "Article.feature"
        def todoTA1_autofill = new TodoTask(repositoryUrl, false, "1", [[path: featureAdamPath1, lines: [111]]])
        def iTA1_autofill = todoTA1_autofill.computeTestBasedInterface()
        println "TA1_autofill FILES:"
        iTA1_autofill.findAllFiles().each {
            println it
        }

        def todoTA1_number = new TodoTask(repositoryUrl, false, "2", [[path: featureAdamPath1, lines: [7, 12]]])
        def iTA1_number = todoTA1_number.computeTestBasedInterface()
        println "TA1_number FILES:"
        iTA1_number.findAllFiles().each {
            println it
        }

        def featureAdamPath2 = "News.feature"
        def todoTA2 = new TodoTask(repositoryUrl, false, "3", [[path: featureAdamPath2, lines: [25, 31, 37]]])
        def iTA2 = todoTA2.computeTestBasedInterface()
        println "TA2 FILES:"
        iTA2.findAllFiles().each {
            println it
        }

        def featureBethPath1 = "XMLImport.feature"
        //def todoTB1 = new TodoTask(repositoryUrl, false, "4", [[path:featureBethPath1, lines:[15,35]]])
        def todoTB1 = new TodoTask(repositoryUrl, false, "4", [[path: featureBethPath1, lines: [7, 15, 28, 35, 43]]])
        def iTB1 = todoTB1.computeTestBasedInterface()
        println "TB1 FILES:"
        iTB1.findAllFiles().each {
            println it
        }

        def featureBethPath2 = "Funder.feature"
        def todoTB2 = new TodoTask(repositoryUrl, false, "5", [[path: featureBethPath2, lines: [6, 11, 16]]])
        def iTB2 = todoTB2.computeTestBasedInterface()
        println "TB2 FILES:"
        iTB2.findAllFiles().each {
            println it
        }

        // TA, TA', TB
        println "SIMILARITY(TA1_number,TB1): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA1_number, iTB1)}"
        println "SIMILARITY(TA2,TB1): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA2, iTB1)}"

        // TA, TA', TB' */
        println "SIMILARITY(TA1_number,TB2): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA1_number, iTB2)}"
        println "SIMILARITY(TA2,TB2): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA2, iTB2)}"

        //conflict
        println "SIMILARITY(TA1_autofill,TB1): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA1_autofill, iTB1)}"
        println "SIMILARITY(TA1_autofill,TB2): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA1_autofill, iTB2)}"
        println "SIMILARITY(TA2,TB1): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA2, iTB2)}"
        println "SIMILARITY(TA2,TB2): ${TestSimilarityAnalyser.calculateSimilarityByJaccard(iTA2, iTB2)}"
    }

}

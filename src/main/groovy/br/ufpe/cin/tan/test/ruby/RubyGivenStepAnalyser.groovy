package br.ufpe.cin.tan.test.ruby

import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.test.MethodToAnalyse
import br.ufpe.cin.tan.util.Util
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.MethodDefNode

/***
 * Visits step definitions referenced by given steps.
 * It is needed when the "when filter" is enabled.
 * It is responsible for identifying visit calls. It ignores any other method call.
 */
class RubyGivenStepAnalyser {

    RubyTestCodeVisitor filteredVisitor
    RubyTestCodeVisitor auxVisitor
    RubyTestCodeVisitor methodCallVisitor

    RubyGivenStepAnalyser(RubyTestCodeVisitor methodCallVisitor) {
        this.methodCallVisitor = methodCallVisitor
    }

    def analyse(FCallNode iVisited, MethodToAnalyse method) {
        reset()

        filteredVisitor.stepDefinitionMethod = method
        iVisited.accept(filteredVisitor)

        auxVisitor.stepDefinitionMethod = method
        iVisited.accept(auxVisitor)

        organizeAnalysisResult(filteredVisitor, auxVisitor)
    }

    def analyse(MethodDefNode iVisited, step) {
        reset()
        filteredVisitor.step = step
        iVisited.accept(filteredVisitor)
        filteredVisitor.step = null
        iVisited.accept(auxVisitor)
        organizeAnalysisResult(filteredVisitor, auxVisitor)
    }

    private reset() {
        filteredVisitor = new RubyTestCodeVisitor(methodCallVisitor.projectFiles, methodCallVisitor.lastVisitedFile,
                methodCallVisitor.projectMethods)
        filteredVisitor.filteredAnalysis = true
        auxVisitor = new RubyTestCodeVisitor(methodCallVisitor.projectFiles, methodCallVisitor.lastVisitedFile,
                methodCallVisitor.projectMethods)
    }

    private organizeAnalysisResult(RubyTestCodeVisitor filteredVisitor, RubyTestCodeVisitor auxVisitor) {
        ITest diff = auxVisitor.taskInterface.minus(filteredVisitor.taskInterface)
        def calledSteps = (filteredVisitor.calledSteps + auxVisitor.calledSteps).unique()
        def testMethods = diff.methods.findAll { (it.file != null && Util.isTestFile(it.file)) }
        methodCallVisitor.calledSteps += calledSteps
        methodCallVisitor.stepCallCounter = methodCallVisitor.calledSteps.size()
        methodCallVisitor.lostVisitCall = filteredVisitor.lostVisitCall + auxVisitor.lostVisitCall
        methodCallVisitor.taskInterface.methods += testMethods
        methodCallVisitor.taskInterface.collapseInterfaces(filteredVisitor.taskInterface)
    }

}

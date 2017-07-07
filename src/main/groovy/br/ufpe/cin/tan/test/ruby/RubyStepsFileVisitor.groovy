package br.ufpe.cin.tan.test.ruby

import br.ufpe.cin.tan.test.MethodToAnalyse
import br.ufpe.cin.tan.util.Util
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.util.NoopVisitor

/***
 * Visits steps declaration of interest and its body looking for production method calls.
 */
class RubyStepsFileVisitor extends NoopVisitor {

    List<MethodToAnalyse> methods
    List lines
    RubyTestCodeVisitor methodCallVisitor
    List<String> fileContent
    List<String> body

    RubyStepsFileVisitor(List<MethodToAnalyse> methodsToAnalyse, RubyTestCodeVisitor methodCallVisitor, List<String> fileContent) {
        this.lines = methodsToAnalyse*.line
        this.methods = methodsToAnalyse
        this.methodCallVisitor = methodCallVisitor
        this.fileContent = fileContent
        this.body = []
    }

    /**
     * FCallNode represents a method call with self as an implicit receiver. Step code are identified here.
     */
    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if (iVisited.position.startLine in lines) {
            def matches = methods.findAll { it.line == iVisited.position.startLine }
            matches?.each { method ->
                extractMethodBody(iVisited)
                if (Util.WHEN_FILTER) filteredAnalysis(iVisited, method)
                else noFilteredAnalysis(iVisited, method)
            }
        }
        iVisited
    }

    private filteredAnalysis(FCallNode iVisited, MethodToAnalyse method) {
        switch (method.type) {
            case "Given ":
                RubyGivenStepAnalyser givenStepAnalyser = new RubyGivenStepAnalyser(methodCallVisitor)
                givenStepAnalyser.analyse(iVisited, method)
                break
            case "When ": //common analysis
                noFilteredAnalysis(iVisited, method)
        //we do not analyse "then" step
        }
    }

    private noFilteredAnalysis(FCallNode iVisited, MethodToAnalyse method) {
        methodCallVisitor.stepDefinitionMethod = method
        iVisited.accept(methodCallVisitor)
        methodCallVisitor.stepDefinitionMethod = null
    }

    private extractMethodBody(FCallNode iVisited) {
        def methodBody = fileContent.getAt([iVisited.position.startLine..iVisited.position.endLine])
        body += methodBody
    }

}

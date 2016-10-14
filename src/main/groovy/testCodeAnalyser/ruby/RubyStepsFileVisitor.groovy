package testCodeAnalyser.ruby

import org.jrubyparser.ast.FCallNode
import org.jrubyparser.util.NoopVisitor
import testCodeAnalyser.MethodToAnalyse

/***
 * Visits steps declaration of interest and its body looking for production method calls.
 */
class RubyStepsFileVisitor extends NoopVisitor {

    List<MethodToAnalyse> methods
    List lines
    RubyTestCodeVisitor methodCallVisitor

    RubyStepsFileVisitor(List<MethodToAnalyse> methodsToAnalyse, RubyTestCodeVisitor methodCallVisitor) {
        this.lines = methodsToAnalyse*.line
        this.methods = methodsToAnalyse
        this.methodCallVisitor = methodCallVisitor
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
                methodCallVisitor.stepDefinitionMethod = method
                iVisited.accept(methodCallVisitor)
                methodCallVisitor.stepDefinitionMethod = null
            }
        }
        iVisited
    }

}

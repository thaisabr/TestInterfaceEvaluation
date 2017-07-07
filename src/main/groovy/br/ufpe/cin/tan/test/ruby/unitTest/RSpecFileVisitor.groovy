package br.ufpe.cin.tan.test.ruby.unitTest

import br.ufpe.cin.tan.test.ruby.RubyTestCodeVisitor
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.util.NoopVisitor

/***
 * Visits unit test declaration of interest and its body looking for production method calls.
 */
class RSpecFileVisitor extends NoopVisitor {

    List lines //each element represents a range
    RubyTestCodeVisitor methodCallVisitor

    RSpecFileVisitor(List lines, RubyTestCodeVisitor methodCallVisitor) {
        this.lines = lines
        this.methodCallVisitor = methodCallVisitor
    }

    @Override
    /**
     * FCallNode represents a method call with self as an implicit receiver. Unit tests are identified here.
     */
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        def fcallLines = iVisited.position.startLine..iVisited.position.endLine
        if (fcallLines in lines) {
            iVisited.accept(methodCallVisitor)
        }
        return iVisited
    }

}

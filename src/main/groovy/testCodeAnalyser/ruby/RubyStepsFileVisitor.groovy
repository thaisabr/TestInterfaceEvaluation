package testCodeAnalyser.ruby

import org.jrubyparser.ast.FCallNode
import org.jrubyparser.util.NoopVisitor

/***
 * Visits steps declaration of interest and its body looking for production method calls.
 */
class RubyStepsFileVisitor extends NoopVisitor {

    List lines
    RubyTestCodeVisitor methodCallVisitor

    RubyStepsFileVisitor(List lines, RubyTestCodeVisitor methodCallVisitor){
        this.lines = lines
        this.methodCallVisitor = methodCallVisitor
    }

    /**
     * FCallNode represents a method call with self as an implicit receiver. Step code are identified here.
     */
    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if( iVisited.position.startLine in lines) {
            iVisited.accept(methodCallVisitor)
        }
        return iVisited
    }

}

package testCodeAnalyser.ruby

import org.jrubyparser.ast.FCallNode
import org.jrubyparser.util.NoopVisitor

/***
 * Visits steps declaration of interest and its body looking for production method calls.
 */
class RubyStepsFileVisitor extends NoopVisitor {

    List lines
    RubyTestCodeVisitor methodCallVisitor

    public RubyStepsFileVisitor(List lines, RubyTestCodeVisitor methodCallVisitor){
        this.lines = lines
        this.methodCallVisitor = methodCallVisitor
    }

    @Override
    /**
     * FCallNode represents a method call with self as an implicit receiver. Step code are identified here.
     */
    Object visitFCallNode(FCallNode iVisited) {
        if( iVisited.position.startLine in lines) {
            iVisited.accept(methodCallVisitor)
        }
    }

}

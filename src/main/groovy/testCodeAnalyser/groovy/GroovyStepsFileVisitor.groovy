package testCodeAnalyser.groovy

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.control.SourceUnit

/***
 * Visits steps declaration of interest and its body looking for production method calls.
 */
class GroovyStepsFileVisitor extends ClassCodeVisitorSupport {

    SourceUnit source
    List lines
    GroovyTestCodeVisitor methodCallVisitor

    public GroovyStepsFileVisitor(List lines, GroovyTestCodeVisitor methodCallVisitor){
        this.lines = lines
        this.methodCallVisitor = methodCallVisitor
    }

    @Override
    protected SourceUnit getSourceUnit() {
        source
    }

    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression call){
        super.visitStaticMethodCallExpression(call)
        if (call.lineNumber in lines) {
            call.visit(methodCallVisitor)
        }
    }

}


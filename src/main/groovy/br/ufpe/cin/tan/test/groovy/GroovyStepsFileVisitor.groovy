package br.ufpe.cin.tan.test.groovy

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.control.SourceUnit
import br.ufpe.cin.tan.test.MethodToAnalyse

/***
 * Visits steps declaration of interest and its body looking for production method calls.
 */
class GroovyStepsFileVisitor extends ClassCodeVisitorSupport {

    SourceUnit source
    List lines
    GroovyTestCodeVisitor methodCallVisitor

    GroovyStepsFileVisitor(List<MethodToAnalyse> methodsToAnalyse, GroovyTestCodeVisitor methodCallVisitor) {
        this.lines = methodsToAnalyse*.line
        this.methodCallVisitor = methodCallVisitor
    }

    @Override
    protected SourceUnit getSourceUnit() {
        source
    }

    @Override
    void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
        super.visitStaticMethodCallExpression(call)
        if (call.lineNumber in lines) {
            call.visit(methodCallVisitor)
        }
    }

}


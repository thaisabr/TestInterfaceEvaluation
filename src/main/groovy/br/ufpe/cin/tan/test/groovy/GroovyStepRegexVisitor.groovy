package br.ufpe.cin.tan.test.groovy

import br.ufpe.cin.tan.test.StepRegex
import br.ufpe.cin.tan.util.ConstantData
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.control.SourceUnit

class GroovyStepRegexVisitor extends ClassCodeVisitorSupport {

    List<StepRegex> regexs
    String path
    SourceUnit source

    GroovyStepRegexVisitor(String path) {
        regexs = []
        this.path = path
    }

    @Override
    protected SourceUnit getSourceUnit() {
        source
    }

    @Override
    void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
        super.visitStaticMethodCallExpression(call)
        if (call.methodAsString in ConstantData.STEP_KEYWORDS) {
            regexs += new StepRegex(path: path, value: call.arguments[0].text, line: call.lineNumber)
        }
    }

}

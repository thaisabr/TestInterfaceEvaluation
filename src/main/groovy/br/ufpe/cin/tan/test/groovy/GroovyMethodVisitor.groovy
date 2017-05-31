package br.ufpe.cin.tan.test.groovy

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.SourceUnit

/***
 * Identifies method calls from interest. It should be used associated to GroovyTestCodeVisitor.
 */
class GroovyMethodVisitor extends ClassCodeVisitorSupport {

    SourceUnit source
    def methods
    GroovyTestCodeVisitor methodBodyVisitor

    GroovyMethodVisitor(List methods, GroovyTestCodeVisitor methodBodyVisitor) {
        this.methods = methods
        this.methodBodyVisitor = methodBodyVisitor
    }

    @Override
    protected SourceUnit getSourceUnit() {
        source
    }

    @Override
    void visitMethod(MethodNode node) {
        super.visitMethod(node)
        if (node.name in methods) {
            node.code.visit(methodBodyVisitor)
        }
    }

}

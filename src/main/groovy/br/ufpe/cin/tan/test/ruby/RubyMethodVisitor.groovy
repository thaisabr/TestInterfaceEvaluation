package br.ufpe.cin.tan.test.ruby

import org.jrubyparser.ast.DefnNode
import org.jrubyparser.ast.DefsNode
import org.jrubyparser.util.NoopVisitor

/***
 * Visits methods body searching for other method calls. It should be used associated to RubyTestCodeVisitor.
 */
class RubyMethodVisitor extends NoopVisitor {

    def methods
    RubyTestCodeVisitor methodBodyVisitor

    RubyMethodVisitor(List methods, RubyTestCodeVisitor methodBodyVisitor) {
        this.methods = methods
        this.methodBodyVisitor = methodBodyVisitor
    }

    @Override
    Object visitDefnNode(DefnNode iVisited) {
        super.visitDefnNode(iVisited)
        if (iVisited.name in methods) iVisited.accept(methodBodyVisitor)
        return iVisited
    }

    @Override
    Object visitDefsNode(DefsNode iVisited) {
        super.visitDefsNode(iVisited)
        if (iVisited.name in methods) iVisited.accept(methodBodyVisitor)
        return iVisited
    }

}

package br.ufpe.cin.tan.test.ruby

import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.Util
import org.jrubyparser.ast.DefnNode
import org.jrubyparser.ast.DefsNode
import org.jrubyparser.ast.MethodDefNode
import org.jrubyparser.util.NoopVisitor

/***
 * Visits methods body searching for other method calls. It should be used associated to RubyTestCodeVisitor.
 */
class RubyMethodVisitor extends NoopVisitor {

    List<String> fileContent
    List<String> body
    def methods
    RubyTestCodeVisitor methodBodyVisitor

    RubyMethodVisitor(List methods, RubyTestCodeVisitor methodBodyVisitor, List<String> fileContent) {
        this.methods = methods
        this.methodBodyVisitor = methodBodyVisitor
        this.fileContent = fileContent
        this.body = []
    }

    @Override
    Object visitDefnNode(DefnNode iVisited) {
        super.visitDefnNode(iVisited)
        analyse(iVisited)
    }

    @Override
    Object visitDefsNode(DefsNode iVisited) {
        super.visitDefsNode(iVisited)
        analyse(iVisited)
    }

    private extractMethodBody(MethodDefNode iVisited) {
        def methodBody = fileContent.getAt([iVisited.position.startLine..iVisited.position.endLine])
        body += methodBody
    }

    private analyse(MethodDefNode iVisited) {
        def foundMethod = methods.find { it.name == iVisited.name }
        if (foundMethod) {
            extractMethodBody(iVisited)
            if (Util.WHEN_FILTER && foundMethod.step == ConstantData.GIVEN_STEP_EN) {
                RubyGivenStepAnalyser givenStepAnalyser = new RubyGivenStepAnalyser(methodBodyVisitor)
                givenStepAnalyser.analyse(iVisited, foundMethod.step)
            } else if (Util.WHEN_FILTER && foundMethod.step == ConstantData.THEN_STEP_EN) return
            else {
                methodBodyVisitor.step = foundMethod.step
                iVisited.accept(methodBodyVisitor)
            }
        }
        iVisited
    }

}

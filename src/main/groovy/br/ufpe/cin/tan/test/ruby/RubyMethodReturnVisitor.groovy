package br.ufpe.cin.tan.test.ruby

import br.ufpe.cin.tan.util.ruby.RubyConstantData
import groovy.util.logging.Slf4j
import org.jrubyparser.ast.*
import org.jrubyparser.util.NoopVisitor

@Slf4j
class RubyMethodReturnVisitor extends NoopVisitor {

    Set<String> values
    String methodName
    List<String> args
    def returnNodes //keys: line, value

    RubyMethodReturnVisitor(String name, List<String> args) {
        this.values = [] as Set
        this.methodName = name
        this.args = args
        this.returnNodes = []
    }

    private extractViewPathFromNode(MethodDefNode iVisited) {
        if (iVisited.name == this.methodName) {
            def lines = iVisited.position.startLine..iVisited.position.endLine
            def nodes = this.returnNodes?.findAll { it.line in lines }
            this.values = nodes*.value
        }
    }

    /***
     * Identifies all return nodes that returns a string literal.
     */
    @Override
    Object visitReturnNode(ReturnNode iVisited) {
        super.visitReturnNode(iVisited)
        switch (iVisited.value.class) {
            case StrNode: //Representing a simple String literal
                def node = (StrNode) iVisited.value
                this.returnNodes += [line: iVisited.position.startLine, value: node.value]
                break
            case DStrNode: //A string which contains some dynamic elements which needs to be evaluated (introduced by #)
                def name = ""
                iVisited.value.childNodes().each { c -> if (c instanceof StrNode) name += c.value.trim() }
                def index = name.indexOf("?")
                if (index > 0) name = name.substring(0, index)//ignoring params
                if (!name.contains("//")) {
                    this.returnNodes += [line: iVisited.position.startLine, value: name]
                }
                break
            case FCallNode: //Method call with self as an implicit receiver
            case VCallNode: //Method call without any arguments
            case CallNode: //Method call
                def value = iVisited.value.name
                if (value.contains(RubyConstantData.ROUTE_PATH_SUFIX)) { //it is a path helper method
                    this.returnNodes += [line: iVisited.position.startLine, value: value]
                }
                break
        }

        iVisited
    }

    /***
     * Identifies the method node of interest to extract view path.
     */
    @Override
    Object visitDefnNode(DefnNode iVisited) {
        super.visitDefnNode(iVisited)
        this.extractViewPathFromNode(iVisited)
        return iVisited
    }

    @Override
    Object visitDefsNode(DefsNode iVisited) {
        super.visitDefsNode(iVisited)
        this.extractViewPathFromNode(iVisited)
        return iVisited
    }

}

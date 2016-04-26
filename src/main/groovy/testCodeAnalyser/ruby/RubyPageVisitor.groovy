package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.DStrNode
import org.jrubyparser.ast.DefnNode
import org.jrubyparser.ast.DefsNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.MethodDefNode
import org.jrubyparser.ast.ReturnNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.VCallNode
import org.jrubyparser.util.NoopVisitor
import util.ruby.RubyUtil


@Slf4j
class RubyPageVisitor extends NoopVisitor {

    Set<String> pages
    String methodName
    List<String> args
    def returnNodes //keys: line, value

    public RubyPageVisitor(String name, List<String> args){
        this.pages = [] as Set
        this.methodName = name
        this.args = args
        this.returnNodes = []
    }

    private extractViewPathFromNode(MethodDefNode iVisited){
        if(iVisited.name == methodName){
            def lines = iVisited.position.startLine .. iVisited.position.endLine
            def nodes = returnNodes?.findAll{ it.line in lines }
            pages = nodes*.value
        }
    }

    /***
     * Identifies all return nodes that returns a string literal.
     */
    @Override
    Object visitReturnNode(ReturnNode iVisited) {
        super.visitReturnNode(iVisited)
        switch (iVisited.value.class){
            case StrNode: //Representing a simple String literal
                def node = (StrNode) iVisited.value
                returnNodes += [line: iVisited.position.startLine, value: node.value]
                break
            case DStrNode: //A string which contains some dynamic elements which needs to be evaluated (introduced by #)
                def name = ""
                iVisited.value.childNodes().each{ c-> if(c instanceof StrNode) name += c.value.trim() }
                def index = name.indexOf("?")
                if(index>0) name = name.substring(0, index)//ignoring params
                if(!name.contains("//")) {
                    returnNodes += [line: iVisited.position.startLine, value: name]
                }
                break
            case FCallNode: //Method call with self as an implicit receiver
            case VCallNode: //Method call without any arguments
            case CallNode: //Method call
                def value = iVisited.value.name
                if(value.contains(RubyUtil.ROUTE_SUFIX)){ //it is a path helper method
                    value -= RubyUtil.ROUTE_SUFIX
                    returnNodes += [line: iVisited.position.startLine, value:value]
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
        extractViewPathFromNode(iVisited)
        return iVisited
    }

    @Override
    Object visitDefsNode(DefsNode iVisited) {
        super.visitDefsNode(iVisited)
        extractViewPathFromNode(iVisited)
        return iVisited
    }

}

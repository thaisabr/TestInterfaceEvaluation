package testCodeAnalyser.ruby

import org.jrubyparser.ast.DefnNode
import org.jrubyparser.ast.DefsNode
import org.jrubyparser.ast.MethodDefNode
import org.jrubyparser.ast.ReturnNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.util.NoopVisitor

import util.Util

class RubyPageVisitor extends NoopVisitor {

    Set<String> pages
    List<String> viewFiles
    String methodName
    def returnNodes //keys: line, value

    public RubyPageVisitor(List<String> viewFiles){
        this.pages = [] as Set
        this.viewFiles = viewFiles
        returnNodes = []
    }

    private extractViewPathFromNode(MethodDefNode iVisited){
        if(iVisited.name == methodName){
            def lines = iVisited.position.startLine .. iVisited.position.endLine
            def nodes = returnNodes?.findAll{ it.line in lines }

            /* se o metodo pode retornar diferentes valores, provavelmente ele é um método de propósito geral, o que significa
            * que nem toda view está associada à tarefa. Ainda assim, aqui está sendo aceito ruído e todas as views serão consideradas.*/
            nodes?.each{ node ->
                def page = Util.findViewPathForRailsProjects(node.value, viewFiles)
                if(page && !page.isEmpty()) pages += page
            }
        }
    }

    @Override
    /***
     * Identifies all return nodes that returns a string literal.
     */
    Object visitReturnNode(ReturnNode iVisited) {
        super.visitReturnNode(iVisited)
        if(iVisited.value instanceof StrNode){ //returns a literal
            def node = (StrNode) iVisited.value
            returnNodes += [line: iVisited.position.startLine, value: node.value]
        }
        return iVisited
    }

    @Override
    /***
     * Identifies the method node of interest to extract view path.
     */
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

package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.Node
import org.jrubyparser.util.NoopVisitor


class RubyGetVisitor extends NoopVisitor {


    static REQUEST_TYPES = ["post", "put", "patch", "delete"]
    Node node
    List<Node> nodes
    List<Node> allNodes

    RubyGetVisitor(Node node, List allNodes){
        this.node = node
        this.nodes = []
        this.allNodes = allNodes
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if(iVisited.name == "get" && !iVisited.position.equals(node.position)) nodes += iVisited
        else if(iVisited.name in REQUEST_TYPES){
            allNodes = allNodes - iVisited
        }
        iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if(iVisited.name == "get" && !iVisited.position.equals(node.position)) nodes += iVisited
        else if(iVisited.name in REQUEST_TYPES){
            allNodes = allNodes - iVisited
        }
        iVisited
    }

}

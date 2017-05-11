package br.ufpe.cin.tan.test.ruby.routes

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.Node
import org.jrubyparser.util.NoopVisitor


class RubyGetPostDeleteVisitor extends NoopVisitor {

    static REQUEST_TYPES = ["put", "patch"]
    Node node
    List<Node> nodes
    List<Node> allNodes
    List<Node> otherNodes

    RubyGetPostDeleteVisitor(Node node, List allNodes) {
        this.node = node
        this.nodes = []
        this.otherNodes = []
        this.allNodes = allNodes
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if (iVisited.name == "get" && iVisited.position != node.position) nodes += iVisited
        else if((iVisited.name == "delete" || iVisited.name == "post") && iVisited.position != node.position) otherNodes += iVisited
        else if (iVisited.name in REQUEST_TYPES) allNodes = allNodes - iVisited
        iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if (iVisited.name == "get" && iVisited.position != node.position) nodes += iVisited
        else if((iVisited.name == "delete" || iVisited.name == "post") && iVisited.position != node.position) otherNodes += iVisited
        else if (iVisited.name in REQUEST_TYPES) allNodes = allNodes - iVisited
        iVisited
    }

}

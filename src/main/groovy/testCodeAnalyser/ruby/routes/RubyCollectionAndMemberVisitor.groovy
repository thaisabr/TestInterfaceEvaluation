package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.Node
import org.jrubyparser.util.NoopVisitor


class RubyCollectionAndMemberVisitor extends NoopVisitor {

    Node node
    Node collectionNode
    Node memberNode
    List invalidNodeRanges
    List<Node> allNodes

    RubyCollectionAndMemberVisitor(Node node, List ranges, List allNodes) {
        this.node = node
        this.invalidNodeRanges = ranges
        this.allNodes = allNodes
        node.accept(this)
    }

    def getMemberValues() {
        def values = []
        if (memberNode) {
            def getVisitor = new RubyGetVisitor(memberNode, allNodes)
            memberNode.accept(getVisitor)
            values = getVisitor.nodes
        }
        return values
    }

    def getCollectionValues() {
        def values = []
        if (collectionNode) {
            def getVisitor = new RubyGetVisitor(collectionNode, allNodes)
            collectionNode.accept(getVisitor)
            values = getVisitor.nodes
        }
        return values
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        def nested = invalidNodeRanges.findAll { iVisited.position.startLine in it }
        if (nested.empty) {
            if (iVisited.name == "collection" && !iVisited.position.equals(node.position)) collectionNode = iVisited
            else if (iVisited.name == "member" && !iVisited.position.equals(node.position)) memberNode = iVisited
        }
        iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        def nested = invalidNodeRanges.findAll { iVisited.position.startLine in it }
        if (nested.empty) {
            if (iVisited.name == "collection" && !iVisited.position.equals(node.position)) collectionNode = iVisited
            else if (iVisited.name == "member" && !iVisited.position.equals(node.position)) memberNode = iVisited
        }
        iVisited
    }

}

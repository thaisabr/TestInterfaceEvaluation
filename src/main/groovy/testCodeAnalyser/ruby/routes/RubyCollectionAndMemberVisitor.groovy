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
        def getAndPostValues = []
        def otherValues = []
        if (memberNode) {
            def getVisitor = new RubyGetPostDeleteVisitor(memberNode, allNodes)
            memberNode.accept(getVisitor)
            getAndPostValues = getVisitor.nodes
            otherValues = getVisitor.otherNodes
        }
        [gets:getAndPostValues, others:otherValues]
    }

    def getCollectionValues() {
        def getAndPostValues = []
        def otherValues = []
        if (collectionNode) {
            def getVisitor = new RubyGetPostDeleteVisitor(collectionNode, allNodes)
            collectionNode.accept(getVisitor)
            getAndPostValues = getVisitor.nodes
            otherValues = getVisitor.otherNodes
        }
        [gets:getAndPostValues, others:otherValues]
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        def nested = invalidNodeRanges.findAll { iVisited.position.startLine in it }
        if (nested.empty) {
            if (iVisited.name == "collection" && iVisited.position != node.position) collectionNode = iVisited
            else if (iVisited.name == "member" && iVisited.position != node.position) memberNode = iVisited
        }
        iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        def nested = this.invalidNodeRanges.findAll { iVisited.position.startLine in it }
        if (nested.empty) {
            if (iVisited.name == "collection" && iVisited.position != node.position) collectionNode = iVisited
            else if (iVisited.name == "member" && iVisited.position != node.position) memberNode = iVisited
        }
        iVisited
    }

}

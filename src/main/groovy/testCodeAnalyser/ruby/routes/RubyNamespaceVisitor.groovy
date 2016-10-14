package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.Node
import org.jrubyparser.util.NoopVisitor


class RubyNamespaceVisitor extends NoopVisitor {

    List<Node> namespaces
    List<Node> others
    List<FCallNode> fcallNodes
    List<CallNode> callNodes

    RubyNamespaceVisitor() {
        namespaces = []
        others = []
        fcallNodes = []
        callNodes = []
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        fcallNodes += iVisited
        return iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if (iVisited.name != "draw") callNodes += iVisited
        return iVisited
    }
}

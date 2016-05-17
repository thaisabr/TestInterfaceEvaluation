package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.Node
import org.jrubyparser.util.NoopVisitor


class RubyNestedResourcesVisitor extends NoopVisitor {

    Node node
    def resources
    def resource

    RubyNestedResourcesVisitor(Node node){
        this.node = node
        this.resources = []
        this.resource = []
    }

    def getResources(){
        def finalResult = []
        def result = resources.sort{ it.position.startLine }.sort{ it.position.startOffset }
        result.each{ r ->
            def others = result - [r]
            def ranges = others.collect{ it.position.startLine..it.position.endLine }
            def nested = ranges.findAll{ r.position.startLine in it }
            if(nested.empty) finalResult += r
        }
        return finalResult
    }

    def getResource(){
        def finalResult = []
        def result = resource.sort{ it.position.startLine }.sort{ it.position.startOffset }
        result.each{ r ->
            def others = result - [r]
            def ranges = others.collect{ it.position.startLine..it.position.endLine}
            def nested = ranges.findAll{ r.position.startLine in it }
            if(nested.empty) finalResult += r
        }
        return finalResult
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if(iVisited.name == "resources" && !iVisited.position.equals(node.position)) resources += iVisited
        else if(iVisited.name == "resource" && !iVisited.position.equals(node.position)) resource += iVisited
        iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if(iVisited.name == "resources" && !iVisited.position.equals(node.position)) resources += iVisited
        else if(iVisited.name == "resource" && !iVisited.position.equals(node.position)) resource += iVisited
        iVisited
    }

}

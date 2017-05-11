package br.ufpe.cin.tan.test.ruby.routes

import org.jrubyparser.ast.ArrayNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.SymbolNode
import org.jrubyparser.util.NoopVisitor
import br.ufpe.cin.tan.util.ruby.RubyConstantData

class RubyNestedVisitor extends NoopVisitor {

    String nameSufix
    String pathValuePrefix
    String argsPrefix
    def routes = [] //name, value, args

    RubyNestedVisitor(String nameSufix, String pathValuePrefix, String argsPrefix) {
        this.nameSufix = nameSufix
        this.pathValuePrefix = pathValuePrefix
        this.argsPrefix = argsPrefix
    }

    @Override
    Object visitArrayNode(ArrayNode iVisited) {
        super.visitArrayNode(iVisited)
        def children = iVisited.childNodes()
        for (int i = 0; i < children.size(); i++) {
            def element = children.get(i)
            if (element instanceof SymbolNode && element.name == "get") {
                def previous = children.get(i - 1)
                def actionName = null
                if (previous instanceof StrNode) actionName = previous.value
                else if (previous instanceof SymbolNode) actionName = previous.name

                if (actionName) {
                    def route = [name: "$actionName$nameSufix", file: RubyConstantData.ROUTES_ID, value: "$pathValuePrefix$actionName", arg: "$argsPrefix$actionName"]
                    routes += route
                }
            }
        }
        iVisited
    }

}

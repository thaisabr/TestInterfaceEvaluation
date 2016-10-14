package testCodeAnalyser.ruby

import org.jrubyparser.ast.*
import org.jrubyparser.util.NoopVisitor

class RubyWhenNodeVisitor extends NoopVisitor {

    Set<String> pages
    List auxiliaryMethods
    String arg
    Set<ConditionalExpression> expressions

    RubyWhenNodeVisitor(List<String> args) {
        this.pages = [] as Set
        this.auxiliaryMethods = []
        if (args && !args.empty) this.arg = args?.first()
        this.expressions = [] as Set
    }

    private static extractStringFromWhenCondition(RegexpNode node) {
        [exp: node.value]
    }

    private static extractStringFromWhenCondition(StrNode node) {
        [exp: node.value]
    }

    private static extractResultNodesFromWhen(WhenNode iVisited) {
        def result = []
        def r1 = iVisited.body.childNodes().findAll { isOfInterest(it) }
        if (!r1.empty) result += r1
        def r2 = iVisited.body.childNodes().findAll { it instanceof NewlineNode }*.childNodes()?.flatten()?.findAll {
            isOfInterest(it)
        }
        if (!r2.empty) result += r2
        result
    }

    private static boolean isOfInterest(org.jrubyparser.ast.Node node) {
        return node instanceof StrNode || node instanceof DStrNode || node instanceof VCallNode ||
                node instanceof CallNode || node instanceof FCallNode
    }

    private static extractIfResult(org.jrubyparser.ast.Node node) {
        null //we cannot deal with a result that is neither a string or method call
    }

    private static extractIfResult(VCallNode node) {
        [name: node.name, isMethod: true]
    }

    private static extractIfResult(CallNode node) {
        [name: node.name, isMethod: true]
    }

    private static extractIfResult(FCallNode node) {
        [name: node.name, isMethod: true]
    }

    private static extractIfResult(StrNode node) {
        [name: node.value, isMethod: false]
    }

    private static extractIfResult(DStrNode node) {
        def name = ""
        node.childNodes().each { c -> if (c instanceof StrNode) name += c.value.trim() }
        [name: name, isMethod: false]
    }

    private extractViewPathFromNodeWithArgs() {
        //checks if there is a specific return for the argument
        def realMatches = expressions.findAll { arg ==~ /${it.expression}/ }
        if (!realMatches.empty) {
            auxiliaryMethods = realMatches.findAll { it.resultIsMethod }*.result
            pages = realMatches.findAll { !it.resultIsMethod }*.result
        }

    }

    private extractViewPathFromNode() {
        auxiliaryMethods = expressions.findAll { it.resultIsMethod }*.result
        pages = expressions.findAll { !it.resultIsMethod }*.result
    }

    @Override
    Object visitWhenNode(WhenNode iVisited) {
        super.visitWhenNode(iVisited)
        def condition = extractStringFromWhenCondition(iVisited.expression)
        def resultNodes = extractResultNodesFromWhen(iVisited)
        def result = resultNodes?.collect { extractIfResult(it) }?.findAll { it != null }?.last()
        if (result) {
            expressions += new ConditionalExpression(line: iVisited.position.startLine, expression: condition.exp,
                    result: result.name, resultIsMethod: result.isMethod)
        }
        if (arg) extractViewPathFromNodeWithArgs()
        else extractViewPathFromNode()
        iVisited
    }

}

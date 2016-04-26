package testCodeAnalyser.ruby

import org.jrubyparser.ast.ArrayNode
import org.jrubyparser.ast.DStrNode
import org.jrubyparser.ast.DefnNode
import org.jrubyparser.ast.DefsNode
import org.jrubyparser.ast.INameNode
import org.jrubyparser.ast.IfNode
import org.jrubyparser.ast.MethodDefNode
import org.jrubyparser.ast.NewlineNode
import org.jrubyparser.ast.RegexpNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.WhenNode
import org.jrubyparser.util.NoopVisitor

import java.nio.charset.StandardCharsets


class RubyConditionalVisit extends NoopVisitor {

    Set<String> pages
    List auxiliaryMethods
    String methodName
    String arg
    Set<ConditionalExpression> expressions

    public RubyConditionalVisit(String methodName, List<String> args){
        this.pages = [] as Set
        this.auxiliaryMethods = []
        this.methodName = methodName
        this.arg = args?.first()
        this.expressions = [] as Set

    }

    private static extractIfResult(Node node){
        null //we cannot deal with a result that is neither a string or method call
    }

    private static extractIfResult(INameNode node){
        [name: node.name, isMethod:true]
    }

    private static extractIfResult(StrNode node){
        [name: node.value, isMethod:false]
    }

    private static extractIfResult(DStrNode node){
        def name = ""
        node.childNodes().each{ c-> if(c instanceof StrNode) name += c.value.trim() }
        [name: name, isMethod:false]
    }

    private static extractStringFromIfEqualCondition(IfNode iVisited){
        def condition = ""
        iVisited?.condition?.childNodes()?.each{ child ->
            if(child instanceof ArrayNode) {
                def aux = (child.childNodes().find { it instanceof StrNode }?.value)
                if(aux) condition = new String(aux.getBytes(), StandardCharsets.UTF_8).trim()
            }
        }
        if(condition.empty) null
        else [exp: condition]
    }

    private static extractStringFromWhenCondition(RegexpNode node){
        [exp: node.value]
    }

    private static extractStringFromWhenCondition(StrNode node){
        [exp: node.value]
    }

    private static extractResultNodesFromWhen(WhenNode iVisited){
        def result = []
        def r1 = iVisited.body.childNodes().findAll{
            it instanceof StrNode || it instanceof DStrNode || it instanceof INameNode
        }
        if(!r1.empty) result += r1
        def r2 = iVisited.body.childNodes().findAll{ it instanceof NewlineNode}*.childNodes()?.flatten()?.findAll{
            it instanceof StrNode || it instanceof DStrNode || it instanceof INameNode
        }
        if(!r2.empty){
            result += r2
        }
        result
    }

    private extractViewPathFromNode(MethodDefNode iVisited){
        if(iVisited.name == methodName){
            def lines = iVisited.position.startLine .. iVisited.position.endLine

            //identifies all possible return values of the method
            def matches = expressions.findAll{ it.line in lines }?.sort{ it.line}

            //checks if there is a specific return for the argument
            def realMatches = matches.findAll{ arg ==~ /${it.expression}/ }
            if(!realMatches.empty){
                auxiliaryMethods = realMatches.findAll{ it.resultIsMethod }*.result
                pages = realMatches.findAll{ !it.resultIsMethod }*.result
            }
        }
    }

    @Override
    Object visitIfNode(IfNode iVisited) {
        super.visitIfNode(iVisited)
        def condition = extractStringFromIfEqualCondition(iVisited)
        if(condition){
            def result = iVisited.thenBody.childNodes().collect{ extractIfResult(it) }.findAll{ it != null }?.last()
            def exp =  new ConditionalExpression(line: iVisited.position.startLine, expression:condition.exp,
                    result:result.name, resultIsMethod: result.isMethod)
            expressions += exp
        }
        iVisited
    }

    @Override
    Object visitWhenNode(WhenNode iVisited) {
        super.visitWhenNode(iVisited)
        def condition = extractStringFromWhenCondition(iVisited.expression)
        def resultNodes = extractResultNodesFromWhen(iVisited)
        def result = resultNodes?.collect{ extractIfResult(it) }?.findAll{ it != null }?.last()
        if(result){
            expressions += new ConditionalExpression(line: iVisited.position.startLine, expression:condition.exp,
                    result:result.name, resultIsMethod: result.isMethod)
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
        iVisited
    }

    @Override
    Object visitDefsNode(DefsNode iVisited) {
        super.visitDefsNode(iVisited)
        extractViewPathFromNode(iVisited)
        iVisited
    }

}

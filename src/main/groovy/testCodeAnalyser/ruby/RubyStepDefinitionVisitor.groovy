package testCodeAnalyser.ruby

import org.jrubyparser.ast.FCallNode
import org.jrubyparser.util.NoopVisitor
import taskAnalyser.StepDefinition
import util.Util

/***
 * Visits source code files looking for step definitions. It is used when a commit changed a step definition without
 * changed any Gherkin file.
 */
class RubyStepDefinitionVisitor extends NoopVisitor {

    String path
    String content
    List<StepDefinition> stepDefinitions

    public RubyStepDefinitionVisitor(String path, String content){
        this.path = path
        this.content = content
        stepDefinitions = []
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if(iVisited.name in Util.STEP_KEYWORDS){
            RubyStepRegexVisitor regexVisitor = new RubyStepRegexVisitor(path)
            iVisited.accept(regexVisitor)
            if(!regexVisitor.regexs.empty){
                def regex = regexVisitor.regexs.first()
                def value = iVisited.name + "(${regex.value})"
                def body = content?.readLines()?.getAt(iVisited.position.startLine+1..iVisited.position.endLine-1)
                stepDefinitions += new StepDefinition(path: path, value:value, regex:regex.value, line: iVisited.position.startLine,
                        end:iVisited.position.endLine, body:body)
            }
        }

        return iVisited
    }
}

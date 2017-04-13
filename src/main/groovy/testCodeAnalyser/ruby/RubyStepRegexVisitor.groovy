package testCodeAnalyser.ruby

import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.RegexpNode
import org.jrubyparser.util.NoopVisitor
import testCodeAnalyser.StepRegex
import util.ConstantData

import java.nio.charset.StandardCharsets

/***
 * Visits step definition files looking for regex expressions. The regex is used to match steps in Gherkin files and
 * step definitions.
 */
class RubyStepRegexVisitor extends NoopVisitor {

    List<StepRegex> regexs
    String path

    RubyStepRegexVisitor(String path) {
        this.path = path
        regexs = []
    }

    private static boolean isStepDefinitionNode(RegexpNode node) {
        if (node.grandParent instanceof FCallNode && node.grandParent.name in ConstantData.ALL_STEP_KEYWORDS
                && node.grandParent.position.startLine == node.position.startLine) true
        else false
    }

    @Override
    Object visitRegexpNode(RegexpNode iVisited) {
        super.visitRegexpNode(iVisited)
        if (isStepDefinitionNode(iVisited)) {
            regexs += new StepRegex(path: path, value: new String(iVisited.value.getBytes(), StandardCharsets.UTF_8),
                    line: iVisited.position.startLine)
        }
        return iVisited
    }

}

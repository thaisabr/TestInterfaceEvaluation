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

    public RubyStepRegexVisitor(String path) {
        this.path = path
        regexs = []
    }

    private static boolean isStepDefinitionNode(RegexpNode node) {
        def keywords = ConstantData.STEP_KEYWORDS + ConstantData.STEP_KEYWORDS_PT + ConstantData.STEP_KEYWORDS_DE
        if (node.grandParent instanceof FCallNode && node.grandParent.name in keywords
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

package br.ufpe.cin.tan.test.ruby

import br.ufpe.cin.tan.test.StepRegex
import br.ufpe.cin.tan.util.ConstantData
import org.jrubyparser.ast.*
import org.jrubyparser.util.NoopVisitor

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

    private static boolean isStepDefinitionNode(DRegexpNode node) {
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

    @Override
    Object visitDRegxNode(DRegexpNode iVisited) {
        super.visitDRegxNode(iVisited)
        if (isStepDefinitionNode(iVisited)) {
            def value = ""
            iVisited.childNodes()?.each {
                if (it instanceof StrNode) value += "${it.value}"
                else if (it instanceof EvStrNode) value += ".+"
            }
            if (!value.empty) regexs += new StepRegex(path: path, value: value, line: iVisited.position.startLine)
        }
        return iVisited
    }
}

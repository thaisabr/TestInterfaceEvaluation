package testCodeAnalyser.ruby

import org.jrubyparser.ast.RegexpNode
import org.jrubyparser.util.NoopVisitor
import testCodeAnalyser.StepRegex

/***
 * Visits step definition files looking for regex expressions. The regex is used to match steps in Gherkin files and
 * step definitions.
 */
class RubyStepRegexVisitor extends NoopVisitor {

    List<StepRegex> regexs
    String path

    public RubyStepRegexVisitor(String path){
        this.path = path
        regexs = []
    }

    @Override
    Object visitRegexpNode(RegexpNode iVisited) {
        super.visitRegexpNode(iVisited)
        regexs += new StepRegex(path: path, value:iVisited.value, line: iVisited.position.startLine)
    }

}

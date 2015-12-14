package testCodeAnalyser.ruby

import org.jrubyparser.ast.RegexpNode
import org.jrubyparser.util.NoopVisitor
import testCodeAnalyser.StepRegex


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

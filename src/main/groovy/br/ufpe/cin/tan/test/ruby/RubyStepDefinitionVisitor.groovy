package br.ufpe.cin.tan.test.ruby

import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.util.ConstantData
import groovy.util.logging.Slf4j
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.util.NoopVisitor

/***
 * Visits source code files looking for step definitions. It is used when a commit changed a step definition without
 * changed any Gherkin file.
 */
@Slf4j
class RubyStepDefinitionVisitor extends NoopVisitor {

    String path
    List<String> content
    List<StepDefinition> stepDefinitions

    RubyStepDefinitionVisitor(String path, String content) {
        this.path = path
        this.content = content.readLines()
        stepDefinitions = []
    }

    private static ignoreArgs(String value) {
        if (!value && value.empty) return ""
        def result = ""
        def pipeIndex1 = value.indexOf("|")
        if (pipeIndex1 < 0) result = value
        else {
            def aux = value.substring(pipeIndex1 + 1)
            def pipeIndex2 = aux.indexOf("|")
            if (pipeIndex2 < 0) result = value
            else result = aux.substring(pipeIndex2 + 1)
        }
        result.trim()
    }

    private static extractBodyStyle1(String text) {
        def init = " do"
        def end = "end"
        def index1 = text.indexOf(init)
        def index2 = text.lastIndexOf(end)
        if (index1 < 0 || index2 < 0) return null
        def i = index1 + (init.size() - 1)
        def value = text.substring(i, index2)
        ignoreArgs(value)
    }

    private static extractBodyStyle2(String text) {
        def index1 = text.indexOf("{")
        def index2 = text.lastIndexOf("}")
        if (index1 > 0 && index2 > 0) {
            def i = index1 + 1
            def value = text.substring(i, index2)
            ignoreArgs(value)
        } else null
    }

    private extractNoLineBody(int startLine) {
        def body = []
        def text = content.get(startLine)
        def aux = extractBodyStyle1(text)
        if (aux == null) aux = extractBodyStyle2(text)
        if (aux != null) body += aux
        else {
            log.error "Error to extract body from step (File: $path, start line:${startLine + 1})"
        }
        body
    }

    private extractOneLineBody(int startLine, int endLine) {
        def body = []
        def text = content.subList(startLine, endLine + 1)
        def line = text.join("\n")
        def aux = extractBodyStyle1(line)
        if (aux == null) aux = extractBodyStyle2(line)
        if (aux != null) body += aux
        else {
            log.error "Error to extract body from step (File: $path, start line:${startLine + 1})"
        }
        body
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if (iVisited.name in ConstantData.ALL_STEP_KEYWORDS) {
            RubyStepRegexVisitor regexVisitor = new RubyStepRegexVisitor(path)
            iVisited.accept(regexVisitor)
            if (!regexVisitor.regexs.empty) {
                def regex = regexVisitor.regexs.first()
                def value = iVisited.name + "(${regex.value})"
                def startLine = iVisited.position.startLine
                def endLine = iVisited.position.endLine
                def body

                if (startLine == endLine) { //body has no line
                    body = extractNoLineBody(startLine)
                } else if ((endLine - startLine) == 1) { //body has 1 line
                    body = extractOneLineBody(startLine, endLine)
                } else {
                    body = content[startLine + 1..endLine - 1]
                }

                if (!body.empty) {
                    stepDefinitions += new StepDefinition(path: path, value: value, regex: regex.value,
                            line: iVisited.position.startLine, end: iVisited.position.endLine, body: body)
                }
            }
        }

        return iVisited
    }
}

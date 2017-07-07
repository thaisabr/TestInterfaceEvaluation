package br.ufpe.cin.tan.test.groovy

import br.ufpe.cin.tan.util.groovy.GroovyUtil
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.control.SourceUnit

class GroovyPageVisitor extends ClassCodeVisitorSupport {
    SourceUnit source
    Set<String> pages
    List<String> viewFiles
    static final PAGE_FIELD = "url" //name convention

    GroovyPageVisitor(List<String> viewFiles) {
        this.source = null
        this.pages = [] as Set
        this.viewFiles = viewFiles
    }

    @Override
    protected SourceUnit getSourceUnit() {
        source
    }

    @Override
    void visitField(FieldNode node) {
        super.visitField(node)
        if (node.name == PAGE_FIELD && node.initialValueExpression.value != "") {
            def name = GroovyUtil.findViewPathForGrailsProjects(node.initialValueExpression.value, viewFiles)
            if (!name.isEmpty()) pages += name
        }
    }
}

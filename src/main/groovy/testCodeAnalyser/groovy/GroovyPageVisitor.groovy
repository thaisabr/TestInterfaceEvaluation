package testCodeAnalyser.groovy

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.control.SourceUnit

import util.Util

class GroovyPageVisitor extends ClassCodeVisitorSupport {
    SourceUnit source
    Set<String> pages
    List<String> viewFiles
    static final PAGE_FIELD = "url" //name convention

    public GroovyPageVisitor(List<String> viewFiles){
        this.source = null
        this.pages = [] as Set
        this.viewFiles = viewFiles
    }

    @Override
    protected SourceUnit getSourceUnit() {
        source
    }

    @Override
    public void visitField(FieldNode node){
        super.visitField(node)
        if(node.name==PAGE_FIELD && node.initialValueExpression.value != ""){
            def name = Util.findViewPath(node.initialValueExpression.value, viewFiles)
            if(!name.isEmpty()) pages += name
        }
    }
}

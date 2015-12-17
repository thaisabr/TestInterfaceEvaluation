package testCodeAnalyser.ruby

import org.jrubyparser.ast.ClassNode
import org.jrubyparser.ast.MethodNameNode
import org.jrubyparser.ast.ModuleNode
import org.jrubyparser.util.NoopVisitor

/***
 * Finds all method definition in a file.
 */
class RubyMethodDefinitionVisitor extends NoopVisitor {

    Set methods = [] //keys: name, className, path
    String path

    @Override
    /***
     * Visits a class node and extracts all its method definitions.
     */
    Object visitClassNode(ClassNode iVisited) {
        super.visitClassNode(iVisited)
        iVisited.methodDefs.each{ method ->
            def entry = methods.find{ it.name== method.name && it.className==null && it.path == path }
            if(entry) entry.className = iVisited.CPath.name
            else methods += [name:method.name, className:iVisited.CPath.name, path:path]
        }
        iVisited
    }

    @Override
    /***
     * Visits a module node and extracts all its method definitions.
     */
    Object visitModuleNode(ModuleNode iVisited) {
        super.visitModuleNode(iVisited)
        iVisited.methodDefs.each{ method ->
           def entry = methods.find{ it.name== method.name && it.className==null && it.path == path }
            if(entry) entry.className = iVisited.CPath.name
            else methods += [name:method.name, className:iVisited.CPath.name, path:path]
        }
        iVisited
    }

    @Override
    /***
     * Visits all method definition nodes.
     */
    Object visitMethodNameNode(MethodNameNode iVisited) {
        super.visitMethodNameNode(iVisited)
        def entries = methods.findAll{ it.name== iVisited.name && it.path == path }
        if(!entries) methods += [name:iVisited.name, className:null, path:path]
        iVisited
    }

}

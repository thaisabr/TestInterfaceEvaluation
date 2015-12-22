package testCodeAnalyser.ruby

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.ConstNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.util.NoopVisitor

import util.Util

class RSpecTestDefinitionVisitor extends NoopVisitor {

    List<String> KEYWORDS =["describe"]
    List<String> projectFiles
    def productionClass //keywords: name, path
    Set tests //keywords: name, path, lines
    String path

    RSpecTestDefinitionVisitor(String localPath, String path){
        this.tests = []
        this.projectFiles = Util.findFilesFromDirectoryByLanguage(localPath)
        this.path = path
    }

    private configureProductionClass(def value){
        if(!productionClass){
            def index1 = this.path.lastIndexOf(File.separator)
            def index2 = this.path.indexOf("_spec")

            if(index1!=-1 && index2!=-1){
                def name = this.path.substring(index1+1,index2)+Util.RUBY_EXTENSION
                //def name = File.separator+value.name.toLowerCase()+Util.RUBY_EXTENSION
                def path = Util.findAllProductionFiles(projectFiles).find{ it.endsWith(name) }
                productionClass = [name:value.name, path:path]
            }
            else{
                productionClass = [name:value.name, path:null]
            }
        }
    }

    Set getTests(){
        def result = tests.sort{ it.lines.size() }
        if(!result.isEmpty()) {
            def outterDescribe = tests.last()
            configureProductionClass(outterDescribe)
            result.remove(outterDescribe) //the one that englobes all others
        }
        return result
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)

        if(iVisited.name in KEYWORDS ){
            def name = ""
            if(iVisited.args.last instanceof StrNode){
                name = iVisited.args.last.value
            }
            else if(iVisited.args.last instanceof ConstNode){
                name = iVisited.args.last.name
            }
            tests += [name:name, path:path, lines:iVisited.position.startLine .. iVisited.position.endLine] //delimits method block
        }

        return iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)

        if(iVisited.name == "describe" && iVisited.receiver?.name == "RSpec"){
            println "Chamada do tipo 'RSpec.describe'!!!"
        }
        return iVisited
    }

}

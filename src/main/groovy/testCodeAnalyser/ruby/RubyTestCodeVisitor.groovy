package testCodeAnalyser.ruby

import org.jrubyparser.CompatVersion
import org.jrubyparser.Parser
import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.Colon2ConstNode
import org.jrubyparser.ast.ConstNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.VCallNode
import org.jrubyparser.parser.ParserConfiguration
import org.jrubyparser.util.NoopVisitor
import taskAnalyser.TaskInterface
import testCodeAnalyser.TestCodeVisitor
import util.Util

class RubyTestCodeVisitor extends NoopVisitor implements TestCodeVisitor {

    TaskInterface taskInterface
    List<String> projectFiles
    Set methods //keys: name, class, path; todos os métodos do projeto; usado para identificar origem dos métodos chamados

    public RubyTestCodeVisitor(String repositoryPath){
        this.projectFiles = Util.findFilesFromDirectoryByLanguage(repositoryPath)
        fillMethods()
        this.taskInterface = new TaskInterface()
    }

    /***
     * ESSE MÉTODO DEVE SER USADO DESDE SEMPRE, COMO É FEITO COM AS EXPRESSÕES REGULARES PARA O PROJETO. LOGO,
     * DEVE TER UM CONTRATO PARA FORÇAR A IMPLEMENTAÇÃO DELE E ESSE CONTRATO DEVE ESTAR EM OUTRA CLASSE.
     * Finds all methods declaration in project
     * @param path project local folder
     */
    private fillMethods(){
        RubyMethodDefinitionVisitor visitor = new RubyMethodDefinitionVisitor()

        Parser rubyParser = new Parser()
        CompatVersion version = CompatVersion.RUBY2_0
        ParserConfiguration config = new ParserConfiguration(0, version)

        projectFiles?.each{ file ->
            visitor.path = file
            FileReader reader = new FileReader(file)
            def node = rubyParser.parse("<code>", reader, config)
            node.accept(visitor)
            reader.close()
        }

        this.methods = visitor.methods
    }

    /*@Override
    /**
     * Global scope node (::FooBar).  This is used to gain access to the global scope (that of the
     * Object class) when referring to a constant or method.
     *
    Object visitColon3Node(Colon3Node iVisited) {
        super.visitColon3Node(iVisited)
        println "Colon3Node: ${iVisited.name}"
    }

    /*@Override
    /**
     * Represents a '::' constant access or method call (Java::JavaClass).
     *
    Object visitColon2Node(Colon2Node iVisited) {
        super.visitColon2Node(iVisited)
        println "Colon2Node: ${iVisited.name}"
    }*/

    @Override
    /**
     * A method or operator call.
     */
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)

        //println "Chamada de metodo!"
        /*iVisited.properties.each{ k, v ->
            println "$k: $v"
        }*/

        switch (iVisited.receiver.class){
            case ConstNode: //representa uma constante; aqui, quando o método é static, chamado usando o nome da classe
            case Colon2ConstNode: //método de superclasse
                def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
                taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file:path]
                break
            case FCallNode: //representa chamada de método com self como receptor
            case VCallNode: //representa chamada de método que nao tem lista de parametros
            case CallNode: //representa chamada de método "convencional"

                /*def receiver = methods.findAll{ it.name == iVisited.name }
                if(receiver) {
                    receiver.each {
                        taskInterface.methods += [name: iVisited.name, type: it.className, file: it.path]
                    }
                }
                else{
                    taskInterface.methods += [name: iVisited.name, type: null, file: null]
                }*/
                break
            default:
                //println "--------------------------------------------------------------------"
                //println "RECEIVER DEFAULT! Receiver type: ${iVisited.receiver.class}"
                /*iVisited.receiver.properties.each{ k, v ->
                    println "$k: $v"
                }
                println "--------------------------------------------------------------------"*/
                break
        }
    }

    @Override
    /**
     * Represents a method call with self as an implicit receiver.
     */
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if(!(iVisited.name in Util.STEP_KEYWORDS)){
            def receiverName = methods.findAll{ it.name == iVisited.name }
            if(!receiverName) taskInterface.methods += [name: iVisited.name, type: "self", file: null]
            receiverName.each{
                taskInterface.methods += [name: iVisited.name, type: it.className, file: it.path]
            }
        }
    }

    @Override
    /**
     * RubyMethod call without any arguments (sem lista de parâmetros delimitada por parênteses)
     */
    Object visitVCallNode(VCallNode iVisited) {
        super.visitVCallNode(iVisited)
        def receiverName = methods.findAll{ it.name == iVisited.name }
        if(!receiverName) taskInterface.methods += [name: iVisited.name, type: null, file: null]
        receiverName.each{
            taskInterface.methods += [name: iVisited.name, type: it.className, file: it.path]
        }
    }

}

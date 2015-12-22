package testCodeAnalyser.ruby

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.Colon2ConstNode
import org.jrubyparser.ast.Colon3Node
import org.jrubyparser.ast.ConstNode
import org.jrubyparser.ast.DVarNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.GlobalVarNode
import org.jrubyparser.ast.InstVarNode
import org.jrubyparser.ast.VCallNode
import org.jrubyparser.util.NoopVisitor
import taskAnalyser.TaskInterface
import testCodeAnalyser.TestCodeVisitor
import util.Util

class RubyTestCodeVisitor extends NoopVisitor implements TestCodeVisitor {

    TaskInterface taskInterface
    List<String> projectFiles
    List<String> viewFiles
    String lastVisitedFile
    Set methods //keys: name, class, path; todos os métodos do projeto; usado para identificar origem dos métodos chamados

    def productionClass //keys: name, path; used when visiting RSpec files; try a better way to represent it!

    public RubyTestCodeVisitor(List<String> projectFiles, String currentFile, Set methods){
        this.projectFiles = projectFiles
        this.viewFiles = projectFiles.findAll{ it.contains(Util.VIEWS_FILES_RELATIVE_PATH) }
        this.taskInterface = new TaskInterface()
        this.lastVisitedFile = currentFile
        this.methods = methods
    }

    @Override
    /**
     * A method or operator call.
     */
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)

        //println "Method call: ${iVisited.name} (${iVisited.position.startLine});   Receptor: ${iVisited.receiver.name}"

        /* unit test file */
        if(productionClass && iVisited.receiver.properties.containsKey("name") && iVisited.receiver.name == "subject") {
            taskInterface.methods += [name: iVisited.name, type: productionClass.name, file: productionClass.path]
        }
        else {
            switch (iVisited.receiver.class) {
                case ConstNode: //constant expression; static method call; example: User.find_by_email("trashmail@meurio.org.br")
                    def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
                    taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
                    break
                case Colon3Node: //superclass method
                case Colon2ConstNode: //call example: "ActionMailer::Base.deliveries"
                    def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
                    taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
                    path = Util.getClassPathForRuby(iVisited.receiver.leftNode.name, projectFiles)
                    taskInterface.classes += [name: iVisited.receiver.leftNode.name, file: path]
                    break
                case InstVarNode: //instance variable, example: @user.should_not be_nil
                    def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
                    if (path) {
                        taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
                        /* verifica se o método realmente existe no arquivo. Tem método que é incluido dinamicamente por frameworks
                        * e podem não aparecer na fase em que a AST é gerada. Estou deixando essa verificação apenas para
                        * deixar documentado que isso pode acontecer. */
                        def receiver = methods.findAll { it.name == iVisited && it.path == path }
                        if (receiver.isEmpty()) {
                            println "The method called by instance variable was not found: " +
                                    "${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine})"
                        }
                    } else {
                        taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
                    }
                    break
                case DVarNode: //dynamic variable (e.g. block scope local variable)
                    [name: iVisited.name, type: iVisited.receiver.name, file: null]
                    break
                case GlobalVarNode: //access to a global variable; usage of "?"
                    if (!iVisited.receiver.name == "?") {
                        println "CALL BY GLOBAL VARIABLE \nPROPERTIES:"
                        iVisited.receiver.properties.each { k, v -> println "$k: $v" }
                    }
                    break
                case FCallNode: //method call with self as an implicit receiver
                case VCallNode: //method call without any arguments
                case CallNode: //method call
                    def receiver = methods.findAll { it.name == iVisited.name }
                    if (receiver.isEmpty()) {
                        taskInterface.methods += [name: iVisited.name, type: null, file: null]
                    } else {
                        receiver.each {
                            taskInterface.methods += [name: iVisited.name, type: it.className, file: it.path]
                        }
                    }
                    break
                default:
                    println "RECEIVER DEFAULT! Receiver type: ${iVisited.receiver.class}"
            }
        }
        iVisited
    }

    @Override
    /**
     * Represents a method call with self as an implicit receiver.
     */
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)

        //println "Method call: ${iVisited.name} (${iVisited.position.startLine});   Receptor: self"

        if(iVisited.name == "visit"){ //indicates de view
            /* if the argument is a literal, the view was found */
            if(iVisited.args.last.class == ConstNode){
                def name = Util.findViewPathForRailsProjects(iVisited.args.last.name, viewFiles)
                taskInterface.referencedPages += name
            }
            /* If the argument is a method call that returns a literal, we understand the view was found.
               Otherwise, it is not possible to extract it and find the view. */
            else if(iVisited.args.last.class == VCallNode || iVisited.args.last.class == CallNode ||
                    iVisited.args.last.class == FCallNode){
                def methodsToVisit = methods.findAll{ it.name == iVisited.args.last.name }
                methodsToVisit?.each{ m ->
                    taskInterface.calledPageMethods += [name: iVisited.name, arg:m.name, file:m.path]
                }
            }

        } else if(!(iVisited.name in Util.STEP_KEYWORDS)){
            taskInterface.methods += [name: iVisited.name, type: "self", file: lastVisitedFile]
        }
        iVisited
    }

    @Override
    /**
     * RubyMethod call without any arguments
     */
    Object visitVCallNode(VCallNode iVisited) {
        super.visitVCallNode(iVisited)

        //println "Method call: ${iVisited.name} (${iVisited.position.startLine})"

        def receiverName = methods.findAll{ it.name == iVisited.name }
        if(!receiverName) taskInterface.methods += [name: iVisited.name, type: null, file: null]
        receiverName.each{
            taskInterface.methods += [name: iVisited.name, type: it.className, file: it.path]
        }
        iVisited
    }

}

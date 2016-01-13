package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.ArrayNode
import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.CaseNode
import org.jrubyparser.ast.Colon2ConstNode
import org.jrubyparser.ast.Colon2Node
import org.jrubyparser.ast.Colon3Node
import org.jrubyparser.ast.ConstNode
import org.jrubyparser.ast.DStrNode
import org.jrubyparser.ast.DVarNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.FixnumNode
import org.jrubyparser.ast.GlobalVarNode
import org.jrubyparser.ast.IfNode
import org.jrubyparser.ast.InstVarNode
import org.jrubyparser.ast.LocalVarNode
import org.jrubyparser.ast.NewlineNode
import org.jrubyparser.ast.OrNode
import org.jrubyparser.ast.SelfNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.VCallNode
import org.jrubyparser.util.NoopVisitor
import taskAnalyser.TaskInterface
import testCodeAnalyser.TestCodeVisitor
import util.Util

@Slf4j
class RubyTestCodeVisitor extends NoopVisitor implements TestCodeVisitor {

    TaskInterface taskInterface
    List<String> projectFiles
    List<String> viewFiles
    String lastVisitedFile
    Set methods //keys: name, args, path; all methods from project

    def productionClass //keys: name, path; used when visiting RSpec files; try a better way to represent it!

    public RubyTestCodeVisitor(List<String> projectFiles, String currentFile, Set methods){
        this.projectFiles = projectFiles
        this.viewFiles = projectFiles.findAll{ it.contains(Util.VIEWS_FILES_RELATIVE_PATH+File.separator) }
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
                case Colon2Node: //Represents a '::' constant access or method call (Java::JavaClass)
                case Colon2ConstNode: //call example: "ActionMailer::Base.deliveries"
                    def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
                    taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
                    path = Util.getClassPathForRuby(iVisited.receiver.leftNode.name, projectFiles)
                    taskInterface.classes += [name: iVisited.receiver.leftNode.name, file: path]
                    break
                case Colon3Node: //Global scope node (::FooBar).  This is used to gain access to the global scope (that of the Object class) when referring to a constant or method.
                    def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
                    taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
                    break
                case SelfNode: //Represents 'self' keyword
                    log.info "SELF_NODE: ${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine})"
                    taskInterface.methods += [name: iVisited.name, type: "self", file: lastVisitedFile]
                    break
                case LocalVarNode: //Access a local variable
                case InstVarNode: //instance variable, example: @user.should_not be_nil
                    def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
                    if (path) {
                        taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
                        /* verifica se o método realmente existe no arquivo. Tem método que é incluido dinamicamente por frameworks
                        * e podem não aparecer na fase em que a AST é gerada. Estou deixando essa verificação apenas para
                        * deixar documentado que isso pode acontecer. */
                        def receiver = methods.findAll { it.name == iVisited && it.path == path }
                        if (receiver.isEmpty()) {
                            log.warn "The method called by instance variable was not found: " +
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
                        log.info "CALL BY GLOBAL VARIABLE \nPROPERTIES:"
                        iVisited.receiver.properties.each { k, v -> log.info "$k: $v" }
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
                case ArrayNode: //Represents an array. This could be an array literal, quoted words or some args stuff.
                    taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
                    break
                case NewlineNode: //A new (logical) source code line
                    // the found situation does not make sense: (DB.tables - [:schema_migrations]).each { |table| DB[table].truncate }
                case StrNode: //Representing a simple String literal
                case DStrNode: //A string which contains some dynamic elements which needs to be evaluated (introduced by #)
                case FixnumNode: //Represents an integer literal
                case OrNode: //represents '||' (or) statements
                case IfNode: //example: if @current_inventory_pool
                case CaseNode: // A Case statement.  Represents a complete case statement, including the body with its when statements.
                    /* Example:
                       Url: https://github.com/leihs/leihs/blob/6dbcbfa63af065d6554889d015b08c9e2a7efce3
                       File: features/step_definitions/borrow/rueckgaben_abholungen_steps.rb (line 4)

                    find("a[href*='borrow/#{case visit_type
                                                  when "Rückgaben"
                                                    "returns"
                                                  when "Abholungen"
                                                    "to_pick_up"
                                                  end}'] > span", text: case visit_type
                                                                        when "Rückgaben"
                                                                          @current_user.visits.take_back
                                                                        when "Abholungen"
                                                                          @current_user.visits.hand_over
                                                                        end.count.to_s)
                    */
                    break
                default:
                    log.warn "RECEIVER DEFAULT! called: ${iVisited.name} $lastVisitedFile (${iVisited.position.startLine}); " +
                            "Receiver type: ${iVisited.receiver.class}"
                    //log.warn "RECEIVER DEFAULT! Receiver type: ${iVisited.receiver.class}"
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
        } else if(!(iVisited.name in Util.STEP_KEYWORDS) && !(iVisited.name in  Util.STEP_KEYWORDS_PT) ){
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

package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import  org.jrubyparser.ast.Node
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

    RubyTestCodeVisitor(List<String> projectFiles, String currentFile, Set methods){
        this.projectFiles = projectFiles
        this.viewFiles = projectFiles.findAll{ it.contains(Util.VIEWS_FILES_RELATIVE_PATH+File.separator) }
        this.taskInterface = new TaskInterface()
        this.lastVisitedFile = currentFile
        this.methods = methods
    }

    private registryMethodCall(CallNode iVisited){
        def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
        if(path) taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
    }

    private registryMethodCallFromUnknownReceiver(Node iVisited){
        def matches = methods.findAll { it.name == iVisited.name }
        if(matches.empty) taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
        else matches.each{  taskInterface.methods += [name: iVisited.name, type: Util.getClassName(it.path), file: it.path] }
    }

    private registryMethodCallFromSelf(Node iVisited){
        if(lastVisitedFile.contains(Util.VIEWS_FILES_RELATIVE_PATH)){
            def index = lastVisitedFile.lastIndexOf(File.separator)
            taskInterface.methods += [name: iVisited.name, type:lastVisitedFile.substring(index+1), file:lastVisitedFile]
        } else {
            taskInterface.methods += [name: iVisited.name, type:Util.getClassName(lastVisitedFile), file:lastVisitedFile]
        }
    }

    private registryMethodCallFromInstanceVariable(CallNode iVisited){
        def path = Util.getClassPathForRuby(iVisited.receiver.name, projectFiles)
        if (path) {
            /* verifica se o método realmente existe no arquivo. Tem método que é incluido dinamicamente por frameworks
                * e podem não aparecer na fase em que a AST é gerada. Estou deixando essa verificação apenas para
                * deixar documentado que isso pode acontecer. */
            def matches = methods.findAll { it.name == iVisited.name && it.path == path }
            if (matches.empty) {
                registryClassUsageUsingFilename(path)
                if(iVisited.name!="should" && iVisited.name!="should_not") { //ignore test methods
                    log.warn "The method called by instance variable was not found: " +
                            "${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine + 1})"
                    /* de fato, quando cai aqui sao metodos para recuperar propriedades em sua maioria. Por exemplo:
                    * @mobilization.hashtag, que na verdade eh getHashTag e eh gerado pelo rails;
                    * mobilization.save!, save eh provido pelo rails tambem.*/
                }
            } else{
                //log.info "The method called by instance variable was found: " +
                //        "${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine + 1})"
                taskInterface.methods += [name: iVisited.name, type: Util.getClassName(path), file: path]
            }
        } else { //nao caiu nenhuma vez aqui para o projeto meurio
            //log.warn "The type of instance variable was not found: " +
            //        "${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine + 1})"
            taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
        }
    }

    private registryClassUsage(String name){
        def path = Util.getClassPathForRuby(name, projectFiles)
        if(path) taskInterface.classes += [name: name, file: path]
    }

    private registryClassUsageUsingFilename(String path){
        if(path.contains(Util.VIEWS_FILES_RELATIVE_PATH)){
            def index = path.lastIndexOf(File.separator)
            taskInterface.classes += [name:path.substring(index+1), file:path]
        } else {
            taskInterface.classes += [name:Util.getClassName(path), file:path]
        }
    }

    /**
     * A method or operator call.
     */
    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)

        //println "Method call: ${iVisited.name} (${iVisited.position.startLine});   Receptor: ${iVisited.receiver.name}"

        /* unit test file */
        if(productionClass && iVisited.receiver.properties.containsKey("name") && iVisited.receiver.name == "subject") {
            taskInterface.methods += [name: iVisited.name, type: productionClass.name, file: productionClass.path]
        }
        else {
            switch (iVisited.receiver.class) {
                case Colon3Node: //Global scope node (::FooBar).  This is used to gain access to the global scope (that of the Object class) when referring to a constant or method.
                case ConstNode: //constant expression; static method call; example: User.find_by_email("trashmail@meurio.org.br")
                    registryMethodCall(iVisited)
                    break
                case Colon2Node: //Represents a '::' constant access or method call (Java::JavaClass)
                case Colon2ConstNode: //call example: "ActionMailer::Base.deliveries"
                    registryMethodCall(iVisited)
                    registryClassUsage(iVisited.receiver.leftNode.name)
                    break
                case SelfNode: //Represents 'self' keyword
                    log.info "SELF_NODE: ${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine+1})"
                    registryMethodCallFromSelf(iVisited)
                    break
                case LocalVarNode: //Access a local variable
                case InstVarNode: //instance variable, example: @user.should_not be_nil
                    //log.info "CHAMADA DE METODO COM INST_VAR_NODE"
                    registryMethodCallFromInstanceVariable(iVisited)
                    break
                case GlobalVarNode: //access to a global variable; usage of "?"
                    log.warn "CALL BY GLOBAL VARIABLE \nPROPERTIES:"
                    iVisited.receiver.properties.each { k, v -> log.warn "$k: $v" }
                    if (!iVisited.receiver.name == "?") log.warn "GLOBAL VARIABLE IS '?'"
                    break
                case DVarNode: //dynamic variable (e.g. block scope local variable)
                case FCallNode: //method call with self as an implicit receiver
                case VCallNode: //method call without any arguments
                case CallNode: //method call
                    registryMethodCallFromUnknownReceiver(iVisited)
                    break
                case ArrayNode: //Represents an array. This could be an array literal, quoted words or some args stuff.
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
                    log.warn "RECEIVER DEFAULT! called: ${iVisited.name} $lastVisitedFile (${iVisited.position.startLine+1}); " +
                            "Receiver type: ${iVisited.receiver.class}"
            }
        }
        iVisited
    }

    /**
     * Represents a method call with self as an implicit receiver.
     */
    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)

        if(iVisited.name == "visit"){ //indicates de view
            /* if the argument is a literal, the view was found */
            if(iVisited.args.last.class == ConstNode){
                def name = Util.findViewPathForRailsProjects(iVisited.args.last.name, viewFiles)
                if(name) taskInterface.referencedPages += name
            }
            /* If the argument is a method call that returns a literal, we understand the view was found.
               Otherwise, it is not possible to extract it and find the view. */
            else if(iVisited.args.last.class == VCallNode || iVisited.args.last.class == CallNode ||
                    iVisited.args.last.class == FCallNode){ //caso da chamada visit to_url(arg1)
                def methodsToVisit = methods.findAll{ it.name == iVisited.args.last.name }
                methodsToVisit?.each{ m ->
                    taskInterface.calledPageMethods += [name: iVisited.name, arg:m.name, file:m.path]
                }
            }
        } else if(iVisited.name == "expect"){ //alternative for should and should_not
            def argClass = iVisited?.args?.last?.class
            if(argClass && argClass==InstVarNode){
                def name = iVisited.args.last.name
                name = name.toUpperCase().getAt(0) + name.substring(1)
                registryClassUsage(name)
                log.info "expect parameter: $name - $lastVisitedFile (${iVisited.position.startLine+1})"
            } else log.info "expect no parameter!!! Method call: ${iVisited.name} (${iVisited.position.startLine})"

        } else if(!(iVisited.name in Util.STEP_KEYWORDS) && !(iVisited.name in  Util.STEP_KEYWORDS_PT) ){
            //o metodo auxiliar cuja chamada pode ser usada como argumento para visit pode cair aqui... ou seja,
            //ele é considerado como argumento e como chamada absoluta
            registryMethodCallFromSelf(iVisited)
        }
        iVisited
    }

    /**
     * RubyMethod call without any arguments
     */
    @Override
    Object visitVCallNode(VCallNode iVisited) {
        super.visitVCallNode(iVisited)
        registryMethodCallFromUnknownReceiver(iVisited)
        iVisited
    }

}

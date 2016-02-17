package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.HashNode
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
import org.jrubyparser.ast.SymbolNode
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

    RubyTestCodeVisitor(String currentFile){ //test purpose only
        this.taskInterface = new TaskInterface()
        projectFiles = []
        viewFiles = []
        lastVisitedFile = currentFile
    }

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

    private static int countArgsMethodCall(CallNode iVisited){
        def counter = 0
        iVisited?.args?.childNodes()?.each{ child ->
            if(child instanceof HashNode && child?.listNode?.size()>0){
                counter += child.listNode.childNodes().findAll{ it instanceof SymbolNode}?.size()
            } else counter++
        }
        counter
    }

    private searchForMethodMatch(Node iVisited){
        def matches = []
        def argsCounter = countArgsMethodCall((CallNode)iVisited)
        matches = methods.findAll {
            it.name == iVisited.name && argsCounter <= it.args && argsCounter >= it.args-it.optionalArgs
        }
        matches
    }

    private registryMethodCallFromUnknownReceiver(Node iVisited, boolean hasArgs){
        def matches = []
        if(hasArgs) matches = searchForMethodMatch(iVisited)
        else  matches = methods.findAll { it.name==iVisited.name && (it.args-it.optionalArgs)==0 }

        if(matches.empty) taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
        else matches.each{
            log.info "match: ${it.name}; ${it.path}; ${it.args}; ${it.optionalArgs}"
            taskInterface.methods += [name: iVisited.name, type: Util.getClassName(it.path), file: it.path]
        }
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
            /* Checks if the method really exists. There are methods that are generated automatically by Rails.
            * In any case, the call is registered.*/
            def matches = searchForMethodMatch(iVisited)
            if (matches.empty) {
                registryClassUsageUsingFilename(path)
                log.warn "The method called by instance variable was not found: " +
                        "${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine + 1})"
                /* Examples: @mobilization.hashtag; mobilization.save! */
            } else{
                taskInterface.methods += [name: iVisited.name, type: Util.getClassName(path), file: path]
            }
        } else { //it seems it never has happened
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

    private analyseVisitCall(FCallNode iVisited){
        /* if the argument is a literal, the view was found */
        if(iVisited.args.last.class == ConstNode){
            def foundPages = Util.findViewPathForRailsProjects(iVisited.args.last.name, viewFiles)
            if(foundPages && !foundPages.empty) {
                taskInterface.referencedPages += foundPages
            }
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
    }

    private analyseExpectCall(FCallNode iVisited){
        def argClass = iVisited?.args?.last?.class
        if(argClass && argClass==InstVarNode){
            def name = iVisited.args.last.name
            name = name.toUpperCase().getAt(0) + name.substring(1)
            registryClassUsage(name)
        }
    }

    /**
     * A method or operator call.
     */
    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        //println "Method call: ${iVisited.name} (${iVisited.position.startLine});   Receptor: ${iVisited.receiver.name}"

        def operators = ["[]","*","/","+","-","==","!=",">","<",">=","<=","<=>","===",".eql?","equal?","defined?","%",
                         "<<",">>","=~","&","|","^","~","!","**"]
        def excludedMethods = ["should", "should_not"] + operators
        if(iVisited.name in excludedMethods ) return iVisited

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
                    registryMethodCallFromSelf(iVisited)
                    break
                case LocalVarNode: //Access a local variable
                case InstVarNode: //instance variable, example: @user.should_not be_nil
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
                    registryMethodCallFromUnknownReceiver(iVisited, true)
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
        //log.info "Method call: ${iVisited.name}; $lastVisitedFile; (${iVisited.position.startLine+1})"

        def excludedMethods = ["puts", "print"]
        if(iVisited.name in excludedMethods) return iVisited

        switch (iVisited.name){
            case "visit": //indicates the view
                log.info "VISIT CALL: $lastVisitedFile (${iVisited.position.startLine+1})"
                analyseVisitCall(iVisited)
                break
            case "expect": //alternative for should and should_not
                analyseExpectCall(iVisited)
                break
            case "steps": //when a step calls another step; until the moment, nothing is done about it.
            case "step":
                taskInterface.methods += [name: iVisited.name, type: "StepCall", file: null]
                break
            default: //helper method for visit and expect can match such a condition
                if(!(iVisited.name in Util.STEP_KEYWORDS) && !(iVisited.name in  Util.STEP_KEYWORDS_PT) ){
                    registryMethodCallFromSelf(iVisited)
                }
        }
        iVisited
    }

    /**
     * RubyMethod call without any arguments
     */
    @Override
    Object visitVCallNode(VCallNode iVisited) {
        super.visitVCallNode(iVisited)
        //log.info "Method call: ${iVisited.name}; $lastVisitedFile; (${iVisited.position.startLine+1}); no args!"
        registryMethodCallFromUnknownReceiver(iVisited, false)
        iVisited
    }

}

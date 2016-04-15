package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.HashNode
import org.jrubyparser.ast.InstAsgnNode
import org.jrubyparser.ast.Node
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
import util.ConstantData
import util.Util
import util.ruby.RubyUtil

@Slf4j
class RubyTestCodeVisitor extends NoopVisitor implements TestCodeVisitor {

    TaskInterface taskInterface
    List<String> projectFiles
    List<String> viewFiles
    String lastVisitedFile
    Set methods //keys: name, args, path; all methods from project
    def productionClass //keys: name, path; used when visiting RSpec files; try a better way to represent it!
    def calledSteps //keys:text, path, line

    RubyTestCodeVisitor(String currentFile){ //test purpose only
        this.taskInterface = new TaskInterface()
        projectFiles = []
        viewFiles = []
        lastVisitedFile = currentFile
        calledSteps = []
    }

    RubyTestCodeVisitor(List<String> projectFiles, String currentFile, Set methods){
        this.projectFiles = projectFiles
        this.viewFiles = projectFiles.findAll{ it.contains(Util.VIEWS_FILES_RELATIVE_PATH+File.separator) }
        this.taskInterface = new TaskInterface()
        this.lastVisitedFile = currentFile
        this.methods = methods
        calledSteps = []
    }

    private registryMethodCall(CallNode iVisited){
        def path = RubyUtil.getClassPathForRubyClass(iVisited.receiver.name, projectFiles)
        if(path) taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
    }

    private static int countArgsMethodCall(def iVisited){
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
        def argsCounter = countArgsMethodCall(iVisited)
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
            taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(it.path), file: it.path]
        }
    }

    private registryMethodCallFromSelf(Node iVisited){
        if(lastVisitedFile.contains(Util.VIEWS_FILES_RELATIVE_PATH)){
            def index = lastVisitedFile.lastIndexOf(File.separator)
            taskInterface.methods += [name: iVisited.name, type:lastVisitedFile.substring(index+1), file:lastVisitedFile]
        } else {
            def matches = searchForMethodMatch(iVisited)
            if(matches.empty) {
                taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(lastVisitedFile), file: lastVisitedFile]
            } else {
                matches.each{
                    taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(it.path), file: it.path]
                }
            }
        }
    }

    private registryMethodCallFromInstanceVariable(CallNode iVisited){
        def path = RubyUtil.getClassPathForRubyInstanceVariable(iVisited.receiver.name, projectFiles)
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
                taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(path), file: path]
            }
        } else { //it seems it never has happened
            taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
        }
    }

    private registryClassUsage(String name){
        def path = RubyUtil.getClassPathForRubyClass(name, projectFiles)
        if(path) taskInterface.classes += [name: name, file: path]
    }

    private registryClassUsageUsingFilename(String path){
        if(path.contains(Util.VIEWS_FILES_RELATIVE_PATH)){
            def index = path.lastIndexOf(File.separator)
            taskInterface.classes += [name:path.substring(index+1), file:path]
        } else {
            taskInterface.classes += [name:RubyUtil.getClassName(path), file:path]
        }
    }

    private registryMethodCallVisitArg(FCallNode iVisited){
        def methodsToIgnore = ["current_path"]
        log.info "param is method call: ${iVisited.args.last.name}"

        //it should consider all parameters to better identify the method
        def methodsToVisit = methods.findAll { it.name == iVisited.args.last.name }

        if (methodsToVisit.empty) {
            if(!(iVisited.args.last.name in methodsToIgnore) && iVisited.args.last.name.endsWith(RubyUtil.ROUTE_SUFIX)){
                taskInterface.calledPageMethods += [name: iVisited.args.last.name-RubyUtil.ROUTE_SUFIX, file:RubyUtil.ROUTES_ID]
                log.info "param is (undefined) route method call: ${iVisited.args.last.name}"
            }
            else log.info "param is (undefined) method call: ${iVisited.args.last.name}"
        } else{
            log.info "param is (defined) method call: ${iVisited.args.last.name}"
            methodsToVisit?.each { m -> taskInterface.calledPageMethods += [name: m.name, file: m.path] }
        }
    }

    private static extractPath(def value){
        if(value.startsWith("http://")) value = value - "http://"
        else if(value.startsWith("https://")) value = value - "https://"
        def i = value.indexOf("/")
        if(i>0) value = value.substring(i+1)
        value
    }

    private registryVisitStringArg(def value){
        value = extractPath(value)
        def index = value.indexOf("?")
        if(index>0) value = value.substring(0, index)//ignoring params
        taskInterface.calledPageMethods += [name: value, file: RubyUtil.ROUTES_ID]
        log.info "param is literal: $value"
    }

    private registryVisitDynamicStringArg(FCallNode iVisited){
        String name = ""
        iVisited.args.last.childNodes().each{ c-> if(c instanceof StrNode) name += c.value.trim() }
        name = extractPath(name)
        def index = name.indexOf("?")
        if(index>0) name = name.substring(0, index)//ignoring params

        /* if the dynamic content is not at the end of the string, the resultin url will be wrong. Example:
           visit "/portal/classes/#{clazz.id}/remove_offering?offering_id=#{offering.id}"
           Extracted url: /portal/classes//remove_offering  */
        if(name.contains("//edit")){
            def begin = name.indexOf("//edit")
            def end = index + 7
            def finalName = ""
            if(index>0)  finalName = name.substring(0,begin) + "/edit" + name.substring(end)
            if(!finalName.empty){
                taskInterface.calledPageMethods += [name: name, file: RubyUtil.ROUTES_ID]
                log.info "param is dynamic literal: $name"
            }
        }
        else if(!name.contains("//")) {
            taskInterface.calledPageMethods += [name: name, file: RubyUtil.ROUTES_ID]
            log.info "param is dynamic literal: $name"
        }
        else log.warn "param is dynamic literal that cannot be correctly retrieved: $name"
    }

    private analyseVisitCall(FCallNode iVisited){
        log.info "VISIT CALL: ${lastVisitedFile} (${iVisited.position.startLine+1});"

        /* if the argument is a literal, the view path was found */
        switch(iVisited.args.last.class) {
            case LocalVarNode: //esse caso pode ser resolvido se o parametro vier do texto do step
                /*  def verified_visit(path)
                      visit path
                      verify_current_path(path)
                    end */
                log.info "param is a local variable: ${iVisited.args.last.name}"
                break
            case InstVarNode: //visit @contract
                log.info "param is a instance variable: ${iVisited.args.last.name}"
                break
            case CaseNode:
                /* https://github.com/leihs/leihs/blob/8fb0eace3f441320b6aa70980acf5ee1d279dc6c/features/
                   step_definitions/examples/benutzerverwaltung_steps.rb
                    When(/^I am looking at the user list( outside an inventory pool| in any inventory pool)?$/) do |arg1|
                      visit case arg1
                              when " outside an inventory pool"
                                manage_users_path
                              when " in any inventory pool"
                                @current_inventory_pool = InventoryPool.first
                                manage_inventory_pool_users_path(@current_inventory_pool)
                              else
                                manage_inventory_pool_users_path(@current_inventory_pool)
                            end
                    end
               */
                log.info "param is a case node"
                break
            case StrNode: //Representing a simple String literal
                registryVisitStringArg(iVisited.args.last.value)
                break
            case DStrNode://A string which contains some dynamic elements which needs to be evaluated (introduced by #)
                registryVisitDynamicStringArg(iVisited)
                break
            case DVarNode: //dynamic variable (e.g. block scope local variable)
                log.info "param is a dynamic variable: ${iVisited.args.last.name}"
                break
            /* If the argument is a method call that returns a literal, we understand the view was found.
            Otherwise, it is not possible to extract it and find the view. */ //caso da chamada visit to_url(arg1)
            case VCallNode:
            case CallNode:
            case FCallNode:
                registryMethodCallVisitArg(iVisited)
                break
            default:
                log.info "info about visit call: "
                iVisited.properties.each { k, v -> log.info "$k: $v" }
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

    private registryStepCall(FCallNode iVisited){
        //registries frequency of step calls
        taskInterface.methods += [name: iVisited.name, type: "StepCall", file: null]

        def argValue = ""
        iVisited?.args?.childNodes()?.each{ child ->
            if(child instanceof DStrNode){
                child.childNodes().each{ c-> if(c instanceof StrNode) argValue += c.value.trim() }
            } else if(child instanceof StrNode) argValue += child.value.trim()
        }

        argValue?.readLines()?.each{
            if(!it.startsWith("|")) calledSteps += [text:it.trim(), path:lastVisitedFile, line:iVisited.position.startLine]
        }
    }

    /**
     * A method or operator call.
     */
    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        //println "Method call: ${iVisited.name} $lastVisitedFile (${iVisited.position.startLine+1});   Receptor: ${iVisited.receiver.name}"

        def operators = ["[]","*","/","+","-","==","!=",">","<",">=","<=","<=>","===",".eql?","equal?","defined?","%",
                         "<<",">>","=~","&","|","^","~","!","**"]
        def excludedMethods = ["should", "should_not"] + operators
        if(iVisited.name in excludedMethods ) return iVisited

        // unit test file
        if(productionClass && iVisited.receiver.properties.containsKey("name") && iVisited.receiver.name == "subject") {
            taskInterface.methods += [name: iVisited.name, type: productionClass.name, file: productionClass.path]
        }
        // routing methods
        else if(iVisited.name!="current_path" && iVisited.name.endsWith(RubyUtil.ROUTE_SUFIX)){
            taskInterface.calledPageMethods += [name: iVisited.name-RubyUtil.ROUTE_SUFIX, file:RubyUtil.ROUTES_ID]
        } else {
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
                analyseVisitCall(iVisited)
                break
            case "expect": //alternative for should and should_not
                analyseExpectCall(iVisited)
                break
            /*case "many_steps": //another way to call steps (check if is really used)
                log.info "many_steps call!"
                break*/
            case "steps": //when a step calls another step
            case "step":
                registryStepCall(iVisited)
                break
            default: //helper methods for visit can match such a condition
                if(!(iVisited.name in ConstantData.STEP_KEYWORDS) && !(iVisited.name in  ConstantData.STEP_KEYWORDS_PT) ){
                    registryMethodCallFromSelf(iVisited)
                }
        }
        iVisited
    }

    /**
     * RubyMethod call without any arguments.
     */
    @Override
    Object visitVCallNode(VCallNode iVisited) {
        super.visitVCallNode(iVisited)
        //log.info "Method call: ${iVisited.name}; $lastVisitedFile; (${iVisited.position.startLine+1}); no args!"
        if(iVisited.name!="current_path" && iVisited.name.endsWith(RubyUtil.ROUTE_SUFIX)) {
            taskInterface.calledPageMethods += [name: iVisited.name - RubyUtil.ROUTE_SUFIX, file: RubyUtil.ROUTES_ID]
        } else registryMethodCallFromUnknownReceiver(iVisited, false)
        iVisited
    }

    /**
     * Represents an instance variable assignment.
     */
    @Override
    Object visitInstAsgnNode(InstAsgnNode iVisited) {
        super.visitInstAsgnNode(iVisited)
        def className = RubyUtil.getClassPathForRubyInstanceVariable(iVisited.name, projectFiles)
        if(className && !className.empty) registryClassUsageUsingFilename(className)
        iVisited
    }

    /**
     * Represents an instance variable accessor.
     */
    @Override
    Object visitInstVarNode(InstVarNode iVisited) {
        super.visitInstVarNode(iVisited)
        def className = RubyUtil.getClassPathForRubyInstanceVariable(iVisited.name, projectFiles)
        if(className && !className.empty) registryClassUsageUsingFilename(className)
        iVisited
    }

}

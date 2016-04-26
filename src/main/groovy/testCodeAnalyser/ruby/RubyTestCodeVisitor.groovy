package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.HashNode
import org.jrubyparser.ast.INameNode
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
import org.jrubyparser.ast.WhenNode
import org.jrubyparser.util.NoopVisitor
import taskAnalyser.task.TaskInterface
import testCodeAnalyser.MethodToAnalyse
import testCodeAnalyser.StepCall
import testCodeAnalyser.TestCodeVisitor
import util.ConstantData
import util.Util
import util.ruby.RubyUtil

@Slf4j
class RubyTestCodeVisitor extends NoopVisitor implements TestCodeVisitor {

    static final OPERATORS = ["[]","*","/","+","-","==","!=",">","<",">=","<=","<=>","===",".eql?","equal?","defined?","%",
                     "<<",">>","=~","&","|","^","~","!","**"]
    static final EXCLUDED_METHODS = ["puts", "print", "assert", "should", "should_not"] + RubyUtil.EXCLUDED_PATH_METHODS +
            ConstantData.STEP_KEYWORDS + ConstantData.STEP_KEYWORDS_PT + OPERATORS

    TaskInterface taskInterface
    List<String> projectFiles
    List<String> viewFiles
    String lastVisitedFile
    Set projectMethods //keys: name, args, path; all methods from project
    def productionClass //keys: name, path; used when visiting RSpec files; try a better way to represent it!
    List<StepCall> calledSteps
    MethodToAnalyse stepDefinitionMethod

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
        this.projectMethods = methods
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
        matches = projectMethods.findAll {
            it.name == iVisited.name && argsCounter <= it.args && argsCounter >= it.args-it.optionalArgs
        }
        matches
    }

    private registryMethodCallFromUnknownReceiver(Node iVisited, boolean hasArgs){
        def matches = []
        if(hasArgs) matches = searchForMethodMatch(iVisited)
        else  matches = projectMethods.findAll { it.name==iVisited.name && (it.args-it.optionalArgs)==0 }

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

    private registryMethodCallVisitArg(String name){
        //it should consider all parameters to better identify the method
        def methodsToVisit = projectMethods.findAll { it.name == name }

        if (methodsToVisit.empty) {
            if(RubyUtil.isRouteMethod(name)){
                taskInterface.calledPageMethods += [file:RubyUtil.ROUTES_ID, name: name-RubyUtil.ROUTE_SUFIX, args:[]]
                log.info "param is (undefined) route method call: $name"
            }
            else log.info "param is (undefined) method call: $name"
        } else{
            log.info "param is (defined) method call: $name"
            def args = []
            if(stepDefinitionMethod){
                args = stepDefinitionMethod.args
            }
            methodsToVisit?.each { m -> taskInterface.calledPageMethods += [file: m.path, name: m.name, args:args] }
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
        taskInterface.calledPageMethods += [file: RubyUtil.ROUTES_ID, name: value, args: []]
        log.info "param is literal: $value"
    }

    private registryVisitDynamicStringArg(DStrNode node){
        String name = ""
        node.childNodes().each{ c-> if(c instanceof StrNode) name += c.value.trim() }
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
                taskInterface.calledPageMethods += [file: RubyUtil.ROUTES_ID, name: name, args: []]
                log.info "param is dynamic literal: $name"
            }
        }
        else if(!name.contains("//")) {
            taskInterface.calledPageMethods += [file: RubyUtil.ROUTES_ID, name: name, args: []]
            log.info "param is dynamic literal: $name"
        }
        else log.warn "param is dynamic literal that cannot be correctly retrieved: $name"
    }

    private analyseVisitCall(FCallNode iVisited){
        log.info "VISIT CALL: ${lastVisitedFile} (${iVisited.position.startLine+1});"
        registryVisitCall(iVisited.args.last)
    }

    /* Example: https://github.com/concord-consortium/rigse/blob/74359e8c178fbe6c2c625ab329e8d8fae7bb59ab/features/step_definitions/web_steps.rb
        def verified_visit(path)
             visit path
             verify_current_path(path)
        end
        P.S.: The solution does not deal with the example, because it is not a step definition. */
    private registryVisitCall(LocalVarNode node){
        log.info "param is a local variable: ${node.name}"
        if(stepDefinitionMethod){
            def arg = stepDefinitionMethod.args?.last()
            if(arg) {
                log.info "vai usar argumento de step como string em visit call!"
                registryVisitStringArg(arg)
            }
        }
    }

    /* visit @contract */
    private registryVisitCall(InstVarNode node){
        log.info "param is a instance variable: ${node.name}"
    }

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
    private registryVisitCall(CaseNode node){
        log.info "param is a case node"
        node.childNodes().findAll{ it instanceof WhenNode }
    }

    /* Representing a simple String literal */
    private registryVisitCall(StrNode node){
        registryVisitStringArg(node.value)
    }

    /* A string which contains some dynamic elements which needs to be evaluated (introduced by #) */
    private registryVisitCall(DStrNode node){
        registryVisitDynamicStringArg(node)
    }

    /* dynamic variable (e.g. block scope local variable)*/
    private registryVisitCall(DVarNode node){
        log.info "param is a dynamic variable: ${node.name}"
    }

    /* If the argument is a method call that returns a literal, we understand the view was found.
       Otherwise, it is not possible to extract it and find the view. */ //uso INameNode para identificar chamadas de método, mas na realidade isso pode pegar bem mais coisa!
    private registryVisitCall(INameNode node){
        registryMethodCallVisitArg(node.name)
    }

    /* default case */
    private registryVisitCall(Node node){
        log.info "information about argument of visit call:"
        node.properties.each { k, v -> log.info "$k: $v" }
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
            if(!it.startsWith("|")) {
                def stepText = it.trim()
                for(def i=0; i<ConstantData.STEP_KEYWORDS.size(); i++){
                    def keyword = ConstantData.STEP_KEYWORDS.get(i)
                    if(stepText.startsWith(keyword)){
                        stepText = stepText.replaceFirst(keyword,"").trim()
                        break
                    }
                }
                calledSteps += new StepCall(text:stepText, path:lastVisitedFile, line:iVisited.position.startLine)
            }
        }
    }

    private registry(CallNode iVisited, Colon3Node receiver){
        registryMethodCall(iVisited)
    }

    private registry(CallNode iVisited, ConstNode receiver){
        registryMethodCall(iVisited)
    }

    private registry(CallNode iVisited, Colon2Node receiver){
        registryMethodCall(iVisited)
        registryClassUsage(iVisited.receiver.leftNode.name)
    }

    private registry(CallNode iVisited, Colon2ConstNode receiver){
        registryMethodCall(iVisited)
        registryClassUsage(iVisited.receiver.leftNode.name)
    }

    private registry(CallNode iVisited, SelfNode receiver){
        registryMethodCallFromSelf(iVisited)
    }

    private registry(CallNode iVisited, LocalVarNode receiver){
        registryMethodCallFromInstanceVariable(iVisited)
    }

    private registry(CallNode iVisited, InstVarNode receiver){
        registryMethodCallFromInstanceVariable(iVisited)
    }

    private registry(CallNode iVisited, DVarNode receiver){
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, FCallNode receiver){
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, VCallNode receiver){
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, CallNode receiver){
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, Node receiver){
        def excluded = [ArrayNode, NewlineNode, StrNode, DStrNode, FixnumNode, OrNode, IfNode, CaseNode]
        if(receiver instanceof GlobalVarNode){
            log.warn "CALL BY GLOBAL VARIABLE \nPROPERTIES:"
            receiver.properties.each { k, v -> log.warn "$k: $v" }
            if ( !(receiver.name == "?") ) log.warn "GLOBAL VARIABLE IS '?'"
        }
        else if( !(receiver.class in excluded)){
            log.warn "RECEIVER DEFAULT! called: ${iVisited.name} $lastVisitedFile (${iVisited.position.startLine+1}); " +
                    "Receiver type: ${iVisited.receiver.class}"
        }
    }

    /**
     * A method or operator call.
     */
    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if(iVisited.name in EXCLUDED_METHODS) return iVisited
        //log.info "Method call: ${iVisited.name} $lastVisitedFile (${iVisited.position.startLine+1});   Receptor: ${iVisited.receiver.class}"

        // unit test file
        if(productionClass && iVisited.receiver.properties.containsKey("name") && iVisited.receiver.name == "subject") {
            taskInterface.methods += [name: iVisited.name, type: productionClass.name, file: productionClass.path]
        }
        // routing methods
        else if(RubyUtil.isRouteMethod(iVisited.name)){
            taskInterface.calledPageMethods += [file:RubyUtil.ROUTES_ID, name: iVisited.name-RubyUtil.ROUTE_SUFIX, args: []]
        // methods of interest
        } else {
            registry(iVisited, iVisited.receiver)
        }
        iVisited
    }

    /**
     * Represents a method call with self as an implicit receiver.
     */
    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)

        if(iVisited.name in EXCLUDED_METHODS) return iVisited
        //log.info "Method call (fcallnode): ${iVisited.name}; $lastVisitedFile; (${iVisited.position.startLine+1})"

        if( (iVisited.grandParent instanceof FCallNode) && iVisited.grandParent.name == "visit") return iVisited
        else if(RubyUtil.isRouteMethod(iVisited.name)){
            taskInterface.calledPageMethods += [file:RubyUtil.ROUTES_ID, name: iVisited.name-RubyUtil.ROUTE_SUFIX, args: []]
        } else {
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
        if(RubyUtil.isRouteMethod(iVisited.name)) {
            taskInterface.calledPageMethods += [file: RubyUtil.ROUTES_ID, name: iVisited.name - RubyUtil.ROUTE_SUFIX, args: []]
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

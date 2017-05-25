package br.ufpe.cin.tan.test.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.*
import org.jrubyparser.util.NoopVisitor
import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.test.MethodToAnalyse
import br.ufpe.cin.tan.test.StepCall
import br.ufpe.cin.tan.test.TestCodeVisitor
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.util.ruby.RubyConstantData
import br.ufpe.cin.tan.util.ruby.RubyUtil

@Slf4j
class RubyTestCodeVisitor extends NoopVisitor implements TestCodeVisitor {

    ITest taskInterface
    List<String> projectFiles
    List<String> viewFiles
    String lastVisitedFile
    Set projectMethods //keys: name, args, path; all methods from project
    def productionClass //keys: name, path; used when visiting RSpec files; try a better way to represent it!
    List<StepCall> calledSteps
    static int stepCallCounter
    MethodToAnalyse stepDefinitionMethod
    int visitCallCounter
    Set lostVisitCall //keys: path, line

    RubyTestCodeVisitor(String currentFile) { //test purpose only
        this.taskInterface = new ITest()
        projectFiles = []
        viewFiles = []
        lastVisitedFile = currentFile
        calledSteps = []
        lostVisitCall = [] as Set
    }

    RubyTestCodeVisitor(List<String> projectFiles, String currentFile, Set methods) {
        this.projectFiles = projectFiles
        viewFiles = projectFiles.findAll { it.contains(Util.VIEWS_FILES_RELATIVE_PATH + File.separator) }
        taskInterface = new ITest()
        lastVisitedFile = currentFile
        projectMethods = methods
        calledSteps = []
        lostVisitCall = [] as Set
    }

    private registryMethodCall(CallNode iVisited) {
        def paths = RubyUtil.getClassPathForRubyClass(iVisited.receiver.name, projectFiles)
        paths.each{ path ->
            taskInterface.methods += [name: iVisited.name, type: iVisited.receiver.name, file: path]
        }
    }

    private static int countArgsMethodCall(iVisited) {
        def counter = 0
        iVisited?.args?.childNodes()?.each { child ->
            if (child instanceof HashNode && child?.listNode?.size() > 0) {
                counter += child.listNode.childNodes().findAll { it instanceof SymbolNode }?.size()
            } else counter++
        }
        counter
    }

    def searchForMethodMatch(Node iVisited) {
        def matches = []
        def argsCounter = countArgsMethodCall(iVisited)
        matches = projectMethods.findAll {
            it.name == iVisited.name && argsCounter <= it.args && argsCounter >= it.args - it.optionalArgs
        }
        matches
    }

    def searchForMethodMatch(String method, int argsCounter) {
        def matches = []
        matches = projectMethods.findAll {
            it.name == method && argsCounter <= it.args && argsCounter >= it.args - it.optionalArgs
        }
        matches
    }

    private registryMethodCallFromUnknownReceiver(Node iVisited, boolean hasArgs) {
        def matches = []
        if (hasArgs) matches = searchForMethodMatch(iVisited)
        else matches = projectMethods.findAll { it.name == iVisited.name && (it.args - it.optionalArgs) == 0 }

        if (matches.empty) taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
        else matches.each {
            taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(it.path), file: it.path]
        }
    }

    private registryMethodCallFromSelf(Node iVisited) {
        if (lastVisitedFile.contains(Util.VIEWS_FILES_RELATIVE_PATH)) {
            def index = lastVisitedFile.lastIndexOf(File.separator)
            taskInterface.methods += [name: iVisited.name, type: lastVisitedFile.substring(index + 1), file: lastVisitedFile]
        } else {
            def matches = searchForMethodMatch(iVisited)
            if (matches.empty) {
                taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(lastVisitedFile), file: lastVisitedFile]
            } else {
                matches.each {
                    taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(it.path), file: it.path]
                }
            }
        }
    }

    private registryMethodCallFromInstanceVariable(CallNode iVisited) {
        def paths = RubyUtil.getClassPathForRubyInstanceVariable(iVisited.receiver.name, projectFiles)
        if (paths) {
            /* Checks if the method really exists. There are methods that are generated automatically by Rails.
            * In any case, the call is registered.*/
            def matches = searchForMethodMatch(iVisited)
            if (matches.empty) {
                this.registryClassUsageUsingFilename(paths)
                log.warn "The method called by instance variable was not found: " +
                        "${iVisited.receiver.name}.${iVisited.name} $lastVisitedFile (${iVisited.position.startLine + 1})"
                /* Examples: @mobilization.hashtag; mobilization.save! */
            } else {
                paths.each{ path -> taskInterface.methods += [name: iVisited.name, type: RubyUtil.getClassName(path), file: path] }
            }
        } else { //it seems it never has happened
            taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
        }
    }

    def registryClassUsageUsingFilename(List<String> paths) {
        paths.each{ path ->
            if (path?.contains(Util.VIEWS_FILES_RELATIVE_PATH)) {
                def index = path?.lastIndexOf(File.separator)
                taskInterface.classes += [name: path?.substring(index + 1), file: path]
            } else {
                taskInterface.classes += [name: RubyUtil.getClassName(path), file: path]
            }
        }
    }

    private registryMethodCallVisitArg(String name) {
        //it should consider all parameters to better identify the method
        def methodsToVisit = projectMethods.findAll { it.name == name }

        if (methodsToVisit.empty) {
            if (RubyUtil.isRouteMethod(name)) {
                taskInterface.calledPageMethods += [file: RubyConstantData.ROUTES_ID, name: name - RubyConstantData.ROUTE_PATH_SUFIX, args: []]
                //log.info "visit param is a route method call: $name"
            } else {
                //log.info "visit param is a undefined method call: $name"
                lostVisitCall += [path: lastVisitedFile, line: -1]
            }
        } else {
            def args = []
            if (stepDefinitionMethod) args = stepDefinitionMethod.args
            //log.info "visit param is defined method call: $name; args: $args"
            methodsToVisit?.each { m ->
                taskInterface.calledPageMethods += [file: m.path, name: m.name, args: args]
            }
        }
    }

    private static extractPath(value) {
        if (value.startsWith("http://")) value = value - "http://"
        else if (value.startsWith("https://")) value = value - "https://"
        def i = value.indexOf("/")
        if (i > 0) value = value.substring(i + 1)
        value
    }

    private registryVisitStringArg(value) {
        value = extractPath(value)
        def index = value.indexOf("?")
        if (index > 0) value = value.substring(0, index)//ignoring params
        taskInterface.calledPageMethods += [file: RubyConstantData.ROUTES_ID, name: value, args: []]
        //log.info "param is literal: $value"
    }

    private registryVisitDynamicStringArg(DStrNode node) {
        String name = ""
        node.childNodes().each { c -> if (c instanceof StrNode) name += c.value.trim() }
        name = extractPath(name)
        def index = name.indexOf("?")
        if (index > 0) name = name.substring(0, index)//ignoring params
        /* if the dynamic content is not at the end of the string, the resulting url will be wrong. Example:
           visit "/portal/classes/#{clazz.id}/remove_offering?offering_id=#{offering.id}"
           Extracted url: /portal/classes//remove_offering  */
        name = name.replaceAll("//", "/:id/")
        taskInterface.calledPageMethods += [file: RubyConstantData.ROUTES_ID, name: name, args: []]
        //log.info "param is dynamic literal: $name"

    }

    private analyseVisitCall(FCallNode iVisited) {
        log.info "VISIT CALL: ${lastVisitedFile} (${iVisited.position.startLine+1});"
        //iVisited.args.last.properties.each { k, v -> log.info "$k: $v" }
        visitCallCounter++
        registryVisitCall(iVisited.args.last)
    }

    /* Example: https://github.com/concord-consortium/rigse/blob/74359e8c178fbe6c2c625ab329e8d8fae7bb59ab/features/step_definitions/web_steps.rb
        def verified_visit(path)
             visit path
             verify_current_path(path)
        end
        P.S.: The solution does not deal with the example, because it is not a step definition. */
    private registryVisitCall(LocalVarNode node) {
        //log.info "param is a local variable: ${node.name}"
        if (stepDefinitionMethod && !stepDefinitionMethod.args.empty) {
            def arg = stepDefinitionMethod.args.last()
            if (arg) registryVisitStringArg(arg)
        } else {
            log.warn "Visit call with local variable as receiver inside a method that is not a step definition:" +
                    "\n${lastVisitedFile} (${node.position.startLine+1})"
            lostVisitCall += [path: lastVisitedFile, line: node.position.startLine+1]
        }
    }

    /* Examples: visit @contract
    *
    * https://github.com/leihs/leihs/blob/13a145a3b6b97dfd1af2a52881a436a8b9a47bd9/features/step_definitions/examples/benutzerverwaltung_steps.rb
    * Wenn(/^man versucht auf die Administrator Benutzererstellenansicht zu gehen$/) do
        @path = edit_backend_user_path(User.first)
        visit @path
      end
    * */
    private registryVisitCall(InstVarNode node) {
        //log.info "param is a instance variable: ${node.name}"
        def method = (node.name - "@") + "_path"
        registryMethodCallVisitArg(method)
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
    private registryVisitCall(CaseNode node) {
        //log.info "param is a case node"
        def args = []
        if (stepDefinitionMethod) args = stepDefinitionMethod.args
        RubyWhenNodeVisitor whenNodeVisitor = new RubyWhenNodeVisitor(args)
        node.accept(whenNodeVisitor)

        whenNodeVisitor.pages?.each { page ->
            taskInterface.calledPageMethods += [file: RubyConstantData.ROUTES_ID, name: page, args: []]
            //log.info "Page in casenode: $page"
        }

        whenNodeVisitor.auxiliaryMethods.each { method ->
            registryMethodCallVisitArg(method)
            //log.info "Method in casenode: ${method}"
        }

    }

    /* Representing a simple String literal */
    private registryVisitCall(StrNode node) {
        //log.info "param is a string literal"
        registryVisitStringArg(node.value)
    }

    /* A string which contains some dynamic elements which needs to be evaluated (introduced by #) */
    private registryVisitCall(DStrNode node) {
        //log.info "param is a dynamic string"
        registryVisitDynamicStringArg(node)
    }

    /* dynamic variable (e.g. block scope local variable)
    * Example:
        * When /^I visit the route (.+)$/ do |path|
            visit path
          end
    * */
    private registryVisitCall(DVarNode node) {
        //log.info "param is a dynamic variable: ${node.name}"
        if (stepDefinitionMethod && !stepDefinitionMethod.args.empty) {
            def arg = stepDefinitionMethod.args.last()
            if (arg) {
                registryVisitStringArg(arg)
            }
        }
    }

    /* If the argument is a method call (VCallNode, CallNode, FCallNode) that returns a literal, we understand the view was found.
       Otherwise, it is not possible to extract it and find the view. */
    private registryVisitCall(VCallNode node) {
        //log.info "param is a method call (VCallNode): ${node.name}"
        registryMethodCallVisitArg(node.name)
    }

    /* If the argument is a method call (VCallNode, CallNode, FCallNode) that returns a literal, we understand the view was found.
       Otherwise, it is not possible to extract it and find the view. */
    private registryVisitCall(CallNode node) {
        //log.info "param is a method call (CallNode): ${node.name}"
        //concatenação de String, com possível chamada de método.
        //ex: visit "/children/" + input_child_hash["_id"], :put, {:child => input_child_hash, :format => 'json'}
        if(node.name in RubyConstantData.OPERATORS){ //visit call uses a String argument...
            log.info "Visit call uses a transformed String as argument. Transforming operator: ${node.name}"
            lostVisitCall += [path: lastVisitedFile, line: node.position.startLine+1]
        } else registryMethodCallVisitArg(node.name)
    }

    /* If the argument is a method call (VCallNode, CallNode, FCallNode) that returns a literal, we understand the view was found.
       Otherwise, it is not possible to extract it and find the view. */
    private registryVisitCall(FCallNode node) {
        //log.info "param is a method call (FCallNode): ${node.name}"
        registryMethodCallVisitArg(node.name)
    }

    private analyseFirstArg(Node node){
        def first = null
        if(node.parent instanceof ArrayNode && !node.parent.childNodes().empty){
            first = node.parent.childNodes().first()
        }
        if(first) registryVisitCall(first)
        else lostVisitCall += [path: lastVisitedFile, line: node.position.startLine+1]
    }

    private registryVisitCall(HashNode node) {
        //log.info "param is a hash node"
        analyseFirstArg(node)
    }

    private registryVisitCall(NilNode node) {
        //log.info "param is a nil node"
        analyseFirstArg(node)
    }

    /* default case */
    private registryVisitCall(Node node) {
        lostVisitCall += [path: lastVisitedFile, line: node.position.startLine+1]
        log.warn "information about unknown argument of visit call:"
        node.properties.each { k, v -> log.info "$k: $v" }
    }

    private analyseExpectCall(FCallNode iVisited) {
        def argClass = iVisited?.args?.last?.class
        if (argClass && argClass == InstVarNode) {
            def name = iVisited.args.last.name
            name = name.toUpperCase().getAt(0) + name.substring(1)
            registryClassUsage(name)
        }
    }

    private registryStepCall(FCallNode iVisited) {
        //registries frequency of step calls
        taskInterface.methods += [name: iVisited.name, type: "StepCall", file: "${++stepCallCounter}"]

        def argValue = ""
        iVisited?.args?.childNodes()?.each { child ->
            if (child instanceof DStrNode) {
                child.childNodes().each { c -> if (c instanceof StrNode) argValue += c.value.trim() }
            } else if (child instanceof StrNode) {
                argValue += child.value.trim()
            } else if(child instanceof CallNode){
                child.childNodes().each { c -> if (c instanceof StrNode) argValue += c.value.trim() }
            }
        }

        def lines = argValue?.readLines()?.collect{ it.replaceAll(/[ \t]+/, " ").replaceAll(/[ ]+/, " ").trim() }
        if(lines.empty) return
        if(lines.any{ it.contains("<") && it.contains(">")}){ //scenario outline
            //log.info "called step is scenario outline"
            /*def argsTable = lines.findAll{ it.startsWith("|") }
            argsTable -= argsTable.first()
            argsTable.each{ argsTableLine ->
                def values = argsTableLine.split("\\|")*.replaceAll("\\|", "")*.trim()
                values = values?.findResults { i -> i.empty ? null : i } as Set
            }*/
        } else if( lines.any{ it.startsWith("|")} ){ //datatable
            //log.info "called step is datatable"
            lines = lines.findAll{ !it.startsWith("|") }
            registryCall(lines, iVisited)
        } else { //regular scenario
            //log.info "called step is a regular scenario"
            registryCall(lines, iVisited)
        }
    }

    private registryCall(lines, FCallNode iVisited){
        lines.each{ step ->
            if(!step.empty){
                def keyword = ConstantData.STEP_KEYWORDS.find{ step.startsWith(it) }
                if(keyword) step = step.replaceFirst(keyword, "").trim()
                calledSteps += new StepCall(text: step, path: lastVisitedFile, line: iVisited.position.startLine)
            }
        }
    }

    private registry(CallNode iVisited, Colon3Node receiver) {
        registryMethodCall(iVisited)
    }

    private registry(CallNode iVisited, ConstNode receiver) {
        registryMethodCall(iVisited)
    }

    private registry(CallNode iVisited, Colon2Node receiver) {
        registryMethodCall(iVisited)
        registryClassUsage(iVisited.receiver.leftNode.name)
    }

    private registry(CallNode iVisited, Colon2ConstNode receiver) {
        registryMethodCall(iVisited)
        registryClassUsage(iVisited.receiver.leftNode.name)
    }

    private registry(CallNode iVisited, SelfNode receiver) {
        registryMethodCallFromSelf(iVisited)
    }

    private registry(CallNode iVisited, LocalVarNode receiver) {
        registryMethodCallFromInstanceVariable(iVisited)
    }

    private registry(CallNode iVisited, InstVarNode receiver) {
        registryMethodCallFromInstanceVariable(iVisited)
    }

    private registry(CallNode iVisited, DVarNode receiver) {
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, FCallNode receiver) {
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, VCallNode receiver) {
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, CallNode receiver) {
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, ClassVarNode receiver){
        registryMethodCallFromUnknownReceiver(iVisited, true)
    }

    private registry(CallNode iVisited, RegexpNode receiver){ }

    private registry(CallNode iVisited, Node receiver) {
        def excluded = [ArrayNode, NewlineNode, FixnumNode, OrNode, IfNode, CaseNode, NthRefNode,
                        StrNode, DStrNode, DXStrNode, XStrNode, EvStrNode, DRegexpNode, HashNode]

        //special meaning: see https://gist.github.com/LogaJ/5945449
        def invalidGlobalNames = [":", "0", "*", "?", "\$", "~", "1-\$9", "&", "+", "`", "'", "!", "@"]
        if (receiver instanceof GlobalVarNode && !(receiver.name in invalidGlobalNames) ) {
            taskInterface.methods += [name: iVisited.name, type: "Object", file: null]
        } else if (!(receiver.class in excluded)) {
            log.warn "RECEIVER DEFAULT! called: ${iVisited.name} $lastVisitedFile (${iVisited.position.startLine + 1}); " +
                    "Receiver type: ${iVisited.receiver.class}"
        }
    }

    def registryClassUsage(String name) {
        def paths = RubyUtil.getClassPathForRubyClass(name, projectFiles)
        if(paths.empty && Util.FRAMEWORK_FILES.findAll { it.contains(name) }.empty){
            taskInterface.classes += [name: name, file: null]
        }
        else {
            paths.each{ path -> taskInterface.classes += [name: name, file: path] }
        }
    }

    def registryCallFromInstanceVariable(String method, int argsCounter, String receiver) {
        def registered = true
        def paths = RubyUtil.getClassPathForRubyInstanceVariable(receiver, projectFiles)
        def matches = searchForMethodMatch(method, argsCounter)

        /* Receiver is valid and the called method is auto-generated.
           Example: @mobilization.hashtag; mobilization.save! */
        if(!paths.empty && matches.empty) {
            this.registryClassUsageUsingFilename(paths)
        }
        //receiver is valid and the method really exists
        else if(!paths.empty && !matches.empty){
            paths.each{ path -> taskInterface.methods += [name:method, type: RubyUtil.getClassName(path), file: path] }
        }
        //receiver is invalid but the method really exists
        else if (paths.empty && !matches.empty) {
            matches.each{ m -> taskInterface.methods += [name:method, type: RubyUtil.getClassName(m.path), file: m.path] }
        }
        //receiver is invalid and the method is auto-generated
        else {
            taskInterface.methods += [name:method, type: "Object", file: null]
            registered = false

        }
        registered
    }

    /**
     * A method or operator call.
     */
    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if (iVisited.name in RubyConstantData.IGNORED_METHODS) return iVisited
        //log.info "Method call: ${iVisited.name} $lastVisitedFile (${iVisited.position.startLine+1});   Receptor: ${iVisited.receiver.class}"

        // unit test file
        if (productionClass && iVisited.receiver.properties.containsKey("name") && iVisited.receiver.name == "subject") {
            taskInterface.methods += [name: iVisited.name, type: productionClass.name, file: productionClass.path]
        }
        // routing methods
        else if (RubyUtil.isRouteMethod(iVisited.name)) {
            taskInterface.calledPageMethods += [file: RubyConstantData.ROUTES_ID, name: iVisited.name - RubyConstantData.ROUTE_PATH_SUFIX, args: []]
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

        if (iVisited.name in RubyConstantData.IGNORED_METHODS) return iVisited
        //log.info "Method call (fcallnode): ${iVisited.name}; $lastVisitedFile; (${iVisited.position.startLine+1})"

        if ((iVisited.grandParent instanceof FCallNode) && iVisited.grandParent.name == "visit") return iVisited
        else if (RubyUtil.isRouteMethod(iVisited.name)) {
            taskInterface.calledPageMethods += [file: RubyConstantData.ROUTES_ID, name: iVisited.name - RubyConstantData.ROUTE_PATH_SUFIX, args: []]
        } else {
            switch (iVisited.name) {
                case "visit": //indicates the view
                    analyseVisitCall(iVisited)
                    break
                case "expect": //alternative for should and should_not
                    analyseExpectCall(iVisited)
                    break
                case "steps": //when a step calls another step
                case "step":
                    registryStepCall(iVisited)
                    break
                default: //helper methods for visit can match such a condition
                    if (!(iVisited.name in ConstantData.ALL_STEP_KEYWORDS)) registryMethodCallFromSelf(iVisited)
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
        if (RubyUtil.isRouteMethod(iVisited.name)) {
            taskInterface.calledPageMethods += [file: RubyConstantData.ROUTES_ID, name: iVisited.name - RubyConstantData.ROUTE_PATH_SUFIX, args: []]
        } else registryMethodCallFromUnknownReceiver(iVisited, false)
        iVisited
    }

    /**
     * Represents an instance variable assignment.
     */
    @Override
    Object visitInstAsgnNode(InstAsgnNode iVisited) {
        super.visitInstAsgnNode(iVisited)
        def classNames = RubyUtil.getClassPathForRubyInstanceVariable(iVisited.name, projectFiles)
        if (classNames && !classNames.empty) registryClassUsageUsingFilename(classNames)
        iVisited
    }

    /**
     * Represents an instance variable accessor.
     */
    @Override
    Object visitInstVarNode(InstVarNode iVisited) {
        super.visitInstVarNode(iVisited)
        def classNames = RubyUtil.getClassPathForRubyInstanceVariable(iVisited.name, projectFiles)
        if (classNames && !classNames.empty) registryClassUsageUsingFilename(classNames)
        iVisited
    }

}

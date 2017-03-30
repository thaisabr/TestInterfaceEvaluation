package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.CompatVersion
import org.jrubyparser.Parser
import org.jrubyparser.ast.Node
import org.jrubyparser.lexer.SyntaxException
import org.jrubyparser.parser.ParserConfiguration
import taskAnalyser.task.StepDefinition
import taskAnalyser.task.TaskInterface
import taskAnalyser.task.UnitFile
import testCodeAnalyser.FileToAnalyse
import testCodeAnalyser.StepRegex
import testCodeAnalyser.TestCodeAbstractParser
import testCodeAnalyser.TestCodeVisitor
import testCodeAnalyser.ruby.routes.Route
import testCodeAnalyser.ruby.routes.RouteHelper
import testCodeAnalyser.ruby.routes.RubyConfigRoutesVisitor
import testCodeAnalyser.ruby.unitTest.RSpecFileVisitor
import testCodeAnalyser.ruby.unitTest.RSpecTestDefinitionVisitor
import testCodeAnalyser.ruby.views.ViewCodeExtractor
import util.ConstantData
import util.RegexUtil
import util.Util
import util.ruby.RubyConstantData
import util.ruby.RubyUtil

import java.util.regex.Matcher

@Slf4j
class RubyTestCodeParser extends TestCodeAbstractParser {

    String routesFile
    Set<Route> routes
    Set<Route> problematicRoutes
    TaskInterface interfaceFromViews
    ViewCodeExtractor viewCodeExtractor
    static counter = 1

    RubyTestCodeParser(String repositoryPath) {
        super(repositoryPath)
        this.routesFile = repositoryPath + RubyConstantData.ROUTES_FILE
        this.routes = [] as Set
        this.problematicRoutes = [] as Set
        this.interfaceFromViews = new TaskInterface()
        if(Util.VIEW_ANALYSIS) viewCodeExtractor = new ViewCodeExtractor()
    }

    /***
     * Generates AST for Ruby file.
     * @param path path of interest file
     * @return the root node of the AST
     */
    private Node generateAst(reader, String path){
        Parser rubyParser = new Parser()
        CompatVersion version = CompatVersion.RUBY2_0
        ParserConfiguration config = new ParserConfiguration(0, version)
        Node result = null

        try {
            result = rubyParser.parse("<code>", reader, config)
        } catch (SyntaxException ex) {
            log.error "Problem to visit file $path: ${ex.message}"
            def index = ex.message.indexOf(",")
            def msg = index >= 0 ? ex.message.substring(index + 1).trim() : ex.message.trim()
            compilationErrors += [path: path, msg: msg]
        }
        finally {
            reader?.close()
        }
        result
    }

    private Node generateAst(String path) {
        FileReader reader = new FileReader(path)
        this.generateAst(reader, path)
    }

    private Node generateAst(String path, String content) {
        StringReader reader = new StringReader(content)
        this.generateAst(reader, path)
    }

    private generateProjectRoutes() {
        def node = this.generateAst(routesFile)
        RubyConfigRoutesVisitor visitor = new RubyConfigRoutesVisitor(node)
        def allRoutes = visitor?.routingMethods
        problematicRoutes = allRoutes.findAll{
            !it.arg.contains("#") || (it.name!="root" && it.value==~/[\/\\(?\.*\\)?]+/ && it.value!="/")
        }
        routes = allRoutes - problematicRoutes
        routes.collect{
            it.value = it.value.replaceAll("//", "/")
            it
        }
    }

    private extractMethodReturnUsingArgs(pageMethod){ //keywords: file, name, args
        def result = []
        def pageVisitor = new RubyConditionalVisitor(pageMethod.name, pageMethod.args)
        this.generateAst(pageMethod.file)?.accept(pageVisitor)
        result += pageVisitor.pages
        result += pageVisitor.auxiliaryMethods
        result
    }

    private extractAllPossibleReturnFromMethod(pageMethod){ //keywords: file, name, args
        def pageVisitor = new RubyMethodReturnVisitor(pageMethod.name, pageMethod.args)
        generateAst(pageMethod.file)?.accept(pageVisitor) //extracts path from method
        pageVisitor.values
    }

    private extractDataFromAuxiliaryMethod(pageMethod) { //keywords: file, name, args
        def result = [] as Set
        def specificFailed = false
        def extractSpecificReturn = !(pageMethod.args.empty)
        if (extractSpecificReturn) {
            log.info "extracting a specific return..."
            result += this.extractMethodReturnUsingArgs(pageMethod)
            if(result?.empty) specificFailed = true
        }
        if (!extractSpecificReturn || specificFailed) { //tries to extract all possible return values
            log.info "extracting all possible returns..."
            result += this.extractAllPossibleReturnFromMethod(pageMethod)
        }

        [result: result, specificReturn:extractSpecificReturn, specificFailed:specificFailed]
    }

    private registryPathData(RubyTestCodeVisitor visitor, String path){
        def foundView = false
        def data = this.registryControllerAndActionUsage(visitor, path)
        def registryMethodCall = data.registry
        if(registryMethodCall) {
            def viewData = this.registryViewRelatedToAction(visitor, data.controller, data.action)
            foundView = viewData.found
            /*if(!foundView){
                viewData = this.registryViewRelatedToPath(visitor, path)
                foundView = viewData.found
            }*/
        }
        [registryMethodCall:registryMethodCall, foundView:foundView]
    }

    private extractActionFromView(String view){
        def index1 = view.lastIndexOf("/")
        def index2 = view.indexOf(".")
        def action = view.substring(index1+1, index2)
        def controller = view.substring(0,index1) - this.repositoryPath
        if(action && controller) return "$controller#$action"
        else return null
    }

    private registryMethodCallAndViewAccessFromRoute(RubyTestCodeVisitor visitor, String path) {
        def registryMethodCall = false
        def foundView = false

        if(RouteHelper.routeIsFile(path)){
            log.info "ROUTE IS FILE: $path"
            if(RouteHelper.isViewFile(path)){
                def views = viewFiles?.findAll { it.contains(path) }
                if(views && !views.empty){ //no case execute it yet
                    visitor?.taskInterface?.referencedPages += views
                    interfaceFromViews.referencedPages += views
                    foundView = true
                    //trying to find controller and action by view seems risky
                    /*def methodCall
                    views?.each{ view ->
                        def action = this.extractActionFromView(view)
                        log.info "ACTION EXTRACTED FROM VIEW '$view': $action"
                        if(action) {
                            def data = this.registryControllerAndActionUsage(visitor, path)
                            methodCall = data.registry
                            if(methodCall) registryMethodCall = methodCall
                        }
                    }*/
                }
            } else {
                log.info "ROUTE FILE IS INVALID: $path"
            }
        }
        else if(RouteHelper.routeIsAction(path)){
            def r = this.registryPathData(visitor, path)
            registryMethodCall = r.registryMethodCall
            foundView = r.foundView
        } else {
            log.info "PATH: $path"
            def candidates = this.routes.findAll{ path == it.value } //trying to find an equal path
            if(!candidates.empty) {
                def candidate = candidates.first()
                log.info "CANDIDATE: $candidate"
                if(candidate.arg && !candidate.arg.empty) {
                    def r = this.registryPathData(visitor, candidate.arg)
                    if(r.registryMethodCall) registryMethodCall = r.registryMethodCall
                    if(r.foundView) foundView = r.foundView
                }
            } else { //if it was not found, we trie to find a compatible one
                candidates = this.routes.findAll{ path ==~ /${it.value}/ }
                if(candidates.empty) {
                    def viewData = this.registryViewRelatedToPath(visitor, path)
                    foundView = viewData.found
                } else {
                    def candidate = candidates.first()
                    log.info "CANDIDATE: $candidate"
                    if(candidate.arg && !candidate.arg.empty) {
                        def r = this.registryPathData(visitor, candidate.arg)
                        if(r.registryMethodCall) registryMethodCall = r.registryMethodCall
                        if(r.foundView) foundView = r.foundView
                    }
                }
            }
        }

        [path:path, call:registryMethodCall, view:foundView]
    }

    private static extractControllerAndActionFromPath(String route) {
        def result = null
        def name = route.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        if (name.contains("#")) {
            def index = name.indexOf("#")
            def controller = name.substring(0, index)
            def action = name.substring(index + 1, name.length())
            if (controller && action) result = [controller: controller, action: action]
        }
        result
    }

    private registryUsedPaths(RubyTestCodeVisitor visitor, Set<String> usedPaths) {
        log.info "All used paths (${usedPaths.size()}): $usedPaths"
        if(!usedPaths || usedPaths.empty) return
        def result = []
        usedPaths?.each { path ->
            result += this.registryMethodCallAndViewAccessFromRoute(visitor, path)
        }
        def methodCall = result.findAll{ it.call }*.path
        def view = result.findAll{ it.view }*.path
        def problematic = result.findAll{ !it.call && !it.view }*.path

        log.info "Used paths with method call (${methodCall.size()}): $methodCall"
        log.info "Used paths with view (${view.size()}): $view"
        log.info "All found views until the moment: ${visitor?.taskInterface?.referencedPages}"
        log.info "Used paths with no data (${problematic.size()}): $problematic"
        this.notFoundViews += problematic
    }

    private registryControllerAndActionUsage(RubyTestCodeVisitor visitor, String value){
        def controller = null
        def action = null
        def registryMethodCall = false
        def data = extractControllerAndActionFromPath(value)
        if (data) {
            registryMethodCall = true
            def className = RubyUtil.underscoreToCamelCase(data.controller + "_controller")
            def filePaths = RubyUtil.getClassPathForRubyClass(className, this.projectFiles)
            filePaths.each{ filePath ->
                visitor?.taskInterface?.methods += [name: data.action, type: className, file: filePath]
                interfaceFromViews.methods += [name: data.action, type: className, file: filePath]
            }
            controller = data.controller
            action = data.action
        }
        [controller:controller, action:action, registry:registryMethodCall]
    }

    private registryView(RubyTestCodeVisitor visitor, List<String> views){
        def foundView = false
        if(views && !views.empty) {
            visitor?.taskInterface?.referencedPages += views
            interfaceFromViews.referencedPages += views
            foundView = true
        }
        foundView
    }

    private registryViewRelatedToAction(RubyTestCodeVisitor visitor, String controller, String action){
        def views = RubyUtil.searchViewFor(controller, action, this.viewFiles)
        def foundView = registryView(visitor, views)
        [views: views, found:foundView]
    }

    private registryDirectAccessedViews(RubyTestCodeVisitor visitor, List<String> files){
        log.info "All direct accessed views: ${files.size()}"
        def views = []
        def found = []
        def problematic = []
        files.each{ file ->
            def founds = this.viewFiles.findAll{ it.endsWith(file) }
            founds.each {
                int index1 = it.indexOf(Util.REPOSITORY_FOLDER_PATH)
                views += it.substring(index1+Util.REPOSITORY_FOLDER_PATH.size())
                def index2 = it.indexOf(this.repositoryPath)
                found += it.substring(index2+this.repositoryPath.size()+1)
            }
            if(founds.empty) problematic += file
        }

        problematic.each{ problem ->
            def newName = problem -".html"
            def founds = this.viewFiles.findAll{ it.endsWith(newName) }
            founds.each {
                int index1 = it.indexOf(Util.REPOSITORY_FOLDER_PATH)
                views += it.substring(index1+Util.REPOSITORY_FOLDER_PATH.size())
                def index2 = it.indexOf(this.repositoryPath)
                found += it.substring(index2+this.repositoryPath.size()+1)
            }
            if(!founds.empty) problematic -= problem
        }

        registryView(visitor, views)
        log.info "Found direct accessed views: ${views.size()}"
        views.each{ log.info it.toString() }

        log.info "Not found direct accessed views: ${problematic.size()}"
        problematic.each{ log.info it.toString() }
        this.notFoundViews += problematic
    }

    private registryViewRelatedToPath(RubyTestCodeVisitor visitor, String path){
        def views = []
        def index = path.lastIndexOf("/")
        def controller = path.substring(0,index+1)
        def action = path.substring(index+1)
        def regex = /.*${controller}_?$action.+/
        if(File.separator == "\\") regex = regex.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,"\\\\\\\\")
        def matches = viewFiles?.findAll { it ==~ regex }
        if (matches && matches.size() > 0) {
            if (matches.size() == (1 as int)) views = matches
            else {
                def match = matches.find { it.contains("index") }
                if (match) views += match
                else views = matches
            }
        }
        def foundView = registryView(visitor, views)
        [views: views, found:foundView]
    }

    private registryUsedRailsPaths(RubyTestCodeVisitor visitor, Set<String> railsPathMethods){
        log.info "All used rails path methods (${railsPathMethods.size()}): $railsPathMethods"
        if(!railsPathMethods || railsPathMethods.empty) return
        def methodCall = []
        def view = []
        def foundViews = []
        def projectMethods = []

        railsPathMethods?.each{ method -> //it was used some *_path method generated by Rails
            def registryMethodCall = false
            def foundView = false
            def views = []
            def route = this.routes?.find { it.name ==~ /${method}e?/ }
            if (route){ //it is really a route method
                if (route.arg && route.arg != "") { //there is controller#action related to path method
                    def data = this.registryControllerAndActionUsage(visitor, route.arg)
                    registryMethodCall = data.registry
                    if(registryMethodCall) {
                        methodCall += method
                        //tries to find view file
                        def viewData = registryViewRelatedToAction(visitor, data.controller, data.action)
                        foundView = viewData.found
                        if(foundView){
                            view += method
                            views = viewData.views
                        }
                    }
                }
                /*if (route.value && route.value != "" && !foundView) { //tries to extract data from path
                    def viewData = this.registryViewRelatedToPath(visitor, route.value)
                    foundView = viewData.found
                    if(foundView){
                        view += method
                        views = viewData.views
                    }
                }*/
            } else { //maybe it is a method defined by the project
                def methodName1 = method + RubyConstantData.ROUTE_PATH_SUFIX
                def methodName2 = method + RubyConstantData.ROUTE_URL_SUFIX
                def matches = methods.findAll{ it.name == methodName1 || it.name == methodName2 }
                matches?.each { m ->
                    def newMethod = [name:m.name, type: RubyUtil.getClassName(m.path), file: m.path]
                    visitor?.taskInterface?.methods += newMethod
                    interfaceFromViews.methods += newMethod
                }
                if(!matches.empty) {
                    projectMethods += method
                }
            }
            foundViews += views
            log.info "Found route related to rails path method '${method}': ${registryMethodCall || foundView}"
        }

        def problematic = railsPathMethods - ((methodCall + view + projectMethods)?.unique())
        this.notFoundViews += problematic
        log.info "Rails path methods with method call (${methodCall.size()}): $methodCall"
        log.info "Rails path methods with view (${view.size()}): $view"
        log.info "All found views from rails path methods (${foundViews.size()}): ${foundViews}"
        log.info "Rails path methods that are actually project methods (${projectMethods.size()}): ${projectMethods}"
        log.info "Rails path methods with no data (${problematic.size()}): ${problematic}"
    }

    private extractCallsFromViewFiles(RubyTestCodeVisitor visitor, Set<String> analysedViewFiles){
        def viewFiles = visitor.taskInterface.findAllProdFiles().findAll{ Util.isViewFile(it) }
        if(analysedViewFiles && !analysedViewFiles.empty) viewFiles -= analysedViewFiles
        def calls = []
        viewFiles?.each{ viewFile ->
            def path = Util.REPOSITORY_FOLDER_PATH + viewFile
            path = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/"))
            try{
                def r = []
                String code = viewCodeExtractor?.extractCode(path)
                code?.eachLine { line -> r += Eval.me(line) }
                log.info "Extracted code from view (${r.size()}): $path"
                r.each{ log.info it.toString() }
                calls += r
            } catch(Exception ex){
                def src = new File(path)
                def dst = new File(ConstantData.DEFAULT_VIEW_ANALYSIS_ERROR_FOLDER + File.separator + src.name + counter)
                dst << src.text
                log.error "Error to extract code from view file: $path (${ex.message})"
                counter ++
            }
        }
        calls.unique()
    }

    private organizeRailsPathMethodCalls(calls, RubyTestCodeVisitor visitor){
        def railsPathMethods = calls?.findAll{ it.receiver.empty &&
                (it.name.endsWith(RubyConstantData.ROUTE_PATH_SUFIX) || it.name.endsWith(RubyConstantData.ROUTE_URL_SUFIX) ) }
        def railsMethods = (railsPathMethods*.name).collect{ it - RubyConstantData.ROUTE_PATH_SUFIX - RubyConstantData.ROUTE_URL_SUFIX }
        this.registryUsedRailsPaths(visitor, railsMethods as Set)
        railsPathMethods
    }

    private organizePathAccess(calls, RubyTestCodeVisitor visitor){
        def usedPaths = calls?.findAll{ it.receiver.empty && it.name.contains("/") }
        def paths = usedPaths*.name
        this.registryUsedPaths(visitor, paths as Set)
        usedPaths
    }

    private static organizeClassUsage(calls, RubyTestCodeVisitor visitor){
        def classesOnly = calls?.findAll{ it.name.empty && !it.receiver.empty }
        classesOnly?.each{
            String name = it.receiver
            if(name.startsWith("@")) name = name.substring(1)
            visitor.registryClassUsage(name)
        }
        log.info "Used classes (${classesOnly.size()}): ${classesOnly*.receiver}"
        classesOnly
    }

    private static organizeMethodCallsWithReceiver(calls, RubyTestCodeVisitor visitor){
        def found = []
        def methodsWithReceiver = calls?.findAll{ !it.receiver.empty && !it.name.empty }

        methodsWithReceiver?.each{
            String receiverName = it.receiver
            if(receiverName.startsWith("@")) receiverName = receiverName.substring(1)
            def result = visitor.registryCallFromInstanceVariable(it.name, 0, receiverName)
            if(result) found += it
        }

        log.info "Method calls in views with receiver: ${methodsWithReceiver.size()}"
        methodsWithReceiver.each{ log.info it.toString() }
        log.info "Method calls in views with receiver correctly registered: ${found.size()}"
        found.each{ log.info it.toString() }
        def notFound = methodsWithReceiver - found
        log.info "Method calls in views with receiver with no data: ${notFound.size()}"
        notFound.each{ log.info it.toString() }

        methodsWithReceiver
    }

    //we match method call and method definition based on method name only.
    //the best approach is to also consider the arguments
    private organizeMethodCallsNoReceiver(calls, RubyTestCodeVisitor visitor){
        def methodsUnknownReceiver = calls?.findAll{ it.receiver.empty && !it.name.empty }
        def notFoundMethods = []
        methodsUnknownReceiver.each{ method ->
            def matches = methods.findAll{ it.name == method.name }
            matches?.each { m ->
                def newMethod = [name:m.name, type: RubyUtil.getClassName(m.path), file: m.path]
                visitor?.taskInterface?.methods += newMethod
                interfaceFromViews.methods += newMethod
            }
            if(matches.empty){
                def newMethod = [name: method, type: "Object", file: null]
                visitor?.taskInterface?.methods += newMethod
                interfaceFromViews.methods += newMethod
                notFoundMethods += method
            }
        }
        log.info "Methods calls in views with unknown receiver: ${methodsUnknownReceiver.size()}"
        methodsUnknownReceiver.each{ log.info it.toString() }
        log.info "Methods calls in views with unknown receiver with no data: ${notFoundMethods.size()}"
        notFoundMethods.each{ log.info it.toString() }

        methodsUnknownReceiver
    }

    private organizeViewFileAccess(calls, RubyTestCodeVisitor visitor){
        def files = calls?.findAll{
            it.receiver.empty &&
                    (it.name.endsWith(ConstantData.ERB_EXTENSION) || it.name.endsWith(ConstantData.HAML_EXTENSION))
        }
        def accessedViewFiles = files*.name?.collect{
            String n = it.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            if(!n.contains(Util.VIEWS_FILES_RELATIVE_PATH)) {
                def aux = Util.VIEWS_FILES_RELATIVE_PATH.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                        Matcher.quoteReplacement(File.separator))
                if(!n.startsWith(File.separator)) n = File.separator + n
                n = aux + n
            }
            n
        }
        this.registryDirectAccessedViews(visitor, accessedViewFiles)
        files
    }

    private registryCallsIntoViewFiles(RubyTestCodeVisitor visitor, Set<String> analysedViewFiles){
        if(!(visitor instanceof RubyTestCodeVisitor)) return

        def calls = extractCallsFromViewFiles(visitor, analysedViewFiles)
        if(calls.empty) return

        log.info "All calls from view file(s): ${calls.size()}"
        calls?.each{ log.info it.toString() }

        analysedViewFiles += visitor.taskInterface.findAllProdFiles().findAll{ Util.isViewFile(it) }
        def files = organizeViewFileAccess(calls, visitor)

        def noFiles = calls - files
        def usedPaths = organizePathAccess(noFiles, visitor)

        def railsPathMethods = organizeRailsPathMethodCalls(noFiles-usedPaths, visitor)
        def others = noFiles - (usedPaths + railsPathMethods)

        def classesOnly = organizeClassUsage(others, visitor)
        others -= classesOnly

        def methodsWithReceiver = organizeMethodCallsWithReceiver(others, visitor)
        others -= methodsWithReceiver
        def methodsUnknownReceiver = organizeMethodCallsNoReceiver(others, visitor)
        others -= methodsUnknownReceiver

        log.info "Calls from view file(s) that we can not deal with: ${others.size()}"
        others.each{ log.info it.toString() }

        //check if there is new view files to analyse
        registryCallsIntoViewFiles(visitor, analysedViewFiles)
    }

    @Override
    void findAllPages(TestCodeVisitor visitor) {
        /* generates all routes according to config/routes.rb file */
        if(this.routes.empty) {
            this.generateProjectRoutes()
            log.info "All project routes:"
            this.routes.each { log.info it.toString() }
            log.info "Problematic routes:"
            this.problematicRoutes.each{ log.info it.toString() }
        }

        /* identifies used routes */
        def calledPaths = visitor?.taskInterface?.calledPageMethods
        calledPaths.removeAll([null])  //it is null if test code references a class or file that does not exist
        def usedPaths = [] as Set

        def auxiliaryMethods =  calledPaths.findAll{ it.file != RubyConstantData.ROUTES_ID }
        def railsPathMethods = (calledPaths - auxiliaryMethods)?.findAll{ !it.name.contains("/") }
        def explicityPaths = calledPaths - (auxiliaryMethods + railsPathMethods)
        usedPaths += explicityPaths*.name

        /* identifies used routes (by auxiliary methods) */
        def usedRoutesByAuxMethods = [] as Set
        auxiliaryMethods?.each{ auxMethod -> //it was used an auxiliary method; the view path must be extracted
            log.info "FIND_ALL_PAGES; visiting file '${auxMethod.file}' and method '${auxMethod.name} (${auxMethod.args})'"
            def data = this.extractDataFromAuxiliaryMethod(auxMethod)
            usedRoutesByAuxMethods += data.result
            def allReturn = false
            if(!data.specificReturn || (data.specificReturn && data.specificFailed) ) allReturn = true
            log.info "Found route related to auxiliary path method '${auxMethod.name}': ${!data.result.empty} (all return: $allReturn)"
        }

        /* deals with rails *_path methods */
        def pathMethodsReturnedByAuxMethods = usedRoutesByAuxMethods.findAll{ it.contains(RubyConstantData.ROUTE_PATH_SUFIX) }
        usedPaths += usedRoutesByAuxMethods - pathMethodsReturnedByAuxMethods
        railsPathMethods = railsPathMethods*.name
        railsPathMethods += pathMethodsReturnedByAuxMethods?.collect{ it - RubyConstantData.ROUTE_PATH_SUFIX }

        /* extracts data from used routes */
        this.registryUsedPaths((RubyTestCodeVisitor) visitor, usedPaths)
        this.registryUsedRailsPaths((RubyTestCodeVisitor) visitor, railsPathMethods as Set)

        /* extracts data from view (ERB or HAML) files (this code must be moved in the future) */
        if(viewCodeExtractor) this.registryCallsIntoViewFiles((RubyTestCodeVisitor) visitor, [] as Set)
    }

    /***
     * Finds all regex expression in a source code file.
     *
     * @param path ruby file
     * @return map identifying the file and its regexs
     */
    @Override
    List<StepRegex> doExtractStepsRegex(String path) {
        def node = this.generateAst(path)
        def visitor = new RubyStepRegexVisitor(path)
        node?.accept(visitor)
        visitor.regexs
    }

    @Override
    List<StepDefinition> doExtractStepDefinitions(String path, String content) {
        def node = this.generateAst(path, content)
        def visitor = new RubyStepDefinitionVisitor(path, content)
        node?.accept(visitor)
        visitor.stepDefinitions
    }

    @Override
    Set doExtractMethodDefinitions(String file) {
        RubyMethodDefinitionVisitor visitor = new RubyMethodDefinitionVisitor()
        visitor.path = file
        def node = this.generateAst(file)
        node?.accept(visitor)
        visitor.methods
    }

    /***
     * Visits a step body and method calls inside it. The result is stored as a field of the returned visitor.
     *
     * @param file List of map objects that identifies files by 'path' and 'lines'.
     * @return visitor to visit method bodies
     */
    @Override
    TestCodeVisitor parseStepBody(FileToAnalyse file) {
        def node = this.generateAst(file.path)
        def visitor = new RubyTestCodeVisitor(projectFiles, file.path, methods)
        def testCodeVisitor = new RubyStepsFileVisitor(file.methods, visitor)
        node?.accept(testCodeVisitor)
        visitor
    }

    /***
     * Visits selected method bodies from a source code file searching for other method calls. The result is stored as a
     * field of the input visitor.
     *
     * @param file a map object that identifies a file by 'path' and 'methods'. A method is identified by its name.
     * @param visitor visitor to visit method bodies
     */
    @Override
    visitFile(file, TestCodeVisitor visitor) {
        def node = this.generateAst(file.path)
        visitor.lastVisitedFile = file.path
        def auxVisitor = new RubyMethodVisitor(file.methods, (RubyTestCodeVisitor) visitor)
        node?.accept(auxVisitor)
    }

    @Override
    TestCodeVisitor parseUnitBody(UnitFile file) {
        def node = generateAst(file.path)
        def visitor = new RubyTestCodeVisitor(projectFiles, file.path, methods)
        visitor.lastVisitedFile = file.path
        visitor.productionClass = file.productionClass //keywords: name, path
        def testCodeVisitor = new RSpecFileVisitor(file.tests*.lines, visitor)
        node?.accept(testCodeVisitor)
        visitor
    }

    @Override
    UnitFile doExtractUnitTest(String path, String content, List<Integer> changedLines) {
        UnitFile unitFile = null
        try {
            def visitor = new RSpecTestDefinitionVisitor(path, content, repositoryPath)
            def node = generateAst(path, content)
            node?.accept(visitor)
            if (visitor.tests.empty) {
                log.info "The unit file does not contain any test definition!"
            } else {
                def changedTests = visitor.tests.findAll { it.lines.intersect(changedLines) }
                if (changedTests) {
                    unitFile = new UnitFile(path: path, tests: changedTests, productionClass: visitor.productionClass)
                } else {
                    log.info "No unit test was changed or the changed one was not found!"
                }
            }
        } catch (FileNotFoundException ex) {
            log.warn "Problem to parse unit test file: ${ex.message}. Reason: The commit deleted it."
        }
        unitFile
    }

    @Override
    String getClassForFile(String path) {
        RubyUtil.getClassName(path)
    }

    @Override
    Set<String> getCodeFromViewAnalysis() {
        interfaceFromViews.findAllProdFiles().sort()
    }
}

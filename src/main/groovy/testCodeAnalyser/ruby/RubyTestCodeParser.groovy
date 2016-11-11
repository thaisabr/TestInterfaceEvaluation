package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.CompatVersion
import org.jrubyparser.Parser
import org.jrubyparser.ast.Node
import org.jrubyparser.lexer.SyntaxException
import org.jrubyparser.parser.ParserConfiguration
import taskAnalyser.task.StepDefinition
import taskAnalyser.task.UnitFile
import testCodeAnalyser.FileToAnalyse
import testCodeAnalyser.StepRegex
import testCodeAnalyser.TestCodeAbstractParser
import testCodeAnalyser.TestCodeVisitor
import testCodeAnalyser.ruby.routes.Route
import testCodeAnalyser.ruby.routes.RubyConfigRoutesVisitor
import testCodeAnalyser.ruby.unitTest.RSpecFileVisitor
import testCodeAnalyser.ruby.unitTest.RSpecTestDefinitionVisitor
import testCodeAnalyser.ruby.views.ErbAnalyser
import util.RegexUtil
import util.Util
import util.ruby.RubyConstantData
import util.ruby.RubyUtil

import java.util.regex.Matcher

@Slf4j
class RubyTestCodeParser extends TestCodeAbstractParser {

    static ErbAnalyser erbAnalyser = new ErbAnalyser()
    String routesFile
    Set<Route> routes

    RubyTestCodeParser(String repositoryPath) {
        super(repositoryPath)
        this.routesFile = repositoryPath + RubyConstantData.ROUTES_FILE
        this.routes = [] as Set
    }

    /***
     * Generates AST for Ruby file.
     * @param path path of interest file
     * @return the root node of the AST
     */
    private Node generateAst(def reader, String path){
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
        routes = visitor?.routingMethods
    }

    private extractMethodReturnUsingArgs(def pageMethod){ //keywords: file, name, args
        def result = []
        def pageVisitor = new RubyConditionalVisitor(pageMethod.name, pageMethod.args)
        this.generateAst(pageMethod.file)?.accept(pageVisitor)
        result += pageVisitor.pages
        result += pageVisitor.auxiliaryMethods
        result
    }

    private extractAllPossibleReturnFromMethod(def pageMethod){ //keywords: file, name, args
        def pageVisitor = new RubyMethodReturnVisitor(pageMethod.name, pageMethod.args)
        generateAst(pageMethod.file)?.accept(pageVisitor) //extracts path from method
        pageVisitor.values
    }

    private extractDataFromAuxiliaryMethod(def pageMethod) { //keywords: file, name, args
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

    private static routeIsFile(String name) {
        name.contains(".") && !name.contains("*")
    }

    private static routeIsAction(String name) {
        name.contains("#")
    }

    private registryPathData(TestCodeVisitor visitor, String path){
        def foundView = false
        def data = this.registryControllerAndActionUsage(visitor, path)
        def registryMethodCall = data.registry
        if(registryMethodCall) {
            def viewData = this.registryViewRelatedToAction(visitor, data.controller, data.action)
            foundView = viewData.found
            if(!foundView){
                viewData = this.registryViewRelatedToPath(visitor, path)
                foundView = viewData.found
            }
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

    private registryMethodCallAndViewAccessFromRoute(TestCodeVisitor visitor, String path) {
        def registryMethodCall = false
        def foundView = false

        if(routeIsFile(path)){
            log.info "ROUTE IS FILE: $path"
            def views = viewFiles?.findAll { it.contains(path) }
            if(views && !views.empty){ //no case execute it yet
                visitor?.taskInterface?.referencedPages += views
                foundView = true
                def methodCall
                views?.each{ view ->
                    def action = this.extractActionFromView(view)
                    log.info "ACTION EXTRACTED FROM VIEW '$view': $action"
                    if(action) {
                        def data = this.registryControllerAndActionUsage(visitor, path)
                        methodCall = data.registry
                        if(methodCall) registryMethodCall = methodCall
                    }
                }
            }
        }
        else if(routeIsAction(path)){
            def r = this.registryPathData(visitor, path)
            registryMethodCall = r.registryMethodCall
            foundView = r.foundView
        } else {
            log.info "PATH: $path"
            def candidates = this.routes.findAll{ path == it.value || path ==~ /${it.value}/ }
            if(candidates.empty){
                def viewData = this.registryViewRelatedToPath(visitor, path)
                foundView = viewData.found
            }
            else{
                candidates.each{ candidate ->
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

    private registryUsedPaths(TestCodeVisitor visitor, Set<String> usedPaths) {
        log.info "All used paths: $usedPaths"
        if(!usedPaths || usedPaths.empty) return
        def result = []
        usedPaths?.each { path ->
            result += this.registryMethodCallAndViewAccessFromRoute(visitor, path)
        }
        def methodCall = result.findAll{ it.call }*.path
        def view = result.findAll{ it.view }*.path
        def problematic = result.findAll{ !it.call && !it.view }*.path

        log.info "Paths with method call: $methodCall"
        log.info "Paths with view: $view"
        log.info "All founded views: ${visitor?.taskInterface?.referencedPages}"
        log.info "Paths with no data: $problematic"
        this.notFoundViews += problematic
    }

    private registryControllerAndActionUsage(TestCodeVisitor visitor, String value){
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
            }
            controller = data.controller
            action = data.action
        }
        [controller:controller, action:action, registry:registryMethodCall]
    }

    private static registryView(TestCodeVisitor visitor, List<String> views){
        def foundView = false
        if(views && !views.empty) {
            visitor?.taskInterface?.referencedPages += views
            foundView = true
        }
        foundView
    }

    private registryViewRelatedToAction(TestCodeVisitor visitor, String controller, String action){
        def views = RubyUtil.searchViewFor(controller, action, this.viewFiles)
        def foundView = registryView(visitor, views)
        [views: views, found:foundView]
    }

    private registryViewRelatedToPath(TestCodeVisitor visitor, String path){
        def views = []
        def matches = viewFiles?.findAll { it ==~ /.*$path.*/ }
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

    private registryUsedRailsPaths(TestCodeVisitor visitor, Set<String> railsPathMethods){
        log.info "All used rails path methods: $railsPathMethods"
        if(!railsPathMethods || railsPathMethods.empty) return
        def methodCall = []
        def view = []
        def foundViews = []

        railsPathMethods?.each{ method -> //it was used some *_path method generated by Rails
            def registryMethodCall = false
            def foundView = false
            def views = []
            def route = this.routes?.find { it.name ==~ /${method}e?/ }
            if (route){
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
                if (route.value && route.value != "" && !foundView) { //tries to extract data from path
                    def viewData = this.registryViewRelatedToPath(visitor, route.value)
                    foundView = viewData.found
                    if(foundView){
                        view += method
                        views = viewData.views
                    }
                }
            }
            foundViews += views
            log.info "Found route related to rails path method '${method}': ${registryMethodCall || foundView}"
        }

        def problematic = railsPathMethods - ((methodCall + view)?.unique())
        this.notFoundViews += problematic

        log.info "Path methods with method call: $methodCall"
        log.info "Path methods with view: $view"
        log.info "All founded views from path methods: ${foundViews}"
        log.info "Path methods with no data: $problematic"
    }

    @Override
    void findAllPages(TestCodeVisitor visitor) {
        /* generates all routes according to config/routes.rb file */
        if(this.routes.empty) {
            this.generateProjectRoutes()
            log.info "All project routes:"
            this.routes.each { log.info it.toString() }
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
        def pathMethodsReturnedByAuxMethods = usedRoutesByAuxMethods.findAll{ it.contains(RubyConstantData.ROUTE_SUFIX) }
        usedPaths += usedRoutesByAuxMethods - pathMethodsReturnedByAuxMethods
        railsPathMethods = railsPathMethods*.name
        railsPathMethods += pathMethodsReturnedByAuxMethods?.collect{ it - RubyConstantData.ROUTE_SUFIX }

        /* extracts data from used routes */
        this.registryUsedPaths(visitor, usedPaths)
        this.registryUsedRailsPaths(visitor, railsPathMethods as Set)

        /* extracts data from erb files (this code must be moved in the future) */
        this.registryCallsFromErb(visitor)
    }

    private static extractCallsFromErb(TestCodeVisitor visitor){
        def erbs = visitor.taskInterface.findAllFiles().findAll{ Util.isErbFile(it) }
        def calls = []
        erbs?.each{ erb ->
            String code = erbAnalyser.extractCode(Util.REPOSITORY_FOLDER_PATH + File.separator +erb)
            code.eachLine { line ->
                calls += Eval.me(line)
            }
        }
        calls.unique()
    }

    private registryCallsFromErb(TestCodeVisitor visitor){
        if(!(visitor instanceof RubyTestCodeVisitor)) return

        def calls = extractCallsFromErb(visitor)

        //access to files or paths
        def usedPaths = calls?.findAll{ it.receiver.empty && it.name.contains("/") }
        def paths = usedPaths*.name
        this.registryUsedPaths(visitor, paths as Set)

        //rails path methods
        def railsPathMethods = (calls - usedPaths)?.findAll{ it.receiver.empty && it.name.contains("_path") }
        def railsMethods = (railsPathMethods*.name)?.collect{ it - RubyConstantData.ROUTE_SUFIX }
        this.registryUsedRailsPaths(visitor, railsMethods as Set)

        def others = (calls - usedPaths - railsPathMethods)

        //class usage only
        def classesOnly = others?.findAll{ it.name.empty && !it.receiver.empty }
        def rubyVisitor = (RubyTestCodeVisitor) visitor
        classesOnly?.each{
            String name = it.name
            if(name.startsWith("@")) name = name.substring(1)
            rubyVisitor.registryClassUsage(name)
        }

        //method call
        def methods = others?.findAll{ !it.receiver.empty && !it.name.empty }
        methods?.each{
            String name = it.name
            if(name.startsWith("@")) name = name.substring(1)
            rubyVisitor.registryCallFromInstanceVariable(name, 0, it.receiver)
        }
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
    def visitFile(def file, TestCodeVisitor visitor) {
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
}

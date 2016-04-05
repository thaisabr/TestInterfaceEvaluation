package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.CompatVersion
import org.jrubyparser.Parser
import org.jrubyparser.ast.Node
import org.jrubyparser.lexer.SyntaxException
import org.jrubyparser.parser.ParserConfiguration
import taskAnalyser.StepDefinition
import taskAnalyser.UnitFile
import testCodeAnalyser.StepRegex
import testCodeAnalyser.TestCodeAbstractParser
import testCodeAnalyser.TestCodeVisitor
import testCodeAnalyser.ruby.unitTest.RSpecFileVisitor
import util.ruby.RubyUtil

@Slf4j
class RubyTestCodeParser extends TestCodeAbstractParser {

    String routesFile
    def routes //name, file, value

    RubyTestCodeParser(String repositoryPath){
        super(repositoryPath)
        routesFile = repositoryPath+RubyUtil.ROUTES_FILE
    }

    /***
     * Generates AST for Ruby file.
     * @param path path of interest file
     * @return the root node of the AST
     */
    static Node generateAst(String path){
        FileReader reader = new FileReader(path)
        Parser rubyParser = new Parser()
        CompatVersion version = CompatVersion.RUBY2_0
        ParserConfiguration config = new ParserConfiguration(0, version)
        def result = null

        try{
            result = rubyParser.parse("<code>", reader, config)
        } catch(SyntaxException ex){
            log.error "Problem to visit file $path: ${ex.message}"
        }
        finally {
            reader?.close()
        }

        result
    }

    static Node generateAst(String path, String content){
        StringReader reader = new StringReader(content)
        Parser rubyParser = new Parser()
        CompatVersion version = CompatVersion.RUBY2_0
        ParserConfiguration config = new ParserConfiguration(0, version)
        def result = null

        try{
            result = rubyParser.parse("<code>", reader, config)
        } catch(SyntaxException ex){
            log.error "Problem to visit file $path: ${ex.message}"
        }
        finally {
            reader?.close()
        }

        result
    }

    /***
     * Finds all regex expression in a source code file.
     *
     * @param path ruby file
     * @return map identifying the file and its regexs
     */
    @Override
    List<StepRegex> doExtractStepsRegex(String path){
        def node = generateAst(path)
        def visitor = new RubyStepRegexVisitor(path)
        node?.accept(visitor)
        visitor.regexs
    }

    @Override
    List<StepDefinition> doExtractStepDefinitions(String path, String content) {
        def node = generateAst(path, content)
        def visitor = new RubyStepDefinitionVisitor(path, content)
        node?.accept(visitor)
        visitor.stepDefinitions
    }

    @Override
    Set doExtractMethodDefinitions(String file) {
        RubyMethodDefinitionVisitor visitor = new RubyMethodDefinitionVisitor()
        visitor.path = file
        def node = generateAst(file)
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
    TestCodeVisitor parseStepBody(def file) {
        def node = generateAst(file.path)
        def visitor = new RubyTestCodeVisitor(projectFiles, file.path, methods)
        visitor.lastVisitedFile = file.path
        def testCodeVisitor = new RubyStepsFileVisitor(file.lines, visitor)
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
        def node = generateAst(file.path)
        visitor.lastVisitedFile = file.path
        def auxVisitor = new RubyMethodVisitor(file.methods, (RubyTestCodeVisitor) visitor)
        node?.accept(auxVisitor)
    }

    private findAllRoutes(){
        ConfigRoutesVisitor visitor = new ConfigRoutesVisitor(routesFile)
        def node = generateAst(routesFile)
        node?.accept(visitor)
        visitor?.routingMethods
    }

    private identifyRoutes(def f){
        def result = []
        if(f.file == RubyUtil.ROUTES_ID) { //search for route
            def route = routes?.find{ it.name == f.name }
            if(route) result += route.arg
            else result += f.name //if the route was not found, searches for path directly
        }
        else { //it was used an auxiliary method; the view path must be extracted
            def pageVisitor = new RubyPageVisitor(viewFiles, f.name)
            generateAst(f.file)?.accept(pageVisitor) //extracts path from method
            if(!pageVisitor.pages.empty) {
                pageVisitor.pages.each { page ->
                    def route = routes?.find{ it.name == page || it.value == page}
                    if(route) {
                        result += route.arg
                    } else {
                        result += page //if the route was not found, searches for path directly
                    }
                }
            }
        }

        def finalResult = []
        result.each{ r ->
            if(r.startsWith("/")){ //dealing with redirects (to validate)
                def newRoutes = identifyRoutes([name:r.substring(1), file:RubyUtil.ROUTES_ID])
                def newResult = newRoutes?.findAll{ it != r.substring(1) }
                if(newResult && !newResult.empty) r = newResult
            }
            finalResult += r
        }

        finalResult
    }

    @Override
    void findAllPages(TestCodeVisitor visitor) {
        def filesToVisit = visitor?.taskInterface?.calledPageMethods
        if(!routes) routes = findAllRoutes()
        log.info "all routes: $routes"

        def result = [] as Set
        filesToVisit = filesToVisit.findAll{ it!= null } //it could be null if the test code references a class or file that does not exist
        filesToVisit?.each{ f ->
            log.info "FIND_ALL_PAGES; visiting file '${f.file}' and method '${f.name}'"
            result += identifyRoutes(f)
        }

        result = result.unique()
        log.info "All pages to identify: $result"
        def founded = []
        result?.each{ page ->
            def foundPages = RubyUtil.findViewPathForRailsProjects(page, viewFiles)
            if(foundPages && !foundPages.empty) {
                visitor?.taskInterface?.referencedPages += foundPages
                founded += page
            }
        }
        log.info "All founded views: ${visitor?.taskInterface?.referencedPages}"
        log.info "Not identified pages: ${result - founded}"
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

}

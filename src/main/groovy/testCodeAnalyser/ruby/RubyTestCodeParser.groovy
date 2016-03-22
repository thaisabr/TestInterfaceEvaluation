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
        FileReader reader = new FileReader(file)
        Parser rubyParser = new Parser()
        CompatVersion version = CompatVersion.RUBY2_0
        ParserConfiguration config = new ParserConfiguration(0, version)

        def result = [] as Set
        RubyMethodDefinitionVisitor visitor = new RubyMethodDefinitionVisitor()
        visitor.path = file

        try{
            def node = rubyParser.parse("<code>", reader, config)
            node.accept(visitor)
            result = visitor.methods
        } catch(SyntaxException ex){
            log.error "Problem to visit file $file: ${ex.message}"
        }
        finally {
            reader?.close()
        }
        result
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

    @Override
    void findAllPages(TestCodeVisitor visitor) {
        def filesToVisit = visitor?.taskInterface?.calledPageMethods
        log.info "filesToVisit: $filesToVisit"
        if(!routes) routes = findAllRoutes()
        log.info "all routes: $routes"

        def result = [] as Set
        filesToVisit = filesToVisit.findAll{ it!= null } //it could be null if the test code references a class or file that does not exist
        filesToVisit?.each{ f ->
            log.info "FIND_ALL_PAGES; visiting file '${f.file}' and method '${f.name}'"
            if(f.file == RubyUtil.ROUTES_ID) { //search for route
                def route = routes?.find{ it.name == f.name }
                if(route) {
                    log.info "founded route: $route"
                    result += route.arg
                } else {
                    log.info "failed to found route: ${f.name}"
                    result += f.name //if the route was not found, searches for path directly
                }
            }
            else { //it was used an auxiliary method; the view path must be extracted
                def pageVisitor = new RubyPageVisitor(viewFiles, f.name)
                generateAst(f.file)?.accept(pageVisitor) //extrai caminho do método
                if(!pageVisitor.pages.empty) {
                    log.info "pageVisitor.pages: ${pageVisitor.pages}"
                    pageVisitor.pages.each { page ->
                        def route = routes?.find{ it.name == page || it.value == page}
                        if(route) {
                            log.info "founded route: $route"
                            result += route.arg
                        } else {
                            log.info "failed to found route: $page"
                            result += page //if the route was not found, searches for path directly
                        }
                    }
                } else log.info "no founded route inside method: ${f.name}"
            }
        }

        log.info "All pages to identify: ${result}"
        result?.each{ page ->
            def foundPages = RubyUtil.findViewPathForRailsProjects(page, viewFiles)
            if(foundPages && !foundPages.empty) {
                visitor?.taskInterface?.referencedPages += foundPages
                log.info "View(s) found: $foundPages"
            }
            else log.info "View(s) not found: $page"
        }
    }

    def findAllRoutes(){
        FileReader reader = null
        ConfigRoutesVisitor visitor = null
        try{
            visitor = new ConfigRoutesVisitor(routesFile)
            CompatVersion version = CompatVersion.RUBY2_0
            reader = new FileReader(routesFile)
            ParserConfiguration config = new ParserConfiguration(0, version)
            Parser rubyParser = new Parser()
            def node = rubyParser.parse("<code>", reader, config)
            node?.accept(visitor)
        } catch(SyntaxException ex){
            log.info "Problem to visit file $routesFile: ${ex.message}"
        }
        finally {
            reader?.close()
        }
        visitor?.routingMethods
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

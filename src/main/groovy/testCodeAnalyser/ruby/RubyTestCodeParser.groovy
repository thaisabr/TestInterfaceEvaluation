package testCodeAnalyser.ruby

import org.jrubyparser.CompatVersion
import org.jrubyparser.Parser
import org.jrubyparser.ast.Node
import org.jrubyparser.lexer.SyntaxException
import org.jrubyparser.parser.ParserConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import taskAnalyser.UnitFile
import testCodeAnalyser.StepRegex
import testCodeAnalyser.TestCodeAbstractParser
import testCodeAnalyser.TestCodeVisitor
import testCodeAnalyser.ruby.unitTest.RSpecFileVisitor


class RubyTestCodeParser extends TestCodeAbstractParser {

    static final Logger log = LoggerFactory.getLogger(RubyTestCodeParser.class)

    RubyTestCodeParser(String repositoryPath){
        super(repositoryPath)
    }

    /***
     * Generates AST for Ruby file.
     * @param path path of interest file
     * @return the root node of the AST
     */
    static Node generateAst(String path){
        FileReader reader = new FileReader(path)
        Parser rubyParser = new Parser()
        CompatVersion version = CompatVersion.RUBY2_0 //CompatVersion.RUBY1_9 //CompatVersion.RUBY1_8
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

        return result
    }

    @Override
    /***
     * Finds all regex expression in a source code file.
     *
     * @param path ruby file
     * @return map identifying the file and its regexs
     */
    List<StepRegex> doExtractStepsRegex(String path){
        def node = generateAst(path)
        def visitor = new RubyStepRegexVisitor(path)
        node?.accept(visitor)
        visitor.regexs
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

    @Override
    /***
     * Visits a step body and method calls inside it. The result is stored as a field of the returned visitor.
     *
     * @param file List of map objects that identifies files by 'path' and 'lines'.
     * @return visitor to visit method bodies
     */
    TestCodeVisitor parseStepBody(def file) {
        def node = generateAst(file.path)
        def visitor = new RubyTestCodeVisitor(projectFiles, file.path, methods)
        visitor.lastVisitedFile = file.path
        def testCodeVisitor = new RubyStepsFileVisitor(file.lines, visitor)
        node?.accept(testCodeVisitor)
        visitor
    }

    @Override
    /***
     * Visits selected method bodies from a source code file searching for other method calls. The result is stored as a
     * field of the input visitor.
     *
     * @param file a map object that identifies a file by 'path' and 'methods'. A method is identified by its name.
     * @param visitor visitor to visit method bodies
     */
    def visitFile(def file, TestCodeVisitor visitor) {
        def node = generateAst(file.path)
        visitor.lastVisitedFile = file.path
        def auxVisitor = new RubyMethodVisitor(file.methods, (RubyTestCodeVisitor) visitor)
        node?.accept(auxVisitor)
    }

    @Override
    void findAllPages(TestCodeVisitor visitor) {
        def pageVisitor = new RubyPageVisitor(viewFiles)
        def filesToVisit = visitor?.taskInterface?.calledPageMethods
        filesToVisit?.each{ f ->
            if(f != null){ //f could be null if the test code references a class or file that does not exist
                pageVisitor.methodName = f.arg
                generateAst(f.file)?.accept(pageVisitor)
            }
        }
        visitor?.taskInterface?.referencedPages = pageVisitor.pages
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

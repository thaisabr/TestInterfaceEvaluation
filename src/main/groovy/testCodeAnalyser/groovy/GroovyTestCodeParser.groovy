package testCodeAnalyser.groovy

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import taskAnalyser.UnitFile
import testCodeAnalyser.StepRegex
import testCodeAnalyser.TestCodeAbstractParser
import testCodeAnalyser.TestCodeVisitor
import util.Util


class GroovyTestCodeParser extends TestCodeAbstractParser {

    static GroovyClassLoader classLoader

    static {
        /******************************* VALORES FIXADOS APENAS PARA RODAR; ORGANIZAR DEPOIS **************************/
        classLoader = new GroovyClassLoader()
        String projectPath = "${System.getProperty("user.home")}${File.separator}Documents${File.separator}GitHub${File.separator}OriginalRgms"
        configurePlugins([], "${System.getProperty("user.home")}${File.separator}.grails${File.separator}ivy-cache")
        configureClassLoader("$projectPath${File.separator}target${File.separator}classes",
                "$projectPath${File.separator}target${File.separator}test-classes${File.separator}functional")
        /**************************************************************************************************************/
    }

    GroovyTestCodeParser(String repositoryPath) {
        super(repositoryPath)
    }

    private static configureClassLoader(String productionPath, String testPath){
        classLoader.addClasspath(productionPath) //compiled code files
        classLoader.addClasspath(testPath) //compiled test code
    }

    private static configurePlugins(List pluginsPath, String dependencyCache){
        if(pluginsPath.isEmpty()){
            def jars = Util.findJarFilesFromDirectory(dependencyCache)
            jars?.each{
                classLoader.addClasspath(it)
            }
        }
        else{
            pluginsPath?.each{ path ->
                classLoader.addClasspath(path)
            }
        }
    }

    private static generateAst(String path){
        def file = new File(path)
        SourceUnit unit = SourceUnit.create(file.name, file.text)
        CompilationUnit compUnit = new CompilationUnit(classLoader)
        compUnit.addSource(unit)
        compUnit.compile(Phases.SEMANTIC_ANALYSIS)
        unit.getAST()
    }

    @Override
    /***
     * Finds all regex expression in a source code file.
     *
     * @param path ruby file
     * @return map identifying the file and its regexs
     */
    List<StepRegex> doExtractStepsRegex(String path) {
        def node = generateAst(path)
        def visitor = new GroovyStepRegexVisitor(path)
        ClassNode classNode = node.scriptClassDummy
        classNode.visitContents(visitor)
        visitor.regexs
    }

    @Override
    Set doExtractMethodDefinitions(String path) {
        def methods = [] as Set
        CompilationUnit compUnit =  new CompilationUnit()
        def file = new File(path)
        SourceUnit unit = SourceUnit.create(file.name, file.text)
        compUnit.addSource(unit)
        compUnit.compile(Phases.CONVERSION)
        def node = unit.getAST()
        ClassNode classNode = node.scriptClassDummy
        classNode.methods.each{
            methods += [name:it.name, className:classNode.name, path:path]
        }
        methods
    }

    @Override
    /***
     * Visits a step body and method calls inside it. The result is stored as a field of the returned visitor.
     *
     * @param file List of map objects that identifies files by 'path' and 'lines'.
     * @return visitor to visit method bodies
     */
    TestCodeVisitor parseStepBody(def file){
        def ast = generateAst(file.path)
        def visitor = new GroovyTestCodeVisitor(repositoryPath, file.path)
        def testCodeVisitor = new GroovyStepsFileVisitor(file.lines, visitor)
        ast.classes.get(0).visitContents(testCodeVisitor)
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
        def ast = generateAst(file.path)
        visitor.lastVisitedFile = file.path
        def auxVisitor = new GroovyMethodVisitor(file.methods, (GroovyTestCodeVisitor) visitor)
        ast.classes.get(0).visitContents(auxVisitor)
    }

    @Override
    void findAllPages(TestCodeVisitor visitor) {
        def pageCodeVisitor = new GroovyPageVisitor(viewFiles)
        def filesToVisit = visitor?.taskInterface?.calledPageMethods*.file as Set
        filesToVisit?.each{ f ->
            if(f != null){ //f could be null if the test code references a class or file that does not exist
                generateAst(f).classes.get(0).visitContents(pageCodeVisitor) //gets url
            }
        }
        visitor?.taskInterface?.referencedPages = pageCodeVisitor.pages
    }

    @Override
    TestCodeVisitor parseUnitBody(UnitFile file) {
        return null
    }

}

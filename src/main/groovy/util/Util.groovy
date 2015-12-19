package util

import commitAnalyser.CodeChange
import gherkin.GherkinDialectProvider
import org.springframework.util.ClassUtils
import util.exception.InvalidLanguageException

import java.util.regex.Matcher

class Util {

    static final FILE_SEPARATOR_REGEX = /(\\|\/)/
    static final NEW_LINE_REGEX = /\r\n|\n/

    static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L"
    static final String FEATURE_FILENAME_EXTENSION = ".feature"
    static final String JAR_FILENAME_EXTENSION = ".jar"
    static final String GROOVY_EXTENSION = ".groovy"
    static final String JAVA_EXTENSION = ".java"
    static final String RUBY_EXTENSION = ".rb"

    static final String GIT_EXTENSION = ".git"
    static final String GITHUB_URL = "https://github.com/";

    static final String REPOSITORY_FOLDER_PATH
    static final String TASKS_FILE
    static final String DEFAULT_TASK_FILE = "tasks.csv"
    static final String GHERKIN_FILES_RELATIVE_PATH
    static final String STEPS_FILES_RELATIVE_PATH
    static final String PRODUCTION_FILES_RELATIVE_PATH
    static final String VIEWS_FILES_RELATIVE_PATH

    static final List<String> STEP_KEYWORDS = new GherkinDialectProvider().defaultDialect.stepKeywords.unique()*.trim()
    static final List<String> PAGE_METHODS = ["to", "at"]

    static final LanguageOption TEST_CODE_LANGUAGE

    static final String PROPERTIES_FILE_NAME = "configuration.properties"
    static final Properties properties

    private static List<String> excludedPath
    private static regex_testCode

    static {
        properties = new Properties()
        loadProperties()
        regex_testCode = configureTestCodeRegex()
        excludedPath = configureExcludedPath()
        REPOSITORY_FOLDER_PATH = configureRepositoryFolderPath()
        TASKS_FILE = configureTasksFilePath()
        GHERKIN_FILES_RELATIVE_PATH = (properties.'spgroup.gherkin.files.relative.path').replaceAll(FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        STEPS_FILES_RELATIVE_PATH = File.separator+(properties.'spgroup.steps.files.relative.path').replaceAll(FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        PRODUCTION_FILES_RELATIVE_PATH = File.separator+(properties.'spgroup.production.files.relative.path').replaceAll(FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        VIEWS_FILES_RELATIVE_PATH = PRODUCTION_FILES_RELATIVE_PATH + File.separator + "views"
        TEST_CODE_LANGUAGE = (properties.'spgroup.language').trim().toUpperCase() as LanguageOption
    }

    private static loadProperties(){
        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(PROPERTIES_FILE_NAME)
        properties.load(is)
    }

    private static configureTasksFilePath(){
        String value = properties.'spgroup.task.file.path'
        if(value!=null && !value.isEmpty()) return value.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        else return DEFAULT_TASK_FILE
    }

    private static configureRepositoryFolderPath(){
        String value = properties.'spgroup.task.repositories.path'
        if(value!=null && !value.isEmpty()){
            value = value.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            if(!value.endsWith(File.separator)) value += File.separator
        }
        else value = "repositories${File.separator}"
        return value
    }

    private static configureExcludedPath(){
        excludedPath = (properties.'spgroup.task.interface.path.toignore').split(",")*.replaceAll(" ", "")
        return excludedPath*.replaceAll(FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator)+Matcher.quoteReplacement(File.separator))
    }

    private static configureTestCodeRegex(){
        def testPath = (properties.'spgroup.task.interface.path.test').split(",")*.replaceAll(" ", "")
        if(testPath.size() > 1){
            regex_testCode = ".*("
            testPath.each{ dir ->
                regex_testCode += dir+"|"
            }
            regex_testCode = regex_testCode.substring(0,regex_testCode.lastIndexOf("|"))
            regex_testCode += ").*"
        }
        else{
            regex_testCode = ".*${testPath.get(0)}.*"
        }

        return regex_testCode.replaceAll(FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator)+Matcher.quoteReplacement(File.separator))
    }

    /***
     * VERSAO ANTIGA, A LOGICA É: SE É UM ARQUIVO VÁLIDO E NÃO É ARQUIVO DE TESTE, É ARQUIVO DE PRODUÇÃO. MAS DEPOIS
     * EU DEFINI NO ARQUIVO DE PROPRIEDADES A CONFIGURACAO DO DIRETORIO QUE REPRESENTA ARQUIVOS DE PRODUCAO. REVER ISSO.
     * Finds all production files among a list of files.
     * @param files
     * @return a list of production files
     */
    static List findAllProductionFiles(List<String> files){
        files?.findAll{ file ->
            !(excludedPath).any{ file.contains(it)} && !isTestCode(file)
        }
    }

    static List findAllProductionFilesFromCodeChanges(List<CodeChange> changes){
        changes?.findAll{ change ->
            !(excludedPath).any{ change.filename.contains(it)} && !isTestCode(change.filename)
        }
    }

    static List findAllTestFiles(List<String> files){
        files?.findAll{ isTestCode(it) }
    }

    static List findAllTestFilesFromCodeChanges(List<CodeChange> changes){
        changes?.findAll{ isTestCode(it.filename) }
    }

    /***
     * Checks if a local file contains test code based on its path.
     * The criteria used (file type to ignore and test path) is defined at configuration.properties file.
     * @param path the file path.
     * @return true if the file contains test code. Otherwise, it returns false.
     */
    static boolean isTestCode(String path){
        if( !(excludedPath).any{ path?.contains(it) } && path==~/$regex_testCode/ ) true
        else false
    }

    static String configureGitRepositoryName(String url){
        String name = url - GITHUB_URL - GIT_EXTENSION
        return name.replaceAll("/", "_")
    }

    static String getRepositoriesCanonicalPath(){
        new File(".").getCanonicalPath() + File.separator + REPOSITORY_FOLDER_PATH
    }

    /**
     * Empties a folder.
     *
     * @param folder folder's path.
     */
    static emptyFolder(String folder) {
        def dir = new File(folder)
        def files = dir.listFiles()
        if(files != null) {
            files.each{ f ->
                if (f.isDirectory()) emptyFolder(f.getAbsolutePath())
                else f.delete()
            }
        }
    }

    static List<String> findFilesFromDirectoryByLanguage(String directory){
        def files = findFilesFromDirectory(directory)
        switch (TEST_CODE_LANGUAGE){
            case LanguageOption.JAVA:
                files = files.findAll{it.contains(JAVA_EXTENSION)}
                break
            case LanguageOption.GROOVY:
                files = files.findAll{it.contains(GROOVY_EXTENSION)}
                break
            case LanguageOption.RUBY:
                files = files.findAll{it.contains(RUBY_EXTENSION)}
                break
            default: throw new InvalidLanguageException()
        }
        return files
    }

    static List<String> findFilesFromDirectory(String directory){
        def f = new File(directory)
        def files = []

        if(!f.exists()) return files

        f?.eachDirRecurse{ dir ->
            dir.listFiles().each{
                if(it.isFile()){
                    files += it.absolutePath.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
                }
            }
        }
        f?.eachFile{
            files += it.absolutePath.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
        files
    }

    /********** A PARTIR DAQUI, TUDO FOI INCLUÍDO APÓS INTEGRAÇÃO COM ANALISADOR DE CÓDIGO GROOVY. *******************/

    static findJarFilesFromDirectory(String directory){
        def files = findFilesFromDirectory(directory)
        files.findAll{it.contains(JAR_FILENAME_EXTENSION)}
    }

    static configClassnameFromMethod(String className){
        if (className.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && className.endsWith(";")) {
            className = className.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), className.length() - 1)
        }
        return className
    }

    static boolean isPageMethod(String referencedMethod){
        if(referencedMethod in PAGE_METHODS) true
        else false
    }

    /***
     *
     * @param className
     * @param extension
     * @param projectFiles
     * @return
     */
    private static getClassPath(String className, String extension, Collection<String> projectFiles){
        def name = ClassUtils.convertClassNameToResourcePath(className)+extension
        name = name.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        projectFiles?.find{ it.endsWith(File.separator+name) }
    }

    /***
     * Provides the file path of a Groovy class.
     * @param className class full name
     * @param projectFiles list of file names
     * @return the file name that matches to the class name
     */
    static String getClassPathForGroovy(String className, Collection<String> projectFiles){
        getClassPath(className, GROOVY_EXTENSION, projectFiles)
    }

    /***
     * Provides the file path of a Java class.
     * @param className class full name
     * @param projectFiles list of file names
     * @return the file name that matches to the class name
     */
    static String getClassPathForJava(String className, Collection<String> projectFiles){
        getClassPath(className, JAVA_EXTENSION, projectFiles)
    }

    /***
     * Provides the file path of a Ruby class or module.
     * @param className class or module short name
     * @param projectFiles list of file names
     * @return the file name that matches to the class or module name
     */
    static String getClassPathForRuby(String className, Collection<String> projectFiles){
        def name = (className.toLowerCase()+RUBY_EXTENSION).replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def regex_expression = ".*${Matcher.quoteReplacement(File.separator)}$name"
        projectFiles?.find{ it ==~ /$regex_expression/ }
    }

    /**
     * (A FAZER; DEFINIR REGRA DE MAPEAMENTO) ISSO É ESPECÍFICO PARA RUBY
     * @param resourcePath pode ser url, diretório local ou arquivo local
     * @param projectFiles
     * @return
     */
    static String findViewPathForRailsProjects(String resourcePath, List projectFiles){
        def name = resourcePath.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        int n = name.count(File.separator)
        if(n>1){
            def index = name.lastIndexOf(File.separator)
            name = name.substring(0,index)
        }
        def match = projectFiles?.find{ it.contains(name) }
        if(match) name = match
        else name = ""
        name
    }

    /**
     * ISSO É ESPECÍFICO PARA GROOVY
     */
    static String findViewPathForGrailsProjects(String resourcePath, List projectFiles){
        def name = resourcePath.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        int n = name.count(File.separator)
        if(n>1){
            def index = name.lastIndexOf(File.separator)
            name = name.substring(0,index)
        }
        def match = projectFiles?.find{ it.contains(name) }
        if(match) name = match
        else name = ""
        name
    }

    /**
     * ISSO É ESPECÍFICO PARA GROOVY
     */
    static boolean isValidClassByAPI(String referencedClass){
        def INVALID_CLASS_REGEX = /.*(groovy|java|springframework|apache|grails|spock|geb|selenium|cucumber).*/
        if(INVALID_CLASS_REGEX) {
            if(referencedClass ==~ INVALID_CLASS_REGEX) false
            else true
        }
        else true
    }

    /**
     * ISSO É ESPECÍFICO PARA GROOVY
     * Filters method calls to ignore methods provided by API.
     * @param referencedClass
     * @param path
     * @return true if is a valid class. Otherwise, returns false.
     */
    static boolean isValidClass(String referencedClass, String path){
        if(path!=null && !path.isEmpty() && isValidClassByAPI(referencedClass)) true
        else false
    }

}

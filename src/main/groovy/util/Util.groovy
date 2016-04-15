package util

import commitAnalyser.CodeChange
import org.springframework.util.ClassUtils
import util.exception.InvalidLanguageException

import java.util.regex.Matcher

class Util {

    protected static final String REPOSITORY_FOLDER_PATH
    protected static final String GHERKIN_FILES_RELATIVE_PATH
    protected static final String STEPS_FILES_RELATIVE_PATH
    protected static final String UNIT_TEST_FILES_RELATIVE_PATH
    protected static final String PRODUCTION_FILES_RELATIVE_PATH
    protected static final String VIEWS_FILES_RELATIVE_PATH
    protected static final String VALID_EXTENSION
    protected static final List<String> VALID_EXTENSIONS
    protected static final List<String> VALID_FOLDERS
    protected static final List<String> VALID_VIEW_FILES
    protected static final LanguageOption CODE_LANGUAGE
    protected static final Properties properties
    protected static List<String> excludedPath
    protected static List<String> excludedExtensions
    protected static List<String> excludedFolders
    protected static regex_testCode

    public static final String TASKS_FILE

    static {
        properties = new Properties()
        loadProperties()
        regex_testCode = configureTestCodeRegex()
        excludedPath = configureExcludedPath()
        excludedExtensions = excludedPath.findAll{ it.startsWith(".") }
        excludedFolders = excludedPath - excludedExtensions
        REPOSITORY_FOLDER_PATH = configureRepositoryFolderPath()
        TASKS_FILE = configureTasksFilePath()
        GHERKIN_FILES_RELATIVE_PATH = (properties.'spgroup.gherkin.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        STEPS_FILES_RELATIVE_PATH = (properties.'spgroup.steps.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        UNIT_TEST_FILES_RELATIVE_PATH = (properties.'spgroup.rspec.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        PRODUCTION_FILES_RELATIVE_PATH = (properties.'spgroup.production.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        VIEWS_FILES_RELATIVE_PATH = PRODUCTION_FILES_RELATIVE_PATH + File.separator + "views"
        CODE_LANGUAGE = (properties.'spgroup.language').trim().toUpperCase() as LanguageOption

        switch(CODE_LANGUAGE){
            case LanguageOption.RUBY:
                VALID_EXTENSION = ConstantData.RUBY_EXTENSION
                VALID_VIEW_FILES = [".html", ".html.haml", ".html.erb", ".html.slim"]
                break
            case LanguageOption.GROOVY:
                VALID_EXTENSION = ConstantData.GROOVY_EXTENSION
                VALID_VIEW_FILES = []
                break
            case LanguageOption.JAVA:
                VALID_EXTENSION = ConstantData.JAVA_EXTENSION
                VALID_VIEW_FILES = []
                break
        }

        VALID_EXTENSIONS = [VALID_EXTENSION] + VALID_VIEW_FILES + [ConstantData.FEATURE_FILENAME_EXTENSION]
        VALID_FOLDERS = [GHERKIN_FILES_RELATIVE_PATH, UNIT_TEST_FILES_RELATIVE_PATH, PRODUCTION_FILES_RELATIVE_PATH]
    }

    private static loadProperties(){
        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(ConstantData.PROPERTIES_FILE_NAME)
        properties.load(is)
    }

    private static configureTasksFilePath(){
        String value = properties.'spgroup.task.file.path'
        if(value!=null && !value.isEmpty()) return value.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        else return ConstantData.DEFAULT_TASK_FILE
    }

    private static configureRepositoryFolderPath(){
        String value = properties.'spgroup.task.repositories.path'
        if(value!=null && !value.isEmpty()){
            value = value.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            if(!value.endsWith(File.separator)) value += File.separator
        }
        else value = "repositories${File.separator}"
        return value
    }

    private static configureExcludedPath(){
        excludedPath = (properties.'spgroup.task.interface.path.toignore').split(",")*.replaceAll(" ", "")
        return excludedPath*.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
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

        if(File.separator == "\\"){
            return regex_testCode.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                    Matcher.quoteReplacement(File.separator)+Matcher.quoteReplacement(File.separator))
        }
        else {
            return regex_testCode.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
    }

    static String configureGitRepositoryName(String url){
        String name = url - ConstantData.GITHUB_URL - ConstantData.GIT_EXTENSION
        return name.replaceAll("/", "_")
    }

    static String getRepositoriesCanonicalPath(){
        new File(".").getCanonicalPath() + File.separator + REPOSITORY_FOLDER_PATH
    }

    /***
     * Checks if a local file contains production code based on its path.
     * The criteria used (file type to ignore and production path) is defined at configuration.properties file.
     *
     * @param path the file path
     * @return true if the file contains production code. Otherwise, it returns false
     */
    static boolean isProductionCode(String path){
        if(path == null || path == "") false
        else if( (VALID_VIEW_FILES+VALID_EXTENSION).any{ path.endsWith(it)} &&
                path ==~ /.*$PRODUCTION_FILES_RELATIVE_PATH$RegexUtil.FILE_SEPARATOR_REGEX.*/ ) true
        else false
    }

    /***
     * Finds all production files among a list of files.
     *
     * @param files a list of file paths
     * @return a list of production file paths
     */
    static Collection<String> findAllProductionFiles(Collection<String> files){
        files?.findAll{ file ->
            (VALID_VIEW_FILES+VALID_EXTENSION).any{file.endsWith(it)} &&
                    file ==~ /.*$PRODUCTION_FILES_RELATIVE_PATH$RegexUtil.FILE_SEPARATOR_REGEX.*/
        }
    }

    /***
     * Finds all production files among a list of code changes.
     *
     * @param changes a list of code changes
     * @return a list of production code changes
     */
    static List<CodeChange> findAllProductionFilesFromCodeChanges(List<CodeChange> changes){
        changes?.findAll{ change ->
            (VALID_VIEW_FILES+VALID_EXTENSION).any{ change.filename.endsWith(it)} &&
                    change.filename ==~ /.*$PRODUCTION_FILES_RELATIVE_PATH$RegexUtil.FILE_SEPARATOR_REGEX.*/
        }
    }

    /***
     * Finds all test files among a list of files.
     *
     * @param files a list of file paths
     * @return a list of test file paths
     */
    static List findAllTestFiles(List<String> files){
        files?.findAll{ isTestCode(it) }
    }

    /***
     * Finds all test files among a list of code changes.
     *
     * @param changes a list of code changes
     * @return a list of test code changes
     */
    static List findAllTestFilesFromCodeChanges(List<CodeChange> changes){
        changes?.findAll{ isTestCode(it.filename) }
    }

    /***
     * Checks if a local file contains test code based on its path.
     * The criteria used (file type to ignore and test path) is defined at configuration.properties file.
     *
     * @param path the file path
     * @return true if the file contains test code. Otherwise, it returns false
     */
    static boolean isTestCode(String path){
        if(path == null || path == "") false
        else if( !(excludedPath).any{ path?.contains(it) } && path==~/$regex_testCode/ ) true
        else false
    }

    static boolean isValidCode(String path){
        if( path && !path.empty && VALID_FOLDERS.any{path.contains(it+File.separator)} && VALID_EXTENSIONS.any{path.endsWith(it)} ) true
        else false
    }

    static boolean isStepDefinitionCode(String path){
        if(path == null || path == "") false
        else if(path.contains(STEPS_FILES_RELATIVE_PATH+File.separator) &&
                path.endsWith(stepFileExtension())) true
        else false
    }

    static boolean isGherkinCode(String path){
        if(path == null || path == "") false
        else if(path.endsWith(ConstantData.FEATURE_FILENAME_EXTENSION)) true
        else false
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
        switch (CODE_LANGUAGE){
            case LanguageOption.JAVA:
                files = files.findAll{it.contains(ConstantData.JAVA_EXTENSION)}
                break
            case LanguageOption.GROOVY:
                files = files.findAll{it.contains(ConstantData.GROOVY_EXTENSION)}
                break
            case LanguageOption.RUBY:
                files = files.findAll{it.contains(ConstantData.RUBY_EXTENSION)}
                break
            default: throw new InvalidLanguageException()
        }
        return files
    }

    static String stepFileExtension(){
        switch (CODE_LANGUAGE){
            case LanguageOption.JAVA:
                return ConstantData.JAVA_EXTENSION
                break
            case LanguageOption.GROOVY:
                return ConstantData.GROOVY_EXTENSION
                break
            case LanguageOption.RUBY:
                return ConstantData.RUBY_EXTENSION
                break
            default: throw new InvalidLanguageException()
        }
    }

    static List<String> findFilesFromDirectory(String directory){
        def f = new File(directory)
        def files = []

        if(!f.exists()) return files

        f?.eachDirRecurse{ dir ->
            dir.listFiles().each{
                if(it.isFile()) files += it.absolutePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            }
        }
        f?.eachFile{
            if(it.isFile()) files += it.absolutePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
        files
    }

    static String underscoreToCamelCase(String underscore){
        if(!underscore || underscore.empty || underscore.isAllWhitespace()) return ""
        def name = underscore[0].toUpperCase()+underscore.substring(1)
        name.replaceAll(/_\w/){ it[1].toUpperCase() }
    }

    static String camelCaseToUnderscore(String camelCase){
        if(!camelCase || camelCase.empty || camelCase.isAllWhitespace()) return ""
        camelCase.replaceAll(/(\B[A-Z])/,'_$1').toLowerCase()
    }

    static findJarFilesFromDirectory(String directory){
        def files = findFilesFromDirectory(directory)
        files.findAll{it.contains(ConstantData.JAR_FILENAME_EXTENSION)}
    }

    private static getClassPath(String className, String extension, Collection<String> projectFiles){
        def name = ClassUtils.convertClassNameToResourcePath(className)+extension
        name = name.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        projectFiles?.find{ it.endsWith(File.separator+name) }
    }

}

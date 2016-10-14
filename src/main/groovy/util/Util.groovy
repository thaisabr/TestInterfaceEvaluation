package util

import org.springframework.util.ClassUtils
import util.exception.InvalidLanguageException
import java.util.regex.Matcher

class Util {

    public static String FRAMEWORK_PATH
    public static String GEMS_PATH
    public static String ACTIVESUPPORT_INFLECTOR_PATH
    public static String I18N_PATH
    public static final List<String> FRAMEWORK_FILES
    public static final String REPOSITORY_FOLDER_PATH
    public static final String GHERKIN_FILES_RELATIVE_PATH
    public static final String STEPS_FILES_RELATIVE_PATH
    public static final String UNIT_TEST_FILES_RELATIVE_PATH
    public static final String PRODUCTION_FILES_RELATIVE_PATH
    public static final String VIEWS_FILES_RELATIVE_PATH
    public static final String CONTROLLER_FILES_RELATIVE_PATH
    public static final String MODEL_FILES_RELATIVE_PATH
    public static final String LIB_RELATIVE_PATH
    public static final String TASKS_FILE
    public static final List<String> VALID_FOLDERS
    protected static final String VALID_EXTENSION
    protected static final List<String> VALID_EXTENSIONS
    protected static final List<String> VALID_VIEW_FILES
    protected static final LanguageOption CODE_LANGUAGE
    protected static final Properties properties

    static {
        properties = new Properties()
        loadProperties()
        configureRailsPaths()
        FRAMEWORK_FILES = findFilesFromDirectory(FRAMEWORK_PATH)
        REPOSITORY_FOLDER_PATH = configureRepositoryFolderPath()
        TASKS_FILE = configureTasksFilePath()
        GHERKIN_FILES_RELATIVE_PATH = (properties.'spgroup.gherkin.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        STEPS_FILES_RELATIVE_PATH = (properties.'spgroup.steps.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        UNIT_TEST_FILES_RELATIVE_PATH = (properties.'spgroup.unit.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        PRODUCTION_FILES_RELATIVE_PATH = (properties.'spgroup.production.files.relative.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        VIEWS_FILES_RELATIVE_PATH = PRODUCTION_FILES_RELATIVE_PATH + File.separator + "views"
        CONTROLLER_FILES_RELATIVE_PATH = PRODUCTION_FILES_RELATIVE_PATH + File.separator + "controllers"
        MODEL_FILES_RELATIVE_PATH = PRODUCTION_FILES_RELATIVE_PATH + File.separator + "models"
        CODE_LANGUAGE = (properties.'spgroup.language').trim().toUpperCase() as LanguageOption

        switch(CODE_LANGUAGE){
            case LanguageOption.RUBY:
                VALID_EXTENSION = ConstantData.RUBY_EXTENSION
                VALID_VIEW_FILES = [".html", ".html.haml", ".html.erb", ".html.slim"]
                LIB_RELATIVE_PATH = "lib"
                break
            case LanguageOption.GROOVY:
                VALID_EXTENSION = ConstantData.GROOVY_EXTENSION
                VALID_VIEW_FILES = []
                LIB_RELATIVE_PATH = ""
                break
            case LanguageOption.JAVA:
                VALID_EXTENSION = ConstantData.JAVA_EXTENSION
                VALID_VIEW_FILES = []
                LIB_RELATIVE_PATH = ""
                break
        }

        VALID_EXTENSIONS = [VALID_EXTENSION] + VALID_VIEW_FILES + [ConstantData.FEATURE_FILENAME_EXTENSION]
        VALID_FOLDERS = [GHERKIN_FILES_RELATIVE_PATH, UNIT_TEST_FILES_RELATIVE_PATH, PRODUCTION_FILES_RELATIVE_PATH, LIB_RELATIVE_PATH]
    }

    private static configureRailsPaths(){
        FRAMEWORK_PATH = (properties.'spgroup.framework.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        GEMS_PATH = (properties.'spgroup.gems.path').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        def inflectorFolder = (properties.'spgroup.gems.activesupport-inflector.folder').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        ACTIVESUPPORT_INFLECTOR_PATH = GEMS_PATH+Matcher.quoteReplacement(File.separator)+
                inflectorFolder+Matcher.quoteReplacement(File.separator)+"lib"
        def i18nFolder = (properties.'spgroup.gems.i18n.folder').replaceAll(RegexUtil.FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
        I18N_PATH = GEMS_PATH+Matcher.quoteReplacement(File.separator)+i18nFolder+
                Matcher.quoteReplacement(File.separator)+"lib"
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

    static String configureGitRepositoryName(String url){
        String name = url - ConstantData.GITHUB_URL - ConstantData.GIT_EXTENSION
        return name.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
    }

    static String getRepositoriesCanonicalPath(){
        new File(".").getCanonicalPath() + File.separator + REPOSITORY_FOLDER_PATH
    }

    /***
     * Finds all production files among a list of files.
     *
     * @param files a list of file paths
     * @return a list of production file paths
     */
    static Collection<String> findAllProductionFiles(Collection<String> files){
        files?.findAll{ isCoreCode(it) }
    }

    /***
     * Checks if a local file contains test code based on its path.
     * The criteria used (file type to ignore and test path) is defined at configuration.properties file.
     *
     * @param path the file path
     * @return true if the file contains test code. Otherwise, it returns false
     */
    static boolean isTestCode(String path){
        if( path?.contains(UNIT_TEST_FILES_RELATIVE_PATH+File.separator) ||
                path?.contains(GHERKIN_FILES_RELATIVE_PATH+File.separator) ||
                path?.contains(STEPS_FILES_RELATIVE_PATH+File.separator) ||
                path?.contains("test"+File.separator) ){
            true
        }
        else false
    }

    static boolean isValidCode(String path){
        if( VALID_FOLDERS.any{path?.contains(it+File.separator)} && VALID_EXTENSIONS.any{path?.endsWith(it)} ) true
        else false
    }

    static boolean isStepDefinitionCode(String path){
        if( path?.contains(STEPS_FILES_RELATIVE_PATH+File.separator) && path?.endsWith(VALID_EXTENSION) ) true
        else false
    }

    static boolean isGherkinCode(String path){
        if( path?.endsWith(ConstantData.FEATURE_FILENAME_EXTENSION) ) true
        else false
    }

    static boolean isUnitTestCode(String path){
        if( path?.contains(UNIT_TEST_FILES_RELATIVE_PATH+File.separator) && path?.endsWith(VALID_EXTENSION) ) true
        else false
    }

    static boolean isCoreCode(String path){
        if( isValidCode(path) && !isTestCode(path) ) true
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

    static deleteFolder(String folder) {
        def dir = new File(folder)
        def files = dir.listFiles()
        if(files != null) {
            files.each{ f ->
                if (f.isDirectory()) emptyFolder(f.getAbsolutePath())
                else f.delete()
            }
        }
        dir.deleteDir()
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

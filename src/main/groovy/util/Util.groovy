package util

import util.exception.InvalidLanguageException

import java.util.regex.Matcher

abstract class Util {

    static final String GEM_SUFFIX = Matcher.quoteReplacement(File.separator) + "lib"
    static final Properties properties
    public static final String TASKS_FILE
    public static final String REPOSITORY_FOLDER_PATH
    public static final LanguageOption CODE_LANGUAGE
    public static final String GHERKIN_FILES_RELATIVE_PATH
    public static final String STEPS_FILES_RELATIVE_PATH
    public static final String UNIT_TEST_FILES_RELATIVE_PATH
    public static final String PRODUCTION_FILES_RELATIVE_PATH
    public static final String VIEWS_FILES_RELATIVE_PATH
    public static final String CONTROLLER_FILES_RELATIVE_PATH
    public static final String MODEL_FILES_RELATIVE_PATH
    public static final String LIB_RELATIVE_PATH
    public static final String FRAMEWORK_PATH
    public static final List<String> FRAMEWORK_FILES
    public static final List<String> VALID_FOLDERS
    public static final String VALID_EXTENSION
    public static final List<String> VALID_EXTENSIONS
    public static final List<String> VALID_VIEW_FILES
    public static final String GEMS_PATH
    public static final String GEM_INFLECTOR
    public static final String GEM_I18N
    public static final String GEM_PARSER
    public static final String GEM_AST
    public static final boolean VIEW_ANALYSIS

    static {
        properties = new Properties()
        loadProperties()
        TASKS_FILE = configureTasksFilePath()
        REPOSITORY_FOLDER_PATH = configureRepositoryFolderPath()
        CODE_LANGUAGE = configureLanguage()
        GHERKIN_FILES_RELATIVE_PATH = configureGherkin()
        STEPS_FILES_RELATIVE_PATH = configureSteps()
        UNIT_TEST_FILES_RELATIVE_PATH = configureUnitTest()
        PRODUCTION_FILES_RELATIVE_PATH = configureProduction()
        VIEWS_FILES_RELATIVE_PATH = "$PRODUCTION_FILES_RELATIVE_PATH${File.separator}views"
        CONTROLLER_FILES_RELATIVE_PATH = "$PRODUCTION_FILES_RELATIVE_PATH${File.separator}controllers"
        MODEL_FILES_RELATIVE_PATH = "$PRODUCTION_FILES_RELATIVE_PATH${File.separator}models"
        FRAMEWORK_PATH = configureFramework()
        FRAMEWORK_FILES = findFilesFromDirectory(FRAMEWORK_PATH)

        //configure language dependents
        switch (CODE_LANGUAGE) {
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

        VALID_EXTENSIONS = [VALID_EXTENSION] + VALID_VIEW_FILES + [ConstantData.FEATURE_EXTENSION]
        VALID_FOLDERS = [GHERKIN_FILES_RELATIVE_PATH, UNIT_TEST_FILES_RELATIVE_PATH, PRODUCTION_FILES_RELATIVE_PATH,
                         LIB_RELATIVE_PATH]

        GEMS_PATH = (properties.(ConstantData.PROP_GEMS)).replace(File.separator, Matcher.quoteReplacement(File.separator))
        GEM_INFLECTOR = configureGemInflector()
        GEM_I18N = configureGemI18n()
        GEM_PARSER = configureGemParser()
        GEM_AST = configureGemAst()
        VIEW_ANALYSIS = configureViewAnalysis()
    }

    private static loadProperties() {
        File configFile = new File(ConstantData.PROPERTIES_FILE_NAME)
        FileInputStream resourceStream = new FileInputStream(configFile)
        properties.load(resourceStream)
    }

    private static configureMandatoryProperties(String value, String defaultValue) {
        if (!value || value.empty) value = defaultValue
        value.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
    }

    private static boolean configureBooleanProperties(String value, boolean defaultValue) {
        if (!value || value.empty) defaultValue
        else Boolean.valueOf(value)
    }

    private static configureTasksFilePath() {
        configureMandatoryProperties(properties.(ConstantData.PROP_TASK_FILE), ConstantData.DEFAULT_TASK_FILE)
    }

    private static configureRepositoryFolderPath() {
        def value = configureMandatoryProperties(properties.(ConstantData.PROP_REPOSITORY), ConstantData.DEFAULT_REPOSITORY_FOLDER)
        if (!value.endsWith(File.separator)) value += File.separator
        value
    }

    private static configureLanguage(){
        def value = configureMandatoryProperties(properties.(ConstantData.PROP_CODE_LANGUAGE), ConstantData.DEFAULT_LANGUAGE)
        value.trim().toUpperCase() as LanguageOption
    }

    private static configureGherkin(){
        configureMandatoryProperties(properties.(ConstantData.PROP_GHERKIN), ConstantData.DEFAULT_GHERKIN_FOLDER)
    }

    private static configureSteps(){
        configureMandatoryProperties(properties.(ConstantData.PROP_STEPS), ConstantData.DEFAULT_STEPS_FOLDER)
    }

    private static configureUnitTest(){
        configureMandatoryProperties(properties.(ConstantData.PROP_UNIT_TEST), ConstantData.DEFAULT_UNITY_FOLDER)
    }

    private static configureProduction(){
        configureMandatoryProperties(properties.(ConstantData.PROP_PRODUCTION), ConstantData.DEFAULT_PRODUCTION_FOLDER)
    }

    private static configureFramework(){
        configureMandatoryProperties(properties.(ConstantData.PROP_FRAMEWORK), "")
    }

    private static configureGem(String value, String defaultValue){
        def folder = configureMandatoryProperties(value, defaultValue)
        GEMS_PATH + Matcher.quoteReplacement(File.separator) + folder + GEM_SUFFIX
    }

    private static configureGemInflector(){
        configureGem(properties.(ConstantData.PROP_GEM_INFLECTOR), ConstantData.DEFAULT_GEM_INFLECTOR)
    }

    private static configureGemI18n(){
        configureGem(properties.(ConstantData.PROP_GEM_I18N), ConstantData.DEFAULT_GEM_I18N_FOLDER)
    }

    private static configureGemParser(){
        configureGem(properties.(ConstantData.PROP_GEM_PARSER), ConstantData.DEFAULT_GEM_PARSER_FOLDER)
    }

    private static configureGemAst(){
        configureGem(properties.(ConstantData.PROP_GEM_AST), ConstantData.DEFAULT_GEM_AST_FOLDER)
    }

    private static boolean configureViewAnalysis(){
        configureBooleanProperties(properties.(ConstantData.PROP_VIEW_ANALYSIS), ConstantData.DEFAULT_VIEW_ANALYSIS)
    }

    static String configureGitRepositoryName(String url) {
        String name = url - ConstantData.GITHUB_URL - ConstantData.GIT_EXTENSION
        return name.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
    }

    static String getRepositoriesCanonicalPath() {
        new File(".").getCanonicalPath() + File.separator + REPOSITORY_FOLDER_PATH
    }

    static Collection<String> findAllProductionFiles(Collection<String> files) {
        files?.findAll { isProductionCode(it) }
    }

    static boolean isTestCode(String path) {
        if (path?.contains(UNIT_TEST_FILES_RELATIVE_PATH + File.separator) ||
                path?.contains(GHERKIN_FILES_RELATIVE_PATH + File.separator) ||
                path?.contains(STEPS_FILES_RELATIVE_PATH + File.separator) ||
                path?.contains("test" + File.separator)) {
            true
        } else false
    }

    static boolean isValidCode(String path) {
        if (VALID_FOLDERS.any { path?.contains(it + File.separator) } && VALID_EXTENSIONS.any {
            path?.endsWith(it) }) true
        else if(VALID_FOLDERS.any { path?.contains(it + File.separator) } && path.count(".")==1 &&
                (path.endsWith(".erb") || path.endsWith(".haml") || path.endsWith(".slim"))) true
        else false
    }

    static boolean isStepDefinitionCode(String path) {
        if (path?.contains(STEPS_FILES_RELATIVE_PATH + File.separator) && path?.endsWith(VALID_EXTENSION)) true
        else false
    }

    static boolean isGherkinCode(String path) {
        if (path?.endsWith(ConstantData.FEATURE_EXTENSION)) true
        else false
    }

    static boolean isUnitTestCode(String path) {
        if (path?.contains(UNIT_TEST_FILES_RELATIVE_PATH + File.separator) && path?.endsWith(VALID_EXTENSION)) true
        else false
    }

    static boolean isProductionCode(String path) {
        if (isValidCode(path) && !isTestCode(path)) true
        else false
    }

    static boolean isErbFile(String path){
        if (path?.contains(VIEWS_FILES_RELATIVE_PATH + File.separator) && path?.endsWith(ConstantData.ERB_EXTENSION)) true
        else false
    }

    static emptyFolder(String folder) {
        def dir = new File(folder)
        def files = dir.listFiles()
        if (files != null) {
            files.each { f ->
                if (f.isDirectory()) emptyFolder(f.getAbsolutePath())
                else f.delete()
            }
        }
    }

    static deleteFolder(String folder) {
        emptyFolder(folder)
        def dir = new File(folder)
        dir.deleteDir()
    }

    static List<String> findFilesFromDirectoryByLanguage(String directory) {
        def files = findFilesFromDirectory(directory)
        switch (CODE_LANGUAGE) {
            case LanguageOption.JAVA:
                files = files.findAll { it.contains(ConstantData.JAVA_EXTENSION) }
                break
            case LanguageOption.GROOVY:
                files = files.findAll { it.contains(ConstantData.GROOVY_EXTENSION) }
                break
            case LanguageOption.RUBY:
                files = files.findAll { it.contains(ConstantData.RUBY_EXTENSION) }
                break
            default: throw new InvalidLanguageException()
        }
        return files
    }

    static List<String> findFilesFromDirectory(String directory) {
        def f = new File(directory)
        def files = []

        if (!f.exists()) return files

        f?.eachDirRecurse { dir ->
            dir.listFiles().each {
                if (it.isFile()) files += it.absolutePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            }
        }
        f?.eachFile {
            if (it.isFile()) files += it.absolutePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
        files.sort()
    }

    static String underscoreToCamelCase(String underscore) {
        if (!underscore || underscore.empty || underscore.isAllWhitespace()) return ""
        def name = underscore[0].toUpperCase() + underscore.substring(1)
        name.replaceAll(/_\w/) { it[1].toUpperCase() }
    }

    static String camelCaseToUnderscore(String camelCase) {
        if (!camelCase || camelCase.empty || camelCase.isAllWhitespace()) return ""
        camelCase.replaceAll(/(\B[A-Z])/, '_$1').toLowerCase()
    }

    static findJarFilesFromDirectory(String directory) {
        def files = findFilesFromDirectory(directory)
        files.findAll { it.contains(ConstantData.JAR_EXTENSION) }
    }

}

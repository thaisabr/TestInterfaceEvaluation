package br.ufpe.cin.tan.util

import br.ufpe.cin.tan.exception.InvalidLanguageException

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
    public static final List<String> SPECIAL_VALID_VIEW_FILES
    public static final String GEMS_PATH
    public static final String GEM_INFLECTOR
    public static final String GEM_I18N
    public static final String GEM_PARSER
    public static final String GEM_AST
    public static final boolean VIEW_ANALYSIS
    public static final boolean CONTROLLER_FILTER
    public static final boolean WHEN_FILTER
    public static final boolean VIEW_FILTER
    public static final boolean MULTIPLE_TASK_FILES
    public static final List<String> COVERAGE_GEMS
    public static final boolean RESTRICT_GHERKIN_CHANGES

    static {
        properties = new Properties()
        loadProperties()
        TASKS_FILE = configureTasksFilePath()
        MULTIPLE_TASK_FILES = TASKS_FILE.empty
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
                SPECIAL_VALID_VIEW_FILES = [ConstantData.ERB_EXTENSION, ConstantData.HAML_EXTENSION, ConstantData.SLIM_EXTENSION]
                VALID_VIEW_FILES = [ConstantData.HTML_HAML_EXTENSION, ConstantData.MOBILE_HAML_EXTENSION,
                                    ConstantData.HTML_ERB_EXTENSION, ConstantData.HTML_SLIM_EXTENSION]
                LIB_RELATIVE_PATH = "lib"
                break
            case LanguageOption.GROOVY:
                VALID_EXTENSION = ConstantData.GROOVY_EXTENSION
                SPECIAL_VALID_VIEW_FILES = []
                VALID_VIEW_FILES = []
                LIB_RELATIVE_PATH = ""
                break
            case LanguageOption.JAVA:
                VALID_EXTENSION = ConstantData.JAVA_EXTENSION
                SPECIAL_VALID_VIEW_FILES = []
                VALID_VIEW_FILES = []
                LIB_RELATIVE_PATH = ""
                break
        }

        VALID_EXTENSIONS = [VALID_EXTENSION] + VALID_VIEW_FILES + [ConstantData.FEATURE_EXTENSION]
        VALID_FOLDERS = [GHERKIN_FILES_RELATIVE_PATH, PRODUCTION_FILES_RELATIVE_PATH, LIB_RELATIVE_PATH]

        GEMS_PATH = (properties.(ConstantData.PROP_GEMS)).replace(File.separator, Matcher.quoteReplacement(File.separator))
        GEM_INFLECTOR = configureGemInflector()
        GEM_I18N = configureGemI18n()
        GEM_PARSER = configureGemParser()
        GEM_AST = configureGemAst()
        VIEW_ANALYSIS = configureViewAnalysis()
        CONTROLLER_FILTER = configureControllerFilter()
        WHEN_FILTER = configureWhenFilter()
        VIEW_FILTER = configureViewFilter()
        createFolders()
        COVERAGE_GEMS = configureCoverageGems()

        RESTRICT_GHERKIN_CHANGES = configureGherkinAdds()
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
        configureMandatoryProperties(properties.(ConstantData.PROP_TASK_FILE), "")
    }

    private static configureRepositoryFolderPath() {
        def value = configureMandatoryProperties("", ConstantData.DEFAULT_REPOSITORY_FOLDER)
        if (!value.endsWith(File.separator)) value += File.separator
        value
    }

    private static configureLanguage() {
        def value = configureMandatoryProperties(properties.(ConstantData.PROP_CODE_LANGUAGE), ConstantData.DEFAULT_LANGUAGE)
        value.trim().toUpperCase() as LanguageOption
    }

    private static configureGherkin() {
        configureMandatoryProperties(properties.(ConstantData.PROP_GHERKIN), ConstantData.DEFAULT_GHERKIN_FOLDER)
    }

    private static configureSteps() {
        configureMandatoryProperties(properties.(ConstantData.PROP_STEPS), ConstantData.DEFAULT_STEPS_FOLDER)
    }

    private static configureUnitTest() {
        configureMandatoryProperties(properties.(ConstantData.PROP_UNIT_TEST), ConstantData.DEFAULT_UNITY_FOLDER)
    }

    private static configureProduction() {
        configureMandatoryProperties(properties.(ConstantData.PROP_PRODUCTION), ConstantData.DEFAULT_PRODUCTION_FOLDER)
    }

    private static configureFramework() {
        configureMandatoryProperties(properties.(ConstantData.PROP_FRAMEWORK), "")
    }

    private static configureGem(String value, String defaultValue) {
        def folder = configureMandatoryProperties(value, defaultValue)
        GEMS_PATH + Matcher.quoteReplacement(File.separator) + folder + GEM_SUFFIX
    }

    private static configureGemInflector() {
        configureGem(properties.(ConstantData.PROP_GEM_INFLECTOR), ConstantData.DEFAULT_GEM_INFLECTOR)
    }

    private static configureGemI18n() {
        configureGem(properties.(ConstantData.PROP_GEM_I18N), ConstantData.DEFAULT_GEM_I18N_FOLDER)
    }

    private static configureGemParser() {
        configureGem(properties.(ConstantData.PROP_GEM_PARSER), ConstantData.DEFAULT_GEM_PARSER_FOLDER)
    }

    private static configureGemAst() {
        configureGem(properties.(ConstantData.PROP_GEM_AST), ConstantData.DEFAULT_GEM_AST_FOLDER)
    }

    private static boolean configureViewAnalysis() {
        configureBooleanProperties(properties.(ConstantData.PROP_VIEW_ANALYSIS), ConstantData.DEFAULT_VIEW_ANALYSIS)
    }

    private static boolean configureControllerFilter() {
        configureBooleanProperties(properties.(ConstantData.PROP_CONTROLLER_FILTER), ConstantData.DEFAULT_CONTROLLER_FILTER)
    }

    private static boolean configureWhenFilter() {
        configureBooleanProperties(properties.(ConstantData.PROP_WHEN_FILTER), ConstantData.DEFAULT_WHEN_FILTER)
    }

    private static boolean configureViewFilter() {
        configureBooleanProperties(properties.(ConstantData.PROP_VIEW_FILTER), ConstantData.DEFAULT_VIEW_FILTER)
    }

    private static boolean configureGherkinAdds() {
        configureBooleanProperties(properties.(ConstantData.PROP_RESTRICT_GHERKIN_CHANGES), ConstantData.DEFAULT_RESTRICT_GHERKIN_CHANGES)
    }

    private static createFolder(String folder) {
        File zipFolder = new File(folder)
        if (!zipFolder.exists()) {
            zipFolder.mkdirs()
        }
    }

    private static createFolders() {
        createFolder(ConstantData.DEFAULT_EVALUATION_FOLDER)
        createFolder(ConstantData.DEFAULT_REPOSITORY_FOLDER)
        createFolder(ConstantData.DEFAULT_VIEW_ANALYSIS_ERROR_FOLDER)
    }

    private static configureCoverageGems() {
        def result = []
        String gems = properties.(ConstantData.PROP_COVERAGE_GEMS)
        if (gems && !gems.empty) {
            result = gems.tokenize(',')*.trim()
        }
        result
    }

    static String configureGitRepositoryName(String url) {
        String name = url - ConstantData.GITHUB_URL - ConstantData.GIT_EXTENSION
        return name.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
    }

    static String getRepositoriesCanonicalPath() {
        new File(".").getCanonicalPath() + File.separator + REPOSITORY_FOLDER_PATH
    }

    static Collection<String> findAllProductionFiles(Collection<String> files) {
        files?.findAll { isProductionFile(it) }
    }

    static boolean isTestFile(String path) {
        if (!path || path.empty) return false
        def p = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def root = extractRootFolder(path)
        p = p - root
        if (p?.contains("${UNIT_TEST_FILES_RELATIVE_PATH}${File.separator}") ||
                p?.contains("${GHERKIN_FILES_RELATIVE_PATH}${File.separator}") ||
                p?.contains("${STEPS_FILES_RELATIVE_PATH}${File.separator}") ||
                p?.contains("test${File.separator}")) {
            true
        } else false
    }

    static boolean isValidFile(String path) {
        if (!path || path.empty) return false
        def p = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def root = extractRootFolder(path)
        p = p - root
        if (VALID_FOLDERS.any { p.startsWith(it + File.separator) } && VALID_EXTENSIONS.any {
            p.endsWith(it)
        }) true
        else if (isViewFile(path)) true
        else false
    }

    static boolean isStepDefinitionFile(String path) {
        if (!path || path.empty) return false
        def p = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def root = extractRootFolder(path)
        p = p - root
        if (p.startsWith(STEPS_FILES_RELATIVE_PATH + File.separator) && p.endsWith(VALID_EXTENSION)) true
        else false
    }

    static boolean isGherkinFile(String path) {
        if (!path || path.empty) return false
        def p = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def root = extractRootFolder(path)
        p = p - root
        if (p.startsWith(ConstantData.DEFAULT_GHERKIN_FOLDER + File.separator) && p.endsWith(ConstantData.FEATURE_EXTENSION)) true
        else false
    }

    static boolean isUnitTestFile(String path) {
        if (!path || path.empty) return false
        def p = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def root = extractRootFolder(path)
        p = p - root
        if (p.startsWith(UNIT_TEST_FILES_RELATIVE_PATH + File.separator) && p.endsWith(VALID_EXTENSION)) true
        else false
    }

    static boolean isProductionFile(String path) {
        if (isValidFile(path) && !isTestFile(path)) true
        else false
    }

    static boolean isViewFile(String path) {
        if (!path || path.empty) return false
        def p = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def root = extractRootFolder(path)
        p = p - root
        def isCommonFile = VALID_VIEW_FILES.any { p.endsWith(it) }
        def isSpecialFile = p.count(".") == 1 && SPECIAL_VALID_VIEW_FILES.any { p.endsWith(it) }
        if (p.startsWith("${VIEWS_FILES_RELATIVE_PATH}${File.separator}") && (isCommonFile || isSpecialFile)) true
        else false
    }

    static boolean isControllerFile(String path) {
        if (!path || path.empty) return false
        def p = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def root = extractRootFolder(path)
        p = p - root
        if (p.startsWith("${CONTROLLER_FILES_RELATIVE_PATH}${File.separator}")) true
        else false
    }

    static extractRootFolder(String path) {
        def root = ""
        if (!path || path.empty) return root
        def p = path?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))

        if (p?.contains(REPOSITORY_FOLDER_PATH)) {
            def i1 = p.indexOf(REPOSITORY_FOLDER_PATH)
            def begin = p.substring(0, i1)
            def temp = p.substring(i1 + REPOSITORY_FOLDER_PATH.size())
            def i2 = temp.indexOf(File.separator)
            def projectFolder = temp.substring(0, i2)
            root = begin + REPOSITORY_FOLDER_PATH + projectFolder + File.separator
            root = root.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
        root
    }

    static organizePathsForInterfaces(Collection<String> files) {
        files?.findResults { i ->
            if (i) {
                if (!i.contains(REPOSITORY_FOLDER_PATH)) i = REPOSITORY_FOLDER_PATH + i
                def root = extractRootFolder(i)
                i - root
            } else null
        }
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
                files = files.findAll { it.endsWith(ConstantData.JAVA_EXTENSION) }
                break
            case LanguageOption.GROOVY:
                files = files.findAll { it.endsWith(ConstantData.GROOVY_EXTENSION) }
                break
            case LanguageOption.RUBY:
                files = files.findAll { it.endsWith(ConstantData.RUBY_EXTENSION) }
                break
            default: throw new InvalidLanguageException()
        }
        return files
    }

    static findTaskFiles() {
        findFilesFromDirectory(ConstantData.DEFAULT_TASKS_FOLDER).findAll {
            it.endsWith(ConstantData.CSV_FILE_EXTENSION)
        }
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

    static List<String> findFoldersFromDirectory(String directory) {
        def f = new File(directory)
        def folders = []

        if (!f.exists()) return folders

        f?.eachDirRecurse { dir ->
            folders += dir.absolutePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
        folders.sort()
    }

    static String underscoreToCamelCase(String underscore) {
        if (!underscore || underscore.empty || underscore.isAllWhitespace()) return ""
        def name = underscore[0].toUpperCase() + underscore.substring(1)
        name.replaceAll(/_\w/) { it[1].toUpperCase() }
    }

    static String camelCaseToUnderscore(String camelCase) {
        if (!camelCase || camelCase.empty || camelCase.isAllWhitespace()) return ""
        camelCase.replaceAll(/(\B[A-Z])/, '_$1').toLowerCase().replaceAll(/::/, Matcher.quoteReplacement(File.separator))
    }

    static findJarFilesFromDirectory(String directory) {
        def files = findFilesFromDirectory(directory)
        files.findAll { it.contains(ConstantData.JAR_EXTENSION) }
    }

    static filterFiles(files) {
        def filteredFiles = files

        //identifying view files
        if (VIEW_FILTER) filteredFiles = files?.findAll { isViewFile(it) }

        //identifying controller files
        if (CONTROLLER_FILTER) filteredFiles = files?.findAll { isControllerFile(it) }

        filteredFiles
    }

}

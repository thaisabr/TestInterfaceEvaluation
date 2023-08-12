package br.ufpe.cin.tan.util

import br.ufpe.cin.tan.exception.InvalidLanguageException
import groovy.util.logging.Slf4j

import java.util.regex.Matcher

@Slf4j
abstract class Util {

    static boolean USING_PROPERTIES_FILE
    static final String GEM_SUFFIX = Matcher.quoteReplacement(File.separator) + "lib"
    static Properties properties
    public static String TASKS_FILE
    public static int TASK_MAX_SIZE
    public static String REPOSITORY_FOLDER_PATH
    public static LanguageOption CODE_LANGUAGE
    public static String GHERKIN_FILES_RELATIVE_PATH
    public static String STEPS_FILES_RELATIVE_PATH
    public static String UNIT_TEST_FILES_RELATIVE_PATH
    public static String PRODUCTION_FILES_RELATIVE_PATH
    public static String VIEWS_FILES_RELATIVE_PATH
    public static String CONTROLLER_FILES_RELATIVE_PATH
    public static String MODEL_FILES_RELATIVE_PATH
    public static String LIB_RELATIVE_PATH
    public static List<String> FRAMEWORK_PATH
    public static String FRAMEWORK_LIB_PATH
    public static List<String> FRAMEWORK_FILES
    public static List<String> VALID_FOLDERS
    public static String VALID_EXTENSION
    public static List<String> VALID_EXTENSIONS
    public static List<String> VALID_VIEW_FILES
    public static List<String> SPECIAL_VALID_VIEW_FILES
    public static String GEMS_PATH
    public static String GEM_INFLECTOR
    public static String GEM_I18N
    public static String GEM_PARSER
    public static String GEM_AST
    public static boolean VIEW_ANALYSIS
    public static boolean CONTROLLER_FILTER
    public static boolean WHEN_FILTER
    public static boolean VIEW_FILTER
    public static boolean MULTIPLE_TASK_FILES
    public static List<String> COVERAGE_GEMS
    public static RESTRICT_GHERKIN_CHANGES
    public static boolean RUNNING_ALL_CONFIGURATIONS
    public static boolean SIMILARITY_ANALYSIS

    static {
        def gemsPathValue = configureFrameworkBySystem()
        if(!gemsPathValue.empty) gemsPathValue = gemsPathValue.first()

        if(FRAMEWORK_PATH.size()==0) {
            FRAMEWORK_FILES = []
        }
        else if(FRAMEWORK_PATH.size()==1) {
            FRAMEWORK_FILES = findFilesFromDirectory(FRAMEWORK_PATH.first())
        }
        else {
            def files = []
            FRAMEWORK_PATH.each{path ->
                files += findFilesFromDirectory(path)
            }
            FRAMEWORK_FILES = files
        }
        log.info "FRAMEWORK_PATH: ${FRAMEWORK_PATH}"
        log.info "FRAMEWORK_FILES: ${FRAMEWORK_FILES.size()}"

        REPOSITORY_FOLDER_PATH = configureRepositoryFolderPath()

        GEMS_PATH = configureGemPath(gemsPathValue)
        log.info "GEMS_PATH: ${GEMS_PATH}"

        loadProperties()
        log.info "Properties were loaded."

        log.info "FRAMEWORK_LIB_PATH: ${FRAMEWORK_LIB_PATH}"
        log.info "GEM_INFLECTOR: ${GEM_INFLECTOR}"
        log.info "GEM_I18N: ${GEM_I18N}"
        log.info "GEM_PARSER: ${GEM_PARSER}"
        log.info "GEM_AST: ${GEM_AST}"
    }

    static void configureEnvironment(String gherkinFilesRelativePath, String stepFilesRelativePath,
                                     String unitFilesRelativePath) {
        GHERKIN_FILES_RELATIVE_PATH = configureGherkin(gherkinFilesRelativePath)
        STEPS_FILES_RELATIVE_PATH = configureSteps(stepFilesRelativePath)
        UNIT_TEST_FILES_RELATIVE_PATH = configureUnitTest(unitFilesRelativePath)
    }

    private static configurePropertiesRelatedToCodeLanguage() {
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
    }

    private static readPropertiesFile() {
        try {
            properties = new Properties()
            File configFile = new File("src${File.separator}main${File.separator}resources${File.separator}" +
                    ConstantData.PROPERTIES_FILE_NAME)
            if (configFile.exists()) {
                FileInputStream fileInputStream = new FileInputStream(configFile)
                properties.load(fileInputStream)
            } else {
                InputStream resourceStream = Util.class.getResourceAsStream(File.separator + ConstantData.PROPERTIES_FILE_NAME)
                properties.load(resourceStream)
            }
            USING_PROPERTIES_FILE = true
        } catch (Exception exception) {
            log.warn "Properties file not found."
            properties = null
            USING_PROPERTIES_FILE = false
        }
    }

    private static boolean loadProperties(){
        readPropertiesFile()
        TASKS_FILE = configureTasksFilePath()
        TASK_MAX_SIZE = configureTaskMaxSize()
        MULTIPLE_TASK_FILES = TASKS_FILE.empty
        CODE_LANGUAGE = configureLanguage()
        GHERKIN_FILES_RELATIVE_PATH = configureGherkin()
        STEPS_FILES_RELATIVE_PATH = configureSteps()
        UNIT_TEST_FILES_RELATIVE_PATH = configureUnitTest()
        PRODUCTION_FILES_RELATIVE_PATH = configureProduction()
        VIEWS_FILES_RELATIVE_PATH = "$PRODUCTION_FILES_RELATIVE_PATH${File.separator}views"
        CONTROLLER_FILES_RELATIVE_PATH = "$PRODUCTION_FILES_RELATIVE_PATH${File.separator}controllers"
        MODEL_FILES_RELATIVE_PATH = "$PRODUCTION_FILES_RELATIVE_PATH${File.separator}models"
        FRAMEWORK_LIB_PATH = configureLib()

        configurePropertiesRelatedToCodeLanguage()

        VALID_EXTENSIONS = [VALID_EXTENSION] + VALID_VIEW_FILES + [ConstantData.FEATURE_EXTENSION]
        VALID_FOLDERS = [GHERKIN_FILES_RELATIVE_PATH, PRODUCTION_FILES_RELATIVE_PATH, LIB_RELATIVE_PATH]

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
        RUNNING_ALL_CONFIGURATIONS = configureRunningConfigurations()
        SIMILARITY_ANALYSIS = configureSimilarityAnalysis()
    }

    private static configureMandatoryProperties(String value, String defaultValue) {
        if (!value || value.empty) value = defaultValue
        value.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
    }

    /*private static identifyGem(String gem){
        switch (gem){
            case GEM_I18N : return ["i18n", "0.7.0"]
            case GEM_INFLECTOR : return ["activesupport-inflector", "0.1.0"]
            case GEM_PARSER : return ["parser", "2.3.1.4"]
            case GEM_AST : return ["ast", "2.3.0"]
        }
    }*/

    /*private static installRequiredGems(String gemProperty){
        def gem = identifyGem(gemProperty)
        def paths = FRAMEWORK_PATH.split(",")
        paths.each{ path ->
            println "installing ${gem.get(0)} ${gem.get(1)}"
            ProcessBuilder builder = new ProcessBuilder("${path}/bin/gem", "install", gem.get(0), "-v", gem.get(1))
            builder.directory(new File(System.getProperty("user.home")))
            Process process = builder.start()
            process.waitFor()
            def output = process.inputStream.readLines()
            process.inputStream.close()
        }
    }*/

    private static boolean configureBooleanProperties(String value, boolean defaultValue) {
        if (!value || value.empty) defaultValue
        else Boolean.valueOf(value)
    }

    private static configureTasksFilePath() {
        configureMandatoryProperties(properties?.(ConstantData.PROP_TASK_FILE), "")
    }

    private static configureTasksFilePath(String value) {
        configureMandatoryProperties(value, "")
    }

    private static int configureTaskMaxSize() {
        def maxSize = ConstantData.DEFAULT_TASK_SIZE
        def value = properties?.(ConstantData.PROP_TASK_MAX_SIZE)
        if (value) maxSize = value as int
        maxSize
    }

    private static int configureTaskMaxSize(String v) {
        def maxSize = ConstantData.DEFAULT_TASK_SIZE
        def value = v
        if (value) maxSize = value as int
        maxSize
    }

    private static configureRepositoryFolderPath() {
        def value = configureMandatoryProperties("", ConstantData.DEFAULT_REPOSITORY_FOLDER)
        if (!value.endsWith(File.separator)) value += File.separator
        value
    }

    private static configureLanguage() {
        def value = configureMandatoryProperties(properties?.(ConstantData.PROP_CODE_LANGUAGE), ConstantData.DEFAULT_LANGUAGE)
        value.trim().toUpperCase() as LanguageOption
    }

    private static configureLanguage(String v) {
        def value = configureMandatoryProperties(v, ConstantData.DEFAULT_LANGUAGE)
        value.trim().toUpperCase() as LanguageOption
    }

    private static configureGherkin() {
        configureMandatoryProperties(properties?.(ConstantData.PROP_GHERKIN), ConstantData.DEFAULT_GHERKIN_FOLDER)
    }

    private static configureGherkin(String v) {
        configureMandatoryProperties(v, ConstantData.DEFAULT_GHERKIN_FOLDER)
    }

    private static configureSteps() {
        configureMandatoryProperties(properties?.(ConstantData.PROP_STEPS), ConstantData.DEFAULT_STEPS_FOLDER)
    }

    private static configureSteps(String v) {
        configureMandatoryProperties(v, ConstantData.DEFAULT_STEPS_FOLDER)
    }

    private static configureUnitTest() {
        configureMandatoryProperties(properties?.(ConstantData.PROP_UNIT_TEST), ConstantData.DEFAULT_UNITY_FOLDER)
    }

    private static configureUnitTest(String v) {
        configureMandatoryProperties(v, ConstantData.DEFAULT_UNITY_FOLDER)
    }

    private static configureProduction() {
        configureMandatoryProperties(properties?.(ConstantData.PROP_PRODUCTION), ConstantData.DEFAULT_PRODUCTION_FOLDER)
    }

    private static configureProduction(String v) {
        configureMandatoryProperties(v, ConstantData.DEFAULT_PRODUCTION_FOLDER)
    }

    private static ArrayList<String> findGemCommand(){
        def path = System.getenv("Path")
        if(path == null) path = System.getenv("PATH")
        def pathValues = path?.split(";")
        def candidates = pathValues?.findAll{ it.endsWith("bin") }
        def match = []
        candidates?.each{candidate ->
            def files = findFilesFromDirectory(candidate)
            match += files?.findAll{ it.contains("${File.separator}gem.")}
        }
        log.info "match in findGemCommand: "
        match?.each{log.info it.toString() }
        return match
    }

    private static findFrameworkAndGemsPath(String command){
        ProcessBuilder builder = new ProcessBuilder(command, "env")
        builder.directory(new File(System.getProperty("user.home")))
        Process process = builder.start()
        process.waitFor()
        def output = process.inputStream.readLines()
        process.inputStream.close()

        log.info "output in findFrameworkAndGemsPath:"
        output.each{ log.info it.toString() }


        def frameworkPath = output.find{ it.contains("EXECUTABLE DIRECTORY:") }
        if(frameworkPath) {
            def index1 = frameworkPath.indexOf(":")
            def index2 = frameworkPath.indexOf("/bin")
            if(index1>-1 && index2>-1) frameworkPath = frameworkPath.substring(index1+2, index2)
            else frameworkPath = ""
        }
        else frameworkPath = ""

        def gemsPath = output.find{ it.contains("INSTALLATION DIRECTORY:") }
        if(gemsPath) {
            def index1 = gemsPath.indexOf(":")
            if(index1>-1) gemsPath = gemsPath.substring(index1+2)
            else gemsPath = ""
        }
        else gemsPath = ""
        [framework: frameworkPath, gems: gemsPath]
    }

    private static configureFrameworkBySystem(){
        def frameworkPath = []
        def gemPath = []
        def commands = findGemCommand()
        commands.each{ command ->
            log.info "command in configureFrameworkBySystem: ${command.toString()}"
            def result = findFrameworkAndGemsPath(command)
            frameworkPath += result.framework.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            gemPath += result.gems
        }
        FRAMEWORK_PATH = frameworkPath
        gemPath
    }

    private static configureLib() {
        configureMandatoryProperties(properties?.(ConstantData.PROP_LIB), GEMS_PATH)
    }

    private static configureLib(String v) {
        configureMandatoryProperties(v, "")
    }

    private static configureGem(String value, String defaultValue) {
        def folder = configureMandatoryProperties(value, defaultValue)
        GEMS_PATH.replace(File.separator, Matcher.quoteReplacement(File.separator)) +
                Matcher.quoteReplacement(File.separator) + "gems" +  Matcher.quoteReplacement(File.separator) +
                folder + GEM_SUFFIX
    }

    private static configureGemPath() {
        String value = (properties?.(ConstantData.PROP_GEMS)).replace(File.separator, Matcher.quoteReplacement(File.separator))
        configureMandatoryProperties(value, "")
    }

    private static configureGemPath(String v) {
        String value = v?.replace(File.separator, Matcher.quoteReplacement(File.separator))
        configureMandatoryProperties(value, "")
    }

    private static configureGemInflector() {
        configureGem(properties?.(ConstantData.PROP_GEM_INFLECTOR), ConstantData.DEFAULT_GEM_INFLECTOR)
    }

    private static configureGemInflector(String v) {
        configureGem(v, ConstantData.DEFAULT_GEM_INFLECTOR)
    }

    private static configureGemI18n() {
        configureGem(properties?.(ConstantData.PROP_GEM_I18N), ConstantData.DEFAULT_GEM_I18N_FOLDER)
    }

    private static configureGemI18n(String v) {
        configureGem(v, ConstantData.DEFAULT_GEM_I18N_FOLDER)
    }

    private static configureGemParser() {
        configureGem(properties?.(ConstantData.PROP_GEM_PARSER), ConstantData.DEFAULT_GEM_PARSER_FOLDER)
    }

    private static configureGemParser(String v) {
        configureGem(v, ConstantData.DEFAULT_GEM_PARSER_FOLDER)
    }

    private static configureGemAst() {
        configureGem(properties?.(ConstantData.PROP_GEM_AST), ConstantData.DEFAULT_GEM_AST_FOLDER)
    }

    private static configureGemAst(String v) {
        configureGem(v, ConstantData.DEFAULT_GEM_AST_FOLDER)
    }

    private static boolean configureViewAnalysis() {
        configureBooleanProperties(properties?.(ConstantData.PROP_VIEW_ANALYSIS), ConstantData.DEFAULT_VIEW_ANALYSIS)
    }

    private static boolean configureViewAnalysis(String v) {
        configureBooleanProperties(v, ConstantData.DEFAULT_VIEW_ANALYSIS)
    }

    private static boolean configureControllerFilter() {
        configureBooleanProperties(properties?.(ConstantData.PROP_CONTROLLER_FILTER), ConstantData.DEFAULT_CONTROLLER_FILTER)
    }

    private static boolean configureControllerFilter(String v) {
        configureBooleanProperties(v, ConstantData.DEFAULT_CONTROLLER_FILTER)
    }

    private static boolean configureWhenFilter() {
        configureBooleanProperties(properties?.(ConstantData.PROP_WHEN_FILTER), ConstantData.DEFAULT_WHEN_FILTER)
    }

    private static boolean configureWhenFilter(String v) {
        configureBooleanProperties(v, ConstantData.DEFAULT_WHEN_FILTER)
    }

    private static boolean configureViewFilter() {
        configureBooleanProperties(properties?.(ConstantData.PROP_VIEW_FILTER), ConstantData.DEFAULT_VIEW_FILTER)
    }

    private static boolean configureViewFilter(String v) {
        configureBooleanProperties(v, ConstantData.DEFAULT_VIEW_FILTER)
    }

    private static boolean configureGherkinAdds() {
        configureBooleanProperties(properties?.(ConstantData.PROP_RESTRICT_GHERKIN_CHANGES), ConstantData.DEFAULT_RESTRICT_GHERKIN_CHANGES)
    }

    private static boolean configureGherkinAdds(String v) {
        configureBooleanProperties(v, ConstantData.DEFAULT_RESTRICT_GHERKIN_CHANGES)
    }

    private static boolean configureRunningConfigurations() {
        configureBooleanProperties(properties?.(ConstantData.PROP_RUN_ALL_CONFIGURATIONS), ConstantData.DEFAULT_RUN_ALL_CONFIGURATIONS)
    }

    private static boolean configureRunningConfigurations(String v) {
        configureBooleanProperties(v, ConstantData.DEFAULT_RUN_ALL_CONFIGURATIONS)
    }

    private static boolean configureSimilarityAnalysis() {
        configureBooleanProperties(properties?.(ConstantData.PROP_SIMILARITY), ConstantData.DEFAULT_SIMILARITY)
    }

    private static boolean configureSimilarityAnalysis(String v) {
        configureBooleanProperties(v, ConstantData.DEFAULT_SIMILARITY)
    }

    private static createFolder(String folder) {
        File zipFolder = new File(folder)
        if (!zipFolder.exists()) {
            zipFolder.mkdirs()
        }
    }

    private static createFolders() {
        if (!RUNNING_ALL_CONFIGURATIONS) createFolder(ConstantData.DEFAULT_EVALUATION_FOLDER)
        createFolder(ConstantData.DEFAULT_REPOSITORY_FOLDER)
        createFolder(ConstantData.DEFAULT_VIEW_ANALYSIS_ERROR_FOLDER)
    }

    private static configureCoverageGems() {
        def result = []
        String gems = properties?.(ConstantData.PROP_COVERAGE_GEMS)
        if (gems && !gems.empty) {
            result = gems.tokenize(',')*.trim()
        }
        result
    }

    private static configureCoverageGems(String value) {
        def result = []
        String gems = value
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
        } else if (p?.contains("${File.separator}app${File.separator}")) {
            def index = p.indexOf("${File.separator}app${File.separator}")
            root = p.substring(0, index + 1)
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

    static List<String> findFrameworkClassFiles() {
        if (FRAMEWORK_LIB_PATH.empty) return []
        findFilesFromDirectoryByLanguage(FRAMEWORK_LIB_PATH)
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
        files?.sort()
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

    static setRunningConfiguration(boolean whenFilter, boolean gherkinFilter, String folder) {
        WHEN_FILTER = whenFilter
        RESTRICT_GHERKIN_CHANGES = gherkinFilter
        ConstantData.DEFAULT_EVALUATION_FOLDER = folder
        createFolder(ConstantData.DEFAULT_EVALUATION_FOLDER)
    }

}

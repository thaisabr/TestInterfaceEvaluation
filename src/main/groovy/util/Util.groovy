package util

import commitAnalyser.CodeChange

import java.util.regex.Matcher

class Util {

    static final FILE_SEPARATOR_REGEX = /(\\|\/)/
    static final NEW_LINE_REGEX = /\r\n|\n/

    static final String FEATURE_FILENAME_EXTENSION = ".feature"
    static final String GIT_EXTENSION = ".git"
    static final String GITHUB_URL = "https://github.com/";

    static final String REPOSITORY_FOLDER_PATH
    static final String TASKS_FILE
    static final String GHERKIN_FILES_RELATIVE_PATH
    static final String STEPS_FILES_RELATIVE_PATH

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
        STEPS_FILES_RELATIVE_PATH = (properties.'spgroup.steps.files.relative.path').replaceAll(FILE_SEPARATOR_REGEX,
                Matcher.quoteReplacement(File.separator))
    }

    private static loadProperties(){
        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(PROPERTIES_FILE_NAME)
        properties.load(is)
    }

    private static configureTasksFilePath(){
        String value = properties.'spgroup.task.file.path'
        if(value!=null && !value.isEmpty()) return value.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        else return "tasks.csv"
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
        files?.findAll{ file ->
            isTestCode(file)
        }
    }

    static List findAllTestFilesFromCodeChanges(List<CodeChange> changes){
        changes?.findAll{ change ->
            isTestCode(change.filename)
        }
    }

    static boolean isTestCode(String path){
        if( !(excludedPath).any{ path.contains(it)} && path==~/$regex_testCode/ ) true
        else false
    }

    static String configureGitRepositoryName(String url){
        String name = url - GITHUB_URL - GIT_EXTENSION
        return name.replaceAll("/", "_")
    }

    static List<String> findFilesFromDirectory(String directory){
        def f = new File(directory)
        def files = []
        f.eachDirRecurse{ dir ->
            dir.listFiles().each{
                if(it.isFile()){
                    files += it.absolutePath.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
                }
            }
        }
        f.eachFile{
            files += it.absolutePath.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        }
        files
    }

}

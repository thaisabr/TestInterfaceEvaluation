package util

import commitAnalyser.CodeChange

import java.util.regex.Matcher

class Util {

    static final FILE_SEPARATOR_REGEX = /(\\|\/)/
    static final NEW_LINE_REGEX = /\r\n|\n/

    static final String FEATURE_FILENAME_EXTENSION = ".feature"
    static final String JSON_FILENAME_EXTENSION = ".json"
    static final String GIT_EXTENSION = ".git"
    static final String GITHUB_URL = "https://github.com/";

    static final String JSON_PATH = "json${File.separator}"
    static final String REPOSITORY_FOLDER_PATH
    static final String REPOSITORY_FOLDER_NAME = "repositories${File.separator}"
    static final String TASKS_FILE = "input${File.separator}tasks.csv"

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
    }

    private static loadProperties(){
        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(PROPERTIES_FILE_NAME)
        properties.load(is)
    }

    private static configureRepositoryFolderPath(){
        String value = properties.'spgroup.task.repositories.path'
        if(value!=null && !value.isEmpty()) return value.replaceAll(FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        else return REPOSITORY_FOLDER_NAME
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

    static List getProductionFiles(List<CodeChange> changes){
        changes?.findAll{ change ->
            !(excludedPath).any{ change.filename.contains(it)} && !isTestCode(change.filename)
        }
    }

    static List getTestFiles(List<CodeChange> changes){
        changes?.findAll{ change ->
            isTestCode(change.filename)
        }
    }

    static boolean isTestCode(String path){
        if( !(excludedPath).any{ path.contains(it)} && path==~/$regex_testCode/ ) true
        else false
    }

    static String getJsonFileName(String path){
        def beginIndex = path.lastIndexOf(File.separator)
        def name = path.substring(beginIndex+1)
        def jsonName = JSON_PATH + (name - FEATURE_FILENAME_EXTENSION) + JSON_FILENAME_EXTENSION
        return jsonName
    }

    static String configureGitRepositoryName(String url){
        String name = url - Util.GITHUB_URL - Util.GIT_EXTENSION
        return name.replaceAll("/", "_")
    }
}

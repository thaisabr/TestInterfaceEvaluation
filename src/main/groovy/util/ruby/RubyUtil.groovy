package util.ruby

import groovy.util.logging.Slf4j
import util.ConstantData
import util.RegexUtil
import util.Util

import java.util.regex.Matcher

@Slf4j
class RubyUtil extends Util {

    public static final String ROUTES_FILE = File.separator + "config" + File.separator + "routes.rb"
    public static final String ROUTES_ID = "ROUTES"
    public static final String ROUTE_SUFIX = "_path"
    public static final String INFLECTOR_FILE = "inflector.rb"
    public static
    final EXCLUDED_PATH_METHODS = ["current_path", "recognize_path", "assert_routing", "assert_recognizes", " assert_response"]

    static List<String> searchViewFor(String controller, String action, List viewFiles) {
        def result = []
        def match = viewFiles?.findAll { it.contains("views${File.separator}$controller${File.separator}$action") }
        if (match && !match.empty) result += match
        else {
            def matches = viewFiles?.findAll { it.contains("views${File.separator}$controller") }
            if (matches && !matches.empty) result += matches
        }
        result
    }

    static String getClassName(String path) {
        if (!path || path.empty || path.isAllWhitespace()) return ""
        def firstIndex = path.lastIndexOf(File.separator)
        def lastIndex = path.lastIndexOf(".")
        def underscore = ""
        if (firstIndex >= 0 && lastIndex >= 0) underscore = path.substring(firstIndex + 1, lastIndex)
        underscoreToCamelCase(underscore)
    }

    static boolean isRouteMethod(String name) {
        if (!(name in EXCLUDED_PATH_METHODS) && name.endsWith(ROUTE_SUFIX)) true
        else false
    }

    /***
     * Provides the file path of a Ruby class or module.
     *
     * @param className class or module short name
     * @param projectFiles list of file names
     * @return the file name that matches to the class or module name
     */
    static String getClassPathForRubyClass(String className, Collection<String> projectFiles) {
        def underscore = camelCaseToUnderscore(className)
        def name = (underscore + ConstantData.RUBY_EXTENSION).replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def regex_expression = ".*${Matcher.quoteReplacement(File.separator)}$name"
        projectFiles?.find { it ==~ /$regex_expression/ }
    }

    static String getClassPathForRubyInstanceVariable(String varName, Collection<String> projectFiles) {
        def name = varName + ConstantData.RUBY_EXTENSION
        def regex_expression = ".*${Matcher.quoteReplacement(File.separator)}$name"
        projectFiles?.find { it ==~ /$regex_expression/ }
    }

}

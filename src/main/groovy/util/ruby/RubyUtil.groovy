package util.ruby

import groovy.util.logging.Slf4j
import util.ConstantData
import util.RegexUtil
import util.Util

import java.util.regex.Matcher
import java.util.regex.Pattern

@Slf4j
class RubyUtil extends Util {

    public static final String ROUTES_FILE = File.separator + "config" + File.separator + "routes.rb"
    public static final String ROUTES_ID = "ROUTES"
    public static final String ROUTE_SUFIX = "_path"
    public static
    final EXCLUDED_PATH_METHODS = ["current_path", "recognize_path", "assert_routing", "assert_recognizes", " assert_response"]

    private static routeIsFile(String name) {
        name.contains(".") && !name.contains("*")
    }

    private static routeIsAction(String name) {
        name.contains("#")
    }

    private static routeIsFolderOrStringOrRegex(String name) {
        name.contains(File.separator)
    }

    private static extractViewFromAction(String name, List viewFiles) {
        def result = []
        def index = name.indexOf("#")
        def controller = name.substring(0, index)
        def action = name.substring(index + 1, name.length())
        if (controller && action) {
            result += searchViewFor(controller, action, viewFiles)
        }
        result
    }

    private static extractViewFromOther(String name, List viewFiles) {
        def result = []
        def matches = viewFiles?.findAll { it ==~ /.*${Pattern.quote(name)}.*/ }
        if (matches && matches.size() > 0) {
            if (matches.size() == 1) result = matches
            else {
                def match = matches.find { it.contains("index") }
                if (match) result += match
                else result = matches
            }
        }
        result
    }

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

    static List<String> findViewPathForRailsProjects(String resourcePath, List viewFiles) {
        def result = []
        if (!resourcePath || resourcePath.empty || resourcePath.allWhitespace) return result
        def name = resourcePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        if (routeIsFile(name)) {
            def match = viewFiles?.findAll { it.contains(name) }
            if (match) result += match
        } else if (routeIsAction(name)) {
            result += extractViewFromAction(name, viewFiles)
            if (result.empty) log.info "no view match for ${resourcePath} (controller#action)"
        } else if (routeIsFolderOrStringOrRegex(name)) {
            result += extractViewFromOther(name, viewFiles)
        } else {
            log.warn "The searched views is unexpected: $name" //or is an unknown string
        }
        log.info "view match for ${resourcePath}: ${result}"
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

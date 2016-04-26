package util.ruby

import util.ConstantData
import util.RegexUtil
import util.Util
import java.util.regex.Matcher

class RubyUtil extends Util {

    public static final String ROUTES_FILE = File.separator+"config"+File.separator+"routes.rb"
    public static final String ROUTES_ID = "ROUTES"
    public static final String ROUTE_SUFIX = "_path"
    public static final EXCLUDED_PATH_METHODS = ["current_path"]

    /***
     * Provides the file path of a Ruby class or module.
     *
     * @param className class or module short name
     * @param projectFiles list of file names
     * @return the file name that matches to the class or module name
     */
    static String getClassPathForRubyClass(String className, Collection<String> projectFiles){
        def underscore = camelCaseToUnderscore(className)
        def name = (underscore+ConstantData.RUBY_EXTENSION).replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        def regex_expression = ".*${Matcher.quoteReplacement(File.separator)}$name"
        projectFiles?.find{ it ==~ /$regex_expression/ }
    }

    static String getClassPathForRubyInstanceVariable(String varName, Collection<String> projectFiles){
        def name = varName+ ConstantData.RUBY_EXTENSION
        def regex_expression = ".*${Matcher.quoteReplacement(File.separator)}$name"
        projectFiles?.find{ it ==~ /$regex_expression/ }
    }

    static List<String> findViewPathForRailsProjects(String resourcePath, List projectFiles){
        if(!resourcePath || resourcePath.empty || resourcePath.allWhitespace ) return ""

        def result = []
        def viewFiles = projectFiles?.findAll{ file ->
            file.contains(VIEWS_FILES_RELATIVE_PATH) && VALID_VIEW_FILES.any{ file.endsWith(it) }
        }
        def name = resourcePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))

        if(name.contains(".")){ //is a file
            def match = viewFiles?.findAll{ it.contains(name) }
            if(match) result += match
        } else if(name.contains("#")){
            def index = name.indexOf("#")
            def controller = name.substring(0, index)
            def action = name.substring(index+1,name.length())
            if(controller && action){
                def match = viewFiles?.findAll{ it.contains(File.separator+controller+File.separator+action)}
                if(match && !match.empty) result += match
            }
        } else { //is a directory
            def matches = viewFiles?.findAll{ it.contains(name) }
            if(matches && matches.size()>0){
                if(matches.size() == 1) result = matches
                else {
                    def match = matches.find{ it.contains("index") }
                    if(match) result += match
                    else result = matches
                }
            }
        }
        result
    }

    static String getClassName(String path){
        if(!path || path.empty || path.isAllWhitespace()) return ""
        def firstIndex = path.lastIndexOf(File.separator)
        def lastIndex = path.lastIndexOf(".")
        def underscore = ""
        if(firstIndex>=0 && lastIndex>=0) underscore = path.substring(firstIndex+1,lastIndex)
        underscoreToCamelCase(underscore)
    }

    static boolean isRouteMethod(String name){
        if( !(name in EXCLUDED_PATH_METHODS) && name.endsWith(ROUTE_SUFIX) ) true
        else false
    }
}

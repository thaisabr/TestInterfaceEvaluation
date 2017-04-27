package util.ruby

import util.ConstantData
import util.Util

import java.util.regex.Matcher

class RubyUtil extends Util {

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
        if (!(name in RubyConstantData.EXCLUDED_PATH_METHODS) && name.endsWith(RubyConstantData.ROUTE_PATH_SUFIX)) true
        else false
    }

    static List<String> getClassPathForRubyClass(String original, Collection<String> projectFiles) {
        def underscore = camelCaseToUnderscore(original)
        getClassPathForRubyInstanceVariable(underscore, projectFiles)
    }

    static List<String> getClassPathForRubyInstanceVariable(String original, Collection<String> projectFiles) {
        if(original.empty || original.contains(" ")) return [] //invalid class reference
        def name = original + ConstantData.RUBY_EXTENSION
        def exp = ".*$File.separator$name".replace(File.separator, Matcher.quoteReplacement(File.separator))
        projectFiles?.findAll { it ==~ /$exp/ }
    }

    static checkRailsVersionAndGems(String path){
        def result = []
        File file = new File(path+File.separator+RubyConstantData.GEM_FILE)
        if(file.exists()){
            def lines = file.readLines()
            RubyConstantData.GEMS_OF_INTEREST.each{ gem ->
                def regex = /\s*gem\s+"?'?${gem}"?'?.*/
                def version = ""
                def foundGem = lines.find{ !(it.trim().startsWith("#")) && it==~regex }
                if(foundGem){
                    if(gem == "rails"){
                        def index = foundGem.lastIndexOf(",")
                        if(index>-1) version = foundGem?.substring(index+1)?.trim()
                        result += version
                    } else result += gem

                }
            }
        }
        result
    }

}

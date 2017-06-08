package br.ufpe.cin.tan.util.ruby

import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.Util

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
        getClassPathForVariable(underscore, projectFiles)
    }

    static List<String> getClassPathForVariable(String original, Collection<String> projectFiles) {
        if(original.empty || original.contains(" ")) return [] //invalid class reference
        def name = camelCaseToUnderscore(original) + ConstantData.RUBY_EXTENSION
        def exp = ".*$File.separator$name".replace(File.separator, Matcher.quoteReplacement(File.separator))
        projectFiles?.findAll { it ==~ /$exp/ }
    }

    static checkRubyVersion(List<String> lines){
        def rubyRegex = /\s*ruby\s+"?'?.+"?'?.*/
        def rubyVersion = ""
        def foundRuby = lines.find{ !(it.trim().startsWith("#")) && it==~rubyRegex }
        if(foundRuby){
            def index = foundRuby.indexOf("'")
            if(index<0) index = foundRuby.indexOf('"')
            if(index<0) return rubyVersion
            rubyVersion = foundRuby.substring(index+1)?.trim()
            rubyVersion = rubyVersion.substring(0, rubyVersion.size()-1)
        }
        rubyVersion
    }

    static checkRailsVersionAndGems(String path){
        List<String> gems = []
        def railsVersion = ""
        def rubyVersion = ""
        File file = new File(path+File.separator+RubyConstantData.GEM_FILE)
        if(file.exists()){
            def lines = file.readLines()
            rubyVersion = checkRubyVersion(lines)
            RubyConstantData.GEMS_OF_INTEREST.each{ gem ->
                def regex = /\s*gem\s+"?'?${gem}"?'?.*/
                def foundGem = lines.find{ !(it.trim().startsWith("#")) && it==~regex }
                if(foundGem){
                    if(gem == "rails"){
                        def index = foundGem.lastIndexOf(",")
                        if(index>-1) railsVersion = foundGem?.substring(index+1)?.trim()
                    } else gems += gem

                }
            }
        }
        [rails:railsVersion, ruby:rubyVersion, gems:gems]
    }

}

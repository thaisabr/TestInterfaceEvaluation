package br.ufpe.cin.tan.util.groovy

import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.RegexUtil
import br.ufpe.cin.tan.util.Util

import java.util.regex.Matcher

class GroovyUtil extends Util {

    static final List<String> PAGE_METHODS = ["to", "at"]

    /***
     * Provides the file path of a Groovy class.
     *
     * @param className class full name
     * @param projectFiles list of file names
     * @return the file name that matches to the class name
     */
    static String getClassPathForGroovy(String className, Collection<String> projectFiles) {
        getClassPath(className, ConstantData.GROOVY_EXTENSION, projectFiles)
    }

    static String findViewPathForGrailsProjects(String resourcePath, List projectFiles) {
        def name = resourcePath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
        int n = name.count(File.separator)
        if (n > 1) {
            def index = name.lastIndexOf(File.separator)
            name = name.substring(0, index)
        }
        def match = projectFiles?.find { it.contains(name) }
        if (match) name = match
        else name = ""
        name
    }

    static boolean isValidClassByAPI(String referencedClass) {
        def INVALID_CLASS_REGEX = /.*(groovy|java|springframework|apache|grails|spock|geb|selenium|cucumber).*/
        if (INVALID_CLASS_REGEX) {
            if (referencedClass ==~ INVALID_CLASS_REGEX) false
            else true
        } else true
    }

    /**
     * Filters method calls to ignore methods provided by API.
     * @param referencedClass
     * @param path
     * @return true if is a valid class. Otherwise, returns false.
     */
    static boolean isValidClass(String referencedClass, String path) {
        if (path != null && !path.isEmpty() && isValidClassByAPI(referencedClass)) true
        else false
    }

    static configClassnameFromMethod(String className) {
        if (className.startsWith(ConstantData.NON_PRIMITIVE_ARRAY_PREFIX) && className.endsWith(";")) {
            className = className.substring(ConstantData.NON_PRIMITIVE_ARRAY_PREFIX.length(), className.length() - 1)
        }
        return className
    }

    static boolean isPageMethod(String referencedMethod) {
        if (referencedMethod in PAGE_METHODS) true
        else false
    }

}

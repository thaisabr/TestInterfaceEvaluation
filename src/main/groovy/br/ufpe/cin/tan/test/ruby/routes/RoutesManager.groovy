package br.ufpe.cin.tan.test.ruby.routes

import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.util.ruby.RequestType
import br.ufpe.cin.tan.util.ruby.RubyConstantData
import groovy.util.logging.Slf4j

@Slf4j
class RoutesManager {

    String repositoryPath
    String routesFile
    List<String> lines
    List<Route> routes
    boolean oldRoutesStyle
    Set<Route> problematicRoutes
    List<String> routesFiles

    RoutesManager(String repositoryPath, String hash) {
        this.repositoryPath = repositoryPath
        routes = []
        problematicRoutes = []
        findRouteFiles()
        routesFile = routesFiles.find { it.endsWith("${hash}.txt") }
        if (routesFile && !routesFile.empty) {
            readRoutesFile()
            isOldRouteStyle()
            extractRoutes()
        }
    }

    def extractRoutes() {
        def requestTypes = RequestType.values()*.toString()
        routes = []
        problematicRoutes = []
        def identifiers
        if (oldRoutesStyle) identifiers = configureIdentifiersForOldStyle()
        else identifiers = configureIdentifiersForNewStyle()
        lines.each { line ->
            String[] content = configureContent(line)
            if (content.size() >= 3 && content.size() <= 4 && identifiers.any { line.contains(it) }) {
                def valueIndex = content.findIndexOf { it.contains("/") }
                def argsIndex = content.findIndexOf { it.contains("#") }
                def requestTypeIndex = content.findIndexOf { i -> requestTypes.any { i.contains(it) } }
                def nameIndex = -1
                def indexes = [valueIndex, argsIndex, requestTypeIndex].findAll { it != -1 }
                if (indexes.size() < content.size()) nameIndex = 0
                String[] requestType = requestTypeIndex > -1 ? configureMultipleRequestType(content[requestTypeIndex]) : []
                def value = valueIndex > -1 ? formatRouteValue(content[valueIndex]) : ""
                def name = nameIndex > -1 ? content[nameIndex] : ""
                def arg = ""
                if (argsIndex > -1) {
                    arg = content[argsIndex]
                    if (oldRoutesStyle) arg = configureArgs(arg)
                }

                if (requestType.size() == 2) {
                    routes += new Route(name: name, file: RubyConstantData.ROUTES_ID, value: value, arg: arg,
                            type: RequestType.valueOf(requestType[0]))
                    routes += new Route(name: name, file: RubyConstantData.ROUTES_ID, value: value, arg: arg,
                            type: RequestType.valueOf(requestType[1]))
                } else if (requestType.size() == 1) {
                    routes += new Route(name: name, file: RubyConstantData.ROUTES_ID, value: value, arg: arg,
                            type: RequestType.valueOf(requestType[0]))
                } else {
                    routes += new Route(name: name, file: RubyConstantData.ROUTES_ID, value: value, arg: arg, type: "")
                }
            } else {
                problematicRoutes.add(content)
            }
        }
    }

    private configureContent(String line) {
        String[] content = line.split()
        if (oldRoutesStyle) {
            def controllerIndex = content.findIndexOf { it.contains(":controller=>") }
            def controller = controllerIndex > -1 ? content[controllerIndex] : null
            def controllerValue = (controller ? controller - ":controller=>" : "").replaceAll("'", "")
                    .replaceAll('"', "").replaceAll(",", "")
                    .replaceAll("\\{", "").replaceAll("}", "")
            def actionIndex = content.findIndexOf { it.contains(":action=>") }
            def action = actionIndex > -1 ? content[actionIndex] : null
            def actionValue = (action ? action - ":action=>" : "").replaceAll("'", "")
                    .replaceAll('"', "").replaceAll(",", "")
                    .replaceAll("\\{", "").replaceAll("}", "")
            if (controller) content -= controller
            if (action) content -= action
            if (controllerValue && actionValue) content += ["$controllerValue#$actionValue"]
            else content
        } else {
            def invalid = content.findAll { it.contains("{") || it.contains("}") }
            content -= invalid
        }
        content*.trim()
    }

    private static configureIdentifiersForOldStyle() {
        RequestType.values()*.name + [":controller=>"]
    }

    private static configureIdentifiersForNewStyle() {
        RequestType.values()*.name + ["#"]
    }

    private static configureArgs(String value) {
        value.tokenize(",")*.trim().first()
    }

    private static formatRouteValue(String value) {
        value = value - "(.:format)"
        value.replaceAll("/:.[^/]*\$", "/.*").replaceAll("/\\(:.*\\)\$", "(/.*)")
                .replaceAll("/:[^/]+/", "(/.*)?/").replaceAll("/\\(:.*\\)/", "(/.*)/")
    }

    private static String[] configureMultipleRequestType(String typeField) {
        def type = [typeField] as String[]
        if (typeField.contains("|")) type = typeField?.tokenize("|")?.unique()
        def index = type.findIndexOf { it == "OPTIONS" }
        if (index > -1) type -= type[index]
        type
    }

    private findRouteFiles() {
        def folder = "routes"
        def index = repositoryPath.lastIndexOf("_")
        if (index > -1) {
            def projectFolder = repositoryPath.substring(index + 1)
            folder += "${File.separator}$projectFolder"
            routesFiles = Util.findFilesFromDirectory(folder)
        } else routesFiles = []
    }

    private isOldRouteStyle() {
        oldRoutesStyle = false
        if (lines.empty) return
        def first = lines.get(0)
        if (first.contains(":controller=>") || first.contains(":action=>")) oldRoutesStyle = true
    }

    private readRoutesFile() {
        File file = new File(routesFile)
        def text = ""
        file.withReader("utf-8") { reader ->
            text = reader.text
        }
        lines = text.readLines()
        eliminateHeader()
    }

    private eliminateHeader() {
        if (!lines.empty) {
            def first = lines.get(0).split()
            def words = ["Prefix", "Verb", "URI", "Pattern", "Controller#Action"] as String[]
            def result = first - words
            if (result.size() == 0) lines.remove(0)
        }
    }

}

package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.CallNode
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.SymbolNode
import org.jrubyparser.util.NoopVisitor
import util.RegexUtil
import util.ruby.RubyUtil

class RubyGetPropertiesVisitor extends NoopVisitor {

    static propertiesOfInterest = ["get", "to", "as", "controller", "action", "via", "on", "defaults", "constraints"]
    boolean isMember
    boolean isCollection
    def propsValues = []
    String prefix
    String original
    String aliasSingular
    String controllerName
    String indexName

    RubyGetPropertiesVisitor() {

    }

    RubyGetPropertiesVisitor(boolean isMember, boolean isCollection, String prefix, String original, String aliasSingular,
                             String controllerName, String indexName) {
        this.isMember = isMember
        this.isCollection = isCollection
        this.prefix = prefix
        this.original = original
        this.aliasSingular = aliasSingular
        this.controllerName = controllerName
        this.indexName = indexName
    }

    def getOnValue() {
        def value = ""
        def values = propsValues.sort { it.line }.sort { it.position }
        def indexOn = values.findIndexOf { it.value == "on" }
        if (indexOn > -1) value = values.get(indexOn + 1).value
        return value
    }

    private Route generateResourcesMemberRoute(String actionName, String actionValue, String argValue, String pathMethodName) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix

        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            nameSufix = "_${formatedPrefix}_$aliasSingular"
            pathValuePrefix = "/${prefix}/$original(/.*)?/"
            argsPrefix = "${prefix}/$controllerName#"
        } else {
            nameSufix = "_$aliasSingular"
            pathValuePrefix = "/$original(/.*)?/"
            argsPrefix = "$controllerName#"
        }

        def methodName = "${actionName.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}$nameSufix"
        if (pathMethodName) methodName = pathMethodName

        def arg = "$argsPrefix$actionValue"
        if (argValue) arg = argValue

        return new Route(name: methodName, file: RubyUtil.ROUTES_ID, value: "$pathValuePrefix$actionName", arg: arg)
    }

    private Route generateResourcesCollectionRoute(String actionName, String actionValue, String argValue,
                                             String pathMethodName) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            nameSufix = "_${formatedPrefix}_$indexName"
            pathValuePrefix = "/${prefix}/$original/"
            argsPrefix = "${prefix}/$controllerName#"
        } else {
            nameSufix = "_$indexName"
            pathValuePrefix = "/$original/"
            argsPrefix = "$controllerName#"
        }

        def methodName = "${actionName.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}$nameSufix"
        if (pathMethodName) methodName = pathMethodName

        def arg = "$argsPrefix$actionValue"
        if (argValue) arg = argValue

        return new Route(name: methodName, file: RubyUtil.ROUTES_ID, value: "$pathValuePrefix$actionName", arg: arg)
    }

    Route getRoute() {
        def values = propsValues.sort { it.line }.sort { it.position }
        if (values.size() < 2) return

        String actionName = values.get(1).value
        if (actionName.startsWith("/:")) actionName = actionName.replace("/:", "/")
        actionName = actionName.replaceAll("/:.*\$", "/.*")
        actionName = actionName.replaceAll("/:.*/", "/.*/")

        def actionValue = actionName //action que sera realmente chamada
        String controllerActionString = null
        String toValue = null
        String onValue = null
        String pathMethodName = null

        def indexOn = values.findIndexOf { it.value == "on" }
        def indexTo = values.findIndexOf { it.value == "to" }
        def indexAs = values.findIndexOf { it.value == "as" }
        def indexAction = values.findIndexOf { it.value == "action" }

        //there is explicit action
        if (indexAction > -1) actionValue = values.get(indexAction + 1).value

        if (indexOn > -1) {
            onValue = values.get(indexOn + 1).value
            if (onValue != "member" && onValue != "collection") actionName = "$onValue/$actionName"
        }

        //there is explicit pathMethodName
        if (indexAs > -1) pathMethodName = values.get(indexAs + 1).value

        if (indexTo > -1) {
            toValue = values.get(indexTo + 1).value
            if (toValue.contains("#")) controllerActionString = toValue
            else actionValue = toValue
        }

        //configures route
        if (isMember) generateResourcesMemberRoute(actionName, actionValue, controllerActionString, pathMethodName)
        else if (isCollection) generateResourcesCollectionRoute(actionName, actionValue, controllerActionString, pathMethodName)
        else null
    }

    @Override
    Object visitStrNode(StrNode iVisited) {
        super.visitStrNode(iVisited)
        propsValues += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.value]
        iVisited
    }

    @Override
    Object visitSymbolNode(SymbolNode iVisited) {
        super.visitSymbolNode(iVisited)
        propsValues += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name]
        iVisited
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if (iVisited.name in propertiesOfInterest) {
            propsValues += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name]
        }
        iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if (iVisited.name in propertiesOfInterest) {
            propsValues += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name]
        }
        iVisited
    }
}

package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.SymbolNode
import org.jrubyparser.util.NoopVisitor
import util.RegexUtil


class RubyNonResourcefulPropertiesVisitor extends NoopVisitor {

    def argsNodes = []

    /* Examples:
           no args
           one arg
           name, to, value
           name, to, value, as, pathMethod
           name, to, value, type, value, as, pathMethod
           name, to, value, via, values
           to, value
           name, value
           name, value, as, pathMethod or controller, controllervalue, action, actionValue
           name, controller, controllerValue, action, actionValue
           controller, controllerValue, action, actionValue, as, pathMethod
    */

    def getValues() {
        def values = argsNodes.sort { it.position }
        String pathValue = ""
        def controllerValue = null
        def actionValue = null
        def pathMethodName = ""
        def controllerActionString = null
        def result //name, value, method

        if (values.size() < 2) return

        //relevant nodes: to, as, controller, action, via, defaults, constraints
        //TO REMEMBER: via, defaults e contraints sao conjuntos de valores sem relevancia nesse ponto e por isso nao sao extraidos
        def indexTo = values.findIndexOf { it.value == "to" }
        def indexAs = values.findIndexOf { it.value == "as" }
        def indexController = values.findIndexOf { it.value == "controller" }
        def indexAction = values.findIndexOf { it.value == "action" }
        def indexPath = 0

        //there is no explicit name
        if (indexTo == 0 || indexController == 0) indexPath = -1

        //there is explicit controller
        if (indexController > -1) controllerValue = values.get(indexController + 1).value

        //there is explicit action
        if (indexAction > -1) actionValue = values.get(indexAction + 1).value

        //there is explicit pathMethodName
        if (indexAs > -1) pathMethodName = values.get(indexAs + 1).value

        //there is explicit path value
        if (indexPath > -1) pathValue = values.get(indexPath).value
        pathValue = pathValue.replaceAll("/:(\\w|\\.|:|#|&|=|\\+|\\?)*/", "/.*/").replaceAll("/:(\\w|\\.|:|#|&|=|\\+|\\?)*[^/()]", "/.*")
        if (!pathValue.startsWith("/")) pathValue = "/" + pathValue
        if (pathMethodName.empty && pathValue.size() > 1 && !pathValue.contains(".*")) {
            pathMethodName = pathValue.substring(1).replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        }

        //configures controllerActionString value
        if (controllerValue && actionValue) controllerActionString = "$controllerValue#$actionValue"
        else if (controllerValue && !actionValue) controllerActionString = "$controllerValue#index"
        else {
            if (indexPath == 0 && indexTo == -1) controllerActionString = values.get(indexPath + 1).value
            else if (indexTo > -1) controllerActionString = values.get(indexTo + 1).value
        }

        //configures result
        result = [name: pathMethodName, value: pathValue, arg: controllerActionString]
        return result
    }

    @Override
    Object visitStrNode(StrNode strNode) {
        super.visitStrNode(strNode)
        argsNodes += [position: strNode.position.startOffset, value: strNode.value]
        strNode
    }

    @Override
    Object visitSymbolNode(SymbolNode iVisited) {
        super.visitSymbolNode(iVisited)
        argsNodes += [position: iVisited.position.startOffset, value: iVisited.name]
        iVisited
    }

}

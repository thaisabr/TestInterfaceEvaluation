package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.SymbolNode
import org.jrubyparser.util.NoopVisitor


class RubyRootPropertiesVisitor extends NoopVisitor {

    def nodeName
    def argsNodes = []

    RubyRootPropertiesVisitor(String nodeName) {
        this.nodeName = nodeName
    }

    def getValues() {
        def values = argsNodes.sort { it.position }
        String pathValue = "/"
        String controllerValue = null
        String actionValue = null
        String pathMethodName = nodeName
        String controllerActionString = null
        def result //name, value, method

        if (values.size() == (0 as int)) return null
        if (values.size() == (1 as int)) {
            controllerActionString = values.get(0).value
        } else {
            //relevant nodes: to, as, controller, action
            //Others, like: via, defaults and contraints are not relevant at this point. For this reason, we ignore them.
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
            else if (nodeName) pathMethodName = nodeName

            //there is explicit path value
            if (indexPath > -1) pathValue = values.get(indexPath).value
            pathValue = pathValue.replaceAll("/:.*\$", "/.*")
            pathValue = pathValue.replaceAll("/:.*/", "/.*/")

            //configures controllerActionString value
            if (controllerValue && actionValue) controllerActionString = "$controllerValue#$actionValue"
            else if (controllerValue && !actionValue) controllerActionString = "$controllerValue#index"
            else {
                if (indexPath == 0 && indexTo == -1) controllerActionString = values.get(indexPath + 1).value
                else if (indexTo > -1) controllerActionString = values.get(indexTo + 1).value
            }

            if (!pathValue.startsWith("/")) pathValue = "/" + pathValue
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

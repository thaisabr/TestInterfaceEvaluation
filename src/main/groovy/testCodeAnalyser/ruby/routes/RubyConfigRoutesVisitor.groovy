package testCodeAnalyser.ruby.routes

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.Node
import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.SymbolNode
import util.RegexUtil
import util.ruby.RequestType
import util.ruby.RubyConstantData

@Slf4j
class RubyConfigRoutesVisitor {

    List terms
    Node fileNode
    RubyNamespaceVisitor namespacevisitor
    List<Node> nodes
    Set<Route> routingMethods //name, file, value, arg

    static Inflector inflector = new Inflector()

    RubyConfigRoutesVisitor(Node fileNode) {
        this.fileNode = fileNode
        this.routingMethods = [] as Set
        this.namespacevisitor = new RubyNamespaceVisitor()
        fileNode?.accept(this.namespacevisitor)
        this.nodes = this.namespacevisitor.fcallNodes.sort { it.position.startLine }
        this.nodes += this.namespacevisitor.callNodes.sort { it.position.startLine }
        while (!this.nodes.empty) {
            def next = this.nodes.first()
            generateRoutes(next, null, null)
            this.nodes = this.nodes - [next]
        }
    }

    private static formatRouteValue(String name){
        name.replaceAll("/:.[^/]*\$", "(/.*)").replaceAll("/\\(:.*\\)\$", "(/.*)")
                .replaceAll("/:.*/", "(/.*)/").replaceAll("/\\(:.*\\)/", "(/.*)/")
    }

    static configureActionName(String entry){
        def actionName = entry
        if(actionName.startsWith("/:")){
            actionName = formatRouteValue(actionName)
        } else if( actionName ==~ /.+\(\/:.+\)/ ){ //ex.: settings(/:tab)
            def index = actionName.indexOf("(")
            def name = formatRouteValue(actionName.substring(index+1))
            actionName = actionName.substring(0,index) + name
        }
        actionName
    }

    def getRoutingMethods(){
        routingMethods.collect{
            if(it.value.startsWith(":") || it.value.startsWith("(:")) {
                it.value = it.value.replaceFirst("\\(:.*\\)", ".*").replaceFirst(":.*/", ".*/")
            }
            if(!it.value.startsWith("/") && !it.value.startsWith(".") ) it.value = "/" + it.value
            it.value = configureActionName(it.value)
            if(it.arg.startsWith("/")) it.arg = it.arg.substring(1)
            if(it.name.startsWith("_")) it.name = it.name.substring(1)
            it.name = it.name.replaceAll(/_{2,}/, "_")
            it
        }

    }

    private static extractArgs(Node iVisited, argsVisitor) {
        def args = null
        iVisited?.childNodes()?.each {
            it.accept(argsVisitor)
            def values = argsVisitor.values
            if (values && !values?.empty) args = values
        }
        args
    }

    private static extractArgValues(List args) {
        def styleRequestFirst = isRequestFirstStyleCode(args)
        def values = []
        def requestType = RequestType.values()*.name
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).value in requestType) {
                def type = RequestType.valueOfName(args.get(i).value)
                if (styleRequestFirst) values += [name:args.get(i + 1).value, type:type]
                else values += [name:args.get(i - 1).value, type:type]
                i++
            }
        }
        values
    }

    private static isRequestFirstStyleCode(dataList) {
        def styleRequestFirst = false
        if (dataList.first().value in (RequestType.values()*.name)) styleRequestFirst = true
        return styleRequestFirst
    }

    /***
     * @param args [as="", member=[line, position, value], collection=[], only=[], except=[], controller:""]
     */
    private static resourceArgsIsEmpty(Map args) {
        def result = true
        def e = args.values().find { it != null && !it.empty }
        if (e) result = false
        result
    }

    private static configurePath(args, Set<Route> routes, String original) {
        if (args.path && !args.path.value.empty) {
            routes = routes.collect { route ->
                route.value = route.value.replace("/$original", "/${args.path.value}")
                route
            }
        }
    }

    private static configureAlias(String alias, String singular, Set<Route> memberRoutes, Set<Route> collectionRoutes) {
        if (alias != singular) {
            memberRoutes = memberRoutes?.collect { route ->
                route.name = route.name.replace(singular, alias)
                route
            }

            collectionRoutes = collectionRoutes?.collect { route ->
                route.name = route.name.replace(singular, alias)
                route
            }
        }
    }

    private static getNameForNamespace(Node iVisited, String prefix, String methodName) {
        String path, name, aux
        def child = iVisited?.args?.childNodes()
        List<SymbolNode> entities = child?.findAll { it instanceof SymbolNode }
        if(entities.empty){
            entities = child?.findAll{ it instanceof StrNode }
            if(!entities.empty) aux = entities.first().value
            else{
                log.error "Unexpected value: call testCodeAnalyser.ruby.routes.RubyConfigRoutesVisitor: 116"
                log.error "Position: ${iVisited.position.startLine+1}"
            }
        } else aux = entities.first().name

        if(aux){
            if (prefix) path = prefix + "/" + aux
            else path = aux
            if (methodName && methodName!= prefix) name = methodName + "_" + path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            else name = path.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        }
        [path:path, name:name]
    }

    private static getNameForRouteMethod(Node iVisited, String prefix, String methodName) {
        String value = ""
        String path, name = ""
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        if(!entities.empty) value = entities.first().name
        else {
            entities = iVisited?.args?.childNodes()?.findAll { it instanceof StrNode }
            if(!entities.empty) value = entities.first().value
        }
        if (prefix) {
            path = prefix + "/" + value
            name = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        }
        else path = value
        if (methodName) {
            if(name.empty) name = methodName
            else if(methodName != name) name = methodName + "_" + name
        }
        [path:path, name:name]
    }

    private
    static configureNames(SymbolNode entity, String parentSingular, String parentPlural, String originalParent) {
        String original = entity.name
        String plural = original
        String singular = inflector.singularize(plural)
        if (original == singular) plural = inflector.pluralize(original)

        String controllerName = plural
        String indexName
        if (!parentSingular && !parentPlural) {
            indexName = original
        } else {
            indexName = "${parentSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$original"
            singular = "${parentSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$singular"
            plural = "${parentPlural.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$plural"
            original = "${originalParent}(/.*)?/$original"
        }

        [original: original, plural: plural, singular: singular, controllerName: controllerName, indexName: indexName]
    }

    private static configureAliasAndPath(args, String alias, String singular, String original, resourcesData) {
        configureAlias(alias, singular, resourcesData.memberRoutes, resourcesData.collectionRoutes)
        configurePath(args, resourcesData.memberRoutes, original)
        configurePath(args, resourcesData.collectionRoutes, original)
    }

    private getChildNodes(Node iVisited) {
        def others = this.nodes - [iVisited]
        def childNodes = others.findAll {
            it.position.startLine in iVisited.position.startLine..iVisited.position.endLine
        }
        childNodes
    }

    private generateNonResourcefulRoute(Node iVisited, argsVisitor, String prefix, String formattedPrefix) {
        def name = ""
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value
        if(!args.name.empty) name = args.name
        if(formattedPrefix && !formattedPrefix.empty && !name.empty) name = "${formattedPrefix}_$name"
        if (prefix && formattedPrefix) {
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            this.routingMethods += new Route(name: name, file: RubyConstantData.ROUTES_ID,
                    value: "${prefix}${args.value}", arg: "$argPrefix/${args.arg}")
        } else {
            this.routingMethods += new Route(name: name, file: RubyConstantData.ROUTES_ID, value: args.value, arg: args.arg)
        }
    }

    private registryGetNonResourcefulRoute(Node iVisited, String prefix, String formattedPrefix) {
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor()
        generateNonResourcefulRoute(iVisited, argsVisitor, prefix, formattedPrefix)
    }

    private registryMatchNonResourcefulRoute(Node iVisited, String prefix, String formattedString) {
        def name = iVisited?.name
        if (name == "match") name = null
        def argsVisitor = new RubyMatchPropertiesVisitor(name)
        generateNonResourcefulRoute(iVisited, argsVisitor, prefix, formattedString)
    }

    private registryRootRoute(Node iVisited, String prefix, String formattedPrefix) {
        def argsVisitor = new RubyRootPropertiesVisitor(iVisited?.name)
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value
        def isRedirect = false
        if(args.arg.contains("/") && !args.arg.contains("#")){
            def controller = args.arg
            def action = "index"
            args.arg = "${controller}#${action}"
            isRedirect = true
        }
        if (prefix) {
            def arg = args.arg
            if(!isRedirect && formattedPrefix){
                def argPrefix = formattedPrefix.replaceAll("_", "/")
                arg = "$argPrefix/${args.arg}"
            }
            this.routingMethods += new Route(name: "${formattedPrefix}_${iVisited.name}", file: RubyConstantData.ROUTES_ID,
                    value: "${prefix}/", arg: arg)
        } else {
            this.routingMethods += new Route(name: iVisited?.name, file: RubyConstantData.ROUTES_ID, value: "/", arg: args.arg)
        }
    }

    private registryMemberAndCollectionRoutes(Node node, String prefix, String original, String aliasSingular, String controller,
                                              String index, rangesOfNestedResources, rangesOfNestedResource,
                                              String formattedPrefix){
        def alreadyVisitedNodes = []
        def memberData = []
        def collectionData = []

        def visitor = new RubyCollectionAndMemberVisitor(node, rangesOfNestedResources + rangesOfNestedResource, nodes)
        node.accept(visitor)

        def collectionNodes = visitor.collectionValues
        List<Node> collectionValues = collectionNodes.gets
        if (visitor.collectionNode) alreadyVisitedNodes += visitor.collectionNode
        alreadyVisitedNodes += collectionValues
        collectionValues?.each { getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controller, index, formattedPrefix)
            getNode.accept(getPropertiesVisitor)
            def route = getPropertiesVisitor.route
            if(route) collectionData += route
        }

        def memberNodes = visitor.memberValues
        List<Node> memberValues = memberNodes.gets
        if (visitor.memberNode) alreadyVisitedNodes += visitor.memberNode
        alreadyVisitedNodes += memberValues
        memberValues?.each { getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controller, index, formattedPrefix)
            getNode.accept(getPropertiesVisitor)
            def route = getPropertiesVisitor.route
            if(route) memberData += route
        }
        [visited:alreadyVisitedNodes, members:memberData, collection:collectionData, otherNodes:collectionNodes.others+memberNodes.others]
    }

    private registryMemberAndCollectionRoutesAlternativeSyntax(Node node, String prefix, String original, String aliasSingular, String controller,
                                                               String index, String formattedPrefix){
        def alreadyVisitedNodes = []
        def memberData = []
        def collectionData = []
        def memberNodeValue = null
        def collectionNodeValue = null
        def getVisitor = new RubyGetPostDeleteVisitor(node, nodes)
        node.accept(getVisitor)
        def getNodes = getVisitor.nodes
        getNodes?.each { getNode ->
            def visitor = new RubyGetPropertiesVisitor()
            getNode.accept(visitor)
            if (visitor.onValue == "member") {
                memberNodeValue = getNode
                alreadyVisitedNodes += getNode
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controller, index, formattedPrefix)
                getNode.accept(getPropertiesVisitor)
                def route = getPropertiesVisitor.route
                if(route) memberData += route
            } else if (visitor.onValue == "collection") {
                collectionNodeValue = getNode
                alreadyVisitedNodes += getNode
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controller, index, formattedPrefix)
                getNode.accept(getPropertiesVisitor)
                def route = getPropertiesVisitor.route
                if(route) collectionData += route
            }
        }
        [visited:alreadyVisitedNodes, members:memberData, collection:collectionData, otherNodes:getVisitor.otherNodes]
    }

    private static generatePostAndDeleteRoutes(deleteNodes, String prefix, String original, String aliasSingular, String controller,
                                               String index, String formattedPrefix){
        def alreadyVisitedNodes = []
        def routes = []

        deleteNodes?.each { node ->
            def visitor = new RubyGetPropertiesVisitor(false, false, prefix, original, aliasSingular, controller, index, formattedPrefix)
            node.accept(visitor)
            def route = visitor.route
            if(route) routes += route
        }

        [visited:alreadyVisitedNodes, routes:routes]
    }

    private extractResourcesData(Node node, String prefix, String original, String aliasSingular, String controller,
                                 String index, String formattedPrefix) {
        def nestedResourcesVisitor = new RubyNestedResourcesVisitor(node)
        node.accept(nestedResourcesVisitor)

        def nestedResourcesList = nestedResourcesVisitor.resources
        nodes = nodes - nestedResourcesList
        def rangesOfNestedResources = nestedResourcesList.collect { it.position.startLine..it.position.endLine }

        def nestedResourceList = nestedResourcesVisitor.resource
        nodes = nodes - nestedResourceList
        def rangesOfNestedResource = nestedResourceList.collect { it.position.startLine..it.position.endLine }

        /* extracting collection and member (CallNode or FCallNode) */
        def return1 = registryMemberAndCollectionRoutes(node, prefix, original, aliasSingular, controller,
                index, rangesOfNestedResources, rangesOfNestedResource, formattedPrefix)
        def alreadyVisitedNodes = return1.visited
        def memberData = return1.members
        def collectionData = return1.collection

        /* extracting get on member or collection (alternative syntax) */
        def return2 = registryMemberAndCollectionRoutesAlternativeSyntax(node, prefix, original, aliasSingular,
                controller, index, formattedPrefix)
        alreadyVisitedNodes += return2.visited
        memberData += return2.members
        collectionData += return2.collection

        /* dealing with delete and post nodes inside member or collection nodes */
        def otherNodes = return1.otherNodes + return2.otherNodes
        def return3 = generatePostAndDeleteRoutes(otherNodes, prefix, original, aliasSingular, controller, index,
                formattedPrefix)
        alreadyVisitedNodes += return3.visited

        nodes = nodes - alreadyVisitedNodes

        def argsVisitor = new RubyResourcesPropertiesVisitor(node, rangesOfNestedResources, rangesOfNestedResource, alreadyVisitedNodes)
        node.accept(argsVisitor)
        def args = argsVisitor.organizedValues
        return [nestedResourcesList: nestedResourcesList, nestedResourceList: nestedResourceList, args: args,
                collectionRoutes   : collectionData as Set, memberRoutes: memberData as Set,
                otherRoutes: return3.routes]
    }

    private generateNestedResourcesRoute(resourcesData, String prefix, String original, String plural,
                                         String singular, String formattedPrefix) {
        def parentNameSingular = singular
        def parentNamePlural = plural
        def originalParentName = original

        resourcesData.nestedResourcesList.each { nestedResources ->
            extractResources(nestedResources, prefix, parentNameSingular, parentNamePlural, originalParentName, formattedPrefix)
        }

        resourcesData.nestedResourceList.each { nestedResource ->
            extractResource(nestedResource, prefix, parentNameSingular, parentNamePlural, originalParentName, formattedPrefix)
        }
    }

    private registryRoutes(resourcesData, String prefix, String original, String plural, String singular, String formattedPrefix) {
        this.routingMethods += resourcesData.otherRoutes
        this.routingMethods += resourcesData.memberRoutes
        this.routingMethods += resourcesData.collectionRoutes
        generateNestedResourcesRoute(resourcesData, prefix, original, plural, singular, formattedPrefix)
    }

    private generateResourcesRoutes(Node node, String prefix, String index, String original, String plural,
                                    String singular, String controller, String formattedPrefix) {
        def resourcesData = extractResourcesData(node, prefix, original, singular, controller, index, formattedPrefix)
        def args = resourcesData.args
        if (resourceArgsIsEmpty(args)) {
            configureResourcesDefaultRoutes(prefix, index, original, controller, singular, "", formattedPrefix)
        } else {
            def alias = generateBasicResourcesRoutes(args, prefix, index, original, singular, controller, formattedPrefix)
            configureAliasAndPath(args, alias, singular, original, resourcesData)
        }
        registryRoutes(resourcesData, prefix, original, plural, singular, formattedPrefix)

        def internals = nodes.findAll{ it.position.startLine >= node.position.startLine && it.position.startLine <= node.position.endLine }
        def internalMatchNodes = internals.findAll{ it.name == "match" }
        def internalGetNodes = internals.findAll{ it.name == "get" }

        /* extracting get */
        internalGetNodes?.each{ generateResourcefulGetRoute(it, prefix, original, singular, formattedPrefix) }
        nodes = nodes - internalGetNodes

        /* extracting match*/
        internalMatchNodes?.each{ generateResourcefulMatchRoute(it, prefix, original, singular, formattedPrefix) }
        nodes = nodes - internalMatchNodes
    }

    private generateResourcefulGetRoute(Node iVisited, String prefix, String resources, String singular, String formattedPrefix) {
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor()
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(prefix && !prefix.empty) {
            routeName = "${prefix}_${singular}_${routeName}"
            value = "/${prefix}/${resources}/.*${value}"
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            if(routeArg && routeArg.empty) routeArg = "${argPrefix}/${resources}#${routeName}"
            else routeArg = "$argPrefix/${routeArg}"
        } else {
            routeName = "${singular}_${routeName}"
            value = "/${resources}/.*${value}"
            if(routeArg && routeArg.empty) routeArg = "${resources}#${routeName}"
        }

        def route = new Route(name:routeName, file: RubyConstantData.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateResourcefulMatchRoute(Node iVisited, String prefix, String resources, String singular, String formattedPrefix) {
        def name = iVisited?.name
        if (name == "match") name = null
        def argsVisitor = new RubyMatchPropertiesVisitor(name)
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(prefix && !prefix.empty) {
            routeName = "${prefix}_${singular}_${routeName}"
            value = "/${prefix}/${resources}/.*${value}"
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            if(!routeArg || routeArg.empty) routeArg = "${argPrefix}/${resources}#${routeName}"
            else routeArg = "$argPrefix/${routeArg}"
        } else {
            routeName = "${singular}_${routeName}"
            value = "/${resources}/.*${value}"
            if(routeArg && routeArg.empty) routeArg = "${resources}#${routeName}"
        }

        def route = new Route(name:routeName, file: RubyConstantData.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateResourceRoutes(Node node, String prefix, String index, String original, String plural,
                                   String singular, String controller, String formattedPrefix) {
        def resourcesData = extractResourcesData(node, prefix, original, singular, controller, index, formattedPrefix)
        def args = resourcesData.args
        if (resourceArgsIsEmpty(args)) {
            configureResourcesDefaultRoutes(prefix, null, original, controller, index, "", formattedPrefix)
        } else {
            def alias = generateBasicResourceRoutes(args, prefix, index, original, controller, formattedPrefix)
            configureAliasAndPath(args, alias, singular, original, resourcesData)
        }
        registryRoutes(resourcesData, prefix, original, plural, singular, formattedPrefix)

        def internals = nodes.findAll{ it.position.startLine >= node.position.startLine && it.position.startLine <= node.position.endLine }
        def internalMatchNodes = internals.findAll{ it.name == "match" }
        def internalGetNodes = internals.findAll{ it.name == "get" }

        /* extracting get */
        internalGetNodes?.each{ generateSingularResourcefulGetRoute(it, prefix, original, formattedPrefix) }
        nodes = nodes - internalGetNodes

        /* extracting match*/
        internalMatchNodes?.each{ generateSingularResourcefulMatchRoute(it, prefix, original, formattedPrefix) }
        nodes = nodes - internalMatchNodes
    }

    private generateSingularResourcefulGetRoute(Node iVisited, String prefix, String resource, String formattedPrefix) {
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor()
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(prefix && !prefix.empty) {
            routeName = "${routeName}_${prefix}_${resource}"
            value = "${prefix}/${resource}${value}"
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            if(routeArg && routeArg.empty) routeArg = "${argPrefix}/${resource}#${routeName}"
            else routeArg = "$argPrefix/${routeArg}"
        } else {
            routeName = "${routeName}_${resource}"
            value = "/${resource}${value}"
            if(routeArg && routeArg.empty) routeArg = "${resource}#${routeName}"
        }

        def route = new Route(name:routeName, file: RubyConstantData.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateSingularResourcefulMatchRoute(Node iVisited, String prefix, String resource, String formattedPrefix) {
        def name = iVisited?.name
        if (name == "match") name = null
        def argsVisitor = new RubyMatchPropertiesVisitor(name)
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(prefix && !prefix.empty) {
            routeName = "${routeName}_${prefix}_${resource}"
            value = "${prefix}/${resource}${value}"
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            if(!routeArg || routeArg.empty) routeArg = "${argPrefix}/${resource}#${routeName}"
            else routeArg = "$argPrefix/${routeArg}"
        } else {
            routeName = "${routeName}_${resource}"
            value = "/${resource}${value}"
            if(routeArg && routeArg.empty) routeArg = "${resource}#${routeName}"
        }

        def route = new Route(name:routeName, file: RubyConstantData.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateCommonRoutes(args, String prefix, String index, String original, String aliasSingular,
                                 String controller, String path, String formattedPrefix) {
        if (!args.member.empty) {
            def values = extractArgValues(args.member)
            values?.each { value ->
                generateResourcesMemberRoute(value.name, value.type, prefix, original, aliasSingular, controller, path, formattedPrefix)
            }
        }
        if (!args.collection.empty) {
            def values = extractArgValues(args.collection)
            values?.each { value ->
                generateResourcesCollectionRoute(value.name, value.type, prefix, original, index, controller, path, formattedPrefix)
            }
        }
        if (!args.only.empty) {
            def values = args.only*.value
            values.each { value ->
                generateResourcesOnlyRoutes(value, prefix, original, controller, index, aliasSingular, path, formattedPrefix)
            }
        }
    }

    /***
     *
     * @return [as="", member=[line, position, value], collection=[], only=[], except=[], controller:""]
     */
    private generateBasicResourcesRoutes(args, String prefix, String index, String original, String singular,
                                         String controller, String formattedPrefix) {
        String aliasSingular = singular
        String path = ""
        if (!args.as.empty) {
            def plural = args.as.value
            aliasSingular = inflector.singularize(plural)
            index = args.as.value
        }
        if (!args.path.empty) path = args.path.value
        if (!args.controller.empty) controller = args.controller.value
        generateCommonRoutes(args, prefix, index, original, aliasSingular, controller, path, formattedPrefix)
        if (!args.except.empty) {
            def values = args.except*.value
            generateResourcesExceptRoutes(values, prefix, original, controller, index, aliasSingular, path, formattedPrefix)
        }
        if (args.except.empty && args.only.empty) {
            configureResourcesDefaultRoutes(prefix, index, original, controller, aliasSingular, path, formattedPrefix)
        }
        aliasSingular
    }

    private generateBasicResourceRoutes(args, String prefix, String index, String original, String controller,
                                        String formattedPrefix) {
        String aliasSingular = index
        String path = ""
        if (!args.as.empty) index = args.as.value
        if (!args.path.empty) path = args.path.value
        if (!args.controller.empty) controller = args.controller.value
        generateCommonRoutes(args, prefix, index, original, aliasSingular, controller, path, formattedPrefix)
        if (!args.except.empty) {
            def values = args.except*.value
            generateResourcesExceptRoutes(values, prefix, original, controller, null, aliasSingular, path, formattedPrefix)
        }
        if (args.except.empty && args.only.empty) {
            configureResourcesDefaultRoutes(prefix, null, original, controller, index, path, formattedPrefix)
        }
        aliasSingular
    }

    private generateIndexResourceRoute(String prefix, String original, String controller, String index, String formattedPrefix) {
        def singular = inflector.singularize(index)
        if(singular == index){
            index = index + "_index"
        }
        if (prefix && formattedPrefix) {
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            this.routingMethods += new Route(name: "${formattedPrefix}_$index", file: RubyConstantData.ROUTES_ID,
                    value: "/${prefix}/$original", arg: "${argPrefix}/$controller#index")
        } else {
            this.routingMethods += new Route(name: index, file: RubyConstantData.ROUTES_ID, value: "/$original",
                    arg: "$controller#index")
        }
    }

    private generateShowResourceRoute(String prefix, String original, String controller, String alias, String formattedPrefix) {
        if (prefix && formattedPrefix) {
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            this.routingMethods += new Route(name: "${formattedPrefix}_${alias}", file: RubyConstantData.ROUTES_ID,
                    value: "/${prefix}/$original/.*", arg: "${argPrefix}/$controller#show")
        } else {
            this.routingMethods += new Route(name: alias, file: RubyConstantData.ROUTES_ID, value: "/$original/.*",
                    arg: "$controller#show")
        }
    }

    private generateNewResourceRoute(String prefix, String original, String controller, String alias, String formattedPrefix) {
        if (prefix && formattedPrefix) {
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            this.routingMethods += new Route(name: "new_${formattedPrefix}_$alias", file: RubyConstantData.ROUTES_ID,
                    value: "/${prefix}/$original/new", arg: "${argPrefix}/$controller#new")
        } else {
            this.routingMethods += new Route(name: "new_$alias", file: RubyConstantData.ROUTES_ID, value: "/$original/new",
                    arg: "$controller#new")
        }
    }

    private generateEditResourceRoute(String prefix, String original, String controller, String alias, String formattedPrefix) {
        if (prefix && formattedPrefix) {
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            this.routingMethods += new Route(name: "edit_${formattedPrefix}_$alias", file: RubyConstantData.ROUTES_ID,
                    value: "/${prefix}/$original(/.*)?/edit", arg: "${argPrefix}/$controller#edit")
        } else {
            this.routingMethods += new Route(name: "edit_$alias", file: RubyConstantData.ROUTES_ID, value: "/$original(/.*)?/edit",
                    arg: "$controller#edit")
        }
    }

    private generateResourcesExceptRoutes(except, String prefix, String original, String controller,
                                          String index, String alias, String path, String formattedPrefix) {
        def all = ["edit", "index", "new", "show"]
        def routesToGenerate = all - except
        if (path && !path.empty) original = path
        if (index && routesToGenerate.contains("index")) generateIndexResourceRoute(prefix, original, controller, index, formattedPrefix)
        routesToGenerate?.each { action ->
            switch (action) {
                case "new": generateNewResourceRoute(prefix, original, controller, alias, formattedPrefix)
                    break
                case "edit": generateEditResourceRoute(prefix, original, controller, alias, formattedPrefix)
                    break
                case "show": generateShowResourceRoute(prefix, original, controller, alias, formattedPrefix)
                    break
            }
        }
    }

    private generateResourcesOnlyRoutes(value, String prefix, String original, String controller,
                                        String index, String alias, String path, String formattedPrefix) {
        if (path && !path.empty) {
            original = path
        }
        switch (value) {
            case "index": generateIndexResourceRoute(prefix, original, controller, index, formattedPrefix)
                break
            case "new": generateNewResourceRoute(prefix, original, controller, alias, formattedPrefix)
                break
            case "edit": generateEditResourceRoute(prefix, original, controller, alias, formattedPrefix)
                break
            case "show": generateShowResourceRoute(prefix, original, controller, alias, formattedPrefix)
                break
        }
    }

    private generateResourcesMemberRoute(action, type, String prefix, String original, String aliasSingular,
                                         String controller, String path, String formattedPrefix) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if (path && !path.empty) {
            original = path
        }
        if (prefix && formattedPrefix) {
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            nameSufix = "_${formattedPrefix}_$aliasSingular"
            pathValuePrefix = "/${prefix}/$original(/.*)?/"
            argsPrefix = "${argPrefix}/$controller#"
        } else {
            nameSufix = "_$aliasSingular"
            pathValuePrefix = "/$original(/.*)?/"
            argsPrefix = "$controller#"
        }
        this.routingMethods += new Route(name: "$action$nameSufix", file: RubyConstantData.ROUTES_ID, value: "$pathValuePrefix$action",
                arg: "$argsPrefix$action", type: type)
    }

    private generateResourcesCollectionRoute(action, type, String prefix, String original, String index,
                                             String controller, String path, String formattedPrefix) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if (path && !path.empty) {
            original = path
        }
        if (prefix) {
            def argPrefix = formattedPrefix.replaceAll("_", "/")
            nameSufix = "_${formattedPrefix}_$index"
            pathValuePrefix = "/${prefix}/$original/"
            argsPrefix = "${argPrefix}/$controller#"
        } else {
            nameSufix = "_$index"
            pathValuePrefix = "/$original/"
            argsPrefix = "$controller#"
        }
        this.routingMethods += new Route(name: "$action$nameSufix", file: RubyConstantData.ROUTES_ID, value: "$pathValuePrefix$action",
                arg: "$argsPrefix$action", type:type)
    }

    private configureResourcesDefaultRoutes(String prefix, String index, String original, String controller,
                                            String aliasSingular, String path, String formattedPrefix) {
        if (path && !path.empty) original = path
        if (prefix) {
            def argPrefix = formattedPrefix?.replaceAll("_", "/")
            if (index) generateIndexResourceRoute(prefix, original, controller, index, formattedPrefix)
            this.routingMethods += new Route(name: "new_${formattedPrefix}_$aliasSingular", file: RubyConstantData.ROUTES_ID,
                    value: "/${prefix}/$original/new", arg: "${argPrefix}/$controller#new")
            this.routingMethods += new Route(name: "edit_${formattedPrefix}_$aliasSingular", file: RubyConstantData.ROUTES_ID,
                    value: "/${prefix}/$original(/.*)?/edit", arg: "${argPrefix}/$controller#edit")
            this.routingMethods += new Route(name: "${formattedPrefix}_${aliasSingular}", file: RubyConstantData.ROUTES_ID,
                    value: "/${prefix}/$original/.*", arg: "${argPrefix}/$controller#show")
        } else {
            if (index) this.routingMethods += new Route(name: index, file: RubyConstantData.ROUTES_ID, value: "/$original",
                    arg: "$controller#index")
            this.routingMethods += new Route(name: "new_$aliasSingular", file: RubyConstantData.ROUTES_ID,
                    value: "/$original/new", arg: "$controller#new")
            this.routingMethods += new Route(name: "edit_$aliasSingular", file: RubyConstantData.ROUTES_ID,
                    value: "/$original(/.*)?/edit", arg: "$controller#edit")
            this.routingMethods += new Route(name: aliasSingular, file: RubyConstantData.ROUTES_ID, value: "/$original/.*",
                    arg: "$controller#show")
        }
    }

    private extractRoutesInNamespace(Node iVisited, String prefix, String methodName) {
        def result = getNameForNamespace(iVisited, prefix, methodName)
        def childNodes = getChildNodes(iVisited)

        for (int i = 0; i < childNodes.size(); i++) {
            if (!(childNodes.get(i) in nodes)) continue
            generateRoutes(childNodes.get(i), result.path, result.name)
            this.nodes = this.nodes - [childNodes.get(i)]
        }
    }

    private extractRoutesInScope(Node iVisited, String prefix, String methodName){
        //to remember: it is necessary to deal with args like 'module' and 'as'
        def result = getNameForRouteMethod(iVisited, prefix, methodName)
        def childNodes = getChildNodes(iVisited)
        for (int i = 0; i < childNodes.size(); i++) {
            if (!(childNodes.get(i) in nodes)) continue
            generateRoutes(childNodes.get(i), result.path, result.name)
            this.nodes = this.nodes - [childNodes.get(i)]
        }
    }

    private generateDeviseRoutes(Node iVisited) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def plural = entity.name
            def singular = inflector.singularize(plural)

            //Authenticatable (default)
            this.routingMethods += new Route(name: "new_${singular}_session", file: RubyConstantData.ROUTES_ID, value: "/$plural/sign_in",
                    arg: "devise/sessions#new")
            this.routingMethods += new Route(name: "${singular}_session", file: RubyConstantData.ROUTES_ID, value: "/$plural/sign_in",
                    arg: "devise/sessions#create")
            this.routingMethods += new Route(name: "destroy_${singular}_session", file: RubyConstantData.ROUTES_ID, value: "/$plural/sign_out",
                    arg: "devise/sessions#destroy")
            //Recoverable
            this.routingMethods += new Route(name: "new_${singular}_password", file: RubyConstantData.ROUTES_ID, value: "/$plural/password/new",
                    arg: "devise/passwords#new")
            this.routingMethods += new Route(name: "edit_${singular}_password", file: RubyConstantData.ROUTES_ID, value: "/$plural/password(/.*)?/edit",
                    arg: "devise/passwords#edit")
            this.routingMethods += new Route(name: "${singular}_password", file: RubyConstantData.ROUTES_ID, value: "/$plural/password",
                    arg: "devise/passwords#update")
            //Confirmable
            this.routingMethods += new Route(name: "new_${singular}_confirmation", file: RubyConstantData.ROUTES_ID,
                    value: "/$plural/confirmation/new", arg: "devise/confirmations#new")
            this.routingMethods += new Route(name: "${singular}_confirmation", file: RubyConstantData.ROUTES_ID, value: "/$plural/confirmation/.*",
                    arg: "devise/confirmations#show")
            //Registerable
            this.routingMethods += new Route(name: "new_${singular}_registration", file: RubyConstantData.ROUTES_ID,
                    value: "/$plural/registration/new", arg: "devise/registrations#new")
            this.routingMethods += new Route(name: "edit_${singular}_registration", file: RubyConstantData.ROUTES_ID,
                    value: "/$plural/registration(/.*)?/edit", arg: "devise/registrations#edit")
            this.routingMethods += new Route(name: "${singular}_registration", file: RubyConstantData.ROUTES_ID, value: "/$plural/registration",
                    arg: "devise/registrations#update")
            //Lockable
            this.routingMethods += new Route(name: "new_${singular}_unlock", file: RubyConstantData.ROUTES_ID, value: "/$plural/unlock/new",
                    arg: "devise/unlocks#new")
            this.routingMethods += new Route(name: "${singular}_unlock", file: RubyConstantData.ROUTES_ID, value: "/$plural/unlock",
                    arg: "devise/unlocks#create")

            //Rememberable
            //Trackable
            //Validatable
            //Timeoutable
            //Omniauthable
        }
    }

    private extractResources(Node iVisited, String prefix, String parentSingular, String parentPlural,
                             String originalParent, String formattedName) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def names = configureNames(entity, parentSingular, parentPlural, originalParent)
            generateResourcesRoutes(iVisited, prefix, names.indexName, names.original, names.plural, names.singular,
                    names.controllerName, formattedName)
        }
    }

    private extractResource(Node iVisited, String prefix, String parentSingular, String parentPlural,
                            String originalParent, String formattedName) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def names = configureNames(entity, parentSingular, parentPlural, originalParent)
            generateResourceRoutes(iVisited, prefix, names.indexName, names.original, names.plural, names.singular,
                    names.controllerName, formattedName)
        }
    }

    private generateRoutes(Node iVisited, String path, String methodName) {
        switch (iVisited?.name) {
            case "namespace": //it a grouping mechanism
                //log.info "namespace: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractRoutesInNamespace(iVisited, path, methodName)
                break
            case "scope"://similar to namespace
                //log.info "scope: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractRoutesInScope(iVisited, path, methodName)
                break
            case "resources":
                //log.info "resources: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractResources(iVisited, path, null, null, null, methodName)
                break
            case "resource": //similar to resources (http://stackoverflow.com/questions/9194767/difference-between-resource-and-resources-methods)
                extractResource(iVisited, path, null, null, null, methodName)
                break
            case "root":
                //log.info "root: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryRootRoute(iVisited, path, methodName)
                break
            case "match":
                //log.info "match: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryMatchNonResourcefulRoute(iVisited, path, methodName)
                break
            case "devise_for": //devise is a gem for authentication
                //log.info "devise_for: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                generateDeviseRoutes(iVisited)
                break
            case "get": //it is also used into resources
            case "post":
            case "put":
            case "patch":
            case "delete":
                registryGetNonResourcefulRoute(iVisited, path, methodName)
                break
            case "mount": //calls rake application
            case "redirect": //it is used with "get" and others; it does not require treatment
            case "devise_scope": //it is not important for the study purpose
            case "authenticated":
            case "routes":
            case "collection": //it is used into resources
            case "member": //it is used into resources
                break
            default: registryMatchNonResourcefulRoute(iVisited, path, methodName)
        }
    }

}

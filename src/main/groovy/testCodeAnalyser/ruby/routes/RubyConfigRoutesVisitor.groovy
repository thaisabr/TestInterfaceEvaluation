package testCodeAnalyser.ruby.routes

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.Node
import org.jrubyparser.ast.SymbolNode
import util.RegexUtil
import util.ruby.RubyUtil

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
            generateRoutes(next, null)
            this.nodes = this.nodes - [next]
        }
    }

    private static extractArgs(Node iVisited, def argsVisitor) {
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
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).value == "get") {
                if (styleRequestFirst) values += args.get(i + 1)
                else values += args.get(i - 1)
                i++
            }
        }
        values
    }

    private static isRequestFirstStyleCode(def dataList) {
        def styleRequestFirst = false
        if (dataList.first().value in RubyUtil.REQUEST_TYPES) styleRequestFirst = true
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

    private static configurePath(def args, Set<Route> routes, String original) {
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

    private static getNameForNamespace(Node iVisited, String prefix) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        String name
        if (prefix) name = prefix + "/" + entities.first().name
        else name = entities.first().name
        return name
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

    private static configureAliasAndPath(def args, String alias, String singular, String original, def resourcesData) {
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

    private generateNonResourcefulRoute(Node iVisited, def argsVisitor, String namespace) {
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value
        if (namespace) {
            def formatedPrefix = namespace.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            this.routingMethods += new Route(name: "${formatedPrefix}_${args.name}", file: RubyUtil.ROUTES_ID,
                    value: "${namespace}${args.value}", arg: "$namespace/${args.arg}")
        } else {
            this.routingMethods += new Route(name: args.name, file: RubyUtil.ROUTES_ID, value: args.value, arg: args.arg)
        }
    }

    private registryGetNonResourcefulRoute(Node iVisited, String namespace) {
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor()
        generateNonResourcefulRoute(iVisited, argsVisitor, namespace)
    }

    private registryMatchNonResourcefulRoute(Node iVisited, String namespace) {
        def name = iVisited?.name
        if (name == "match") name = null
        def argsVisitor = new RubyMatchPropertiesVisitor(name)
        generateNonResourcefulRoute(iVisited, argsVisitor, namespace)
    }

    private registryRootRoute(Node iVisited, String namespace) {
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
        if (namespace) {
            def formatedPrefix = namespace.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            def arg = args.arg
            if(!isRedirect) arg = "$namespace/${args.arg}"
            this.routingMethods += new Route(name: "${formatedPrefix}_${iVisited.name}", file: RubyUtil.ROUTES_ID,
                    value: "${namespace}/", arg: arg)
        } else {
            this.routingMethods += new Route(name: iVisited?.name, file: RubyUtil.ROUTES_ID, value: "/", arg: args.arg)
        }
    }

    private extractResourcesData(Node node, String prefix, String original, String aliasSingular, String controller,
                                 String index) {
        def nestedResourcesVisitor = new RubyNestedResourcesVisitor(node)
        node.accept(nestedResourcesVisitor)

        def nestedResourcesList = nestedResourcesVisitor.resources
        this.nodes = this.nodes - nestedResourcesList
        def rangesOfNestedResources = nestedResourcesList.collect { it.position.startLine..it.position.endLine }

        def nestedResourceList = nestedResourcesVisitor.resource
        this.nodes = this.nodes - nestedResourceList
        def rangesOfNestedResource = nestedResourceList.collect { it.position.startLine..it.position.endLine }

        /* extracting collection and member (CallNode or FCallNode) */
        def alreadyVisitedNodes = []

        def collectionAndMemberVisitor = new RubyCollectionAndMemberVisitor(node, rangesOfNestedResources + rangesOfNestedResource, this.nodes)
        node.accept(collectionAndMemberVisitor)
        List<Node> collectionValues = collectionAndMemberVisitor.collectionValues
        if (collectionAndMemberVisitor.collectionNode) alreadyVisitedNodes += collectionAndMemberVisitor.collectionNode
        alreadyVisitedNodes += collectionValues
        def collectionData = []
        collectionValues.each { getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controller, index)
            getNode.accept(getPropertiesVisitor)
            collectionData += [node: getNode, route: getPropertiesVisitor.route]
        }

        List<Node> memberValues = collectionAndMemberVisitor.memberValues
        if (collectionAndMemberVisitor.memberNode) alreadyVisitedNodes += collectionAndMemberVisitor.memberNode
        alreadyVisitedNodes += memberValues
        def memberData = []
        memberValues.each { getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controller, index)
            getNode.accept(getPropertiesVisitor)
            memberData += [node: getNode, route: getPropertiesVisitor.route]
        }

        /* extracting get on member or collection */
        def memberNodeValue = null
        def collectionNodeValue = null
        def getVisitor = new RubyGetVisitor(node, this.nodes)
        node.accept(getVisitor)
        def getNodes = getVisitor.nodes
        getNodes.each { getNode ->
            def visitor = new RubyGetPropertiesVisitor()
            getNode.accept(visitor)
            if (visitor.onValue == "member") {
                memberNodeValue = getNode
                alreadyVisitedNodes += getNode
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controller, index)
                getNode.accept(getPropertiesVisitor)
                memberData += [node: getNode, route: getPropertiesVisitor.route]
            } else if (visitor.onValue == "collection") {
                collectionNodeValue = getNode
                alreadyVisitedNodes += getNode
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controller, index)
                getNode.accept(getPropertiesVisitor)
                collectionData += [node: getNode, route: getPropertiesVisitor.route]
            }
        }

        this.nodes = this.nodes - alreadyVisitedNodes

        def argsVisitor = new RubyResourcesPropertiesVisitor(node, rangesOfNestedResources, rangesOfNestedResource, alreadyVisitedNodes)
        node.accept(argsVisitor)
        def args = argsVisitor.organizedValues
        return [nestedResourcesList: nestedResourcesList, nestedResourceList: nestedResourceList, args: args,
                collectionRoutes   : collectionData*.route as Set, memberRoutes: memberData*.route as Set]
    }

    private generateNestedResourcesRoute(
            def resourcesData, String namespace, String original, String plural, String singular) {
        def parentNameSingular = singular
        def parentNamePlural = plural
        def originalParentName = original

        resourcesData.nestedResourcesList.each { nestedResources ->
            extractResources(nestedResources, namespace, parentNameSingular, parentNamePlural, originalParentName)
        }

        resourcesData.nestedResourceList.each { nestedResource ->
            extractResource(nestedResource, namespace, parentNameSingular, parentNamePlural, originalParentName)
        }
    }

    private registryRoutes(def resourcesData, String namespace, String original, String plural, String singular) {
        this.routingMethods += resourcesData.memberRoutes
        this.routingMethods += resourcesData.collectionRoutes
        generateNestedResourcesRoute(resourcesData, namespace, original, plural, singular)
    }

    private generateResourcesRoutes(Node node, String namespace, String index, String original, String plural,
                                    String singular, String controller) {
        def resourcesData = extractResourcesData(node, namespace, original, singular, controller, index)
        def args = resourcesData.args
        if (resourceArgsIsEmpty(args)) {
            configureResourcesDefaultRoutes(namespace, index, original, controller, singular, "")
        } else {
            def alias = generateBasicResourcesRoutes(args, namespace, index, original, singular, controller)
            configureAliasAndPath(args, alias, singular, original, resourcesData)
        }
        registryRoutes(resourcesData, namespace, original, plural, singular)

        def internals = nodes.findAll{ it.position.startLine >= node.position.startLine && it.position.startLine <= node.position.endLine }
        def internalMatchNodes = internals.findAll{ it.name == "match" }
        def internalGetNodes = internals.findAll{ it.name == "get" }

        /* extracting get */
        internalGetNodes?.each{ generateResourcefulGetRoute(it, namespace, original, singular) }
        nodes = nodes - internalGetNodes

        /* extracting match*/
        internalMatchNodes?.each{ generateResourcefulMatchRoute(it, namespace, original, singular) }
        nodes = nodes - internalMatchNodes
    }

    private generateResourcefulGetRoute(Node iVisited, String namespace, String resources, String singular) {
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor()
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(namespace && !namespace.empty) {
            routeName = "${namespace}_${singular}_${routeName}"
            value = "/${namespace}/${resources}/.*${value}"
            if(routeArg && routeArg.empty) routeArg = "${namespace}/${resources}#${routeName}"
            else routeArg = "$namespace/${routeArg}"
        } else {
            routeName = "${singular}_${routeName}"
            value = "/${resources}/.*${value}"
            if(routeArg && routeArg.empty) routeArg = "${resources}#${routeName}"
        }

        def route = new Route(name:routeName, file:RubyUtil.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateResourcefulMatchRoute(Node iVisited, String namespace, String resources, String singular) {
        def name = iVisited?.name
        if (name == "match") name = null
        def argsVisitor = new RubyMatchPropertiesVisitor(name)
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(namespace && !namespace.empty) {
            routeName = "${namespace}_${singular}_${routeName}"
            value = "/${namespace}/${resources}/.*${value}"
            if(!routeArg || routeArg.empty) routeArg = "${namespace}/${resources}#${routeName}"
            else routeArg = "$namespace/${routeArg}"
        } else {
            routeName = "${singular}_${routeName}"
            value = "/${resources}/.*${value}"
            if(routeArg && routeArg.empty) routeArg = "${resources}#${routeName}"
        }

        def route = new Route(name:routeName, file:RubyUtil.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateResourceRoutes(Node node, String namespace, String index, String original, String plural,
                                   String singular, String controller) {
        def resourcesData = extractResourcesData(node, namespace, original, singular, controller, index)
        def args = resourcesData.args
        if (resourceArgsIsEmpty(args)) {
            configureResourcesDefaultRoutes(namespace, null, original, controller, index, "")
        } else {
            def alias = generateBasicResourceRoutes(args, namespace, index, original, controller)
            configureAliasAndPath(args, alias, singular, original, resourcesData)
        }
        registryRoutes(resourcesData, namespace, original, plural, singular)

        def internals = nodes.findAll{ it.position.startLine >= node.position.startLine && it.position.startLine <= node.position.endLine }
        def internalMatchNodes = internals.findAll{ it.name == "match" }
        def internalGetNodes = internals.findAll{ it.name == "get" }

        /* extracting get */
        internalGetNodes?.each{ generateSingularResourcefulGetRoute(it, namespace, original) }
        nodes = nodes - internalGetNodes

        /* extracting match*/
        internalMatchNodes?.each{ generateSingularResourcefulMatchRoute(it, namespace, original) }
        nodes = nodes - internalMatchNodes
    }

    private generateSingularResourcefulGetRoute(Node iVisited, String namespace, String resource) {
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor()
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(namespace && !namespace.empty) {
            routeName = "${routeName}_${namespace}_${resource}"
            value = "${namespace}/${resource}${value}"
            if(routeArg && routeArg.empty) routeArg = "${namespace}/${resource}#${routeName}"
            else routeArg = "$namespace/${routeArg}"
        } else {
            routeName = "${routeName}_${resource}"
            value = "/${resource}${value}"
            if(routeArg && routeArg.empty) routeArg = "${resource}#${routeName}"
        }

        def route = new Route(name:routeName, file:RubyUtil.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateSingularResourcefulMatchRoute(Node iVisited, String namespace, String resource) {
        def name = iVisited?.name
        if (name == "match") name = null
        def argsVisitor = new RubyMatchPropertiesVisitor(name)
        def args = extractArgs(iVisited, argsVisitor)
        if (!args) return
        if(!args.value.startsWith("/")) args.value = "/" + args.value

        String routeName = args.name
        String value = args.value
        String routeArg = args.arg
        if(namespace && !namespace.empty) {
            routeName = "${routeName}_${namespace}_${resource}"
            value = "${namespace}/${resource}${value}"
            if(!routeArg || routeArg.empty) routeArg = "${namespace}/${resource}#${routeName}"
            else routeArg = "$namespace/${routeArg}"
        } else {
            routeName = "${routeName}_${resource}"
            value = "/${resource}${value}"
            if(routeArg && routeArg.empty) routeArg = "${resource}#${routeName}"
        }

        def route = new Route(name:routeName, file:RubyUtil.ROUTES_ID, value:value, arg:routeArg)
        this.routingMethods += route
    }

    private generateCommonRoutes(def args, String prefix, String index, String original, String aliasSingular,
                                 String controller, String path) {
        if (!args.member.empty) {
            def values = extractArgValues(args.member)*.value
            values.each { value ->
                generateResourcesMemberRoute(value, prefix, original, aliasSingular, controller, path)
            }
        }
        if (!args.collection.empty) {
            def values = extractArgValues(args.collection)*.value
            values.each { value ->
                generateResourcesCollectionRoute(value, prefix, original, index, controller, path)
            }
        }
        if (!args.only.empty) {
            def values = args.only*.value
            values.each { value ->
                generateResourcesOnlyRoutes(value, prefix, original, controller, index, aliasSingular, path)
            }
        }
    }

    /***
     *
     * @return [as="", member=[line, position, value], collection=[], only=[], except=[], controller:""]
     */
    private generateBasicResourcesRoutes(def args, String prefix, String index, String original, String singular,
                                         String controller) {
        String aliasSingular = singular
        String path = ""
        if (!args.as.empty) {
            def plural = args.as.value
            aliasSingular = inflector.singularize(plural)
            index = args.as.value
        }
        if (!args.path.empty) path = args.path.value
        if (!args.controller.empty) controller = args.controller.value
        generateCommonRoutes(args, prefix, index, original, aliasSingular, controller, path)
        if (!args.except.empty) {
            def values = args.except*.value
            generateResourcesExceptRoutes(values, prefix, original, controller, index, aliasSingular, path)
        }
        if (args.except.empty && args.only.empty) {
            configureResourcesDefaultRoutes(prefix, index, original, controller, aliasSingular, path)
        }
        aliasSingular
    }

    private generateBasicResourceRoutes(def args, String prefix, String index, String original, String controller) {
        String aliasSingular = index
        String path = ""
        if (!args.as.empty) index = args.as.value
        if (!args.path.empty) path = args.path.value
        if (!args.controller.empty) controller = args.controller.value
        generateCommonRoutes(args, prefix, index, original, aliasSingular, controller, path)
        if (!args.except.empty) {
            def values = args.except*.value
            generateResourcesExceptRoutes(values, prefix, original, controller, null, aliasSingular, path)
        }
        if (args.except.empty && args.only.empty) {
            configureResourcesDefaultRoutes(prefix, null, original, controller, index, path)
        }
        aliasSingular
    }

    private generateIndexResourceRoute(String prefix, String original, String controller, String index) {
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        if (prefix) {
            this.routingMethods += new Route(name: "${formatedPrefix}_$index", file: RubyUtil.ROUTES_ID,
                    value: "/${prefix}/$original", arg: "${prefix}/$controller#index")
        } else {
            this.routingMethods += new Route(name: index, file: RubyUtil.ROUTES_ID, value: "/$original",
                    arg: "$controller#index")
        }
    }

    private generateShowResourceRoute(String prefix, String original, String controller, String alias) {
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        if (prefix) {
            this.routingMethods += new Route(name: "${formatedPrefix}_${alias}", file: RubyUtil.ROUTES_ID,
                    value: "/${prefix}/$original/.*", arg: "${prefix}/$controller#show")
        } else {
            this.routingMethods += new Route(name: alias, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                    arg: "$controller#show")
        }
    }

    private generateNewResourceRoute(String prefix, String original, String controller, String alias) {
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        if (prefix) {
            this.routingMethods += new Route(name: "new_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                    value: "/${prefix}/$original/new", arg: "${prefix}/$controller#new")
        } else {
            this.routingMethods += new Route(name: "new_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/new",
                    arg: "$controller#new")
        }
    }

    private generateEditResourceRoute(String prefix, String original, String controller, String alias) {
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        if (prefix) {
            this.routingMethods += new Route(name: "edit_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                    value: "/${prefix}/$original(/.*)?/edit", arg: "${prefix}/$controller#edit")
        } else {
            this.routingMethods += new Route(name: "edit_$alias", file: RubyUtil.ROUTES_ID, value: "/$original(/.*)?/edit",
                    arg: "$controller#edit")
        }
    }

    private generateResourcesExceptRoutes(def except, String prefix, String original, String controller,
                                          String index, String alias, String path) {
        def all = ["edit", "index", "new", "show"]
        def routesToGenerate = all - except
        if (path && !path.empty) original = path
        if (index && routesToGenerate.contains("index")) generateIndexResourceRoute(prefix, original, controller, index)
        routesToGenerate?.each { action ->
            switch (action) {
                case "new": generateNewResourceRoute(prefix, original, controller, alias)
                    break
                case "edit": generateEditResourceRoute(prefix, original, controller, alias)
                    break
                case "show": generateShowResourceRoute(prefix, original, controller, alias)
                    break
            }
        }
    }

    private generateResourcesOnlyRoutes(def value, String prefix, String original, String controller,
                                        String index, String alias, String path) {
        if (path && !path.empty) {
            original = path
        }
        switch (value) {
            case "index": generateIndexResourceRoute(prefix, original, controller, index)
                break
            case "new": generateNewResourceRoute(prefix, original, controller, alias)
                break
            case "edit": generateEditResourceRoute(prefix, original, controller, alias)
                break
            case "show": generateShowResourceRoute(prefix, original, controller, alias)
                break
        }
    }

    private generateResourcesMemberRoute(def action, String prefix, String original, String aliasSingular,
                                         String controller, String path) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if (path && !path.empty) {
            original = path
        }
        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            nameSufix = "_${formatedPrefix}_$aliasSingular"
            pathValuePrefix = "/${prefix}/$original(/.*)?/"
            argsPrefix = "${prefix}/$controller#"
        } else {
            nameSufix = "_$aliasSingular"
            pathValuePrefix = "/$original(/.*)?/"
            argsPrefix = "$controller#"
        }
        this.routingMethods += new Route(name: "$action$nameSufix", file: RubyUtil.ROUTES_ID, value: "$pathValuePrefix$action",
                arg: "$argsPrefix$action")
    }

    private generateResourcesCollectionRoute(def action, String prefix, String original, String index,
                                             String controller, String path) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if (path && !path.empty) {
            original = path
        }
        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            nameSufix = "_${formatedPrefix}_$index"
            pathValuePrefix = "/${prefix}/$original/"
            argsPrefix = "${prefix}/$controller#"
        } else {
            nameSufix = "_$index"
            pathValuePrefix = "/$original/"
            argsPrefix = "$controller#"
        }
        this.routingMethods += new Route(name: "$action$nameSufix", file: RubyUtil.ROUTES_ID, value: "$pathValuePrefix$action",
                arg: "$argsPrefix$action")
    }

    private configureResourcesDefaultRoutes(String prefix, String index, String original, String controller,
                                            String aliasSingular, String path) {
        if (path && !path.empty) original = path
        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            if (index) generateIndexResourceRoute(prefix, original, controller, index)
            this.routingMethods += new Route(name: "new_${formatedPrefix}_$aliasSingular", file: RubyUtil.ROUTES_ID,
                    value: "/${prefix}/$original/new", arg: "${prefix}/$controller#new")
            this.routingMethods += new Route(name: "edit_${formatedPrefix}_$aliasSingular", file: RubyUtil.ROUTES_ID,
                    value: "/${prefix}/$original(/.*)?/edit", arg: "${prefix}/$controller#edit")
            this.routingMethods += new Route(name: "${formatedPrefix}_${aliasSingular}", file: RubyUtil.ROUTES_ID,
                    value: "/${prefix}/$original/.*", arg: "${prefix}/$controller#show")
        } else {
            if (index) this.routingMethods += new Route(name: index, file: RubyUtil.ROUTES_ID, value: "/$original",
                    arg: "$controller#index")
            this.routingMethods += new Route(name: "new_$aliasSingular", file: RubyUtil.ROUTES_ID,
                    value: "/$original/new", arg: "$controller#new")
            this.routingMethods += new Route(name: "edit_$aliasSingular", file: RubyUtil.ROUTES_ID,
                    value: "/$original(/.*)?/edit", arg: "$controller#edit")
            this.routingMethods += new Route(name: aliasSingular, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                    arg: "$controller#show")
        }
    }

    private extractRoutesInNamespace(Node iVisited, String prefix) {
        String name = getNameForNamespace(iVisited, prefix)
        def childNodes = getChildNodes(iVisited)

        for (int i = 0; i < childNodes.size(); i++) {
            if (!(childNodes.get(i) in nodes)) continue
            generateRoutes(childNodes.get(i), name)
            this.nodes = this.nodes - [childNodes.get(i)]
        }
    }

    private generateDeviseRoutes(Node iVisited) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def plural = entity.name
            def singular = inflector.singularize(plural)

            //Authenticatable (default)
            this.routingMethods += new Route(name: "new_${singular}_session", file: RubyUtil.ROUTES_ID, value: "/$plural/sign_in",
                    arg: "devise/sessions#new")
            this.routingMethods += new Route(name: "${singular}_session", file: RubyUtil.ROUTES_ID, value: "/$plural/sign_in",
                    arg: "devise/sessions#create")
            this.routingMethods += new Route(name: "destroy_${singular}_session", file: RubyUtil.ROUTES_ID, value: "/$plural/sign_out",
                    arg: "devise/sessions#destroy")
            //Recoverable
            this.routingMethods += new Route(name: "new_${singular}_password", file: RubyUtil.ROUTES_ID, value: "/$plural/password/new",
                    arg: "devise/passwords#new")
            this.routingMethods += new Route(name: "edit_${singular}_password", file: RubyUtil.ROUTES_ID, value: "/$plural/password(/.*)?/edit",
                    arg: "devise/passwords#edit")
            this.routingMethods += new Route(name: "${singular}_password", file: RubyUtil.ROUTES_ID, value: "/$plural/password",
                    arg: "devise/passwords#update")
            //Confirmable
            this.routingMethods += new Route(name: "new_${singular}_confirmation", file: RubyUtil.ROUTES_ID,
                    value: "/$plural/confirmation/new", arg: "devise/confirmations#new")
            this.routingMethods += new Route(name: "${singular}_confirmation", file: RubyUtil.ROUTES_ID, value: "/$plural/confirmation/.*",
                    arg: "devise/confirmations#show")
            //Registerable
            this.routingMethods += new Route(name: "new_${singular}_registration", file: RubyUtil.ROUTES_ID,
                    value: "/$plural/registration/new", arg: "devise/registrations#new")
            this.routingMethods += new Route(name: "edit_${singular}_registration", file: RubyUtil.ROUTES_ID,
                    value: "/$plural/registration(/.*)?/edit", arg: "devise/registrations#edit")
            this.routingMethods += new Route(name: "${singular}_registration", file: RubyUtil.ROUTES_ID, value: "/$plural/registration",
                    arg: "devise/registrations#update")
            //Lockable
            this.routingMethods += new Route(name: "new_${singular}_unlock", file: RubyUtil.ROUTES_ID, value: "/$plural/unlock/new",
                    arg: "devise/unlocks#new")
            this.routingMethods += new Route(name: "${singular}_unlock", file: RubyUtil.ROUTES_ID, value: "/$plural/unlock",
                    arg: "devise/unlocks#create")

            //Rememberable
            //Trackable
            //Validatable
            //Timeoutable
            //Omniauthable
        }
    }

    private extractResources(Node iVisited, String namespace, String parentSingular, String parentPlural, String originalParent) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def names = configureNames(entity, parentSingular, parentPlural, originalParent)
            generateResourcesRoutes(iVisited, namespace, names.indexName, names.original, names.plural, names.singular, names.controllerName)
        }
    }

    private extractResource(Node iVisited, String namespace, String parentSingular, String parentPlural, String originalParent) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def names = configureNames(entity, parentSingular, parentPlural, originalParent)
            generateResourceRoutes(iVisited, namespace, names.indexName, names.original, names.plural, names.singular, names.controllerName)
        }
    }

    private generateRoutes(Node iVisited, String namespace) {
        switch (iVisited?.name) {
            case "namespace": //it a grouping mechanism
                //log.info "namespace: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractRoutesInNamespace(iVisited, namespace)
                break
            case "scope"://similar to namespace
                //log.info "scope: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                break
            case "resources":
                //log.info "resources: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractResources(iVisited, namespace, null, null, null)
                break
            case "resource": //similar to resources (http://stackoverflow.com/questions/9194767/difference-between-resource-and-resources-methods)
                extractResource(iVisited, namespace, null, null, null)
                break
            case "root":
                //log.info "root: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryRootRoute(iVisited, namespace)
                break
            case "match":
                //log.info "match: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryMatchNonResourcefulRoute(iVisited, namespace)
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
                registryGetNonResourcefulRoute(iVisited, namespace)
                break
            case "mount": //calls rake application
            case "redirect": //it is used with "get" and others; it does not require treatment
            case "devise_scope": //it is not important for the study purpose
            case "authenticated":
            case "routes":
            case "collection": //it is used into resources
            case "member": //it is used into resources
                break
            default: registryMatchNonResourcefulRoute(iVisited, namespace)
        }
    }

}

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

    public static final REQUEST_TYPES = ["get", "post", "put", "patch", "delete"]

    RubyConfigRoutesVisitor(Node fileNode) {
        this.fileNode = fileNode
        this.routingMethods = [] as Set
        namespacevisitor = new RubyNamespaceVisitor()
        fileNode?.accept(namespacevisitor)
        nodes = namespacevisitor.fcallNodes.sort { it.position.startLine }
        nodes += namespacevisitor.callNodes.sort { it.position.startLine }
        while (!nodes.empty) {
            def next = nodes.first()
            extractData(next, null)
            nodes = nodes - [next]
        }
    }

    private getChildNodes(Node iVisited) {
        def others = nodes - [iVisited]
        def childNodes = others.findAll {
            it.position.startLine in iVisited.position.startLine..iVisited.position.endLine
        }
        return childNodes
    }

    private registryGetNonResourcefulRoute(Node iVisited, String namespace) {
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor()
        def args = []
        iVisited?.childNodes()?.each {
            it.accept(argsVisitor)
            def values = argsVisitor.values
            if (values && !values?.empty) args = argsVisitor.values
        }
        if (!args) return
        if (namespace) {
            def formatedPrefix = namespace.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += new Route(name: "${formatedPrefix}_${args.name}", file: RubyUtil.ROUTES_ID,
                    value: "${namespace}/${args.value}", arg: "$namespace/${args.arg}")
        } else {
            routingMethods += new Route(name: args.name, file: RubyUtil.ROUTES_ID, value: args.value, arg: args.arg)
        }
    }

    private registryMatchNonResourcefulRoute(Node iVisited, String namespace) {
        def name = iVisited?.name
        if (name == "match") name = null
        def argsVisitor = new RubyMatchPropertiesVisitor(name)
        def args = null

        iVisited?.childNodes()?.each {
            it.accept(argsVisitor)
            def values = argsVisitor.values
            if (values && !values?.empty) args = argsVisitor.values
        }
        if (!args) return
        if (namespace) {
            def formatedPrefix = namespace.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += new Route(name: "${formatedPrefix}_${args.name}", file: RubyUtil.ROUTES_ID,
                    value: "${namespace}/${args.value}", arg: "$namespace/${args.arg}")
        } else {
            routingMethods += new Route(name: args.name, file: RubyUtil.ROUTES_ID, value: args.value, arg: args.arg)
        }
    }

    private registryRootRoute(Node iVisited, String prefix) {
        def argsVisitor = new RubyRootPropertiesVisitor(iVisited?.name)
        def args = null
        iVisited?.childNodes()?.each {
            it.accept(argsVisitor)
            def values = argsVisitor.values
            if (values && !values?.empty) args = argsVisitor.values
        }
        if (!args) return
        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += new Route(name: "${formatedPrefix}_${iVisited.name}", file: RubyUtil.ROUTES_ID,
                    value: "${prefix}/", arg: "$prefix/${args.arg}")
        } else {
            routingMethods += new Route(name: iVisited?.name, file: RubyUtil.ROUTES_ID, value: "/", arg: args.arg)
        }
    }

    private extractResourcesData(Node node, String prefix, String original, String aliasSingular, String controllerName,
                                 String indexName) {
        def nestedResourcesVisitor = new RubyNestedResourcesVisitor(node)
        node.accept(nestedResourcesVisitor)

        def nestedResourcesList = nestedResourcesVisitor.resources
        nodes = nodes - nestedResourcesList
        def rangesOfNestedResources = nestedResourcesList.collect { it.position.startLine..it.position.endLine }

        def nestedResourceList = nestedResourcesVisitor.resource
        nodes = nodes - nestedResourceList
        def rangesOfNestedResource = nestedResourceList.collect { it.position.startLine..it.position.endLine }

        /* extracting collection and member (CallNode or FCallNode) */
        def alreadyVisitedNodes = []

        def collectionAndMemberVisitor = new RubyCollectionAndMemberVisitor(node, rangesOfNestedResources + rangesOfNestedResource, nodes)
        node.accept(collectionAndMemberVisitor)
        List<Node> collectionValues = collectionAndMemberVisitor.collectionValues
        if (collectionAndMemberVisitor.collectionNode) alreadyVisitedNodes += collectionAndMemberVisitor.collectionNode
        alreadyVisitedNodes += collectionValues
        def collectionData = []
        collectionValues.each { getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controllerName, indexName)
            getNode.accept(getPropertiesVisitor)
            collectionData += [node: getNode, route: getPropertiesVisitor.route]
        }

        List<Node> memberValues = collectionAndMemberVisitor.memberValues
        if (collectionAndMemberVisitor.memberNode) alreadyVisitedNodes += collectionAndMemberVisitor.memberNode
        alreadyVisitedNodes += memberValues
        def memberData = []
        memberValues.each { getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controllerName, indexName)
            getNode.accept(getPropertiesVisitor)
            memberData += [node: getNode, route: getPropertiesVisitor.route]
        }

        /* extracting get on member or collection */
        def memberNodeValue = null
        def collectionNodeValue = null
        def getVisitor = new RubyGetVisitor(node, nodes)
        node.accept(getVisitor)
        def getNodes = getVisitor.nodes
        getNodes.each { getNode ->
            def visitor = new RubyGetPropertiesVisitor()
            getNode.accept(visitor)
            if (visitor.onValue == "member") {
                memberNodeValue = getNode
                alreadyVisitedNodes += getNode
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controllerName, indexName)
                getNode.accept(getPropertiesVisitor)
                memberData += [node: getNode, route: getPropertiesVisitor.route]
            } else if (visitor.onValue == "collection") {
                collectionNodeValue = getNode
                alreadyVisitedNodes += getNode
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controllerName, indexName)
                getNode.accept(getPropertiesVisitor)
                collectionData += [node: getNode, route: getPropertiesVisitor.route]
            }
        }

        nodes = nodes - alreadyVisitedNodes

        def argsVisitor = new RubyResourcesPropertiesVisitor(node, rangesOfNestedResources, rangesOfNestedResource, alreadyVisitedNodes)
        node.accept(argsVisitor)
        def args = argsVisitor.organizedValues
        return [nestedResourcesList: nestedResourcesList, nestedResourceList: nestedResourceList, args: args,
                collectionRoutes   : collectionData*.route as Set, memberRoutes: memberData*.route as Set]
    }

    private static extractArgValues(List args) {
        def styleRequestFirst = isRequestFirstStyleCode(args)
        def values = []
        if (styleRequestFirst) {
            for (int i = 0; i < args.size(); i++) {
                if (args.get(i).value == "get") {
                    values += args.get(i + 1)
                    i++
                }
            }
        } else {
            for (int i = 0; i < args.size(); i++) {
                if (args.get(i).value == "get") {
                    values += args.get(i - 1)
                    i++
                }
            }
        }
        values
    }

    private static isRequestFirstStyleCode(def dataList) {
        def styleRequestFirst = false
        if (dataList.first().value in REQUEST_TYPES) styleRequestFirst = true
        return styleRequestFirst
    }

    /***
     * @param args [as="", member=[line, position, value], collection=[], only=[], except=[], controller:""]
     */
    private static resourceArgsIsEmpty(Map args) {
        def result = true
        def e = args.values().find{ it != null && !it.empty }
        if(e) result = false
        result
    }

    private static configurePath(def args, Set<Route> routes, String original){
        if(args.path && !args.path.value.empty){
            routes = routes.collect{ route ->
                route.value = route.value.replace("/$original", "/${args.path.value}")
                route
            }
        }
    }

    private static configureAlias(String alias, String singular, Set<Route> memberRoutes, Set<Route> collectionRoutes){
        if (alias != singular) {
            memberRoutes = memberRoutes?.collect{ route ->
                route.name = route.name.replace(singular, alias)
                route
            }

            collectionRoutes = collectionRoutes?.collect{ route ->
                route.name = route.name.replace(singular, alias)
                route
            }
        }
    }

    private generateResourcesRoutes(Node node, String namespaceValue, String indexName, String original, String plural,
                                    String singular, String controllerName) {
        def resourcesData = extractResourcesData(node, namespaceValue, original, singular, controllerName, indexName)
        String aliasSingular = singular
        def args = resourcesData.args
        if (resourceArgsIsEmpty(args)) {
            configureResourcesDefaultRoute(namespaceValue, indexName, original, controllerName, aliasSingular, "")
        } else {
            def alias = generateBasicResourcesRoutes(args, namespaceValue, indexName, original, singular, controllerName)
            configureAlias(alias, singular, resourcesData.memberRoutes, resourcesData.collectionRoutes)
            configurePath(args, resourcesData.memberRoutes, original)
            configurePath(args, resourcesData.collectionRoutes, original)
        }
        routingMethods += resourcesData.memberRoutes
        routingMethods += resourcesData.collectionRoutes

        def parentNameSingular = singular
        def parentNamePlural = plural
        def originalParentName = original

        resourcesData.nestedResourcesList.each { nestedResources ->
            extractResources(nestedResources, namespaceValue, parentNameSingular, parentNamePlural, originalParentName)
        }

        resourcesData.nestedResourceList.each { nestedResource ->
            extractResource(nestedResource, namespaceValue, parentNameSingular, parentNamePlural, originalParentName)
        }
    }

    /***
     *
     * @return [as="", member=[line, position, value], collection=[], only=[], except=[], controller:""]
     */
    private generateBasicResourcesRoutes(def args, String prefix, String indexName, String original, String singular,
                                         String controllerName) {
        String aliasSingular = singular
        String path = ""

        if(!args.path.empty){
            path = args.path.value
        }
        if (!args.as.empty) {
            def plural = args.as.value
            aliasSingular = inflector.singularize(plural)
            indexName = args.as.value
        }
        if (!args.controller.empty) controllerName = args.controller.value
        if (!args.member.empty) {
            def values = extractArgValues(args.member)*.value
            values.each { value ->
                generateResourcesMemberRoute(value, prefix, original, aliasSingular, controllerName, path)
            }
        }
        if (!args.collection.empty) {
            def values = extractArgValues(args.collection)*.value
            values.each { value ->
                generateResourcesCollectionRoute(value, prefix, original, indexName, controllerName, path)
            }
        }
        if (!args.only.empty) {
            def values = args.only*.value
            values.each { value ->
                generateResourcesOnlyRoutes(value, prefix, original, controllerName, indexName, aliasSingular)
            }
        }
        if (!args.except.empty) {
            def values = args.except*.value
            generateResourcesExceptRoutes(values, prefix, original, controllerName, indexName, aliasSingular)
        }
        if (args.except.empty && args.only.empty) {
            configureResourcesDefaultRoute(prefix, indexName, original, controllerName, aliasSingular, path)
        }

        aliasSingular
    }

    private generateResourcesExceptRoutes(def except, String prefix, String original, String controllerName,
                                          String indexName, String alias) {
        def all = ["edit", "index", "new", "show"]
        def routesToGenerate = all - except
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        routesToGenerate.each { actionName ->
            switch (actionName) {
                case "index":
                    if (prefix) {
                        routingMethods += new Route(name : "${formatedPrefix}_$indexName", file: RubyUtil.ROUTES_ID,
                                           value: "/${prefix}/$original", arg: "${prefix}/$controllerName#index")
                    } else {
                        routingMethods += new Route(name: indexName, file: RubyUtil.ROUTES_ID, value: "/$original",
                                           arg : "$controllerName#index")
                    }
                    break
                case "new":
                    if (prefix) {
                        routingMethods += new Route(name : "new_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                           value: "/${prefix}/$original/new", arg: "${prefix}/$controllerName#new")
                    } else {
                        routingMethods += new Route(name: "new_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/new",
                                           arg : "$controllerName#new")
                    }
                    break
                case "edit":
                    if (prefix) {
                        routingMethods += new Route(name : "edit_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                           value: "/${prefix}/$original(/.*)?/edit", arg: "${prefix}/$controllerName#edit")
                    } else {
                        routingMethods += new Route(name: "edit_$alias", file: RubyUtil.ROUTES_ID, value: "/$original(/.*)?/edit",
                                           arg : "$controllerName#edit")
                    }
                    break
                case "show":
                    if (prefix) {
                        routingMethods += new Route(name : "${formatedPrefix}_${alias}", file: RubyUtil.ROUTES_ID,
                                           value: "/${prefix}/$original/.*", arg: "${prefix}/$controllerName#show")
                    } else {
                        routingMethods += new Route(name: alias, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                                           arg : "$controllerName#show")
                    }
                    break
            }
        }
    }

    private generateResourcesOnlyRoutes(def value, String prefix, String original, String controllerName,
                                        String indexName, String alias) {
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        switch (value) {
            case "index":
                if (prefix) {
                    routingMethods += new Route(name : "${formatedPrefix}_$indexName", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original", arg: "${prefix}/$controllerName#index")
                } else {
                    routingMethods += new Route(name: indexName, file: RubyUtil.ROUTES_ID, value: "/$original",
                                       arg : "$controllerName#index")
                }
                break
            case "new":
                if (prefix) {
                    routingMethods += new Route(name : "new_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/new", arg: "${prefix}/$controllerName#new")
                } else {
                    routingMethods += new Route(name: "new_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/new",
                                       arg : "$controllerName#new")
                }
                break
            case "edit":
                if (prefix) {
                    routingMethods += new Route(name : "edit_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original(/.*)?/edit", arg: "${prefix}/$controllerName#edit")
                } else {
                    routingMethods += new Route(name: "edit_$alias", file: RubyUtil.ROUTES_ID, value: "/$original(/.*)?/edit",
                                       arg : "$controllerName#edit")
                }
                break
            case "show":
                if (prefix) {
                    routingMethods += new Route(name : "${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/.*", arg: "${prefix}/$controllerName#show")
                } else {
                    routingMethods += new Route(name: alias, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                                       arg : "$controllerName#show")
                }
                break
        }
    }

    private generateResourceOnlyRoutes(def value, String prefix, String original, String controllerName,
                                       String indexName, String alias, String path) {
        if(path && !path.empty) {
            original = path
        }
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        switch (value) {
            case "index":
                if (prefix) {
                    routingMethods += new Route(name : "${formatedPrefix}_$indexName", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original", arg: "${prefix}/$controllerName#index")
                } else {
                    routingMethods += new Route(name: indexName, file: RubyUtil.ROUTES_ID, value: "/$original",
                                       arg : "$controllerName#index")
                }
                break
            case "new":
                if (prefix) {
                    routingMethods += new Route(name : "new_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/new", arg: "${prefix}/$controllerName#new")
                } else {
                    routingMethods += new Route(name: "new_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/new",
                                       arg : "$controllerName#new")
                }
                break
            case "edit":
                if (prefix) {
                    routingMethods += new Route(name : "edit_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/edit", arg: "${prefix}/$controllerName#edit")
                } else {
                    routingMethods += new Route(name: "edit_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/edit",
                                       arg : "$controllerName#edit")
                }
                break
            case "show":
                if (prefix) {
                    routingMethods += new Route(name : "${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/.*", arg: "${prefix}/$controllerName#show")
                } else {
                    routingMethods += new Route(name: alias, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                                       arg : "$controllerName#show")
                }
                break
        }
    }

    private generateResourcesMemberRoute(def actionName, String prefix, String original, String aliasSingular,
                                         String controllerName, String path) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if(path && !path.empty) {
            original = path
        }
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
        routingMethods += new Route(name: "$actionName$nameSufix", file: RubyUtil.ROUTES_ID, value: "$pathValuePrefix$actionName",
                           arg : "$argsPrefix$actionName")
    }

    private generateResourcesCollectionRoute(def actionName, String prefix, String original, String indexName,
                                             String controllerName, String path) {
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if(path && !path.empty) {
            original = path
        }
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
        routingMethods += new Route(name: "$actionName$nameSufix", file: RubyUtil.ROUTES_ID, value: "$pathValuePrefix$actionName",
                           arg : "$argsPrefix$actionName")
    }

    private configureResourcesDefaultRoute(String prefix, String indexName, String original, String controllerName,
                                           String aliasSingular, String path) {
        if(!path.empty) original = path

        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += new Route(name : "${formatedPrefix}_$indexName", file: RubyUtil.ROUTES_ID,
                               value: "/${prefix}/$original", arg: "${prefix}/$controllerName#index")
            routingMethods += new Route(name : "new_${formatedPrefix}_$aliasSingular", file: RubyUtil.ROUTES_ID,
                               value: "/${prefix}/$original/new", arg: "${prefix}/$controllerName#new")
            routingMethods += new Route(name : "edit_${formatedPrefix}_$aliasSingular", file: RubyUtil.ROUTES_ID,
                               value: "/${prefix}/$original(/.*)?/edit", arg: "${prefix}/$controllerName#edit")
            routingMethods += new Route(name : "${formatedPrefix}_${aliasSingular}", file: RubyUtil.ROUTES_ID,
                               value: "/${prefix}/$original/.*", arg: "${prefix}/$controllerName#show")
        } else {
            routingMethods += new Route(name: indexName, file: RubyUtil.ROUTES_ID, value: "/$original",
                    arg: "$controllerName#index")
            routingMethods += new Route(name: "new_$aliasSingular", file: RubyUtil.ROUTES_ID,
                    value: "/$original/new", arg: "$controllerName#new")
            routingMethods += new Route(name: "edit_$aliasSingular", file: RubyUtil.ROUTES_ID,
                    value: "/$original(/.*)?/edit", arg: "$controllerName#edit")
            routingMethods += new Route(name: aliasSingular, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                    arg: "$controllerName#show")
        }
    }

    private generateResourceExceptRoutes(
            def except, String prefix, String original, String controllerName, String alias, String path) {
        if(path && !path.empty) {
            original = path
        }
        def all = ["edit", "new", "show"]
        def routesToGenerate = all - except
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        routesToGenerate.each { actionName ->
            switch (actionName) {
                case "new":
                    if (prefix) {
                        routingMethods += new Route(name : "new_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                           value: "/${prefix}/$original/new", arg: "${prefix}/$controllerName#new")
                    } else {
                        routingMethods += new Route(name: "new_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/new",
                                           arg : "$controllerName#new")
                    }
                    break
                case "edit":
                    if (prefix) {
                        routingMethods += new Route(name : "edit_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                           value: "/${prefix}/$original/edit", arg: "${prefix}/$controllerName#edit")
                    } else {
                        routingMethods += new Route(name: "edit_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/edit",
                                           arg : "$controllerName#edit")
                    }
                    break
                case "show":
                    if (prefix) {
                        routingMethods += new Route(name : "${formatedPrefix}_${alias}", file: RubyUtil.ROUTES_ID,
                                           value: "/${prefix}/$original/.*", arg: "${prefix}/$controllerName#show")
                    } else {
                        routingMethods += new Route(name: alias, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                                           arg : "$controllerName#show")
                    }
                    break
            }
        }
    }

    private generateBasicResourceRoutes(def args, String prefix, String indexName, String original, String singular,
                                        String controllerName) {
        String aliasSingular = indexName
        String path = ""

        if(!args.path.empty){
            path = args.path.value
        }

        if (!args.as.empty) {
            indexName = args.as.value
        }
        if (!args.controller.empty) controllerName = args.controller.value
        if (!args.member.empty) {
            def values = extractArgValues(args.member)*.value
            values.each { value ->
                generateResourcesMemberRoute(value, prefix, original, aliasSingular, controllerName, path)
            }
        }
        if (!args.collection.empty) {
            def values = extractArgValues(args.collection)*.value
            values.each { value ->
                generateResourcesCollectionRoute(value, prefix, original, indexName, controllerName, path)
            }
        }
        if (!args.only.empty) {
            def values = args.only*.value
            values.each { value ->
                generateResourceOnlyRoutes(value, prefix, original, controllerName, indexName, aliasSingular, path)
            }
        }
        if (!args.except.empty) {
            def values = args.except*.value
            generateResourceExceptRoutes(values, prefix, original, controllerName, aliasSingular, path)
        }
        if (args.except.empty && args.only.empty) {
            configureResourceDefaultRoute(prefix, original, controllerName, indexName, path)
        }
        return aliasSingular
    }

    private configureResourceDefaultRoute(String prefix, String original, String controllerName, String aliasSingular,
                                          String path) {
        if(path && !path.empty) {
            original = path
        }
        if (prefix) {
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += new Route(name : "new_${formatedPrefix}_$aliasSingular", file: RubyUtil.ROUTES_ID,
                               value: "/${prefix}/$original/new", arg: "${prefix}/$controllerName#new")
            routingMethods += new Route(name : "edit_${formatedPrefix}_$aliasSingular", file: RubyUtil.ROUTES_ID,
                               value: "/${prefix}/$original/edit", arg: "${prefix}/$controllerName#edit")
            routingMethods += new Route(name : "${formatedPrefix}_${aliasSingular}", file: RubyUtil.ROUTES_ID,
                               value: "/${prefix}/$original/.*", arg: "${prefix}/$controllerName#show")
        } else {
            routingMethods += new Route(name: "new_$aliasSingular", file: RubyUtil.ROUTES_ID, value: "/$original/new",
                    arg: "$controllerName#new")
            routingMethods += new Route(name: "edit_$aliasSingular", file: RubyUtil.ROUTES_ID, value: "/$original/edit",
                    arg: "$controllerName#edit")
            routingMethods += new Route(name: aliasSingular, file: RubyUtil.ROUTES_ID, value: "/$original/.*",
                    arg: "$controllerName#show")
        }

    }

    private generateResourceRoutes(Node node, String namespaceValue, String indexName, String original, String plural,
                                   String singular, String controllerName) {
        def resourcesData = extractResourcesData(node, namespaceValue, original, singular, controllerName, indexName)
        def args = resourcesData.args
        if (resourceArgsIsEmpty(args)) {
            configureResourceDefaultRoute(namespaceValue, original, controllerName, indexName, "")
        } else {
            def alias = generateBasicResourceRoutes(args, namespaceValue, indexName, original, singular, controllerName)
            configureAlias(alias, singular, resourcesData.memberRoutes, resourcesData.collectionRoutes)
            configurePath(args, resourcesData.memberRoutes, original)
            configurePath(args, resourcesData.collectionRoutes, original)
        }
        routingMethods += resourcesData.memberRoutes
        routingMethods += resourcesData.collectionRoutes

        def parentNameSingular = singular
        def parentNamePlural = plural
        def originalParentName = original

        resourcesData.nestedResourcesList.each { nestedResources ->
            extractResources(nestedResources, namespaceValue, parentNameSingular, parentNamePlural, originalParentName)
        }

        resourcesData.nestedResourceList.each { nestedResource ->
            extractResource(nestedResource, namespaceValue, parentNameSingular, parentNamePlural, originalParentName)
        }
    }

    private static getNameForNamespace(Node iVisited, String prefix) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        String name
        if (prefix) name = prefix + "/" + entities.first().name
        else name = entities.first().name
        return name
    }

    private extractRoutesInNamespace(Node iVisited, String prefix) {
        String name = getNameForNamespace(iVisited, prefix)
        def childNodes = getChildNodes(iVisited)

        for (int i = 0; i < childNodes.size(); i++) {
            if (!(childNodes.get(i) in nodes)) continue
            extractData(childNodes.get(i), name)
            nodes = nodes - [childNodes.get(i)]
        }
    }

    private extractDevise(Node iVisited) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def plural = entity.name
            def singular = inflector.singularize(plural)

            //Authenticatable (default)
            routingMethods += new Route(name: "new_${singular}_session", file: RubyUtil.ROUTES_ID, value: "/$plural/sign_in",
                               arg : "devise/sessions#new")
            routingMethods += new Route(name: "${singular}_session", file: RubyUtil.ROUTES_ID, value: "/$plural/sign_in",
                               arg : "devise/sessions#create")
            routingMethods += new Route(name: "destroy_${singular}_session", file: RubyUtil.ROUTES_ID, value: "/$plural/sign_out",
                               arg : "devise/sessions#destroy")
            //Recoverable
            routingMethods += new Route(name: "new_${singular}_password", file: RubyUtil.ROUTES_ID, value: "/$plural/password/new",
                               arg : "devise/passwords#new")
            routingMethods += new Route(name: "edit_${singular}_password", file: RubyUtil.ROUTES_ID, value: "/$plural/password/edit",
                               arg : "devise/passwords#edit")
            routingMethods += new Route(name: "${singular}_password", file: RubyUtil.ROUTES_ID, value: "/$plural/password",
                               arg : "devise/passwords#update")
            //Confirmable
            routingMethods += new Route(name : "new_${singular}_confirmation", file: RubyUtil.ROUTES_ID,
                               value: "/$plural/confirmation/new", arg: "devise/confirmations#new")
            routingMethods += new Route(name: "${singular}_confirmation", file: RubyUtil.ROUTES_ID, value: "/$plural/confirmation/.*",
                               arg : "devise/confirmations#show")
            //Registerable
            routingMethods += new Route(name : "new_${singular}_registration", file: RubyUtil.ROUTES_ID,
                               value: "/$plural/registration/new", arg: "devise/registrations#new")
            routingMethods += new Route(name : "edit_${singular}_registration", file: RubyUtil.ROUTES_ID,
                               value: "/$plural/registration/edit", arg: "devise/registrations#edit")
            routingMethods += new Route(name: "${singular}_registration", file: RubyUtil.ROUTES_ID, value: "/$plural/registration",
                               arg : "devise/registrations#update")
            //Lockable
            routingMethods += new Route(name: "new_${singular}_unlock", file: RubyUtil.ROUTES_ID, value: "/$plural/unlock/new",
                               arg : "devise/unlocks#new")
            routingMethods += new Route(name: "${singular}_unlock", file: RubyUtil.ROUTES_ID, value: "/$plural/unlock",
                               arg : "devise/unlocks#create")

            //Rememberable
            //Trackable
            //Validatable
            //Timeoutable
            //Omniauthable
        }
    }

    private extractResources(Node iVisited, String namespaceValue, String parentNameSingular, String parentNamePlural,
                             String originalParentName) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            String original = entity.name
            String plural = original
            String singular = inflector.singularize(plural)
            if (original == singular) { //the original term is singular
                plural = inflector.pluralize(original)
            }

            String controllerName = plural
            String indexName
            if (!parentNameSingular && !parentNamePlural) {
                indexName = original
            } else {
                indexName = "${parentNameSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$original"
                singular = "${parentNameSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$singular"
                plural = "${parentNamePlural.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$plural"
                original = "${originalParentName}(/.*)?/$original"
            }

            generateResourcesRoutes(iVisited, namespaceValue, indexName, original, plural, singular, controllerName)
        }
    }

    private extractResource(Node iVisited, String namespaceValue, String parentNameSingular, String parentNamePlural, String originalParentName) {
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll { it instanceof SymbolNode }
        entities?.each { entity ->
            def original = entity.name
            def plural = original
            def singular = inflector.singularize(plural)
            if (original == singular) plural = inflector.pluralize(original)

            String controllerName = plural
            String indexName
            if (!parentNameSingular && !parentNamePlural) {
                indexName = original
            } else {
                indexName = "${parentNameSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$original"
                singular = "${parentNameSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$singular"
                plural = "${parentNamePlural.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$plural"
                original = "${originalParentName}(/.*)?/$original"
            }

            generateResourceRoutes(iVisited, namespaceValue, indexName, original, plural, singular, controllerName)
        }
    }

    private extractData(Node iVisited, String namespaceValue) {
        switch (iVisited?.name) {
            case "namespace": //it a grouping mechanism
                //log.info "namespace: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractRoutesInNamespace(iVisited, namespaceValue)
                break
            case "scope"://similar to namespace
                //log.info "scope: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                break
            case "resources":
                //log.info "resources: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractResources(iVisited, namespaceValue, null, null, null)
                break
            case "resource":
                //similar to resources (http://stackoverflow.com/questions/9194767/difference-between-resource-and-resources-methods)
                extractResource(iVisited, namespaceValue, null, null, null)
                break
            case "root":
                //log.info "root: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryRootRoute(iVisited, namespaceValue)
                break
            case "match":
                //log.info "match: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryMatchNonResourcefulRoute(iVisited, namespaceValue)
                break
            case "devise_for": //devise is a gem for authentication
                //log.info "devise_for: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractDevise(iVisited)
                break
            case "get": //it is also used into resources
            case "post":
            case "put":
            case "patch":
            case "delete":
                registryGetNonResourcefulRoute(iVisited, namespaceValue)
                break
            case "mount": //calls rake application
            case "redirect": //it is used with "get" and others; it does not require treatment
            case "devise_scope": //it is not important for the study purpose
            case "authenticated":
            case "routes":
            case "collection": //it is used into resources
            case "member": //it is used into resources
                break
            default: registryMatchNonResourcefulRoute(iVisited, namespaceValue)
        }
    }
}

package testCodeAnalyser.ruby.routes

import groovy.util.logging.Slf4j
import org.atteo.evo.inflector.English
import org.javalite.common.Inflector
import org.jrubyparser.ast.Node
import org.jrubyparser.ast.SymbolNode
import util.ConstantData
import util.RegexUtil
import util.ruby.RubyUtil

@Slf4j
class RubyConfigRoutesVisitor {
    //Rails.application.routes.named_routes.helpers

    public static final REQUEST_TYPES = ["get", "post", "put", "patch", "delete"]
    List terms
    Node fileNode
    RubyNamespaceVisitor namespacevisitor
    List<Node> nodes
    Set routingMethods //name, file, value

    RubyConfigRoutesVisitor(Node fileNode, List<String> modelFiles) {
        terms = []
        modelFiles.each{ file ->
            def initIndex = file.lastIndexOf(File.separator)
            def endIndex = file.lastIndexOf(ConstantData.RUBY_EXTENSION)
            def singular = file.substring(initIndex+1,endIndex)
            terms += [singular: singular, plural:English.plural(singular,2)]
        }
        this.fileNode = fileNode
        routingMethods = [] as Set
        namespacevisitor = new RubyNamespaceVisitor()
        fileNode?.accept(namespacevisitor)
        nodes = namespacevisitor.fcallNodes.sort{ it.position.startLine }
        nodes += namespacevisitor.callNodes.sort{ it.position.startLine }
        while(!nodes.empty){
            def next = nodes.first()
            extractData(next, null)
            nodes = nodes - [next]
        }
    }

    private getChildNodes(Node iVisited){
        def others = nodes-[iVisited]
        def childNodes = others.findAll{
            it.position.startLine in iVisited.position.startLine..iVisited.position.endLine
        }
        return childNodes
    }

    private registryGetNonResourcefulRoute(Node iVisited, String prefix){
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor(null)
        def args = []
        iVisited?.childNodes()?.each{
            it.accept(argsVisitor)
            def values = argsVisitor.values
            if(values && !values?.empty) args = argsVisitor.values
        }
        if(!args) return
        routingMethods += [name:args.name, file:RubyUtil.ROUTES_ID, value:args.value, arg:args.arg]
    }

    private registryMatchNonResourcefulRoute(Node iVisited, String prefix){
        def argsVisitor = new RubyNonResourcefulPropertiesVisitor(iVisited?.name)
        def args = null

        iVisited?.childNodes()?.each{
            it.accept(argsVisitor)
            def values = argsVisitor.values
            if(values && !values?.empty) args = argsVisitor.values
        }
        if(!args) return
        if(prefix){
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += [name:"${formatedPrefix}_${args.name}", file:RubyUtil.ROUTES_ID, value:"${prefix}/${args.value}", arg:"$prefix/${args.arg}"]
        } else {
            routingMethods += [name:args.name, file:RubyUtil.ROUTES_ID, value:args.value, arg:args.arg]
        }
    }

    private registryRootRoute(Node iVisited, String prefix){
        def argsVisitor = new RubyRootPropertiesVisitor(iVisited?.name)
        def args = null
        iVisited?.childNodes()?.each{
            it.accept(argsVisitor)
            def values = argsVisitor.values
            if(values && !values?.empty) args = argsVisitor.values
        }
        if(!args) return
        if(prefix){
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += [name:"${formatedPrefix}_${iVisited.name}", file:RubyUtil.ROUTES_ID, value:"${prefix}/", arg:"$prefix/${args.arg}"]
        } else {
            routingMethods += [name:iVisited?.name, file:RubyUtil.ROUTES_ID, value:"/", arg:args.arg]
        }
    }

    private extractResourcesData(Node node, String prefix, String original, String aliasSingular, String controllerName,
                                 String indexName){
        def nestedResourcesVisitor = new RubyNestedResourcesVisitor(node)
        node.accept(nestedResourcesVisitor)

        def nestedResourcesList = nestedResourcesVisitor.resources
        nodes = nodes - nestedResourcesList
        def rangesOfNestedResources = nestedResourcesList.collect{ it.position.startLine..it.position.endLine }

        def nestedResourceList = nestedResourcesVisitor.resource
        nodes = nodes - nestedResourceList
        def rangesOfNestedResource = nestedResourceList.collect{ it.position.startLine..it.position.endLine }

        /* extracting collection and member (CallNode or FCallNode) */
        def alreadyVisitedNodes = []

        def collectionAndMemberVisitor = new RubyCollectionAndMemberVisitor(node, rangesOfNestedResources+rangesOfNestedResource, nodes)
        node.accept(collectionAndMemberVisitor)
        List<Node> collectionValues = collectionAndMemberVisitor.collectionValues
        if(collectionAndMemberVisitor.collectionNode) alreadyVisitedNodes += collectionAndMemberVisitor.collectionNode
        alreadyVisitedNodes += collectionValues
        //nodes = nodes - [collectionAndMemberVisitor.collectionNode]
        //nodes = nodes - collectionValues
        def collectionData = []
        collectionValues.each{ getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controllerName, indexName)
            getNode.accept(getPropertiesVisitor)
            collectionData += [node: getNode, route:getPropertiesVisitor.route]
        }

        List<Node> memberValues = collectionAndMemberVisitor.memberValues
        if(collectionAndMemberVisitor.memberNode) alreadyVisitedNodes += collectionAndMemberVisitor.memberNode
        alreadyVisitedNodes += memberValues
        //nodes = nodes - [collectionAndMemberVisitor.memberNode]
        //nodes = nodes - memberValues
        def memberData = []
        memberValues.each{ getNode ->
            def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controllerName, indexName)
            getNode.accept(getPropertiesVisitor)
            memberData += [node: getNode, route:getPropertiesVisitor.route]
        }

        /* extracting get on member or collection */
        def memberNodeValue = null
        def collectionNodeValue = null
        def getVisitor = new RubyGetVisitor(node, nodes)
        node.accept(getVisitor)
        def getNodes = getVisitor.nodes
        getNodes.each{ getNode ->
            def visitor = new RubyGetPropertiesVisitor()
            getNode.accept(visitor)
            if(visitor.onValue == "member") {
                memberNodeValue = getNode
                alreadyVisitedNodes += getNode
                //nodes = nodes - [memberNodeValue]
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(true, false, prefix, original, aliasSingular, controllerName, indexName)
                getNode.accept(getPropertiesVisitor)
                memberData += [node: getNode, route:getPropertiesVisitor.route]
            }
            else if(visitor.onValue == "collection") {
                collectionNodeValue = getNode
                alreadyVisitedNodes += getNode
                //nodes = nodes - [collectionNodeValue]
                def getPropertiesVisitor = new RubyGetPropertiesVisitor(false, true, prefix, original, aliasSingular, controllerName, indexName)
                getNode.accept(getPropertiesVisitor)
                collectionData += [node: getNode, route:getPropertiesVisitor.route]
            }
        }

        nodes = nodes - alreadyVisitedNodes

        def argsVisitor = new RubyResourcesPropertiesVisitor(node, rangesOfNestedResources, rangesOfNestedResource, alreadyVisitedNodes)
        node.accept(argsVisitor)
        def args = argsVisitor.organizedValues
        return [nestedResourcesList: nestedResourcesList, nestedResourceList:nestedResourceList, args:args,
                collectionRoutes:collectionData*.route, memberRoutes:memberData*.route]
    }

    private static extractArgValues(List args){
        def styleRequestFirst = isRequestFirstStyleCode(args)
        def values = []
        if(styleRequestFirst){
            for(int i=0; i<args.size(); i++){
                if(args.get(i).value == "get") {
                    values += args.get(i+1)
                    i++
                }
            }
        } else {
            for(int i=0; i<args.size(); i++){
                if(args.get(i).value == "get") {
                    values += args.get(i-1)
                    i++
                }
            }
        }
        values
    }

    private static isRequestFirstStyleCode(def dataList){
        def styleRequestFirst = false
        if(dataList.first().value in REQUEST_TYPES) styleRequestFirst = true
        return styleRequestFirst
    }

    /***
     *  @param args [as="", member=[line, position, value], collection=[], only=[], except=[], controller:""]
     */
    private static resourceArgsIsEmpty(def args){
        args.as.empty && args.controller.empty && args.member.empty && args.collection.empty && args.only.empty && args.except.empty
    }

    private generateResourcesRoutes(Node node, String namespaceValue, String indexName, String original, String plural,
                                    String singular, String controllerName){
        def resourcesData = extractResourcesData(node, namespaceValue, original, singular, controllerName, indexName)
        String aliasSingular = singular
        def args = resourcesData.args
        if(resourceArgsIsEmpty(args)) {
            configureResourcesDefaultRoute(namespaceValue, indexName, original, controllerName, aliasSingular)
            routingMethods += resourcesData.memberRoutes
            routingMethods += resourcesData.collectionRoutes
        }
        else {
            def alias = generateBasicResourcesRoutes(args, namespaceValue, indexName, original, singular, controllerName)
            if(alias != singular){
                resourcesData.memberRoutes?.each{ route ->
                    routingMethods += route.name.replace(singular, alias)
                }
                resourcesData.collectionRoutes?.each{ route ->
                    routingMethods += route.name.replace(singular, alias)
                }
            } else{
                routingMethods += resourcesData.memberRoutes
                routingMethods += resourcesData.collectionRoutes
            }
        }

        def parentNameSingular
        def parentNamePlural
        def originalParentName
        if(namespaceValue) {
            parentNameSingular = "${namespaceValue}/${singular}"
            parentNamePlural = "${namespaceValue}/${plural}"
            originalParentName = "${namespaceValue}/${original}"
        } else {
            parentNameSingular = singular
            parentNamePlural = plural
            originalParentName = original
        }

        resourcesData.nestedResourcesList.each{ nestedResources ->
            extractResources(nestedResources, null, parentNameSingular, parentNamePlural, originalParentName)
        }

        resourcesData.nestedResourceList.each{ nestedResource ->
            extractResource(nestedResource, null, parentNameSingular, parentNamePlural, originalParentName)
        }
    }

    /***
     *
     * @return [as="", member=[line, position, value], collection=[], only=[], except=[], controller:""]
     */
    private generateBasicResourcesRoutes(def args, String prefix, String indexName, String original, String singular,
                                         String controllerName){
        String aliasSingular = singular

        if(!args.as.empty) {
            def plural = args.as.value
            aliasSingular = terms?.find{ it.plural == plural }?.singular
            if(!aliasSingular) aliasSingular = Inflector.singularize(plural)
            indexName = args.as.value
        }
        if(!args.controller.empty) controllerName = args.controller.value
        if(!args.member.empty){
            def values = extractArgValues(args.member)*.value
            values.each{ value ->
                generateResourcesMemberRoute(value, prefix, original, aliasSingular, controllerName)
            }
        }
        if(!args.collection.empty){
            def values = extractArgValues(args.collection)*.value
            values.each{ value ->
                generateResourcesCollectionRoute(value, prefix, original, indexName, controllerName)
            }
        }
        if(!args.only.empty){
            def values = args.only*.value
            values.each{ value ->
                generateResourcesOnlyRoutes(value, prefix, original, controllerName, indexName, aliasSingular)
            }
        }
        if(!args.except.empty) {
            def values = args.except*.value
            generateResourcesExceptRoutes(values, prefix, original, controllerName, indexName, aliasSingular)
        }
        if(args.except.empty && args.only.empty) {
            configureResourcesDefaultRoute(prefix, indexName, original, controllerName, aliasSingular)
        }

        return aliasSingular
    }

    private generateResourcesExceptRoutes(def except, String prefix, String original, String controllerName,
                                          String indexName, String alias){
        def all = ["edit", "index", "new", "show"]
        def routesToGenerate = all - except
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        routesToGenerate.each{ actionName ->
            switch(actionName){
                case "index":
                    if(prefix){
                        routingMethods += [name:"${formatedPrefix}_$indexName", file:RubyUtil.ROUTES_ID,
                                   value:"/${prefix}/$original", arg:"${prefix}/$controllerName#index"]
                    } else {
                        routingMethods += [name:indexName, file:RubyUtil.ROUTES_ID, value:"/$original",
                                           arg:"$controllerName#index"]
                    }
                    break
                case "new":
                    if(prefix){
                        routingMethods += [name:"new_${formatedPrefix}_$alias", file:RubyUtil.ROUTES_ID,
                                   value:"/${prefix}/$original/new", arg:"${prefix}/$controllerName#new"]
                    } else {
                        routingMethods += [name:"new_$alias", file:RubyUtil.ROUTES_ID, value:"/$original/new",
                                           arg:"$controllerName#new"]
                    }
                    break
                case "edit":
                    if(prefix){
                        routingMethods += [name:"edit_${formatedPrefix}_$alias", file:RubyUtil.ROUTES_ID,
                                   value:"/${prefix}/$original/.*/edit", arg:"${prefix}/$controllerName#edit"]
                    } else {
                        routingMethods += [name:"edit_$alias", file:RubyUtil.ROUTES_ID, value:"/$original/.*/edit",
                                           arg:"$controllerName#edit"]
                    }
                    break
                case "show":
                    if(prefix){
                        routingMethods += [name:"${formatedPrefix}_${alias}", file:RubyUtil.ROUTES_ID,
                                   value:"/${prefix}/$original/", arg:"${prefix}/$controllerName#show"]
                    } else {
                        routingMethods += [name:alias, file:RubyUtil.ROUTES_ID, value:"/$original/",
                                           arg:"$controllerName#show"]
                    }
                    break
            }
        }
    }

    private generateResourcesOnlyRoutes(def value, String prefix, String original, String controllerName,
                                        String indexName, String alias){
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        switch (value) {
            case "index":
                if (prefix) {
                    routingMethods += [name: "${formatedPrefix}_$indexName", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original", arg: "${prefix}/$controllerName#index"]
                } else {
                    routingMethods += [name: indexName, file: RubyUtil.ROUTES_ID, value: "/$original",
                                       arg: "$controllerName#index"]
                }
                break
            case "new":
                if (prefix) {
                    routingMethods += [name: "new_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/new", arg: "${prefix}/$controllerName#new"]
                } else {
                    routingMethods += [name: "new_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/new",
                                       arg: "$controllerName#new"]
                }
                break
            case "edit":
                if (prefix) {
                    routingMethods += [name: "edit_${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/.*/edit", arg: "${prefix}/$controllerName#edit"]
                } else {
                    routingMethods += [name: "edit_$alias", file: RubyUtil.ROUTES_ID, value: "/$original/.*/edit",
                                       arg: "$controllerName#edit"]
                }
                break
            case "show":
                if (prefix) {
                    routingMethods += [name: "${formatedPrefix}_$alias", file: RubyUtil.ROUTES_ID,
                                       value: "/${prefix}/$original/", arg: "${prefix}/$controllerName#show"]
                } else {
                    routingMethods += [name: alias, file: RubyUtil.ROUTES_ID, value: "/$original/",
                                       arg: "$controllerName#show"]
                }
                break
        }
    }

    private generateResourcesMemberRoute(def actionName, String prefix, String original, String aliasSingular,
                                         String controllerName){
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if(prefix){
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            nameSufix = "_${formatedPrefix}_$aliasSingular"
            pathValuePrefix = "/${prefix}/$original/.*/"
            argsPrefix = "${prefix}/$controllerName#"
        } else {
            nameSufix = "_$aliasSingular"
            pathValuePrefix = "/$original/.*/"
            argsPrefix = "$controllerName#"
        }
        routingMethods += [name:"$actionName$nameSufix", file:RubyUtil.ROUTES_ID, value:"$pathValuePrefix$actionName",
                           arg:"$argsPrefix$actionName"]
    }

    private generateResourcesCollectionRoute(def actionName, String prefix, String original, String indexName,
                                             String controllerName){
        def nameSufix
        def pathValuePrefix
        def argsPrefix
        if(prefix){
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            nameSufix = "_${formatedPrefix}_$indexName"
            pathValuePrefix = "/${prefix}/$original/"
            argsPrefix = "${prefix}/$controllerName#"
        } else {
            nameSufix = "_$indexName"
            pathValuePrefix = "/$original/"
            argsPrefix = "$controllerName#"
        }
        routingMethods += [name:"$actionName$nameSufix", file:RubyUtil.ROUTES_ID, value:"$pathValuePrefix$actionName",
                           arg:"$argsPrefix$actionName"]
    }

    private configureResourcesDefaultRoute(String prefix, String indexName, String original, String controllerName,
                                           String aliasSingular){
        if(prefix){
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += [name:"${formatedPrefix}_$indexName", file:RubyUtil.ROUTES_ID,
                               value:"/${prefix}/$original", arg:"${prefix}/$controllerName#index"]
            routingMethods += [name:"new_${formatedPrefix}_$aliasSingular", file:RubyUtil.ROUTES_ID,
                               value:"/${prefix}/$original/new", arg:"${prefix}/$controllerName#new"]
            routingMethods += [name:"edit_${formatedPrefix}_$aliasSingular", file:RubyUtil.ROUTES_ID,
                               value:"/${prefix}/$original/.*/edit", arg:"${prefix}/$controllerName#edit"]
            routingMethods += [name:"${formatedPrefix}_${aliasSingular}", file:RubyUtil.ROUTES_ID,
                               value:"/${prefix}/$original/", arg:"${prefix}/$controllerName#show"]
        } else {
            routingMethods += [name:indexName, file:RubyUtil.ROUTES_ID, value:"/$original", arg:"$controllerName#index"]
            routingMethods += [name:"new_$aliasSingular", file:RubyUtil.ROUTES_ID, value:"/$original/new", arg:"$controllerName#new"]
            routingMethods += [name:"edit_$aliasSingular", file:RubyUtil.ROUTES_ID, value:"/$original/.*/edit", arg:"$controllerName#edit"]
            routingMethods += [name:aliasSingular, file:RubyUtil.ROUTES_ID, value:"/$original/", arg:"$controllerName#show"]
        }
    }

    private generateResourceExceptRoutes(def except, String prefix, String original, String controllerName, String alias){
        def all = ["edit", "new", "show"]
        def routesToGenerate = all - except
        def formatedPrefix = prefix?.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
        routesToGenerate.each{ actionName ->
            switch(actionName){
                case "new":
                    if(prefix){
                        routingMethods += [name:"new_${formatedPrefix}_$alias", file:RubyUtil.ROUTES_ID,
                                           value:"/${prefix}/$original/new", arg:"${prefix}/$controllerName#new"]
                    } else {
                        routingMethods += [name:"new_$alias", file:RubyUtil.ROUTES_ID, value:"/$original/new",
                                           arg:"$controllerName#new"]
                    }
                    break
                case "edit":
                    if(prefix){
                        routingMethods += [name:"edit_${formatedPrefix}_$alias", file:RubyUtil.ROUTES_ID,
                                           value:"/${prefix}/$original/.*/edit", arg:"${prefix}/$controllerName#edit"]
                    } else {
                        routingMethods += [name:"edit_$alias", file:RubyUtil.ROUTES_ID, value:"/$original/.*/edit",
                                           arg:"$controllerName#edit"]
                    }
                    break
                case "show":
                    if(prefix){
                        routingMethods += [name:"${formatedPrefix}_${alias}", file:RubyUtil.ROUTES_ID,
                                           value:"/${prefix}/$original/", arg:"${prefix}/$controllerName#show"]
                    } else {
                        routingMethods += [name:alias, file:RubyUtil.ROUTES_ID, value:"/$original/",
                                           arg:"$controllerName#show"]
                    }
                    break
            }
        }
    }

    private generateBasicResourceRoutes(def args, String prefix, String indexName, String original, String singular,
                                        String controllerName){
        String aliasSingular = singular

        if(!args.as.empty) {
            //aliasSingular = Inflector.singularize(args.as.value)
            //indexName = args.as.value
            def plural = args.as.value
            aliasSingular = terms?.find{ it.plural == plural }?.singular
            if(!aliasSingular) aliasSingular = Inflector.singularize(plural)
            indexName = args.as.value
        }
        if(!args.controller.empty) controllerName = args.controller.value
        if(!args.member.empty){
            def values = extractArgValues(args.member)*.value
            values.each{ value ->
                generateResourcesMemberRoute(value, prefix, original, aliasSingular, controllerName)
            }
        }
        if(!args.collection.empty){
            def values = extractArgValues(args.collection)*.value
            values.each{ value ->
                generateResourcesCollectionRoute(value, prefix, original, indexName, controllerName)
            }
        }
        if(!args.only.empty){
            def values = args.only*.value
            values.each{ value ->
                generateResourcesOnlyRoutes(value, prefix, original, controllerName, indexName, aliasSingular)
            }
        }
        if(!args.except.empty) {
            def values = args.except*.value
            generateResourceExceptRoutes(values, prefix, original, controllerName, aliasSingular)
        }
        if(args.except.empty && args.only.empty) {
            configureResourceDefaultRoute(prefix, original, controllerName, aliasSingular)
        }
        return aliasSingular
    }

    private configureResourceDefaultRoute(String prefix, String original, String controllerName, String aliasSingular){
        if(prefix){
            def formatedPrefix = prefix.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")
            routingMethods += [name:"new_${formatedPrefix}_$aliasSingular", file:RubyUtil.ROUTES_ID,
                               value:"/${prefix}/$original/new", arg:"${prefix}/$controllerName#new"]
            routingMethods += [name:"edit_${formatedPrefix}_$aliasSingular", file:RubyUtil.ROUTES_ID,
                               value:"/${prefix}/$original/.*/edit", arg:"${prefix}/$controllerName#edit"]
            routingMethods += [name:"${formatedPrefix}_${aliasSingular}", file:RubyUtil.ROUTES_ID,
                               value:"/${prefix}/$original/", arg:"${prefix}/$controllerName#show"]
        } else {
            routingMethods += [name:"new_$aliasSingular", file:RubyUtil.ROUTES_ID, value:"/$original/new", arg:"$controllerName#new"]
            routingMethods += [name:"edit_$aliasSingular", file:RubyUtil.ROUTES_ID, value:"/$original/.*/edit", arg:"$controllerName#edit"]
            routingMethods += [name:aliasSingular, file:RubyUtil.ROUTES_ID, value:"/$original/", arg:"$controllerName#show"]
        }

    }

    private generateResourceRoutes(Node node, String namespaceValue, String indexName, String original, String plural,
                                   String singular, String controllerName){
        def resourcesData = extractResourcesData(node, namespaceValue, original, singular, controllerName, indexName)
        String aliasSingular = singular
        def args = resourcesData.args
        if(resourceArgsIsEmpty(args)){
            configureResourceDefaultRoute(namespaceValue, original, controllerName, aliasSingular)
            routingMethods += resourcesData.memberRoutes
            routingMethods += resourcesData.collectionRoutes
        }
        else {
            def alias = generateBasicResourceRoutes(args, namespaceValue, indexName, original, singular, controllerName)
            if(alias != singular){
                resourcesData.memberRoutes?.each{ route ->
                    routingMethods += route.name.replace(singular, alias)
                }
                resourcesData.collectionRoutes?.each{ route ->
                    routingMethods += route.name.replace(singular, alias)
                }
            } else{
                routingMethods += resourcesData.memberRoutes
                routingMethods += resourcesData.collectionRoutes
            }
        }

        def parentNameSingular
        def parentNamePlural
        def originalParentName
        if(namespaceValue) {
            parentNameSingular = "${namespaceValue}/${singular}"
            parentNamePlural = "${namespaceValue}/${plural}"
            originalParentName = "${namespaceValue}/${original}"
        } else {
            parentNameSingular = singular
            parentNamePlural = plural
            originalParentName = original
        }

        resourcesData.nestedResourcesList.each{ nestedResources ->
            extractResources(nestedResources, null, parentNameSingular, parentNamePlural, originalParentName)
        }

        resourcesData.nestedResourceList.each{ nestedResource ->
            extractResource(nestedResource, null, parentNameSingular, parentNamePlural, originalParentName)
        }
    }

    private static getNameForNamespace(Node iVisited, String prefix){
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll{ it instanceof SymbolNode }
        String name
        if(prefix) name = prefix+"/"+entities.first().name
        else name = entities.first().name
        return name
    }

    private extractRoutesInNamespace(Node iVisited, String prefix){
        String name = getNameForNamespace(iVisited, prefix)
        def childNodes = getChildNodes(iVisited)

        for(int i=0; i<childNodes.size(); i++){
            if( !(childNodes.get(i) in nodes)) continue
            extractData(childNodes.get(i), name)
            nodes = nodes - [childNodes.get(i)]
        }
    }

    private extractDevise(Node iVisited, String prefix){
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll{ it instanceof SymbolNode }
        entities?.each{ entity ->
            def plural = entity.name
            def singular = terms?.find{ it.plural == plural }?.singular
            if(!singular) singular = plural

            //Authenticatable (default)
            routingMethods += [name:"new_${singular}_session", file:RubyUtil.ROUTES_ID, value:"/$plural/sign_in",
                               arg:"devise/sessions#new"]
            routingMethods += [name:"${singular}_session", file:RubyUtil.ROUTES_ID, value:"/$plural/sign_in",
                               arg:"devise/sessions#create"]
            routingMethods += [name:"destroy_${singular}_session", file:RubyUtil.ROUTES_ID, value:"/$plural/sign_out",
                               arg:"devise/sessions#destroy"]
            //Recoverable
            routingMethods += [name:"new_${singular}_password", file:RubyUtil.ROUTES_ID, value:"/$plural/password/new",
                               arg:"devise/passwords#new"]
            routingMethods += [name:"edit_${singular}_password", file:RubyUtil.ROUTES_ID, value:"/$plural/password/edit",
                               arg:"devise/passwords#edit"]
            routingMethods += [name:"${singular}_password", file:RubyUtil.ROUTES_ID, value:"/$plural/password",
                               arg:"devise/passwords#update"]
            //Confirmable
            routingMethods += [name:"new_${singular}_confirmation", file:RubyUtil.ROUTES_ID,
                               value:"/$plural/confirmation/new", arg:"devise/confirmations#new"]
            routingMethods += [name:"${singular}_confirmation", file:RubyUtil.ROUTES_ID, value:"/$plural/confirmation",
                               arg:"devise/confirmations#show"]
            //Registerable
            routingMethods += [name:"new_${singular}_registration", file:RubyUtil.ROUTES_ID,
                               value:"/$plural/registration/new", arg:"devise/registrations#new"]
            routingMethods += [name:"edit_${singular}_registration", file:RubyUtil.ROUTES_ID,
                               value:"/$plural/registration/edit", arg:"devise/registrations#edit"]
            routingMethods += [name:"${singular}_registration", file:RubyUtil.ROUTES_ID, value:"/$plural/registration",
                               arg:"devise/registrations#update"]
            //Lockable
            routingMethods += [name:"new_${singular}_unlock", file:RubyUtil.ROUTES_ID, value:"/$plural/unlock/new",
                               arg:"devise/unlocks#new"]
            routingMethods += [name:"${singular}_unlock", file:RubyUtil.ROUTES_ID, value:"/$plural/unlock",
                               arg:"devise/unlocks#create"]

            //Rememberable
            //Trackable
            //Validatable
            //Timeoutable
            //Omniauthable
        }
    }

    private extractResources(Node iVisited, String namespaceValue, String parentNameSingular, String parentNamePlural,
                             String originalParentName){
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll{ it instanceof SymbolNode }
        entities?.each{ entity ->
            String original = entity.name
            String plural = original
            String singular = terms?.find{ it.plural == plural }?.singular
            if(!singular) singular = Inflector.singularize(plural)
            if(original == singular) plural = English.plural(original,2)
            String controllerName = plural
            String indexName
            if(!parentNameSingular && !parentNamePlural) {
                indexName = original
            }
            else {
                indexName = "${parentNamePlural.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$original"
                singular = "${parentNameSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$singular"
                plural = "${parentNamePlural.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$plural"
                original = "${originalParentName}/.*/$original"
            }

            generateResourcesRoutes(iVisited, namespaceValue, indexName, original, plural, singular, controllerName)
        }
    }

    private extractResource(Node iVisited, String namespaceValue, String parentNameSingular, String parentNamePlural, String originalParentName){
        List<SymbolNode> entities = iVisited?.args?.childNodes()?.findAll{ it instanceof SymbolNode }
        entities?.each{ entity ->
            def original = entity.name
            def plural = original
            def singular = terms?.find{ it.plural == plural }?.singular
            if(!singular) singular = Inflector.singularize(plural)
            if(original == singular) plural = English.plural(original,2)
            String controllerName = plural
            String indexName
            if(!parentNameSingular && !parentNamePlural) {
                indexName = original
            }
            else {
                indexName = "${parentNameSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$original"
                singular = "${parentNameSingular.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$singular"
                plural = "${parentNamePlural.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, "_")}_$plural"
                original = "${originalParentName}/.*/$original"
            }

            generateResourceRoutes(iVisited, namespaceValue, indexName, original, plural, singular, controllerName)
        }
    }

    private extractData(Node iVisited, String namespaceValue){
        switch (iVisited?.name){
            case "namespace": //it a grouping mechanism
                log.info "namespace: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractRoutesInNamespace(iVisited, namespaceValue)
                break
            case "scope"://similar to namespace
                log.info "scope: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                break
            case "resources":
                extractResources(iVisited, namespaceValue, null, null, null)
                break
            case "resource":
                //similar to resources (http://stackoverflow.com/questions/9194767/difference-between-resource-and-resources-methods)
                extractResource(iVisited, namespaceValue, null, null, null)
                break
            case "get": //it is also used into resources
                registryGetNonResourcefulRoute(iVisited, namespaceValue)
                break
            case "root":
                log.info "root: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryRootRoute(iVisited, namespaceValue)
                break
            case "match":
                log.info "match: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                registryMatchNonResourcefulRoute(iVisited, namespaceValue)
                break
            case "devise_for": //devise is a gem for authentication
                log.info "devise_for: ${iVisited?.name}; line:${iVisited.position.startLine+1}"
                extractDevise(iVisited, namespaceValue)
                break
            case "mount": //calls rake application
            case "redirect": //it is used with "get" and others; it does not require treatment
            case "devise_scope": //it is not important for the study purpose
            case "post": //visil call is a get request; it is not necessary to extract routes from post, put, patch or delete
            case "put":
            case "patch":
            case "delete":
            case "authenticated":
            case "routes":
            case "collection": //it is used into resources
            case "member": //it is used into resources
                break
            default: registryMatchNonResourcefulRoute(iVisited, namespaceValue)
        }
    }
}

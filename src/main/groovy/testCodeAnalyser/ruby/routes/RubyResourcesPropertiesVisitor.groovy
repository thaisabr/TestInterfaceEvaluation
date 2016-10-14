package testCodeAnalyser.ruby.routes

import org.jrubyparser.ast.*
import org.jrubyparser.util.NoopVisitor

class RubyResourcesPropertiesVisitor extends NoopVisitor {

    def parent
    def rangesOfNestedResources
    def rangesOfNestedResource
    List argsNodes
    List<Node> alreadyVisitedNodes
    static argsOfInterest = ["as", "member", "collection", "only", "except", "controller", "get", "put", "post", "patch", "delete", "path"]

    RubyResourcesPropertiesVisitor(Node parent, List rangesOfNestedResources, List rangesOfNestedResource, List<Node> alreadyVisitedNodes) {
        this.parent = parent
        this.rangesOfNestedResources = rangesOfNestedResources
        this.rangesOfNestedResource = rangesOfNestedResource
        this.argsNodes = []
        this.alreadyVisitedNodes = alreadyVisitedNodes
    }

    def getValues() {
        argsNodes.sort { it.line }.sort { it.position }
    }

    static findParentNodes(List values) {
        def parents = []
        values.each { v ->
            Node node = v.node
            def others = values - [v]
            if (!(others.any { node.isDescendentOf(it.node) })) parents += v
        }
        return parents
    }

    def organizeNodesByParent(List values) {
        def result = [] //parent, children
        def parents = findParentNodes(values)
        parents.each { parent ->
            def others = values - [parent]
            def children = others.findAll { it.node.isDescendentOf(parent.node) }
            result += [parent: parent, children: children]
        }
        return result
    }

    def getOrganizedValues() {
        def values = getValues()

        //[parent:[line, position, value, node], children:List[line, position, value, node]]
        def valuesOrganizedByParent = organizeNodesByParent(values)

        /* configures member */
        int memberIndex = values.findIndexOf { it.value == "member" }
        def membersValues = []
        def member = valuesOrganizedByParent.find { it.parent.value == "member" && !it.children.empty }
        if (member) {
            memberIndex = -1
            membersValues = member.children
            values = values - [member.parent]
            values = values - member.children
        }

        /* configures collection */
        int collectionIndex = values.findIndexOf { it.value == "collection" }
        def collectionsValues = []
        def collection = valuesOrganizedByParent.find { it.parent.value == "collection" && !it.children.empty }
        if (collection) {
            collectionIndex = -1
            collectionsValues = collection.children
            values = values - [collection.parent]
            values = values - collection.children
        }

        int asIndex = values.findIndexOf { it.value == "as" }
        def asValue = ""
        int onlyIndex = values.findIndexOf { it.value == "only" }
        def onlyValues = []
        int exceptIndex = values.findIndexOf { it.value == "except" }
        def exceptValues = []
        int controllerIndex = values.findIndexOf { it.value == "controller" }
        def controllerValue = ""
        int pathIndex = values.findIndexOf { it.value == "path" }
        def pathValue = ""
        def indexes = ([asIndex, memberIndex, collectionIndex, onlyIndex, exceptIndex, controllerIndex, pathIndex]?.unique() - [-1])?.sort()

        indexes.each { i ->
            def otherIndexes = indexes - [i]
            def result = []
            if (otherIndexes.empty) result = values.subList(i + 1, values.size())
            else {
                def j = i + 1
                while (!(j in otherIndexes) && j < values.size()) {
                    result += values.get(j)
                    j++
                }
            }
            switch (i) {
                case asIndex: asValue = values.get(i + 1)
                    break
                case memberIndex: membersValues = result
                    break
                case collectionIndex: collectionsValues = result
                    break
                case onlyIndex: onlyValues = result
                    break
                case exceptIndex: exceptValues = result
                    break
                case controllerIndex: controllerValue = values.get(i + 1)
                    break
                case pathIndex: pathValue = values.get(i + 1)
                    break
            }
        }

        if (!asValue.empty) asValue = [line: asValue.line, position: asValue.position, value: asValue.value]
        membersValues = membersValues.collect { [line: it.line, position: it.position, value: it.value] }
        collectionsValues = collectionsValues.collect { [line: it.line, position: it.position, value: it.value] }
        onlyValues = onlyValues.collect { [line: it.line, position: it.position, value: it.value] }
        exceptValues = exceptValues.collect { [line: it.line, position: it.position, value: it.value] }
        if (!controllerValue.empty) controllerValue = [line: controllerValue.line, position: controllerValue.position, value: controllerValue.value]
        if (!pathValue.empty) pathValue = [line: pathValue.line, position: pathValue.position, value: pathValue.value]

        [as: asValue, member: membersValues, collection: collectionsValues, only: onlyValues, except: exceptValues,
         controller: controllerValue, path: pathValue]
    }

    @Override
    Object visitStrNode(StrNode iVisited) {
        super.visitStrNode(iVisited)
        if (iVisited in alreadyVisitedNodes || !(alreadyVisitedNodes.findAll {
            iVisited.isDescendentOf(it)
        }.empty)) return iVisited
        def nested1 = rangesOfNestedResources.findAll { iVisited.position.startLine in it }
        def nested2 = rangesOfNestedResource.findAll { iVisited.position.startLine in it }
        if (alreadyVisitedNodes.empty && nested1.empty && nested2.empty) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.value, node: iVisited]
        }
        if (!alreadyVisitedNodes.empty && nested1.empty && nested2.empty && !iVisited.position.equals(parent.position)) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.value, node: iVisited]
        }
        return iVisited
    }

    @Override
    Object visitSymbolNode(SymbolNode iVisited) {
        super.visitSymbolNode(iVisited)
        if (iVisited in alreadyVisitedNodes || !(alreadyVisitedNodes.findAll {
            iVisited.isDescendentOf(it)
        }.empty)) return iVisited
        def nested1 = rangesOfNestedResources.findAll { iVisited.position.startLine in it }
        def nested2 = rangesOfNestedResource.findAll { iVisited.position.startLine in it }
        if (alreadyVisitedNodes.empty && nested1.empty && nested2.empty) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name, node: iVisited]
        }
        if (!alreadyVisitedNodes.empty && nested1.empty && nested2.empty && !iVisited.position.equals(parent.position)) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name, node: iVisited]
        }
        return iVisited
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)
        if (iVisited in alreadyVisitedNodes || !(alreadyVisitedNodes.findAll {
            iVisited.isDescendentOf(it)
        }.empty)) return iVisited
        def nested1 = rangesOfNestedResources.findAll { iVisited.position.startLine in it }
        def nested2 = rangesOfNestedResource.findAll { iVisited.position.startLine in it }
        if (alreadyVisitedNodes.empty && nested1.empty && nested2.empty) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name, node: iVisited]
        }
        if (!alreadyVisitedNodes.empty && iVisited.name in argsOfInterest && nested1.empty && nested2.empty &&
                !iVisited.position.equals(parent.position)) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name, node: iVisited]
        }
        return iVisited
    }

    @Override
    Object visitCallNode(CallNode iVisited) {
        super.visitCallNode(iVisited)
        if (iVisited in alreadyVisitedNodes || !(alreadyVisitedNodes.findAll {
            iVisited.isDescendentOf(it)
        }.empty)) return iVisited
        def nested1 = rangesOfNestedResources.findAll { iVisited.position.startLine in it }
        def nested2 = rangesOfNestedResource.findAll { iVisited.position.startLine in it }
        if (alreadyVisitedNodes.empty && nested1.empty && nested2.empty) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name, node: iVisited]
        }
        if (!alreadyVisitedNodes.empty && iVisited.name in argsOfInterest && nested1.empty && nested2.empty &&
                !iVisited.position.equals(parent.position)) {
            argsNodes += [line: iVisited.position.startLine, position: iVisited.position.startOffset, value: iVisited.name, node: iVisited]
        }
        return iVisited
    }

}

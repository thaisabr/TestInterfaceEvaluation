package br.ufpe.cin.tan.analysis.itask

import br.ufpe.cin.tan.test.ruby.MethodBody
import br.ufpe.cin.tan.util.Util

class ITest extends TaskInterface {

    Set methods //static and non-static called methods; keys:[name, type, file, step]
    Set staticFields //declared static fields; [name, type, value, file]
    Set fields //declared fields; [name, type, value, file]
    Set accessedProperties //accessed fields and constants, for example: "foo.bar"

    /******************************************** used by web-based tests *********************************************/
    Set calledPageMethods
    //keys:[file, name, args] //help to identify referenced pages (GSP files); methods "to" and "at";
    Set<String> referencedPages
    /** ****************************************************************************************************************/

    Set matchStepErrors
    Set compilationErrors //[path: String, msgs: List<String>]
    Set notFoundViews
    Set foundAcceptanceTests
    Set foundStepDefs
    Set codeFromViewAnalysis
    int visitCallCounter
    Set lostVisitCall
    Set trace

    Set<MethodBody> code

    ITest() {
        super()
        this.methods = [] as Set
        this.staticFields = [] as Set
        this.fields = [] as Set
        this.accessedProperties = [] as Set
        this.calledPageMethods = [] as Set
        this.referencedPages = [] as Set
        this.matchStepErrors = [] as Set
        this.compilationErrors = [] as Set
        this.notFoundViews = [] as Set
        this.foundAcceptanceTests = [] as Set
        this.foundStepDefs = [] as Set
        this.codeFromViewAnalysis = [] as Set
        this.lostVisitCall = [] as Set
        this.trace = [] as Set
        this.code = [] as Set
    }

    @Override
    String toString() {
        def files = findFilteredFiles()
        if (files.empty) return ""
        else {
            def text = ""
            files.each {
                if (it) text += it + ", "
            }
            def index = text.lastIndexOf(",")
            if (index != -1) return text.substring(0, index)
            else return ""
        }
    }

    boolean isEmpty() {
        def files = getFiles()
        if (files.empty) true
        else false
    }

    /***
     * Lists all production files related to the task.
     * Until the moment, the identification of such files is made by the usage of production classes and methods only.
     *
     * @return a list of files
     */
    Set<String> getFiles() {
        def files = getAllProdFiles()
        Util.organizePathsForInterfaces(files) as Set
    }

    //filtering result to only identify view and/or controller files
    Set<String> findFilteredFiles() {
        Util.filterFiles(this.getFiles())
    }

    Set<String> getViewFilesForFurtherAnalysis() {
        def files = getAllProdFiles()
        files?.findAll { String f -> Util.isViewFile(f) }
    }

    def collapseInterfaces(ITest task) {
        this.classes += task.classes
        this.methods += task.methods
        this.staticFields += task.staticFields
        this.fields += task.fields
        this.accessedProperties += task.accessedProperties
        this.calledPageMethods += task.calledPageMethods
        this.referencedPages += task.referencedPages
        this.matchStepErrors += task.matchStepErrors
        this.compilationErrors += task.compilationErrors
        this.notFoundViews += task.notFoundViews
        this.foundAcceptanceTests += task.foundAcceptanceTests
        this.foundStepDefs += task.foundStepDefs
        this.codeFromViewAnalysis += task.codeFromViewAnalysis
        this.visitCallCounter += task.visitCallCounter
        this.timestamp += task.timestamp
        this.code += task.code
    }

    static ITest collapseInterfaces(List<ITest> interfaces) {
        def taskInterface = new ITest()
        interfaces.each { taskInterface.collapseInterfaces(it) }
        return taskInterface
    }

    ITest minus(ITest task) {
        def taskInterface = new ITest()
        taskInterface.classes = classes - task.classes
        taskInterface.methods = methods - task.methods
        taskInterface.staticFields = staticFields - task.staticFields
        taskInterface.fields = fields - task.fields
        taskInterface.accessedProperties = accessedProperties - task.accessedProperties
        taskInterface.calledPageMethods = calledPageMethods - task.calledPageMethods
        taskInterface.referencedPages = referencedPages - task.referencedPages
        taskInterface.matchStepErrors = matchStepErrors - task.matchStepErrors
        taskInterface.compilationErrors = compilationErrors - task.compilationErrors
        taskInterface.notFoundViews = notFoundViews - task.notFoundViews
        taskInterface.foundAcceptanceTests = foundAcceptanceTests - task.foundAcceptanceTests
        taskInterface.foundStepDefs = foundStepDefs - task.foundStepDefs
        taskInterface.codeFromViewAnalysis = codeFromViewAnalysis - task.codeFromViewAnalysis
        taskInterface.visitCallCounter = visitCallCounter - task.visitCallCounter
        taskInterface.timestamp = timestamp - task.timestamp
        taskInterface.code = code - task.code
        return taskInterface
    }

    /***
     * Lists all files related to the task.
     * Until the moment, the identification of such files is made by the usage of classes and methods only.
     *
     * @return a list of files
     */
    Set<String> findAllFiles() {
        def classes = classes*.file
        def methodFiles = methods?.findAll { it.type != null && !it.type.empty && it.type != "StepCall" }*.file
        def files = ((classes + methodFiles + referencedPages) as Set)?.sort()
        def canonicalPath = Util.getRepositoriesCanonicalPath()
        files?.findResults { i -> i ? i - canonicalPath : null } as Set
    }

    private Set<String> getAllProdFiles() {
        //production classes
        def classes = (classes?.findAll { Util.isProductionFile(it.file) })*.file

        //production methods
        def methodFiles = methods?.findAll {
            it.type != null && !it.type.empty && it.type != "StepCall" &&
                    it.file && Util.isProductionFile(it.file)
        }*.file

        //production files
        ((classes + methodFiles + referencedPages) as Set)?.sort()
    }

    String toStringDetailed() {
        def text = ""
        def canonicalPath = Util.getRepositoriesCanonicalPath()

        text += "Classes: ${classes.size()}\n"
        classes?.sort { it.name }?.each {
            if (it.file) text += "[name:${it.name}, file:${it.file - canonicalPath}, step:${it.step}]\n"
            else text += it.toString() + "\n"
        }

        def methodFiles = methods?.findAll { it.type != null && !it.type.empty && it.type != "StepCall" }
        text += "\nMethods: ${methodFiles.size()}\n"
        methodFiles?.sort { it.name }?.each {
            if (it.file) text += "[name:${it.name}, type:${it.type}, file:${it.file - canonicalPath}, step:${it.step}]\n"
            else text += it.toString() + "\n"
        }

        def pages = referencedPages.collect { it - canonicalPath }?.unique()?.sort()
        text += "\nReferenced pages: ${pages.size()}\n"
        pages?.each { text += it.toString() + "\n" }

        text
    }

}

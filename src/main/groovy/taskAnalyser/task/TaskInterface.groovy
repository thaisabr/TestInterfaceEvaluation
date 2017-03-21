package taskAnalyser.task

import groovy.time.TimeDuration
import util.Util


class TaskInterface {

    Set classes //instantiated classes; keys:[name, file]
    Set methods //static and non-static called methods; keys:[name, type, file]
    Set staticFields //declared static fields; [name, type, value, file]
    Set fields //declared fields; [name, type, value, file]
    Set accessedProperties //accessed fields and constants, for example: "foo.bar"

    /******************************************** used by web-based tests *********************************************/
    Set calledPageMethods
    //keys:[file, name, args] //help to identify referenced pages (GSP files); methods "to" and "at";
    Set<String> referencedPages
    /** ****************************************************************************************************************/

    Set matchStepErrors
    Set compilationErrors
    Set notFoundViews
    Set foundAcceptanceTests
    Set codeFromViewAnalysis
    int visitCallCounter
    TimeDuration timestamp //time to compute task interface

    TaskInterface() {
        this.classes = [] as Set
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
        this.codeFromViewAnalysis = [] as Set
        this.timestamp = new TimeDuration(0,0,0,0)
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
        def files = findAllProdFiles()
        if (files.empty) true
        else false
    }

    /***
     * Lists all production files related to the task.
     * Until the moment, the identification of such files is made by the usage of production classes and methods only.
     *
     * @return a list of files
     */
    Set<String> findAllProdFiles() {
        //production classes
        def classes = (classes?.findAll { Util.isProductionFile(it.file) })*.file

        //production methods
        def methodFiles = methods?.findAll { it.type!=null && !it.type.empty && it.type!="StepCall" && Util.isProductionFile(it.file) }*.file

        //production files
        def files = ((classes + methodFiles + referencedPages) as Set)?.sort()

        def canonicalPath = Util.getRepositoriesCanonicalPath()
        files?.findResults { i -> i ? i - canonicalPath : null } as Set
    }

    //filtering result to only identify view and/or controller files
    Set<String> findFilteredFiles(){
        Util.filterFiles(this.findAllProdFiles())
    }

    def collapseInterfaces(TaskInterface task) {
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
        this.codeFromViewAnalysis += task.codeFromViewAnalysis
        this.visitCallCounter += task.visitCallCounter
        this.timestamp += this.timestamp
    }

    static TaskInterface collapseInterfaces(List<TaskInterface> interfaces) {
        def taskInterface = new TaskInterface()
        interfaces.each { taskInterface.collapseInterfaces(it) }
        return taskInterface
    }

    /***
     * Lists all files related to the task.
     * Until the moment, the identification of such files is made by the usage of classes and methods only.
     *
     * @return a list of files
     */
    Set<String> findAllFiles(){
        def classes = classes*.file
        def methodFiles = methods?.findAll { it.type!=null && !it.type.empty && it.type!="StepCall" }*.file
        def files = ((classes + methodFiles + referencedPages) as Set)?.sort()
        def canonicalPath = Util.getRepositoriesCanonicalPath()
        files?.findResults { i -> i ? i - canonicalPath : null } as Set
    }

}

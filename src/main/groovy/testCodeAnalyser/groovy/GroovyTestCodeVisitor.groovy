package testCodeAnalyser.groovy

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.SourceUnit
import taskAnalyser.TaskInterface
import testCodeAnalyser.TestCodeVisitor
import util.Util

class GroovyTestCodeVisitor extends ClassCodeVisitorSupport implements TestCodeVisitor {

    SourceUnit source
    TaskInterface taskInterface
    List<String> projectFiles //valid files

    public GroovyTestCodeVisitor(String repositoryPath){
        this.source = null
        this.projectFiles = Util.findFilesFromDirectoryByLanguage(repositoryPath)
        this.taskInterface = new TaskInterface()
    }

    private registryMethodCall(MethodCallExpression call){
        def result = false
        def className =  Util.configClassnameFromMethod(call.receiver.type.name)
        def path = Util.getClassPathForGroovy(className, projectFiles)
        if( Util.isValidClass(className, path) ) {
            taskInterface.methods += [name:call.methodAsString, type:className, file:path]
            result = true
        }
        result
    }

    static boolean isValidMethod(String referencedMethod){
        if(referencedMethod ==~ /(println|print|setBinding)/) false
        else true
    }

    private boolean registryIsInternalValidMethodCall(MethodCallExpression call){
        def result = false

        if (call.implicitThis && isValidMethod(call.methodAsString)) { //call from test code
            result = true
            /*if( Util.isPageMethod(call.methodAsString) ){ //PEGAR NOME DE ARQUIVO GSP
                def value = call.arguments.text
                def className = value.substring(1,value.length()-1)
                def path = Util.getClassPathForGroovy(className, projectFiles)
                if(!path?.isEmpty()) taskInterface.calledPageMethods += [name: call.methodAsString, arg:className, file:path]
            }
            else {
                //calls for other methods do not need to be registered
                //methods += [name: call.methodAsString, type: className]
            }*/
        }

        return result
    }

    private boolean registryIsExternalValidMethodCall(MethodCallExpression call){
        def result = false
        if(!call.implicitThis){//call from other class
            if (call.receiver.dynamicTyped) {
                taskInterface.methods += [name: call.methodAsString, type: null, file:null]
                result = true
            } else {
                result = registryMethodCall(call)
            }
        }
        result
    }

    private static printMethodCall(MethodCallExpression call){
        println "!!!!!!!!!!!!! composite call !!!!!!!!!!!!!"
        println "call text: $call.text"
        println "call.receiver.class: ${call.receiver.toString()}"
        call.properties.each{ k, v ->
            println "$k: $v"
        }
        println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    }

    @Override
    protected SourceUnit getSourceUnit() {
        source
    }

    @Override
    public void visitConstructorCallExpression(ConstructorCallExpression call){
        super.visitConstructorCallExpression(call)
        def path = Util.getClassPathForGroovy(call?.type?.name, projectFiles)
        if( Util.isValidClass(call?.type?.name, path) ){
            taskInterface.classes += [name: call?.type?.name, file:path]
        }
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call){
        super.visitMethodCallExpression(call)

        switch (call.receiver.class){
            case ConstructorCallExpression.class: //composite call that includes constructor call
                // ex: def path = new File(".").getCanonicalPath() + File.separator + "test" + File.separator + "files" + File.separator + "TCS.pdf"
                def path = Util.getClassPathForGroovy(call.receiver.type.name, projectFiles)
                if (Util.isValidClass(call.receiver.type.name, path)) {
                    def className = call.objectExpression.type.name
                    taskInterface.classes += [name:call.receiver.type.name, file:path]
                    taskInterface.methods += [name:call.methodAsString, type:className, file:path]
                }
                break
            case VariableExpression.class: //call that uses a reference variable
                def result = registryIsInternalValidMethodCall(call)
                if(!result) registryIsExternalValidMethodCall(call)
                break
            case MethodCallExpression.class: //composite call that does not include constructor call
            case StaticMethodCallExpression.class: //composite static call
            case PropertyExpression.class:
            case ClassExpression.class: //static method call from another class that uses the class name
                registryMethodCall(call)
                break
            case RangeExpression.class: //API call
                break
            default:
                printMethodCall(call)
        }
    }

    @Override
    /***
     * Visits static method or step method(static import)
     */
    public void visitStaticMethodCallExpression(StaticMethodCallExpression call){
        super.visitStaticMethodCallExpression(call)
        def path = Util.getClassPathForGroovy(call.ownerType.name, projectFiles)
        if (Util.isValidClass(call.ownerType.name, path)){
            def className = call.ownerType.name
            taskInterface.methods += [name:call.methodAsString, type:className, file:path]
        }
    }

    @Override
    public void visitField(FieldNode node){
        super.visitField(node)
        def className = node.type.name
        def path = Util.getClassPathForGroovy(className, projectFiles)
        if(Util.isValidClass(className, path)) {
            def result = [name:node.name, type:className, value:node.initialValueExpression.value, file:path]
            if (node.static) taskInterface.staticFields += result
            else taskInterface.fields += result
        }
    }

    @Override
    /***
     * Visits fields and constants from other classes, for example: "foo.bar"
     */
    public void visitPropertyExpression(PropertyExpression expression){
        super.visitPropertyExpression(expression)
        def path = Util.getClassPathForGroovy(expression.objectExpression.type.name, projectFiles)
        if ( Util.isValidClass(expression.objectExpression.type.name, path) ){
            def className = expression.objectExpression.type.name
            taskInterface.accessedProperties += [name:expression.propertyAsString, type:className, file:path]
        }
    }

}

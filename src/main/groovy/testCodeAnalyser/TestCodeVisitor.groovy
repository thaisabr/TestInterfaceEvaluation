package testCodeAnalyser

import taskAnalyser.task.TaskInterface


trait TestCodeVisitor {

    abstract TaskInterface getTaskInterface()

    abstract void setLastVisitedFile(String path)

    abstract getCalledSteps()

    MethodToAnalyse getStepDefinitionMethod() {
        null
    }

    int getVisitCallCounter(){
        0
    }

}
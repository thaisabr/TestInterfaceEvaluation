package testCodeAnalyser

import taskAnalyser.task.TaskInterface


interface TestCodeVisitor {

    TaskInterface getTaskInterface()
    void setLastVisitedFile(String path)
    def getCalledSteps()
    MethodToAnalyse getStepDefinitionMethod()

}
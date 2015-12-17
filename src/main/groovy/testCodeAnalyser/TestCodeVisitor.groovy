package testCodeAnalyser

import taskAnalyser.TaskInterface


interface TestCodeVisitor {

    TaskInterface getTaskInterface()
    void setLastVisitedFile(String path)

}
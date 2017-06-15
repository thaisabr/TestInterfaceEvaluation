package br.ufpe.cin.tan.test

import br.ufpe.cin.tan.analysis.itask.ITest


trait TestCodeVisitorInterface {

    abstract ITest getTaskInterface()

    abstract void setLastVisitedFile(String path)

    abstract getCalledSteps()

    MethodToAnalyse getStepDefinitionMethod() {
        null
    }

    int getVisitCallCounter(){
        0
    }

    Set getLostVisitCall(){
        [] as Set
    }

}
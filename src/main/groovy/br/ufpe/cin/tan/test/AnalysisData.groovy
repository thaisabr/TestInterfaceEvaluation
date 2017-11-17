package br.ufpe.cin.tan.test

import br.ufpe.cin.tan.test.ruby.MethodBody

class AnalysisData {

    Set matchStepErrors
    Set<AcceptanceTest> foundAcceptanceTests
    Set foundStepDefs
    Set codeFromViewAnalysis
    int visitCallCounter
    Set lostVisitCall //keys: path, line
    Set trace //keys: path, lines
    Set<MethodBody> testCode

    AnalysisData() {
        matchStepErrors = [] as Set
        foundAcceptanceTests = [] as Set
        foundStepDefs = [] as Set
        codeFromViewAnalysis = [] as Set
        lostVisitCall = [] as Set
        trace = [] as Set
        testCode = [] as Set
    }

}

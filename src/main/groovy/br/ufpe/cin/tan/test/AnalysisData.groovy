package br.ufpe.cin.tan.test

class AnalysisData {

    Set matchStepErrors
    Set<AcceptanceTest> foundAcceptanceTests
    Set codeFromViewAnalysis
    int visitCallCounter
    Set lostVisitCall //keys: path, line
    Set trace //keys: path, lines
    List<String> testCode

    AnalysisData() {
        matchStepErrors = [] as Set
        foundAcceptanceTests = []
        codeFromViewAnalysis = [] as Set
        lostVisitCall = [] as Set
        trace = [] as Set
        testCode = []
    }

}

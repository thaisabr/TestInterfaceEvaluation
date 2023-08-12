package br.ufpe.cin.tan.conflict

class ConflictAnalyzer {

    PairConflictResult conflictResult

    def computeConflictRiskForPair(PlannedTask task1, PlannedTask task2){
        def files1 = task1.itest.findAllFiles()
        def files2 = task2.itest.findAllFiles()
        def intersection = files1.intersect(files2)
        def union = (files1 + files2).unique()
        def relativeConflictRate = 0.0D
        if(!intersection.empty && !union.empty) relativeConflictRate = intersection.size()/union.size()

        this.conflictResult = new PairConflictResult(conflictingFiles:intersection,
                absoluteConflictRate: intersection.size(),
                relativeConflictRate: relativeConflictRate)
    }

    double sumAbsoluteConflictRiskForTasks(PlannedTask selectedTask, List<PlannedTask> ongoingTasks){
        double result = 0
        ongoingTasks.each{ ongoingTask ->
            def conflictRisk = computeConflictRiskForPair(selectedTask, ongoingTask)
            result += conflictRisk.absoluteConflictRate
        }
        result
    }

    double meanAbsoluteConflictRiskForTasks(PlannedTask selectedTask, List<PlannedTask> ongoingTasks){
        if(ongoingTasks==null || ongoingTasks.empty) 0
        else sumAbsoluteConflictRiskForTasks(selectedTask, ongoingTasks)/ongoingTasks.size()
    }

    double sumRelativeConflictRiskForTasks(PlannedTask selectedTask, List<PlannedTask> ongoingTasks){
        double result = 0
        ongoingTasks.each{ ongoingTask ->
            def conflictRisk = computeConflictRiskForPair(selectedTask, ongoingTask)
            result += conflictRisk.relativeConflictRate
        }
        result
    }

    double meanRelativeConflictRiskForTasks(PlannedTask selectedTask, List<PlannedTask> ongoingTasks){
        if(ongoingTasks==null || ongoingTasks.empty) 0
        else sumRelativeConflictRiskForTasks(selectedTask, ongoingTasks)/ongoingTasks.size()
    }
}

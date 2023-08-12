package br.ufpe.cin.tan.conflict

import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.analysis.task.TodoTask

class PlannedTask {

    TodoTask todoTask
    ITest itest

    PlannedTask(TodoTask todoTask, ITest itest) {
        this.todoTask = todoTask
        this.itest = itest
    }
}

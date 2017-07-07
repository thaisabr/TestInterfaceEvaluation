package br.ufpe.cin.tan.analysis.itask

import groovy.time.TimeDuration


abstract class TaskInterface {

    Set classes //instantiated classes; keys:[name, file]
    TimeDuration timestamp //time to compute task interface

    TaskInterface() {
        classes = [] as Set
        timestamp = new TimeDuration(0, 0, 0, 0)
    }

    abstract Set<String> findFilteredFiles()

}

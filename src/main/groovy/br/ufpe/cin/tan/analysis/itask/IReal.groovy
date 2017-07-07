package br.ufpe.cin.tan.analysis.itask

import br.ufpe.cin.tan.util.Util


class IReal extends TaskInterface {

    boolean isEmpty() {
        if (files.empty) true
        else false
    }

    Set<String> getFiles() {
        def candidates = classes.collect { Util.REPOSITORY_FOLDER_PATH + it.file }
        def prodFiles = candidates?.findAll { Util.isProductionFile(it) }
        if (prodFiles.empty) return []
        def files = prodFiles
        Util.organizePathsForInterfaces(files) as Set
    }

    @Override
    Set<String> findFilteredFiles() {
        Util.filterFiles(files)
    }

}

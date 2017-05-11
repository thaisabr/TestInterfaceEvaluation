package br.ufpe.cin.tan.analysis.itask

import br.ufpe.cin.tan.util.Util


class IReal extends TaskInterface {

    boolean isEmpty() {
        if (files.empty) true
        else false
    }

    Set<String> getFiles() {
        def prodFiles = classes?.findAll { Util.isProductionFile(it.file) }
        if(prodFiles.empty) return []
        def files = prodFiles*.file
        def repoPath = Util.getRepositoriesCanonicalPath()
        files?.findResults { i -> i ? i - repoPath : null } as Set
    }

    @Override
    Set<String> findFilteredFiles() {
        Util.filterFiles(files)
    }

}

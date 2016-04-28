package commitAnalyser

import util.ConstantData
import util.exception.CloningRepositoryException

class GitRepositoryManager {

    static List<GitRepository> repositories = []

    static GitRepository getRepository(String url) throws CloningRepositoryException {
        def repository = repositories.find{ (it.url - ConstantData.GIT_EXTENSION).equals(url) }
        if(!repository){
            repository = new GitRepository(url)
            repositories += repository
        }
        return repository
    }

}

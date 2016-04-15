package commitAnalyser

import util.ConstantData

class GitRepositoryManager {

    static List<GitRepository> repositories = []

    static GitRepository getRepository(String url){
        def repository = repositories.find{ (it.url - ConstantData.GIT_EXTENSION).equals(url) }
        if(!repository){
            repository = new GitRepository(url)
            repositories += repository
        }
        return repository
    }

}

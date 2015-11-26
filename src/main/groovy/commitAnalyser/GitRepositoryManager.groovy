package commitAnalyser

import util.Util


class GitRepositoryManager {

    static List<GitRepository> repositories = []

    static GitRepository getRepository(String url){
        def repository = repositories.find{ (it.url - Util.GIT_EXTENSION).equals(url) }
        if(!repository){
            repository = new GitRepository(url)
            repositories += repository
        }
        return repository
    }

}

package taskAnalyser

import commitAnalyser.Commit
import gherkinParser.Scenario
import util.Util


class Task {

    String repositoryIndex
    String repositoryUrl
    String id
    List<Commit> commits
    List<String> productionFiles
    List<String> testFiles
    List<Scenario> scenarios

    public Task(){

    }

    public Task(String index, String url, String id){
        repositoryIndex = index
        repositoryUrl = url
        this.id = id
        commits = []
        productionFiles = []
        testFiles = []
    }

    public Task(String index, String url, String id, List<Commit> commits){
        this(index, url, id)
        this.commits = commits
        commits*.files?.flatten()?.each{ file ->
            if(Util.isTestCode(file)) testFiles += file
            else productionFiles += file
        }
    }



}

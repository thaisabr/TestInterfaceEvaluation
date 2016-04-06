package taskAnalyser

import commitAnalyser.GitRepository
import commitAnalyser.GitRepositoryManager
import testCodeAnalyser.TestCodeAbstractParser
import testCodeAnalyser.groovy.GroovyTestCodeParser
import testCodeAnalyser.java.JavaTestCodeParser
import testCodeAnalyser.ruby.RubyTestCodeParser
import util.LanguageOption
import util.Util
import util.exception.InvalidLanguageException


abstract class Task {

    String id
    GitRepository gitRepository
    TestCodeAbstractParser testCodeParser

    Task(String rootDirectory, boolean isRemote, String id){
        this.id = id
        if(isRemote){
            this.gitRepository = GitRepositoryManager.getRepository(rootDirectory)
            configureTestCodeParser(gitRepository.localPath)
        }
        else{
            configureTestCodeParser(rootDirectory)
        }
    }

    def configureTestCodeParser(String path){
        switch (Util.CODE_LANGUAGE){
            case LanguageOption.JAVA:
                testCodeParser = new JavaTestCodeParser(path)
                break
            case LanguageOption.GROOVY:
                testCodeParser = new GroovyTestCodeParser(path)
                break
            case LanguageOption.RUBY:
                testCodeParser = new RubyTestCodeParser(path)
                break
            default: throw new InvalidLanguageException()
        }
    }

    String computeTextBasedInterface(){
        def text = ""
        def gherkinFiles = getAcceptanceTests()
        if(!gherkinFiles || gherkinFiles.empty) return text
        gherkinFiles?.each{ file ->
            text += "${file.feature.keyword}: ${file.feature.name}\n${file.feature.description}\n"

            if(file.feature.background) {
                text += "${file.feature.background.keyword}: ${file.feature.background.name}\n"
                file.feature.background.steps.each { step ->
                    text += "${step.keyword}: ${step.text}\n"
                }
            }
            file.changedScenarioDefinitions?.each{ definition ->
                text += "${definition.keyword}: ${definition.name}\n"
                definition.steps.each{ step ->
                    text += "${step.keyword}: ${step.text}\n"
                }
            }
        }
        text
    }

    abstract TaskInterface computeTestBasedInterface()

    abstract List<GherkinFile> getAcceptanceTests()

}

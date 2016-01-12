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

    abstract TaskInterface computeTestBasedInterface()

}

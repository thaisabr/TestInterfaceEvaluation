package taskAnalyser.task

import commitAnalyser.GitRepository
import testCodeAnalyser.TestCodeAbstractParser
import testCodeAnalyser.groovy.GroovyTestCodeParser
import testCodeAnalyser.java.JavaTestCodeParser
import testCodeAnalyser.ruby.RubyTestCodeParser
import util.LanguageOption
import util.Util
import util.exception.CloningRepositoryException
import util.exception.InvalidLanguageException


abstract class Task {

    String id
    GitRepository gitRepository
    TestCodeAbstractParser testCodeParser

    Task(String rootDirectory, String id) throws CloningRepositoryException {
        this.id = id
        this.gitRepository = GitRepository.getRepository(rootDirectory)
        configureTestCodeParser()
    }

    def configureTestCodeParser() {
        switch (Util.CODE_LANGUAGE) {
            case LanguageOption.JAVA:
                testCodeParser = new JavaTestCodeParser(gitRepository?.localPath)
                break
            case LanguageOption.GROOVY:
                testCodeParser = new GroovyTestCodeParser(gitRepository?.localPath)
                break
            case LanguageOption.RUBY:
                testCodeParser = new RubyTestCodeParser(gitRepository?.localPath)
                break
            default: throw new InvalidLanguageException()
        }
    }

    String computeTextBasedInterface() {
        def text = ""
        def gherkinFiles = getAcceptanceTests()
        if (!gherkinFiles || gherkinFiles.empty) return text
        gherkinFiles?.each { file ->
            text += file.text + "\n"
        }
        text.replaceAll("(?m)^\\s", "") //(?m) - regex multiline - to avoid lines that only contain blank space
    }

    abstract TaskInterface computeTestBasedInterface()

    abstract List<GherkinFile> getAcceptanceTests()

}

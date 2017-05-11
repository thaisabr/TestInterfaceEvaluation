package br.ufpe.cin.tan.analysis.task

import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.test.TestCodeAbstractParser
import br.ufpe.cin.tan.commit.GitRepository
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.test.groovy.GroovyTestCodeParser
import br.ufpe.cin.tan.test.java.JavaTestCodeParser
import br.ufpe.cin.tan.test.ruby.RubyTestCodeParser
import br.ufpe.cin.tan.util.LanguageOption
import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.exception.CloningRepositoryException
import br.ufpe.cin.tan.exception.InvalidLanguageException


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

    abstract ITest computeTestBasedInterface()

    abstract List<ChangedGherkinFile> getAcceptanceTests()

}

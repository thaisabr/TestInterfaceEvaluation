package br.ufpe.cin.tan.analysis.task

import br.ufpe.cin.tan.analysis.itask.ITest
import br.ufpe.cin.tan.commit.GitRepository
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.exception.CloningRepositoryException
import br.ufpe.cin.tan.exception.InvalidLanguageException
import br.ufpe.cin.tan.test.TestCodeAbstractAnalyser
import br.ufpe.cin.tan.test.groovy.GroovyTestCodeAnalyser
import br.ufpe.cin.tan.test.java.JavaTestCodeAnalyser
import br.ufpe.cin.tan.test.ruby.RubyTestCodeAnalyser
import br.ufpe.cin.tan.util.LanguageOption
import br.ufpe.cin.tan.util.Util
import groovy.util.logging.Slf4j

@Slf4j
abstract class Task {

    String id
    GitRepository gitRepository
    TestCodeAbstractAnalyser testCodeParser

    Task(String rootDirectory, String id) throws CloningRepositoryException {
        log.info "Configuring task '${id}'"
        this.id = id
        this.gitRepository = GitRepository.getRepository(rootDirectory)
        configureTestCodeParser()
    }

    def configureTestCodeParser() {
        switch (Util.CODE_LANGUAGE) {
            case LanguageOption.JAVA:
                testCodeParser = new JavaTestCodeAnalyser(gitRepository?.localPath, gitRepository.gherkinManager)
                break
            case LanguageOption.GROOVY:
                testCodeParser = new GroovyTestCodeAnalyser(gitRepository?.localPath, gitRepository.gherkinManager)
                break
            case LanguageOption.RUBY:
                testCodeParser = new RubyTestCodeAnalyser(gitRepository?.localPath, gitRepository.gherkinManager)
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

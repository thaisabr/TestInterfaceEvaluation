package br.ufpe.cin.tan.test.java

import br.ufpe.cin.tan.commit.change.gherkin.GherkinManager
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.commit.change.unit.ChangedUnitTestFile
import br.ufpe.cin.tan.test.FileToAnalyse
import br.ufpe.cin.tan.test.StepRegex
import br.ufpe.cin.tan.test.TestCodeAbstractAnalyser
import br.ufpe.cin.tan.test.TestCodeVisitorInterface

class JavaTestCodeAnalyser extends TestCodeAbstractAnalyser {

    JavaTestCodeAnalyser(String repositoryPath, GherkinManager gherkinManager) {
        super(repositoryPath, gherkinManager)
    }

    @Override
    void findAllPages(TestCodeVisitorInterface visitor) {

    }

    @Override
    List<StepRegex> doExtractStepsRegex(String path) {
        return null
    }

    @Override
    List<StepDefinition> doExtractStepDefinitions(String path, String content) {
        return null
    }

    @Override
    Set doExtractMethodDefinitions(String path) {
        return null
    }

    @Override
    TestCodeVisitorInterface parseStepBody(FileToAnalyse file) {
        return null
    }

    @Override
    def visitFile(Object file, TestCodeVisitorInterface visitor) {
        return null
    }

    @Override
    TestCodeVisitorInterface parseUnitBody(ChangedUnitTestFile file) {
        return null
    }

    @Override
    ChangedUnitTestFile doExtractUnitTest(String path, String content, List<Integer> changedLines) {
        return null
    }

    @Override
    String getClassForFile(String path) {
        return null
    }

    @Override
    boolean hasCompilationError(String path) {
        return false
    }
}

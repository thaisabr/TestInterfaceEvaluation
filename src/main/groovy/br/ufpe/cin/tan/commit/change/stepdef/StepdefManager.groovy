package br.ufpe.cin.tan.commit.change.stepdef

import gherkin.ParserException
import groovy.util.logging.Slf4j
import org.eclipse.jgit.revwalk.RevCommit
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.test.TestCodeAbstractParser

@Slf4j
class StepdefManager {

    TestCodeAbstractParser parser

    StepdefManager(TestCodeAbstractParser parser){
        this.parser = parser
    }

    List<StepDefinition> parseStepDefinitionFile(String filename, String content, String sha) {
        List<StepDefinition> stepDefinitions = null
        if (!content || content == "") {
            log.warn "Problem to parse step definition file '$filename'. Reason: The commit deleted it."
        } else {
            try {
                stepDefinitions = parser.doExtractStepDefinitions(filename, content)
            } catch (ParserException ex) {
                log.warn "Problem to parse step definition file '$filename' (commit $sha). ${ex.class}: ${ex.message}."
                log.warn content
            }
        }
        stepDefinitions
    }

    /***
     * Identifies step definitions at added step definition files by the first commit of the repository.
     * It is used only when dealing with done tasks.
     */
    ChangedStepdefFile extractStepDefinitionAdds(RevCommit commit, String content, String path) {
        ChangedStepdefFile changedStepDefFile = null
        def newStepDefinitions = parseStepDefinitionFile(path, content, commit.name)

        if (newStepDefinitions && !newStepDefinitions.isEmpty()) {
            changedStepDefFile = new ChangedStepdefFile(path: path, changedStepDefinitions: newStepDefinitions)
        }
        changedStepDefFile
    }

}

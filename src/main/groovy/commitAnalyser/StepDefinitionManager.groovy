package commitAnalyser

import gherkin.ParserException
import groovy.util.logging.Slf4j
import org.eclipse.jgit.revwalk.RevCommit
import taskAnalyser.task.StepDefinition
import taskAnalyser.task.StepDefinitionFile
import testCodeAnalyser.TestCodeAbstractParser

@Slf4j
class StepDefinitionManager {

    static List<StepDefinition> parseStepDefinitionFile(String filename, String content, String sha,
                                                        TestCodeAbstractParser parser) {
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
    static StepDefinitionFile extractStepDefinitionAdds(RevCommit commit, String content, String path,
                                                        TestCodeAbstractParser parser) {
        StepDefinitionFile changedStepDefFile = null
        def newStepDefinitions = parseStepDefinitionFile(path, content, commit.name, parser)

        if (newStepDefinitions && !newStepDefinitions.isEmpty()) {
            changedStepDefFile = new StepDefinitionFile(path: path, changedStepDefinitions: newStepDefinitions)
        }
        changedStepDefFile
    }

}

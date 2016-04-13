package commitAnalyser

import gherkin.ParserException
import org.eclipse.jgit.revwalk.RevCommit
import taskAnalyser.StepDefinition
import taskAnalyser.StepDefinitionFile
import testCodeAnalyser.TestCodeAbstractParser


class StepDefinitionManager {

    static List<StepDefinition> parseStepDefinitionFile(String filename, String content, String sha,
                                                           TestCodeAbstractParser parser){
        List<StepDefinition> stepDefinitions = null
        if(!content || content==""){
            GitRepository.log.warn "Problem to parse step definition file '$filename'. Reason: The commit deleted it."
        }
        else{
            try{
                stepDefinitions = parser.doExtractStepDefinitions(filename, content)
            } catch(ParserException ex){
                GitRepository.log.warn "Problem to parse step definition file '$filename' (commit $sha). ${ex.class}: ${ex.message}."
                GitRepository.log.warn content
            }
        }
        stepDefinitions
    }

    /***
     * Identifies step definitions at added step definition files by the first commit of the repository.
     * It is used only when dealing with done tasks.
     */
    static StepDefinitionFile extractStepDefinitionAdds(RevCommit commit, String content, String path,
                                                               TestCodeAbstractParser parser){
        StepDefinitionFile changedStepDefFile = null
        def newStepDefinitions = parseStepDefinitionFile(path, content, commit.name, parser)

        if(newStepDefinitions && !newStepDefinitions.isEmpty()){
            changedStepDefFile = new StepDefinitionFile(commitHash:commit.name, path:path,
                    changedStepDefinitions:newStepDefinitions)
        }
        changedStepDefFile
    }

}

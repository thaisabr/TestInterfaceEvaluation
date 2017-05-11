package br.ufpe.cin.tan.commit.change.stepdef

import br.ufpe.cin.tan.commit.change.CodeChange
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition

class ChangedStepdefFile implements CodeChange {

    String path
    List<StepDefinition> changedStepDefinitions = []

    @Override
    String toString() {
        def text = "Step file: ${path}\nChanged step definitions:\n"
        changedStepDefinitions.each { definition ->
            text += definition.toString() + "\n"
        }
        return text
    }

}

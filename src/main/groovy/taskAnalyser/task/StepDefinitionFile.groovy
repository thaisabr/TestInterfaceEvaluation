package taskAnalyser.task

import commitAnalyser.CodeChange

class StepDefinitionFile implements CodeChange {

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

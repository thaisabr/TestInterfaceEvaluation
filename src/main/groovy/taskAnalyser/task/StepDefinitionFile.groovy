package taskAnalyser.task


class StepDefinitionFile {

    String commitHash
    String path
    List<StepDefinition> changedStepDefinitions  = []

    @Override
    String toString() {
        def text = "Step file: ${path}\nChanged step definitions:\n"
        changedStepDefinitions.each{ definition ->
            text += definition.toString()+"\n"
        }
        return text
    }

}

package testCodeAnalyser


class StepRegex {

    String path
    String value
    int line
    int end

    @Override
    String toString() {
        "File($line-$end): $path :: $value"
    }

}

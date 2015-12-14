package testCodeAnalyser


class StepRegex {

    String path
    String value
    int line

    @Override
    String toString() {
        "File($line): $path :: $value"
    }

}

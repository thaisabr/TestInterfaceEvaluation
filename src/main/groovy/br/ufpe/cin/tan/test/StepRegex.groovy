package br.ufpe.cin.tan.test


class StepRegex {

    String path
    String value
    int line

    @Override
    String toString() {
        "File $path ($line): $value"
    }

}

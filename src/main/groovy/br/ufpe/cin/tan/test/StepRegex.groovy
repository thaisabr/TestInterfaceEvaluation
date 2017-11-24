package br.ufpe.cin.tan.test


class StepRegex {

    String keyword //it is used only when we are dealing with generic steps (asterisk keyword)
    String path
    String value
    int line

    @Override
    String toString() {
        "File $path ($line): $value"
    }

    void setKeyword(String keyword) {
        if (keyword.endsWith(" ")) this.keyword = keyword
        else this.keyword = keyword + " "
    }

}

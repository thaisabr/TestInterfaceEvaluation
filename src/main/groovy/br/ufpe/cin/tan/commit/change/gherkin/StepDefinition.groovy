package br.ufpe.cin.tan.commit.change.gherkin


class StepDefinition {

    String keyword
    String path
    String value
    String regex
    int line
    int end
    List<String> body

    @Override
    String toString() {
        def text = "Step ($line-$end): $value\n"
        body?.each { text += it + "\n" }
        return text
    }

    int size() {
        return end - line
    }

    void setKeyword(String keyword) {
        if (keyword.endsWith(" ")) this.keyword = keyword
        else this.keyword = keyword + " "
    }

}

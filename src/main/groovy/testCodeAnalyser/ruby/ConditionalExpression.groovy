package testCodeAnalyser.ruby


class ConditionalExpression {

    int line
    String expression
    String result
    boolean resultIsMethod

    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        ConditionalExpression that = (ConditionalExpression) o
        if (line != that.line) return false
        return true
    }

    @Override
    int hashCode() {
        return line
    }

    @Override
    String toString() {
        "[line:$line, exp:$expression, result:$result, isMethod:$resultIsMethod]"
    }
}

package br.ufpe.cin.tan.test.ruby

class MethodBody {

    List<String> lines

    MethodBody(List<String> lines) {
        this.lines = lines
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        MethodBody that = (MethodBody) o

        if (lines.size() != that.lines.size()) return false

        boolean isEquals = true
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) != that.lines.get(i)) {
                isEquals = false
                break
            }
        }
        return isEquals
    }

    int hashCode() {
        return (lines != null ? lines.hashCode() : 0)
    }
}

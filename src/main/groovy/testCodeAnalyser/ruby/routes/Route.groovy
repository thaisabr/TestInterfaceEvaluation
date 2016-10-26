package testCodeAnalyser.ruby.routes

class Route {

    String name
    String file
    String value
    String arg

    public void setName(String name){
        this.name = name.replaceAll("/:(\\w|\\.|:|#|&|=|\\+|\\?)*/", "/.*/").replaceAll("/:(\\w|\\.|:|#|&|=|\\+|\\?)*[^/()]", "/.*")
    }

    @Override
    String toString() {
        return "[name: '" + name + '\'' +
                ", file: '" + file + '\'' +
                ", value: '" + value + '\'' +
                ", arg: '" + arg + '\'' + ']'
    }

    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        Route route = (Route) o

        if (arg != route.arg) return false
        if (file != route.file) return false
        if (name != route.name) return false
        if (value != route.value) return false

        return true
    }

    @Override
    int hashCode() {
        int result
        result = name.hashCode()
        result = 31 * result + file.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + arg.hashCode()
        return result
    }

}

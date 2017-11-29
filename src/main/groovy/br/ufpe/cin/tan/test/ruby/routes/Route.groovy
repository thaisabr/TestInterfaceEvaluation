package br.ufpe.cin.tan.test.ruby.routes

import br.ufpe.cin.tan.util.ruby.RequestType

class Route {

    String name
    String file
    String value
    String arg
    String type = RequestType.GET

    void setName(String name) {
        this.name = name.replaceAll("/:(\\w|\\.|:|#|&|=|\\+|\\?)*/", "/.*/")
                .replaceAll("/:(\\w|\\.|:|#|&|=|\\+|\\?)*[^/()]", "/.*").replaceAll("/", "_")
    }

    @Override
    String toString() {
        return "[name: '" + name + '\'' +
                ", file: '" + file + '\'' +
                ", value: '" + value + '\'' +
                ", arg: '" + arg + '\'' +
                ", type: '" + type + '\'' + ']'
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
        if (type != route.type) return false

        return true
    }

    @Override
    int hashCode() {
        int result
        result = name.hashCode()
        result = 31 * result + file.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + arg.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

}

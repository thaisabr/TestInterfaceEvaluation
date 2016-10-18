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
    public String toString() {
        return "[name: '" + name + '\'' +
                ", file: '" + file + '\'' +
                ", value: '" + value + '\'' +
                ", arg: '" + arg + '\'' + ']'
    }
}

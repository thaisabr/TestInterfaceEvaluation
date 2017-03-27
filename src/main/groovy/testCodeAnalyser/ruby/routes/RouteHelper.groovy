package testCodeAnalyser.ruby.routes


class RouteHelper {

    static routeIsFile(String name) {
        name.contains(".") && !name.contains("*")
    }

    static routeIsAction(String name) {
        name.contains("#")
    }




}

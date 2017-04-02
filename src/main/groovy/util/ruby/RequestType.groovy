package util.ruby


enum RequestType {

    GET("get"), POST("post"), PUT("put"), PATCH("patch"), DELETE("delete")

    String name

    RequestType(String name) {
        this.name = name
    }

    static RequestType valueOfName(String name) {
        values().find { it.name == name }
    }

}

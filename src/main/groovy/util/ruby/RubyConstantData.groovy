package util.ruby


interface RubyConstantData {

    String ROUTES_FILE = File.separator + "config" + File.separator + "routes.rb"
    String ROUTES_ID = "ROUTES"
    String ROUTE_SUFIX = "_path"
    List<String> REQUEST_TYPES = ["get", "post", "put", "patch", "delete"]
    List<String> EXCLUDED_PATH_METHODS = ["current_path", "recognize_path", "assert_routing", "assert_recognizes", " assert_response"]
    String ERB_ANALYSER_FILE = "erb${File.separator}Grab_controller_from_erb_file.rb"

}

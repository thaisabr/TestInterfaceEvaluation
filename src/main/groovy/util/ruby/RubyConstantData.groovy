package util.ruby


abstract class RubyConstantData {

    public static String ROUTES_FILE = File.separator + "config" + File.separator + "routes.rb"
    public static String ROUTES_ID = "ROUTES"
    public static String ROUTE_PATH_SUFIX = "_path"
    public static String ROUTE_URL_SUFIX = "_url"
    public static List<String> EXCLUDED_PATH_METHODS = ["current_path", "recognize_path", "assert_routing", "assert_recognizes", " assert_response"]
    public static String VIEW_ANALYSER_FILE = "view_analyser.rb"
    public static String GEM_FILE = "Gemfile"
    public static List<String> GEMS_OF_INTEREST = ["rails", "cucumber-rails", "rspec-rails", "simplecov", "coveralls", "factory_girl"]

}

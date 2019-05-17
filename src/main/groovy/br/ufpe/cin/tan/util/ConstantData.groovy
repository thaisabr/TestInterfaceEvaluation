package br.ufpe.cin.tan.util

import gherkin.GherkinDialectProvider


abstract class ConstantData {

    public static final String PROPERTIES_FILE_NAME = "configuration.properties"
    public static final String PROP_TASK_FILE = "spgroup.task.file.path"
    public static final String PROP_TASK_MAX_SIZE = "spgroup.task.max.size"
    public static final String PROP_CODE_LANGUAGE = "spgroup.language"
    public static final String PROP_GHERKIN = "spgroup.gherkin.files.relative.path"
    public static final String PROP_STEPS = "spgroup.steps.files.relative.path"
    public static final String PROP_UNIT_TEST = "spgroup.unit.files.relative.path"
    public static final String PROP_PRODUCTION = "spgroup.production.files.relative.path"
    public static final String PROP_FRAMEWORK = "spgroup.framework.path"
    public static final String PROP_GEMS = "spgroup.gems.path"
    public static final String PROP_GEM_INFLECTOR = "spgroup.gems.activesupport-inflector.folder"
    public static final String PROP_GEM_I18N = "spgroup.gems.i18n.folder"
    public static final String PROP_GEM_PARSER = "spgroup.gems.parser"
    public static final String PROP_GEM_AST = "spgroup.gems.ast"
    public static final String PROP_LIB = "spgroup.lib.path"

    public static final String DEFAULT_TASKS_FOLDER = "tasks"
    public static final int DEFAULT_TASK_SIZE = 5000 //that is, we want to disable task size filter
    public static final String DEFAULT_REPOSITORY_FOLDER = "spg_repos"
    public static final String DEFAULT_LANGUAGE = "ruby"
    public static final String DEFAULT_GHERKIN_FOLDER = "features"
    public static final String DEFAULT_STEPS_FOLDER = "features/step_definitions"
    public static final String DEFAULT_UNITY_FOLDER = "spec"
    public static final String DEFAULT_PRODUCTION_FOLDER = "app"

    public static final String DEFAULT_GEM_INFLECTOR = "activesupport-inflector-0.1.0"
    public static final String DEFAULT_GEM_I18N_FOLDER = "i18n-0.7.0"
    public static final String DEFAULT_GEM_PARSER_FOLDER = "parser-2.3.1.4"
    public static final String DEFAULT_GEM_AST_FOLDER = "ast-2.3.0"

    public static String DEFAULT_EVALUATION_FOLDER = "output"
    public static final String DEFAULT_TEXT_FOLDER = "text"
    public static final String SELECTED_TASKS_BY_CONFIGS_FOLDER = "selected"
    public static
    final String DEFAULT_EVALUATION_FILE = "$DEFAULT_EVALUATION_FOLDER${File.separator}evaluation_result.csv"
    public static final String ORGANIZED_FILE_SUFIX = "-org.csv"
    public static final String FILTERED_FILE_SUFIX = "-filtered.csv"
    public static final String SIMILARITY_FILE_SUFIX = "-similarity.csv"
    public static final String SIMILARITY_ORGANIZED_FILE_SUFIX = "-similarity-org.csv"
    public static final String CONTROLLER_FILE_SUFIX = "-controller.csv"
    public static final String CONTROLLER_ORGANIZED_FILE_SUFIX = "-controller-org.csv"
    public static final String TEST_EXECUTION_FILE_SUFIX = "-tests.csv"
    public static final String RELEVANT_TASKS_FILE_SUFIX = "-relevant.csv"
    public static final String RELEVANT_TASKS_DETAILS_FILE_SUFIX = "-relevant-detailed.csv"
    public static final String VALID_TASKS_DETAILS_FILE_SUFIX = "-valid-detailed.csv"
    public static final String VALID_TASKS_FILE_SUFIX = "-valid.csv"
    public static final String INVALID_TASKS_FILE_SUFIX = "-invalid.csv"
    public static final String RANDOM_RESULTS_FILE_SUFIX = "-random.csv"

    public static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L"
    public static final String HTML_EXTENSION = ".html"
    public static final String ERB_EXTENSION = ".erb"
    public static final String HTML_ERB_EXTENSION = HTML_EXTENSION + ERB_EXTENSION
    public static final String HAML_EXTENSION = ".haml"
    public static final String HTML_HAML_EXTENSION = HTML_EXTENSION + HAML_EXTENSION
    public static final String MOBILE_HAML_EXTENSION = ".mobile" + HAML_EXTENSION
    public static final String SLIM_EXTENSION = ".slim"
    public static final String HTML_SLIM_EXTENSION = HTML_EXTENSION + SLIM_EXTENSION
    public static final String CSV_FILE_EXTENSION = ".csv"
    public static final String FEATURE_EXTENSION = ".feature"
    public static final String JAR_EXTENSION = ".jar"
    public static final String GROOVY_EXTENSION = ".groovy"
    public static final String JAVA_EXTENSION = ".java"
    public static final String RUBY_EXTENSION = ".rb"
    public static final String GIT_EXTENSION = ".git"
    public static final String TEXT_EXTENSION = ".txt"
    public static final String GITHUB_URL = "https://github.com/"

    public static
    final List<String> STEP_KEYWORDS = new GherkinDialectProvider().defaultDialect.stepKeywords.unique()*.trim()
    public static
    final List<String> STEP_KEYWORDS_PT = new GherkinDialectProvider().getDialect("pt", null).stepKeywords.unique()*.trim()
    public static
    final List<String> STEP_KEYWORDS_DE = new GherkinDialectProvider().getDialect("de", null).stepKeywords.unique()*.trim()
    public static final List<String> ALL_STEP_KEYWORDS = STEP_KEYWORDS + STEP_KEYWORDS_PT + STEP_KEYWORDS_DE
    public static final String GIVEN_STEP_EN = new GherkinDialectProvider().defaultDialect.givenKeywords.last()
    public static final String WHEN_STEP_EN = new GherkinDialectProvider().defaultDialect.whenKeywords.last()
    public static final String THEN_STEP_EN = new GherkinDialectProvider().defaultDialect.thenKeywords.last()
    public static final String AND_STEP_EN = new GherkinDialectProvider().defaultDialect.andKeywords.last()
    public static final String BUT_STEP_EN = new GherkinDialectProvider().defaultDialect.butKeywords.last()
    public static final String GENERIC_STEP = "* "

    public static final String INFLECTOR_FILE = "inflector.rb"

    public static final String PROP_VIEW_ANALYSIS = "spgroup.itest.views"
    public static final boolean DEFAULT_VIEW_ANALYSIS = true
    public static final String DEFAULT_VIEW_ANALYSIS_ERROR_FOLDER = "error"

    public static final String PROP_CONTROLLER_FILTER = "spgroup.itest.filter.controller"
    public static final boolean DEFAULT_CONTROLLER_FILTER = false

    public static final String PROP_WHEN_FILTER = "spgroup.itest.filter.when"
    public static final boolean DEFAULT_WHEN_FILTER = false

    public static final String PROP_VIEW_FILTER = "spgroup.itest.filter.view"
    public static final boolean DEFAULT_VIEW_FILTER = false

    public static final String PROP_RESTRICT_GHERKIN_CHANGES = "spgroup.itest.filter.gherkinAdds"
    public static final boolean DEFAULT_RESTRICT_GHERKIN_CHANGES = false

    public static final String PROP_COVERAGE_GEMS = "spgroup.gems.coverage"

    public static final String PROP_RUN_ALL_CONFIGURATIONS = "spgroup.itest.all.configurations"
    public static final boolean DEFAULT_RUN_ALL_CONFIGURATIONS = false

    public static final String PROP_SIMILARITY = "spgroup.itest.similarity"
    public static final boolean DEFAULT_SIMILARITY = false

    public static final int DEFAULT_TASK_LIMIT = 10000
}

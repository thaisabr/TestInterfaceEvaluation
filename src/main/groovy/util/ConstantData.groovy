package util

import gherkin.GherkinDialectProvider


interface ConstantData {

    String PROPERTIES_FILE_NAME = "configuration.properties"
    String PROP_TASK_FILE = "spgroup.task.file.path"
    String PROP_REPOSITORY = "spgroup.task.repositories.path"
    String PROP_CODE_LANGUAGE = "spgroup.language"
    String PROP_GHERKIN = "spgroup.gherkin.files.relative.path"
    String PROP_STEPS = "spgroup.steps.files.relative.path"
    String PROP_UNIT_TEST = "spgroup.unit.files.relative.path"
    String PROP_PRODUCTION = "spgroup.production.files.relative.path"
    String PROP_FRAMEWORK = "spgroup.framework.path"
    String PROP_GEMS = "spgroup.gems.path"
    String PROP_GEM_INFLECTOR = "spgroup.gems.activesupport-inflector.folder"
    String PROP_GEM_I18N = "spgroup.gems.i18n.folder"
    String PROP_GEM_PARSER = "spgroup.gems.parser"
    String PROP_GEM_AST = "spgroup.gems.ast"

    String DEFAULT_TASKS_FOLDER = "tasks"
    String DEFAULT_TASK_FILE = "tasks.csv"
    String DEFAULT_REPOSITORY_FOLDER = "repositories"
    String DEFAULT_LANGUAGE = "ruby"
    String DEFAULT_GHERKIN_FOLDER = "features"
    String DEFAULT_STEPS_FOLDER = "features/step_definitions"
    String DEFAULT_UNITY_FOLDER = "spec"
    String DEFAULT_PRODUCTION_FOLDER = "app"

    String DEFAULT_GEM_INFLECTOR = "activesupport-inflector-0.1.0"
    String DEFAULT_GEM_I18N_FOLDER = "i18n-0.7.0"
    String DEFAULT_GEM_PARSER_FOLDER = "parser-2.3.1.4"
    String DEFAULT_GEM_AST_FOLDER = "ast-2.3.0"

    String DEFAULT_EVALUATION_FOLDER = "output"
    String DEFAULT_EVALUATION_FILE = "$DEFAULT_EVALUATION_FOLDER${File.separator}evaluation_result.csv"
    String ORGANIZED_FILE_SUFIX = "-organized.csv"
    String FILTERED_FILE_SUFIX = "-filtered.csv"
    String SIMILARITY_FILE_SUFIX = "-similarity.csv"
    String SIMILARITY_ORGANIZED_FILE_SUFIX = "-similarityOrganized.csv"

    String NON_PRIMITIVE_ARRAY_PREFIX = "[L"
    String CSV_FILE_EXTENSION = ".csv"
    String FEATURE_FILENAME_EXTENSION = ".feature"
    String JAR_FILENAME_EXTENSION = ".jar"
    String GROOVY_EXTENSION = ".groovy"
    String JAVA_EXTENSION = ".java"
    String RUBY_EXTENSION = ".rb"
    String GIT_EXTENSION = ".git"
    String GITHUB_URL = "https://github.com/"

    List<String> STEP_KEYWORDS = new GherkinDialectProvider().defaultDialect.stepKeywords.unique()*.trim()
    List<String> STEP_KEYWORDS_PT = new GherkinDialectProvider().getDialect("pt", null).stepKeywords.unique()*.trim()
    List<String> STEP_KEYWORDS_DE = new GherkinDialectProvider().getDialect("de", null).stepKeywords.unique()*.trim()

    String INFLECTOR_FILE = "inflector.rb"
}

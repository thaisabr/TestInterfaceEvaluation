package util

import gherkin.GherkinDialectProvider


interface ConstantData {

    String PROPERTIES_FILE_NAME = "configuration.properties"

    String DEFAULT_TASKS_FOLDER = "tasks"
    String DEFAULT_TASK_FILE = "tasks.csv"
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

}

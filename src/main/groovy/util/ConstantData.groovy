package util

import gherkin.GherkinDialectProvider


class ConstantData {

    public static final String PROPERTIES_FILE_NAME = "configuration.properties"

    public static final String DEFAULT_TASKS_FOLDER = "tasks"
    public static final String DEFAULT_TASK_FILE = "tasks.csv"
    public static final String DEFAULT_EVALUATION_FOLDER = "output"
    public static final String DEFAULT_EVALUATION_FILE = "$DEFAULT_EVALUATION_FOLDER${File.separator}evaluation_result.csv"
    public static final String ORGANIZED_FILE_SUFIX = "-organized.csv"
    public static final String FILTERED_FILE_SUFIX = "-filtered.csv"
    public static final String SIMILARITY_FILE_SUFIX = "-similarity.csv"
    public static final String SIMILARITY_ORGANIZED_FILE_SUFIX = "-similarityOrganized.csv"

    public static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L"
    public static final String CSV_FILE_EXTENSION = ".csv"
    public static final String FEATURE_FILENAME_EXTENSION = ".feature"
    public static final String JAR_FILENAME_EXTENSION = ".jar"
    public static final String GROOVY_EXTENSION = ".groovy"
    public static final String JAVA_EXTENSION = ".java"
    public static final String RUBY_EXTENSION = ".rb"
    public static final String GIT_EXTENSION = ".git"
    public static final String GITHUB_URL = "https://github.com/"

    public static final List<String> STEP_KEYWORDS = new GherkinDialectProvider().defaultDialect.stepKeywords.unique()*.trim()
    public static final List<String> STEP_KEYWORDS_PT = new GherkinDialectProvider().getDialect("pt",null).stepKeywords.unique()*.trim()
    public static final List<String> STEP_KEYWORDS_DE = new GherkinDialectProvider().getDialect("de",null).stepKeywords.unique()*.trim()


}

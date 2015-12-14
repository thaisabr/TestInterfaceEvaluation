package util.exception


class InvalidLanguageException extends Exception {

    InvalidLanguageException() {
        super("Invalid language. Please, review the configuration properties file.");
    }
}

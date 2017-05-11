package br.ufpe.cin.tan.exception


class InvalidLanguageException extends Exception {

    InvalidLanguageException() {
        super("Invalid language. Please, review the configuration properties file.");
    }
}

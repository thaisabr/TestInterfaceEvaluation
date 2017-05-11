package br.ufpe.cin.tan.exception


class CloningRepositoryException extends Exception {

    CloningRepositoryException(String details) {
        super("Error while cloning Git repository. Details: $details.");
    }
}

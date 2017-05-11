package br.ufpe.cin.tan.commit.change

/***
 * Represents a code change in a production file by a commit.
 */
class ChangedProdFile implements CodeChange {

    String path
    def type //add file, remove file, change file, copy file or renaming file
    List<Integer> lines

    @Override
    String toString() {
        "(Production change) path: $path; type: $type; lines: $lines"
    }

}

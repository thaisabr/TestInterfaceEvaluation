package commitAnalyser

/***
 * Represents a code change in a core file by a commit.
 */
class CoreChange implements CodeChange {

    String path
    def type //add file, remove file, change file, copy file or renaming file
    List<Integer> lines

    @Override
    String toString() {
        "(Core change) path: $path; type: $type; lines: $lines"
    }

}

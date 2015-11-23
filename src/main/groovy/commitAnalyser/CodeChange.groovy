package commitAnalyser


class CodeChange {

    String filename
    def type
    List<Integer> lines //if null, it was not computed yet

}

package testCodeParser


class StepDefinition {

    String file
    int line
    int lastLine
    String type
    String regex

    @Override
    public String toString(){
        "[file: $file, line: $line, lastLine: $lastLine, type: $type, regex: $regex]"
    }

}

package gherkinParser

class Scenario {

    String name
    int line
    String file
    List<Match> testcode //todo

    @Override
    public String toString(){
        //"name: $name, line:$line, file:$file, testcode:$testcode"
        "name: $name, line:$line, file:$file"
    }

}

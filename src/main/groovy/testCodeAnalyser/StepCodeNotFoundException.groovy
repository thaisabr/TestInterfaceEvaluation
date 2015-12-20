package testCodeAnalyser


class StepCodeNotFoundException extends Exception {

    StepCodeNotFoundException(String text, String path, int line){
        super("Step code was not found: $text; $path ($line)")
    }

}

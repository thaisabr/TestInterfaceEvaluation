package util


enum LanguageOption {

    RUBY(".rb"), GROOVY(".groovy"), JAVA(".java")

    String extension

    LanguageOption(String ext){
        extension = ext
    }
}

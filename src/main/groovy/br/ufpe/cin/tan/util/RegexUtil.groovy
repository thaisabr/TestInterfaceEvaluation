package br.ufpe.cin.tan.util


class RegexUtil {

    public static final FILE_SEPARATOR_REGEX = /(\\|\/)/
    public static final NEW_LINE_REGEX = /\r\n|\n/
    public static final GHERKIN_COMMENTED_LINE_REGEX = /^\s*#.*/
    public static final LIB_PATH = /(.+\\lib\\ruby\\gems\\.+)|(.+\/lib\/gems\/.+)/
}

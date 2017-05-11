package br.ufpe.cin.tan.analysis

import br.ufpe.cin.tan.util.RegexUtil
import br.ufpe.cin.tan.util.Util


class AnalysisResult {

    List<AnalysedTask> tasks = []
    String file
    String url
    int stepCounter
    int gherkinCounter
    int testsCounter

    void setUrl(String url){
        this.url = url - Util.REPOSITORY_FOLDER_PATH
        if(this.url[0] ==~ RegexUtil.FILE_SEPARATOR_REGEX) this.url = this.url.substring(1)
    }

}

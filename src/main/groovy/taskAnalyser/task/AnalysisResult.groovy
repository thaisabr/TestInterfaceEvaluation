package taskAnalyser.task

import util.RegexUtil
import util.Util


class AnalysisResult {

    List<AnalysedTask> validTasks = []
    String file
    String url
    int stepCounter
    int gherkinCounter
    int testsCounter
    int allTasks

    void setUrl(String url){
        this.url = url - Util.REPOSITORY_FOLDER_PATH
        if(this.url[0] ==~ RegexUtil.FILE_SEPARATOR_REGEX) this.url = this.url.substring(1)
    }

}

package taskAnalyser.task

import commitAnalyser.Commit
import groovy.util.logging.Slf4j
import util.RegexUtil
import util.exception.CloningRepositoryException
import util.ruby.RubyUtil

import java.util.regex.Matcher

@Slf4j
class RailsTask extends DoneTask {

    RailsTask(String repositoryIndex, String repositoryUrl, String id, List<Commit> commits) throws CloningRepositoryException {
        super(repositoryIndex, repositoryUrl, id, commits)
    }

    def routeFileChanged(){
        def identity = ""
        def taskChanged = false
        def otherTaskChanged = false
        def routesFileName = RubyUtil.ROUTES_FILE.substring(1).replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/"))

        def files = commits*.coreChanges*.path?.flatten()?.unique()?.sort()
        def find = files.find{ it.endsWith(RubyUtil.ROUTES_FILE.substring(1)) }
        if(find) {
            taskChanged = true
            log.info "Task ${id} changed routes file"
        }

        def allCommitsOfRoutesFile = gitRepository.identifyCommitsInFile(routesFileName)
        def result = allCommitsOfRoutesFile.findAll{ it.commitTime>commits.first().date && it.commitTime<commits.last().date }
        if(!result.empty) {
            otherTaskChanged = true
            identity = result*.name.join(",")
            log.info "Other task changed routes file before task ${id} be finished"
        }

        [changedByTask:taskChanged, changedByOtherTask:otherTaskChanged, commits:identity]
    }
}

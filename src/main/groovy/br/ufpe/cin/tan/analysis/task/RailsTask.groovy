package br.ufpe.cin.tan.analysis.task

import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.util.RegexUtil
import br.ufpe.cin.tan.exception.CloningRepositoryException
import br.ufpe.cin.tan.util.ruby.RubyConstantData

import java.util.regex.Matcher

@Slf4j
class RailsTask extends DoneTask {

    RailsTask(String repositoryUrl, String id, List<String> hashes) throws CloningRepositoryException {
        super(repositoryUrl, id, hashes)
    }

    def routeFileChanged() {
        def identity = ""
        def taskChanged = false
        def otherTaskChanged = false
        def routesFileName = RubyConstantData.ROUTES_FILE.substring(1).replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/"))

        def files = commits*.coreChanges*.path?.flatten()?.unique()?.sort()
        def find = files.find { it.endsWith(RubyConstantData.ROUTES_FILE.substring(1)) }
        if (find) {
            taskChanged = true
            log.info "Task ${id} changed routes file"
        }

        def allCommitsOfRoutesFile = gitRepository.identifyCommitsInFile(routesFileName)
        def result = allCommitsOfRoutesFile.findAll {
            it.commitTime > commits.first().date && it.commitTime < commits.last().date
        }
        if (!result.empty) {
            otherTaskChanged = true
            identity = result*.name.join(",")
            log.info "Other task changed routes file before task ${id} be finished"
        }

        [changedByTask: taskChanged, changedByOtherTask: otherTaskChanged, commits: identity]
    }
}

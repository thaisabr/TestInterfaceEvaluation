package br.ufpe.cin.tan.commit

import br.ufpe.cin.tan.commit.change.ChangedProdFile
import br.ufpe.cin.tan.commit.change.CodeChange
import br.ufpe.cin.tan.commit.change.RenamingChange
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile
import br.ufpe.cin.tan.commit.change.gherkin.GherkinManager
import br.ufpe.cin.tan.commit.change.gherkin.StepDefinition
import br.ufpe.cin.tan.commit.change.stepdef.ChangedStepdefFile
import br.ufpe.cin.tan.commit.change.stepdef.StepdefManager
import br.ufpe.cin.tan.commit.change.unit.ChangedUnitTestFile
import br.ufpe.cin.tan.commit.change.unit.UnitTestManager
import br.ufpe.cin.tan.exception.CloningRepositoryException
import br.ufpe.cin.tan.test.TestCodeAbstractAnalyser
import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.RegexUtil
import br.ufpe.cin.tan.util.Util
import gherkin.ast.Background
import gherkin.ast.ScenarioDefinition
import groovy.util.logging.Slf4j
import org.eclipse.jgit.api.BlameCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.blame.BlameResult
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter

import java.util.regex.Matcher

/***
 * Represents a git repository to be downloaded for analysis purpose.
 */
@Slf4j
class GitRepository {

    GherkinManager gherkinManager
    static List<GitRepository> repositories = []
    String reposDirectory
    String url
    String name
    String localPath
    String lastCommit //used only to reset the repository for the original state after checkout command
    Set removedSteps

    List<RevCommit> identifyCommitsInFile(String filename) {
        def git = Git.open(new File(localPath))
        List<RevCommit> logs = git?.log()?.all()?.addPath(filename)?.call()?.sort { it.commitTime }
        git.close()
        return logs
    }

    Iterable<RevCommit> searchAllRevCommits() {
        def git = Git.open(new File(localPath))
        Iterable<RevCommit> logs = git?.log()?.all()?.call()?.sort { it.commitTime }
        git.close()
        logs
    }

    Iterable<RevCommit> searchAllRevCommitsBySha(String... hash) {
        def commits = searchAllRevCommits()
        def logs = []
        hash.each { h ->
            def c = commits?.find { it.name == h }
            if (c) logs += c
        }
        logs
    }

    /***
     * Searches commits from a Git repository by hash value.
     *
     * @param hash a set of hash value
     * @return a list of commits that satisfy the search criteria.
     */
    List<Commit> searchCommitsBySha(TestCodeAbstractAnalyser parser, String... hash) {
        def logs = searchAllRevCommitsBySha(hash)
        extractCommitsFromLogs(logs, parser)
    }

    /***
     * Checkouts a specific version of git repository.
     * @param sha the commit's identification.
     */
    def reset(String sha) {
        /*def git = Git.open(new File(localPath))
        git.checkout().setName(sha).setStartPoint(sha).call()
        git.close()*/
        ProcessBuilder builder = new ProcessBuilder("git", "checkout", "-f", sha)
        builder.directory(new File(localPath))
        Process process = builder.start()
        process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
    }

    /***
     * Checkouts the last version of git repository.
     */
    def reset() {
        /*def git = Git.open(new File(localPath))
        git.checkout().setName(lastCommit).setStartPoint(lastCommit).call()
        git.close()*/
        ProcessBuilder builder = new ProcessBuilder("git", "checkout", "-f", lastCommit)
        builder.directory(new File(localPath))
        Process process = builder.start()
        process.waitFor()
        process.inputStream.readLines()
        process.inputStream.close()
    }

    def parseGherkinFile(String filename, String sha) {
        RevCommit revCommit = searchAllRevCommitsBySha(sha)?.first()
        def content = extractFileContent(revCommit, filename)
        def feature = gherkinManager.parseGherkinFile(content, filename, revCommit.name)
        [feature: feature, content: content]
    }

    def parseGherkinFile(String filename) {
        def content = new File(filename).text
        def feature = gherkinManager.parseGherkinFile(content, filename, lastCommit)
        [feature: feature, content: content]
    }

    static GitRepository getRepository(String url) throws CloningRepositoryException {
        def repository = repositories.find { ((it.url - ConstantData.GIT_EXTENSION) == url) }
        if (repository) repository.gherkinManager = new GherkinManager()
        else {
            repository = new GitRepository(url)
            repositories += repository
        }
        return repository
    }

    private configureReposDirectory(){
        String userHome = System.getProperty("user.home");
        this.reposDirectory = userHome + File.separator + Util.REPOSITORY_FOLDER_PATH
    }

    private GitRepository(String path) throws CloningRepositoryException {
        this.gherkinManager = new GherkinManager()
        this.removedSteps = [] as Set

        if (path.startsWith("http")) {
            if (path.endsWith(ConstantData.GIT_EXTENSION)) this.url = path
            else this.url = path + ConstantData.GIT_EXTENSION
            this.name = Util.configureGitRepositoryName(url)
            configureReposDirectory()
            this.localPath = this.reposDirectory + name
            if (isCloned()) {
                this.lastCommit = searchAllRevCommits()?.last()?.name
            } else {
                cloneRepository()
                this.lastCommit = searchAllRevCommits()?.last()?.name
            }
        } else {
            this.localPath = path
            this.lastCommit = searchAllRevCommits()?.last()?.name
            def git = Git.open(new File(this.localPath))
            this.url = git.repository.config.getString("remote", "origin", "url")
            git.close()
            this.name = Util.configureGitRepositoryName(url)
        }
    }

    /***
     * Verifies if a repository is already cloned
     */
    private isCloned() {
        File dir = new File(this.localPath)
        File[] files = dir.listFiles()
        if (files && files.length > 0) true
        else false
    }

    /***
     * Clones a repository if it was not cloned yet.
     */
    private cloneRepository() throws CloningRepositoryException {
        String command = "git clone $url $name"

        try {
            def reposFolder = new File(this.reposDirectory)
            if (!reposFolder.exists()) {
                reposFolder.mkdir()
            }

            def folder = new File(this.localPath)
            if (!folder.exists()) {
                folder.mkdir()
            }

            Process p = Runtime.getRuntime().exec(command, null, reposFolder)
            p.waitFor()
        } catch (Exception ex) {
            Util.deleteFolder(this.localPath)
            throw new CloningRepositoryException(ex.message)
        }
    }

    private List<CodeChange> extractCodeChangesByFirstCommit(RevCommit commit, TestCodeAbstractAnalyser parser) {
        List<CodeChange> codeChanges = []
        def git = Git.open(new File(localPath))
        TreeWalk tw = new TreeWalk(git.repository)
        tw.reset()
        tw.setRecursive(true)
        tw.addTree(commit.tree)
        while (tw.next()) {
            if (!Util.isValidFile(tw.pathString)) continue

            def result = extractFileContent(commit, tw.pathString)

            if (Util.isGherkinFile(tw.pathString)) {
                def change = gherkinManager.extractGherkinAdds(commit, result, tw.pathString)
                if (change != null) codeChanges += change
            } else if (Util.isStepDefinitionFile(tw.pathString)) {
                StepdefManager stepdefManager = new StepdefManager(parser)
                def change = stepdefManager.extractStepDefinitionAdds(commit, result, tw.pathString)
                if (change != null) codeChanges += change
            } else {
                def lines = 0..<result.readLines().size()
                if (Util.isUnitTestFile(tw.pathString)) {
                    //codeChanges += extractUnitChanges(commit, tw.pathString, lines, parser)
                } else {
                    codeChanges += new ChangedProdFile(path: tw.pathString, type: DiffEntry.ChangeType.ADD, lines: lines)
                }
            }
        }
        git.close()
        codeChanges
    }

    /***
     * Computes the difference between two versions of a file or all files from two commits.
     *
     * @param filename file to evaluate. If it is empty, all differences between commits are computed.
     * @param newCommit the commit that contains a new version of the file.
     * @param oldCommit the commit that contains an older version of the file.
     * @return a list of DiffEntry objects that represents the difference between two versions of a file.
     */
    private List<DiffEntry> extractDiff(String filename, RevCommit newCommit, RevCommit oldCommit) {
        def git = Git.open(new File(localPath))
        def oldTree = prepareTreeParser(git, oldCommit.name)
        def newTree = prepareTreeParser(git, newCommit.name)
        def diffCommand = git.diff().setOldTree(oldTree).setNewTree(newTree)
        if (filename != null && !filename.isEmpty()) diffCommand.setPathFilter(PathFilter.create(filename))
        def diffs = diffCommand.call()
        git.close()

        List<DiffEntry> result = []
        diffs.each {
            it.oldPath = it.oldPath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            it.newPath = it.newPath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            result += it
        }

        result.findAll { file -> (Util.isValidFile(file.newPath) || Util.isValidFile(file.oldPath)) }
    }

    private ChangedStepdefFile extractStepDefinitionChanges(RevCommit commit, RevCommit parent, DiffEntry entry,
                                                            TestCodeAbstractAnalyser parser) {
        StepdefManager stepdefManager = new StepdefManager(parser)
        ChangedStepdefFile changedStepFile = null
        def newVersion = extractFileContent(commit, entry.newPath)
        def newDefs = stepdefManager.parseStepDefinitionFile(entry.newPath, newVersion, commit.name)
        def oldVersion = extractFileContent(parent, entry.oldPath)
        def oldDefs = stepdefManager.parseStepDefinitionFile(entry.oldPath, oldVersion, parent.name)

        //searches for changed or removed step definitions
        List<StepDefinition> changedStepDefinitions = []
        if (!Util.RESTRICT_GHERKIN_CHANGES) {
            oldDefs?.each { stepDef ->
                def foundStepDef = newDefs?.find { it.value == stepDef.value }
                if (foundStepDef && foundStepDef.value && !foundStepDef.value.empty) {
                    if (stepDef.size() == foundStepDef.size()) { //step definition might be changed
                        def stepDefEquals = GherkinManager.equals(foundStepDef, stepDef)
                        if (!stepDefEquals) changedStepDefinitions += foundStepDef
                    } else {//step definition was changed
                        changedStepDefinitions += foundStepDef
                    }
                } //if a step definition was removed, it was not relevant for the task
            }
        }

        //searches for added step definitions
        newDefs?.each { newStepDef ->
            def foundStepDef = oldDefs?.find { it.value == newStepDef.value }
            if (!foundStepDef || !foundStepDef.value || foundStepDef.value == "") {//it was not found because it is new
                changedStepDefinitions += newStepDef
            }
        }

        if (!changedStepDefinitions.isEmpty()) {
            changedStepFile = new ChangedStepdefFile(path: entry.newPath, changedStepDefinitions: changedStepDefinitions)
        }

        changedStepFile
    }

    /***
     * Identifies step definitions at added step definition files.
     * It is used only when dealing with done tasks.
     */
    private ChangedStepdefFile extractStepDefinitionAdds(RevCommit commit, DiffEntry entry, TestCodeAbstractAnalyser parser) {
        StepdefManager stepdefManager = new StepdefManager(parser)
        ChangedStepdefFile changedStepFile = null
        def newVersion = extractFileContent(commit, entry.newPath)
        def newStepDefinitions = stepdefManager.parseStepDefinitionFile(entry.newPath, newVersion, commit.name)

        if (newStepDefinitions && !newStepDefinitions.isEmpty()) {
            changedStepFile = new ChangedStepdefFile(path: entry.newPath, changedStepDefinitions: newStepDefinitions)
        }

        changedStepFile
    }

    private ChangedUnitTestFile extractUnitChanges(RevCommit commit, String path, List<Integer> lines, TestCodeAbstractAnalyser parser) {
        def newVersion = extractFileContent(commit, path)
        UnitTestManager.parseUnitFile(path, newVersion, lines, parser)
    }

    /***
     * Identifies changed scenarios definitions at gherkin files (features).
     * It is used only when dealing with done tasks.
     */
    private ChangedGherkinFile extractGherkinChanges(RevCommit commit, RevCommit parent, DiffEntry entry) {
        ChangedGherkinFile changedGherkinFile = null

        def newVersion = extractFileContent(commit, entry.newPath)
        def newFeature = gherkinManager.parseGherkinFile(newVersion, entry.newPath, commit.name)
        def oldVersion = extractFileContent(parent, entry.oldPath)
        def oldFeature = gherkinManager.parseGherkinFile(oldVersion, entry.oldPath, parent.name)

        if (!newFeature || !oldFeature) return changedGherkinFile

        def newScenarioDefinitions = newFeature?.children?.findAll { !(it instanceof Background) }
        def oldScenarioDefinitions = oldFeature?.children?.findAll { !(it instanceof Background) }

        //searches for changed or removed scenario definitions
        List<ScenarioDefinition> changedScenarioDefinitions = []
        if (!Util.RESTRICT_GHERKIN_CHANGES) {
            oldScenarioDefinitions?.each { definition ->
                def foundDefinition = newScenarioDefinitions?.find { it.name == definition.name }
                if (foundDefinition) {
                    if (definition.steps.size() == foundDefinition.steps.size()) {
                        //scenario definition might be changed
                        def equalsDefinitions = GherkinManager.equals(foundDefinition, definition)
                        if (!equalsDefinitions) changedScenarioDefinitions += foundDefinition
                    } else {//scenario definition was changed
                        changedScenarioDefinitions += foundDefinition
                    }
                } else { //if a scenario definition was removed, it was not relevant for the task
                    //log.info "commit ${commit.name} removed scenario from ${entry.newPath}:\n ${definition.name}"
                    definition.steps.each {
                        //log.info "${it.text}; ${entry.newPath} (${it.location.line})"
                        removedSteps += [path: entry.newPath, text: it.text]
                    }
                }
            }
        }

        //searches for added scenario definitions
        newScenarioDefinitions?.each { newDefinition ->
            def foundDefinition = oldScenarioDefinitions?.find { it.name == newDefinition.name }
            if (!foundDefinition) {//it was not found because it is new
                changedScenarioDefinitions += newDefinition
            }
        }

        if (!changedScenarioDefinitions.empty) {
            changedGherkinFile = new ChangedGherkinFile(path: entry.newPath, feature: newFeature, changedScenarioDefinitions: changedScenarioDefinitions)
        }

        return changedGherkinFile
    }

    /***
     * Identifies scenarios definitions at added gherkin files (features).
     * It is used only when dealing with done tasks.
     */
    private ChangedGherkinFile extractGherkinAdds(RevCommit commit, DiffEntry entry) {
        def newVersion = extractFileContent(commit, entry.newPath)
        gherkinManager.extractGherkinAdds(commit, newVersion, entry.newPath)
    }

    private CodeChange configureAddChange(RevCommit commit, DiffEntry entry, TestCodeAbstractAnalyser parser) {
        CodeChange change = null
        if (Util.isGherkinFile(entry.newPath)) {
            change = extractGherkinAdds(commit, entry)
        } else if (Util.isStepDefinitionFile(entry.newPath))
            change = extractStepDefinitionAdds(commit, entry, parser)
        else {
            def result = extractFileContent(commit, entry.newPath)
            def lines = 0..<result.readLines().size()
            if (Util.isUnitTestFile(entry.newPath)) {
                //change = extractUnitChanges(commit, entry.newPath, lines, parser)
            } else if (Util.isProductionFile(entry.newPath)) {
                change = new ChangedProdFile(path: entry.newPath, type: entry.changeType, lines: lines)
            }
        }
        change
    }

    private CodeChange configureModifyChange(RevCommit commit, RevCommit parent, DiffEntry entry, TestCodeAbstractAnalyser parser) {
        CodeChange change = null
        if (Util.isGherkinFile(entry.newPath))
            change = extractGherkinChanges(commit, parent, entry)
        else if (Util.isStepDefinitionFile(entry.newPath))
            change = extractStepDefinitionChanges(commit, parent, entry, parser)
        else {
            def lines = computeChanges(commit, entry.newPath)
            if (Util.isUnitTestFile(entry.newPath)) {
                //change = extractUnitChanges(commit, entry.newPath, lines, parser)
            } else if (Util.isProductionFile(entry.newPath)) {
                change = new ChangedProdFile(path: entry.newPath, type: entry.changeType, lines: lines)
            }
        }
        change
    }

    /***
     * Converts a list of DiffEntry objects into CodeChange objects.
     * Important: DiffEntry.ChangeType.RENAME and DiffEntry.ChangeType.COPY are ignored. As consequence, if a renamed
     * file also has code changes, such changes are also ignored.
     */
    private List<CodeChange> extractCodeChanges(RevCommit commit, RevCommit parent,
                                                TestCodeAbstractAnalyser parser) {
        def diffs = extractDiff(null, commit, parent)
        List<CodeChange> codeChanges = []
        diffs?.each { entry ->
            switch (entry.changeType) {
                case DiffEntry.ChangeType.ADD: //it is necessary to know file size because all lines were changed
                    def change = configureAddChange(commit, entry, parser)
                    if (change != null) {
                        codeChanges += change
                    }
                    break
                case DiffEntry.ChangeType.MODIFY:
                    def change = configureModifyChange(commit, parent, entry, parser)
                    if (change != null) {
                        codeChanges += change
                    }
                    break
                case DiffEntry.ChangeType.DELETE: //the file size is already known
                    if (Util.isProductionFile(entry.oldPath)) {
                        def result = extractFileContent(parent, entry.oldPath)
                        codeChanges += new ChangedProdFile(path: entry.oldPath, type: entry.changeType,
                                lines: 0..<result.readLines().size())
                    }
                    break
                case DiffEntry.ChangeType.RENAME:
                    codeChanges += new RenamingChange(path: entry.newPath, oldPath: entry.oldPath)
                    break
            }
        }

        codeChanges
    }

    private TreeWalk generateTreeWalk(RevTree tree, String filename) {
        def git = Git.open(new File(localPath))
        TreeWalk treeWalk = new TreeWalk(git.repository)
        treeWalk.addTree(tree)
        treeWalk.setRecursive(true)
        if (filename) treeWalk.setFilter(PathFilter.create(filename))
        if (!treeWalk.next()) {
            log.warn "Did not find expected file '${filename}'. It does not exist anymore!"
            treeWalk = null
        }
        git.close()
        return treeWalk
    }

    String extractFileContent(RevCommit commit, String filename) {
        def result = ""
        if (!filename || filename.empty) return result
        def searchedFile = filename.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/"))

        def git = Git.open(new File(localPath))
        TreeWalk treeWalk = generateTreeWalk(commit?.tree, searchedFile)
        if (treeWalk) {
            ObjectId objectId = treeWalk.getObjectId(0)
            if (objectId == ObjectId.zeroId()) return result
            try {
                ObjectLoader loader = git.repository.open(objectId)
                ByteArrayOutputStream stream = new ByteArrayOutputStream()
                loader.copyTo(stream)
                result = stream.toString("UTF-8")
                stream.reset()
            }
            catch (Exception ex) {
                log.error "Error while trying to retrieve content of file '${searchedFile}' for commit '${commit.name}'"
                ex.stackTrace.each { log.error it.toString() }
            }
        }

        git.close()

        return result
    }

    private static AbstractTreeIterator prepareTreeParser(Git git, String objectId) {
        RevWalk walk = null
        RevCommit commit
        RevTree tree
        CanonicalTreeParser oldTreeParser = null

        // from the commit we can build the tree which allows us to construct the TreeParser
        try {
            walk = new RevWalk(git.repository)
            commit = walk.parseCommit(ObjectId.fromString(objectId))
            tree = walk.parseTree(commit.getTree().getId())
            oldTreeParser = new CanonicalTreeParser()
            ObjectReader oldReader = git.repository.newObjectReader()
            oldTreeParser.reset(oldReader, tree.getId())
        } catch (Exception ex) {
            log.error ex.message
            ex.stackTrace.each { log.error it.toString() }
        }
        finally {
            walk?.dispose()
        }

        return oldTreeParser
    }

    private List<CodeChange> extractAllCodeChangesFromCommit(RevCommit commit, TestCodeAbstractAnalyser parser) {
        List<CodeChange> codeChanges = []

        switch (commit.parentCount) {
            case 0: //first commit
                codeChanges = extractCodeChangesByFirstCommit(commit, parser)
                break
            case 1: //commit with one parent
                codeChanges = extractCodeChanges(commit, commit.parents.first(), parser)
                break
            default: //merge commit (commit with more than one parent)
                commit.parents.each { parent ->
                    codeChanges += extractCodeChanges(commit, parent, parser)
                }
        }

        return codeChanges
    }

    private List<Commit> extractCommitsFromLogs(Iterable<RevCommit> logs, TestCodeAbstractAnalyser parser) {
        def commits = []
        logs?.each { c ->
            List<CodeChange> codeChanges = extractAllCodeChangesFromCommit(c, parser)
            List<ChangedProdFile> prodFiles = codeChanges.findAll {
                it instanceof ChangedProdFile
            } as List<ChangedProdFile>

            // identifies changed gherkin files and scenario definitions
            List<ChangedGherkinFile> gherkinChanges = codeChanges?.findAll {
                it instanceof ChangedGherkinFile
            } as List<ChangedGherkinFile>

            //identifies changed step files
            List<ChangedStepdefFile> stepChanges = codeChanges?.findAll {
                it instanceof ChangedStepdefFile
            } as List<ChangedStepdefFile>

            // identifies changed rspec files
            //List<UnitFile> unitChanges = codeChanges?.findAll{ it instanceof UnitFile } as List<UnitFile>
            List<ChangedUnitTestFile> unitChanges = []

            List<RenamingChange> renameChanges = codeChanges?.findAll {
                it instanceof RenamingChange
            } as List<RenamingChange>

            def files = getAllChangedFilesFromCommit(c)

            commits += new Commit(hash: c.name, message: c.fullMessage.replaceAll(RegexUtil.NEW_LINE_REGEX, " "),
                    author: c.authorIdent.name, date: c.commitTime, coreChanges: prodFiles, gherkinChanges: gherkinChanges,
                    unitChanges: unitChanges, stepChanges: stepChanges, renameChanges: renameChanges, merge: c.parentCount > 1,
                    files: files)
        }
        commits
    }

    /* PROBLEM: Deal with removed lines. */

    private List<Integer> computeChanges(RevCommit commit, String filename) {
        def changedLines = []
        def git = Git.open(new File(localPath))
        BlameCommand blamer = new BlameCommand(git.repository)
        blamer.setStartCommit(ObjectId.fromString(commit.name))
        blamer.setFilePath(filename.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/")))
        BlameResult blameResult = blamer.call()

        String text = extractFileContent(commit, filename)
        if (!text && text.empty) {
            git.close()
            return changedLines
        }

        List<String> fileContent = []
        try {
            fileContent = text.readLines()
            for (int i = 0; i < fileContent.size(); i++) {
                RevCommit c = blameResult?.getSourceCommit(i)
                if (c?.name == commit.name) changedLines += i
            }
        } catch (Exception ignored) {
            log.error "Error: git blame '${filename}' (size ${fileContent.size()}) (commit ${commit.name})"
            fileContent.each { log.error it.toString() }
            log.error "Exception: ${ignored.class}; '${ignored.message}'"
        }

        git.close()

        /* if the result is empty, it means changes were removed lines only; the blame command can not deal with
        * this type of change; a new strategy should be defined!!!! */
        return changedLines
    }

    /* CÓDIGO COPIADO DO PROJETO MININGIT PARA IDENTIFICAR TODOS OS ARQUIVOS ALTERADOS POR UMA TAREFA, SEM FILTROS */

    private List getAllChangedFilesFromCommit(RevCommit commit) {
        def files = []

        switch (commit.parentCount) {
            case 0: //first commit
                def git = Git.open(new File(localPath))
                TreeWalk tw = new TreeWalk(git.repository)
                tw.reset()
                tw.setRecursive(true)
                tw.addTree(commit.tree)
                while (tw.next()) {
                    files += tw.pathString.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
                }
                //tw.release()
                git.close()
                break
            case 1: //commit with one parent
                def diffs = getDiff(commit.tree, commit.parents.first().tree)
                files = getAllChangedFilesFromDiffs(diffs)
                break
            default: //merge commit (commit with more than one parent)
                commit.parents.each { parent ->
                    def diffs = getDiff(commit.tree, parent.tree)
                    files += getAllChangedFilesFromDiffs(diffs)
                }
        }

        files?.sort()?.unique()
    }

    private static List getAllChangedFilesFromDiffs(List<DiffEntry> diffs) {
        def files = []
        if (!diffs?.empty) {
            diffs.each { entry ->
                if (entry.changeType == DiffEntry.ChangeType.DELETE) files += entry.oldPath
                else {
                    files += entry.newPath
                }
            }
        }
        return files
    }

    private List<DiffEntry> getDiff(RevTree newTree, RevTree oldTree) {
        def git = Git.open(new File(localPath))
        DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())
        df.setRepository(git.repository)
        df.setDiffComparator(RawTextComparator.DEFAULT)
        df.setDetectRenames(true)
        List<DiffEntry> diffs = df.scan(oldTree, newTree)
        List<DiffEntry> result = []
        diffs.each {
            it.oldPath = it.oldPath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            it.newPath = it.newPath.replaceAll(RegexUtil.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            result += it
        }
        git.close()
        result
    }


}

package commitAnalyser

import gherkin.Parser
import gherkin.ast.Feature
import gherkin.ast.ScenarioDefinition
import groovy.util.logging.Slf4j
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import taskAnalyser.GherkinFile
import org.eclipse.jgit.api.BlameCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.blame.BlameResult
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import taskAnalyser.UnitFile
import testCodeAnalyser.ruby.unitTest.RSpecTestDefinitionVisitor
import testCodeAnalyser.ruby.RubyTestCodeParser
import util.Util

import java.util.regex.Matcher

/***
 * Represents a git repository to be downloaded for analysis purpose.
 */
@Slf4j
class GitRepository {

    String url
    String name
    String localPath

    static int counter = 0 //used to compute branch name

    public GitRepository(String url){
        this.url = url + Util.GIT_EXTENSION
        this.name = Util.configureGitRepositoryName(url)
        this.localPath = Util.REPOSITORY_FOLDER_PATH + name
        cloneRepository()
    }

    /***
     * Clones a repository if it was not cloned yet.
     */
    private cloneRepository(){
        File dir = new File(localPath)
        File[] files = dir.listFiles()
        if(files){
            log.info "Already cloned from " + url + " to " + localPath
        }
        else{
            def result = Git.cloneRepository().setURI(url).setDirectory(dir).call()
            result.close()
            log.info "Cloned from " + url + " to " + localPath
        }
    }

    /***
     * Computes the difference between two versions of a file or all files from two commits using DiffFormatter.
     *
     * @param filename file to evaluate. If it is empty, all differences between commits are computed.
     * @param newTree the tree that contains a new version of the file.
     * @param oldTree the tree that contains an older version of the file.
     * @return a list of DiffEntry objects that represents the difference between two versions of a file.
     */
    private List<DiffEntry> extractDiff(String filename, RevTree newTree, RevTree oldTree){
        def git = Git.open(new File(localPath))

        DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())
        df.setRepository(git.repository)
        df.setDiffComparator(RawTextComparator.DEFAULT)
        df.setDetectRenames(true)
        if(filename!=null && !filename.isEmpty()) df.setPathFilter(PathFilter.create(filename))
        List<DiffEntry> diffs = df.scan(oldTree, newTree)
        df.release()
        git.close()

        List<DiffEntry> result = []
        diffs.each{
            it.oldPath = it.oldPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            it.newPath = it.newPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            result += it
        }

        return result.findAll{ file -> Util.isValidCode(file.newPath) && Util.isValidCode(file.oldPath) }
    }

    /***
     * Computes the difference between two versions of a file or all files from two commits.
     * The result is the same of the previous method.
     *
     * @param filename file to evaluate. If it is empty, all differences between commits are computed.
     * @param newCommit the commit that contains a new version of the file.
     * @param oldCommit the commit that contains an older version of the file.
     * @return a list of DiffEntry objects that represents the difference between two versions of a file.
     */
    private List<DiffEntry> extractDiff(String filename, RevCommit newCommit, RevCommit oldCommit){
        def git = Git.open(new File(localPath))
        def oldTree = prepareTreeParser(git, oldCommit.name)
        def newTree = prepareTreeParser(git, newCommit.name)
        def diffCommand = git.diff().setOldTree(oldTree).setNewTree(newTree)
        if(filename!=null && !filename.isEmpty()) diffCommand.setPathFilter(PathFilter.create(filename))
        def diffs = diffCommand.call()
        git.close()

        List<DiffEntry> result = []
        diffs.each{
            it.oldPath = it.oldPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            it.newPath = it.newPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            result += it
        }

        return result.findAll{ file -> Util.isValidCode(file.newPath) && Util.isValidCode(file.oldPath) }
    }

    /***
     * Prints a file content showing the differences between it and its previous version.
     *
     * @param entry the DiffEntry object that represents the difference between two versions of a file.
     */
    private showDiff(DiffEntry entry){
        def git = Git.open(new File(localPath))
        ByteArrayOutputStream stream = new ByteArrayOutputStream()
        DiffFormatter formatter = new DiffFormatter(stream)
        formatter.setRepository(git.repository)
        formatter.setDetectRenames(true)
        formatter.setContext(1000) //to show all lines
        formatter.format(entry)
        def lines = stream.toString().readLines()
        def result = lines.getAt(5..lines.size()-1)
        result.eachWithIndex{ val, index ->
            println "($index) $val"
        }
        git.close()
        stream.reset()
        formatter.release()
    }

    private static Feature parseGherkinFile(List<String> lines, String filename){
        Feature feature = null
        if(!lines || lines.isEmpty()){
            log.warn "Problem to parse Gherkin file '$filename'. Reason: The commit deleted it."
        }
        else{
            try{
                Parser<Feature> featureParser = new Parser<>()
                def content = lines.join("\n")
                feature = featureParser.parse(content)
            } catch(Exception ex){
                log.warn "Problem to parse Gherkin file '$filename': ${ex.message}."
            }
        }
        feature
    }

    private static boolean equals(ScenarioDefinition sd1, ScenarioDefinition sd2){
        def result = true
        for (int i = 0; i < sd1.steps.size(); i++) {
            def step1 = sd1.steps.get(i)
            def step2 = sd2.steps.get(i)
            if(step1.text != step2.text || step1.keyword != step2.keyword
                    || step1.argument != step2.argument) {
                result = false
                break
            }
        }
        result
    }

    /***
     * Identifies changed scenarios definitions at gherkin files (features).
     * It is used only when dealing with done tasks.
     */
    private CodeChange extractGherkinChanges(RevCommit commit, RevCommit parent, DiffEntry entry){
        CodeChange codeChange = null

        def newVersion = extractFileContent(commit, entry.newPath)
        def newFeature = parseGherkinFile(newVersion, entry.newPath)
        def oldVersion = extractFileContent(parent, entry.oldPath)
        def oldFeature = parseGherkinFile(oldVersion, entry.oldPath)

        if(!newFeature || !oldFeature) return codeChange

        def newScenarioDefinitions = newFeature?.scenarioDefinitions
        def oldScenarioDefinitions = oldFeature?.scenarioDefinitions

        //searches for changed or removed scenario definitions
        List<ScenarioDefinition> changedScenarioDefinitions = []
        oldScenarioDefinitions?.each{ oldScenDef ->
            def foundScenDef = newScenarioDefinitions?.find{ it.name == oldScenDef.name }
            if(foundScenDef && foundScenDef.name && foundScenDef.name != ""){
                if (oldScenDef.steps.size() == foundScenDef.steps.size()){ //scenario definition might be changed
                    def scenDefEquals = equals(foundScenDef, oldScenDef)
                    if(!scenDefEquals) changedScenarioDefinitions += foundScenDef
                } else {//scenario definition was changed
                    changedScenarioDefinitions += foundScenDef
                }
            } //if a scenario definition was removed, it was not relevant for the task
        }

        //searches for added scenario definitions
        newScenarioDefinitions?.each{ newScenDef ->
            def foundScenDef = oldScenarioDefinitions?.find{ it.name == newScenDef.name }
            if(!foundScenDef || !foundScenDef.name || foundScenDef.name == ""){//nao foi achado porque foi adicionado
                changedScenarioDefinitions += newScenDef
            }
        }

        if(!changedScenarioDefinitions.isEmpty()){
            GherkinFile changedGherkinFile = new GherkinFile(commitHash:commit.name, path:entry.newPath,
                    feature:newFeature, changedScenarioDefinitions:changedScenarioDefinitions)
            codeChange = new CodeChange(filename: entry.newPath, type: entry.changeType, lines: [],
                    gherkinFile: changedGherkinFile)
        }

        codeChange
    }

    /***
     * Identifies scenarios definitions at added gherkin files (features) by the first commit of the repository.
     * It is used only when dealing with done tasks.
     */
    private static GherkinFile extractGherkinAdds(RevCommit commit, List<String> content, String path){
        GherkinFile changedGherkinFile = null
        def newFeature = parseGherkinFile(content, path)
        def newScenarioDefinitions = newFeature?.scenarioDefinitions

        if(newScenarioDefinitions && !newScenarioDefinitions.isEmpty()){
            changedGherkinFile = new GherkinFile(commitHash:commit.name, path:path,
                    feature:newFeature, changedScenarioDefinitions:newScenarioDefinitions)
        }
        changedGherkinFile
    }

    /***
     * Identifies scenarios definitions at added gherkin files (features).
     * It is used only when dealing with done tasks.
     */
    private CodeChange extractGherkinAdds(RevCommit commit, DiffEntry entry){
        CodeChange codeChange = null
        def newVersion = extractFileContent(commit, entry.newPath)
        def newFeature = parseGherkinFile(newVersion, entry.newPath)
        def newScenarioDefinitions = newFeature?.scenarioDefinitions

        if(newScenarioDefinitions && !newScenarioDefinitions.isEmpty()){
            GherkinFile changedGherkinFile = new GherkinFile(commitHash:commit.name, path:entry.newPath,
                    feature:newFeature, changedScenarioDefinitions:newScenarioDefinitions)
            codeChange = new CodeChange(filename: entry.newPath, type: entry.changeType, lines: [],
                    gherkinFile: changedGherkinFile)
        }

        codeChange
    }

    /***
     * Converts a list of DiffEntry objects into CodeChange objects.
     * Important: DiffEntry.ChangeType.RENAME and DiffEntry.ChangeType.COPY are ignored. As consequence, if a renamed
     * file also has code changes, such changes are also ignored.
     */
    private List<CodeChange> extractAllCodeChangeFromDiffs(RevCommit commit, RevCommit parent, List<DiffEntry> diffs) {
        List<CodeChange> codeChanges = []
            diffs?.each{ entry ->
                if(Util.isValidCode(entry.newPath) && Util.isValidCode(entry.oldPath)) {
                    switch (entry.changeType) {
                        case DiffEntry.ChangeType.ADD: //it is necessary to know the file size because all lines were changed
                            def result = extractFileContent(commit, entry.newPath)
                            if(entry.newPath.endsWith(Util.FEATURE_FILENAME_EXTENSION) ){
                                def change = extractGherkinAdds(commit, entry)
                                if(change != null) { codeChanges += change }
                            }
                            else{
                                codeChanges += new CodeChange(filename: entry.newPath, type: entry.changeType, lines: 0..<result.size())
                            }
                            break
                        case DiffEntry.ChangeType.DELETE: //the file size is already known
                            def result = extractFileContent(parent, entry.oldPath)
                            codeChanges += new CodeChange(filename: entry.oldPath, type: entry.changeType, lines: 0..<result.size())
                            break
                        case DiffEntry.ChangeType.MODIFY:
                            if(entry.newPath.endsWith(Util.FEATURE_FILENAME_EXTENSION) ){
                                def change = extractGherkinChanges(commit, parent, entry)
                                if(change != null) { codeChanges += change }
                            }
                            else{
                                def lines = computeChanges(commit, entry.newPath)
                                codeChanges += new CodeChange(filename: entry.newPath, type: entry.changeType, lines: lines)
                            }
                            break
                        case DiffEntry.ChangeType.RENAME:
                            log.info "<RENAME> old:${entry.oldPath}; new:${entry.newPath}"
                        case DiffEntry.ChangeType.COPY:
                            codeChanges += new CodeChange(filename: entry.newPath, type: entry.changeType, lines: [])
                    }
                }
            }
        return codeChanges
    }

    /***
     * Retrieves a commit.
     *
     * @param sha the commit's identification.
     * @return the commit.
     */
    private RevCommit extractCommit(String sha){
        def git = Git.open(new File(localPath))
        def result = git.log().call().find{ it.name == sha }
        git.close()
        return result
    }

    private TreeWalk generateTreeWalk(RevTree tree, String filename){
        def git = Git.open(new File(localPath))
        TreeWalk treeWalk = new TreeWalk(git.repository)
        treeWalk.addTree(tree)
        treeWalk.setRecursive(true)
        if(filename) treeWalk.setFilter(PathFilter.create(filename))
        treeWalk.next()
        git.close()
        return treeWalk
    }

    private List<String> extractFileContent(RevCommit commit, String filename) {
        def result = []
        def git = Git.open(new File(localPath))
        filename = filename.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/"))
        RevWalk revWalk = new RevWalk(git.repository)
        TreeWalk treeWalk = generateTreeWalk(commit?.tree, filename)
        ObjectId objectId = treeWalk.getObjectId(0)
        try{
            ObjectLoader loader = git.repository.open(objectId)
            ByteArrayOutputStream stream = new ByteArrayOutputStream()
            loader.copyTo(stream)
            revWalk.dispose()
            result = stream.toString().readLines()
            stream.reset()
        }
        catch(MissingObjectException exception){
            if(objectId.equals(ObjectId.zeroId()))
                log.error "There is no ObjectID for the commit tree. Verify the file separator used in the filename '$filename'."
        }

        git.close()

        return result
    }

    private static AbstractTreeIterator prepareTreeParser(Git git, String objectId)  {
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
        } catch(Exception ex){
            log.error ex.message
        }
        finally{
            walk?.dispose()
        }

        return oldTreeParser
    }

    private static listChangedFiles(def diffs, def commit){
        log.info "FILES FROM COMMIT '${commit.name}':"
        def files = diffs?.collect{ diff->
            if(diff.newPath == "\\dev\\null") diff.oldPath + " (DELETED)"
            else diff.newPath
        }
        files?.sort()?.each{ log.info it }
    }

    private List<CodeChange> extractAllCodeChangesFromCommit(RevCommit commit){
        List<CodeChange> codeChanges = []

        switch(commit.parentCount){
            case 0: //first commit
                def git = Git.open(new File(localPath))
                TreeWalk tw = new TreeWalk(git.repository)
                tw.reset()
                tw.setRecursive(true)
                tw.addTree(commit.tree)
                while(tw.next()){
                    if(Util.isValidCode(tw.pathString)) {
                        List<String> result = extractFileContent(commit, tw.pathString)
                        if( tw.pathString.endsWith(Util.FEATURE_FILENAME_EXTENSION) ){
                            def change = extractGherkinAdds(commit, result, tw.pathString)
                            if(change != null) {
                                codeChanges += new CodeChange(filename: tw.pathString, type: DiffEntry.ChangeType.ADD, lines: [],
                                        gherkinFile: change)
                            }
                        }
                        else{
                            codeChanges += new CodeChange(filename: tw.pathString, type: DiffEntry.ChangeType.ADD, lines: 0..<result.size())
                        }
                    }
                }
                tw.release()
                git.close()
                break
            case 1:
                def diffs = extractDiff(null, commit, commit.parents.first())
                codeChanges = extractAllCodeChangeFromDiffs(commit, commit.parents.first(), diffs)
                break
            default: //merge commit
                commit.parents.each{ parent ->
                    def diffs = extractDiff(null, commit, parent)
                    codeChanges += extractAllCodeChangeFromDiffs(commit, parent, diffs)
                }
        }

        return codeChanges
    }

    private List<Commit> extractCommitsFromLogs(Iterable<RevCommit> logs){
        def commits = []
        logs?.each{ c ->
            List<CodeChange> codeChanges = extractAllCodeChangesFromCommit(c)
            List<CodeChange> prodFiles = Util.findAllProductionFilesFromCodeChanges(codeChanges)
            List<CodeChange> testFiles = Util.findAllTestFilesFromCodeChanges(codeChanges)

            /* identifies changed gherkin files and scenario definitions */
            List<GherkinFile> gherkinChanges = testFiles?.findAll{ it.gherkinFile }*.gherkinFile

            /* identifies changed rspec files */
            List<CodeChange> unitChanges = []//testFiles.findAll{ it.filename.contains(Util.UNIT_TEST_FILES_RELATIVE_PATH+File.separator) }

            commits += new Commit(hash:c.name, message:c.fullMessage.replaceAll(Util.NEW_LINE_REGEX," "),
                    author:c.authorIdent.name, date:c.commitTime, productionChanges: prodFiles,
                    testChanges: testFiles, codeChanges: (prodFiles+testFiles).unique(),
                    gherkinChanges:gherkinChanges, unitChanges:unitChanges)
        }
        return commits
    }

    //* PROBLEM: Deal with removed lines. */
    private List<Integer> computeChanges(RevCommit commit, String filename){
        def changedLines = []
        def git = Git.open(new File(localPath))
        BlameCommand blamer = new BlameCommand(git.repository)
        blamer.setStartCommit(ObjectId.fromString(commit.name))
        blamer.setFilePath(filename.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/")))
        BlameResult blameResult = blamer.call()

        List<String> fileContent = extractFileContent(commit, filename)
        //println "commit: ${commit.name}; filename: $filename; file size: ${fileContent.size()}"
        fileContent.eachWithIndex { line, i ->
            RevCommit c = blameResult?.getSourceCommit(i)
            if(c?.name?.equals(commit.name)) changedLines += i
        }

        git.close()

        /* if the result is empty, it means changes were removed lines only; the blame command can not deal with
        * this type of change; a new strategy should be defined!!!! */
        return changedLines
    }

    String findLastCommitSHA(){
        def git = Git.open(new File(localPath))
        Iterable<RevCommit> logs = git.log().call()
        git.close()
        logs?.sort{it.commitTime}?.last()?.name
    }

    private Iterable<RevCommit> searchAllRevCommits(){
        def git = Git.open(new File(localPath))
        Iterable<RevCommit> logs = git?.log()?.call()?.sort{ it.commitTime }
        git.close()
        logs
    }

    private Iterable<RevCommit> searchAllRevCommitsBySha(String... hash){
        def git = Git.open(new File(localPath))
        def logs = git?.log()?.call()?.findAll{ it.name in hash }?.sort{ it.commitTime }
        git.close()
        logs
    }

    /***
     * Searches all commits from a Git repository.
     *
     * @return a list of commits.
     */
    List<Commit> searchAllCommits(){
        def logs = searchAllRevCommits()
        extractCommitsFromLogs(logs)
    }

    /***
     * Searches commits from a Git repository by hash value.
     *
     * @param hash a set of hash value
     * @return a list of commits that satisfy the search criteria.
     */
    List<Commit> searchCommitsBySha(String... hash) {
        def logs = searchAllRevCommitsBySha(hash)
        extractCommitsFromLogs(logs)
    }

    /***
     * (TO VALIDATE)
     * Interprets changed lines by a group of commits as changed scenarios definitions at gherkin files (features).
     * It is used only when dealing with done tasks.
     * It could introduce error and after validation it should be removed.
     *
     * @param commits commits that caused changes in gherkin files
     * @return changed gherkin content
     *
    List<GherkinFile> identifyChangedGherkinContent(List<Commit> commits) {
        Parser<Feature> featureParser = new Parser<>()
        List<GherkinFile> changedGherkinFiles = []

        commits.each { commit ->
            commit.gherkinChanges.each { change ->
                def path = localPath+File.separator+change.filename
                def reader = null
                try{
                    reader = new FileReader(path)
                    Feature feature = featureParser.parse(reader)
                    def changedScenarioDefinitions = feature?.scenarioDefinitions?.findAll{ it.location.line-1 in change.lines }
                    if(changedScenarioDefinitions){
                        changedGherkinFiles += new GherkinFile(commitHash:commit.hash, path:path,
                                feature:feature, changedScenarioDefinitions:changedScenarioDefinitions)
                    }

                } catch(FileNotFoundException ex){
                    log.warn "Problem to parse Gherkin file: ${ex.message}"
                }
                finally {
                    reader?.close()
                }
            }
        }

        return changedGherkinFiles
    }*/

    /***
     * Interprets changed lines in unit test files.
     * It is used only when dealing with done tasks.
     *
     * @param commit commit that caused changes in unit test files
     * @return list of changed unit test files
     */
    List<UnitFile> identifyChangedUnitTestContent(Commit commit){
        def changedUnitFiles = []
        //println "All changed unit test files: ${commit.unitChanges*.filename}"

        log.info "Commit: ${commit.hash}"
        commit.unitChanges.each{ change ->
            def path = localPath + File.separator + change.filename
            log.info "Change path: $path"
            try{
                def visitor = new RSpecTestDefinitionVisitor(localPath, path)
                def node = RubyTestCodeParser.generateAst(path)
                node?.accept(visitor)
                if(visitor.tests.isEmpty()){
                    log.info "The unit file does not contain any test definition!"
                }
                else{
                    def changedTests = visitor.tests.findAll{ it.lines.intersect(change.lines) }
                    if(changedTests){
                        /*println "All changed unit tests: "
                        changedTests.each{ println it }*/

                        def unitFile = new UnitFile(commitHash:commit.hash, path:path, tests:changedTests, productionClass:visitor.productionClass)
                        changedUnitFiles += unitFile
                    }
                    else{
                        log.info "No unit test was changed or the changed one was not found!"
                    }
                }
            } catch(FileNotFoundException ex){
                log.warn "Problem to parse unit test file: ${ex.message}. Reason: The commit deleted it."
            }
        }

        return changedUnitFiles
    }

    /***
     * (TO VALIDATE)
     * Changes interpretation are based in the checkout of the last commit of the task. It is used only when dealing with done tasks.
     * It could introduce error and after validation it should be removed.
     *
     * @param commits commits that caused changes in unit test files
     * @return list of changed unit test files
     */
    List<UnitFile> identifyChangedUnitTestContent(List<Commit> commits){
        def changedUnitFiles = []
        //println "All changed unit test files: ${commit.unitChanges*.filename}"

        commits.each { commit ->
            log.info "Commit: ${commit.hash}"
            commit.unitChanges.each { change ->
                def path = localPath + File.separator + change.filename
                log.info "Change path: $path"
                try {
                    def visitor = new RSpecTestDefinitionVisitor(localPath, path)
                    def node = RubyTestCodeParser.generateAst(path)
                    node?.accept(visitor)
                    if (visitor.tests.isEmpty()) {
                        log.info "The unit file does not contain any test definition!"
                    } else {
                        def changedTests = visitor.tests.findAll { it.lines.intersect(change.lines) }
                        if (changedTests) {
                            /*println "All changed unit tests: "
                            changedTests.each{ println it }*/

                            def unitFile = new UnitFile(commitHash: commit.hash, path: path, tests: changedTests, productionClass: visitor.productionClass)
                            changedUnitFiles += unitFile
                        } else {
                            log.info "No unit test was changed or the changed one was not found!"
                        }
                    }
                } catch (FileNotFoundException ex) {
                    log.warn "Problem to parse unit test file: ${ex.message}. Reason: The commit deleted it."
                }
            }
        }

        return changedUnitFiles
    }

    /***
     * Checkouts a specific version of git repository.
     * @param sha the commit's identification.
     */
    def reset(String sha){
        def git = Git.open(new File(localPath))
        def branchName = "spgroup-tag" + counter++
        def branch = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().find{ it.name.endsWith(branchName) }
        if(branch) git.branchDelete().setForce(true).setBranchNames(branch.name).call()
        git.checkout().setForce(true).setCreateBranch(true).setName(branchName).setStartPoint(sha).call()
        git.close()
    }

    /***
     * Checkouts the last version of git repository.
     */
    def reset(){
        def git = Git.open(new File(localPath))
        Iterable<RevCommit> logs = git.log().call()
        git.close()
        def sha = logs.sort{it.commitTime}.last()?.name
        reset(sha)
    }

}

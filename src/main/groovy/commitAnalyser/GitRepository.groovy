package commitAnalyser

import gherkin.Parser
import gherkin.ast.Feature
import org.eclipse.jgit.api.ListBranchCommand
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
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import util.Util

import java.util.regex.Matcher

/***
 * Represents a git repository to be downloaded for analysis purpose.
 */
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
            System.out.println("Already cloned from " + url + " to " + localPath)
        }
        else{
            def result = Git.cloneRepository().setURI(url).setDirectory(dir).call()
            result.close()
            System.out.println("Cloned from " + url + " to " + localPath)
        }
    }

    /***
     * Computes the difference between two versions of a file.
     * @param filename file to evaluate
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
        git.close()

        List<DiffEntry> result = []
        diffs.each{
            it.oldPath = it.oldPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            it.newPath = it.newPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            result += it
        }
        return result
    }

    /***
     * Prints a file content showing the differences between it and its previous version.
     * @param entry the DiffEntry object that represents the difference between two versions of a file.
     */
    private showDiff(DiffEntry entry){
        def git = Git.open(new File(localPath))
        ByteArrayOutputStream stream = new ByteArrayOutputStream()
        DiffFormatter formatter = new DiffFormatter(stream)
        formatter.setRepository(git.repository)
        formatter.setDetectRenames(true)
        formatter.setContext(1)
        formatter.format(entry)
        git.close()
        println "DIFF: "
        println stream
    }

    /***
     * Converts a list of DiffEntry objects into CodeChange objects.
     */
    private static List<CodeChange> extractAllCodeChangeFromDiffs(List<DiffEntry> diffs) {
        List<CodeChange> codeChanges = []
        if (!diffs?.empty) {
            diffs.each{ entry ->
                if( entry.changeType.equals(DiffEntry.ChangeType.DELETE) ){
                    codeChanges += new CodeChange(filename:entry.oldPath, type:entry.changeType)
                }
                else {
                    //showDiff(entry)
                    codeChanges += new CodeChange(filename:entry.newPath, type:entry.changeType)
                    //if(entry.changeType==DiffEntry.ChangeType.RENAME) println "<RENAME> old:${entry.oldPath}; new:${entry.newPath}"
                }
            }
        }
        return codeChanges
    }

    /***
     * Retrieves a commit.
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

    private List<String> extractFileContent(ObjectId commitID, String filename) {
        def result = []
        def git = Git.open(new File(localPath))
        filename = filename.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/"))
        RevWalk revWalk = new RevWalk(git.repository)
        RevCommit commit = revWalk.parseCommit(commitID)
        TreeWalk treeWalk = generateTreeWalk(commit?.tree, filename)
        ObjectId objectId = treeWalk.getObjectId(0)
        try{
            ObjectLoader loader = git.repository.open(objectId)
            ByteArrayOutputStream stream = new ByteArrayOutputStream()
            loader.copyTo(stream)
            revWalk.dispose()
            result = stream.toString().readLines()
        }
        catch(MissingObjectException exception){
            if(objectId.equals(ObjectId.zeroId()))
                println "There is no ObjectID for the commit tree. Verify the file separator used in the filename."
        }

        git.close()

        return result
    }

    private List<CodeChange> extractAllCodeChangesFromCommit(RevCommit commit){
        List<CodeChange> codeChanges = []
        if(commit.parentCount>0) {
            def diffs = extractDiff(null, commit.tree, commit.parents.first().tree)
            codeChanges = extractAllCodeChangeFromDiffs(diffs)
        }
        else{ //first commit
            def git = Git.open(new File(localPath))
            TreeWalk tw = new TreeWalk(git.repository)
            tw.reset()
            tw.setRecursive(true)
            tw.addTree(commit.tree)
            while(tw.next()){
                codeChanges += new CodeChange(filename: tw.pathString, type:DiffEntry.ChangeType.ADD)
            }
            tw.release()
            git.close()
        }

        return codeChanges
    }

    private List<Commit> extractCommitsFromLogs(Iterable<RevCommit> logs){
        def commits = []
        logs.each{ c ->
            List<CodeChange> codeChanges = extractAllCodeChangesFromCommit(c)
            List<CodeChange> prodFiles = Util.findAllProductionFilesFromCodeChanges(codeChanges)
            List<CodeChange> testFiles = Util.findAllTestFilesFromCodeChanges(codeChanges)
            commits += new Commit(hash:c.name, message:c.fullMessage.replaceAll(Util.NEW_LINE_REGEX," "),
                    author:c.authorIdent.name, date:c.commitTime, productionChanges: prodFiles,
                    testChanges: testFiles, codeChanges: (prodFiles+testFiles).unique())
        }
        return commits
    }

    //* PROBLEM: Deal with removed lines. */
    private List<Integer> computeChanges(ObjectId commitID, String hash, String filename){
        def changedLines = []
        def git = Git.open(new File(localPath))
        BlameCommand blamer = new BlameCommand(git.repository)
        blamer.setStartCommit(commitID)
        blamer.setFilePath(filename.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/")))
        BlameResult blameResult = blamer.call()

        List<String> fileContent = extractFileContent(commitID, filename)
        fileContent.eachWithIndex { line, i ->
            RevCommit c = blameResult?.getSourceCommit(i)
            if(c?.name?.equals(hash)) changedLines += i
        }

        git.close()

        return changedLines
    }

    /* Important: DiffEntry.ChangeType.RENAME and DiffEntry.ChangeType.COPY are ignored. As consequence, if a renamed
       file also has code changes, such changes are also ignored. */
    private List<Integer> identifyChangedLines(String hash, CodeChange codeChange) {
        def changedLines = []
        RevCommit commit = extractCommit(hash)
        ObjectId commitID = ObjectId.fromString(commit.name)

        switch(codeChange.type){
            case DiffEntry.ChangeType.ADD:
            case DiffEntry.ChangeType.DELETE:
                List<String> fileContent = extractFileContent(commitID, codeChange.filename)
                def lines = fileContent.size()
                changedLines = (0 ..< lines)
                break
            case DiffEntry.ChangeType.MODIFY:
                changedLines = computeChanges(commitID, hash, codeChange.filename)
        }
        return changedLines
    }

    /***
     * Searches all commits from a Git repository.
     * @return a list of commits.
     */
    List<Commit> searchAllCommits(){
        def git = Git.open(new File(localPath))
        Iterable<RevCommit> logs = git.log().call()
        git.close()
        return extractCommitsFromLogs(logs).sort{ it.date }
    }

    /***
     * Searches commits from a Git repository by hash value.
     * @param hash a set of hash value
     * @return a list of commits that satisfy the search criteria.
     */
    List<Commit> searchBySha(String... hash) {
        def git = Git.open(new File(localPath))
        def logs = git.log().call().findAll{ it.name in hash }
        git.close()
        return extractCommitsFromLogs(logs).sort{ it.date }
    }

    /***
     * Defines the gherkin files (features) and scenarios definitions changed by a group of commits.
     * @param commits commits that caused changes in gherkin files
     * @return changed gherkin content
     */
    List<GherkinFile> identifyChangedGherkinContent(List<Commit> commits) {
        Parser<Feature> featureParser = new Parser<>()
        List<GherkinFile> changedGherkinFiles = []

        commits.each { commit ->
            commit.gherkinChanges.each { change ->
                change.lines = identifyChangedLines(commit.hash, change)
                def path = localPath+File.separator+change.filename
                def reader = new FileReader(path)
                try{
                    Feature feature = featureParser.parse(reader)
                    reader.close()
                    def changedScenarioDefinitions = feature?.scenarioDefinitions?.findAll{ it.location.line in change.lines }
                    if(changedScenarioDefinitions){
                        changedGherkinFiles += new GherkinFile(commitHash:commit.hash, path:path,
                                feature:feature, changedScenarioDefinitions:changedScenarioDefinitions)
                    }

                } catch(FileNotFoundException ex){
                    println "Problem to parse Gherkin file: ${ex.message}"
                }
            }
        }

        return changedGherkinFiles
    }

    /***
     * Checkouts a specific version of git repository.
     * @param sha the commit's identification.
     */
    def reset(String sha){
        def git = Git.open(new File(localPath))
        def branchName = "spgroup-tag" + counter++
        def branch = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().find{ it.name.endsWith(branchName) }
        if(branch) git.branchDelete().setBranchNames(branch.name).call()
        //git.checkout().setForce(true).setName(branchName).call()
        git.checkout().setForce(true).setCreateBranch(true).setName(branchName).setStartPoint(sha).call()
        git.close()
    }

    /***
     * Checkouts the last version of git repository.
     */
    def reset(){
        def git = Git.open(new File(localPath))
        git.checkout().setForce(true).setName("master").call()
        git.close()
    }

}

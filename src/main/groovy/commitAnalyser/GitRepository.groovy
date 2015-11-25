package commitAnalyser

import gherkin.Parser
import gherkin.ast.Feature
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
import taskAnalyser.Task
import util.Util

import java.util.regex.Matcher


class GitRepository {

    String url
    String name
    String localPath
    Git git
    ObjectReader reader

    public GitRepository(String url){
        this.url = url + Util.GIT_EXTENSION
        this.name = Util.configureGitRepositoryName(url)
        this.localPath = Util.REPOSITORY_FOLDER_PATH + name
        this.git = checkoutRepository()
        this.reader = git.repository.newObjectReader()
    }

    private Git checkoutRepository(){
        Git result
        File dir = new File(localPath)
        File[] files = dir.listFiles()
        if(files){
            result = Git.open(dir)
            System.out.println("Already cloned from " + url + " to " + localPath)
        }
        else{
            result = Git.cloneRepository().setURI(url).setDirectory(dir).call()
            System.out.println("Cloned from " + url + " to " + localPath)
        }

        return result
        // Note: the call() returns an opened repository already which needs to be closed to avoid file handle leaks!
        //result.getRepository().close()
    }

    private List<DiffEntry> extractDiff(String filename, RevTree newTree, RevTree oldTree){
        DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())
        df.setRepository(git.repository)
        df.setDiffComparator(RawTextComparator.DEFAULT)
        df.setDetectRenames(true)
        if(filename!=null && !filename.isEmpty()) df.setPathFilter(PathFilter.create(filename))
        List<DiffEntry> diffs = df.scan(oldTree, newTree)
        List<DiffEntry> result = []
        diffs.each{
            it.oldPath = it.oldPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            it.newPath = it.newPath.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement(File.separator))
            result += it
        }
        return result
    }

    private showDiff(DiffEntry entry){
        ByteArrayOutputStream stream = new ByteArrayOutputStream()
        DiffFormatter formatter = new DiffFormatter(stream)
        formatter.setRepository(git.repository)
        formatter.setDetectRenames(true)
        formatter.setContext(1) //identifica quantas linhas serão mostradas antes e depois das linhas alteradas
        formatter.format(entry)
        println "DIFF: "
        println stream
        //return stream.toString().split(System.lineSeparator())
    }

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

    private RevCommit extractCommit(String sha){
        return git.log().call().find{ it.name == sha }
    }

    private TreeWalk generateTreeWalk(RevTree tree, String filename){
        TreeWalk treeWalk = new TreeWalk(git.repository)
        treeWalk.addTree(tree)
        treeWalk.setRecursive(true)
        if(filename) treeWalk.setFilter(PathFilter.create(filename))
        treeWalk.next()
        return treeWalk
    }

    private List<String> extractFileContent(ObjectId commitID, String filename) {
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
            return stream.toString().readLines()
        }
        catch(MissingObjectException exception){
            if(objectId.equals(ObjectId.zeroId()))
                println "There is no ObjectID for the commit tree. Verify the file separator used in the filename."
            return []
        }
    }

    private List<CodeChange> extractAllCodeChangesFromCommit(RevCommit commit){
        List<CodeChange> codeChanges = []
        if(commit.parentCount>0) {
            def diffs = extractDiff(null, commit.tree, commit.parents.first().tree)
            codeChanges = extractAllCodeChangeFromDiffs(diffs)
        }
        else{ //first commit
            TreeWalk tw = new TreeWalk(git.repository)
            tw.reset()
            tw.setRecursive(true)
            tw.addTree(commit.tree)
            while(tw.next()){
                codeChanges += new CodeChange(filename: tw.pathString, type:DiffEntry.ChangeType.ADD)
            }
            tw.release()
        }

        return codeChanges
    }

    private List<Commit> extractCommitsFromLogs(Iterable<RevCommit> logs){
        def commits = []
        logs.each{ c ->
            List<CodeChange> codeChanges = extractAllCodeChangesFromCommit(c)
            List<CodeChange> prodFiles = Util.getProductionFiles(codeChanges)
            List<CodeChange> testFiles = Util.getTestFiles(codeChanges)
            commits += new Commit(hash:c.name, message:c.fullMessage.replaceAll(Util.NEW_LINE_REGEX," "),
                    author:c.authorIdent.name, date:c.commitTime, productionChanges: prodFiles,
                    testChanges: testFiles, codeChanges: (prodFiles+testFiles).unique())
        }
        return commits
    }

    //* Oen problem: Deal with removed lines. */
    private List<Integer> computeChanges(ObjectId commitID, String hash, String filename){
        def changedLines = []

        BlameCommand blamer = new BlameCommand(git.repository)
        blamer.setStartCommit(commitID)
        blamer.setFilePath(filename.replaceAll(Util.FILE_SEPARATOR_REGEX, Matcher.quoteReplacement("/")))
        BlameResult blameResult = blamer.call()

        List<String> fileContent = extractFileContent(commitID, filename)
        fileContent.eachWithIndex { line, i ->
            RevCommit c = blameResult?.getSourceCommit(i)
            if(c?.name?.equals(hash)) changedLines += i
        }

        return changedLines
    }

    /* Important: DiffEntry.ChangeType.RENAME and DiffEntry.ChangeType.COPY are ignored. As consequence, if a renamed
       file also has code changes, such changes are also ignored. */
    private List<Integer> extractChangedLines(String hash, CodeChange codeChange) {
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
        Git git = new Git(git.repository)
        Iterable<RevCommit> logs = git.log().call()
        return extractCommitsFromLogs(logs).sort{ it.date }
    }

    /***
     * Searches commits from a Git repository by hash value.
     * @param hash a set of hash value
     * @return a list of commits that satisfy the search criteria.
     */
    List<Commit> searchBySha(String... hash) {
        Git git = new Git(git.repository)
        def logs = git.log().call().findAll{ it.name in hash }
        return extractCommitsFromLogs(logs).sort{ it.date }
    }


    /***
     *
     * @param commits
     * @return
     */
    def extractFeaturesFromCommit(Task task, List<Commit> commits) {
        Parser<Feature> featureParser = new Parser<>()
        task.changedGherkinFiles = []

        commits.each { commit ->
            commit.gherkinChanges.each { change ->
                change.lines = extractChangedLines(commit.hash, change)
                def path = localPath+File.separator+change.filename
                try{
                    Feature feature = featureParser.parse(new FileReader(path))
                    def changedScenarioDefinitions = feature?.scenarioDefinitions?.findAll{ it.location.line in change.lines }
                    if(changedScenarioDefinitions){
                        task.changedGherkinFiles += new GherkinFile(commitHash:commit.hash, path:path,
                                feature:feature, changedScenarioDefinitions:changedScenarioDefinitions)
                    }

                } catch(FileNotFoundException ex){
                    println "Problem to parse Gherkin file: ${ex.message}"
                }
            }
        }
    }

}

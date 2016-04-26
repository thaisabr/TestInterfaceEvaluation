package commitAnalyser

import groovy.util.logging.Slf4j
import taskAnalyser.task.UnitFile
import testCodeAnalyser.TestCodeAbstractParser

@Slf4j
class UnitTestManager {

    static UnitFile parseUnitFile(String filename, String content, List<Integer> lines, TestCodeAbstractParser parser) {
        parser.doExtractUnitTest(filename, content, lines)
    }

}

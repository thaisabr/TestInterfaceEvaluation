package br.ufpe.cin.tan.commit.change.unit

import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.test.TestCodeAbstractParser

@Slf4j
class UnitTestManager {

    static ChangedUnitTestFile parseUnitFile(String filename, String content, List<Integer> lines, TestCodeAbstractParser parser) {
        parser.doExtractUnitTest(filename, content, lines)
    }

}

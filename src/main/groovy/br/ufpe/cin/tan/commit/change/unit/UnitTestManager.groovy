package br.ufpe.cin.tan.commit.change.unit

import br.ufpe.cin.tan.test.TestCodeAbstractAnalyser
import groovy.util.logging.Slf4j

@Slf4j
class UnitTestManager {

    static ChangedUnitTestFile parseUnitFile(String filename, String content, List<Integer> lines, TestCodeAbstractAnalyser parser) {
        parser.doExtractUnitTest(filename, content, lines)
    }

}

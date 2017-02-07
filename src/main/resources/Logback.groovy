import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import util.ConstantData

import static ch.qos.logback.classic.Level.INFO

//Linux: change file name to lower case

appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss} %-5level %logger{36} %file:%line - %msg%n"
    }
}
appender("FILE", FileAppender) {
    file = "${ConstantData.DEFAULT_EVALUATION_FOLDER}${File.separator}evaluation.log"
    append = false
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss} %-5level %logger{36} %file:%line - %msg%n"
    }
}
root(INFO, ["FILE", "STDOUT"])
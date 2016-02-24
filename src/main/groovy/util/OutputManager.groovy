package util

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import groovy.util.logging.Slf4j

@Slf4j
class OutputManager {

    static List<String[]> readOutputCSV(String filename){
        List<String[]> entries = []
        try{
            CSVReader reader = new CSVReader(new FileReader(filename))
            entries = reader.readAll()
            reader.close()
        } catch (Exception ex){
            log.error ex.message
        }
        entries
    }

    static organizeResult(){
        organizeResult(Util.DEFAULT_EVALUATION_FILE)
    }

    static organizeResult(String filename){
        if(!filename || filename.empty) return
        List<String[]> entries = readOutputCSV(filename)
        if(entries.size() <= 4) return

        CSVWriter writer = new CSVWriter(new FileWriter("output${File.separator}evaluation_result_organized.csv"))

        def previousAnalysisData = entries.subList(0,3)
        previousAnalysisData.each{ data ->
            String[] value = data.findAll{!it.allWhitespace}
            writer.writeNext(value)
        }

        String[] resultHeader = entries.get(3).findAll{!it.allWhitespace} + ["Empty_ITest", "Empty_IReal"]
        entries = entries.subList(4,entries.size())

        def emptyITest = entries.findAll{ it[4].empty }
        writer.writeNext("Number of tasks with empty ITest: ${emptyITest.size()}")

        def emptyIReal = entries.findAll{ it[5].empty }
        writer.writeNext("Number of tasks with empty IReal: ${emptyIReal.size()}")

        def emptyITestAndIReal = entries.findAll{ it[4].empty && it[5].empty }
        writer.writeNext("Number of tasks with empty ITest and IReal: ${emptyITestAndIReal.size()}")

        writer.writeNext("Number of valid tasks: ${(entries-emptyITestAndIReal-emptyIReal-emptyITest).size()}")
        writer.writeNext(resultHeader)

        /*writeResult(emptyITestAndIReal, writer)
        writeResult(emptyIReal-emptyITestAndIReal, writer)
        writeResult(emptyITest-emptyITestAndIReal, writer)
        writeResult(entries-emptyITestAndIReal-emptyIReal-emptyITest, writer)*/
        writeResult(entries,writer)

        writer.close()
        log.info "The results were saved!"
    }

    static writeResult(List<String[]> entries, def writer){
        entries.each{ entry ->
            def itest = "no", ireal = "no"
            if(entry[5].empty) ireal = "yes"
            if(entry[4].empty) itest = "yes"

            String[] headers = entry + [itest, ireal]
            writer.writeNext(headers)
        }
    }

}

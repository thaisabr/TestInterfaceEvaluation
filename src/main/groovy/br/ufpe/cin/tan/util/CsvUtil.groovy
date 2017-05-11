package br.ufpe.cin.tan.util

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import groovy.util.logging.Slf4j

@Slf4j
class CsvUtil {

    private static writeCsv(String filename, List content, boolean append){
        if(content==null || content.empty) return
        def file = new File(filename)
        CSVWriter writer = new CSVWriter(new FileWriter(file, append))
        writer.writeAll(content)
        writer.close()
    }

    static List<String[]> read(String filename) {
        List<String[]> entries = []
        File file = new File(filename)
        if(!file.exists()) return entries
        try {
            CSVReader reader = new CSVReader(new FileReader(file))
            entries = reader.readAll()
            reader.close()
        } catch (Exception ex) {
            log.error "Error while reading CSV file ('${filename}')"
            log.error ex.message
            ex.stackTrace.each{ log.error it.toString() }
        }
        entries
    }

    static write(String filename, List content){
        writeCsv(filename, content, false)
    }

    static append(String filename, List<String[]> content){
        writeCsv(filename, content, true)
    }
}

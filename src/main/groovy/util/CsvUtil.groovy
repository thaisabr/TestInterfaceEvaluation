package util

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import taskAnalyser.TaskAnalyser


class CsvUtil {

    private static writeCsv(String filename, List content, boolean append){
        def file = new File(filename)
        CSVWriter writer = new CSVWriter(new FileWriter(file, append))
        writer.writeAll(content)
        writer.close()
    }

    static List<String[]> read(String filename) {
        List<String[]> entries = []
        try {
            CSVReader reader = new CSVReader(new FileReader(filename))
            entries = reader.readAll()
            reader.close()
        } catch (Exception ex) {
            TaskAnalyser.log.error ex.message
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

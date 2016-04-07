package similarityAnalyser.text

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.Directory
import org.apache.lucene.store.RAMDirectory
import taskAnalyser.GherkinFile


class IndexManager {

    StandardAnalyzer analyzer
    Directory indexDirectory
    IndexWriter writer

    private static final FieldType TYPE_STORED = new FieldType()

    static {
        TYPE_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        TYPE_STORED.tokenized = true
        TYPE_STORED.stored = true
        TYPE_STORED.storeTermVectors = true
    }


    public IndexManager() {
        analyzer = new StandardAnalyzer()
        indexDirectory = new RAMDirectory() //Creates a memory directory; if necessary, it is possible to use an index database
        writer = new IndexWriter(indexDirectory, new IndexWriterConfig(analyzer)) //Create a file
    }

    private addDoc(String text){
        Document doc = new Document()
        doc.add(new Field("content", text, TYPE_STORED))
        writer.addDocument(doc)
    }

    def index(List<GherkinFile> gherkinFiles) {
        def text = ""
        def steps = gherkinFiles*.changedScenarioDefinitions*.steps?.flatten()
        steps.each {
            text += "${it.keyword}: ${it.text}\n" //is really necessary to consider keyword?
        }
        addDoc(text)
        writer.commit()
    }

    def index(String text) {
        addDoc(text)
        writer.commit()
    }

}

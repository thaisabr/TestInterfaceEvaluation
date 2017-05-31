package br.ufpe.cin.tan.similarity.text

import gherkin.GherkinDialectProvider
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.util.CharArraySet
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.index.IndexOptions
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.Directory
import org.apache.lucene.store.RAMDirectory
import br.ufpe.cin.tan.commit.change.gherkin.ChangedGherkinFile


class IndexManager {

    StandardAnalyzer analyzer
    Directory indexDirectory
    IndexWriter writer
    CharArraySet stopwords
    FieldType fieldType

    IndexManager() {
        configureStopWords(new GherkinDialectProvider().defaultDialect)
        configureFieldType()
        analyzer = new StandardAnalyzer(stopwords)
        indexDirectory = new RAMDirectory()
        //Creates a memory directory; if necessary, it is possible to use an index database
        writer = new IndexWriter(indexDirectory, new IndexWriterConfig(analyzer)) //Create a file
    }

    def index(List<ChangedGherkinFile> gherkinFiles) {
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

    private configureFieldType() {
        fieldType = new FieldType()
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        fieldType.tokenized = true
        fieldType.stored = true
        fieldType.storeTermVectors = true
    }

    private configureStopWords(def dialect) {
        stopwords = new CharArraySet(200, true)
        def words = []
        words += dialect.backgroundKeywords.unique()*.trim()
        words += dialect.examplesKeywords.unique()*.trim()
        words += dialect.featureKeywords.unique()*.trim()
        words += dialect.scenarioKeywords.unique()*.trim()
        words += dialect.scenarioOutlineKeywords.unique()*.trim()
        words += dialect.stepKeywords.unique()*.trim()
        stopwords.addAll(words)
    }

    private addDoc(String text) {
        Document doc = new Document()
        doc.add(new Field("content", text, fieldType))
        writer.addDocument(doc)
    }

}

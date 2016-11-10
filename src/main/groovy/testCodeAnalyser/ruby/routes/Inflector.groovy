package testCodeAnalyser.ruby.routes

import org.jruby.embed.ScriptingContainer
import util.ConstantData
import util.Util

class Inflector {

    ScriptingContainer container
    Object receiver

    Inflector() {
        container = new ScriptingContainer()
        container.loadPaths.add(Util.GEMS_PATH)
        container.loadPaths.add(Util.GEM_INFLECTOR)
        container.loadPaths.add(Util.GEM_I18N)

        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(ConstantData.INFLECTOR_FILE)
        receiver = container.runScriptlet(is, ConstantData.INFLECTOR_FILE)
    }

    String pluralize(String word) {
        container.callMethod(receiver, "plural", word)
    }

    String singularize(String word) {
        container.callMethod(receiver, "singular", word)
    }

}

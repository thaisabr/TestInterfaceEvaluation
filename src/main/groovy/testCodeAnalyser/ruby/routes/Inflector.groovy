package testCodeAnalyser.ruby.routes

import org.jruby.embed.ScriptingContainer
import util.Util
import util.ruby.RubyUtil


class Inflector {

    ScriptingContainer container
    Object receiver

    Inflector() {
        container = new ScriptingContainer()
        container.loadPaths.add(Util.GEMS_PATH)
        container.loadPaths.add(Util.ACTIVESUPPORT_INFLECTOR_PATH)
        container.loadPaths.add(Util.I18N_PATH)

        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(RubyUtil.INFLECTOR_FILE)
        receiver = container.runScriptlet(is, RubyUtil.INFLECTOR_FILE)
    }

    String pluralize(String word) {
        container.callMethod(receiver, "plural", word)
    }

    String singularize(String word) {
        container.callMethod(receiver, "singular", word)
    }

}

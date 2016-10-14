package testCodeAnalyser.ruby.routes

import org.jruby.embed.PathType
import org.jruby.embed.ScriptingContainer
import util.Util


class Inflector {

    ScriptingContainer container
    Object receiver

    Inflector() {
        container = new ScriptingContainer()
        container.loadPaths.add(Util.GEMS_PATH)
        container.loadPaths.add(Util.ACTIVESUPPORT_INFLECTOR_PATH)
        container.loadPaths.add(Util.I18N_PATH)
        receiver = container.runScriptlet(PathType.ABSOLUTE, "inflector.rb")
    }

    String pluralize(String word) {
        container.callMethod(receiver, "plural", word)
    }

    String singularize(String word) {
        container.callMethod(receiver, "singular", word)
    }

}

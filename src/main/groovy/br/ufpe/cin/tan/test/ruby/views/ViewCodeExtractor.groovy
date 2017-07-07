package br.ufpe.cin.tan.test.ruby.views

import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.util.ruby.RubyConstantData
import org.jruby.embed.ScriptingContainer

class ViewCodeExtractor {

    ScriptingContainer container
    Object receiver

    ViewCodeExtractor() {
        container = new ScriptingContainer()
        container.loadPaths.add(Util.GEMS_PATH)
        container.loadPaths.add(Util.GEM_INFLECTOR)
        container.loadPaths.add(Util.GEM_I18N)
        container.loadPaths.add(Util.GEM_PARSER)
        container.loadPaths.add(Util.GEM_AST)

        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(RubyConstantData.VIEW_ANALYSER_FILE)
        receiver = container.runScriptlet(is, RubyConstantData.VIEW_ANALYSER_FILE)
    }

    String extractCode(String path) {
        container.callMethod(receiver, "extract_production_code", path)
    }

}

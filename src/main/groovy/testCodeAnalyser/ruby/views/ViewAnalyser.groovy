package testCodeAnalyser.ruby.views

import groovy.util.logging.Slf4j
import org.jruby.embed.ScriptingContainer
import util.Util
import util.ruby.RubyConstantData

@Slf4j
class ViewAnalyser {

    ScriptingContainer container
    Object receiver

    ViewAnalyser() {
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

    String extractCode(String path){
        log.info "Extract code from view: $path"
        container.callMethod(receiver, "extract_production_code", path)
    }

}

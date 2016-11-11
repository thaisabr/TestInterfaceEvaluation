package testCodeAnalyser.ruby.views

import groovy.util.logging.Slf4j
import org.jruby.embed.ScriptingContainer
import util.Util
import util.ruby.RubyConstantData

@Slf4j
class ErbAnalyser {

    ScriptingContainer container
    Object receiver

    ErbAnalyser() {
        container = new ScriptingContainer()
        container.loadPaths.add(Util.GEMS_PATH)
        container.loadPaths.add(Util.GEM_INFLECTOR)
        container.loadPaths.add(Util.GEM_I18N)
        container.loadPaths.add(Util.GEM_PARSER)
        container.loadPaths.add(Util.GEM_AST)

        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(RubyConstantData.ERB_ANALYSER_FILE)
        receiver = container.runScriptlet(is, RubyConstantData.ERB_ANALYSER_FILE)
    }

    String extractCode(String path){
        log.info "Extract ERBS from: $path"
        container.callMethod(receiver, "grab_controllers", path)
    }

}

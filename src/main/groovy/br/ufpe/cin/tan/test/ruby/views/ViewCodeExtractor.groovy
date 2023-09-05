package br.ufpe.cin.tan.test.ruby.views

import br.ufpe.cin.tan.util.Util
import br.ufpe.cin.tan.util.ruby.RubyConstantData
import groovy.util.logging.Slf4j
import org.jruby.embed.ScriptingContainer

@Slf4j
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

        String classpath = System.getProperty("java.class.path")
        log.info "CLASSPATH: " + classpath

        ClassLoader loader = Thread.currentThread().getContextClassLoader()
        InputStream is = loader.getResourceAsStream(RubyConstantData.VIEW_ANALYSER_FILE)
        //receiver = container.runScriptlet(is, RubyConstantData.VIEW_ANALYSER_FILE)
        String code = """
            require 'rubygems'
            require 'lib/task_analyser'
            
            def extract_production_code(file_path)
              TaskAnalyser.new.grab_controllers(file_path)
            end"""
        receiver = container.runScriptlet(code)
    }

    String extractCode(String path) {
        container.callMethod(receiver, "extract_production_code", path)
    }

}

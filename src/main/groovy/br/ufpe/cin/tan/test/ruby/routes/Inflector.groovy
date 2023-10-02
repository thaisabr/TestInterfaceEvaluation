package br.ufpe.cin.tan.test.ruby.routes

import br.ufpe.cin.tan.util.ConstantData
import br.ufpe.cin.tan.util.Util
import org.jruby.embed.ScriptingContainer

class Inflector {

    ScriptingContainer container
    Object receiver

    Inflector() {
        container = new ScriptingContainer()
        container.loadPaths.add(Util.GEMS_PATH)
        container.loadPaths.add(Util.GEM_INFLECTOR)
        container.loadPaths.add(Util.GEM_I18N)

        String code = """
            require 'rubygems'
            require 'active_support/inflector'
            
            def plural(text)
              text.pluralize
            end
            
            def singular(text)
              text.singularize
            end"""
        receiver = container.runScriptlet(code)
    }

    String pluralize(String word) {
        container.callMethod(receiver, "plural", word)
    }

    String singularize(String word) {
        container.callMethod(receiver, "singular", word)
    }

}

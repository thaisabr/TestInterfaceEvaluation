#Util class that modifies string in order to correctly name them after their respective method/controller call
require 'active_support/inflector'

class Transform_into

  def self.var_into_controller(var)
    if var != ''
      if var[-1] == 'y'
        var = "#{var[0...-1]}ies_controller"
      else
        var = "#{var}s_controller"
      end
    end
  end

  def self.var_into_method(var)
    if var.to_s[0] == '@'
     "#{(var.to_s)[1..-1]}_path"
    else
      var = "#{var}_path"
    end
  end

  def self.singular(var)
    var.singularize
  end

  def self.name_with_extension(var, language)
    if !var.to_s.include?(".html.#{language}") || !var.to_s.include?(".#{language}")
      var = var << ".html.#{language}"
      if var !~ /\//
        var = '_' << var
      else
        var = var.gsub(/(\/)(?!.*\/)/, "/_");
      end
      var
    end
  end

  def self.plural_for_ivar(var,ivar)
    if var.to_s.eql? ivar.to_s
      var.to_s.pluralize
    else
      var
    end
  end
end
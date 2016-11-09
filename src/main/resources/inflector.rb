require 'rubygems'
require 'active_support/inflector'

def plural(text)
  text.pluralize
end

def singular(text)
  text.singularize
end
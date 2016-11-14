require 'rubygems'
require 'lib/erb_dependencies'

def extract_production_code(file_path)
  Erb_dependencies.new.grab_controllers(file_path)
end
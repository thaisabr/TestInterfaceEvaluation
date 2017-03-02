require 'rubygems'
require 'lib/task_analyser'

def extract_production_code(file_path)
  TaskAnalyser.new.grab_controllers(file_path)
end
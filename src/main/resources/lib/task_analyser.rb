require_relative 'ErbFileAnalyser/Erb_controller_extractor'
require_relative '../lib/HamlFileAnalyser/Haml_controller_extractor'
class TaskAnalyser

  def grab_controllers(file_path)
    extension = get_file_extension(file_path)
    case extension.downcase
      when 'erb'
        begin
          return ErbControllerExtractor.new.erb_controller_extractor(file_path)
        rescue
          raise "Syntax exception : the file #{file_path} is either wrong or this tool cannot analise it"
        end
      when 'haml'
        begin
        return HamlControllerExtractor.new.haml_controller_extractor(file_path)
        rescue
          raise "Syntax exception : the file #{file_path} is either wrong or this tool cannot analise it"
        end
      else
        raise 'This file extension is not currently supported'
    end
  end

  def get_file_extension(file_path)
    extension_regex = /(?<=\.).*/
    extension = (file_path.scan extension_regex)[0]
    while extension.to_s.include?('.')
      extension = extension_regex.match extension.to_s
    end
    extension.to_s
  end

end
#Call erb_tags_remover on the input file, then calls ruby_parser to parse the code into ast, calls method_controller_visitor
#to get all calls to possible controllers and output them in a file

class ErbControllerExtractor

  require_relative '../ErbFileAnalyser/Erb_tags_remover'
  require_relative '../../lib/Analyser/find_controller_calls'
  require_relative '../Util/file_manager'
  require_relative '../Util/ruby_parser'
  require_relative '../Util/output_model'
  require 'ast/node'

  def erb_controller_extractor(file_path)
    output_value = ""
    file_manager = File_manager.new
    file_text = file_manager.read_file(file_path)
    code = ErbTagsRemover.new.remove_erb_tags(file_text)
    parsed_code = Ruby_parser.new.parse_code(code)
    output_array = Find_controller_calls.new([],'','','erb').find_controllers(parsed_code)
    output_array.each do |output|
      if !output.name.nil?
        output = check_and_write_file_path(output, file_path)
      end
      if !output.name.nil?
        output_value = output_value + "[name: '#{output.name}', receiver: '#{output.receiver}', label: '#{output.label}']\n"
      end
    end
    output_value
  end

  def check_and_write_file_path(output,file_path)
    output = remove_symbols(output)
    if !output.name.to_s.include?('/') && output.name.to_s.include?('.erb')
      if output.name[0] == '/' || output.name[0] == "\\"
        output.name = output.name[1..-1]
      end
      output.name = "#{/app.*\/.*\/|app.*\\.*\\/.match(file_path).to_s}#{output.name}"
    else
      if !output.name.to_s.include?('app/views') && output.name.to_s.include?('.erb')
        if output.name[0] == '/' || output.name[0] == "\\"
          output.name = output.name[1..-1]
        end
        output.name = "app/views/#{output.name}"
      end
    end
    output
  end

  def remove_symbols(output)
    if output.name[1] == '@'
      output.name = "#{output.name[0]}#{output.name[2..-1]}"
    elsif output.name[0] == '@'
      output.name = output.name[1..-1]
    end
    output
  end


end
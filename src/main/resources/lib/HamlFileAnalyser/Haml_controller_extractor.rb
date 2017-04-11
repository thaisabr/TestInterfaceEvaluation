#Call haml_end_adder on the input file, then calls haml_parser to parse the code into ruby, parses the ruby code with ruby_parser
# then calls method_controller_visitor to get all calls to possible controllers and outputs them in a string
class HamlControllerExtractor

  require_relative '../../lib/HamlFileAnalyser/Haml_end_adder'
  require_relative '../../lib/Analyser/find_controller_calls'
  require_relative '../../lib/HamlFileAnalyser/Haml_parser'
  require_relative '../Util/file_manager'
  require_relative '../Util/output_model'
  require_relative '../../lib/Util/ruby_parser'

  def haml_controller_extractor(file_path)
    output_value = ""
    text = Haml_end_adder.new([]).add_ends(file_path)
    code = Haml_parser.new.parse(text)
    parsed_code = Ruby_parser.new.parse_code(code)
    output_array = Find_controller_calls.new([],'','','haml').find_controllers(parsed_code)
    output_array.each do |output|
      if !output.name.nil?
        output = check_and_write_file_path(output,file_path)
      end
      if !output.name.nil?
        output_value = output_value + "[name: '#{output.name}', receiver: '#{output.receiver}', label: '#{output.label}', arguments: '#{output.number_of_arguments}']\n"
      end
    end
    output_value
  end

  def check_and_write_file_path(output,file_path)
    output = remove_symbols(output)
    if !output.name.to_s.include?('/') && output.name.to_s.include?('.haml')
      if output.name[0] == '/' || output.name[0] == "\\"
        output.name = output.name[1..-1]
      end
      output.name = "#{/app.*\/.*\/|app.*\\.*\\/.match(file_path).to_s}#{output.name}"
    else
      if !output.name.to_s.include?('app/views') && output.name.to_s.include?('.haml')
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
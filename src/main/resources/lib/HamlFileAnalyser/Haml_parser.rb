#Takes Haml codes with end tags added to the end of each block and transforms them into pure ruby
require_relative '../../lib/Util/ruby_parser'
class Haml_parser

  def parse(text)
    text = remove_commented_lines(text)
    all_tagged_chunks = text.scan(/(?<=\=)(.*)|(?<=\-)(.*)/)
    helper_array = []
    ruby_parser = Ruby_parser.new
    index = 0
    all_tagged_chunks.each do |tagged_chunks|
      tagged_chunks.each do |tagged_chunk|
        if tagged_chunk.class == String
          if /[A-Za-z0-9]| |@|\[/ === tagged_chunk[0]
            possible_code = ''
            test_code = tagged_chunk
            test_code = check_special_conditionals(tagged_chunk, test_code)
            begin
              possible_code = ruby_parser.parse_code(test_code)
            rescue
              possible_code = ''
            end
            if possible_code.class == Parser::AST::Node || (possible_code.class == String && possible_code != '')
              helper_array.push(tagged_chunk)
            end
          end
        end
        index += 1
      end
    end
    helper_array.join("\n")
  end

  def check_special_conditionals(tagged_chunk, test_code)
    conditionals_regex = /(?=( do$| do |if |if\(|begin$|case|unless))|(?=(else|elsif|rescue$|ensure|end$|when))/
    if conditionals_regex === tagged_chunk
      special_conditionals = [/end$/,/else$/,/elsif/,/rescue$/,/ensure/,/when/,/case/]
      special_conditionals.each do |special_conditional|
        if special_conditional === tagged_chunk
          test_code = 'a = true'
          break
        else
          test_code = "#{tagged_chunk}" + "\n end"
        end
      end
    end
    test_code
  end

  def remove_commented_lines(text)
    commented_line_of_code = /\/( *)\- *(.*)|\/( *)\= *(.*)|( +\/\/.*)/
    text_without_comments = text
    text.each_line do |line|
      if commented_line_of_code.match(line)
        text_without_comments.slice!(line)
      end
    end
    text_without_comments
  end
end
#Parse ruby code into ast

require 'parser/current'
require 'ast'

class Ruby_parser

  Parser::Builders::Default.emit_lambda = true # opt-in to most recent AST format
  def parse_code(code)
    begin
      p Parser::CurrentRuby.parse(code)
    rescue Exception => e
      e.message
    end
  end
end
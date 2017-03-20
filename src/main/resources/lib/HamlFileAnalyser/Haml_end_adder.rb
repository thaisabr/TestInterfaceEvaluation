#adds end to each needed loop, if, etc according to it's indentation
class Haml_end_adder


  def initialize(array)
    $lines_array = []
    $lines_that_needs_end = []
    $end_indentation_values = []
  end

  def add_ends(file_path)
    fill_array(file_path)
    has_javascript_on_code = false
    end_of_javascript_block = 0
    File.foreach(file_path).with_index do |line, line_num|
      response_array = check_for_javascript_code(end_of_javascript_block, line_num, line, has_javascript_on_code)
      matches_js_if = response_array[0]
      has_javascript_on_code = response_array[1]
      line = response_array[2]
      comment = check_regex_match('commentary', line)
      not_a_comment = comment.nil?
      matches_do_inside_quote_marks = check_regex_match('do_inside_string_block', line)
      if not_a_comment && !matches_js_if && !matches_do_inside_quote_marks
        if check_regex_match('has_need_for_end', line) && check_regex_match('haml_conditional', line) &&
            (!check_regex_match('special_conditions', line) || check_regex_match('ignore_special_condition', line))
          end_of_javascript_block = check_and_save_end_position(line, line_num, has_javascript_on_code, end_of_javascript_block)
        end
      end
    end
    put_end_on_lines($lines_that_needs_end, $end_indentation_values)
    $lines_array.join("\n")
  end

  def check_and_save_end_position(line, line_num, has_javascript_on_code, end_of_javascript_block)
    indentation_value = check_indentation_value(line)
    next_indentations_values = look_into_next_lines($lines_array,line_num)
    position_plus_taken_care = find_end_position(next_indentations_values,line_num, indentation_value)
    end_was_taken_care = position_plus_taken_care[1]
    if has_javascript_on_code
      if end_was_taken_care
        end_of_javascript_block = position_plus_taken_care[0]
      else
        end_of_javascript_block = $lines_array.size
      end
    end
    if !end_was_taken_care
      $end_indentation_values.push(indentation_value)
      $lines_that_needs_end.push($lines_array.size)
    end
    end_of_javascript_block
  end

  def find_end_position(next_indentations_values,line_num, indentation_value)
    answer_array = []
    past_line_has_mid_block = false
    past_line_indentation_value = 100
    end_was_taken_care = false
    commentary = /(?=\/).*/
    mid_block_keywords = /(?=(else|elsif| rescue| ensure| end| when))/
    array_index = 1
    next_indentations_values.each do |next_indentation_value|
      actual_line = line_num + array_index
      has_mid_block = mid_block_keywords.match($lines_array[actual_line])
      line_break = '
      '
      linejump = /^(?:\n)|\A\s*\z/
      if $lines_array[actual_line] != line_break && !commentary.match($lines_array[actual_line]) && !linejump.match($lines_array[actual_line])
        if next_indentation_value <= indentation_value && !has_mid_block
          if past_line_has_mid_block && past_line_indentation_value < indentation_value
            actual_line -= 1
          end
          answer_array[0] = actual_line
          $lines_that_needs_end.push(actual_line)
          $end_indentation_values.push(indentation_value)
          end_was_taken_care = true
          break
        end
      end
      past_line_has_mid_block = has_mid_block
      past_line_indentation_value = next_indentation_value
      array_index += 1
    end
    answer_array[1] = end_was_taken_care
    answer_array
  end

  def put_end_on_lines(lines_number, indentation_values)
    helper_array = $lines_array
    array_index = 0
    lines_number_plus_indentation_values = order_lines_array_for_insertion(lines_number, indentation_values)
    lines_number = lines_number_plus_indentation_values[0]
    indentation_values = lines_number_plus_indentation_values[1]
    lines_number.each do |line_number|
      indented_end = write_indented_end(indentation_values[array_index])
      helper_array.insert(line_number,indented_end)
      array_index += 1
    end
    $lines_array = helper_array
  end

  def write_indented_end(indentation_value)
    response = '- end'
    while indentation_value != 0
      response = ' ' + response
      indentation_value -= 1
    end
    response
  end

  def change_line_to_conditional(indentation)
    write_indented_conditional(indentation)
  end

  def write_indented_conditional(indentation)
    response = '- if true'
    indentation_value = indentation
    while indentation_value != 0
      response = ' ' + response
      indentation_value -= 1
    end
    response
  end

  def look_into_next_lines(lines_array, line_num)
    next_lines_indentation_values = []
    array_index = 0
    iteration  = 0
    lines_array.each do |line|
      if iteration > line_num
        next_lines_indentation_values[array_index] = check_indentation_value(line)
        array_index += 1
      end
      iteration += 1
    end
    next_lines_indentation_values
  end

  def fill_array(file_path)
    File.foreach(file_path).with_index do |line, line_num|
      $lines_array[line_num] = line
      $lines_array
    end
  end

  def check_indentation_value(line)
    indentation = 0
    line.each_char { |char|
      if char != ' '
        break
      else
        indentation = indentation + 1
      end
    }
    indentation
  end

  def order_lines_array_for_insertion(lines_array, indentation_array)
    helper_lines_array = []
    length = lines_array.length
    changes_array = []
    array_index = 0
    while length > 0
      max_value_index = lines_array.each_with_index.max[1]
      helper_lines_array.push(lines_array[max_value_index])
      lines_array.delete_at(max_value_index)
      changes_array[array_index] = max_value_index
      length -= 1
      array_index += 1
    end
    [helper_lines_array, order_indentation_array_for_insertion(changes_array, indentation_array)]
  end

  def order_indentation_array_for_insertion(changes_array, indentation_array)
    helper_indentation_array = []
    length = changes_array.length
    index = 0
    while length > 0
      change_position = changes_array[index]
      helper_indentation_array.push(indentation_array[change_position])
      indentation_array.delete_at(change_position)
      index += 1
      length -= 1
    end
    helper_indentation_array
  end

  def check_for_same_line_collision(lines_array, indentation_array)
    outer_index = 0
    helper_lines_array = lines_array
    lines_array.each do |line|
      if lines_array.count(line) > 1
        inner_index = 0
        lines_array.each do |next_line|
          if next_line == line
            if indentation_array[inner_index] < indentation_array[outer_index]
              helper_lines_array[inner_index] += 1
            end
          end
          inner_index += 1
        end
      end
      outer_index += 1
    end
    helper_lines_array
  end

  def check_for_javascript_code(end_of_javascript_block, line_num, line, has_javascript_on_code)
    if end_of_javascript_block <= line_num
      has_javascript_on_code = false
    else
      line  = ''
      $lines_array[line_num] = line
    end
    if check_regex_match('javascript_tag',line)
      line = ''
      $lines_array[line_num] = ''
    elsif check_regex_match('javascript_keyword', line)
      indentation = check_indentation_value(line)
      line = change_line_to_conditional(indentation)
      $lines_array[line_num] = line
      has_javascript_on_code = true
    end
    matches_js_if = false
    if check_regex_match('javascript_if', line) && has_javascript_on_code
      matches_js_if = true
    end
    [matches_js_if, has_javascript_on_code, line]
  end

  def check_regex_match(regex_name, string)
    response = false
    has_need_for_end = /(?=( do$| do +| if |-if | if\(|-if\(| begin| case| unless |-unless | unless\())/
    haml_conditional = /(?<=\-)([A-Za-z0-9]| |@|\[)|(?<=\=)(([A-Za-z0-9]| |@|\[).* do)/
    special_conditions = /(?<=\= )(.*) if |^ +- \S.* if |\S+ *-.* if |^ *%.* if |(?<=\- )next if |(?<=\= )(.*)unless|(?<=\- )(.+)unless|".*"(.+)unless| *(=|-) ([A-Za-r0-9]|").*= .* do |value: *\(*.* if.*|^ *:.*/
    ignore_special_condition = /^ *=.* do$|^ *= f\..* do \|f\||( *\- | *= ).* do$/
    javascript_if = /(?<=if)( *)\(.*\)\{|(?<=if)( *)\(.*\)/
    javascript_keyword = /:javascript|:coffeescript|:ruby/
    javascript_tag = /javascript_include_tag/
    commentary = /\/( *)\- *(.*)(?= \/).*| *\/ +.*|(^ +#.*|^ *- *#.*)|(^ *\/\/ *- .*)|^ *\/- *.*/
    do_inside_string_block = /".* do"|".* do +.*?"/

    case regex_name
      when 'has_need_for_end'
        response = has_need_for_end.match string
      when 'haml_conditional'
        response = haml_conditional.match string
      when 'special_conditions'
        response = special_conditions.match string
      when 'ignore_special_condition'
        response = ignore_special_condition.match string
      when 'javascript_if'
        response = javascript_if.match string
      when 'javascript_keyword'
        response = javascript_keyword.match string
      when 'javascript_tag'
        response = javascript_tag.match string
      when 'commentary'
        response = commentary.match string
      when 'do_inside_string_block'
        response = do_inside_string_block.match string
      else
        response
    end
    response
  end


end
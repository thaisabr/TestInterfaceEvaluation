#Visits parse tree looking for calls to controllers, when found, insert them on an array
class Find_controller_calls

  require 'ast/node'
  require_relative '../Util/transform_into'

  def initialize(array, instanceVar, localVar, language)
    $output_array = array
    $instance_variable = instanceVar
    $lvar_derived_from_ivar = localVar
    $button_label = ''
    $submit_name = ''
    $language = language
  end

  $link_to = :link_to
  $str = :str
  $ivar = :ivar
  $lvar = :lvar
  $submit = :submit
  $form_for = :form_for
  $each = :each
  $label = :label
  $hash = :hash
  $confirm = :confirm
  $block = :block
  $send = :send
  $render = :render
  $form_tag = :form_tag
  $pair = :pair
  $empty_array = :[]
  $semantic_form_for = :semantic_form_for
  $buttons = :buttons
  $array = :array
  $action = :action
  $map = :map

  def find_controllers(code)
    if is_still_a_node(code)
      look_for_instance_variable(code)
      look_for_loop_argument(code)
      look_for_button_methods(code)
      code.children.each do |code_children|
        if is_still_a_node(code_children)
          look_for_link_to_calls(code_children)
          look_for_form_for_action(code_children,$instance_variable)
          look_for_submit_calls(code_children, $instance_variable)
          look_for_auto_gen_methods(code_children,$instance_variable,$lvar_derived_from_ivar)
          look_for_render_call(code_children,$instance_variable)
          look_for_form_tag_call(code_children, $instance_variable)
          look_for_semantic_form_for(code_children,$button_label)
          find_controllers(code_children)
        end
      end
    end
    $output_array
  end

  def insert_outputs_on_array(name, receiver, label,number_of_arguments)
    output_model = Output_model.new
    output_model.name = name
    output_model.receiver = receiver
    output_model.label = label
    output_model.number_of_arguments = number_of_arguments
    $output_array.push output_model
  end

end

def look_for_link_to_calls(code)
  controller_name = ''
  method_name = code.children[1]
  if method_name == $link_to
    if is_still_a_node(code.children[2])
      if code.children[2].type == $str && code.children[2].children[0] == '#'
        controller_name = ''
      else
        found_confirm_call = look_for_confirm_call(code)
        if !found_confirm_call && !code.children[3].nil?
          method_argument_type = code.children[3].type
          if method_argument_type == $ivar
            method_argument_value = code.children[3].children[0]
            is_instance_or_method = ( method_argument_value.to_s[0] == '@' || method_argument_value.to_s.include?('_path'))
            if method_argument_value.to_s.size > 1 && (is_instance_or_method || check_if_eq_instance_variable(method_argument_value))
              if method_argument_value.to_s.include?('_path')
                insert_outputs_on_array(method_argument_value, "",'',count_method_arguments(code.children[3]))
              else
                insert_outputs_on_array(Transform_into.var_into_method(method_argument_value), "",'',count_method_arguments(code.children[3]))
              end
            end
          else
            father_node = ''
            method_inside_link_to_has_params = code.children[3].children[1].nil?
            if is_still_a_node(code.children[3].children[0])
              has_callee = code.children[3].children[0].children[1]
            end
            if !method_inside_link_to_has_params && has_callee.class != Symbol
              method_inside_link_to_param = code.children[3].children[1]
              father_node = code.children[3]
              if is_still_a_node(method_inside_link_to_param)
                method_inside_link_to_param = method_inside_link_to_param.children[1]
                father_node = father_node.children[1]
              end
              if is_still_a_node(method_inside_link_to_param)
                if method_inside_link_to_param.type == $pair
                  method_inside_link_to_param = code.children[3].children[1].children[1].children[0]
                  father_node = code.children[3].children[1].children[1]
                  controller_name = code.children[3].children[0].children[1].children[0]
                  if is_still_a_node(method_inside_link_to_param)
                    method_inside_link_to_param = method_inside_link_to_param.children[0]
                    father_node = father_node.children[0]
                  end
                 if is_still_a_node(controller_name)
                    controller_name = ''
                  end
                end
              else
                is_instance_or_method = !method_inside_link_to_param.to_s[0] == '@' || !method_inside_link_to_param.to_s.include?('_path')
                if is_instance_or_method && !method_inside_link_to_param.to_s.include?('_')
                  if is_still_a_node(code.children[3].children[0])
                    if code.children[3].children[0].type == $lvar
                      method_inside_link_to_param = ''
                      father_node = ''
                    else
                      if method_inside_link_to_param.to_s.size > 1
                        method_inside_link_to_param = Transform_into.name_with_extension(method_inside_link_to_param.to_s, $language)
                      end
                    end
                  end
                end
              end
              if !is_still_a_node(method_inside_link_to_param) && method_inside_link_to_param.to_s != ''
                insert_outputs_on_array(method_inside_link_to_param, Transform_into.singular(controller_name.to_s),'',count_method_arguments(father_node))
              end
            end
          end
        end
      end
    end
  end
end

def look_for_submit_calls(code, instance_variable)
  father_node = ''
  method_argument = ''
  method_argument_type = ''
  method_name = code.children[1]
  if method_name == $submit
    if is_still_a_node(code.children[2])
      method_argument_type = code.children[2].type
    end
    if method_argument_type == $str
      method_argument = code.children[2].children[0]
      father_node = code.children[2]
    else
      if method_argument_type == $send
        if code.children[3].nil?
          if is_still_a_node code.children[2].children[0]
            method_argument = code.children[2].children[0].children[0]
            father_node = code.children[2].children[0]
          end
        else
          method_argument = code.children[3].children[0].children[1].children[0]
          father_node = code.children[3].children[0].children[1]
        end
      end
    end
    if method_argument != ''
      if $submit_name != ''
        insert_outputs_on_array(Transform_into.var_into_method($submit_name),'' ,"#{method_argument}".downcase,count_method_arguments(father_node))
      else
        if !is_still_a_node instance_variable
          insert_outputs_on_array("#{method_argument}".downcase,Transform_into.var_into_controller(instance_variable),'',count_method_arguments(father_node))
        end
      end
    end
  end
end

def look_for_auto_gen_methods(code, instance_variable,lvar_derived_from_ivar)
  father_node = ''
  method_name = code.children[1]
  if method_name == $label
    if is_still_a_node(code.children[2])
      method_argument_value = code.children[2].children[0]
      father_node = code.children[2]
      if method_argument_value.is_a?(Parser::AST::Node)
        if is_still_a_node code.children[2].children[0].children[0]
          if !code.children[2].children[0].children[0].children[2].nil?
            method_argument_value = code.children[2].children[0].children[0].children[2].children[0]
            father_node = code.children[2].children[0].children[0].children[2]
          end
        end
      end
    end
    if !method_argument_value.nil?
      if instance_variable != '' || (method_argument_value.to_s.include?('/') || method_argument_value.to_s.include?('_path'))
        insert_outputs_on_array(method_argument_value, instance_variable,'',count_method_arguments(father_node))
      end
    end
  end
  if is_still_a_node(code.children[0])
    variable_type = code.children[0].type
    variable_calls_method = !code.children[1].nil?
    if variable_type == $lvar && variable_calls_method
      method_argument = code.children[0].children[0]
      if method_argument == lvar_derived_from_ivar
        if method_name != $empty_array && method_name.class != Parser::AST::Node
          if instance_variable != '' || ((method_name.to_s).include?('/') || (method_name.to_s).include?('_path'))
            insert_outputs_on_array(method_name, instance_variable,'','0')
          end
        end
      end
    end
  end
end

def look_for_loop_argument(code)
  if $lvar_derived_from_ivar == ""
    code.children.each do |code_children|
      if is_still_a_node(code_children)
        if code_children.type == $block
          if code_children.children[0].type == $send
            loop_type = code_children.children[0].children[1]
            if loop_type == $each
              loop_argument_variable = code_children.children[1].children[0].children[0]
              $lvar_derived_from_ivar = loop_argument_variable
            end
          end
        end
        look_for_loop_argument(code_children)
      end
    end
  else
    $lvar_derived_from_ivar
  end
end

def look_for_instance_variable(code)
  if $instance_variable == ""
    code.children.each do |code_children|
      if is_still_a_node(code_children)
        loop_type = code_children.children[1]
        if loop_type == $form_for && code_children.children[2].type == $ivar
          loop_variable_value = code_children.children[2].children[0]
          $instance_variable = loop_variable_value
        elsif  loop_type == $each
          loop_variable_value = code_children.children[0].children[0]
          if loop_variable_value.to_s[0] == '@'
            $instance_variable = loop_variable_value
          end
        elsif loop_type == $map
          if is_still_a_node(code_children.children[0])
            if is_still_a_node(code_children.children[0].children[1])
              if code_children.children[0].children[1].children[0].children[0].to_s[0] == '@'
                $instance_variable = code_children.children[0].children[1].children[0].children[0]
              end
            else
              if code_children.children[0].children[1].to_s == '@'
                $instance_variable = code_children.children[0].children[1]
              end
            end
          end
        end
        look_for_instance_variable(code_children)
      end
    end
  else
    $instance_variable = Transform_into.singular("#{$instance_variable}")
  end
end

def look_for_confirm_call(code)
  father_node = ''
  has_adictional_call = !code.children[4].nil?
  if has_adictional_call
    link_to_type = code.children[4].type
    possible_confirm_call = code.children[4].children[0]
    if is_still_a_node(possible_confirm_call) && is_still_a_node(possible_confirm_call.children[0])
      has_confirm_call = code.children[4].children[0].children[0].children[0]
    end
    if link_to_type == $hash && has_confirm_call == $confirm
      possible_redirect_name = code.children[4].children[0].children[1]
      if !possible_redirect_name.nil? && !possible_redirect_name.children[2].nil?
          link_to_redirect_name = code.children[4].children[0].children[1].children[2].children[0]
        father_node = code.children[4].children[0].children[1].children[2]
      else
        link_to_redirect_name = code.children[2].children[0]
        father_node = code.children[2]
      end
      if !code.children[3].children[1].nil?
        link_to_argument_variable = code.children[3].children[1]
      else
        link_to_argument_variable = code.children[3].children[0]
      end
      if !is_still_a_node link_to_argument_variable
        insert_outputs_on_array("#{link_to_redirect_name}".downcase,Transform_into.var_into_controller(link_to_argument_variable),'',count_method_arguments(father_node))
        true
      end
    end
  else
    false
  end
end

def look_for_form_for_action(code, instance_variable)
  father_node = ''
  if is_still_a_node(code)
    if code.type == $send
      loop_type = code.children[1]
      if loop_type == $form_for
        has_hash = !code.children[3].nil?
        has_array = !code.children[2].nil?
        if has_array
          possible_array = code.children[2].type
          if possible_array == $array
            name_part_1 = code.children[2].children[0].children[0]
            name_part_2 = code.children[2].children[1].children[0]
            if name_part_1.to_s != '' && name_part_2.to_s != ''
              $submit_name = name_part_1.to_s << '_' << (name_part_2.to_s)[1..-1]
            end
          end
        end
        if has_hash
          hash_implementation1 = code.children[3].children[1].nil?
          if hash_implementation1
            if is_still_a_node(code.children[3].children[0])
              possible_hash = code.children[3].children[0].children[1].type
            end
            if possible_hash == $hash
              loop_action = code.children[3].children[0].children[1].children[0].children[1].children[0]
              father_node = code.children[3].children[0].children[1].children[0].children[1]
            end
          else
            if is_still_a_node code.children[3].children[1]
              possible_hash = code.children[3].children[1].children[1].type
            end
            if possible_hash == $hash
              if code.children[3].children[1].children[1].children[1].nil?
                loop_action = code.children[3].children[1].children[1].children[0].children[1].children[0]
                father_node = code.children[3].children[1].children[1].children[0].children[1]
              else
                loop_action = code.children[3].children[1].children[1].children[1].children[1].children[0]
                father_node = code.children[3].children[1].children[1].children[1].children[1]
              end
            end
          end
          if !loop_action == '' || !instance_variable == ''
            insert_outputs_on_array(loop_action, instance_variable,'',count_method_arguments(father_node))
          end
        end
      end
    end
  end
end


def look_for_semantic_form_for(code, label)
  loop_url = ''
  father_node = ''
  if is_still_a_node(code)
    if code.type == $send
      loop_type = code.children[1]
      if loop_type == $semantic_form_for
        if !code.children[3].nil?
          possible_hash = code.children[3].type
          if possible_hash == $hash
            loop_url = code.children[3].children[0].children[1].children[1]
            father_node = code.children[3].children[0].children[1]
            if is_still_a_node(loop_url)
              loop_url = loop_url.children[1]
              father_node = father_node.children[1]
            end
          end
        else
          if !code.children[2].nil?
            loop_url = code.children[2].children[1]
            father_node = code.children[2]
          end
        end
      end
      if loop_url.to_s != '' && !is_still_a_node(loop_url)
        insert_outputs_on_array(loop_url.to_s,'',label,count_method_arguments(father_node))
      end
    end
  end
end


def look_for_render_call(code, instance_variable)
  father_node = ''
  method_name = code.children[1]
  has_hash = false
  if !code.children[2].nil?
    if is_still_a_node code.children[2]
      if code.children[2].type == $hash
        has_hash = true
      end
    end
  end
  if method_name == $render
    if has_hash
      if is_still_a_node(code.children[2].children[0].children[1])
        type = code.children[2].children[0].children[1].type
        if type != $lvar
          method_argument = code.children[2].children[0].children[1].children[0]
          father_node = code.children[2].children[0].children[1]
        end
      end
      if method_argument.to_s == ''
        method_argument = code.children[2].children[0].children[1].children[1]
        father_node = code.children[2].children[0].children[1]
        type = code.children[2].children[0].children[1].type
      end
      if is_still_a_node method_argument
        if !method_argument.children[1].nil?
          type = method_argument.children[1].type
          father_node = method_argument.children[1]
          method_argument = method_argument.children[1].children[0]
        else
          if method_argument.type != $ivar && method_argument.type != $lvar
            type = method_argument.type
            father_node = method_argument
            method_argument = method_argument.children[0]
          end
        end
      end
    else
      method_argument = code.children[2].children[0]
      father_node = code.children[2]
      type = code.children[2].type
      if is_still_a_node method_argument
        type = method_argument.type
        father_node = method_argument
        method_argument = method_argument.children[0]
      end
    end
    if method_argument.to_s[-1] != '/' && method_argument.to_s.size > 1 && !is_still_a_node(method_argument)
      method_argument = Transform_into.plural_for_ivar(method_argument, instance_variable)
      if type == $send
        insert_outputs_on_array(method_argument,'','',count_method_arguments(father_node))
      else
        insert_outputs_on_array(Transform_into.name_with_extension(method_argument.to_s, $language), '','',count_method_arguments(father_node))
      end
    end
  end
end

def look_for_form_tag_call(code, instance_variable)
  father_node = ''
  method_name = code.children[1]
  if method_name == $form_tag
    possible_hash = code.children[2].type
    if possible_hash == $hash
      if !code.children[2].children[1].nil?
        method_argument = code.children[2].children[1].children[1].children[0]
        father_node = code.children[2].children[1].children[1]
      else
        method_argument = ''
      end
      if is_still_a_node code.children[2].children[0]
        controller_called = code.children[2].children[0].children[1].children[0]
        insert_outputs_on_array(method_argument, controller_called,'',count_method_arguments(father_node))
      end
    else
      method_argument = code.children[2].children[1]
      father_node = code.children[2]
      if is_still_a_node(method_argument)
        if is_still_a_node method_argument.children[0]
          father_node = method_argument.children[0].children[0]
          method_argument = method_argument.children[0].children[0].children[1]
        end
      end
      if !method_argument.to_s == ''
        insert_outputs_on_array(method_argument, instance_variable,'',count_method_arguments(father_node))
      end
    end

  end
end

def look_for_button_methods(code)
  if $button_label == ''
    code.children.each do |code_children|
      if is_still_a_node code_children
        attribute_and_method = code_children.children[0]
        if is_still_a_node attribute_and_method
          method_name = attribute_and_method.children[1]
          if method_name == $buttons || method_name == $action
            possible_hash_type_one = code_children
            possible_hash_type_two = code_children
            if !code_children.children[2].nil?
              possible_hash_type_one = code_children.children[2].children[2]
            elsif !code_children.children[0].nil?
              possible_hash_type_two = code_children.children[0].children[3]
            end
            if is_still_a_node(possible_hash_type_one)
              if possible_hash_type_one.type == $hash
                $button_label = possible_hash_type_one.children[0].children[1].children[0]
                if is_still_a_node($button_label)
                  $button_label = $button_label.children[1].children[0]
                end
              end
            elsif is_still_a_node(possible_hash_type_two)
              if possible_hash_type_two.type == $hash
                $button_label = possible_hash_type_two.children[0].children[1].children[0]
              end
            end
          end
        end
        look_for_button_methods(code_children)
      end
    end
  else
    $button_label
  end
end

def check_if_eq_instance_variable(var)
  if var.to_s == $instance_variable.to_s
    true
  elsif var.to_s == $instance_variable.to_s[1..-1]
    return true
  else
    false
  end
end

def count_method_arguments(code)
  number_of_arguments = 0
  counter = 0
  outside_counter = 2
  if is_still_a_node(code)
    while is_still_a_node(code.children[outside_counter])
      if code.children[outside_counter].type == $hash
        while is_still_a_node(code.children[outside_counter].children[counter])
          if code.children[outside_counter].children[counter].type == $pair
            number_of_arguments += 1
          end
          counter += 1
        end
        outside_counter += 1
      else
        number_of_arguments += 1
        outside_counter += 1
      end
    end
  end
  number_of_arguments
end

def is_still_a_node(code)
  code.is_a?(Parser::AST::Node)
end
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

  def insert_outputs_on_array(name, receiver, label)
    output_model = Output_model.new
    output_model.name = name
    output_model.receiver = receiver
    output_model.label = label
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
          if method_argument_type == $ivar || method_argument_type == $lvar
            method_argument_value = code.children[3].children[0]
            insert_outputs_on_array(Transform_into.var_into_method(method_argument_value), "",'')
          else
            method_inside_link_to_has_params = code.children[3].children[1].nil?
            if !method_inside_link_to_has_params
              method_inside_link_to_param = code.children[3].children[1]
              if is_still_a_node(method_inside_link_to_param)
                if method_inside_link_to_param.type == $pair
                  method_inside_link_to_param = code.children[3].children[1].children[1].children[0]
                  controller_name = code.children[3].children[0].children[1].children[0]
                  if is_still_a_node(method_inside_link_to_param)
                    method_inside_link_to_param = method_inside_link_to_param.children[0]
                  end
                  if is_still_a_node(controller_name)
                    controller_name = ''
                  end
                end
              end
              insert_outputs_on_array(method_inside_link_to_param, Transform_into.singular(controller_name.to_s),'')
            end
          end
        end
      end
    end
  end
end

def look_for_submit_calls(code, instance_variable)
  method_argument = ''
  method_argument_type = ''
  method_name = code.children[1]
  if method_name == $submit
    if is_still_a_node(code.children[2])
      method_argument_type = code.children[2].type
    end
    if method_argument_type == $str
      method_argument = code.children[2].children[0]
    else
      if method_argument_type == $send
        if code.children[3].nil?
          method_argument = code.children[2].children[0].children[0]
        else
          method_argument = code.children[3].children[0].children[1].children[0]
        end
      end
    end
    if method_argument != ''
      if $submit_name != ''
        insert_outputs_on_array(Transform_into.var_into_method($submit_name),'' ,"#{method_argument}".downcase)
      else
        insert_outputs_on_array("#{method_argument}".downcase,Transform_into.var_into_controller(instance_variable),'')
      end
    end
  end
end

def look_for_auto_gen_methods(code, instance_variable,lvar_derived_from_ivar)
  method_name = code.children[1]
  if method_name == $label
    if is_still_a_node(code.children[2])
      method_argument_value = code.children[2].children[0]
      if method_argument_value.is_a?(Parser::AST::Node)
        if !code.children[2].children[0].children[0].children[2].nil?
          method_argument_value = code.children[2].children[0].children[0].children[2].children[0]
        end
      end
    end
    if instance_variable != '' || (method_argument_value.to_s.include?('/') || method_argument_value.to_s.include?('_path'))
      insert_outputs_on_array(method_argument_value, instance_variable,'')
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
            insert_outputs_on_array(method_name, instance_variable,'')
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
            $instance_variable = code_children.children[0].children[1]
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
  has_adictional_call = !code.children[4].nil?
  if has_adictional_call
    link_to_type = code.children[4].type
    if is_still_a_node(code.children[4].children[0])
      has_confirm_call = code.children[4].children[0].children[0].children[0]
    end
    if link_to_type == $hash && has_confirm_call == $confirm
      possible_redirect_name = code.children[4].children[0].children[1]
      if !possible_redirect_name.nil? && !possible_redirect_name.children[2].nil?
        link_to_redirect_name = code.children[4].children[0].children[1].children[2].children[0]
      else
        link_to_redirect_name = code.children[2].children[0]
      end
      if !code.children[3].children[1].nil?
        link_to_argument_variable = code.children[3].children[1]
      else
        link_to_argument_variable = code.children[3].children[0]
      end
      insert_outputs_on_array("#{link_to_redirect_name}".downcase,Transform_into.var_into_controller(link_to_argument_variable),'')
      true
    end
  else
    false
  end
end

def look_for_form_for_action(code, instance_variable)
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
            $submit_name = name_part_1.to_s << '_' << (name_part_2.to_s)[1..-1]
          end
        end
        if has_hash
          hash_implementation1 = code.children[3].children[1].nil?
          if hash_implementation1
            possible_hash = code.children[3].children[0].children[1].type
            if possible_hash == $hash
              loop_action = code.children[3].children[0].children[1].children[0].children[1].children[0]
            end
          else
            possible_hash = code.children[3].children[1].children[1].type
            if possible_hash == $hash
              if code.children[3].children[1].children[1].children[1].nil?
                loop_action = code.children[3].children[1].children[1].children[0].children[1].children[0]
              else
                loop_action = code.children[3].children[1].children[1].children[1].children[1].children[0]
              end
            end
          end
          if !loop_action == '' || !instance_variable == ''
            insert_outputs_on_array(loop_action, instance_variable,'')
          end
        end
      end
    end
  end
end


def look_for_semantic_form_for(code, label)
  loop_url = ''
  if is_still_a_node(code)
    if code.type == $send
      loop_type = code.children[1]
      if loop_type == $semantic_form_for
        if !code.children[3].nil?
          possible_hash = code.children[3].type
          if possible_hash == $hash
            loop_url = code.children[3].children[0].children[1].children[1]
          end
        else
          if !code.children[2].nil?
            loop_url = code.children[2].children[1]
          end
        end
      end
      if loop_url != ''
        insert_outputs_on_array(loop_url.to_s,'',label)
      end
    end
  end
end


def look_for_render_call(code, instance_variable)
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
      method_argument = code.children[2].children[0].children[1].children[0]
      if is_still_a_node method_argument
        if !method_argument.children[1].nil?
          method_argument = method_argument.children[1].children[0]
        else
          method_argument = method_argument.children[0]
        end
      end
    else
      method_argument = code.children[2].children[0]
      if is_still_a_node method_argument
        method_argument = method_argument.children[0]
      end
    end
    method_argument = Transform_into.plural_for_ivar(method_argument, instance_variable)
    insert_outputs_on_array(Transform_into.name_with_extension(method_argument.to_s, $language), instance_variable,'')
  end
end

def look_for_form_tag_call(code, instance_variable)
  method_name = code.children[1]
  if method_name == $form_tag
    possible_hash = code.children[2].type
    if possible_hash == $hash
      if !code.children[2].children[1].nil?
        method_argument = code.children[2].children[1].children[1].children[0]
      else
        method_argument = ''
      end
      controller_called = code.children[2].children[0].children[1].children[0]
      insert_outputs_on_array(method_argument, controller_called,'')
    else
      method_argument = code.children[2].children[1]
      insert_outputs_on_array(method_argument, instance_variable,'')
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

def is_still_a_node(code)
  code.is_a?(Parser::AST::Node)
end
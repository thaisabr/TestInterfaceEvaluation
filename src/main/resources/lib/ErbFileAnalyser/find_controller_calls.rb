#Visits parse tree looking for calls to controllers, when found, insert them on an array
class Find_controller_calls

  require 'ast/node'
  require_relative '../Util/transform_into'

  def initialize(array, instanceVar, localVar)
    $output_array = array
    $instance_variable = instanceVar
    $lvar_derived_from_ivar = localVar
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

  def find_controllers(code)
    if is_still_a_node(code)
      look_for_instance_variable(code)
      look_for_loop_argument(code)
      code.children.each do |code_children|
        if is_still_a_node(code_children)
          look_for_link_to_calls(code_children)
          look_for_submit_calls(code_children, $instance_variable)
          look_for_auto_gen_methods(code_children,$instance_variable,$lvar_derived_from_ivar)
          look_for_form_for_action(code_children,$instance_variable)
          look_for_render_call(code_children,$instance_variable)
          look_for_form_tag_call(code_children, $instance_variable)
          find_controllers(code_children)
        end
      end
      $output_array
    else
      $output_array
    end
  end

  def insert_outputs_on_array(name, receiver)
    output_model = Output_model.new
    output_model.name = name
    output_model.receiver = receiver
    $output_array.push output_model
  end

end

def look_for_link_to_calls(code)
  method_name = code.children[1]
  if method_name == $link_to
    found_confirm_call = look_for_confirm_call(code)
    if !found_confirm_call
      method_argument_type = code.children[3].type
      if method_argument_type == $ivar || method_argument_type == $lvar
        method_argument_value = code.children[3].children[0]
        insert_outputs_on_array(Transform_into.var_into_method(method_argument_value), "")
      else
        method_inside_link_to_has_params = code.children[3].children[1].nil?
        if !method_inside_link_to_has_params
          method_inside_link_to_param = code.children[3].children[1]
          insert_outputs_on_array(method_inside_link_to_param, "")
        end
      end
    end
  end
end

def look_for_submit_calls(code, instance_variable)
  method_name = code.children[1]
  if method_name == $submit
    method_argument_type = code.children[2].type
    if method_argument_type == $str
      method_argument = code.children[2].children[0]
    else
      if method_argument_type == $send
        method_argument = code.children[3].children[0].children[1].children[0]
      end
    end
    insert_outputs_on_array("#{method_argument}".downcase,Transform_into.var_into_controller(instance_variable))
  end
end

def look_for_auto_gen_methods(code, instance_variable,lvar_derived_from_ivar)
  method_name = code.children[1]
  if method_name == $label
    method_argument_value = code.children[2].children[0]
    if method_argument_value.is_a?(Parser::AST::Node)
      method_argument_value = code.children[2].children[0].children[0].children[2].children[0]
      insert_outputs_on_array(method_argument_value, instance_variable)
    else
      insert_outputs_on_array(method_argument_value, instance_variable)
    end
  end
  if is_still_a_node(code.children[0])
    variable_type = code.children[0].type
    variable_calls_method = !code.children[1].nil?
    if variable_type == $lvar && variable_calls_method
      method_argument = code.children[0].children[0]
      if method_argument == lvar_derived_from_ivar
        insert_outputs_on_array(method_name, instance_variable)
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
        end
        look_for_instance_variable(code_children)
      end
    end
  else
    $instance_variable = Transform_into.singular("#{$instance_variable}")
  end
end

def look_for_confirm_call(code)
  has_aditional_call = !code.children[4].nil?
  if has_aditional_call
    link_to_type = code.children[4].type
    has_confirm_call = code.children[4].children[0].children[0].children[0]
    if link_to_type == $hash && has_confirm_call == $confirm
      link_to_redirect_name = code.children[2].children[0]
      link_to_argument_variable = code.children[3].children[0]
      insert_outputs_on_array("#{link_to_redirect_name}".downcase,Transform_into.var_into_controller(link_to_argument_variable))
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
              loop_action = code.children[3].children[1].children[1].children[1].children[1].children[0]
            end
          end
          insert_outputs_on_array(loop_action, instance_variable)
        end
      end
    end
  end
end

def look_for_render_call(code, instance_variable)
  method_name = code.children[1]
  if method_name == $render
    method_argument = code.children[2].children[0]
    insert_outputs_on_array(method_argument, instance_variable)
  end
end

def look_for_form_tag_call(code, instance_variable)
  method_name = code.children[1]
  if method_name == $form_tag
    possible_hash = code.children[2].type
    if possible_hash == $hash
      method_argument = code.children[2].children[1].children[1].children[0]
      controller_called = code.children[2].children[0].children[1].children[0]
      insert_outputs_on_array(method_argument, controller_called)
    else
      method_argument = code.children[2].children[1]
      insert_outputs_on_array(method_argument, instance_variable)
    end

  end
end

def is_still_a_node(code)
  if code.is_a?(Parser::AST::Node)
    true
  else
    false
  end
end
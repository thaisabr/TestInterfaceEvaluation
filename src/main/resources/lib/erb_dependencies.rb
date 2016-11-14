require_relative 'ErbFileAnalyser/Grab_controller_from_erb_file'
class Erb_dependencies

  def grab_controllers(file_path)
    ControllerGrabber.new.grab_controllers(file_path)
  end

end
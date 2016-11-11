class File_manager

  require 'fileutils'

  def create_file(path, extension)
    dir = File.dirname(path)

    unless File.directory?(dir)
      FileUtils.mkdir_p(dir)
    end

    path << ".#{extension}"
    File.new(path, 'w')
  end

  def read_file(path)
    File.open(path, 'rb') { |file| file.read }
  end

  def write_on_file(text, path)
    File.open(path, 'w') do |f|
      f.write text
    end
  end

  def get_file_name(file_path)
    name_with_extension = /(?!.*\/)(.*)$/.match(file_path).to_s
    name = /.*(?=\.)/.match(name_with_extension).to_s
  end

end
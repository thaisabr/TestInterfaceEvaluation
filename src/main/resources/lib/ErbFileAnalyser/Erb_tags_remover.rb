#Remove tags from erb file and output only an array of pure ruby code

class ErbTagsRemover

  def remove_erb_tags(text)
    all_tagged_chunks = text.scan(/(?<=\<%)(.*?)(?=\%>)/m)
    all_tagged_chunks.each do |tagged_chunks|
      tagged_chunks.each do |tagged_chunk|
        if tagged_chunk[0] == '=' || tagged_chunk[0] == '-'
         tagged_chunk.slice!(0)
        end
        if tagged_chunk[-1] == '-'
          tagged_chunk.slice!(-1)
        end
      end
      tagged_chunks.delete_if do |tagged_chunk|
        if tagged_chunk[0] == '#'
          tagged_chunk = ''
          true
        end
      end
    end
    all_tagged_chunks.join("\n")
  end
end
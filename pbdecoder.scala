import com.google.protobuf.util.JsonFormat

import zio.*

object PbDecoder extends ZIOAppDefault:

  val dir         = "/Users/reddicj/Projects/pbdec2"
  val protoFile   = s"$dir/addressbook.fds"
  val pbdataFile  = s"$dir/persons.bin"
  val messageType = "pbtest.person.Person"

  def run =
    ProtobufDecoderUtils
      .dynamicMessages(protoFile, messageType, pbdataFile)
      .foreach(message => zio.Console.printLine(JsonFormat.printer().print(message)))

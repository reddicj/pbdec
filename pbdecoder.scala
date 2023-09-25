import java.io.*
import java.nio.file.*

import scala.jdk.CollectionConverters.*

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.CodedInputStream
import com.google.protobuf.DescriptorProtos

import zio.*
import zio.stream.*

object PbDecoder extends ZIOAppDefault:

  val dir         = "/Users/reddicj/Projects/pbdec2"
  val protoFile   = s"$dir/addressbook.fds"
  val pbdataFile  = s"$dir/persons.bin"
  val messageType = "pbtest.person.Person"

  def run =
    ProtobufDecoderUtils
      .dynamicMessages(protoFile, messageType, pbdataFile)
      .foreach(message => zio.Console.printLine(JsonFormat.printer().print(message)))

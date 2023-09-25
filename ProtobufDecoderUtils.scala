import java.io.*
import java.nio.file.*

import scala.jdk.CollectionConverters.*

import com.google.protobuf.Descriptors.*
import com.google.protobuf.*
import com.google.protobuf.util.JsonFormat
import zio.*
import zio.stream.*

object ProtobufDecoderUtils:

  def dynamicMessages(
    pathToCompiledProto: String,
    messageTypeName: String,
    pathToPbData: String
  ): ZStream[Scope, Throwable, DynamicMessage] =

    def singleMessage(descriptor: Descriptor): ZStream[Scope, Throwable, DynamicMessage] =
      ZStream.fromZIO(
        for
          codedInputStream <- createCodedInputStream(pathToPbData)
          message          <- ZIO.attempt(DynamicMessage.parseFrom(descriptor, codedInputStream))
        yield message
      )

    def delimitedMessages(descriptor: Descriptor): ZStream[Scope, Throwable, DynamicMessage] =
      ZStream.unwrap(
        createCodedInputStream(pathToPbData).map(
          ZStream.unfoldZIO(_)(readDelimitedFrom(descriptor))
        )
      )

    ZStream.unwrap(
      getDescriptor(pathToCompiledProto, messageTypeName)
        .map(d => singleMessage(d) orElse delimitedMessages(d))
    )

  private def getDescriptor(pathToCompiledProto: String, messageTypeName: String): Task[Descriptors.Descriptor] =
    for
      protoBytes <- ZIO.attempt(Files.readAllBytes(Paths.get(pathToCompiledProto)))
      set        <- ZIO.attempt(DescriptorProtos.FileDescriptorSet.parseFrom(protoBytes))
      fileProto  <- ZIO.attempt(set.getFile(0))
      fileDesc   <- ZIO.attempt(Descriptors.FileDescriptor.buildFrom(fileProto, Array.empty))
      descriptor <- ZIO.attempt(fileDesc.findMessageTypeByName(messageTypeName))
    yield descriptor

  private def createCodedInputStream(path: String): RIO[Scope, CodedInputStream] =
    for
      is          <- ZIO.fromAutoCloseable(ZIO.attempt(new BufferedInputStream(FileInputStream(path))))
      codedStream <- ZIO.attempt(CodedInputStream.newInstance(is))
    yield codedStream

  private def readDelimitedFrom(descriptor: Descriptors.Descriptor)(
    codedInputStream: CodedInputStream
  ): Task[scala.Option[(DynamicMessage, CodedInputStream)]] =

    def read(size: Int): Task[DynamicMessage] =
      ZIO.attempt {
        val limit   = codedInputStream.pushLimit(size)
        val message = DynamicMessage.parseFrom(descriptor, codedInputStream)
        codedInputStream.popLimit(limit)
        message
      }

    ZIO
      .attempt(codedInputStream.readRawVarint32())
      .either
      .flatMap {
        case Left(_)     => ZIO.none
        case Right(size) => read(size).map(_ -> codedInputStream).asSome
      }

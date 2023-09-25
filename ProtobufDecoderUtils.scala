import java.io.*
import java.nio.file.*

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.Descriptors.*
import com.google.protobuf.*
import com.google.protobuf.util.JsonFormat
import zio.*
import zio.prelude.*
import zio.stream.*

object ProtobufDecoderUtils:

  def dynamicMessages(
    pathToCompiledProto: String,
    messageType: String,
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
      getDescriptor(pathToCompiledProto, messageType)
        .map(d => singleMessage(d) orElse delimitedMessages(d))
    )

  private def getDescriptor(pathToCompiledProto: String, messageType: String): Task[Descriptors.Descriptor] =

    def build(
      fileProto: FileDescriptorProto,
      fileDescriptorsByFileName: Map[String, Descriptors.FileDescriptor]
    ): scala.Option[Descriptors.FileDescriptor] =
      val deps = fileProto.getDependencyList().asScala.toList
      deps
        .forEach(fileDescriptorsByFileName.get)
        .map(deps => Descriptors.FileDescriptor.buildFrom(fileProto, deps.toArray))

    @tailrec
    def buildAll(
      protoFiles: List[FileDescriptorProto],
      fileDescriptorsByFileName: Map[String, Descriptors.FileDescriptor]
    ): Map[String, Descriptors.FileDescriptor] =
      protoFiles match
        case Nil => fileDescriptorsByFileName
        case h :: t =>
          build(h, fileDescriptorsByFileName) match
            case None     => buildAll(t :+ h, fileDescriptorsByFileName)
            case Some(fd) => buildAll(t, fileDescriptorsByFileName + (fd.getName() -> fd))

    def findMessageTypeByName(fileDescriptorsByFileName: Map[String, Descriptors.FileDescriptor], messageType: String): scala.Option[Descriptors.Descriptor] =
      (for
        fd <- fileDescriptorsByFileName.values.toList
        d  <- fd.getMessageTypes().asScala.toList
        if d.getFullName() == messageType
      yield d).headOption

    for
      protoBytes        <- ZIO.attempt(Files.readAllBytes(Paths.get(pathToCompiledProto)))
      fileDescriptorSet <- ZIO.attempt(DescriptorProtos.FileDescriptorSet.parseFrom(protoBytes))
      protoFiles        <- ZIO.attempt(fileDescriptorSet.getFileList().asScala.toList)
      find <- ZIO
        .attempt(findMessageTypeByName(buildAll(protoFiles, Map.empty), messageType))
        .someOrFail(new RuntimeException(s"Could not find message type: $messageType"))
    yield find

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

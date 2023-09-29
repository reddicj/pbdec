import java.io.IOException

import cats.implicits.*
import com.google.protobuf.util.JsonFormat
import com.monovore.decline.*
import zio.*
import zio.stream.ZStream

object PbDecoder extends ZIOAppDefault:

  private val command: Command[RIO[Scope, Unit]] =

    val pathToFdsFileOpt: Opts[String] =
      Opts.option[String](
        "pathToFdsFile",
        help = "Path to the file descriptor file (e.g. path/to/file.fds)"
      )

    val pathToDataFileOpt: Opts[String] =
      Opts.option[String](
        "pathToDataFile",
        help = "Path to the protobuf binary data file (e.g. path/to/file.bin)"
      )

    val messageTypeOpt: Opts[String] =
      Opts.option[String](
        "messageType",
        help = "The fully qualified name of the protobuf message type (e.g. foo.bar.Baz)"
      )

    Command(
      name = "pbdec",
      header = "A tool to decode protobuf binary data files into JSON"
    )((pathToFdsFileOpt, pathToDataFileOpt, messageTypeOpt).mapN(program(_, _, _)))

  private def program(
    pathToFdsFile: String,
    pathToDataFile: String,
    messageType: String
  ): RIO[Scope, Unit] =
    DynamicMessages
      .withMessageType(pathToFdsFile, pathToDataFile, messageType)
      .map(JsonFormat.printer().print)
      .intersperse("[", ",", "]")
      .foreach(Console.printLine(_))

  override def run =
    getArgs
      .map(args => command.parse(args.toSeq))
      .flatMap {
        case Left(help)     => Console.printLine(help)
        case Right(program) => program
      }

# Protobuf Decoder

WIP program to decode protobuf binary data and print it in JSON format.

The next step with this project is to work out a way of building the descriptor set file (*.fds)
as part of the build process of the project where proto files reside, and then publish them so
they are readily available. This will avoid the faff of compiling one yourself as detailed below.

## Usage

```bash
./pbec --help
```

```bash
./pbdec \
  --pathToFdsFile trades.fds \
  --pathToDataFile trades.bin \
  --messageType workflow.v1.trades.Trade
```

## Building a GraalVM Native Image

Reference: https://scala-cli.virtuslab.org/docs/commands/package#native-image

```bash
scala-cli --power package . -o pbdec --native-image --force
```

## Pre-requisites
Install the protocol buffer compiler `protoc`. Installation instructions are here: https://grpc.io/docs/protoc-installation/.

For the moment the `protoc` tool is needed to compile a descriptor set file from the `*.proto` files (see below).

## Compile proto file into a descriptor set file

To decode protobuf using this tool you will need a descriptor set file (*.fds) - see https://protobuf.dev/programming-guides/techniques/#self-description.

Below is an example of compiling a descriptor set file from the Workflow Engine [trades.proto](https://gitlab.com/pirum-systems/ccs/protobuf/-/blob/main/protobuf/src/main/protobuf/workflow/v1/trades/trades.proto?ref_type=heads) file:

```bash
protoc \
  -I=protobuf/target/protobuf_external \
  -I=protobuf/src/main/protobuf \
  --include_imports \
  --descriptor_set_out trades.fds \
  workflow/v1/trades/trades.proto
```

This command compiles the given proto file to the output file descriptor file, including all imports.
This file contains all the necessary schema info to decode the protobuf binary data.

Note you can read the compiled file:

```bash
protoc < somefilename.fds --decode=google.protobuf.FileDescriptorSet google/protobuf/descriptor.proto
```
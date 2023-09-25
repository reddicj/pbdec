# Protobuf Decoder

WIP program to decode protobuf binary data and print it in a json format.
Note the program requires a 

## Compile proto file into a descriptor set file

`protoc -I=./path/to/protofile --include_imports --descriptor_set_out somefilename.fds someprotofile.proto`

Compiles the given proto file to the output file descriptor file, including all imports.
This file contains all the necessary schema info to decode the protobuf binary data.

To read the compiled file:

`protoc < somefilename.fds --decode=google.protobuf.FileDescriptorSet google/protobuf/descriptor.proto`
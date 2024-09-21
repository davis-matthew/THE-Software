This is the C++ project used to handle the backend compilation of THE-ProgrammingLanguage

The general workflow is:
1. Quad IR is sent to this C++ project, which converts it into LLVM IR
2. We run some of the pre-made LLVM optimization passes
3. We run backend generation from the optimized LLVM IR to create an executable

Setup:
Download LLVM
Download CMake (part of msvc compiler tools?)
Need a C/CXX compiler


./THE-LLVMTranslator/build/THE-LLVMTranslator /the-software/THE-ProgrammingLanguage/testFiles/ProgramOutput.the && /the-software/llvm-project-llvmorg-18.1.8/build/bin/llvm-dis filename.bc
/the-software/llvm-project-llvmorg-18.1.8/build/bin/clang++ filename.bc -o a.out
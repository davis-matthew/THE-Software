This is the C++ project used to handle the backend compilation of THE-ProgrammingLanguage

The general workflow is:
1. Quad IR is sent to this C++ project, which converts it into LLVM IR
2. We run some of the pre-made LLVM optimization passes
3. We run backend generation from the optimized LLVM IR to create an executable

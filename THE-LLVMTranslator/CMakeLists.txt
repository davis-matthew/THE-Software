cmake_minimum_required(VERSION 3.10)
project(THE-LLVMTranslator)

set(LLVM_DIR "C:\\Users\\exeloar\\eclipse-workspace\\THE-Software\\llvm-project\\build\\cmake\\modules\\CMakeFiles")

find_package(LLVM REQUIRED CONFIG)
 
find_package(ZLIB REQUIRED)

# FIXME: only link necessary components

#llvm_map_components_to_libnames(LLVM_LIBS asmparser asmprinter codegen core instrumentation irreader mc support target)
llvm_map_components_to_libnames(LLVM_LIBS codegen core instrumentation irreader mc)


add_executable(THE-LLVMTranslator THE-LLVMTranslator.cpp THE-Instruction.cpp)


# LLVM headers
target_include_directories(THE-LLVMTranslator PRIVATE ${LLVM_INCLUDE_DIRS})
target_compile_definitions(THE-LLVMTranslator PRIVATE ${LLVM_DEFINITIONS})

# Link w/ LLVM libraries
target_link_libraries(THE-LLVMTranslator ${LLVM_LIBS})

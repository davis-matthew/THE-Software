#include <iostream>
#include <fstream>
#include <optional>

#include <llvm/IR/Module.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/DataLayout.h>
#include <llvm/IR/LegacyPassManager.h>

#include <llvm/Support/TargetSelect.h>
#include <llvm/Support/FileSystem.h>
#include <llvm/Support/raw_ostream.h>

#include <llvm/Target/CodeGenCWrappers.h>
#include <llvm/Target/TargetMachine.h>

#include <llvm/MC/TargetRegistry.h>

#include <llvm/TargetParser/Host.h>
#include <llvm/TargetParser/SubtargetFeature.h>

#include <llvm/Object/ObjectFile.h>

#include <llvm/Bitcode/BitcodeWriter.h>

#include "THE-Instruction.h"

using namespace llvm;

std::string filename = "";
llvm::LLVMContext context;
std::vector<THEInstruction> insts;
Module *M = nullptr;
TargetMachine *targetMachine;

//https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl08.html

void processArgs(int argc, char** argv) {
    if(argc == 1){
        std::cout << "Usage: THE-LLVMTranslator <path/to/THE-COMPILER/file>\n";
        std::terminate();
    }
    filename = argv[1];
}
void loadFile() {
    insts = std::vector<THEInstruction>();
    std::ifstream file(filename);
    std::string line;

    if (file.is_open()) {
        while (std::getline(file, line)) {
            insts.push_back(THEInstruction(line));
        }
        file.close();
    } 
    else {
        std::cerr << "Cannot open file!" << std::endl;
    }
}
void generateModule() {
//     InitializeAllTargetInfos();
//     InitializeAllTargets();
//     InitializeAllTargetMCs();
//     InitializeAllAsmParsers();
//     InitializeAllAsmPrinters();

    
    M = new Module("filename", context); //TODO: filename
    IRBuilder<> builder(context);

// std::cout << "a1\n";
    
//     auto TargetTriple = sys::getDefaultTargetTriple();
//     M->setTargetTriple(TargetTriple);
//     std::cout << TargetTriple << "\n";
//     std::string Error;
//     auto Target = TargetRegistry::lookupTarget(TargetTriple, Error);
//     std::cout <<"ERROR: " << Error << "\n";
// std::cout << "a2\n";
//     auto RM = std::optional<Reloc::Model>();
//     TargetOptions opt;
//     targetMachine = Target->createTargetMachine(TargetTriple, "generic", "", opt, RM);
// std::cout << "a3\n";
//     auto DataLayout = targetMachine->createDataLayout();
//     M->setDataLayout(DataLayout);

    

    auto i32 = builder.getInt32Ty();
    auto mainType = FunctionType::get(i32, false);
    auto mainFunc = Function::Create(mainType, Function::ExternalLinkage, "main", M);
    auto entry = BasicBlock::Create(context, "entry", mainFunc);
    builder.SetInsertPoint(entry);

    auto printfType = FunctionType::get(builder.getInt32Ty(), PointerType::getUnqual(builder.getInt8Ty()), true);
    auto printfFunc = Function::Create(printfType, Function::ExternalLinkage, "printf", M);
    auto helloWorld = builder.CreateGlobalStringPtr("Hello World!\n");
    builder.CreateCall(printfFunc, {helloWorld});
    builder.CreateRet(builder.getInt32(0));

    std::error_code EC;
    raw_fd_ostream OS("filename.bc", EC, sys::fs::OF_None);
    WriteBitcodeToFile(*M, OS);

}

void runLLVMPasses() {
    //new pass manager
    //reference opt?
}

void generateExecutable(){
    // raw_fd_ostream dest("a.out", nullptr, sys::fs::OF_None);

    // legacy::PassManager PM;
    // targetMachine->addPassesToEmitFile(PM,dest, nullptr, CodeGenFileType::ObjectFile);

    // PM.run(*M);
    // dest.flush();
}

int main(int argc, char **argv) {
    //std::cout << "1\n";
    processArgs(argc, argv);
    //std::cout << "2\n";
    loadFile();
    //std::cout << "3\n";
    generateModule();
    //std::cout << "4\n";
    runLLVMPasses();
    // std::cout << "5\n";
    // generateExecutable();
    // std::cout << "6\n";
}
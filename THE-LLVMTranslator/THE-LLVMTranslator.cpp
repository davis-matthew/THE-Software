#include "llvm/IR/Module.h"
#include "llvm/IR/LLVMContext.h"

using namespace llvm;

//https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl08.html

Module* convertFileToLLVMIR(){
    LLVMContext context;
    Module* module = new Module("converted", context);


    FunctionType *foo_type = TypeBuilder<int(int, char **), false>::get(getGlobalContext());
    Function *func = cast<Function>(mod->getOrInsertFunction("main", foo_type));
    BasicBlock *block = BasicBlock::Create(getGlobalContext(), "entry", func);
    IRBuilder<> builder(block);
    // (15 * 10) + 5 = ?
    Value *tmp = builder.CreateMul(builder.getInt32(15), builder.getInt32(10), "mul");
    Value *tmp2 = builder.CreateAdd(tmp, builder.getInt32(5), "add");
    builder.CreateRet(tmp2);

    return module;
}

void runLLVMPasses(Module &M){
    //new pass manager
    //reference opt?
}

void generateExecutable(Module &M){
    //https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl08.html

    auto TargetTriple = sys::getDefaultTargetTriple();
    std::string Error;
    auto Target = TargetRegistry::lookupTarget(TargetTriple, Error);

    auto CPU = "generic";
    auto Features = "";

    TargetOptions opt;
    auto TargetMachine = Target->createTargetMachine(TargetTriple, CPU, Features, opt, Reloc::PIC_);

    M->setDataLayout(TargetMachine->createDataLayout());
    M->setTargetTriple(TargetTriple);
}

int main(int argc, char **argv) { 
    Module* m = convertFileToLLVMIR();
    runLLVMPasses(m);
    generateExecutable(m);
}

#include "THE-LLVMTranslator.h"

using namespace llvm;

std::string filename = "";
llvm::LLVMContext context;
Module *M = nullptr;
TargetMachine *targetMachine;

//https://llvm.org/docs/tutorial/MyFirstLanguageFrontend/LangImpl08.html

void processArgs(int argc, char** argv) {
    if(argc == 1){
        std::cout << "Usage: THE-LLVMTranslator <path/to/THE-COMPILER/file>\n";
        std::exit(0);
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
    std::cout << "Parsed Input Successfully" << std::endl;
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
    
    //TODO: SEGFAULTS IF REFERENCES ARG WHICH ISN'T IMPLEMENTED YET.
    for(int id = 0; id < insts.size(); id++){
        THEInstruction* inst = &(insts[id]);
        std::vector<int> args = inst->getArgs();
        std::vector<llvm::Value*> IRargs = std::vector<llvm::Value*>(inst->getArgs().size());
        for(int i = 0; i < args.size(); i++) {
            IRargs[i] = insts[args[i]].getResultIRInst();
        }
        Value* res = nullptr;
        switch(inst->getType()) {
            case THEInstructionType::Given: {
                std::string resultType = inst->getResultType();
                std::cout << "Given of type: {" << resultType <<"} w/ value {"<<inst->getGivenValue()<<"}"<< "\n";
                if( resultType == "int" ) {
                    res = builder.getInt32(stoi(inst->getGivenValue()));
                }
                else if( resultType == "string" ) {
                    //TODO!
                }
                else if( resultType == "bool" ) {
                    if ( inst->getGivenValue() == "true" ) {
                        res = builder.getTrue();
                    }
                    else if( inst->getGivenValue() == "false" ) {
                        res = builder.getFalse();
                    }
                    else {
                        //TODO!
                    }
                }
                break;
            }
            case THEInstructionType::Add: {
                res = builder.CreateAdd(IRargs[0], IRargs[1]);
                break;
            }
            case THEInstructionType::Sub: {
                res = builder.CreateSub(IRargs[0], IRargs[1]);
                break;
            }
            case THEInstructionType::Mult: {
                res = builder.CreateMul(IRargs[0], IRargs[1]);
                break;
            }
            case THEInstructionType::Divide: {
                res = builder.CreateSDiv(IRargs[0], IRargs[1]); // signed division
                break;
            }
    //         case THEInstructionType::Power: {
    //             //TODO
    //             //FIXME: this is not available as an instruction directly in LLVM. Maybe some binary exponentiation tricks would be nice
    //         }
            case THEInstructionType::Modulo: {
                res = builder.CreateSRem(IRargs[0], IRargs[1]); // signed remainder
                break;
            }
            case THEInstructionType::BoolAnd:
            case THEInstructionType::BitAnd: {
                res = builder.CreateAnd(IRargs[0], IRargs[1]);
                break;
            }
            case THEInstructionType::BoolOr:
            case THEInstructionType::BitOr: {
                res = builder.CreateOr(IRargs[0], IRargs[1]);
                break;
            }
            case THEInstructionType::RefEqual:
            case THEInstructionType::Equal: {
                res = builder.CreateICmpEQ(IRargs[0], IRargs[1]);
                break;
            }
            case THEInstructionType::RefNotEqual:
            case THEInstructionType::NotEqual: {
                res = builder.CreateICmpNE(IRargs[0], IRargs[1]);
                break;
            }
            case THEInstructionType::Less: {
                res = builder.CreateICmpSLT(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
            case THEInstructionType::LessEqual: {
                res = builder.CreateICmpSLE(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
            case THEInstructionType::Greater: {
                res = builder.CreateICmpSGT(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
            case THEInstructionType::GreaterEqual: {
                res = builder.CreateICmpSGE(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
    //         case THEInstructionType::Concat: {
    //             //TODO
    //         }
            case THEInstructionType::BoolNot: {
                res = builder.CreateXor(IRargs[0], builder.getInt1(true));
                break;
            }
            case THEInstructionType::BitNot: {
                res = builder.CreateXor(IRargs[0], ConstantInt::get(IRargs[0]->getType(), -1));
                break;
            }
    //         case THEInstructionType::ToString: {
    //             //TODO: Not sure this does anything in LLVM.
    //         }
    //         case THEInstructionType::Load: {
    //             res = builder.CreateLoad(IRargs[0]);
    //             break;
    //         }
    //         case THEInstructionType::Print: {
    //             auto printfFunc = M.getFunction("printf");
    //             if(printfFunc == nullptr) { // create printf if not defined yet
    //                 auto printfType = FunctionType::get(builder.getInt32Ty(), PointerType::getUnqual(builder.getInt8Ty()), true);
    //                 printfFunc = Function::Create(printfType, Function::ExternalLinkage, "printf", M);
    //             }
    //             res = builder.CreateCall(printfFunc, {IRargs[0]});
    //             break;
    //         }
    //         case THEInstructionType::Identity: {
    //             //TODO
    //         }
    //         case THEInstructionType::Store: {
    //             res = builder.CreateLoad(IRargs[1],IRargs[0]); // value, location
    //             break;
    //         }
    //         case THEInstructionType::GetElement: {
    //             //TODO
    //         }
    //         case THEInstructionType::AllocArr: {
    //             //TODO
    //         }
    //         case THEInstructionType::ArrLength: {
    //             //TODO
    //         }
    //         case THEInstructionType::FunctionDef: {
    //             auto i32 = builder.getInt32Ty(); //TODO: all types
    //             auto funcType = FunctionType::get(i32, false);
    //             res = Function::Create(funcType, Function::ExternalLinkage, inst.getName(), M);
    //             auto entry = BasicBlock::Create(context, "entryblock", res);
    //             builder.SetInsertPoint(entry);

    //             break;
    //         }
    //         case THEInstructionType::FunctionCall: {
    //             //TODO
    //         }
    //         case THEInstructionType::AllocVar: {
    //             //TODO
    //         }
    //         case THEInstructionType::If: {
    //             //TODO
    //         }
    //         case THEInstructionType::Else: {
    //             //TODO
    //         }
            default: {
                std::cout << "Unknown instruction type encountered\n";
                continue;
                //throw std::runtime_error("Unknown instruction type.");
            }
        }
        inst->setResultIRInst(res);
    }
    

    // auto printfType = FunctionType::get(builder.getInt32Ty(), PointerType::getUnqual(builder.getInt8Ty()), true);
    // auto printfFunc = Function::Create(printfType, Function::ExternalLinkage, "printf", M);
    // auto helloWorld = builder.CreateGlobalStringPtr("Hello World!\n");
    // builder.CreateCall(printfFunc, {helloWorld});
    builder.CreateRet(insts[3].getResultIRInst());
    M->print(llvm::outs(), nullptr);
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
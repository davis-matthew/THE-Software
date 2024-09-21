#include <llvm/IR/Module.h>
#include <llvm/IR/IRBuilder.h>

#include <string>
#include <vector>

/*
The reason we have a "THEInstruction" class instead of just an instruction is to 
differentiate and disambiguate with the LLVM::Instruction class that we will be
using in the generation of LLVM IR
*/
std::vector<string> split(const string& s, char splitChar) {
    vector<string> temp;
    stringstream strs(s);
    string t = "";
    while (getline(strs, t, splitChar)) { temp.push_back(t); }
    return temp;
}

enum THEInstructionType {
    Add, Subtract, Concat, Mult, Divide, Modulo, Power, And, Or, Not,
	BitAnd, BitOr, BitNot,
	Less, Greater, LessEqual, GreaterEqual, Equal, NotEqual, RefEqual, RefNotEqual,
	Call,
	Print, // Print to the "best" console? stdout?
	Read, // Read a from memory (base type or array)
	WriteToReference, // Write to a reference to a variable or memory
	GetReference, // Get a pointer to a variable/array/struct
	ReadBuiltInProperty, // Read a property of an object, such as length of an array or other special property.
	Given, // Load a value from program memory (like a constant)
	Reassign, // Change the value of an existing variable (or index in an array)
	Alloc, // Allocate memory, but without assigning it to any variable
	Initialize, // Initialize a new variable and assign it to something
	Declare, // Declare the scope of a variable without assigning it to anything???
	ArrayLength, // Read the length of an array
	Break, // Instantly jump out of the end of the nearest loop
	Continue, // Jump directly to the nearest loop header
	If, ElseIf, Else, EndBlock, StartBlock, Loop, FunctionDefinition
};

const std::string ALPHABET_CASEINSENSITIVE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

class THEInstruction {
    THEInstructionType type;
    std::vector<THEInstruction> args;


    llvm::Value* res = nullptr;
public:
    THEInstruction(std::string t) {
        parseInstruction(t);
    }

    void parseInstruction(std::string t) {
        //Trim prefix
        size_t pos = line.find_first_of(ALPHABET_CASEINSENSITIVE);
        if (pos != std::string::npos) {
            t = line.substr(pos);
        }
        else{
            t = "";
        }

        //Command
        pos = line.find_first_not_of(ALPHABET_CASEINSENSITIVE);
        std::string command = line.substr(0,pos);
        line = line.substr(pos);
        
        if (line[0] == '(') {
            size_t end = line.find(')');
            args = split(line.substr(1, end - 2), ',');
            line = line.substr(end+1);
        }

        if (line[0] == ' ' && line[1] == '\'') {
            //parse Name
        }

        if (line[0] == '-' && line[1] == '>') {
            //parse RHS
        }

        //parse type
        //parse name
        //parse parent

        //Command 'string'? (arg1, arg2, ...)?->?type of var 'name' Parent=#

    }
    
    void createInstructionInIR(llvm::Module &M, llvm::IRBuilder<> &builder) {
        if (res != nullptr) { return; } // Skip if we've already created this instruction

        std::vector<llvm::Value*> IRargs = std::vector<llvm::Value*>(args.size());
        for(int i = 0; i < args.size(); i++){
            IRargs[i] = args[i].getResultIRInst();
        }

        switch(type){

            // Operations
            case(Add): { 
                res = builder.CreateAdd(IRargs[0], IRargs[1]); 
                break;
            }
            case(Subtract): {
                res = builder.CreateSub(IRargs[0], IRargs[1]);
                break;
            }
            case(Concat):{}
            case(Mult): {
                res = builder.CreateMul(IRargs[0], IRargs[1]);
                break;
            }
            case(Divide):{
                res = builder.CreateSDiv(IRargs[0], IRargs[1]); // signed division
                break;
            }
            case(Modulo):{
                res = builder.CreateSRem(IRargs[0], IRargs[1]); // signed remainder
                break;
            }
            case(Power):{}
            case(And): {
                res = builder.CreateAnd(IRargs[0], IRargs[1]);
                break;
            }
            case(Or): {
                res = builder.CreateOr(IRargs[0], IRargs[1]);
                break;
            }
            case(Not): {
                res = builder.CreateNot(IRargs[0]);
                break;
            }
            case(BitAnd):{}
            case(BitOr):{}
            case(BitNot):{}

            // Comparisons
            case(Less):{
                res = builder.CreateICmpSLT(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
            case(Greater):{
                res = builder.CreateICmpSGT(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
            case(LessEqual):{
                res = builder.CreateICmpSLE(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
            case(GreaterEqual):{
                res = builder.CreateICmpSGE(IRargs[0], IRargs[1]); // signed comparison
                break;
            }
            case(Equal):{
                res = builder.CreateICmpEQ(IRargs[0], IRargs[1]);
                break;
            }
            case(NotEqual):{
                res = builder.CreateICmpNE(IRargs[0], IRargs[1]);
                break;
            }
            case(RefEqual):{}
            case(RefNotEqual):{}

            // Various Other
            case(Call):{}
            case(Print):{}
            case(Read):{}
            case(WriteToReference):{}
            case(GetReference):{}
            case(ReadBuiltInProperty):{}
            case(Given):{}
            case(Reassign):{}
            case(Alloc):{}
            case(Initialize):{}
            case(Declare):{}
            case(ArrayLength):{}

            // Control Flow
            case(Break):{}
            case(Continue):{}
            case(If):{}
            case(ElseIf):{}
            case(Else):{}
            case(EndBlock):{}
            case(StartBlock):{}
            case(Loop):{}
            case(FunctionDefinition):{}
            default: {
                throw std::runtime_error("Unknown instruction type.");
            }
        }
    }

    llvm::Value* getResultIRInst(){
        if (res == nullptr) {
            //res = createInstructionInIR();
        }
        return res;
    }

    void setResultIRInst(llvm::Value &I){
        res = &I;
    }
};
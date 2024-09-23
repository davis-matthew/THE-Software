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
    Add,
    Sub,
    Mult,
    Divide,
    Power,
    Modulo,
    BoolAnd,
    BoolOr,
    BitAnd,
    BitOr,
    Equal,
    NotEqual,
    RefEqual,
    RefNotEqual,
    Less,
    LessEqual,
    Greater,
    GreaterEqual,
    Concat,
    BoolNot,
    BitNot,
    ToString,
    Load,
    Print,
    Identity,
    Store,
    GetElement,
    AllocArr,
    ArrLength,
    FunctionDef,
    FunctionCall,
    AllocVar,
    ArrLength,
    If,
    Else,

    UNKNOWN
};

const std::string ALPHABET_CASEINSENSITIVE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

THEInstructionType parseCommand(std::string c) {
    switch(c)
    {   
        case "Add":             return THEInstructionType::Add;
        case "Sub":             return THEInstructionType::Sub;
        case "Mult":            return THEInstructionType::Mult;
        case "Divide":          return THEInstructionType::Divide;
        case "Power":           return THEInstructionType::Power;
        case "Modulo":          return THEInstructionType::Modulo;
        case "BoolAnd":         return THEInstructionType::BoolAnd;
        case "BoolOr":          return THEInstructionType::BoolOr;
        case "BitAnd":          return THEInstructionType::BitAnd;
        case "BitOr":           return THEInstructionType::BitOr;
        case "Equal":           return THEInstructionType::Equal;
        case "NotEqual":        return THEInstructionType::NotEqual;
        case "RefEqual":        return THEInstructionType::RefEqual;
        case "RefNotEqual":     return THEInstructionType::RefNotEqual;
        case "Less":            return THEInstructionType::Less;
        case "LessEqual":       return THEInstructionType::LessEqual;
        case "Greater":         return THEInstructionType::Greater;
        case "GreaterEqual":    return THEInstructionType::GreaterEqual;
        case "Concat":          return THEInstructionType::Concat;
        case "BoolNot":         return THEInstructionType::BoolNot;
        case "BitNot":          return THEInstructionType::BitNot;
        case "ToString":        return THEInstructionType::ToString;
        case "Load":            return THEInstructionType::Load;
        case "Print":           return THEInstructionType::Print;
        case "Identity":        return THEInstructionType::Identity;
        case "Store":           return THEInstructionType::Store;
        case "GetElement":      return THEInstructionType::GetElement;
        case "AllocArr":        return THEInstructionType::AllocArr;
        case "ArrLength":       return THEInstructionType::ArrLength;
        case "FunctionDef":     return THEInstructionType::FunctionDef;
        case "FunctionCall":    return THEInstructionType::FunctionCall;
        case "AllocVar":        return THEInstructionType::AllocVar;
        case "ArrLength":       return THEInstructionType::ArrLength;
        case "If":              return THEInstructionType::If;
        case "Else":            return THEInstructionType::Else;
        default:                return THEInstructionType::UNKNOWN;
    }
}

class THEInstruction {
    
    THEInstructionType command;
    std::vector<THEInstruction> args;
    std::string name;
    std::string resultType;
    uint parentID;
    uint endID;
    uint previousIfID;
    uint elseID;


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
        std::string commandStr = line.substr(0,pos);
        this.command = parseCommand(commandStr);
        line = line.substr(pos);
        
        // Args
        if (line[0] == '(') {
            size_t end = line.find(')');
            vector<std::string> args = split(line.substr(1, end - 2), ',');
            line = line.substr(end + 1);
        }
        
        // Name
        if (line[0] == ' ' && line[1] == '\'') {
            line = line.substr(2);
            size_t end = line.find("\'"); //TODO: escaped single quotes?
            self.name = line.substr(0, end);
            line = line.substr(end + 1);
        }

        if (line[0] == '-' && line[1] == '>') {
            //parse RHS
        }

        //parse type
        //parse name
        //parse parent

        //Command 'string'? (arg1, arg2, ...)?->?type of var 'name' Parent=#

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
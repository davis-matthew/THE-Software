#include "THE-Instruction.h"

std::vector<THEInstruction> insts;

/*
The reason we have a "THEInstruction" class instead of just an instruction is to 
differentiate and disambiguate with the LLVM::Instruction class that we will be
using in the generation of LLVM IR
*/
std::vector<std::string> split(const std::string& s, const std::string& delimiter) {
    std::vector<std::string> tokens;
    size_t start = 0;
    size_t end = s.find(delimiter);
    while (end != std::string::npos) {
        tokens.push_back(s.substr(start, end - start));
        start = end + delimiter.length();
        end = s.find(delimiter, start);
    }
    tokens.push_back(s.substr(start));
    return tokens;
}

const std::string ALPHABET_CASEINSENSITIVE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

THEInstructionType parseCommand(std::string c) {
    if ( c == "Add" )             return THEInstructionType::Add;
    if ( c == "Sub" )             return THEInstructionType::Sub;
    if ( c == "Mult" )            return THEInstructionType::Mult;
    if ( c == "Divide" )          return THEInstructionType::Divide;
    if ( c == "Power" )           return THEInstructionType::Power;
    if ( c == "Modulo" )          return THEInstructionType::Modulo;
    if ( c == "BoolAnd" )         return THEInstructionType::BoolAnd;
    if ( c == "BoolOr" )          return THEInstructionType::BoolOr;
    if ( c == "BitAnd" )          return THEInstructionType::BitAnd;
    if ( c == "BitOr" )           return THEInstructionType::BitOr;
    if ( c == "Equal" )           return THEInstructionType::Equal;
    if ( c == "NotEqual" )        return THEInstructionType::NotEqual;
    if ( c == "RefEqual" )        return THEInstructionType::RefEqual;
    if ( c == "RefNotEqual" )     return THEInstructionType::RefNotEqual;
    if ( c == "Less" )            return THEInstructionType::Less;
    if ( c == "LessEqual" )       return THEInstructionType::LessEqual;
    if ( c == "Greater" )         return THEInstructionType::Greater;
    if ( c == "GreaterEqual" )    return THEInstructionType::GreaterEqual;
    if ( c == "Concat" )          return THEInstructionType::Concat;
    if ( c == "BoolNot" )         return THEInstructionType::BoolNot;
    if ( c == "BitNot" )          return THEInstructionType::BitNot;
    if ( c == "ToString" )        return THEInstructionType::ToString;
    if ( c == "Load" )            return THEInstructionType::Load;
    if ( c == "Print" )           return THEInstructionType::Print;
    if ( c == "Identity" )        return THEInstructionType::Identity;
    if ( c == "Store" )           return THEInstructionType::Store;
    if ( c == "GetElement" )      return THEInstructionType::GetElement;
    if ( c == "AllocArr" )        return THEInstructionType::AllocArr;
    if ( c == "ArrLength" )       return THEInstructionType::ArrLength;
    if ( c == "FunctionDef" )     return THEInstructionType::FunctionDef;
    if ( c == "FunctionCall" )    return THEInstructionType::FunctionCall;
    if ( c == "AllocVar" )        return THEInstructionType::AllocVar;
    if ( c == "ArrLength" )       return THEInstructionType::ArrLength;
    if ( c == "If" )              return THEInstructionType::If;
    if ( c == "Else" )            return THEInstructionType::Else;
    if ( c == "Given" )           return THEInstructionType::Given;
    if ( c == "StartBlock" )      return THEInstructionType::StartBlock;
    if ( c == "EndBlock" )        return THEInstructionType::EndBlock;
    if ( c == "Loop" )            return THEInstructionType::Loop;
    if ( c == "Break" )           return THEInstructionType::Break;
    return THEInstructionType::UNKNOWN;
}

bool hasArgs(THEInstructionType type) {
    return type != THEInstructionType::AllocVar
        && type != THEInstructionType::If
        && type != THEInstructionType::Else
        && type != THEInstructionType::Loop
        && type != THEInstructionType::Break
        && type != THEInstructionType::Given
        && type != THEInstructionType::StartBlock
        && type != THEInstructionType::EndBlock;
}
bool hasReturnVal(THEInstructionType type) {
    return type != THEInstructionType::Print
        && type != THEInstructionType::Store
        && type != THEInstructionType::If
        && type != THEInstructionType::Else
        && type != THEInstructionType::Loop
        && type != THEInstructionType::Break
        && type != THEInstructionType::StartBlock
        && type != THEInstructionType::EndBlock;
}
bool hasFunctionName(THEInstructionType type) {
    return type == THEInstructionType::FunctionCall
        || type == THEInstructionType::FunctionDef;
}
bool hasGivenValue(THEInstructionType type) {
    return type == THEInstructionType::Given;
}

THEInstruction::THEInstruction() {

}

THEInstruction::THEInstruction(std::string t) {
    parseInstruction(t);
}

void THEInstruction::parseInstruction(std::string t) {
    //Trim prefix
    size_t pos = t.find_first_of(ALPHABET_CASEINSENSITIVE);
    if (pos != std::string::npos) {
        t = t.substr(pos);
    }
    else{
        t = "";
    }

    //Command
    pos = t.find_first_not_of(ALPHABET_CASEINSENSITIVE);
    std::string commandStr = t.substr(0,pos);
    this->command = parseCommand(commandStr);
    t = t.substr(pos + 1);

    //Args
    if(hasArgs(this->command)) {
        size_t end = t.find(')');
        std::vector<std::string> args;
        if (end == 0) {
            args = std::vector<std::string>();
        }
        else {
            args = split(t.substr(0, end), ", ");
        }
        t = t.substr(end + 1);
        this->args = std::vector<int>(args.size());
        for(int i = 0; i < args.size(); i++){
            std::vector<std::string> tempSplit = split(args[i], " ");
            this->args[i] = stoi(tempSplit[1]);
        }
    }

    //Return Val
    if(hasReturnVal(this->command)) {
        pos = t.find_first_of(ALPHABET_CASEINSENSITIVE);
        t = t.substr(pos);
        pos = t.find_first_not_of(ALPHABET_CASEINSENSITIVE);
        this->resultType = t.substr(0,pos);
        t = t.substr(pos + 1);
    }

    //Given Values
    if(hasGivenValue(this->command)) {
        t = t.substr(1);
        pos = t.find_first_of(']');
        this->givenValue = t.substr(0,pos);
        t = t.substr(pos + 2);
    }

    //Function Name
    if(hasFunctionName(this->command)) {
        t = t.substr(1);
        pos = t.find_first_of(']');
        this->funcName = t.substr(0,pos);
        t = t.substr(pos + 2);
    }

    //Parent
    pos = t.find_first_of(ALPHABET_CASEINSENSITIVE);
    t = t.substr(pos);
    pos = t.find_first_of("=");
    t = t.substr(pos + 1);
    pos = t.find_first_of(" ");
    this->parentID = std::stoi(t.substr(0,pos));
    t = t.substr(pos);
}

THEInstructionType THEInstruction::getType() { 
    return command; 
}

std::string THEInstruction::getResultType() {
    return resultType;
}
    
std::string THEInstruction::getGivenValue() {
    return givenValue;
}

std::vector<int> THEInstruction::getArgs(){
    return args;
}

llvm::Value* THEInstruction::getResultIRInst(){
    return res;
}

void THEInstruction::setResultIRInst(llvm::Value* I){
    res = I;
}
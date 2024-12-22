#ifndef THE_INSTRUCTION_H
#define THE_INSTRUCTION_H

#include <llvm/IR/Module.h>
#include <llvm/IR/IRBuilder.h>

#include <string>
#include <vector>
#include <iostream>
#include <sstream>

#include "THE-LLVMTranslator.h"

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
    If,
    Else,
    Given,
    StartBlock,
    EndBlock,
    Loop,
    Break,

    UNKNOWN
};

class THEInstruction {
    THEInstructionType command;
    std::vector<int> args;
    std::string name;
    std::string resultType;
    std::string funcName;
    std::string givenValue;
    uint parentID;
    uint endID;
    uint previousIfID;
    uint elseID;


    llvm::Value* res = nullptr;

public:
    THEInstruction();

    THEInstruction(std::string);

    void parseInstruction(std::string);

    THEInstructionType getType();

    std::string getResultType();
    
    std::string getGivenValue();

    std::vector<int> getArgs();

    llvm::Value* getResultIRInst();

    void setResultIRInst(llvm::Value*);
};

extern std::vector<THEInstruction> insts;

#endif
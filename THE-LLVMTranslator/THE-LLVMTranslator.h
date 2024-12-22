#ifndef THE_LLVMTRANSLATOR_H
#define THE_LLVMTRANSLATOR_H

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


#endif
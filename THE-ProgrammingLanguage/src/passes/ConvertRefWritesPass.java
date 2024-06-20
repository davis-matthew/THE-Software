package passes;

import java.util.ArrayList;

import parsing.Instruction;
import parsing.InstructionType;
import parsing.Type;
import static parsing.ErrorHandler.*;

/*
This pass converts the following structure:

1  Given->Int '77'
2  Given->Int '88'
3  Given->Int '99'
4  Read(Int, Int)->Int [args 1, 2] (arr read) 'arr[77,88]'
5  WriteToReference(Int, Int) [args 4, 99] 'arr[77,88] = 99'

into:

1  Given->Int '77'
2  Given->Int '88'
3  Given->Int '99'
4  GetReference(Int, Int)->*Int [args 1, 2] (arr read) 'arr[77,88]'
5  WriteToReference(*Int, Int) [args 4, 99] (arr changed) 'arr[77,88] = 99'

*/

public class ConvertRefWritesPass {
	
	// Main pass:
	public static void convertReferenceWritesPass(ArrayList<Instruction> instructions) {
		for (int i = 0; i < instructions.size(); i++) {
			Instruction writeToRefInstr = instructions.get(i);
			if (writeToRefInstr.instructionType == InstructionType.WriteToReference) {
				
				// The first argument is always a Read instruction
				Instruction readInstr = writeToRefInstr.argReferences[0];
				
				if (readInstr.instructionType != InstructionType.Read) {
					printError("WriteToReference must take a Read operation as argument 0");
				}
				
				// Increase the pointer depth by one
				Type pointerReturnType = new Type(
						readInstr.returnType.baseType,
						readInstr.returnType.dimensions,
						readInstr.returnType.isArray,
						readInstr.returnType.pointerDepth + 1
						);
				
				// Replace the Read instruction with a GetReference instruction
				Instruction getRefInstr = new Instruction(InstructionType.GetReference);
				getRefInstr.stringRepresentation = readInstr.stringRepresentation;
				getRefInstr.variableThatWasRead = readInstr.variableThatWasRead;
				getRefInstr.setArgs(readInstr.argReferences);
				getRefInstr.returnType = pointerReturnType;
				getRefInstr.parentInstruction = readInstr.parentInstruction;
				
				int readInstrIndex = findBackward(instructions, readInstr, i);
				if (readInstrIndex == -1) {
					printError("Can't find Read instruction!");
				}
				
				instructions.set(readInstrIndex, getRefInstr);
				
				// Modify the pointer of the WriteToReference instruction to
				// point to this new instruction.
				writeToRefInstr.argReferences[0] = getRefInstr;
				writeToRefInstr.variableThatWasChanged = readInstr.variableThatWasRead;
				
				if (writeToRefInstr.variableThatWasChanged == null) {
					printError("No changed variable was found while converting reference writes");
				}
			}
		}
	}
	
	// Search from the current instruction back to the beginning
	private static int findBackward(ArrayList<Instruction> instructions, Instruction instr, int startIndex) {
		for (int i = startIndex; i >= 0; i--) {
			if (instructions.get(i) == instr) {
				return i;
			}
		}
		return -1;
	}
	
}

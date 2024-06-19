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
5  WriteToReference(*Int, Int) [args 4, 99] 'arr[77,88] = 99'

*/

public class ConvertRefWritesPass {
	
	public static void convertReferenceWritesPass(ArrayList<Instruction> instructions) {
		for (int i = 0; i < instructions.size(); i++) {
			Instruction instr = instructions.get(i);
			if (instr.instructionType == InstructionType.WriteToReference) {
				
				// The first argument is always a Read instruction
				Instruction readInstr = instr.argReferences[0];
				
				if (readInstr.instructionType != InstructionType.Read) {
					printError("WriteToReference must take a Read operation as argument 0");
				}
				
				// Replace the Read instruction with a GetReference instruction
				
				Type pointerReturnType = readInstr.returnType;
				pointerReturnType.isPointer = true;
				
				Instruction refInstr = new Instruction(InstructionType.GetReference);
				refInstr.stringRepresentation = readInstr.stringRepresentation;
				refInstr.setArgs(readInstr.argReferences);
				refInstr.returnType = pointerReturnType;
				refInstr.parentInstruction = readInstr.parentInstruction;
				
				int readInstrIndex = findBackward(instructions, readInstr, i);
				if (readInstrIndex == -1) {
					printError("Can't find Read instruction!");
				}
				
				instructions.set(readInstrIndex, refInstr);
			}
		}
	}
	
	private static int findBackward(ArrayList<Instruction> instructions, Instruction instr, int startIndex) {
		for (int i = startIndex; i >= 0; i--) {
			if (instructions.get(i) == instr) {
				return i;
			}
		}
		return -1;
	}
	
}

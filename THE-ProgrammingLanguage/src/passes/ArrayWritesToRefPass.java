package passes;

import java.util.ArrayList;

import instructions.Instruction;
import parsing.Type;

import static parsing.ErrorHandler.*;

/*

This pass converts a Store on an element of an array into
a GetReference followed by a WriteToReference.

For example:

1   Given->int '77'
2   Given->int '6'
3   Store(int 1, int 2) (arr changed) 'arr[6] = 77'

is changed into:

1   Given->int '77'
2   Given->int '6'
4   GetReference(Int 1)->*Int (arr read) 'arr[77]'
5   WriteToReference(*Int 4, Int 1) (arr changed) 'arr[77] = 77'

*/

public class ArrayWritesToRefPass {
	
	// Main pass call:
	public static void convertReferenceWritesPass(ArrayList<Instruction> instructions) {
		/*
		for (int i = 0; i < instructions.size(); i++) {
			Instruction reassignInstr = instructions.get(i);
			if (reassignInstr.instructionType == InstructionType.Store) {
				
				Type varType = reassignInstr.instructionThatDeclaredVariable.newVarType;
				
				if (varType.isArray) {
					
					if (varType.dimensions != 1) {
						printError("Multidimensional arrays not supported yet by convertReferenceWritesPass");
					}
					
					// Remove the "Reassign" instruction
					instructions.remove(i);
					
					// Create a pointer to the array's element
					Type pointerReturnType = new Type(
							varType.baseType, 0, false,
							varType.pointerDepth + 1);
	
					// The index=0 argument of the Reassign is always the value to assign to the element
					Instruction arrayNewValueInstruction = reassignInstr.args[0];
					
					// The index=1 argument of the Reassign is always the index into the array
					Instruction arrayIndexInstruction = reassignInstr.args[1];
					
					// Create the GetReference instruction
					Instruction getRefInstr = new Instruction(InstructionType.GetArrayIndex);
					getRefInstr.debugString = reassignInstr.debugString;
					getRefInstr.setArgs(arrayIndexInstruction);
					getRefInstr.returnType = pointerReturnType;
					getRefInstr.parentInstruction = reassignInstr.parentInstruction;
					instructions.add(i, getRefInstr);
					
					// Create the WriteToReference instruction
					Instruction writeToRefInstr = new Instruction(InstructionType.WriteToReference);
					writeToRefInstr.debugString = reassignInstr.debugString;
					writeToRefInstr.setArgs(getRefInstr, arrayNewValueInstruction);
					writeToRefInstr.parentInstruction = reassignInstr.parentInstruction;
					instructions.add(i + 1, writeToRefInstr);
				}
			}
		}
		//*/
	}
	
}

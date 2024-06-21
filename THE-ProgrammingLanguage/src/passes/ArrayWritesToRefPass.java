package passes;

import java.util.ArrayList;

import parsing.Instruction;
import parsing.InstructionType;
import parsing.Type;
import parsing.Variable;

import static parsing.ErrorHandler.*;

/*

This pass converts a Reassign on an element of an array into
a GetReference followed by a WriteToReference.

For example:

1   Given->int '77'
2   Given->int '6'
3   Reassign(int 1, int 2) (arr changed) 'arr[6] = 77'

is changed into:

1   Given->int '77'
2   Given->int '6'
4   GetReference(Int 1)->*Int (arr read) 'arr[77]'
5   WriteToReference(*Int 4, Int 1) (arr changed) 'arr[77] = 77'

*/

public class ArrayWritesToRefPass {
	
	// Main pass call:
	public static void convertReferenceWritesPass(ArrayList<Instruction> instructions) {
		for (int i = 0; i < instructions.size(); i++) {
			Instruction reassignInstr = instructions.get(i);
			if (reassignInstr.instructionType == InstructionType.Reassign &&
					reassignInstr.variableThatWasChanged.type.isArray) {
				
				Variable arrVar = reassignInstr.variableThatWasChanged;
				
				if (arrVar.type.dimensions != 1) {
					printError("Multidimensional arrays not supported yet by convertReferenceWritesPass");
				}
				
				// Remove the "Reassign" instruction
				instructions.remove(i);
				
				// Create a pointer to the array's element
				Type pointerReturnType = new Type(
						arrVar.type.baseType, 0, false,
						arrVar.type.pointerDepth + 1);

				// The index=0 argument of the Reassign is always the value to assign to the element
				Instruction arrayNewValueInstruction = reassignInstr.args[0];
				
				// The index=1 argument of the Reassign is always the index into the array
				Instruction arrayIndexInstruction = reassignInstr.args[1];
				
				// Create the GetReference instruction
				Instruction getRefInstr = new Instruction(InstructionType.GetReference);
				getRefInstr.stringRepresentation = reassignInstr.stringRepresentation;
				getRefInstr.variableThatWasRead = arrVar;
				getRefInstr.setArgs(arrayIndexInstruction);
				getRefInstr.returnType = pointerReturnType;
				getRefInstr.parentInstruction = reassignInstr.parentInstruction;
				instructions.add(i, getRefInstr);
				
				// Create the WriteToReference instruction
				Instruction writeToRefInstr = new Instruction(InstructionType.WriteToReference);
				writeToRefInstr.stringRepresentation = reassignInstr.stringRepresentation;
				writeToRefInstr.variableThatWasChanged = arrVar;
				writeToRefInstr.setArgs(getRefInstr, arrayNewValueInstruction);
				writeToRefInstr.parentInstruction = reassignInstr.parentInstruction;
				instructions.add(i + 1, writeToRefInstr);
			}
		}
	}
	
}

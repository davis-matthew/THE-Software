package passes;

import java.util.ArrayList;

import instructions.Instruction;
import instructions.StoreInstr;

/* This pass removes unused instructions from the program.
For example:

0   AllocVar (arr declared)->int[]* 'int[] arr'
1   Given->int '6'
2   AllocArr(int 1)->int[] 'int[6]'
3   Identity(int[]* 0)->int[]* 'arr'
4   Store(int[]* 0, int[] 2) 'arr = int[6]'
5   Given->int '63'
6   GetElement(int[]* 0, int 5)->int* 'arr[63]'
7   Load(int* 6)->int 'arr[63]'
8   ToString(int 7)->string 'arr[63]'
9   Print(string 8) 'arr[63]'

is converted to:

0   AllocVar (arr declared)->int[]* 'int[] arr'
1   Given->int '6'
2   AllocArr(int 1)->int[] 'int[6]'
4   Store(int[]* 0, int[] 2) 'arr = int[6]'
5   Given->int '63'
6   GetElement(int[]* 0, int 5)->int* 'arr[63]'
8   ToString(int 7)->string 'arr[63]'
9   Print(string 8) 'arr[63]'

*/

public class DeleteUnusedInstructionsPass {
	
	// Main call to this pass:
	public static void deleteUnusedInstructions(ArrayList<Instruction> instructions) {
		
		for (int i = instructions.size() - 1; i >= 0; i--) {
			Instruction instr = instructions.get(i);
			
			// Don't remove instructions that modify variable scope,
			// have non-obvious side effects, or don't return anything to use anyway.
			if (!instr.doesStartScope() &&
				!instr.doesEndScope() &&
				!instr.isJump() &&
				!instr.hasGlobalSideEffect(instructions) &&
				!(instr instanceof StoreInstr)) { // TODO sometimes StoreInstr can be optimized out
				
				// If this instruction does not have any references,
				// then it can be optimized out.
				if (!doesInstructionHaveReference(instructions, instr)) {
					instructions.remove(i);
				}
			}
		}
		
		// TODO remove unused functions
	}
	
	// Return true if any other instruction has this instruction as an argument
	private static boolean doesInstructionHaveReference(ArrayList<Instruction> instructions, Instruction instr) {
		
		for (int i = 0; i < instructions.size(); i++) {
			Instruction[] otherArgs = instructions.get(i).getAllArgs();
			for (int j = 0; j < otherArgs.length; j++) {
				if (otherArgs[j] == instr) {
					return true;
				}
			}
		}
		
		return false;
	}
}

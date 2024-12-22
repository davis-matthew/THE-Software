package passes;

import java.util.ArrayList;

import instructions.FunctionDefInstr;
import instructions.IfInstr;
import instructions.Instruction;
import instructions.ReturnInstr;

// This class checks that functions with returns have return statements on all paths

public class CheckReturnPathsPass {
	
	private static int stackHead = -1;
	private static int[] instructionStack = new int[1024];
	
	// Main call to this pass:
	public static void checkReturnPaths(ArrayList<Instruction> instructions) {
		
		stackHead = -1;
		
		// Find each non-void function
		for (int i = 0; i < instructions.size(); i++) {
			Instruction instr = instructions.get(i);
			if (instr instanceof FunctionDefInstr) {
				FunctionDefInstr funcDefInstr = (FunctionDefInstr)instr;
				if (funcDefInstr.functionThatWasDefined.returnType != null) {
					
					// Find the end of this function
					int end = 0;
					while (end < instructions.size() && funcDefInstr.isAncestorOf(instructions.get(end))) {
						end++;
					}
					
					checkAllPaths(instructions, funcDefInstr, i, end);
				}
			}
		}
	}
	
	private static void checkAllPaths(ArrayList<Instruction> instructions, 
			FunctionDefInstr funcDefInstr, int start, int end) {
		
		stackHead++;
		instructionStack[stackHead] = start + 1;
		
		while (true) {
			int nextInstructionIndex = instructionStack[stackHead];
			while (nextInstructionIndex < end) {
				Instruction instr = instructions.get(nextInstructionIndex);
				
				// If we hit a return instruction, then this is the end of this search branch
				if (instr instanceof ReturnInstr) {
					stackHead--;
					if (stackHead < 0) {
						return;
					}
				} else if (instr instanceof IfInstr) {
					IfInstr ifInstr = (IfInstr)instr;
					
					if (ifInstr.elseInstr != null) {
						stackHead++;
						instructionStack[stackHead] = nextInstructionIndex + 1;
						
						stackHead++;
						instructionStack[stackHead] = -1;// TODO get the index of the instruction after ElseInstr;
					}
				}
				
				nextInstructionIndex++;
			}
		}
	}
}

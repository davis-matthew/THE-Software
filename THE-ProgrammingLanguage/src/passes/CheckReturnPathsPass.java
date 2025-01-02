package passes;

import java.util.ArrayList;

import instructions.*;
import static parsing.ErrorHandler.printError;

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
				
				// If this is a non-void function, then check all paths for proper return types.
				if (funcDefInstr.functionThatWasDefined.returnType != null) {
					
					// Find the end of this function
					int end = i + 1;
					while (end < instructions.size() && funcDefInstr.isAncestorOf(instructions.get(end))) {
						end++;
					}
					
					checkAllPaths(instructions, funcDefInstr, i, end);
					
				}
			}
		}
	}
	
	// TODO mark all reached instructions as "checked" here. If any remain unchecked, then we have dead code.
	private static void checkAllPaths(ArrayList<Instruction> instructions, 
			FunctionDefInstr funcDefInstr, int start, int end) {
		
		stackHead++;
		instructionStack[stackHead] = start + 1;
		
		// Keep searching until all branches on the stack are exhausted. (Remember... 0 is a valid stack element.)
		while (stackHead >= 0) {
			
			// Pop the next branch to search
			int nextInstructionIndex = instructionStack[stackHead];
			stackHead--;
			print("starting index " + nextInstructionIndex);
			
			// If we are starting just inside an if-statement, then find the else that we need to skip over.
			ElseInstr elseToSkip = null;
			if (nextInstructionIndex >= 1) {
				Instruction instr = instructions.get(nextInstructionIndex - 1);
				if (instr instanceof IfInstr) {
					elseToSkip = ((IfInstr)instr).elseInstr;
				}
			}
			
			// Search this branch
			boolean didFindReturn = false;
			while (nextInstructionIndex < end) {
				Instruction instr = instructions.get(nextInstructionIndex);
				
				// If we hit a return instruction, then this is the end of this search branch
				if (instr instanceof ReturnInstr) {
					didFindReturn = true;
					break; // Now go to the next branch on the stack to search
				} else if (instr instanceof IfInstr) {
					IfInstr ifInstr = (IfInstr)instr;
					
					// If this is just an If with no Else...
					if (ifInstr.elseInstr == null) {
						
						// One branch starts just inside the IF
						stackHead++;
						instructionStack[stackHead] = nextInstructionIndex + 1;
						
						// Bump us past this if block to simulate it not being triggered
						int endOfIf = instructions.indexOf(ifInstr.endOfBlockInstr);
						if (endOfIf == -1) {
							printError("EndBlock not found in instruction list after " + ifInstr, ifInstr.originalLineNumber);
						}
						nextInstructionIndex = endOfIf;
						
					} else { // This if has an else, so add both branches
						
						// One branch starts just inside the IF
						stackHead++;
						instructionStack[stackHead] = nextInstructionIndex + 1;

						// Bump our current search past the content of the IF into the ELSE block to assume it doesn't get triggered
						int startOfElse = instructions.indexOf(ifInstr.elseInstr);
						if (startOfElse == -1) {
							printError("Else-block not found in instruction list after " + ifInstr, ifInstr.originalLineNumber);
						}
						nextInstructionIndex = startOfElse;
					}
				} else if (instr instanceof ElseInstr) {
					ElseInstr elseInstr = (ElseInstr)instr;
					
					// If this is the else instruction that we need to skip, then move us past it
					if (instr == elseToSkip) {
						int endOfElse = instructions.indexOf(elseInstr.endOfBlockInstr);
						if (endOfElse == -1) {
							printError("EndBlock not found in instruction list after " + elseInstr, elseInstr.originalLineNumber);
						}
						nextInstructionIndex = endOfElse + 1;
					}
				}
				
				// TODO mark this instruction as having been "checked", for efficiency, and for dead code detection
				
				nextInstructionIndex++;
			}
			
			// If we hit the end of the function, then that's bad. We should have hit a return first.
			if (!didFindReturn) {
				printError("Non-void function requires a return", funcDefInstr.originalLineNumber);
			}
		}
	}
	
	static void print(Object o) {
		System.out.println(o);
	}
}

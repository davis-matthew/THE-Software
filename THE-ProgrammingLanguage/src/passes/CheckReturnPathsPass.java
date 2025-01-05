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
					checkAllPaths(instructions, funcDefInstr, i + 1);
				}
			}
		}
	}
	
	// TODO mark all reached instructions as "checked" here. If any remain unchecked, then we have dead code.
	private static void checkAllPaths(ArrayList<Instruction> instructions, 
			FunctionDefInstr funcDefInstr, int start) {
		
		// Create an array that records whether each instruction has already been checked.
		final boolean[] wasChecked = new boolean[instructions.size()];
		
		// Push the first instruction to search onto the stack
		stackHead++;
		instructionStack[stackHead] = start;
		
		// Keep searching until all branches on the stack are exhausted. (Remember... 0 is a valid stack element.)
		while (stackHead >= 0) {
			
			// Pop the next branch to search
			int nextInstructionIndex = instructionStack[stackHead];
			stackHead--;
			
			// Search this branch
			while (true) {
				
				// If this instruction was already checked, then we can skip this whole branch.
				if (wasChecked[nextInstructionIndex]) {
					break;
				}
				wasChecked[nextInstructionIndex] = true;
				
				Instruction instr = instructions.get(nextInstructionIndex);
				
				// If we hit a return instruction, then this is the end of this search branch
				if (instr instanceof ReturnInstr) {
					
					break; // Now go to the next branch on the stack to search
					
				} else if (instr instanceof EndBlockInstr) {
					
					if (instr.parentInstruction instanceof IfInstr) {
						
						IfInstr ifInstr = (IfInstr)instr.parentInstruction;
						if (ifInstr.elseInstr == null) {
							// Implicitly jump to just after this IF-block
						} else {
							
							// Jump to just after the end of the ELSE block
							int endOfElse = instructions.indexOf(ifInstr.elseInstr.endOfBlockInstr);
							if (endOfElse == -1) {
								printError("Else-block not found in instruction list after " + ifInstr, ifInstr.originalLineNumber);
							}
							nextInstructionIndex = endOfElse;
							
						}
						
					} else if (instr.parentInstruction instanceof ElseInstr) {
						
						// Jump to just after the IF-ELSE chain.
						// This happens implicitly here.
					
					} else if (instr.parentInstruction instanceof FunctionDefInstr) {
						
						// If we got to the end of the non-void function, then that's bad.
						printError("Non-void function requires a return", funcDefInstr.originalLineNumber);
					}
					
				} else if (instr instanceof IfInstr) {
					IfInstr ifInstr = (IfInstr)instr;
					
					// If this is just an IF with no ELSE...
					if (ifInstr.elseInstr == null) {
						
						// The other branch starts just after the IF
						int endOfIf = instructions.indexOf(ifInstr.endOfBlockInstr);
						if (endOfIf == -1) {
							printError("EndBlock not found in instruction list after " + ifInstr, ifInstr.originalLineNumber);
						}
						stackHead++;
						instructionStack[stackHead] = endOfIf + 1;
						
					} else { // This IF has an else
						
						// The second branch starts just inside the ELSE
						int startOfElse = instructions.indexOf(ifInstr.elseInstr);
						if (startOfElse == -1) {
							printError("Else-block not found in instruction list after " + ifInstr, ifInstr.originalLineNumber);
						}
						stackHead++;
						instructionStack[stackHead] = startOfElse + 1;
					}
				} else if (instr instanceof BreakInstr ||
						   instr instanceof ContinueInstr) {
					
					LoopInstr loopStartInstr = null;
					if (instr instanceof BreakInstr) {
						loopStartInstr = ((BreakInstr)instr).loopStartInstr;
					} else if (instr instanceof ContinueInstr) {
						loopStartInstr = ((ContinueInstr)instr).loopStartInstr;
					}
					
					// If we hit a break, the jump to the exit of the loop
					// The second branch starts just inside the ELSE
					int endOfLoop = instructions.indexOf(loopStartInstr.endInstr);
					if (endOfLoop == -1) {
						printError("Loop-End not found in instruction list after " + instr, instr.originalLineNumber);
					}
					nextInstructionIndex = endOfLoop;
					
				} else if (instr instanceof ElseInstr) {
					printError("Error: found invalid ElseInstr at " + nextInstructionIndex, instr.originalLineNumber);
				}
				
				nextInstructionIndex++;
			}
		}
		
		// Check if there were any instructions not covered by all branches.
		// It must be dead code.
		for (int i = 0; i < wasChecked.length; i++) {
			if (!wasChecked[i]) {
				Instruction instr = instructions.get(i);
				
				// Ignore function definitions and other non-executable instructions
				if (!(instr instanceof FunctionDefInstr) &&
					!(instr instanceof EndBlockInstr)&&
					!(instr instanceof ElseInstr)) {
					
					printError("Dead code at " + instr, instr.originalLineNumber);
				}
			}
		}
		
	}
	
	static void print(Object o) {
		System.out.println(o);
	}
}

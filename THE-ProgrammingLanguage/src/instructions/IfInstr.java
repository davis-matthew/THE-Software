package instructions;

import static parsing.ErrorHandler.*;

import parsing.BaseType;

public class IfInstr extends Instruction {
	
	// The end of an If may be an ElseIf, an Else, or an EndBlock.
	// The end instruction associated with this beginning instruction.
	public Instruction endOfBlockInstr = null;
	
	// The else-block of this if-else chain, if present.
	public Instruction elseInstr = null;
	
	// The condition that triggers the if-statement
	public Instruction conditionInstr;
	
	// Regarding 'wasThisAnElseIfBlock' below:
	// When generating if-elseif-else chains, the else-if blocks are converted to if-blocks nested inside else-blocks.
	// So when closing the inner if block, we need to add an extra closing bracket for the extra else-block
	// that was injected.
	/* For example:
	if cond1
		print(1)
	] elseif cond2
		print(2)
	]
	
	becomes:
	
	if cond1
		print(1)
	] else
		if cond2
			print(2)
		] // This one needs to be inserted!
	]
	*/
	public boolean wasThisAnElseIfBlock;
	
	public IfInstr(Instruction parentInstruction, String debugString, Instruction conditionInstr, boolean wasThisAnElseIfBlock) {
		
		super(parentInstruction, null, debugString);
		this.conditionInstr = conditionInstr;
		this.wasThisAnElseIfBlock = wasThisAnElseIfBlock;
		
		if (conditionInstr.returnType == null) {
			printError("If-condition must be a bool. (Got void.)");
		}
		
		if (!conditionInstr.returnType.isA(BaseType.Bool)) {
			printError("If-condition cannot take a " + conditionInstr.returnType + " type.");
		}
	}
	
}

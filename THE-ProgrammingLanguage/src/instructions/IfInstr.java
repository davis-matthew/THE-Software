package instructions;

import static parsing.ErrorHandler.*;

import parsing.BaseType;

public class IfInstr extends Instruction {
	
	// The end of an If may be an ElseIf, an Else, or an EndBlock.
	// The end instruction associated with this beginning instruction.
	public Instruction endChainInstruction = null;
	
	// The next instruction in a mandatory chain (if-elseif-else chains only)
	public Instruction nextChainedInstruction = null;
	
	// The condition that triggers the if-statement
	public Instruction conditionInstr;
	
	public IfInstr(Instruction parentInstruction, String debugString, Instruction conditionInstr,
			Instruction nextChainedInstruction, EndBlockInstr endChainInstruction) {
		
		super(parentInstruction, null, debugString);
		this.endChainInstruction = endChainInstruction;
		this.nextChainedInstruction = nextChainedInstruction;
		this.conditionInstr = conditionInstr;
		
		if (conditionInstr.returnType == null) {
			printError("If-condition cannot be void");
		}
		
		if (conditionInstr.returnType.isA(BaseType.Bool)) {
			printError("If-condition cannot take a " + conditionInstr.returnType + " type");
		}
	}
	
}

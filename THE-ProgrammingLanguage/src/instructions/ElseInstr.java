package instructions;

public class ElseInstr extends Instruction {
	
	// The end of an If may be an ElseIf, an Else, or an EndBlock.
	// The end instruction associated with this beginning instruction.
	public Instruction endChainInstruction = null;
	
	public ElseInstr(Instruction parentInstruction, String debugString, EndBlockInstr endChainInstruction) {
		super(parentInstruction, null, debugString);
		this.endChainInstruction = endChainInstruction;
	}
	
}

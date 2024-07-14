package instructions;

public class ElseInstr extends Instruction {
	
	// This is the end of the else-block.
	public EndBlockInstr endOfBlockInstr = null;
	
	// Reference to the if-statement that this belongs to.
	public IfInstr previousIfInstr;
	
	public ElseInstr(Instruction parentInstruction, String debugString, IfInstr previousIfInstr) {
		super(parentInstruction, null, debugString);
		this.previousIfInstr = previousIfInstr;
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				previousIfInstr, endOfBlockInstr
		};
	}
	
}

package instructions;

import parsing.Type;

public class ToStringInstr extends Instruction {
	
	public Instruction arg;
	
	public ToStringInstr(Instruction parentInstruction, String debugString, Instruction arg) {
		super(parentInstruction, Type.String, debugString);
		this.arg = arg;
	}
	
}

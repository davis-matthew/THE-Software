package instructions;

import parsing.Compiler;

public class BoolNotInstr extends Instruction {
	
	public Instruction arg;
	
	public BoolNotInstr(Instruction parentInstruction, String debugString, Instruction arg) {
		super(parentInstruction, null, debugString);
		this.arg = arg;
		this.returnType = Compiler.getReturnTypeFromInstructionAndOperands(this, arg.returnType, null);
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				arg
		};
	}
	
}

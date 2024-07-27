package instructions;

import parsing.CompilePass;

public class BitNotInstr extends Instruction {
	
	public Instruction arg;
	
	public BitNotInstr(Instruction parentInstruction, String debugString, Instruction arg) {
		super(parentInstruction, null, debugString);
		this.arg = arg;
		this.returnType = CompilePass.getReturnTypeFromInstructionAndOperands(this, arg.returnType, null);
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				arg
		};
	}
	
}

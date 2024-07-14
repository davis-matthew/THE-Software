package instructions;

import parsing.Compiler;

public class BitNotInstr extends Instruction {
	
	public Instruction arg;
	
	public BitNotInstr(Instruction parentInstruction, String debugString, Instruction arg) {
		super(parentInstruction, null, debugString);
		this.arg = arg;
		this.returnType = Compiler.getReturnTypeFromInstructionAndOperands(this, arg.returnType, null);
	}
	
}

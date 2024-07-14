package instructions;

import parsing.Compiler;

public class GreaterInstr extends Instruction {
	
	public Instruction arg1;
	public Instruction arg2;
	
	public GreaterInstr(Instruction parentInstruction, String debugString, Instruction arg1, Instruction arg2) {
		super(parentInstruction, null, debugString);
		this.arg1 = arg1;
		this.arg2 = arg2;
		this.returnType = Compiler.getReturnTypeFromInstructionAndOperands(this, arg1.returnType, arg2.returnType);
	}
	
}
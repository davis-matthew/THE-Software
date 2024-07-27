package instructions;

import parsing.CompilePass;

public class DivideInstr extends Instruction {
	
	public Instruction arg1;
	public Instruction arg2;
	
	public DivideInstr(Instruction parentInstruction, String debugString, Instruction arg1, Instruction arg2) {
		super(parentInstruction, null, debugString);
		this.arg1 = arg1;
		this.arg2 = arg2;
		this.returnType = CompilePass.getReturnTypeFromInstructionAndOperands(this, arg1.returnType, arg2.returnType);
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				arg1, arg2
		};
	}
	
}

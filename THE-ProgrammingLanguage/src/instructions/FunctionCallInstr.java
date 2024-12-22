package instructions;

import parsing.Function;

public class FunctionCallInstr extends Instruction {

	public Function functionThatWasCalled; // Reference to the function that was called
	public Instruction[] args;
	
	public FunctionCallInstr(Instruction parentInstruction, String debugString,
					Function functionThatWasCalled, Instruction[] args) {
		
		super(parentInstruction, null, debugString);
		this.functionThatWasCalled = functionThatWasCalled;
		this.args = args;
		this.returnType = functionThatWasCalled.returnType;
	}
	
	public Instruction[] getAllArgs() {
		return args;
	}
	
}

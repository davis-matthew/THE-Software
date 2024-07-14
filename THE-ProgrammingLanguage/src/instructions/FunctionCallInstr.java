package instructions;

import parsing.Function;

public class FunctionCallInstr extends Instruction {

	public Function functionThatWasCalled; // Reference to the function that was called
	
	public FunctionCallInstr(Instruction parentInstruction, String debugString, Function functionThatWasCalled) {
		
		super(parentInstruction, null, debugString);
		this.functionThatWasCalled = functionThatWasCalled;
	}
	
}

package instructions;

import parsing.Function;

public class FunctionDefInstr extends Instruction {

	public EndBlockInstr endBlockInstr; // Reference to the end of the function
	
	public Function functionThatWasDefined; // Reference to the function that was declared
	
	public FunctionDefInstr(Instruction parentInstruction, String debugString, Function functionThatWasDefined) {
		super(parentInstruction, null, debugString);
		this.functionThatWasDefined = functionThatWasDefined;
	}
	
}

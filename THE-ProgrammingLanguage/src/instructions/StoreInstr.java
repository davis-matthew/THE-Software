package instructions;

import static parsing.ErrorHandler.*;

public class StoreInstr extends Instruction {

	public DeclareInstr declareInstr; // The instruction that declared the address to store to
	public Instruction valueToStore; // The instruction that returns the value to store
	
	public StoreInstr(Instruction parentInstruction, String debugString, DeclareInstr declareInstr, Instruction valueToStore) {
		super(parentInstruction, null, debugString);
		this.declareInstr = declareInstr;
		this.valueToStore = valueToStore;
		
		if (valueToStore.returnType == null) {
			printError("Cannot read from void");
		}
	}
	
}

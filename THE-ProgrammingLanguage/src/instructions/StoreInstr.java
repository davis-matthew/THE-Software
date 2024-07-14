package instructions;

import static parsing.ErrorHandler.*;

public class StoreInstr extends Instruction {

	public Instruction instrThatReturnedPointer; // The instruction that returned the address to store to
	public Instruction valueToStore; // The instruction that returns the value to store
	
	public StoreInstr(Instruction parentInstruction, String debugString, Instruction instrThatReturnedPointer, Instruction valueToStore) {
		super(parentInstruction, null, debugString);
		this.instrThatReturnedPointer = instrThatReturnedPointer;
		this.valueToStore = valueToStore;
		
		if (!instrThatReturnedPointer.returnType.isPointer()) {
			printError("StoreInstr must reference an instruction that returns a pointer, " +
					"not a " + instrThatReturnedPointer.returnType);
		}
		
		if (valueToStore.returnType == null) {
			printError("Cannot read from void");
		}
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				instrThatReturnedPointer, valueToStore
		};
	}
	
}

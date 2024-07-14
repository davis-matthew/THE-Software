package instructions;

import parsing.Type;
import static parsing.ErrorHandler.*;

public class LoadInstr extends Instruction {

	public Instruction instrThatReturnedPointer;
	
	private static Type getTypeFromPointer(Instruction instr) {
		if (instr.returnType.isPointer()) {
			return instr.returnType.makeTypePointedToByThis();
		}
		printError("LoadInstr must reference an instruction that returns a pointer, " +
				"not a " + instr.returnType);
		return null;
	}
	
	public LoadInstr(Instruction parentInstruction, String debugString, Instruction instrThatReturnedPointer) {
		super(parentInstruction, getTypeFromPointer(instrThatReturnedPointer), debugString);
		this.instrThatReturnedPointer = instrThatReturnedPointer;
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				instrThatReturnedPointer
		};
	}
	
}

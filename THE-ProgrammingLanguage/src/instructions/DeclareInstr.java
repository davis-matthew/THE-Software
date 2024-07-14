package instructions;

import parsing.Type;

public class DeclareInstr extends Instruction {
	
	public Type varType;
	public String varName; // Name of the variable that was created during this declaration
	
	public DeclareInstr(Instruction parentInstruction, String debugString, Type varType, String varName) {
		super(parentInstruction, varType.makePointerToThis(), debugString);
		this.varType = varType;
		this.varName = varName;
	}
	
}

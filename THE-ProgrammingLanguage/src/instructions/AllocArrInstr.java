package instructions;

import parsing.Type;

public class AllocArrInstr extends Instruction {
	
	public Instruction[] dimensionSizes; // Size of this array in every dimension
	
	public AllocArrInstr(Instruction parentInstruction, String debugString, Type elementType, Instruction[] dimensionSizes) {
		super(parentInstruction, elementType.makeArrayOfThis(dimensionSizes.length), debugString);
		this.dimensionSizes = dimensionSizes;
	}
	
	public Instruction[] getAllArgs() {
		return dimensionSizes;
	}
	
}

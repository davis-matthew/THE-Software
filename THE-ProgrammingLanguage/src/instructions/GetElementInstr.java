package instructions;

import static parsing.ErrorHandler.*;

public class GetElementInstr extends Instruction {
	
	public AllocVarInstr declareInstr;
	
	public Instruction[] instructionsForIndices; // Which index in each dimension to read from the array
	
	public GetElementInstr(Instruction parentInstruction, String debugString,
			AllocVarInstr declareInstr, Instruction[] instructionsForIndices) {
		
		super(parentInstruction, declareInstr.varType.getArrayElementType().makePointerToThis(), debugString);
		this.declareInstr = declareInstr;
		this.instructionsForIndices = instructionsForIndices;
		
		if (!declareInstr.varType.isArray) {
			printError("Cannot read index from non-array type");
		}
		
		// If we have an array size mismatch
		if (instructionsForIndices.length != declareInstr.varType.dimensions) {
			printError("Cannot retrieve " + instructionsForIndices.length + "D index from " +
						declareInstr.varType.dimensions + "D array");
		}
	}
	
	public Instruction[] getAllArgs() {
		Instruction[] instructions = new Instruction[instructionsForIndices.length + 1];
		instructions[0] = declareInstr;
		for (int i = 0; i < instructionsForIndices.length; i++) {
			instructions[i + 1] = instructionsForIndices[i];
		}
		return instructions;
	}
	
}

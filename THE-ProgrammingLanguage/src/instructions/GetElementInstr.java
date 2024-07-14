package instructions;

import static parsing.ErrorHandler.*;

public class GetElementInstr extends Instruction {
	
	public DeclareInstr declareInstr;
	
	public Instruction[] instructionsForIndices; // Which index in each dimension to read from the array
	
	public GetElementInstr(Instruction parentInstruction, String debugString,
			DeclareInstr declareInstr, Instruction[] instructionsForIndices) {
		
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
	
}

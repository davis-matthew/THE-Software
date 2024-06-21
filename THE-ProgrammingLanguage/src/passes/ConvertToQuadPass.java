package passes;

import java.util.ArrayList;

import parsing.Instruction;
import parsing.QuadInstruction;

// This pass converts all Instructions to QuadInstructions

public class ConvertToQuadPass {
	
	// Main pass call:
	public static ArrayList<QuadInstruction> convertToQuadInstructions(ArrayList<Instruction> instructions) {
		
		ArrayList<QuadInstruction> quadInstructions = new ArrayList<QuadInstruction>(instructions.size());
		
		for (int i = 0; i < instructions.size(); i++) {
			QuadInstruction newInstruction = convertToQuad(instructions.get(i));
			if (newInstruction != null) {
				quadInstructions.add(newInstruction);
			}
		}
		
		return quadInstructions;
	}
	
	// Convert a single Instruction to a QuadInstruction.
	// May return null.
	private static QuadInstruction convertToQuad(Instruction instruction) {
		
		// TODO Convert to QuadIR
		
		return null;
	}
	
}

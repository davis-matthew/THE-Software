package instructions;

import parsing.Type;
import static parsing.ErrorHandler.*;

public class ArrLengthInstr extends Instruction {
	
	public IdentityInstr pointerInstr; // Reference to the instruction that retrieved the pointer to this array
	
	public Instruction dimensionToRead; // Which dimension of the array to get the length of (starting at 0)
	public boolean getElementCount; // Whether to get the number of elements in this array (counting all dimensions)
	
	public ArrLengthInstr(Instruction parentInstruction, String debugString,
			IdentityInstr pointerInstr, Instruction dimensionToRead, boolean getElementCount) {
		
		super(parentInstruction, Type.Int, debugString);
		this.pointerInstr = pointerInstr;
		this.getElementCount = getElementCount;
		this.dimensionToRead = dimensionToRead;
		
		if (dimensionToRead != null) {
			if (getElementCount) {
				printError("Cannot get full element count of non-zero dimension");
			}
		}
		
		if (pointerInstr.returnType == null) {
			printError("Cannot read length of void");
		}
		
		if (!pointerInstr.returnType.isArray) {
			printError("Cannot read length of non-array type");
		}
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				pointerInstr,
				dimensionToRead
		};
	}
	
}

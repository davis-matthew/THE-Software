package instructions;

public class GetPointerInstr extends Instruction {
	
	public DeclareInstr declareInstr;
	
	public GetPointerInstr(Instruction parentInstruction, String debugString, DeclareInstr declareInstr) {
		super(parentInstruction, declareInstr.varType.makePointerToThis(), debugString);
	}
	
}

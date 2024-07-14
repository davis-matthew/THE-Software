package instructions;

public class LoadInstr extends Instruction {

	public DeclareInstr declareInstr;
	
	public LoadInstr(Instruction parentInstruction, String debugString, DeclareInstr declareInstr) {
		super(parentInstruction, declareInstr.varType, debugString);
		this.declareInstr = declareInstr;
	}
	
}

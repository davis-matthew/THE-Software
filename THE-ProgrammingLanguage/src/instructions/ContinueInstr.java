package instructions;

public class ContinueInstr extends Instruction {

	public LoopInstr loopStartInstr; // The parent loop header to jump to
	
	public ContinueInstr(Instruction parentInstruction, String debugString, LoopInstr loopStartInstr) {
		super(parentInstruction, null, debugString);
		this.loopStartInstr = loopStartInstr;
	}
	
}

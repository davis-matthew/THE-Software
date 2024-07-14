package instructions;

public class BreakInstr extends Instruction {

	public LoopInstr loopStartInstr; // The parent loop to break
	
	public BreakInstr(Instruction parentInstruction, String debugString, LoopInstr loopStartInstr) {
		super(parentInstruction, null, debugString);
		this.loopStartInstr = loopStartInstr;
	}
	
}

package instructions;

public class EndBlockInstr extends Instruction {

	public EndBlockInstr(Instruction parentInstruction, String debugString) {
		super(parentInstruction, null, debugString);
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				
		};
	}
	
}

package instructions;

public class StartBlockInstr extends Instruction {
	
	public StartBlockInstr(Instruction parentInstruction, String debugString) {
		super(parentInstruction, null, debugString);
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				
		};
	}
	
}

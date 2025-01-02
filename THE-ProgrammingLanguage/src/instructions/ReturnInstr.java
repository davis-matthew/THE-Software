package instructions;

public class ReturnInstr extends Instruction {
	
	public Instruction arg0;
	
	public ReturnInstr(Instruction parentInstruction, String debugString, Instruction arg) {
		super(parentInstruction, null, debugString);
		this.arg0 = arg;
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				arg0
		};
	}
}

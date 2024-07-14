package instructions;

public class IdentityInstr extends Instruction {
	
	public Instruction arg;
	
	public IdentityInstr(Instruction parentInstruction, String debugString, Instruction arg) {
		super(parentInstruction, arg.returnType, debugString);
		this.arg = arg;
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				arg
		};
	}
	
}

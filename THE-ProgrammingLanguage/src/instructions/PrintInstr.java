package instructions;

public class PrintInstr extends Instruction {
	
	public ToStringInstr stringArg;
	
	public PrintInstr(Instruction parentInstruction, String debugString, ToStringInstr arg) {
		super(parentInstruction, null, debugString);
		this.stringArg = arg;
	}
}

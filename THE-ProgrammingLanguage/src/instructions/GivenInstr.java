package instructions;

import parsing.Type;

public class GivenInstr extends Instruction {
	
	// The actual value of this 'Given' instruction type
	public Object rawValue = null;
	
	public GivenInstr(Instruction parentInstruction, String debugString, Object rawValue, Type type) {
		super(parentInstruction, type, debugString);
		this.rawValue = rawValue;
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				
		};
	}
}

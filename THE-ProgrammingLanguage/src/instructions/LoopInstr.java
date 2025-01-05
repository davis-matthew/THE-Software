package instructions;

public class LoopInstr extends Instruction {

	// Some loop constructs require code ran before starting the next iteration.
	// This string contains code to be generated at the end of a loop.
	public String codeToInjectBeforeEndOfBlock = null;
	
	// Some loop constructs require additional code generated after the completion of the loop.
	// This string contains code to be appended after the close of the loop.
	public String codeToInjectAfterEndOfBlock = null;
	
	// Whether this was a do-while loop before being compiled into a generic Loop
	public boolean wasThisADoWhileLoop = false;
	
	public EndBlockInstr endInstr;
	
	public LoopInstr(Instruction parentInstruction, String debugString, boolean wasThisADoWhileLoop) {
		
		super(parentInstruction, null, debugString);
		this.wasThisADoWhileLoop = wasThisADoWhileLoop;
	}
	
	public Instruction[] getAllArgs() {
		return new Instruction[] {
				endInstr
		};
	}
	
}

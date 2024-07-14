package instructions;

enum QuadInstructionType {
	Assignment, Branch, Print
}

public class QuadInstruction 
{
	QuadInstructionType type;
	String instruction;
	QuadInstruction(QuadInstructionType type, String instruction) {
		this.type = type;
		this.instruction = instruction;
	}
	
	public String toLLVMIR() {
		return ""; // TODO: implement
	}
}

/*
Given -> []
AllocAndAssign(Type) -> 
	if scalar:
		variable_name = value
	if array: // May change later
		variable_name = value
If(Bool)/Elseif ->
	br arg .labelTrue .labelFalse
	.labelTrue
Else ->
	[]
	
Not(Bool) -> 
	x = !y
Break ->
	br true .labelOutsideOfReference/Arg .labelImpossible
Print ->
	print(x)
	
EndBlock ->
	if/elseif/else ->
		br true .endofchain .labelimpossible
		if it's the last branch: 
			.endofchain
	
	loop ->
		br true .loopheader .labelimpossible
		
Loop ->
	.loopheader

*/
package parsing;

// This class contains information about a single instruction (add, sub, mov, math, if, while, ...)

public class Instruction {
	
	// The global instruction id counter
	private static int nextInstructionNum = 0;
	
	// The local instruction id
	public final int id;
	
	// What type of instruction this is
	public final InstructionType instructionType;
	
	// The arguments or operands for this instruction, if any
	public Instruction[] argReferences = null;
	
	// The actual value of this 'Given' instruction type
	public Object primitiveGivenValue = null;
	
	// A string representation of what this instruction applies to (for debug only)
	public String stringRepresentation = null;
	
	// The variable written to by this instruction, if applicable.
	// (for Reassign, Alloc, Initialize, or Declare instructions only)
	public Variable variableThatWasChanged = null;
	
	// The variable read from by this instruction.
	// Only applicable for the Read instruction type.
	public Variable variableThatWasRead = null;
	
	// The return type of this instruction (may be null for types that don't return, like '=' or WriteToReference)
	public Type returnType = null;
	
	// The routine reference (only applicable for RoutineDefinition instructions)
	public Function functionThatWasDefined = null;
	
	// The name of the routine that was referenced (for Call and FunctionDefinition instruction type)
	public String routineName = null;
	
	// Reference to the routine instruction that this Call instruction executes
	public Instruction callFunctionReference = null;
	
	// The instruction that contains this instruction (such as an If, a loop, or a method).
	// This may be null for instructions not in any conditional structure or method.
	public Instruction parentInstruction = null;

	// The end of an If may be an ElseIf, an Else, or an EndBlock.
	// The end instruction associated with this beginning instruction.
	// Only applies to instructions that can contain other instructions (If, While, functions...).
	public Instruction endInstruction = null;
	
	// The next instruction in a mandatory chain (if-elseif-else chains only)
	public Instruction nextChainedInstruction = null;
	
	// Some loop constructs require code ran before starting the next iteration.
	// This string contains code to be generated at the end of a loop.
	public String codeToInjectAtEndOfBlock = null;
	
	// Whether this was a do-while loop before being compiled into a generic Loop
	public boolean wasThisADoWhileLoop = false;
	
	// Create an instruction of a given type, and give it a unique id
	public Instruction(InstructionType type) {
		id = nextInstructionNum;
		nextInstructionNum++;
		instructionType = type;
	}
	
	// Add arguments to this instruction
	public void setArgs(Instruction... args) {
		if (argReferences != null) {
			new Exception("Arguments already assigned for instruction:\n" + this).printStackTrace();
		}
		argReferences = args;
	}
	
	// Return true if this is an If, ElseIf, or Else
	public boolean isConditional() {
		return instructionType == InstructionType.If ||
				instructionType == InstructionType.ElseIf ||
				instructionType == InstructionType.Else;
	}
	
	public QuadInstruction toQuadIR() {
		
		
		// TODO Convert to QuadIR here
		
		
		return null;
	}
	
	// Beautiful representation of this instruction
	public String toString() {
		String s = id + " ";
		
		// Add padding to align columns
		if (id < 100) {
			s += " ";
		}
		if (id < 10) {
			s += " ";
		}
		
		// Determine the indentation for this instruction
		Instruction parent = parentInstruction;
		String indents = "";
		while (parent != null) {
			indents += "| ";
			parent = parent.parentInstruction;
		}
		s += indents;
		
		s += instructionType;
		
		if (argReferences != null) {
			s += "(";
			for (int i = 0; i < argReferences.length; i++) {
				s += argReferences[i].returnType;
				if (i != argReferences.length-1) {
					s += ", ";
				}
			}
			s += ")";
		}
		
		if (returnType != null && returnType != Type.Void) {
			s += "->" + returnType;
		}
		
		if (argReferences != null) {
			s += " [args ";
			for (int i = 0; i < argReferences.length; i++) {
				s += argReferences[i].id;
				if (i != argReferences.length-1) {
					s += ", ";
				}
			}
			s += "]";
		}
		
		if (callFunctionReference != null) {
			String argsString = "";
			Type[] argTypes = callFunctionReference.functionThatWasDefined.argTypes;
			for (int j = 0; j < argTypes.length; j++) {
				argsString += argTypes[j];
				if (j != argTypes.length-1) {
					argsString += ", ";
				}
			}
			s += " [call " + callFunctionReference.routineName + "(" + argsString + ")]";
		}
		
		if (variableThatWasChanged != null) {
			s += " (" + variableThatWasChanged + " changed)";
		}
		
		if (variableThatWasRead != null) {
			s += " (" + variableThatWasRead + " read)";
		}
		
		s += " '" + stringRepresentation + "'";
		
		if (parentInstruction != null) {
			s += " Parent=" + parentInstruction.id;
		}
		
		if (endInstruction != null) {
			s += " End=" + endInstruction.id;
		}
		
		if (nextChainedInstruction != null) {
			s += " Chain=" + nextChainedInstruction.id;
		}
		
		return s;
	}
}

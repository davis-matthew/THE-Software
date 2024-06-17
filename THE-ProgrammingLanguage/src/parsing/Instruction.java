package parsing;

// This class contains information about a single instruction (add, sub, mov, math, if, while, ...)

enum InstructionType {
	Add, Sub, Concat, Mult, Div, Modulo, Power, And, Or, Not,
	BitAnd, BitOr, BitNot,
	Less, Greater, LessEqual, GreaterEqual, Equal, NotEqual, RefEqual, RefNotEqual,
	Print, KeyboardRead, ToString, Call,
	Read, // Read a from memory
	WriteToReference, // Write to a reference to a variable or memory
	ReadBuiltInProperty, // Read a property of an object, such as length of an array or other special property.
	Given, // Load a value from program memory (like a constant)
	Reassign, // Change the value of an existing variable
	Alloc, // Allocate memory, but without assigning it to any variable
	AllocAndAssign, // Allocate a new variable and assign it to something
	DeclareScope, // Declare the scope of a variable without assigning it to anything???
	ArrayLength, // Read the length of an array
	If, ElseIf, Else, EndBlock, Loop, Enscope, FunctionDefinition,
	Break, Continue;
	
	public String toSymbolForm() {
		if (this == Add || this == Concat) {
			return "+";
		} else if (this == Mult) {
			return "*";
		} else if (this == Div) {
			return "/";
		} else if (this == Sub) {
			return "-";
		} else if (this == Modulo) {
			return "%";
		} else if (this == Power) {
			return "^";
		} else if (this == And || this == BitAnd) {
			return "AND";
		} else if (this == Or || this == BitOr) {
			return "OR";
		} else if (this == Not || this == BitNot) {
			return "!";
		} else if (this == Less) {
			return "<";
		} else if (this == Greater) {
			return ">";
		} else if (this == LessEqual) {
			return "<=";
		} else if (this == GreaterEqual) {
			return ">=";
		} else if (this == Equal) {
			return "=";
		} else if (this == NotEqual) {
			return "!=";
		} else if (this == RefEqual) {
			return "@=";
		} else if (this == RefNotEqual) {
			return "!@=";
		} else if (this == Print) {
			return "print";
		} else if (this == Reassign || this == AllocAndAssign) {
			return "=";
		} else if (this == Break) {
			return "break";
		} else if (this == Continue) {
			return "continue";
		} else if (this == If) {
			return "if";
		} else if (this == ElseIf) {
			return "elseif";
		} else {
			new Exception("toSymbolForm not implemented yet for " + this).printStackTrace();
		}
		return null;
	}
	
	// Return true if this instruction restricts the scope of the contents
	public boolean doesStartScope() {
		return this == Enscope || this == FunctionDefinition || this == Loop ||
				this == If || this == Else || this == ElseIf;
	}
	
	// Return true if this instruction closes a scope block (such as end-while)
	public boolean doesEndScope() {
		return this == EndBlock || this == Else || this == ElseIf;
	}
}

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
	// (for Reassign, Alloc, AllocAndAssign, or DeclareScope instructions only)
	public Variable variableThatWasChanged = null;
	
	// The variable read from by this instruction.
	// Only applicable for the Read instruction type.
	public Variable variableThatWasRead = null;
	
	// The return type of this instruction (should never be null)
	public Type returnType = null;
	
	// The routine reference (only applicable for RoutineDefinition instructions)
	public Function functionThatWasDefined = null;
	
	// The name of the routine that was referenced (for Call and RoutineDefinition instruction type)
	public String routineName = null;
	
	// Reference to the routine instruction that this Call instruction executes
	public Instruction callFunctionReference = null;
	
	// The instruction that contains this instruction (such as an If, a loop, or a method).
	// This may be null for instructions not in any conditional structure or method.
	public Instruction parentInstruction = null;

	// The end of an If may be an ElseIf, an Else, or an EndBlock.
	// The end instruction associated with this beginning instruction.
	// Only applies to instructions that can contain other instructions (If, While, routines...).
	public Instruction endInstruction = null;
	
	// The next instruction in a mandatory chain (if-elseif-else chains only)
	public Instruction nextChainedInstruction = null;
	
	// Some loop constructs require some code ran before starting the next iteration.
	// This string contains code to be generated at the end of a loop.
	public String codeToInjectAtEndOfBlock = null;
	
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

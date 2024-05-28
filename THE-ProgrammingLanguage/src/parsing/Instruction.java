package parsing;

import java.util.ArrayList;

// This class contains information about a single instruction (add, sub, mov, math, if, while, ...)

enum InstructionType {
	Add, Sub, Concat, Mult, Div, Modulo, Power, And, Or, Not, Increment, Decrement,
	BitAnd, BitOr, BitNot,
	Less, Greater, LessEqual, GreaterEqual, Equal, NotEqual, RefEqual, RefNotEqual,
	Print, KeyboardRead, ToString, Call,
	Read, ReadProperty,
	Given, // Load a value from program memory (like a constant)
	Reassign, // Change the value of an existing variable
	Alloc,
	AllocAndAssign, // Create a new variable and assign it to something
	DeclareScope, // Sort of same as "Alloc"? Can probably be removed.
	Length,
	If, ElseIf, Else, EndBlock, Loop, Enscope, RoutineDefinition,
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
		} else if (this == Increment) {
			return "++";
		} else if (this == Decrement) {
			return "--";
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
	public Routine routineThatWasDefined = null;
	
	// The name of the routine that was referenced (for Call and RoutineDefinition instruction type)
	public String routineName = null;
	
	// Reference to the routine instruction that this Call instruction executes
	public Instruction callRoutineReference = null;
	
	// The instruction that contains this instruction (such as an If, a loop, or a method).
	// This may be null for instructions not in any conditional structure or method.
	public Instruction parentInstruction = null;

	// The end of an If may be an ElseIf, an Else, or an EndBlock.
	// The end instruction associated with this beginning instruction.
	// Only applies to instructions that can contain other instructions (If, While, routines...).
	public Instruction endInstruction = null;
	
	// The next instruction in a mandatory chain (if-elseif-else chains only)
	public Instruction nextChainedInstruction = null;
	
	// The list of instructions that immediately need the result of this instruction.
	// (No transitive results in this list.)
	public ArrayList<Instruction> referencedBy = new ArrayList<Instruction>(0);
	
	// The list of instructions that this instruction immediately needs the result of.
	// (No transitive results in this list.)
	public ArrayList<Instruction> references = new ArrayList<Instruction>(0);
	
	// Create an instruction of a given type, and give it a unique id
	public Instruction(InstructionType type) {
		id = nextInstructionNum;
		nextInstructionNum++;
		instructionType = type;
	}
	
	// Add arguments to this instruction
	public void createArgs(int numArgs) {
		argReferences = new Instruction[numArgs];
	}
	
	/*
	// Add the given instructions to the list of referenced instructions,
	//   and add this instruction to the list of referenced instructions in
	//   each of the other instructions.
	public void addReferences(ArrayList<Instruction> otherInstructions) {
		// Add the reference to this
		references.addAll(otherInstructions);
		
		// Add this as a reference to all others
		for (int i = 0; i < otherInstructions.size(); i++) {
			otherInstructions.get(i).referencedBy.add(this);
		}
	}
	*/
	
	// Add the given instruction to the list of referenced instructions,
	//   and add this instruction to the list of referenced instructions in
	//   the other instruction.
	public void addTopologicalReference(Instruction otherInstruction) {
		// Add the reference to this
		references.add(otherInstruction);
		
		// Add this as a reference to the other
		otherInstruction.referencedBy.add(this);
	}
	
	// Return true if this is a container-type instruction (like If, Else, While, Routine...)
	public boolean isContainerInstruction() {
		return instructionType == InstructionType.If ||
				instructionType == InstructionType.Else ||
				instructionType == InstructionType.ElseIf ||
				instructionType == InstructionType.Enscope ||
				instructionType == InstructionType.Loop ||
				instructionType == InstructionType.RoutineDefinition;
	}
	
	// Return true if this is an If, ElseIf, Else, or Switch
	public boolean isConditional() {
		return instructionType == InstructionType.If ||
				instructionType == InstructionType.ElseIf ||
				instructionType == InstructionType.Else;
				// instructionType == InstructionType.Switch;
	}
	
	// Beautiful representation of this instruction
	public String toString() {
		String s = id + " ";
		if (id < 10) {
			s += " ";
		}
		
		// Determine the indentation for this instruction
		Instruction parent = parentInstruction;
		String indents = "";
		while (parent != null) {
			indents += ": ";
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
		
		if (returnType != null) {
			s += "->" + returnType;
		}
		
		if (references.size() != 0) {
			s += " [ref ";
			for (int i = 0; i < references.size(); i++) {
				s += references.get(i).id;
				if (i != references.size()-1) {
					s += ", ";
				}
			}
			s += "]";
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
		
		if (callRoutineReference != null) {
			String argsString = "";
			Type[] argTypes = callRoutineReference.routineThatWasDefined.argTypes;
			for (int j = 0; j < argTypes.length; j++) {
				argsString += argTypes[j];
				if (j != argTypes.length-1) {
					argsString += ", ";
				}
			}
			s += " [call " + callRoutineReference.routineName + "(" + argsString + ")]";
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

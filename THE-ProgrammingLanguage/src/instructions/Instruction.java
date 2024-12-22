package instructions;

import java.util.ArrayList;

import parsing.Type;

// This class contains information about a single instruction (add, sub, mov, math, if, while, ...)

public abstract class Instruction {
	
	// The global instruction id counter
	private static int nextInstructionNum = 0;
	
	// The local instruction id
	public final int id;
	
	// A string representation of what this instruction applies to (for debug only)
	public String debugString;
	
	// The return type of this instruction (may be null for types that don't return, like '=' or WriteToReference)
	public Type returnType;
	
	// The instruction that contains this instruction (such as an If, a loop, or a method).
	// This may be null for instructions not in any conditional structure or method.
	public Instruction parentInstruction = null;
	
	// Create an instruction of a given type, and give it a unique id
	public Instruction(Instruction parentInstruction, Type returnType, String debugString) {
		this.id = nextInstructionNum;
		nextInstructionNum++;
		
		this.parentInstruction = parentInstruction;
		this.returnType = returnType;
		this.debugString = debugString;
		if (this.debugString == null) {
			this.debugString = toSymbolForm();
		}
	}
	
	// Beautiful representation of this instruction
	@Override
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
		
		s += name();
		
		// Binary operators
		if (this instanceof AddInstr) {
			AddInstr instr = (AddInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof SubInstr) {
			SubInstr instr = (SubInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof MultInstr) {
			MultInstr instr = (MultInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof DivideInstr) {
			DivideInstr instr = (DivideInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof PowerInstr) {
			PowerInstr instr = (PowerInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof ModuloInstr) {
			ModuloInstr instr = (ModuloInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof BoolAndInstr) {
			BoolAndInstr instr = (BoolAndInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof BoolOrInstr) {
			BoolOrInstr instr = (BoolOrInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof BitAndInstr) {
			BitAndInstr instr = (BitAndInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof BitOrInstr) {
			BitOrInstr instr = (BitOrInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof EqualInstr) {
			EqualInstr instr = (EqualInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof NotEqualInstr) {
			NotEqualInstr instr = (NotEqualInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof RefEqualInstr) {
			RefEqualInstr instr = (RefEqualInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof RefNotEqualInstr) {
			RefNotEqualInstr instr = (RefNotEqualInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof LessInstr) {
			LessInstr instr = (LessInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof LessEqualInstr) {
			LessEqualInstr instr = (LessEqualInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof GreaterInstr) {
			GreaterInstr instr = (GreaterInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof GreaterEqualInstr) {
			GreaterEqualInstr instr = (GreaterEqualInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}
		if (this instanceof ConcatInstr) {
			ConcatInstr instr = (ConcatInstr)this;
			s += "(" + instr.arg1.returnType + " " + instr.arg1.id + ", " + instr.arg2.returnType + " " + instr.arg2.id + ")";
		}

		// Unary operators
		if (this instanceof BoolNotInstr) {
			BoolNotInstr instr = (BoolNotInstr)this;
			s += "(" + instr.arg.returnType + " " + instr.arg.id + ")";
		}
		if (this instanceof BitNotInstr) {
			BitNotInstr instr = (BitNotInstr)this;
			s += "(" + instr.arg.returnType + " " + instr.arg.id + ")";
		}
		if (this instanceof ToStringInstr) {
			ToStringInstr instr = (ToStringInstr)this;
			s += "(" + instr.arg.returnType + " " + instr.arg.id + ")";
		}
		if (this instanceof LoadInstr) {
			LoadInstr instr = (LoadInstr)this;
			s += "(" + instr.instrThatReturnedPointer.returnType + " " + instr.instrThatReturnedPointer.id + ")";
		}
		if (this instanceof PrintInstr) {
			PrintInstr instr = (PrintInstr)this;
			s += "(" + instr.stringArg.returnType + " " + instr.stringArg.id + ")";
		}
		if (this instanceof IdentityInstr) {
			IdentityInstr instr = (IdentityInstr)this;
			s += "(" + instr.arg.returnType + " " + instr.arg.id + ")";
		}
		if (this instanceof IfInstr) {
			IfInstr instr = (IfInstr)this;
			s += "(" + instr.conditionInstr.returnType + " " + instr.conditionInstr.id + ")";
		}
		
		// Other types of instructions
		if (this instanceof StoreInstr) {
			StoreInstr instr = (StoreInstr)this;
			s += "(" + instr.instrThatReturnedPointer.returnType + " " + instr.instrThatReturnedPointer.id + ", " +
					   instr.valueToStore.returnType + " " + instr.valueToStore.id + ")";
		}
		if (this instanceof GetElementInstr) {
			GetElementInstr instr = (GetElementInstr)this;
			s += "(" + instr.declareInstr.returnType + " " + instr.declareInstr.id;
			for (int i = 0; i < instr.instructionsForIndices.length; i++) {
				s += ", " + instr.instructionsForIndices[i].returnType + " " + instr.instructionsForIndices[i].id;
			}
			s += ")";
		}
		if (this instanceof AllocArrInstr) {
			AllocArrInstr instr = (AllocArrInstr)this;
			s += "(";
			for (int i = 0; i < instr.dimensionSizes.length; i++) {
				if (i != 0) {
					s += ", ";
				}
				s += instr.dimensionSizes[i].returnType + " " + instr.dimensionSizes[i].id;
			}
			s += ")";
		}
		if (this instanceof ArrLengthInstr) {
			ArrLengthInstr instr = (ArrLengthInstr)this;
			s += "(" + instr.pointerInstr.returnType + " " + instr.pointerInstr.id;
			if (!instr.getElementCount) {
				s += ", " + instr.dimensionToRead.returnType + " " + instr.dimensionToRead.id;
			}
			s += ")";
		}
		if (this instanceof FunctionDefInstr) {
			FunctionDefInstr instr = (FunctionDefInstr)this;
			Type[] args = instr.functionThatWasDefined.argTypes;
			s += "(";
			for (int i = 0; i < args.length; i++) {
				if (i != 0) {
					s += ", ";
				}
				s += args[i];
			}
			s += ")";
			
			// For function definitions, we have a special way of handling returns.
			if (instr.functionThatWasDefined.returnType != null) {
				s += "->" + instr.functionThatWasDefined.returnType;
			}
		}
		if (this instanceof FunctionCallInstr) {
			FunctionCallInstr instr = (FunctionCallInstr)this;
			Instruction[] args = instr.args;
			s += "(";
			for (int i = 0; i < args.length; i++) {
				if (i != 0) {
					s += ", ";
				}
				s += args[i].returnType + " " + args[i].id;
			}
			s += ")";
		}
		
		// Print the return type, if there is one
		if (returnType != null) {
			s += "->" + returnType;
		}
		
		// Print some stuff after the return
		if (this instanceof FunctionCallInstr) {
			FunctionCallInstr instr = (FunctionCallInstr)this;
			s += " [" + instr.functionThatWasCalled.name + "]";
		}
		if (this instanceof FunctionDefInstr) {
			FunctionDefInstr instr = (FunctionDefInstr)this;
			s += " [" + instr.functionThatWasDefined.name + "]";
		}
		if (this instanceof GivenInstr) {
			GivenInstr instr = (GivenInstr)this;
			s += " [" + instr.rawValue + "]";
		}
		
		if (this instanceof IfInstr) {
			IfInstr instr = (IfInstr)this;
			if (instr.endOfBlockInstr != null) {
				s += " End=" + instr.endOfBlockInstr.id;
			} else {
				print("*****Null endOfBlockInstr found*****");
			}
			if (instr.elseInstr != null) {
				s += " Else=" + instr.elseInstr.id;
			}
		}
		if (this instanceof ElseInstr) {
			ElseInstr instr = (ElseInstr)this;
			if (instr.endOfBlockInstr != null) {
				s += " End=" + instr.endOfBlockInstr.id;
			} else {
				print("*****Null endOfBlockInstr found*****");
			}
			if (instr.previousIfInstr != null) {
				s += " PreviousIf=" + instr.previousIfInstr.id;
			} else {
				print("*****Null ifInstr found*****");
			}
		}
		
		if (parentInstruction != null) {
			s += " Parent=" + parentInstruction.id;
		} else {
			s += " Parent=-1";
		}
		
		if (debugString != null && !debugString.isEmpty()) {
			s += " '" + debugString + "'";
		}
		if (this instanceof AllocVarInstr) {
			AllocVarInstr instr = (AllocVarInstr)this;
			s += " (" + instr.varName + " declared)";
		}
		if (this instanceof ArrLengthInstr) {
			ArrLengthInstr instr = (ArrLengthInstr)this;
			if (instr.getElementCount) {
				s += " [all elements]";
			}
		}
		
		return s;
	}
	

	// Return the programming language symbol for this instruction
	public String toSymbolForm() {
		if (this instanceof AddInstr || this instanceof ConcatInstr) {
			return "+";
		} else if (this instanceof MultInstr) {
			return "*";
		} else if (this instanceof DivideInstr) {
			return "/";
		} else if (this instanceof SubInstr) {
			return "-";
		} else if (this instanceof ModuloInstr) {
			return "%";
		} else if (this instanceof PowerInstr) {
			return "^";
		} else if (this instanceof BoolAndInstr) {
			return "&&";
		} else if (this instanceof BitAndInstr) {
			return "&";
		} else if (this instanceof BoolOrInstr) {
			return "||";
		} else if (this instanceof BitOrInstr) {
			return "|";
		} else if (this instanceof BoolNotInstr) {
			return "!";
		} else if (this instanceof BitNotInstr) {
			return "~";
		} else if (this instanceof LessInstr) {
			return "<";
		} else if (this instanceof GreaterInstr) {
			return ">";
		} else if (this instanceof LessEqualInstr) {
			return "<=";
		} else if (this instanceof GreaterEqualInstr) {
			return ">=";
		} else if (this instanceof EqualInstr) {
			return "=";
		} else if (this instanceof NotEqualInstr) {
			return "!=";
		} else if (this instanceof RefEqualInstr) {
			return "@=";
		} else if (this instanceof RefNotEqualInstr) {
			return "!@=";
		} else if (this instanceof PrintInstr) {
			return "print";
		} else if (this instanceof StoreInstr) {
			return "=";
		} else if (this instanceof BreakInstr) {
			return "break";
		} else if (this instanceof ContinueInstr) {
			return "continue";
		} else if (this instanceof IfInstr) {
			return "if";
		} else if (this instanceof ElseInstr) {
			return "else";
		} else {
			new Exception("toSymbolForm not implemented yet for " + this).printStackTrace();
		}
		return null;
	}
	
	// Return true if this statement can make the code jump to another location
	public boolean isJump() {
		return this instanceof IfInstr ||
				this instanceof ElseInstr ||
				this instanceof BreakInstr ||
				this instanceof ContinueInstr ||
				this instanceof ReturnInstr;
	}
	
	// Return true if this instruction restricts the scope of the contents
	public boolean doesStartScope() {
		return this instanceof StartBlockInstr ||
				this instanceof FunctionDefInstr ||
				this instanceof LoopInstr ||
				this instanceof IfInstr ||
				this instanceof ElseInstr;
	}
	
	// Return true if this instruction has undetectable consequences.
	// For example, system calls, print, and file manipulation.
	public boolean hasGlobalSideEffect(ArrayList<Instruction> instructions) {
		
		if (this instanceof PrintInstr) {
			return true; // Print always has side effects
		}
		
		// If this is a function call, determine if it has any side effects inside it
		if (this instanceof FunctionCallInstr) {
			FunctionCallInstr funcCallInstr = (FunctionCallInstr)this;
			FunctionDefInstr funcDefInstr = funcCallInstr.functionThatWasCalled.functionDefInstr;
			
			// Find the index of the instruction that defined the function
			int i;
			for (i = 0; i < instructions.size(); i++) {
				if (instructions.get(i) == funcDefInstr) {
					break;
				}
			}
			
			// Iterate over all instructions in this function
			if (i < instructions.size() - 1) {
				i++;
				for (; i < instructions.size(); i++) {
					Instruction instr = instructions.get(i);
					if (funcDefInstr.isAncestorOf(instr)) {
						if (instr.hasGlobalSideEffect(instructions)) {
							return true;
						}
					} else {
						// Once we leave the function, then we don't need to search any further
						break;
					}
				}
			}
		}
		
		return false;
	}
	
	public boolean isAncestorOf(Instruction childInstr) {
		while (childInstr != null && childInstr.parentInstruction != this) {
			childInstr = childInstr.parentInstruction;
		}
		if (childInstr == null) {
			return false;
		}
		return childInstr.parentInstruction == this;
	}
	
	// Return true if this instruction closes a scope block (such as end-while)
	public boolean doesEndScope() {
		return this instanceof EndBlockInstr;
	}
	
	public String name() {
		return this.getClass().getSimpleName().replace("Instr", "");
	}
	
	// Return all instructions that this instruction depends on.
	public abstract Instruction[] getAllArgs();
	
	// Convenient print
	protected static void print(Object o) {
		System.out.println(o);
	}
}

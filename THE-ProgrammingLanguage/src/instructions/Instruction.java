package instructions;

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
	
	// Return true if this is an If, ElseIf, or Else
	public boolean isConditional() {
		return this instanceof IfInstr ||
				this instanceof ElseIfInstr ||
				this instanceof ElseInstr;
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
		if (this instanceof StoreInstr) {
			StoreInstr instr = (StoreInstr)this;
			s += "(" + instr.declareInstr.returnType + " " + instr.declareInstr.id + ", " +
					   instr.valueToStore.returnType + " " + instr.valueToStore.id + ")";
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
			s += "(" + instr.declareInstr.returnType + " " + instr.declareInstr.id + ")";
		}
		if (this instanceof PrintInstr) {
			PrintInstr instr = (PrintInstr)this;
			s += "(" + instr.stringArg.returnType + " " + instr.stringArg.id + ")";
		}
		
		// Print the return type, if there is one
		if (returnType != null) {
			s += "->" + returnType;
		}
		
		if (this instanceof FunctionCallInstr) {
			FunctionCallInstr instr = (FunctionCallInstr)this;
			s += " [call " + instr.functionThatWasCalled.name + "]";
		}
		
		if (this instanceof DeclareInstr) {
			DeclareInstr instr = (DeclareInstr)this;
			s += " (" + instr.varName + " declared)";
		}
		
		if (debugString != null && !debugString.isEmpty()) {
			s += " '" + debugString + "'";
		}
		
		if (parentInstruction != null) {
			s += " Parent=" + parentInstruction.id;
		}

		if (this instanceof IfInstr) {
			IfInstr instr = (IfInstr)this;
			s += " End=" + instr.endChainInstruction.id;
			if (instr.nextChainedInstruction != null) {
				s += " Chain=" + instr.nextChainedInstruction.id;
			}
		}
		if (this instanceof ElseIfInstr) {
			ElseIfInstr instr = (ElseIfInstr)this;
			s += " End=" + instr.endChainInstruction.id;
			if (instr.nextChainedInstruction != null) {
				s += " Chain=" + instr.nextChainedInstruction.id;
			}
		}
		if (this instanceof ElseInstr) {
			ElseInstr instr = (ElseInstr)this;
			s += " End=" + instr.endChainInstruction.id;
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
		} else if (this instanceof ElseIfInstr) {
			return "elseif";
		} else {
			new Exception("toSymbolForm not implemented yet for " + this).printStackTrace();
		}
		return null;
	}
	
	// Return true if this instruction restricts the scope of the contents
	public boolean doesStartScope() {
		return this instanceof StartBlockInstr ||
				this instanceof FunctionDefInstr ||
				this instanceof LoopInstr ||
				this instanceof IfInstr ||
				this instanceof ElseInstr ||
				this instanceof ElseIfInstr;
	}
	
	// Return true if this instruction closes a scope block (such as end-while)
	public boolean doesEndScope() {
		return this instanceof EndBlockInstr ||
				this instanceof ElseInstr ||
				this instanceof ElseIfInstr;
	}
	
	public String name() {
		return this.getClass().getSimpleName().replace("Instr", "");
	}
	
	// Convenient print
	protected static void print(Object o) {
		System.out.println(o);
	}
}

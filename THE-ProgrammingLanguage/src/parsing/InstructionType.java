package parsing;

// This enum defines a particular type of instruction.

public enum InstructionType {
	
	Add, Subtract, Concat, Mult, Divide, Modulo, Power, And, Or, Not,
	BitAnd, BitOr, BitNot,
	Less, Greater, LessEqual, GreaterEqual, Equal, NotEqual, RefEqual, RefNotEqual,
	Call,
	Print, // Print to the "best" console? stdout?
	Read, // Read a from memory
	WriteToReference, // Write to a reference to a variable or memory
	GetReference, // Get a pointer to a variable/array/struct
	ReadBuiltInProperty, // Read a property of an object, such as length of an array or other special property.
	Given, // Load a value from program memory (like a constant)
	Reassign, // Change the value of an existing variable
	Alloc, // Allocate memory, but without assigning it to any variable
	Initialize, // Initialize a new variable and assign it to something
	Declare, // Declare the scope of a variable without assigning it to anything???
	ArrayLength, // Read the length of an array
	Break, // Instantly jump out of the end of the nearest loop
	Continue, // Jump directly to the nearest loop header
	If, ElseIf, Else, EndBlock, StartBlock, Loop, FunctionDefinition;
	
	// Return the programming language symbol for this instruction
	public String toSymbolForm() {
		if (this == Add || this == Concat) {
			return "+";
		} else if (this == Mult) {
			return "*";
		} else if (this == Divide) {
			return "/";
		} else if (this == Subtract) {
			return "-";
		} else if (this == Modulo) {
			return "%";
		} else if (this == Power) {
			return "^";
		} else if (this == And) {
			return "&&";
		} else if (this == BitAnd) {
			return "&";
		} else if (this == Or) {
			return "||";
		} else if (this == BitOr) {
			return "|";
		} else if (this == Not) {
			return "!";
		} else if (this == BitNot) {
			return "~";
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
		} else if (this == Reassign || this == Initialize) {
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
		return this == StartBlock || this == FunctionDefinition || this == Loop ||
				this == If || this == Else || this == ElseIf;
	}
	
	// Return true if this instruction closes a scope block (such as end-while)
	public boolean doesEndScope() {
		return this == EndBlock || this == Else || this == ElseIf;
	}
}

package parsing;

// This class stores information about a program variable

public class Variable {
	public String name; // User-given name of this variable
	public Type type;
	public Instruction instructionThatDeclared; // The instruction that declared this variable
	
	// Create a new variable of a certain name and type
	public Variable(String varName, Type varType) {
		name = varName;
		type = varType;
	}
	
	public String toString() {
		return name;
	}
}

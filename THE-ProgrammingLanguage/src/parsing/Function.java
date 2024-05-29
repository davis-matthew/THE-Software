package parsing;

// This class stores information about a program function

public class Function {
	public String name; // User-given name of this function
	public Type[] returnTypes;
	public Type[] argTypes;
	
	// Create a new function of a certain name and type
	public Function(String name, Type[] returnTypes, Type[] argTypes) {
		this.name = name;
		this.returnTypes = returnTypes;
		this.argTypes = argTypes;
	}
	
	// Return true if this function is distinguishable from the given
	//   routing by name, or by overloaded arguments.
	public boolean isDistinguisable(Function other) {
		if (!this.name.equals(other.name)) {
			return true;
		}
		
		// If there are a different number of parameters
		if (argTypes.length != other.argTypes.length) {
			return true;
		}
		
		// Compare each of the parameters
		int i = 0;
		while (i < argTypes.length && i < other.argTypes.length) {
			if (argTypes[i] != other.argTypes[i]) {
				return true;
			}
			i++;
		}
		
		return false;
	}
	
	public String toString() {
		return name;
	}
}

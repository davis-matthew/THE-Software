package parsing;

public enum Type {
	Bool, Long, Int, Float, Double,
	ArrayBool, ArrayLong, ArrayInt, ArrayFloat, ArrayDouble, ArrayString, ArrayObject,
	String, Object, Void;
	
	int dimensions; // For array type only
	
	private Type() {}
	
	public boolean isArrayType() {
		return this == Type.ArrayInt ||
				this == Type.ArrayLong ||
				this == Type.ArrayDouble ||
				this == Type.ArrayFloat ||
				this == Type.ArrayString||
				this == Type.ArrayObject ||
				this == Type.ArrayBool;
	}
	
	public boolean doesArrayContainsType(Type other) {
		return (this == Type.ArrayBool && other == Type.Bool) ||
				(this == Type.ArrayInt && other == Type.Int) ||
				(this == Type.ArrayString && other == Type.String) ||
				(this == Type.ArrayObject && other == Type.Object) ||
				(this == Type.ArrayDouble && other == Type.Double);
	}
	
	// Given an array type, return the primitive type that this array contains
	public Type toArrayPrimitiveType() {
		if (this == ArrayBool) {
			return Bool;
		} else if (this == ArrayInt) {
			return Int;
		} else if (this == ArrayLong) {
			return Long;
		} else if (this == ArrayFloat) {
			return Float;
		} else if (this == ArrayDouble) {
			return Double;
		} else if (this == ArrayString) {
			return String;
		} else if (this == ArrayObject) {
			return Object;
		} else {
			new Exception("Type " + this + " not implemented yet").printStackTrace();
			return null;
		}
	}
	
	public boolean isNumberType() {
		return this == Int || this == Long || this == Float || this == Double;
	}
	
	public boolean isIntegerType() {
		return this == Int || this == Long;
	}
	
	// Return true if this is some sort of discrete type (other than boolean)
	public boolean isDiscreteType() {
		return this == Int || this == Long;
	}
	
	// Return the type from the given language string
	public static Type getTypeFromString(String s) {
		if (s.equals("bool")) {
			return Type.Bool;
		} else if (s.equals("int")) {
			return Type.Int;
		} else if (s.equals("long")) {
			return Type.Long;
		} else if (s.equals("float")) {
			return Type.Float;
		} else if (s.equals("double")) {
			return Type.Double;
		} else if (s.equals("string")) {
			return Type.String;
		} else if (s.equals("void")) {
			return Type.Void;
		} else {
			new Exception("Primitive type " + s + " unimplemented").printStackTrace();
			return null;
		}
	}
	
	// Return the array type from the given language string (excluding brackets)
	public static Type getArrayTypeFromString(String s) {
		if (s.equals("bool")) {
			return Type.ArrayBool;
		} else if (s.equals("int")) {
			return Type.ArrayInt;
		} else if (s.equals("long")) {
			return Type.ArrayLong;
		} else if (s.equals("float")) {
			return Type.ArrayFloat;
		} else if (s.equals("double")) {
			return Type.ArrayDouble;
		} else if (s.equals("string")) {
			return Type.ArrayString;
		} else {
			Compiler.print("Unknown array type: " + s);
			return null;
		}
	}
	
	// Return true if this is a primitive variable type
	public boolean isPrimitiveType() {
		return this == Bool || this == Long || this ==  Int || this ==  Float || this ==  Double;
	}
	
	// Return true if this value can be implicitly cast to the given type
	public boolean canImplicitlyCastTo(final Type type) {
		if (this == type) {
			return true;
		}
		
		// Everything can be cast to a string
		if (type == Type.String) {
			return true;
		}
		
		if (this == Type.Int) {
			if (type == Type.Double || type == Type.Float || type == Type.Long) {
				return true;
			}
		} else if (this == Type.Long) {
			if (type == Type.Double || type == Type.Float) {
				return true;
			}
		} else if (this == Type.Float) {
			if (type == Type.Double) {
				return true;
			}
		}
		
		return false;
	}
}

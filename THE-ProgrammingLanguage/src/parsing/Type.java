package parsing;

import static parsing.ErrorHandler.*;

// This class stores information about a particular "type" of a variable, array, etc.
// This may be a primitive type (int, bool, etc.), or
// it may be a more complex type (*int, bool[4,5], or even ***string[4,4,6]).

public class Type {
	
	// Convenient references for re-use
	public static final Type Bool = new Type(BaseType.Bool);
	public static final Type Int = new Type(BaseType.Int);
	public static final Type Long = new Type(BaseType.Long);
	public static final Type Float = new Type(BaseType.Float);
	public static final Type Double = new Type(BaseType.Double);
	public static final Type String = new Type(BaseType.String);
	
	public final BaseType baseType; // The fundamental type of this (possibly) composite type
	public final int dimensions;	// For array type only
	public final int pointerDepth;	// How many pointers deep is this? (0 = primitive, 1 = *int, 2 = **int, 3 = ***int, etc.)
	public final boolean isArray;	// True if this is an array of the baseType
	public final boolean isBaseType; // True if this is just a regular value, not an array or pointer.
	
	// Instantiate a basic type
	public Type(BaseType baseType) {
		this.baseType = baseType;
		this.dimensions = 0;
		this.pointerDepth = 0;
		this.isArray = false;
		this.isBaseType = true;
	}
	
	// Instantiate a type from the given string.
	// For example int*[,,]** or just bool
	public Type(String s) {
		
		int baseTypeEndIndex = 0;
		while (baseTypeEndIndex < s.length() && ParseUtil.isLetter(s.charAt(baseTypeEndIndex))) {
			baseTypeEndIndex++;
		}
		
		String baseTypeString = s.substring(0, baseTypeEndIndex);
		this.baseType = getBaseTypeFromString(baseTypeString);
		
		// Compute the pointer depth (number of * after the base type).
		int p = baseTypeEndIndex;
		while (p < s.length() && s.charAt(p) == '*') {
			p++;
		}
		this.pointerDepth = p - baseTypeEndIndex;
		
		// Compute the array dimensions
		if (p < s.length() && s.charAt(p) == '[') {
			p++;
			int dims = 1;
			boolean didEndWithBracket = false;
			for (; p < s.length(); p++) {
				char c = s.charAt(p);
				if (c == ',') {
					dims++;
				} else if (c == ' ') {
					// Spaces are fine in array type-declarations
				} else if (c == ']') {
					// Good
					didEndWithBracket = true;
					break;
				} else {
					printError("Malformed array type: '" + s + "'");
				}
			}
			
			if (!didEndWithBracket) {
				printError("Array type missing closing bracket: '" + s + "'");
			}
			
			this.dimensions = dims;
			this.isArray = true;
		} else {
			this.dimensions = 0;
			this.isArray = false;
		}
		
		this.isBaseType = (this.pointerDepth == 0) && !isArray;
	}
	
	// Instantiate an array type
	public Type(BaseType baseType, int dimensions) {
		this.baseType = baseType;
		this.dimensions = dimensions;
		this.pointerDepth = 0;
		this.isArray = true;
		this.isBaseType = false;
		
		if (dimensions <= 0) {
			new Exception("Non positive-dimension array disallowed!").printStackTrace();
		}
	}

	// Instantiate an array type from the string BaseType
	public Type(String s, int dimensions) {
		this.baseType = getBaseTypeFromString(s);
		this.dimensions = dimensions;
		this.pointerDepth = 0;
		this.isArray = true;
		this.isBaseType = false;
		
		if (dimensions <= 0) {
			new Exception("Non positive-dimension array disallowed!").printStackTrace();
		}
	}
	
	// Instantiate a type with all available parameters
	public Type(BaseType baseType, int dimensions, boolean isArray, int pointerDepth) {
		this.baseType = baseType;
		this.dimensions = dimensions;
		this.pointerDepth = pointerDepth;
		this.isArray = isArray;
		this.isBaseType = !isArray && pointerDepth == 0;
		
		if (isArray && dimensions <= 0) {
			new Exception("Non positive-dimension array disallowed!").printStackTrace();
		}
	}
	
	public boolean isPointer() {
		return pointerDepth > 0;
	}
	
	public boolean isNumberType() {
		if (isArray || isPointer()) {
			return false;
		}
		return baseType == BaseType.Int || baseType == BaseType.Long ||
				baseType == BaseType.Float || baseType == BaseType.Double;
	}
	
	public boolean isIntegerType() {
		if (isArray || isPointer()) {
			return false;
		}
		return baseType == BaseType.Int || baseType == BaseType.Long;
	}
	
	// Return true if this type is exactly the given base type, and not a pointer or array.
	public boolean isA(BaseType type) {
		return this.baseType == type && !isPointer() && !isArray;
	}
	
	// Return the type from the given language string
	public static BaseType getBaseTypeFromString(String s) {
		if (s.equals("bool")) {
			return BaseType.Bool;
		} else if (s.equals("int")) {
			return BaseType.Int;
		} else if (s.equals("long")) {
			return BaseType.Long;
		} else if (s.equals("float")) {
			return BaseType.Float;
		} else if (s.equals("double")) {
			return BaseType.Double;
		} else if (s.equals("string")) {
			return BaseType.String;
		} else {
			new Exception("Base type " + s + " not implemented").printStackTrace();
			return null;
		}
	}
	
	// Return the type that this array contains
	public Type getArrayElementType() {
		if (!isArray) {
			new Exception("getArrayContainedType can only be called on array types").printStackTrace();
			return null;
		}
		
		if (baseType == BaseType.Bool) {
			return Type.Bool;
		} else if (baseType == BaseType.Int) {
			return Type.Int;
		} else if (baseType == BaseType.Long) {
			return Type.Long;
		} else if (baseType == BaseType.Float) {
			return Type.Float;
		} else if (baseType == BaseType.Double) {
			return Type.Double;
		} else if (baseType == BaseType.String) {
			return Type.String;
		} else {
			new Exception("Array type " + baseType + " not implemented").printStackTrace();
			return null;
		}
	}
	
	// Return true if this type can be implicitly cast to the given type
	public boolean canImplicitlyCastTo(final Type other) {
		
		// If they are truly identical in memory
		if (this == other) {
			return true;
		}
		
		// If they are identical
		if (baseType == other.baseType &&
			dimensions == other.dimensions &&
			pointerDepth == other.pointerDepth) {
			
			return true;
		}
		
		// Everything can be cast to a string
		if (other.baseType == BaseType.String && !other.isArray && !other.isPointer()) {
			return true;
		}
		
		if (pointerDepth != other.pointerDepth) {
			return false;
		}
		
		if (dimensions != other.dimensions) {
			return false;
		}
		
		if (baseType == BaseType.Int) {
			if (other.baseType == BaseType.Double || other.baseType == BaseType.Float || other.baseType == BaseType.Long) {
				return true;
			}
		} else if (baseType == BaseType.Long) {
			if (other.baseType == BaseType.Double || other.baseType == BaseType.Float) {
				return true;
			}
		} else if (baseType == BaseType.Float) {
			if (other.baseType == BaseType.Double) {
				return true;
			}
		}
		
		return false;
	}
	
	// Return a copy of this type, but with pointerDepth increased by 1.
	// int[] -> int[]*
	public Type makePointerToThis() {
		return new Type(baseType, dimensions, isArray, pointerDepth + 1);
	}
	
	// Return a copy of this type, but with isArray = true
	// int -> int[]
	public Type makeArrayOfThis(int numDimensions) {
		if (isArray) {
			printError("Arrays of arrays are not implemented yet");
		}
		if (pointerDepth != 0) {
			printError("Arrays of pointers not implemented yet");
		}
		return new Type(baseType, numDimensions, true, 0);
	}
	
	@Override
	public String toString() {
		String s = "";
		
		s += baseType.name().toLowerCase();
		
		if (isArray) {
			s += "[";
			for (int i = 0; i < dimensions - 1; i++) {
				s += ",";
			}
			s += "]";
		}
		
		for (int i = 0; i < pointerDepth; i++) {
			s += "*";
		}
		
		return s;
	}
}

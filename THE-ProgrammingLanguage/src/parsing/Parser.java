package parsing;

import java.util.ArrayList;

// This class provides advanced string parsing functions for a custom programming language

public class Parser {
	
	// This contains the names of binary operators
	//   grouped by operator precedence in reverse order.
	static final String[][] binaryOperators =
		{
			{"=", "!="},			// content equal, not content equal
			{"@=", "!@="},			// reference equal, not reference equal
			{"<", ">", "<=", ">="}, // Size comparisons, <, >, <=, >=
			{"AND", "OR"},			// Logic
			{"+", "-"},				// Addition, subtraction
			{"*", "/", "%"},		// Multiplication, division, modulus
			{"^"}					// Raise to power
		};
	
	// List of assignment operators (always come after a variable name)
	static final String[] assignmentOperators =
		{
			"=", "<-", "++", "--", "AND=", "OR=", "+=", "-=", "*=", "/=", "^=", "%=", "@="
		};
	
	// List of keywords that indicate a variable instantiate or allocation
	static final String[] dataTypes =
		{
			"bool",
			"int",
			"long",
			"float",
			"double",
			"string",
			"void"
		};

	// Remove all comments from every line
	public static String[] stripComments(String[] lines) {
		
		boolean isInComment = false;
		for (int i = 0; i < lines.length; i++) {
			
			if (!isInComment) {
				
				// Remove single line comments
				int singleLineCommentIndex = lines[i].indexOf("//");
				if (singleLineCommentIndex != -1) {
					lines[i] = lines[i].substring(0, singleLineCommentIndex);
				}
				
				// Find a multiline comment
				int multilineCommentStartIndex = lines[i].indexOf("/*");
				if (multilineCommentStartIndex != -1) {
					lines[i] = lines[i].substring(0, multilineCommentStartIndex);
					isInComment = true;
				}
			} else {
				int multilineCommentEndIndex = lines[i].indexOf("*/");
				if (multilineCommentEndIndex != -1) {
					lines[i] = lines[i].substring(multilineCommentEndIndex + 2);
					isInComment = false;
				}
			}
			
			// Skip lines in a comment
			if (isInComment) {
				lines[i] = "";
			} else {
				// Prepare this string for evaluation
				lines[i] = Parser.prepareStringForEvaluation(lines[i]);
			}
		}
		
		return lines;
	}
	
	// Prepare the string for faster parsing later
	public static String prepareStringForEvaluation(String s) {
		
		if (s.isEmpty()) {
			return s;
		}
		
		// Remove spaces at the end of each line
		s = replaceOutsideLiteral(s, "   ", " ", false);
		s = replaceOutsideLiteral(s, "  ", " ", false);
		s = replaceOutsideLiteral(s, " \n", "\n", false);
		
		// Add spaces around operators
		boolean isInsideString = false;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '"') {
				isInsideString = !isInsideString;
			} else if (!isInsideString) {
				// Find any binary operator at this index (without relying on surrounding spaces)
				String op = getBinaryOperatorNoSpaces(s, i);
				if (op != null) {
					s = s.substring(0, i) + " " + op + " " + s.substring(i+op.length());
					i += op.length();
				}
			}
		}
		
		// Remove double and triple spaces
		s = replaceOutsideLiteral(s, "   ", " ", false);
		s = replaceOutsideLiteral(s, "  ", " ", false);
		
		// Remove tabs
		s = replaceOutsideLiteral(s, "\t", "", false);
		
		s = replaceNegativesWithMultiplication(s);
		
		// Remove white space after unary functions
		s = replaceOutsideLiteral(s, "# ", "#", false);
		s = replaceOutsideLiteral(s, "! ", "!", false);
		
		return s;
	}
	
	// Remove white space from each individual line
	static String[] removeWhiteSpace(String[] lines) {
		for (int i = 0; i < lines.length; i++) {
			lines[i] = replaceOutsideLiteral(lines[i], "  ", " ", false);
			lines[i] = replaceOutsideLiteral(lines[i], "\t", "", false).trim();
		}
		return lines;
	}
	
	// Break the given string into individual lines
	static String[] breakIntoLines(String text) {
		return text.split("\n");
	}
	
	// Return true if there are excess characters at the end of this line after the given function content
	static boolean checkForExcessCharacters(String line, String content) {
		int endIndex = line.lastIndexOf(content) + content.length() + 1;
		boolean hasExcessCharacters = endIndex < line.length();
		if (hasExcessCharacters) {
			if (endIndex == line.length() - 1) {
				printError("Excess character at end of line '" + line + "'");
			} else {
				printError("Excess characters at end of line '" + line + "'");
			}
		}
		return hasExcessCharacters;
	}
	
	// Returns the argument to a unary function (like ! or #) from the given startIndex
	static String getUnaryFunctionArgument(String text, int startIndex) {
		// Find the end index of the parameters
		char[] chars = text.toCharArray();
		
		startIndex++;
		
		if (startIndex >= chars.length) {
			printError("Missing operand in '" + text + "'");
			return null;
		}
		
		int endIndex = startIndex;
		int numParentheses = 0;
		int numBrackets = 0;
		boolean isInsideString = false;
		boolean foundArgumentsEnd = false;
		
		while (endIndex < chars.length) {
			if (chars[endIndex] == '"') {
				isInsideString = !isInsideString;
			} else if (chars[endIndex] == '(') {
				numParentheses++;
			} else if (chars[endIndex] == ')') {
				numParentheses--;
			} else if (chars[endIndex] == '[') {
				numBrackets++;
			} else if (chars[endIndex] == ']') {
				numBrackets--;
			}
			
			// If this is the first parenthetic and bracket level
			if (numParentheses == 0 && numBrackets == 0 && !isInsideString) {
				if (endIndex == chars.length-1) {
					endIndex++;
					foundArgumentsEnd = true;
					break;
				} else if (chars[endIndex] == ' ') {
					foundArgumentsEnd = true;
					break;
				}
			}
			
			endIndex++;
		}
		
		if (numParentheses > 0) {
			printError("Missing closing parenthesis in '" + text + "'");
			return null;
		}
		
		if (numBrackets > 0) {
			printError("Missing closing square brackets in '" + text + "'");
			return null;
		}
		
		if (isInsideString) {
			printError("Missing closing quotation in '" + text + "'");
			return null;
		}
		
		if (!foundArgumentsEnd) {
			printError("Malformed operand in '" + text + "'");
			return null;
		}
		
		return text.substring(startIndex, endIndex);
	}
	
	// Return the arguments to a function whose name starts at the given index
	static String getFunctionArguments(String text, int startIndex) {
		// Find the end index of the parameters (according to the end parenthesis)
		char[] chars = text.toCharArray();
		boolean foundArgumentsStart = false;
		
		// Find the starting index of the arguments
		while (startIndex < chars.length) {
			if (chars[startIndex] == '(') {
				foundArgumentsStart = true;
				startIndex++;
				break;
				
				// If this is an invalid symbol
			} else if (!Parser.isLetter(chars[startIndex]) && chars[startIndex] != ' ' &&
					!Parser.isDigit(chars[startIndex]) && chars[startIndex] != '_' &&
					chars[startIndex] != '$') {
				break;
			}
			startIndex++;
		}
		
		if (!foundArgumentsStart || startIndex >= chars.length) {
			printError("Function is missing arguments in '" + text + "'");
			return null;
		}

		int endIndex = startIndex;
		int numParentheses = 1;
		int numBrackets = 0;
		boolean isInsideString = false;
		boolean foundArgumentsEnd = false;
		
		while (endIndex < chars.length) {
			if (chars[endIndex] == '"') {
				isInsideString = !isInsideString;
			} else if (chars[endIndex] == '(') {
				numParentheses++;
			} else if (chars[endIndex] == ')') {
				numParentheses--;
				
				// If this is the first parenthetic and bracket level
				if (numParentheses == 0 && numBrackets == 0 && !isInsideString) {
					foundArgumentsEnd = true;
					break;
				}
			} else if (chars[endIndex] == '[') {
				numBrackets++;
			} else if (chars[endIndex] == ']') {
				numBrackets--;
			}
			endIndex++;
		}
		
		if (numParentheses > 0) {
			printError("Missing closing parenthesis in '" + text + "'");
			return null;
		}
		
		if (numBrackets > 0) {
			printError("Missing closing square brackets in '" + text + "'");
			return null;
		}
		
		if (isInsideString) {
			printError("Missing closing quotation in '" + text + "'");
			return null;
		}
		
		if (!foundArgumentsEnd || endIndex >= chars.length) {
			printError("Malformed arguments in '" + text + "'");
			return null;
		}
		
		return text.substring(startIndex, endIndex);
	}
	
	// Return true if this line starts with the given word, and is followed by a
	//   space or parenthesis.
	static boolean doesLineStartWith(String line, String word) {
		return line.length() >= word.length() && line.startsWith(word) &&
				(line.length() == word.length() ||
				line.charAt(word.length()) == '(' || line.charAt(word.length()) == ' ');
	}
	
	// Return the next operator and its end index.
	// Start searching from the given index.
	static Object[] getFirstAssignmentOperator(String line, int startIndex) {
		
		// Skip past the spaces, if any
		while (startIndex < line.length() && line.charAt(startIndex) == ' ') {
			startIndex++;
		}
		
		String substring = line.substring(startIndex);
		
		// Iterate over all of the assignment operators
		for (int i = 0; i < assignmentOperators.length; i++) {
			if (substring.startsWith(assignmentOperators[i])) {
				return new Object[] {assignmentOperators[i], startIndex + assignmentOperators[i].length()};
			}
		}
		
		return null;
	}
	
	// Return true if this line has an assignment operator in it
	static boolean hasAssignmentOperator(String line) {
		// Skip past variable names
		int startIndex = -1;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (!isLetter(c) && !isDigit(c) && c != '_' && c != '$' && c != ' ') {
				startIndex = i;
				break;
			}
		}
		
		if (startIndex == -1) {
			return false;
		}
		
		// Start searching for the assignment operator here
		for (int i = startIndex; i < line.length(); i++) {
			String substring = line.substring(startIndex);
			
			// Iterate over all of the assignment operators
			for (int j = 0; j < assignmentOperators.length; j++) {
				if (substring.startsWith(assignmentOperators[j])) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	// Return true if this line is a function call on its own
	static boolean isFunctionCall(String line) {
		char[] chars = line.toCharArray();
		
		Object[] data = getVariableName(line, 0);
		if (data == null) {
			return false;
		}
		
		// Iterate past any space to find the opening parenthesis
		int nameEndIndex = (int)data[1];
		for (int i = nameEndIndex; i < chars.length; i++) {
			if (chars[i] != ' ') {
				if (chars[i] == '(') {
					return true;
				} else {
					return false;
				}
			}
		}
		
		return false;
	}
	
	// Return the name of this function and the argument string that goes with it
	static String[] getFunctionNameAndArgs(String line) {
		// Find the first opening parenthesis
		
		int parenthesisIndex = line.indexOf('(');
		if (parenthesisIndex == -1) {
			printError("Arguments missing in function call");
			return null;
		}
		
		String name = line.substring(0, parenthesisIndex).trim();
		
		String args = getFunctionArguments(line, parenthesisIndex);
		
		if (args != null) {
			return new String[] {name, args};
		}
		return null;
	}
	
	// Return which data type this line starts with, if any.
	// Also return the end index of this type-string.
	static Object[] getFirstDataType(String line) {
		for (int i = 0; i < dataTypes.length; i++) {
			int length = dataTypes[i].length();
			
			if (line.startsWith(dataTypes[i]) && line.length() > length) {
				
				if (line.charAt(length) == ' ') {
					return new Object[] {Type.getTypeFromString(dataTypes[i]), length};
				} else if (line.charAt(length) == '[') { // This must be an array
					// Determine the dimension of this array
					int commaCount = 0;
					int endIndex = 0;
					
					for (int j = length+1; j < line.length(); j++) {
						if (line.charAt(j) == ',') {
							commaCount++;
						} else if (line.charAt(j) == ']') {
							endIndex = j+1;
							break;
						} else {
							printError("Malformed array syntax");
							return null;
						}
					}
					
					int dimension = commaCount + 1;
					Type type = Type.getArrayTypeFromString(dataTypes[i]);
					type.dimensions = dimension;
					return new Object[] {type, endIndex};
				}
			}
		}
		
		return null;
	}
	
	// Return true if this is an array definition
	static boolean isArrayDefinition(String s) {
		for (int i = 0; i < dataTypes.length; i++) {
			int length = dataTypes[i].length();
			if (s.startsWith(dataTypes[i]) && s.length() > length) {
				if (s.charAt(length) == '[') { // This must be an array
					return true;
				}
			}
		}
		
		return false;
	}
	
	// Return true if this is (probably) an array reference (read)
	static boolean isArrayReference(String s) {
		// Find the first opening bracket
		for (int i = 1; i < s.length()-1; i++) {
			if (s.charAt(i) == '[') {
				return true;
			} else if (s.charAt(i) == ' ') { // No spaces allowed before the bracket
				return false;
			}
		}
		
		return false;
	}
	
	// Extract the variable name from the array reference (remove the trailing brackets)
	static String getVariableNameFromArrayReference(String s) {
		// Find the first opening bracket
		for (int i = 1; i < s.length()-1; i++) {
			if (s.charAt(i) == '[') {
				return s.substring(0, i);
			} else if (s.charAt(i) == ' ') { // No spaces allowed before the bracket
				return null;
			}
		}
		return null;
	}
	
	// Return the type of array, and an array of string expressions
	//   that represent the dimensions of this array instantiation.
	static Object[] getArrayDefinitionInfo(String s) {
		for (int i = 0; i < dataTypes.length; i++) {
			int length = dataTypes[i].length();
			if (s.startsWith(dataTypes[i]) && s.length() > length) {
				if (s.charAt(length) == '[') { // This must be an array
					int endIndex = -1;
					boolean isInsideString = false;
					int numParentheses = 0;
					int numBrackets = 1;
					
					// Find the next matching closing bracket
					for (int j = length+1; j < s.length(); j++) {
						if (s.charAt(j) == '[') {
							numBrackets++;
						} else if (s.charAt(j) == ']') {
							numBrackets--;
							if (numBrackets == 0 && numParentheses == 0 && !isInsideString) {
								endIndex = j;
								break;
							}
						} else if (s.charAt(j) == '(') {
							numParentheses++;
						} else if (s.charAt(j) == ')') {
							numParentheses--;
						} else if (s.charAt(j) == '"') {
							isInsideString = !isInsideString;
						}
					}
					
					if (endIndex == -1) {
						printError("Missing end bracket in expression '" + s + "'");
						return null;
					}
					
					// Separate the data into the individual arguments (the dimensions of the array)
					String[] args = separateArguments(s.substring(length+1, endIndex));
					
					return new Object[] {Type.getArrayTypeFromString(dataTypes[i]), args};
				}
			}
		}
		
		return null;
	}
	
	// Return the array name, and an array of string expressions
	//   that represent the indexes of this array reference.
	static Object[] getArrayReadInfo(String s) {
		String varName = getVariableNameFromArrayReference(s);
		
		int endIndex = -1;
		boolean isInsideString = false;
		int numParentheses = 0;
		int numBrackets = 1;
		
		// Find the next matching closing bracket
		for (int j = varName.length() + 1; j < s.length(); j++) {
			if (s.charAt(j) == '[') {
				numBrackets++;
			} else if (s.charAt(j) == ']') {
				numBrackets--;
				if (numBrackets == 0 && numParentheses == 0 && !isInsideString) {
					endIndex = j;
					break;
				}
			} else if (s.charAt(j) == '(') {
				numParentheses++;
			} else if (s.charAt(j) == ')') {
				numParentheses--;
			} else if (s.charAt(j) == '"') {
				isInsideString = !isInsideString;
			}
		}
		
		if (endIndex == -1) {
			printError("Missing end bracket in expression '" + s + "'");
			return null;
		}
		
		// Separate the data into the individual arguments (the dimensions of the array)
		String[] args = separateArguments(s.substring(varName.length() + 1, endIndex));
		
		return new Object[] {varName, args};
	}
	
	// Return the indexes (as a string array) and the end index of the given array indexing string. 
	static Object[] getArrayIndexInfo(String s, int startIndex) {
		if (s.charAt(startIndex) != '[') { // This must be an array
			return null;
		}
		
		int endIndex = -1;
		boolean isInsideString = false;
		int numParentheses = 0;
		int numBrackets = 1;
		
		// Find the next matching closing bracket
		for (int j = startIndex+1; j < s.length(); j++) {
			if (s.charAt(j) == '[') {
				numBrackets++;
			} else if (s.charAt(j) == ']') {
				numBrackets--;
				if (numBrackets == 0 && numParentheses == 0 && !isInsideString) {
					endIndex = j;
					break;
				}
			} else if (s.charAt(j) == '(') {
				numParentheses++;
			} else if (s.charAt(j) == ')') {
				numParentheses--;
			} else if (s.charAt(j) == '"') {
				isInsideString = !isInsideString;
			}
		}
		
		if (endIndex == -1) {
			printError("Missing end bracket in expression '" + s + "'");
			return null;
		}
		
		// Separate the data into the individual arguments (the dimensions of the array)
		String[] args = separateArguments(s.substring(startIndex + 1, endIndex));
		
		return new Object[] {args, endIndex};
	}
	
	// Return the arguments of this function separated by commas in a string array.
	// The given string is the whole string of arguments, parentheses excluded.
	static String[] separateArguments(String s) {
		// No arguments here
		if (s.isEmpty()) {
			return new String[0];
		}
		
		ArrayList<Integer> commaIndices = new ArrayList<Integer>(3);
		commaIndices.add(-1);
		
		int numParentheses = 0;
		int numBrackets = 0;
		boolean isInString = false;
		
		// Search for commas at the lowest level
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\"') {
				isInString = !isInString;
			} else if (c == '[') {
				numBrackets++;
			} else if (c == ']') {
				numBrackets--;
			} else if (c == '(') {
				numParentheses++;
			} else if (c == ')') {
				numParentheses--;
			} else if (c == ',') {
				if (!isInString && numParentheses == 0 && numBrackets == 0) {
					commaIndices.add(i);
				}
			}
		}
		
		if (numParentheses != 0) {
			printError("Missing parentheses in expression '" + s + "'");
			return null;
		}
		if (numBrackets != 0) {
			printError("Missing bracket in expression '" + s + "'");
			return null;
		}
		if (isInString) {
			printError("Missing quotation in expression '" + s + "'");
			return null;
		}
		
		commaIndices.add(s.length());
		
		String[] args = new String[commaIndices.size()-1];
		for (int i = 0; i < args.length; i++) {
			args[i] = s.substring(commaIndices.get(i)+1, commaIndices.get(i+1)).trim();
		}
		
		return args;
	}
	
	// Return true if the array of strings contains a duplicate entry
	static boolean checkForDuplicates(String[] args) {
		for (int i = 0; i < args.length-1; i++) {
			for (int j = i+1; j < args.length; j++) {
				if (args[i].equals(args[j])) {
					return true;
				}
			}
		}
		return false;
	}
	
	// Return the first variable name and end index starting at the given index.
	static Object[] getVariableName(String s, int firstIndex) {
		char[] chars = s.toCharArray();
		
		// Check if everything before the first parenthesis is a valid identifier
		boolean foundValidChar = false;
		int endIndex = firstIndex;
		for (int i = firstIndex; i < chars.length; i++) {
			char c = chars[i];
			
			// If this is a valid character
			if (isLetter(c) || c == '_' || c == '$' || (i > firstIndex && isDigit(c))) {
				foundValidChar = true;
			} else if (foundValidChar) {
				endIndex = i;
				break;
			}
		}
		
		// If the beginning and the end are the same, then there is no content
		if (endIndex == firstIndex) {
			return null;
		}
		
		// Extract the variable name
		return new Object[] {s.substring(firstIndex, endIndex), endIndex};
	}
	
	// Return the first variable name after a space on this line, and its end index
	static Object[] getVariableNameAfterSpace(String s) {
		
		char[] chars = s.toCharArray();
		
		// Search for the first space
		int firstIndex = 0;
		boolean foundFirstSpace = false;
		while (firstIndex < chars.length) {
			if (chars[firstIndex] == ' ') {
				foundFirstSpace = true;
			} else if (foundFirstSpace) {
				// If we had a space before, but this character is not a space,
				//    then the word begins here
				break;
			}
			firstIndex++;
		}
		
		// If the space index is zero, then this is invalid
		if (firstIndex == 0) {
			return null;
		}
		
		int endIndex = firstIndex;
		boolean isFirstCharacter = true;
		while (endIndex < chars.length) {
			char c = chars[endIndex];
			// Stop once we found an invalid character
			if (isFirstCharacter) {
				// If this is an invalid character
				if (!isLetter(c) && c != '_' && c != '$') {
					break;
				} else { // This must be a valid character
					isFirstCharacter = false;
				}
			} else {
				// If this is an invalid character
				if (!isLetter(c) && !isDigit(c) && c != '_' && c != '$') {
					break;
				}
			}
			endIndex++;
		}
		
		// If the beginning and the end are the same or one off, then there is no content
		if (endIndex == firstIndex) {
			return null;
		}
		
		// Extract the variable name
		return new Object[] {s.substring(firstIndex, endIndex), endIndex};
	}
	
	/*
	// Add spaces around negative signs that are being used for subtraction,
	//   and not ones in strings or ones being used for negative numbers.
	static String addSpacesAroundSubtractions(String o) {
		boolean isInsideString = false;
		for (int i = 1; i < o.length()-1; i++) {
			if (o.charAt(i) == '"') {
				isInsideString = !isInsideString;
			} else if (!isInsideString && o.charAt(i) == '-') {
				// If the previous character is a number of value of some sort
				if (isPrecededByValue(o, i-1)) {
					o = o.substring(0, i) + " - " + o.substring(i+1);
					i += 2;
				}
			}
		}
		return o;
	}
	*/
	
	// Return true if this index in the string is preceded by some sort of number or variable
	static boolean isPrecededByValue(String s, int index) {
		index--;
		
		while (index >= 0) {
			char c = s.charAt(index);
			if (c != ' ') {
				if (isDigit(c) || isLetter(c) || c == ']' || c == ')' ||
						c == '}' || c == '_' || c == '.') {
					return true;
				} else {
					return false;
				}
			}
			index--;
		}
		
		return false;
	}
	
	// Replace all negative signs with "-1 *"
	static String replaceNegativesWithMultiplication(String s) {
		
		for (int i = 0; i < s.length()-1; i++) {
			if (s.charAt(i) == '-') {
				
				// If this negative sign (not a subtraction)
				if (!isPrecededByValue(s, i)) {
					// Try to get past spaces
					char trailingChar = s.charAt(i + 1);
					if (trailingChar == ' ' && i + 2 < s.length()) {
						trailingChar = s.charAt(i + 2);
					}
					
					// Make sure the next character is not part of a literal number
					if (!isDigit(trailingChar) && trailingChar != '.') {
					
						// Replace '-' with "-1 * "
						s = s.substring(0, i) + "-1 * " + s.substring(i+1);
					}
				}
			}
		}
		
		return s;
	}
	
	// Replace the string with the new string, but avoid it between quotation marks.
	// If 'wholeWord' is true, then then reject matches embedded between other letters
	static String replaceOutsideLiteral(String original, String old, String rep, boolean wholeWord) {
		boolean isInLiteral = false;
		outer:
		for (int i = 0; i < original.length() - old.length() + 1; i++) {
			if (original.charAt(i) == '\"') {
				isInLiteral = !isInLiteral;
			}
			
			// Only do the replacement if this is not in a literal
			if (!isInLiteral) {
				for (int j = 0; j+i < original.length() && j < old.length(); j++) {
					if (original.charAt(i+j) != old.charAt(j)) {
						continue outer;
					}
				}
				
				// If there need to not be characters on either side
				if (wholeWord) {
					// Make sure that there is not a character directly on either side of this
					if (i > 0 &&
							(isLetter(original.charAt(i-1)) ||
									original.charAt(i-1) == '=')) {
						continue;
					}
					if (i + old.length() < original.length() &&
							(isLetter(original.charAt(i + old.length())) ||
									original.charAt(i + old.length()) == '=')) {
						continue;
					}
				}
				
				// This is a match, so replace it
				String firstPart = original.substring(0, i);
				String secondPart = original.substring(i + old.length());
				original = firstPart + rep + secondPart;
				
				// Skip over this replacement
				int delta = rep.length() - old.length() + 1;
				if (delta < 0) {
					delta = 0;
				}
				i += delta;
			}
		}
		return original;
	}
	
	// Return information about the lowest precedence binary operator on the lowest parenthetic level
	static Object[] findLowestPrecedenceBinaryOperatorAtLowestLevel(final String s) {
		
		// Search through the operations in order of operations
		for (int j = 0; j < binaryOperators.length; j++) {
			int numParentheses = 0;
			int numBrackets = 0;
			boolean isInString = false;
			
			// Binary operator cannot be the first symbol.
			// We want the last one, so iterate backward.
			for (int i = s.length()-1; i > 0; i--) {
				char c = s.charAt(i);
				if (c == '\"') {
					isInString = !isInString;
				} else if (c == '[') {
					numBrackets++;
				} else if (c == ']') {
					numBrackets--;
				} else if (c == '(') {
					numParentheses++;
				} else if (c == ')') {
					numParentheses--;
				} else {
					// Try to find and return the binary operator and index if possible
					String binaryOperator = getBinaryOperatorFromList(s, i, binaryOperators[j]);
					if (binaryOperator != null && numParentheses == 0 &&
							numBrackets == 0 && !isInString) {
						
						return new Object[] {binaryOperator, i, i + binaryOperator.length()};
					}
				}
			}
		}
		
		return null;
	}
	
	// Remove unnecessary surrounding parentheses
	static String removeUnnecessaryParentheses(String s) {
		
		// Repeat while there are parentheses at the beginning and the end
		while (!s.isEmpty() && s.charAt(0) == '(' && s.charAt(s.length()-1) == ')') {
			// Make sure the parentheses are in fact redundant
			int numParentheses = 1;
			boolean isInString = false;
			
			for (int i = 1; i < s.length()-1; i++) {
				if (s.charAt(i) == '"') {
					isInString = !isInString;
				} else if (!isInString) {
					if (s.charAt(i) == '(') {
						numParentheses++;
					} else if (s.charAt(i) == ')') {
						numParentheses--;
						
						// If the parenthetic level ever reaches zero,
						//    then these parentheses don't contain the whole expression.
						if (numParentheses == 0) {
							return s;
						}
					}
				}
			}
			s = s.substring(1, s.length() - 1);
		}
		
		return s;
	}
	
	// Return which binary operator this is at the given starting index, if any
	static String getBinaryOperator(String s, int index) {
		
		// Binary operators are always padded by spaces
		if (index == 0 || index == s.length()-1 || s.charAt(index-1) != ' ' || s.charAt(index+1) != ' ') {
			return null;
		}
		
		String substring = s.substring(index);
		
		// Iterate over every binary operator
		for (int i = 0; i < binaryOperators.length; i++) {
			for (int j = 0; j < binaryOperators[i].length; j++) {
				
				if (substring.startsWith(binaryOperators[i][j]) &&
							s.charAt(index + binaryOperators[i][j].length()) == ' ') {
					return binaryOperators[i][j];
				}
			}
		}
		
		return null;
	}
	
	// Return which binary operator this is at the given starting index, if any.
	// Do this without relying on surrounding spaces.
	static String getBinaryOperatorNoSpaces(String s, int index) {
		
		// Binary operators cannot be at the end of a line
		if (index == 0 || index == s.length()-1) {
			return null;
		}
		
		String substring = s.substring(index);
		
		// Iterate over every binary operator (and take the longest one)
		String longestOperator = null;
		int length = 0;
		for (int i = 0; i < binaryOperators.length; i++) {
			for (int j = 0; j < binaryOperators[i].length; j++) {
				String op = binaryOperators[i][j];
				
				// If this binary operator is the last character on the line
				if (index + op.length() >= s.length()) {
					continue;
				}
				
				// Find the previous non-space character
				char previousChar = 0;
				for (int k = index-1; k >= 0; k--) {
					if (s.charAt(k) != ' ') {
						previousChar = s.charAt(k);
						break;
					}
				}
				
				// If there is no previous character, then this is not a binary operator
				if (previousChar == 0) {
					continue;
				}
				
				// Make sure that all binary operators are preceded by some sort of value
				if (!isPrecededByValue(s, index)) {
					continue;
				}
				
				char trailingChar = s.charAt(index + op.length());
				
				/*
				// If this is a negative sign, and a number is directly trailing it, then
				//   it is not a binary operator.
				if (op.equals("-") && (isDigit(trailingChar) || trailingChar == '.')) {
					continue;
				}
				*/
				
				// Make sure that the next character is a valid symbol
				if (isLetter(trailingChar) || isDigit(trailingChar) || trailingChar == '.'
						|| trailingChar == '(' || trailingChar == '!' || trailingChar == ' '
						|| trailingChar == '#' || trailingChar == '@'
						|| trailingChar == '_' || trailingChar == '-') {
					
					if (substring.startsWith(op)) {
						if (op.length() > length) {
							length = op.length();
							longestOperator = op;
						}
					}
				}
			}
		}
		
		// Iterate over the assignment operators too
		for (int i = 0; i < assignmentOperators.length; i++) {
			String op = assignmentOperators[i];
			
			// If this binary operator is the last character on the line
			if (index + op.length() >= s.length()) {
				continue;
			}
			
			// Make sure that the next character is not an invalid symbol
			char trailingChar = s.charAt(index + op.length());
			if (isLetter(trailingChar) || isDigit(trailingChar) || trailingChar == '.'
					|| trailingChar == '(' || trailingChar == '!' || trailingChar == '"'
					|| trailingChar == '\'' || trailingChar == ' ' || trailingChar == '#'
					|| trailingChar == '@' || trailingChar == '_') {
				
				if (substring.startsWith(op)) {
					if (op.length() > length) {
						length = op.length();
						longestOperator = op;
					}
				}
			}
		}
		
		return longestOperator;
	}
	
	// Return which binary operator this is at the given starting index, if any
	static String getBinaryOperatorFromList(String s, int index, String[] operators) {
		
		// Binary operators cannot be the last character in a line
		if (index == 0 || index == s.length()-1 || s.charAt(index-1) != ' ') {
			return null;
		}
		
		String substring = s.substring(index);
		// Iterate over every binary operator in the given list
		for (int j = 0; j < operators.length; j++) {
			// Binary operators must have a space afterward
			if (substring.startsWith(operators[j]) && s.charAt(index + operators[j].length()) == ' ') {
				return operators[j];
			}
		}
		
		return null;
	}
	
	// Return true if this is a single number (integer, float, double, etc.)
	static boolean isNumber(final String s) {
		char[] chars = s.toCharArray();
		
		boolean alreadyGotDigit = false;
		boolean alreadyGotSign = false;
		boolean alreadyGotMiddleSpace = false;
		boolean alreadyFoundDecimal = false;
		
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '-' || chars[i] == '+') {
				if (alreadyGotSign || alreadyGotDigit || alreadyGotMiddleSpace || alreadyFoundDecimal) {
					return false;
				}
				alreadyGotSign = true;
			} else if (chars[i] == '.') {
				if (alreadyFoundDecimal) {
					return false;
				}
				alreadyFoundDecimal = true;
			} else if (chars[i] == ' ') {
				if (alreadyGotDigit || alreadyFoundDecimal) {
					alreadyGotMiddleSpace = true;
				}
			} else if (isDigit(chars[i])) {
				if (alreadyGotMiddleSpace) {
					return false;
				}
				alreadyGotDigit = true;
			} else {
				return false;
			}
		}
		
		if (!alreadyGotDigit) {
			return false;
		}
		
		return true;
	}

	// Return true if this is a 64-bit float
	static boolean isDouble(final String s) {
		char[] chars = s.toCharArray();
		
		boolean alreadyGotDigit = false;
		boolean alreadyGotSign = false;
		boolean alreadyGotMiddleSpace = false;
		boolean alreadyFoundDecimal = false;
		
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '-' || chars[i] == '+') {
				if (alreadyGotSign || alreadyGotDigit || alreadyGotMiddleSpace || alreadyFoundDecimal) {
					return false;
				}
				alreadyGotSign = true;
			} else if (chars[i] == '.') {
				if (alreadyFoundDecimal) {
					return false;
				}
				alreadyFoundDecimal = true;
			} else if (chars[i] == ' ') {
				if (alreadyGotDigit || alreadyFoundDecimal || alreadyGotSign) {
					alreadyGotMiddleSpace = true;
				}
			} else if (isDigit(chars[i])) {
				if (alreadyGotMiddleSpace) {
					return false;
				}
				alreadyGotDigit = true;
			} else {
				return false;
			}
		}
		
		if (!alreadyGotDigit) {
			return false;
		}
		
		return true;
	}
	
	// Return true if this is a 32-bit float
	static boolean isFloat(final String s) {
		char[] chars = s.toCharArray();
		
		boolean alreadyGotDigit = false;
		boolean alreadyGotSign = false;
		boolean alreadyGotMiddleSpace = false;
		boolean alreadyFoundDecimal = false;
		
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '-' || chars[i] == '+') {
				if (alreadyGotSign || alreadyGotDigit || alreadyGotMiddleSpace || alreadyFoundDecimal) {
					return false;
				}
				alreadyGotSign = true;
			} else if (chars[i] == '.') {
				if (alreadyFoundDecimal) {
					return false;
				}
				alreadyFoundDecimal = true;
			} else if (chars[i] == ' ') {
				if (alreadyGotDigit || alreadyFoundDecimal || alreadyGotSign) {
					alreadyGotMiddleSpace = true;
				}
			} else if (isDigit(chars[i])) {
				if (alreadyGotMiddleSpace) {
					return false;
				}
				alreadyGotDigit = true;
			} else if (chars[i] == 'f' || chars[i] == 'F') {
				if (!alreadyGotDigit) {
					return false;
				}
				if (i != chars.length-1) { // f must be the last symbol in a float
					return false;
				}
			} else {
				return false;
			}
		}
		
		if (!alreadyGotDigit) {
			return false;
		}
		
		return true;
	}
	
	// Return true if this is a signed 32-bit integer
	static boolean isSignedInteger(final String s) {
		char[] chars = s.toCharArray();
		
		boolean alreadyGotDigit = false;
		boolean alreadyGotSign = false;
		int digitStart = -1;
		
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '-' || chars[i] == '+') {
				if (alreadyGotSign || alreadyGotDigit) {
					return false;
				}
				alreadyGotSign = true;
			} else if (chars[i] == ' ') {
				if (alreadyGotDigit || alreadyGotSign) {
					return false;
				}
			} else if (isDigit(chars[i])) {
				if (!alreadyGotDigit) {
					digitStart = i;
				}
				alreadyGotDigit = true;
			} else {
				return false;
			}
		}
		
		if (!alreadyGotDigit) {
			return false;
		}
		
		// Check if this number is too big to be a signed int
		try {
			Integer.parseInt(s.substring(digitStart));
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}

	// Return true if this is a signed 64-bit long
	static boolean isSignedLong(final String s) {
		char[] chars = s.toCharArray();
		
		boolean alreadyGotDigit = false;
		boolean alreadyGotSign = false;
		int digitStart = -1;
		
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '-' || chars[i] == '+') {
				if (alreadyGotSign || alreadyGotDigit) {
					return false;
				}
				alreadyGotSign = true;
			} else if (chars[i] == ' ') {
				if (alreadyGotDigit || alreadyGotSign) {
					return false;
				}
			} else if (isDigit(chars[i])) {
				if (!alreadyGotDigit) {
					digitStart = i;
				}
				alreadyGotDigit = true;
			} else if (chars[i] == 'L') {
				if (!alreadyGotDigit) {
					return false;
				}
				if (i != chars.length-1) { // L must be the last symbol in a long
					return false;
				}
			} else {
				return false;
			}
		}
		
		if (!alreadyGotDigit) {
			return false;
		}
		
		// Check if this number is too big to be a signed int
		try {
			// Get rid of the L before parsing
			if (s.charAt(s.length()-1) == 'L') {
				Long.parseLong(s.substring(digitStart, s.length()-1));
			} else {
				Long.parseLong(s.substring(digitStart));
			}
		} catch (Exception e) {
			return false;
		}
		
		return true;
	}
	
	// Return true if this entire given string is a literal (has quotation marks at the beginning and end only)
	static boolean isString(String s) {
		if (s.charAt(0) == '\"' && s.charAt(s.length()-1) == '\"') {
			// Try to find another quotation mark in the string
			for (int i = 1; i < s.length()-1; i++) {
				if (s.charAt(i) == '\"') {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	// Return true if this is a bool value
	static boolean isBool(String text) {
		return text.equals("true") || text.equals("false");
	}
	
	// Return true if this is a alphabetical letter
	static boolean isLetter(char a) {
		return (a >= 65 && a <= 90) || (a >= 97 && a <= 122);
	}
	
	// Return true if this is a number (0-9)
	static boolean isDigit(char a) {
		return a >= 48 && a <= 57;
	}
	
	// Parse an integer
	static int parseInt(String val) {
		try {
			return Integer.parseInt(val);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	// Parse a long
	static long parseLong(String val) {
		try {
			// Get rid of the L before parsing
			if (val.charAt(val.length()-1) == 'L') {
				return Long.parseLong(val.substring(0, val.length()-1));
			} else {
				return Long.parseLong(val);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	// Parse a float
	static float parseFloat(String val) {
		try {
			return Float.parseFloat(val);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	// Parse a double
	static double parseDouble(String val) {
		try {
			return Double.parseDouble(val);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
	
	// Parse a bool
	static boolean parseBool(String val) {
		if (val.equals("true")) {
			return true;
		} else if (val.equals("false")) {
			return false;
		} else {
			new Exception("Cannot parse " + val + " as boolean").printStackTrace();
			return false;
		}
	}
	
	// Capitalize the first letter of a string
	static String capitalize(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}
	
	static void printError(String message) {
		System.out.println(message);
		if (Compiler.currentParsingLineNumber < Compiler.lines.length) {
			System.out.println("'" + Compiler.lines[Compiler.currentParsingLineNumber] + "'");
		}
		System.out.println("(on line " + (Compiler.currentParsingLineNumber + 1) + ")");
		
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		for (int i = 2; i < stackTrace.length-1; i++) {
			print(stackTrace[i]);
		}
		/*
		String lineNum = Thread.currentThread().getStackTrace()[2].toString();
		print(lineNum.substring(lineNum.indexOf('(')));
		*/
	}
	
	static void print(Object o) {
		if (Compiler.debugPrintOn) {
			System.out.println(o);
		}
	}
}

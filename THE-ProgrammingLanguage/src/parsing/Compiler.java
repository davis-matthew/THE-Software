package parsing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

// Created by Daniel Williams
// Created on May 31, 2020
// Last updated on May 27, 2024

// A fast programming language that maybe is good.

public class Compiler {
	
	static final String fileToRead = "testFiles/ProgramInput.txt";
	static final String fileToWrite = "testFiles/ProgramOutput.txt";
	
	// The current line that is being parsed
	static int currentParsingLineNumber = -1;
	
	// The list of instructions as they are compiled
	static ArrayList<Instruction> instructions;
	
	// List of literal instructions from the source code to be parsed
	static String[] lines = null;
	
	// Whether to view debug printing or not
	static final boolean debugPrintOn = true;
	
	public static void main(String[] args) {
		
		String text = loadFile(fileToRead);
		lines = Parser.breakIntoLines(text);
		lines = Parser.removeWhiteSpace(lines);
		
		// Remove comments
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
		
		// Estimate the number of instructions in this program
		instructions = new ArrayList<Instruction>(lines.length*5 + 10);
		
		currentParsingLineNumber = 0;
		
		boolean hadError = parseLinesAtLevel(null);
		if (hadError) {
			return;
		}
		
		// Check for invalid scope
		if (lines.length > currentParsingLineNumber) {
			printError("Extra ']' in program");
			return;
		}
		
		// Check the contents of the main routine for code between sub-routines
		boolean foundCodeAfterRoutine = searchForCodeAfterRoutine(null);
		if (foundCodeAfterRoutine) {
			printError("Method may not be followed by sequential code");
			return;
		}
		
		// Find which method each method call was referencing
		hadError = findMethodCallReferences();
		if (hadError) {
			return;
		}
		
		// Print out all of the instructions to the console
		for (int i = 0; i < instructions.size(); i++) {
			print(instructions.get(i));
		}
		
		print("\nQuad instructions:");
		ArrayList<QuadInstruction> quadInstructions = convertToQuadInstructions();
		for (int i = 0; i < quadInstructions.size(); i++) {
			print(quadInstructions.get(i));
		}
		print("");
		
		//saveFile(fileToWrite, text);
	}
	
	// Parse all lines of code at a certain indentation level (in a certain scope)
	// Return true if there was an error.
	static boolean parseLinesAtLevel(Instruction parentInstruction) {
		
		// True if this is the shallowest level
		boolean isFirstRecursion = currentParsingLineNumber == 0;
		
		// Parse every line until we get to the end, or we exit the current scope
		do {
			int i = currentParsingLineNumber;
			
			// Don't parse empty lines
			if (lines[i].isEmpty()) {
				currentParsingLineNumber++;
				continue;
			}
			
			// If this is decreasing scope, then return from here
			if (lines[i].equals("]")) {
				return false;
			}
			
			// If this was an If or ElseIf instruction, then we need to drop down a level
			//    after parsing the contained instructions.
			if (lines[i].equals("else") || Parser.doesLineStartWith(lines[i], "elseif")) {
				// Else's and ElseIfs are parsed from one recursion too deep, so
				//    parse this line from the parent's perspective.
				Instruction grandparentInstruction = null;
				if (parentInstruction != null) {
					grandparentInstruction = parentInstruction.parentInstruction;
				} else {
					printError("Incorrect scope");
					return true;
				}
				
				// Parse this line (instructions are added inside)
				boolean hadError = parseLine(grandparentInstruction, lines[i]);
				if (hadError) {
					return true;
				}
				
				// Always return here because the Else decreases the scope.
				return false;
			} else if (Parser.doesLineStartWith(lines[i], "case") || lines[i].equals("default")) {
				// Cases are parsed inside the parseLine function
				return false;
			} else {
				// Parse this line (instructions are added inside)\
				boolean hadError = parseLine(parentInstruction, lines[i]);
				if (hadError) {
					return true;
				}
			}
			
			// If this is the end of a while, decrease scope after parsing
			if (Parser.doesLineStartWith(lines[i], "] while")) {
				return false;
			}
			
			currentParsingLineNumber++;
		} while (currentParsingLineNumber < lines.length);
		
		// The code will only get here if there is a missing ending bracket
		if (!isFirstRecursion) {
			printError("Missing ']' in program");
			return true;
		}
		
		return false;
	}
	
	// Parse a single line of code.
	// Return true if there was an error.
	static boolean parseLine(Instruction parentInstruction, String line) {
		
		if (line.equals("break") || line.equals("continue")) { // Break or continue in loop
			
			Instruction instr;
			if (line.equals("break")) {
				instr = new Instruction(InstructionType.Break);
			} else {
				instr = new Instruction(InstructionType.Continue);
			}
			
			instr.stringRepresentation = instr.instructionType.toSymbolForm();
			instr.returnType = Type.Void; // Doesn't return anything
			instr.parentInstruction = parentInstruction;

			// Find the nearest ancestor loop that contains this statement
			Instruction parentLoop = findNearestAncestorLoop(parentInstruction);
			if (parentLoop == null) {
				printError(Parser.capitalize(instr.instructionType.toSymbolForm())
						+ "statement must be within a loop");
				return true;
			}
			
			instr.createArgs(1);
			instr.argReferences[0] = parentLoop;
			instr.addTopologicalReference(parentLoop);
			
			instructions.add(instr);
			
		} else if (line.equals("[")) { // Enclosing scope (Enscope)
			
			Instruction instr = new Instruction(InstructionType.Enscope);
			instr.stringRepresentation = "enscope";
			instr.returnType = Type.Void; // Doesn't return anything
			instr.parentInstruction = parentInstruction;
			instructions.add(instr);
			
			// Get the instructions contained by this while-loop
			currentParsingLineNumber++;
			boolean hadError2 = parseLinesAtLevel(instr);
			if (hadError2) {
				return true;
			}
			
			// Create the EndBlock to end this scope enclosure
			Instruction endInstr = new Instruction(InstructionType.EndBlock);
			endInstr.stringRepresentation = "descope";
			endInstr.returnType = Type.Void;
			endInstr.parentInstruction = instr;
			instructions.add(endInstr);
			
			instr.endInstruction = endInstr;
			
		} else if (Parser.doesLineStartWith(line, "for")) { // For-loop
			
			// This instruction restricts the scope of the whole for-loop and initialization
			Instruction enscopeInstr = new Instruction(InstructionType.Enscope);
			enscopeInstr.stringRepresentation = "for-loop enscope";
			enscopeInstr.returnType = Type.Void; // Doesn't return anything
			enscopeInstr.parentInstruction = parentInstruction;
			instructions.add(enscopeInstr);
			
			// Get the contents of the for-loop header
			int conditionsStartIndex = 3;
			String expressionContent = line.substring(conditionsStartIndex);
			String[] args = Parser.separateArguments(expressionContent);
			if (args == null) {
				return true;
			}
			
			// Create the increment, start bound, and stop bound instructions
			Instruction stepValInstr;
			Instruction startBound;
			Instruction stopBound;
			Variable loopCounterVar;
			Variable forEachVar = null; // These two may be null
			Variable arrayVar = null;
			
			if (args.length == 0) { // Invalid
				printError("Missing arguments in For-loop header");
				return true;
			} else if (args.length == 1) { // For-each loop
				
				// The "in" keyword is needed in here
				if (args[0].indexOf(" in") == -1) {
					printError("For-each loop requires 'in' keyword");
					return true;
				}
				
				String[] forEachArgs = args[0].split(" in ");
				if (forEachArgs.length < 2 || forEachArgs[1].trim().isEmpty()) {
					printError("For-each loop missing arguments after 'in' keyword");
					return true;
				}
				
				// Get the type of variable that is being used to store the value in the loop
				String loopVarAssignmentString = forEachArgs[0].trim();
				
				int firstSpaceIndex = loopVarAssignmentString.indexOf(' ');
				if (firstSpaceIndex == -1) {
					printError("For-loop variable type missing");
					return true;
				}
				
				String forEachVarTypeString = loopVarAssignmentString.substring(0, firstSpaceIndex);
				Type forEachVarType = Type.getTypeFromString(forEachVarTypeString);
				String forEachVarName = loopVarAssignmentString.substring(firstSpaceIndex).trim();
				
				// Try to find an existing variable in this scope with the same name
				Variable existingVar = findVariableByName(enscopeInstr, forEachVarName);
				if (existingVar != null) {
					printError("Variable '" + forEachVarName +
							"' has already been declared in this scope");
					return true;
				}
				forEachVar = new Variable(forEachVarName, forEachVarType);
				
				Instruction forEachVarAssign = new Instruction(InstructionType.DeclareScope);
				forEachVarAssign.stringRepresentation = "declare " + forEachVar.type;
				forEachVarAssign.variableThatWasChanged = forEachVar;
				forEachVarAssign.returnType = Type.Void; // Doesn't return anything
				forEachVarAssign.parentInstruction = enscopeInstr;
				instructions.add(forEachVarAssign);
				
				// Create the indexing variable with a generic name
				loopCounterVar = new Variable("GENERATED_forEachIndex", Type.Int);
				
				// Create the start bound
				Instruction zeroInstr = new Instruction(InstructionType.Given);
				zeroInstr.stringRepresentation = "0";
				zeroInstr.primitiveGivenValue = 0;
				zeroInstr.returnType = Type.Int;
				zeroInstr.parentInstruction = enscopeInstr;
				instructions.add(zeroInstr);
				
				startBound = new Instruction(InstructionType.AllocAndAssign);
				startBound.stringRepresentation = zeroInstr.stringRepresentation;
				startBound.createArgs(1);
				startBound.argReferences[0] = zeroInstr;
				startBound.addTopologicalReference(zeroInstr);
				startBound.variableThatWasChanged = loopCounterVar;
				startBound.returnType = Type.Void; // Doesn't return anything
				startBound.parentInstruction = enscopeInstr;
				instructions.add(startBound);
				
				// Try to find an existing variable in this scope with the same name
				String arrayVarName = forEachArgs[1];
				arrayVar = findVariableByName(enscopeInstr, arrayVarName);
				
				// Make sure the array variable is an array type
				if (!arrayVar.type.isArrayType()) {
					printError("'" + arrayVarName + "' must be an array type");
					return true;
				}
				
				// Make sure the array values may be cast to the reference variable type
				if (!arrayVar.type.toArrayPrimitiveType().canImplicitlyCastTo(forEachVarType)) {
					printError("Cannot implicitly cast from " + arrayVar.type.toArrayPrimitiveType() +
							" to " + forEachVarType);
					return true;
				}
				
				// Read the properties of the array (to get the length later)
				Instruction readProperty = new Instruction(InstructionType.ReadProperty);
				readProperty.stringRepresentation = arrayVar.name;
				readProperty.variableThatWasRead = arrayVar;
				readProperty.returnType = arrayVar.type;
				readProperty.parentInstruction = enscopeInstr;
				
				// TODO add references to instruction that assigned this variable's properties too.
				boolean foundAssignment = addReferenceToInstructionsThatAssigned(readProperty, arrayVar);
				if (!foundAssignment) {
					printError("Variable '" + arrayVarName + "' has not been initialized");
					return true;
				}
				
				instructions.add(readProperty);
				
				// Create the stop bound
				stopBound = new Instruction(InstructionType.Length);
				stopBound.stringRepresentation = "#(" + arrayVar.name + ")";
				stopBound.createArgs(1);
				stopBound.argReferences[0] = readProperty;
				stopBound.addTopologicalReference(readProperty);
				stopBound.returnType = getReturnTypeFromInstructionAndOperands(
						stopBound.instructionType, arrayVar.type, null);
				stopBound.parentInstruction = enscopeInstr;
				instructions.add(stopBound);
				
				// Always increment a for-each loop by 1
				stepValInstr = new Instruction(InstructionType.Given);
				stepValInstr.stringRepresentation = "1";
				stepValInstr.primitiveGivenValue = 1;
				stepValInstr.returnType = Type.Int;
				stepValInstr.parentInstruction = enscopeInstr;
				instructions.add(stepValInstr);
				
			} else if (args.length == 2 || args.length == 3) { // Indexed for-loop
				
				// Get the type of variable that is being used to iterate
				String loopVarAssignmentString = args[0].trim();
				
				int firstSpaceIndex = loopVarAssignmentString.indexOf(' ');
				if (firstSpaceIndex == -1) {
					printError("For-loop variable type missing");
					return true;
				}
				
				String loopVarTypeString = loopVarAssignmentString.substring(0, firstSpaceIndex);
				Type varType = Type.getTypeFromString(loopVarTypeString);
				
				// Make sure the loop variable is an integer type
				if (!varType.isNumberType()) {
					printError("For-loop variable must be a numeric type");
					return true;
				}
				
				int equalSignIndex = loopVarAssignmentString.indexOf('=');
				if (equalSignIndex == -1) {
					printError("Assignment must be made in For-loop variable");
					return true;
				}
				
				String loopVarName = loopVarAssignmentString.substring(firstSpaceIndex, equalSignIndex).trim();
				
				// Try to find an existing variable in this scope with the same name
				Variable existingVar = findVariableByName(enscopeInstr, loopVarName);
				if (existingVar != null) {
					printError("Variable '" + loopVarName +
							"' has already been declared in this scope");
					return true;
				}
				
				String startBoundExpression = loopVarAssignmentString.substring(equalSignIndex+1).trim();
				
				// Get the instructions for the content of this assignment
				boolean hadError = parseExpression(enscopeInstr, startBoundExpression);
				if (hadError) {
					return true;
				}
				
				Instruction lastInstruction = instructions.get(instructions.size()-1);
				
				// Get the stop bound type
				Type givenType = lastInstruction.returnType;
				if (!givenType.canImplicitlyCastTo(varType)) {
					printError("Cannot implicitly cast from " + givenType + " to " + varType);
					return true;
				}
				
				loopCounterVar = new Variable(loopVarName, varType);
				
				startBound = new Instruction(InstructionType.AllocAndAssign);
				startBound.stringRepresentation = startBoundExpression;
				startBound.createArgs(1);
				startBound.argReferences[0] = lastInstruction;
				startBound.addTopologicalReference(lastInstruction);
				startBound.variableThatWasChanged = loopCounterVar;
				startBound.returnType = Type.Void; // Doesn't return anything
				startBound.parentInstruction = enscopeInstr;
				instructions.add(startBound);
				
				String stopBoundExpression = args[1].trim();
				
				// Get the instructions for the content of this assignment
				hadError = parseExpression(enscopeInstr, stopBoundExpression);
				if (hadError) {
					return true;
				}
				
				stopBound = instructions.get(instructions.size()-1);
				
				// Make sure the stop bound is a number of some sort
				if (!stopBound.returnType.isNumberType()) {
					printError("For-loop stop bound must be a numeric type");
					return true;
				}
				
				// If the counting increment is explicitly given
				if (args.length == 3) {
					String incrementExpression = args[2].trim();
					
					// Get the instructions for the content of this assignment
					hadError = parseExpression(enscopeInstr, incrementExpression);
					if (hadError) {
						return true;
					}
					
					stepValInstr = instructions.get(instructions.size()-1);
					
					// Make sure the increment is a number of some sort
					if (!stepValInstr.returnType.isNumberType()) {
						printError("For-loop increment must be a numeric type");
						return true;
					}
				} else {
					// The counting increment is not explicitly given, so assume it is 1
					
					stepValInstr = new Instruction(InstructionType.Given);
					stepValInstr.stringRepresentation = "1";
					stepValInstr.primitiveGivenValue = 1;
					stepValInstr.returnType = Type.Int;
					stepValInstr.parentInstruction = enscopeInstr;
					instructions.add(stepValInstr);
				}
			} else {
				printError("Too many arguments in For-loop header");
				return true;
			}

			// Create the condition for forward or backward counting
			Instruction zeroInstr = new Instruction(InstructionType.Given);
			zeroInstr.stringRepresentation = "0";
			zeroInstr.primitiveGivenValue = 0;
			zeroInstr.returnType = Type.Int;
			zeroInstr.parentInstruction = enscopeInstr;
			instructions.add(zeroInstr);
			
			Instruction signCondition = new Instruction(InstructionType.GreaterEqual);
			signCondition.stringRepresentation = stepValInstr.stringRepresentation + " >= 0";
			signCondition.createArgs(2);
			signCondition.argReferences[0] = stepValInstr;
			signCondition.argReferences[1] = zeroInstr;
			signCondition.addTopologicalReference(stepValInstr);
			signCondition.addTopologicalReference(zeroInstr);
			signCondition.parentInstruction = enscopeInstr;
			signCondition.returnType = getReturnTypeFromInstructionAndOperands(
					signCondition.instructionType, stepValInstr.returnType, zeroInstr.returnType);
			instructions.add(signCondition);
			
			Instruction ifSignInstr = new Instruction(InstructionType.If);
			ifSignInstr.stringRepresentation = signCondition.stringRepresentation;
			ifSignInstr.createArgs(1);
			ifSignInstr.argReferences[0] = signCondition;
			ifSignInstr.addTopologicalReference(signCondition);
			ifSignInstr.returnType = Type.Void; // Doesn't return anything
			ifSignInstr.parentInstruction = enscopeInstr;
			instructions.add(ifSignInstr);
			
			// Generate the contents of the for-loop with the given condition
			final int initialInstructionIndex = currentParsingLineNumber;
			boolean hadError = addForLoopContents(ifSignInstr, stopBound, stepValInstr,
							line, loopCounterVar, forEachVar, arrayVar,
							InstructionType.GreaterEqual);
			if (hadError) {
				return true;
			}
			
			// Create the EndBlock to end of the previous chained instruction
			Instruction ifSignEndInstr = new Instruction(InstructionType.EndBlock);
			ifSignEndInstr.stringRepresentation = "end if";
			ifSignEndInstr.returnType = Type.Void;
			ifSignEndInstr.parentInstruction = ifSignInstr;
			instructions.add(ifSignEndInstr);
			
			// Mark the end of the previous chaining instruction (the sign If)
			ifSignInstr.endInstruction = ifSignEndInstr;
			
			// Create the Else block for the sign switch
			Instruction elseSignInstr = new Instruction(InstructionType.Else);
			elseSignInstr.stringRepresentation = "else";
			elseSignInstr.returnType = Type.Void;
			elseSignInstr.parentInstruction = enscopeInstr;
			instructions.add(elseSignInstr);
			
			// Mark the next instruction in the chain from the previous
			ifSignInstr.nextChainedInstruction = elseSignInstr;
			
			// Generate the contents of the for-loop again with a different stop-condition
			currentParsingLineNumber = initialInstructionIndex;
			boolean hadError2 = addForLoopContents(ifSignInstr, stopBound, stepValInstr,
							line, loopCounterVar, forEachVar, arrayVar,
							InstructionType.Less);
			if (hadError2) {
				return true;
			}
			
			// Create the EndBlock to end this else-statement
			Instruction elseSignEnd = new Instruction(InstructionType.EndBlock);
			elseSignEnd.stringRepresentation = "end else";
			elseSignEnd.returnType = Type.Void;
			elseSignEnd.parentInstruction = elseSignInstr;
			instructions.add(elseSignEnd);
			
			// Mark this as the end instruction for the else-block
			elseSignInstr.endInstruction = elseSignEnd;
			
			// Create the end of the for-loop scope enclosure
			Instruction descopeInstr = new Instruction(InstructionType.EndBlock);
			descopeInstr.stringRepresentation = "descope for";
			descopeInstr.returnType = Type.Void; // Doesn't return anything
			descopeInstr.parentInstruction = enscopeInstr;
			instructions.add(descopeInstr);
			
			enscopeInstr.endInstruction = descopeInstr;
			
		} else if (line.equals("do")) { // Do-while loop (header part)
			
			Instruction instr = new Instruction(InstructionType.Loop);
			instr.stringRepresentation = "do loop start";
			instr.returnType = Type.Void; // Doesn't return anything
			instr.parentInstruction = parentInstruction;
			instructions.add(instr);
			
			// Get the instructions contained by this while-loop
			currentParsingLineNumber++;
			boolean hadError2 = parseLinesAtLevel(instr);
			if (hadError2) {
				return true;
			}
			
			// This do-loop header should have been assigned a DoEnd instruction upon parsing its contents
			if (instr.endInstruction == null) {
				printError("Do-while loop must be closed with a While-condition");
				return true;
			}
			
		} else if (Parser.doesLineStartWith(line, "switch")) { // Switch statement (header part)
			
			// Get the contents of the switch value
			int switchValueStartIndex = 6;
			String expressionContent = line.substring(switchValueStartIndex);
			
			// Detect an empty conditional statement
			if (expressionContent.trim().isEmpty()) {
				printError("Expression missing in Switch header");
				return true;
			}
			
			// Get the instructions for the content of the switching value
			boolean hadError = parseExpression(parentInstruction, expressionContent);
			if (hadError) {
				return true;
			}
			
			Instruction switchValueInstr = instructions.get(instructions.size()-1);
			
			// Make sure the returned value is a discrete type
			if (!switchValueInstr.returnType.isDiscreteType()) {
				printError("Switch value must be a discrete non-boolean type");
				return true;
			}
			
			// We are done parsing the 'switch' header
			currentParsingLineNumber++;
			
			Instruction previousIfInstr = null;
			boolean foundCaseStatement = false;
			boolean foundDefaultStatement = false;
			
			// Parse each "case" and "default" inside the switch statement
			while (true) {
				
				// Find the next non-blank line
				while (lines[currentParsingLineNumber].isEmpty()) {
					if (currentParsingLineNumber >= lines.length) {
						printError("Unclosed Switch statement");
						return true;
					}
					currentParsingLineNumber++;
				}
				
				String newLine = lines[currentParsingLineNumber];
				
				// If this is a case statement
				if (Parser.doesLineStartWith(newLine, "case")) {
					if (foundDefaultStatement) {
						printError("Switch-default may not be followed by Case");
						return true;
					}
					
					// Create the if- or elseif-condition
					
					// Get the contents of the case value
					int caseValueStartIndex = 4;
					String caseContent = newLine.substring(caseValueStartIndex);
					
					// Detect an empty conditional statement
					if (caseContent.trim().isEmpty()) {
						printError("Expression missing in Case header");
						return true;
					}
					
					// Get the instructions for the content of the switching value
					hadError = parseExpression(parentInstruction, caseContent);
					if (hadError) {
						return true;
					}
					
					Instruction lastInstruction = instructions.get(instructions.size()-1);
					
					// Determine if this is an If or an ElseIf (for the first Case or later Cases)
					Instruction ifInstr = null;
					if (foundCaseStatement) {
						ifInstr = new Instruction(InstructionType.ElseIf);
						previousIfInstr.nextChainedInstruction = ifInstr;
					} else {
						ifInstr = new Instruction(InstructionType.If);
					}
					
					Instruction equalInstr = new Instruction(InstructionType.Equal);
					equalInstr.stringRepresentation = switchValueInstr.stringRepresentation +
							" " + equalInstr.instructionType.toSymbolForm() + " " + caseContent;
					equalInstr.createArgs(2);
					equalInstr.argReferences[0] = switchValueInstr;
					equalInstr.addTopologicalReference(switchValueInstr);
					equalInstr.argReferences[1] = lastInstruction;
					equalInstr.addTopologicalReference(lastInstruction);
					equalInstr.returnType = getReturnTypeFromInstructionAndOperands(
							equalInstr.instructionType, switchValueInstr.returnType, lastInstruction.returnType);
					equalInstr.parentInstruction = parentInstruction;
					instructions.add(equalInstr);
					
					ifInstr.stringRepresentation = ifInstr.instructionType.toSymbolForm() +
							" " + equalInstr.stringRepresentation;
					ifInstr.createArgs(1);
					ifInstr.argReferences[0] = equalInstr;
					ifInstr.addTopologicalReference(equalInstr);
					ifInstr.returnType = Type.Void; // Doesn't return anything
					ifInstr.parentInstruction = parentInstruction;
					instructions.add(ifInstr);
					
					// Get the instructions contained by this case statement
					currentParsingLineNumber++;
					hadError = parseLinesAtLevel(ifInstr);
					if (hadError) {
						return true;
					}
					
					// Create the EndBlock for this If-block
					Instruction endIf = new Instruction(InstructionType.EndBlock);
					endIf.stringRepresentation = "end " + ifInstr.instructionType.toSymbolForm();
					endIf.returnType = Type.Void;
					endIf.parentInstruction = ifInstr;
					instructions.add(endIf);
					
					ifInstr.endInstruction = endIf;

					previousIfInstr = ifInstr;
					foundCaseStatement = true;
				} else if (newLine.equals("default")) { // If this is the default case
					if (!foundCaseStatement) {
						printError("Switch-default must be preceded by Case");
						return true;
					}

					// Create the Else-block
					Instruction elseInstr = new Instruction(InstructionType.Else);
					elseInstr.stringRepresentation = "else";
					elseInstr.returnType = Type.Void; // Doesn't return anything
					elseInstr.parentInstruction = parentInstruction;
					instructions.add(elseInstr);
					
					previousIfInstr.nextChainedInstruction = elseInstr;
					
					// Get the instructions contained by this case statement
					currentParsingLineNumber++;
					hadError = parseLinesAtLevel(elseInstr);
					if (hadError) {
						return true;
					}
					
					// Create the EndBlock for this If-block
					Instruction endElse = new Instruction(InstructionType.EndBlock);
					endElse.stringRepresentation = "end else";
					endElse.returnType = Type.Void;
					endElse.parentInstruction = elseInstr;
					instructions.add(endElse);
					
					elseInstr.endInstruction = endElse;
					
					foundDefaultStatement = true;
				} else if (newLine.equals("]")) { // End of the switch chain
					if (!foundCaseStatement) {
						printError("Switch must contain at least one Case");
						return true;
					}
					break;
				} else {
					if (!foundCaseStatement) {
						printError("Switch-header must be immediately followed by Case");
						//printError("Invalid line after switch-header");
						return true;
					}
				}
			}
			
		} else if (Parser.doesLineStartWith(line, "] while")) { // Do-while loop (end part)
			
			// Find the last Loop instruction that hasn't been connected to a EndBlock instruction.
			Instruction doStartInstruction = null;
			int instrIndex = instructions.size()-1;
			while (instrIndex >= 0 && (!instructions.get(instrIndex).isContainerInstruction() ||
						instructions.get(instrIndex).endInstruction != null)) {
				instrIndex--;
			}
			if (instrIndex >= 0 && instructions.get(instrIndex) == parentInstruction &&
					instructions.get(instrIndex).instructionType == InstructionType.Loop &&
					instructions.get(instrIndex).endInstruction == null) {
				doStartInstruction = instructions.get(instrIndex);
			} else {
				printError("Do-While footer not preceded by Do-While header");
				return true;
			}
			
			// Get the contents of the conditional
			int conditionStartIndex = 7;
			String expressionContent = line.substring(conditionStartIndex);
			
			// Detect an empty conditional statement
			if (expressionContent.trim().isEmpty()) {
				printError("Boolean expression missing in Do-While loop");
				return true;
			}
			
			// Get the instructions for the content of this assignment
			boolean hadError = parseExpression(parentInstruction, expressionContent);
			if (hadError) {
				return true;
			}
			
			Instruction lastInstruction = instructions.get(instructions.size()-1);
			
			// Invert the truth of the last instruction in the break condition
			Instruction notInstr = new Instruction(InstructionType.Not);
			notInstr.stringRepresentation = notInstr.instructionType.toSymbolForm() +
					"(" + expressionContent + ")";
			notInstr.createArgs(1);
			notInstr.argReferences[0] = lastInstruction;
			notInstr.addTopologicalReference(lastInstruction);
			notInstr.returnType = Type.Bool;
			notInstr.parentInstruction = doStartInstruction;
			instructions.add(notInstr);
			
			// Build the if-statement for the while loop
			Instruction ifInstr = new Instruction(InstructionType.If);
			ifInstr.stringRepresentation = notInstr.instructionType.toSymbolForm() +
					"(" + expressionContent + ")";
			ifInstr.createArgs(1);
			ifInstr.argReferences[0] = notInstr;
			ifInstr.addTopologicalReference(notInstr);
			ifInstr.returnType = Type.Void; // Doesn't return anything
			ifInstr.parentInstruction = doStartInstruction;
			instructions.add(ifInstr);
			
			// Create a break statement for this while loop
			Instruction breakInstr = new Instruction(InstructionType.Break);
			breakInstr.stringRepresentation = "break";
			breakInstr.parentInstruction = ifInstr;
			breakInstr.returnType = Type.Void;
			breakInstr.createArgs(1);
			breakInstr.argReferences[0] = doStartInstruction;
			breakInstr.addTopologicalReference(doStartInstruction);
			instructions.add(breakInstr);
			
			// Create the EndBlock for this If-block
			Instruction endIf = new Instruction(InstructionType.EndBlock);
			endIf.stringRepresentation = "end if";
			endIf.returnType = Type.Void;
			endIf.parentInstruction = ifInstr;
			instructions.add(endIf);
			
			ifInstr.endInstruction = endIf;
			
			// Make sure the return type of the condition is boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
				return true;
			}
			
			Instruction doEndInstr = new Instruction(InstructionType.EndBlock);
			doEndInstr.stringRepresentation = "end do-while";
			doEndInstr.createArgs(1);
			doEndInstr.argReferences[0] = lastInstruction;
			doEndInstr.addTopologicalReference(lastInstruction);
			doEndInstr.returnType = Type.Void; // Doesn't return anything
			doEndInstr.parentInstruction = doStartInstruction;
			instructions.add(doEndInstr);
			
			// Mark this as the end of the DoStart instruction
			doStartInstruction.endInstruction = doEndInstr;
			
		} else if (Parser.doesLineStartWith(line, "while")) { // While loop
			
			Instruction loopStartLabel = new Instruction(InstructionType.Loop);
			loopStartLabel.stringRepresentation = "while loop start";
			loopStartLabel.returnType = Type.Void; // Doesn't return anything
			loopStartLabel.parentInstruction = parentInstruction;
			instructions.add(loopStartLabel);
			
			// Get the contents of the conditional
			int conditionStartIndex = 5;
			String expressionContent = line.substring(conditionStartIndex);
			
			// Detect an empty conditional statement
			if (expressionContent.trim().isEmpty()) {
				printError("Boolean expression missing in While loop");
				return true;
			}
			
			// Get the instructions for the content of this assignment
			boolean hadError = parseExpression(loopStartLabel, expressionContent);
			if (hadError) {
				return true;
			}
			
			Instruction lastInstruction = instructions.get(instructions.size()-1);
			
			// Make sure the return type of the "While" condition is a boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
				return true;
			}
			
			// Invert the truth of the last instruction in the break condition
			Instruction notInstr = new Instruction(InstructionType.Not);
			notInstr.stringRepresentation = notInstr.instructionType.toSymbolForm() +
					"(" + expressionContent + ")";
			notInstr.createArgs(1);
			notInstr.argReferences[0] = lastInstruction;
			notInstr.addTopologicalReference(lastInstruction);
			notInstr.returnType = Type.Bool;
			notInstr.parentInstruction = loopStartLabel;
			instructions.add(notInstr);
			
			// Build the if-statement for the while loop
			Instruction ifInstr = new Instruction(InstructionType.If);
			ifInstr.stringRepresentation = notInstr.instructionType.toSymbolForm() +
					"(" + expressionContent + ")";
			ifInstr.createArgs(1);
			ifInstr.argReferences[0] = notInstr;
			ifInstr.addTopologicalReference(notInstr);
			ifInstr.returnType = Type.Void; // Doesn't return anything
			ifInstr.parentInstruction = loopStartLabel;
			instructions.add(ifInstr);
			
			// Create a break statement for this while loop
			Instruction breakInstr = new Instruction(InstructionType.Break);
			breakInstr.stringRepresentation = "break";
			breakInstr.parentInstruction = ifInstr;
			breakInstr.returnType = Type.Void;
			breakInstr.createArgs(1);
			breakInstr.argReferences[0] = loopStartLabel;
			breakInstr.addTopologicalReference(loopStartLabel);
			instructions.add(breakInstr);
			
			// Create the EndBlock for this If-block
			Instruction endIf = new Instruction(InstructionType.EndBlock);
			endIf.stringRepresentation = "end if";
			endIf.returnType = Type.Void;
			endIf.parentInstruction = ifInstr;
			instructions.add(endIf);
			
			ifInstr.endInstruction = endIf;
			
			// Get the instructions contained by this while-loop
			currentParsingLineNumber++;
			boolean hadError2 = parseLinesAtLevel(loopStartLabel);
			if (hadError2) {
				return true;
			}
			
			// Create the EndBlock to end this While loop
			Instruction endWhile = new Instruction(InstructionType.EndBlock);
			endWhile.stringRepresentation = "end while";
			endWhile.returnType = Type.Void;
			endWhile.parentInstruction = loopStartLabel;
			instructions.add(endWhile);
			
			// Mark this as the end instruction for this While loop
			loopStartLabel.endInstruction = endWhile;
			
		} else if (Parser.doesLineStartWith(line, "if")  ||
					Parser.doesLineStartWith(line, "elseif")) { // If-statement or ElseIf-statement
			
			// Determine if this is an If-statement, or an ElseIf-statement
			boolean isElseIf = line.startsWith("elseif");
			
			// Find the last If or ElseIf instruction that hasn't been connected to an end instruction.
			Instruction precedingChainInstruction = null; // Only use for ElseIf
			if (isElseIf) {
				int instrIndex = instructions.size()-1;
				while (instrIndex >= 0 && (!instructions.get(instrIndex).isContainerInstruction() ||
							instructions.get(instrIndex).endInstruction != null)) {
					instrIndex--;
				}
				if (instrIndex >= 0 && (instructions.get(instrIndex).instructionType == InstructionType.If ||
						instructions.get(instrIndex).instructionType == InstructionType.ElseIf)) {
					precedingChainInstruction = instructions.get(instrIndex);
				} else {
					printError("ElseIf block not preceded by If or ElseIf block");
					return true;
				}
			}
			
			// More of this is defined below
			Instruction ifInstr = new Instruction(isElseIf ? InstructionType.ElseIf : InstructionType.If);
			
			// Mark the end of the previous chaining instruction
			if (isElseIf) {
				// Create the EndBlock to end the preceding chained instruction
				Instruction previousEndInstr = new Instruction(InstructionType.EndBlock);
				previousEndInstr.stringRepresentation = "end " + precedingChainInstruction.instructionType;
				previousEndInstr.returnType = Type.Void;
				previousEndInstr.parentInstruction = precedingChainInstruction;
				instructions.add(previousEndInstr);
				
				// Mark the end of the previous chaining instruction
				precedingChainInstruction.endInstruction = previousEndInstr;
				
				// Mark this as the next instruction in the chain from the previous
				precedingChainInstruction.nextChainedInstruction = ifInstr;
			}
			
			// Get the contents of the conditional
			int conditionStartIndex = 2;
			if (isElseIf) {
				conditionStartIndex = 6;
			}
			String expressionContent = line.substring(conditionStartIndex);
			
			// Detect an empty conditional statement
			if (expressionContent.trim().isEmpty()) {
				String instructionType = isElseIf ? "ElseIf" : "If";
				printError("Boolean expression missing in " + instructionType);
				return true;
			}
			
			// Get the instructions for the content of this assignment
			boolean hadError = parseExpression(parentInstruction, expressionContent);
			if (hadError) {
				return true;
			}
			
			Instruction lastInstruction = instructions.get(instructions.size()-1);
			
			// Make sure the return type of an "if" condition is a boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
				return true;
			}

			ifInstr.stringRepresentation = expressionContent;
			ifInstr.createArgs(1);
			ifInstr.argReferences[0] = lastInstruction;
			ifInstr.addTopologicalReference(lastInstruction);
			ifInstr.returnType = Type.Void; // Doesn't return anything
			ifInstr.parentInstruction = parentInstruction;
			instructions.add(ifInstr);
			
			// Get the instructions contained by this If-statement
			currentParsingLineNumber++;
			boolean hadError2 = parseLinesAtLevel(ifInstr);
			if (hadError2) {
				return true;
			}
			
			// If, after parsing, this If-statement still does not have an end-block assigned,
			//    then create one and assign it.
			if (ifInstr.endInstruction == null) {
				// Create the EndBlock to end this else-statement
				Instruction endInstr = new Instruction(InstructionType.EndBlock);
				endInstr.stringRepresentation = "end " + ifInstr.instructionType.toSymbolForm();
				endInstr.returnType = Type.Void;
				endInstr.parentInstruction = ifInstr;
				instructions.add(endInstr);
				
				// Mark this as the end instruction for this else-block
				ifInstr.endInstruction = endInstr;
			}
			
		} else if (line.equals("else")) { // This is an else chained onto an if
			
			// Find the last If or ElseIf instruction that hasn't been connected to an end instruction
			//    that also has the same parent as this instruction.
			int instrIndex = instructions.size()-1;
			while (instrIndex >= 0 && (!instructions.get(instrIndex).isContainerInstruction() ||
						instructions.get(instrIndex).endInstruction != null)) {
				instrIndex--;
			}
			
			Instruction precedingChainInstruction = null;
			if (instrIndex >= 0 && instructions.get(instrIndex).parentInstruction == parentInstruction &&
					(instructions.get(instrIndex).instructionType == InstructionType.If ||
					instructions.get(instrIndex).instructionType == InstructionType.ElseIf)) {
				precedingChainInstruction = instructions.get(instrIndex);
			} else {
				printError("Else block not preceded by If or ElseIf block");
				return true;
			}
			
			// Create the EndBlock to end of the previous chained instruction
			Instruction previousEndInstr = new Instruction(InstructionType.EndBlock);
			previousEndInstr.stringRepresentation = "end " + precedingChainInstruction.instructionType;
			previousEndInstr.returnType = Type.Void;
			previousEndInstr.parentInstruction = precedingChainInstruction;
			instructions.add(previousEndInstr);
			
			// Mark the end of the previous chaining instruction
			precedingChainInstruction.endInstruction = previousEndInstr;
			
			// Create the Else block
			Instruction elseInstr = new Instruction(InstructionType.Else);
			elseInstr.stringRepresentation = "else";
			elseInstr.returnType = Type.Void;
			elseInstr.parentInstruction = parentInstruction;
			instructions.add(elseInstr);
			
			// Mark the next instruction in the chain from the previous
			precedingChainInstruction.nextChainedInstruction = elseInstr;
			
			// Get the instructions contained by this Else-statement
			currentParsingLineNumber++;
			boolean hadError2 = parseLinesAtLevel(elseInstr);
			if (hadError2) {
				return true;
			}
			
			// Create the EndBlock to end this else-statement
			Instruction endInstr = new Instruction(InstructionType.EndBlock);
			endInstr.stringRepresentation = "end else";
			endInstr.returnType = Type.Void;
			endInstr.parentInstruction = elseInstr;
			instructions.add(endInstr);
			
			// Mark this as the end instruction for this else-block
			elseInstr.endInstruction = endInstr;
			
		} else if (Parser.getFirstDataType(line) != null) { // If this is a declaration of some sort
			Object[] typeData = Parser.getFirstDataType(line);
			Type varType = (Type)typeData[0];
			int typeEndIndex = (int)typeData[1];
			
			// Make sure there is a space after the variable type
			if (line.charAt(typeEndIndex) != ' ') {
				printError("Declaration name missing");
				return true;
			}
			
			// Get the first variable name on this line at this index, and its end index
			Object[] varData = Parser.getVariableName(line, typeEndIndex+1);
			if (varData == null) {
				printError("Declaration name missing");
				return true;
			}
			String varName = (String)varData[0];
			int varEndIndex = (int)varData[1];
			
			// Determine whether this is a variable, or method declaration
			boolean isMethodDeclaration = false;
			if (varType == Type.Void || (varEndIndex < line.length() && line.charAt(varEndIndex) == '(')) {
				isMethodDeclaration = true;
			}
			
			if (isMethodDeclaration) {
				
				// Cannot define a method inside anything except another method or main program
				if (parentInstruction != null &&
						parentInstruction.instructionType != InstructionType.RoutineDefinition) {
					printError("Methods may only be defined inside other methods or main program");
					return true;
				}
				
				// Parse the types of the parameters to this method
				String paramString = Parser.getFunctionArguments(line, typeEndIndex);
				String[] params = Parser.separateArguments(paramString);
				if (params == null) {
					return true;
				}
				Type[] paramTypes = new Type[params.length];
				String[] paramNames = new String[params.length];
				
				for (int i = 0; i < params.length; i++) {
					Object[] data = Parser.getFirstDataType(params[i]);
					if (data != null) {
						paramTypes[i] = (Type)data[0];
						int index = (int)data[1];
						paramNames[i] = params[i].substring(index).trim();
					} else {
						printError("Invalid parameter declaration or type in '" + params[i] + "'");
						return true;
					}
				}
				
				boolean foundDuplicates = Parser.checkForDuplicates(paramNames);
				if (foundDuplicates) {
					printError("Duplicate parameter name in method header");
					return true;
				}
				
				// Create the routine signature
				Routine routine = new Routine(varName, new Type[]{varType}, paramTypes);
				
				// Try to find an existing variable in this scope that is in naming conflict with this one
				boolean foundConflict = findConflictingRoutine(parentInstruction, routine);
				if (foundConflict) {
					printError("Method '" + varName + "' has already been declared in this scope");
					return true;
				}
				
				String argTypesString = "";
				for (int j = 0; j < paramTypes.length; j++) {
					argTypesString += paramTypes[j];
					if (j != paramTypes.length-1) {
						argTypesString += ", ";
					}
				}
				
				// Create the routine definition
				Instruction routineInstr = new Instruction(InstructionType.RoutineDefinition);
				routineInstr.stringRepresentation = varName + "(" + argTypesString + ")";
				routineInstr.routineThatWasDefined = routine;
				routineInstr.routineName = varName;
				routineInstr.parentInstruction = parentInstruction;
				routineInstr.returnType = varType; // TODO add multiple returns
				instructions.add(routineInstr);
				
				routineInstr.createArgs(params.length);
				for (int i = 0; i < params.length; i++) {
					
					// Create the parameter variable
					Variable var = new Variable(paramNames[i], paramTypes[i]);
					
					Instruction instr = new Instruction(InstructionType.DeclareScope);
					instr.stringRepresentation = var.type + " " + var.name;
					instr.returnType = Type.Void; // Doesn't return anything
					instr.variableThatWasChanged = var;
					instr.parentInstruction = routineInstr;
					instructions.add(instr);
					
					routineInstr.argReferences[i] = instr;
				}
				
				// Parse the contents of this routine
				currentParsingLineNumber++;
				boolean hadError = parseLinesAtLevel(routineInstr);
				if (hadError) {
					return true;
				}
				
				// Check the contents of this routine for code between sub-routines
				boolean foundCodeAfterRoutine = searchForCodeAfterRoutine(routineInstr);
				if (foundCodeAfterRoutine) {
					printError("Method may not be followed by sequential code");
					return true;
				}
				
				// Create the end instruction for this routine
				Instruction endInstr = new Instruction(InstructionType.EndBlock);
				endInstr.stringRepresentation = "end " + varName;
				endInstr.returnType = Type.Void;
				endInstr.parentInstruction = routineInstr;
				instructions.add(endInstr);
				
				// Mark this as the end instruction for this else-block
				routineInstr.endInstruction = endInstr;
			} else {
				
				// Try to find an existing variable in this scope that is in naming conflict with this one
				Variable existingVar = findVariableByName(parentInstruction, varName);
				if (existingVar != null) {
					printError("Variable '" + varName + "' has already been declared in this scope");
					return true;
				}
				
				// Create the variable
				Variable var = new Variable(varName, varType);
				
				Object[] assignmentData = Parser.getFirstAssignmentOperator(line, varEndIndex);
				String operator = null;
				int operatorEndIndex;
				String expressionContent = null;
				
				// There may not be any assignment on this line (only declaration)
				if (assignmentData != null) {
					operator = (String)assignmentData[0];
					operatorEndIndex = (int)assignmentData[1];
					expressionContent = line.substring(operatorEndIndex).trim();
				}
				
				// If this is an scope declaration only (no value assigned, and no allocation)
				if (assignmentData == null) {
					// Make sure there aren't excess characters at the end of this line
					if (Parser.checkForExcessCharacters(line, varName)) {
						return true;
					}
					
					Instruction instr = new Instruction(InstructionType.DeclareScope);
					instr.stringRepresentation = varType + " " + varName;
					instr.returnType = Type.Void; // Doesn't return anything
					instr.variableThatWasChanged = var;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
					
				} else if (operator.equals("=")) { // If this is an allocation and assignment
					// Get the instructions for the content of this assignment
					boolean hadError = parseExpression(parentInstruction, expressionContent);
					if (hadError) {
						return true;
					}
					
					Instruction lastInstruction = instructions.get(instructions.size()-1);
					
					// Get the operand type
					Type operandType = lastInstruction.returnType;
					if (!operandType.canImplicitlyCastTo(varType)) {
						printError("Cannot implicitly cast from " + operandType + " to " + varType);
						return true;
					}
					
					// If this is an array, then make sure the dimension matches
					if (varType.isArrayType() && varType.dimensions != lastInstruction.argReferences.length) {
						printError("Unmatching array dimensions");
						return true;
					}
					
					Instruction instr = new Instruction(InstructionType.AllocAndAssign);
					instr.stringRepresentation = expressionContent;
					instr.createArgs(1);
					instr.argReferences[0] = lastInstruction;
					instr.addTopologicalReference(lastInstruction);
					instr.returnType = Type.Void; // Doesn't return anything
					instr.variableThatWasChanged = var;
					instr.parentInstruction = parentInstruction;
					
					instructions.add(instr);
				} else {
					printError("Invalid assignment operator");
					return true;
				}
			}
		} else if (Parser.getVariableName(line, 0) != null &&
					Parser.hasAssignmentOperator(line)) { // If this is a reassignment of a variable
			
			Object[] varData = Parser.getVariableName(line, 0);
			if (varData == null) {
				return true;
			}
			
			String varName = (String)varData[0];
			int varEndIndex = (int)varData[1]; // May be reassigned if this is an array indexer
			
			Variable var = findVariableByName(parentInstruction, varName);
			if (var == null) {
				printError("'" + varName + "' has not been declared");
				return true;
			}
			
			Type varType = var.type;
			
			// Only used for array types
			Instruction[] arrayIndices = null;
			String[] arrayIndexArgs = null;
			
			// Try to find any array indexer after the variable name
			Object[] arrayIndexData = Parser.getArrayIndexInfo(line, varEndIndex);
			if (arrayIndexData != null) {
				// Make sure the variable is an array type
				if (!varType.isArrayType()) {
					printError("Cannot index type " + varType);
					return true;
				}
				
				arrayIndexArgs = (String[])arrayIndexData[0];
				varEndIndex = (int)arrayIndexData[1] + 1;
				
				// Make sure the dimensions of the variable match the arguments
				if (varType.dimensions != arrayIndexArgs.length) {
					printError("'" + varName + "' requires "+ varType.dimensions +
							" dimensions, got " + arrayIndexArgs.length);
					return true;
				}
				
				arrayIndices = new Instruction[arrayIndexArgs.length];
				
				// Parse each of the arguments to the array index
				for (int i = 0; i < arrayIndexArgs.length; i++) {
					// Recursively parse the expressions
					boolean hadError = parseExpression(parentInstruction, arrayIndexArgs[i]);
					if (hadError) {
						return true;
					}
					
					Instruction lastInstruction = instructions.get(instructions.size()-1);
					
					if (lastInstruction.returnType != Type.Int) {
						printError("Array index must be of type " + Type.Int);
						return true;
					}
					
					arrayIndices[i] = lastInstruction;
				}
				
				// Switch the variable type to the array's primitive content
				varType = varType.toArrayPrimitiveType();
			}
			
			// Get the operator on this line
			Object[] assignmentData = Parser.getFirstAssignmentOperator(line, varEndIndex);
			if (assignmentData == null) {
				printError("Missing assignment");
				return true;
			}
			String operator = (String)assignmentData[0];
			int operatorEndIndex = (int)assignmentData[1];
			
			String expressionContent = line.substring(operatorEndIndex).trim();
			
			// If this is an assignment (by value)
			if (operator.equals("=")) {
				// Get the instructions for the content of this assignment
				boolean hadError = parseExpression(parentInstruction, expressionContent);
				if (hadError) {
					return true;
				}

				// Get the operand type
				Instruction lastInstruction = instructions.get(instructions.size()-1);
				Type operandType = lastInstruction.returnType;
				
				// Check if this can be explicitly cast
				if (!operandType.canImplicitlyCastTo(varType)) {
					printError("Cannot implicitly cast from " + operandType + " to " + varType);
					return true;
				}
				
				Instruction instr = new Instruction(InstructionType.Reassign);
				if (arrayIndices != null) { // If this is an array type, then add references to the indices
					instr.createArgs(arrayIndices.length);
					for (int i = 0; i < arrayIndices.length; i++) {
						instr.argReferences[i] = arrayIndices[i];
						instr.addTopologicalReference(arrayIndices[i]);
					}
				}
				instr.stringRepresentation = expressionContent;
				instr.returnType = Type.Void; // Doesn't return anything
				instr.variableThatWasChanged = var;
				instr.parentInstruction = parentInstruction;
				instr.addTopologicalReference(lastInstruction);
				
				// Add references to all previous instructions possibly that read or declared this variable
				boolean foundLastInstruction = addReferenceToInstructionsThatDeclaredOrRead(instr, var);
				if (!foundLastInstruction) {
					printError("Variable " + varName + " was never declared");
					return true;
				}
				
				instructions.add(instr);
			} else if (operator.equals("++") || operator.equals("--")) {
				if (!varType.isNumberType()) {
					printError(operator + " cannot be performed on " + varType);
					return true;
				}
				
				if (!expressionContent.isEmpty()) {
					printError("Unexpected symbols at end of line '" + line + "'");
					return true;
				}
				
				Instruction oneInstr = new Instruction(InstructionType.Given);
				oneInstr.stringRepresentation = "1";
				oneInstr.primitiveGivenValue = 1;
				oneInstr.returnType = Type.Int;
				oneInstr.parentInstruction = parentInstruction;
				instructions.add(oneInstr);
				
				InstructionType instructionType = InstructionType.Add;
				if (operator.equals("--")) {
					instructionType = InstructionType.Sub;
				}
				
				boolean hadError = generateBinaryReassignment(var, instructionType,
						oneInstr, parentInstruction, null);
				if (hadError) {
					return true;
				}
				
			} else if (operator.equals("+=") || operator.equals("-=") || // If this is a shorthand operator
					operator.equals("/=") || operator.equals("*=") ||
					operator.equals("^=") || operator.equals("%=") ||
					operator.equals("OR=") || operator.equals("AND=")) {
				
				// Get the instructions for the content of this assignment
				boolean hadError = parseExpression(parentInstruction, expressionContent);
				if (hadError) {
					return true;
				}
				
				// Get the last instruction from the increment to be changed
				Instruction lastInstruction = instructions.get(instructions.size()-1);
				InstructionType shorthandInstructionType = getInstructionTypeFromOperator(operator);
				
				hadError = generateBinaryReassignment(var, shorthandInstructionType,
									lastInstruction, parentInstruction, null);
				if (hadError){
					return true;
				}
			} else {
				printError("Invalid assignment operator");
				return true;
			}
		} else if (Parser.isMethodCall(line)) { // If this is a method call alone on a line
			
			String[] data = Parser.getMethodNameAndArgs(line);
			String methodName = null;
			String methodArgString = null;
			if (data != null) {
				methodName = data[0];
				methodArgString = data[1];
			}
			
			// Make sure there aren't excess characters at the end of this line
			if (Parser.checkForExcessCharacters(line, methodArgString)) {
				return true;
			}
			
			String[] args = Parser.separateArguments(methodArgString);
			if (args == null) {
				return true;
			}
			
			// If this is the built-in print function
			if (methodName.equals("print")) {
				
				if (args.length != 1) {
					printError("print(args) expects one argument; Got " + args.length + " arguments.");
				}
				
				boolean hadError = parseExpression(parentInstruction, args[0]);
				if (hadError) {
					return true;
				}
				
				Instruction lastInstruction = instructions.get(instructions.size()-1);
				
				Instruction instr = new Instruction(InstructionType.Print);
				instr.stringRepresentation = args[0];
				instr.createArgs(1);
				instr.argReferences[0] = lastInstruction;
				instr.returnType = Type.Void;
				instr.parentInstruction = parentInstruction;
				instr.addTopologicalReference(lastInstruction);
				if (instr.returnType == null) {
					return true;
				}
				
				instructions.add(instr);
			} else { // This must be a user-defined method
				Instruction instr = new Instruction(InstructionType.Call);
				instr.stringRepresentation = line;
				instr.routineName = methodName;
				instr.createArgs(args.length);
				
				// Parse each of the arguments to this method
				for (int i = 0; i < args.length; i++) {
					// Recursively parse the expressions
					boolean hadError = parseExpression(parentInstruction, args[i]);
					if (hadError) {
						return true;
					}
					
					Instruction lastInstruction = instructions.get(instructions.size()-1);
					instr.argReferences[i] = lastInstruction;
					instr.addTopologicalReference(lastInstruction);
				}
				
				instr.returnType = null; // This will be determined after the whole program is compiled
				instr.parentInstruction = parentInstruction;
				instructions.add(instr);
			}
		} else {
			printError("Invalid line: " + line);
			return true;
		}
		
		return false;
	}
	
	// Recursively parse an expression (no assignment allowed)
	static boolean parseExpression(Instruction parentInstruction, String text) {
		text = Parser.removeUnnecessaryParentheses(text.trim());
		
		// TODO test method scope rules
		// TODO parse methods inside expressions
		// TODO add a read keyboard function
		
		if (text.isEmpty()) {
			printError("Empty expression encountered (value expected)");
			return true;
		}
		
		// If this contains a binary operator (on this level)
		Object[] operatorData = Parser.findLowestPrecedenceBinaryOperatorAtLowestLevel(text);
		if (operatorData != null) {
			String op = (String)operatorData[0];
			int start = (int)operatorData[1];
			int end = (int)operatorData[2];
			
			String firstHalf = text.substring(0, start);
			String lastHalf = text.substring(end);
			
			// Recursively parse the expressions
			boolean hadError1 = parseExpression(parentInstruction, firstHalf);
			if (hadError1) {
				return true;
			}
			Instruction lastInstruction1 = instructions.get(instructions.size()-1);
			
			boolean hadError2 = parseExpression(parentInstruction, lastHalf);
			if (hadError2) {
				return true;
			}
			Instruction lastInstruction2 = instructions.get(instructions.size()-1);
			
			// Get the types of each operand to this operator
			Type operandType1 = lastInstruction1.returnType;
			Type operandType2 = lastInstruction2.returnType;
			
			// Create the instruction for this binary operator
			InstructionType type = getInstructionTypeFromOperator(op);
			if (type == null) {
				return true;
			}
			
			// Handle overloaded binary operators depending on operand type
			if (type == InstructionType.Add) {
				// This must be a string concatenation
				if (operandType1 == Type.String || operandType2 == Type.String) {
					type = InstructionType.Concat;
				}
			}
			if (operandType1 == Type.Int || operandType1 == Type.Long) {
				if (type == InstructionType.And) {
					type = InstructionType.BitAnd;
				} else if (type == InstructionType.Or) {
					type = InstructionType.BitOr;
				} else if (type == InstructionType.Not) {
					type = InstructionType.BitNot;
				}
			}
			
			Type returnType = getReturnTypeFromInstructionAndOperands(type, operandType1, operandType2);
			if (returnType == null) {
				return true;
			}

			Instruction instr = new Instruction(type);
			instr.stringRepresentation = firstHalf + ", " + lastHalf;
			instr.createArgs(2);
			instr.argReferences[0] = lastInstruction1;
			instr.argReferences[1] = lastInstruction2;
			instr.returnType = returnType;
			instr.parentInstruction = parentInstruction;
			instr.addTopologicalReference(lastInstruction1);
			instr.addTopologicalReference(lastInstruction2);
			
			// Add all of the new instruction
			instructions.add(instr);
		} else { // There are no more binary operators in this expression
			
			// If it's an invert, then record that instruction
			if (text.charAt(0) == '!') {
				String content = Parser.getUnaryFunctionArgument(text, 0);

				// Recursively parse the expressions
				boolean hadError = parseExpression(parentInstruction, content);
				if (hadError) {
					return true;
				}
				
				Instruction lastInstruction = instructions.get(instructions.size()-1);
				
				Instruction instr = new Instruction(InstructionType.Not);
				instr.stringRepresentation = content;
				instr.createArgs(1);
				instr.argReferences[0] = lastInstruction;
				instr.returnType = getReturnTypeFromInstructionAndOperands(instr.instructionType, Type.Bool, null);
				if (instr.returnType == null) {
					return true;
				}
				instr.parentInstruction = parentInstruction;
				instr.addTopologicalReference(lastInstruction);
				
				// Add the new instruction
				instructions.add(instr);
			} else if (text.charAt(0) == '#') {
				
				String expressionContent = Parser.getUnaryFunctionArgument(text, 0);

				// Recursively parse the expressions
				boolean hadError = parseExpression(parentInstruction, expressionContent);
				if (hadError) {
					return true;
				}
				
				Instruction lastInstruction = instructions.get(instructions.size()-1);
				Type operandType = lastInstruction.returnType;
				
				// Make sure the argument is an array type
				if (!operandType.isArrayType()) {
					printError("Argument to # operator must be an array type");
					return true;
				}
				
				Instruction instr = new Instruction(InstructionType.Length);
				instr.stringRepresentation = "#" + expressionContent;
				instr.createArgs(1);
				instr.argReferences[0] = lastInstruction;
				instr.addTopologicalReference(lastInstruction);
				instr.returnType = getReturnTypeFromInstructionAndOperands(
						instr.instructionType, operandType, null);
				instr.parentInstruction = parentInstruction;
				instructions.add(instr);
				
			} else {
				
				// If this is a signed integer
				if (Parser.isSignedInteger(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = Parser.parseInt(text);
					instr.returnType = Type.Int;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (Parser.isSignedLong(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = Parser.parseLong(text);
					instr.returnType = Type.Long;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (Parser.isDouble(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = Parser.parseDouble(text);
					instr.returnType = Type.Double;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (Parser.isFloat(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = Parser.parseFloat(text);
					instr.returnType = Type.Float;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (Parser.isBool(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = Parser.parseBool(text);
					instr.returnType = Type.Bool;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (Parser.isString(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = text;
					instr.returnType = Type.String;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (Parser.isArrayDefinition(text)) {
					Object[] arrayData = Parser.getArrayDefinitionInfo(text);
					if (arrayData == null) {
						printError("Malformed array definition in '" + text + "'");
						return true;
					}
					Type arrayType = (Type)arrayData[0];
					String[] dimensions = (String[])arrayData[1];
					
					Instruction instr = new Instruction(InstructionType.Alloc);
					instr.stringRepresentation = text;
					instr.createArgs(dimensions.length);
					
					// Parse each of the arguments to the array dimension
					for (int i = 0; i < dimensions.length; i++) {
						// Recursively parse the expressions
						boolean hadError = parseExpression(parentInstruction, dimensions[i]);
						if (hadError) {
							return true;
						}
						
						Instruction lastInstruction = instructions.get(instructions.size()-1);
						
						if (lastInstruction.returnType != Type.Int) {
							printError("Array dimension must be of type " + Type.Int);
							return true;
						}
						
						instr.argReferences[i] = lastInstruction;
						instr.addTopologicalReference(lastInstruction);
					}
					
					instr.returnType = arrayType;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
					
				} else if (Parser.isArrayReference(text)) {
					Object[] arrayData = Parser.getArrayReadInfo(text);
					if (arrayData == null) {
						printError("Malformed array in '" + text + "'");
						return true;
					}
					String arrName = (String)arrayData[0];
					String[] dimensions = (String[])arrayData[1];
					
					Variable var = findVariableByName(parentInstruction, arrName);
					if (var == null) {
						printError("Unknown array '" + arrName + "'");
						return true;
					}
					
					Instruction instr = new Instruction(InstructionType.Read);
					instr.stringRepresentation = text;
					instr.variableThatWasRead = var;
					instr.returnType = var.type.toArrayPrimitiveType();
					instr.parentInstruction = parentInstruction;
					
					// Find the all previous instructions that assigned this variable, and reference them
					boolean foundLastWriteInstruction = addReferenceToInstructionsThatAssigned(instr, var);
					if (!foundLastWriteInstruction) {
						printError("Array '" + var + "' was never initialized");
						return true;
					}

					instr.createArgs(dimensions.length);
					
					// Parse each of the arguments to the array index
					for (int i = 0; i < dimensions.length; i++) {
						// Recursively parse the expressions
						boolean hadError = parseExpression(parentInstruction, dimensions[i]);
						if (hadError) {
							return true;
						}
						
						Instruction lastInstruction = instructions.get(instructions.size()-1);
						
						if (lastInstruction.returnType != Type.Int) {
							printError("Array dimension must be of type " + Type.Int);
							return true;
						}
						
						instr.argReferences[i] = lastInstruction;
						instr.addTopologicalReference(lastInstruction);
					}
					
					instructions.add(instr);
					
				} else {
					// Check if this is a recognized variable
					Variable var = findVariableByName(parentInstruction, text);
					if (var == null) {
						printError("Unknown expression '" + text + "'");
						return true;
					}
					
					// If this variable is a primitive type
					if (var.type.isPrimitiveType()) {
						
						Instruction instr = new Instruction(InstructionType.Read);
						instr.stringRepresentation = text;
						instr.variableThatWasRead = var;
						instr.returnType = var.type;
						instr.parentInstruction = parentInstruction;
						
						// Find the all previous instructions that assigned this variable, and reference them
						boolean foundLastWriteInstruction = addReferenceToInstructionsThatAssigned(instr, var);
						if (!foundLastWriteInstruction) {
							printError("Variable '" + var + "' was never initialized");
							return true;
						}
						
						instructions.add(instr);
					} else {
						// Reading a composite type directly doesn't make sense.
						// So instead, we are reading a property of the composite type.
						
						Instruction instr = new Instruction(InstructionType.ReadProperty);
						instr.stringRepresentation = text;
						instr.variableThatWasRead = var;
						instr.returnType = var.type;
						instr.parentInstruction = parentInstruction;
						
						// TODO add references to instruction that assigned this variable's properties too.
						// Find the all previous instructions that assigned this variable, and reference them
						boolean foundLastWriteInstruction = addReferenceToInstructionsThatAssigned(instr, var);
						if (!foundLastWriteInstruction) {
							printError("Variable '" + var + "' was never initialized");
							return true;
						}
						
						instructions.add(instr);
					}
				}
			}
		}
		
		return false;
	}
	
	// Generate the instructions to perform a binary operator on a single variable
	// Instructions generated:
	//    1. Read variable
	//    2. Binary operator on (1) and given lastInstruction
	//    3. Reassign variable to (2)
	static boolean generateBinaryReassignment(Variable var,
				InstructionType instructionType, Instruction lastInstruction,
				Instruction parentInstruction, Instruction readInstr) {
		
		// Determine any overloaded operators
		if (var.type == Type.String) {
			if (instructionType == InstructionType.Add) {
				instructionType = InstructionType.Concat;
			}
		} else if (var.type == Type.Int || var.type == Type.Long) {
			if (instructionType == InstructionType.And) {
				instructionType = InstructionType.BitAnd;
			} else if (instructionType == InstructionType.Or) {
				instructionType = InstructionType.BitOr;
			} else if (instructionType == InstructionType.Not) {
				instructionType = InstructionType.BitNot;
			}
		}
		
		Type operandType = lastInstruction.returnType;
		
		Type returnType = getReturnTypeFromInstructionAndOperands(
				instructionType, var.type, operandType);
		
		if (returnType == null) {
			return true;
		}
		
		// Check if the return type can be explicitly cast to the variable being assigned to
		if (!returnType.canImplicitlyCastTo(var.type)) {
			printError("Cannot implicitly cast " + returnType + " to " + var.type);
			return true;
		}
		
		// Create the read operation (if not given)
		if (readInstr == null) {
			readInstr = new Instruction(InstructionType.Read);
			readInstr.stringRepresentation = var.name;
			readInstr.variableThatWasRead = var;
			readInstr.returnType = var.type;
			readInstr.parentInstruction = parentInstruction;
			instructions.add(readInstr);
			
			// Find the all previous instructions that possibly assigned this variable, and reference them
			boolean foundLastWriteInstruction = addReferenceToInstructionsThatAssigned(readInstr, var);
			if (!foundLastWriteInstruction) {
				printError("Variable " + var + " was never initialized");
				return true;
			}
		}
		
		// Create the instruction that computes the new value to assign
		Instruction mathInstr = new Instruction(instructionType);
		mathInstr.stringRepresentation = var.name + " " + instructionType.toSymbolForm()
				+ " (" + lastInstruction.stringRepresentation + ")";
		mathInstr.createArgs(2);
		mathInstr.argReferences[0] = readInstr;
		mathInstr.argReferences[1] = lastInstruction;
		mathInstr.addTopologicalReference(readInstr);
		mathInstr.addTopologicalReference(lastInstruction);
		mathInstr.returnType = returnType;
		mathInstr.parentInstruction = parentInstruction;
		instructions.add(mathInstr);

		// Create the instruction that reassigns the value
		Instruction reassignInstr = new Instruction(InstructionType.Reassign);
		reassignInstr.createArgs(1);
		reassignInstr.stringRepresentation = var.name + " = "
				+ mathInstr.stringRepresentation;
		reassignInstr.returnType = Type.Void; // Doesn't return anything
		reassignInstr.variableThatWasChanged = var;
		reassignInstr.parentInstruction = parentInstruction;
		reassignInstr.argReferences[0] = mathInstr;
		reassignInstr.addTopologicalReference(mathInstr);
		instructions.add(reassignInstr);
		
		// No errors
		return false;
	}
	
	// Generate the instructions for a generic for-loop.
	// Return true if there was an error.
	static boolean addForLoopContents(Instruction parentInstruction, Instruction stopBound,
				Instruction stepValInstr, String line, Variable loopCounterVar,
				Variable forEachVar, Variable arrayVar, InstructionType stopCondition) {
		
		// This instruction precedes all content of the loop that is repeated
		Instruction loopStartLabel = new Instruction(InstructionType.Loop);
		loopStartLabel.stringRepresentation = "for loop start";
		loopStartLabel.returnType = Type.Void; // Doesn't return anything
		loopStartLabel.parentInstruction = parentInstruction;
		instructions.add(loopStartLabel);
		
		// Create the upper bound break condition
		{
			Instruction readLoopCount = new Instruction(InstructionType.Read);
			readLoopCount.stringRepresentation = loopCounterVar.name;
			readLoopCount.variableThatWasRead = loopCounterVar;
			readLoopCount.returnType = loopCounterVar.type;
			readLoopCount.parentInstruction = loopStartLabel;
			instructions.add(readLoopCount);
			
			Instruction breakBoundCondition = new Instruction(stopCondition);
			breakBoundCondition.stringRepresentation = readLoopCount.stringRepresentation +
					" " + stopCondition.toSymbolForm() + " " + stopBound.stringRepresentation;
			breakBoundCondition.createArgs(2);
			breakBoundCondition.argReferences[0] = readLoopCount;
			breakBoundCondition.addTopologicalReference(readLoopCount);
			breakBoundCondition.argReferences[1] = stopBound;
			breakBoundCondition.addTopologicalReference(stopBound);
			breakBoundCondition.returnType = getReturnTypeFromInstructionAndOperands(
					stopCondition, readLoopCount.returnType, stopBound.returnType);
			breakBoundCondition.parentInstruction = loopStartLabel;
			instructions.add(breakBoundCondition);
			
			Instruction ifBoundBreakCondition = new Instruction(InstructionType.If);
			ifBoundBreakCondition.stringRepresentation = breakBoundCondition.stringRepresentation;
			ifBoundBreakCondition.createArgs(1);
			ifBoundBreakCondition.argReferences[0] = breakBoundCondition;
			ifBoundBreakCondition.addTopologicalReference(breakBoundCondition);
			ifBoundBreakCondition.returnType = Type.Void; // Doesn't return anything
			ifBoundBreakCondition.parentInstruction = loopStartLabel;
			instructions.add(ifBoundBreakCondition);
			
			Instruction boundBreak = new Instruction(InstructionType.Break);
			boundBreak.stringRepresentation = "break for";
			boundBreak.returnType = Type.Void; // Doesn't return anything
			boundBreak.createArgs(1);
			boundBreak.argReferences[0] = loopStartLabel;
			boundBreak.addTopologicalReference(loopStartLabel);
			boundBreak.parentInstruction = ifBoundBreakCondition;
			instructions.add(boundBreak);
			
			Instruction ifBoundEnd = new Instruction(InstructionType.EndBlock);
			ifBoundEnd.stringRepresentation = "end if";
			ifBoundEnd.returnType = Type.Void;
			ifBoundEnd.parentInstruction = ifBoundBreakCondition;
			instructions.add(ifBoundEnd);
			
			ifBoundBreakCondition.endInstruction = ifBoundEnd;
		}
		
		// Create the loop variable read operation (it may be used many times)
		Instruction countReadInstr = new Instruction(InstructionType.Read);
		countReadInstr.stringRepresentation = loopCounterVar.name;
		countReadInstr.variableThatWasRead = loopCounterVar;
		countReadInstr.returnType = loopCounterVar.type;
		countReadInstr.parentInstruction = loopStartLabel;
		instructions.add(countReadInstr);
		
		// Reassign the for-each loop reference variable
		if (forEachVar != null) {
			Instruction arrayReadInstr = new Instruction(InstructionType.Read);
			arrayReadInstr.stringRepresentation = arrayVar.name + "[" + loopCounterVar.name + "]";
			arrayReadInstr.variableThatWasRead = arrayVar;
			arrayReadInstr.createArgs(1);
			arrayReadInstr.argReferences[0] = countReadInstr;
			arrayReadInstr.addTopologicalReference(countReadInstr);
			arrayReadInstr.returnType = arrayVar.type.toArrayPrimitiveType();
			arrayReadInstr.parentInstruction = loopStartLabel;
			
			// Find the all previous instructions that possibly assigned this variable, and reference them
			boolean foundLastWriteInstruction = addReferenceToInstructionsThatAssigned(arrayReadInstr, arrayVar);
			if (!foundLastWriteInstruction) {
				printError("Array '" + arrayVar + "' was never initialized");
				return true;
			}
			
			instructions.add(arrayReadInstr);
			
			// Reassign the variable
			Instruction reassignForEachVar = new Instruction(InstructionType.Reassign);
			reassignForEachVar.stringRepresentation = forEachVar.name + " = " + arrayReadInstr.stringRepresentation;
			reassignForEachVar.returnType = Type.Void; // Doesn't return anything
			reassignForEachVar.variableThatWasChanged = forEachVar;
			reassignForEachVar.parentInstruction = loopStartLabel;
			reassignForEachVar.createArgs(1);
			reassignForEachVar.argReferences[0] = arrayReadInstr;
			reassignForEachVar.addTopologicalReference(arrayReadInstr);
			instructions.add(reassignForEachVar);
		}
		
		// Get the instructions contained by this for-loop
		currentParsingLineNumber++;
		boolean hadError = parseLinesAtLevel(loopStartLabel);
		if (hadError) {
			return true;
		}
		
		hadError = generateBinaryReassignment(loopCounterVar, InstructionType.Add,
				stepValInstr, loopStartLabel, countReadInstr);
		if (hadError) {
			return true;
		}
		
		// Create the end of the for-loop
		Instruction endInstr = new Instruction(InstructionType.EndBlock);
		endInstr.stringRepresentation = "end for";
		endInstr.returnType = Type.Void; // Doesn't return anything
		endInstr.parentInstruction = loopStartLabel;
		instructions.add(endInstr);
		
		// Mark this as the end of the Loop instruction
		loopStartLabel.endInstruction = endInstr;
		
		// Reference the loop counter increment instruction from the Loop instruction
		loopStartLabel.createArgs(1);
		loopStartLabel.argReferences[0] = countReadInstr;
		
		return false;
	}
	
	// Return the return data type for the given instruction type and operands
	static Type getReturnTypeFromInstructionAndOperands(InstructionType type, Type operandType1, Type operandType2) {
		
		if (type == InstructionType.Add ||
				type == InstructionType.Sub ||
				type == InstructionType.Mult ||
				type == InstructionType.Div ||
				type == InstructionType.Power) {
			
			if (operandType1 == Type.Int && operandType2 == Type.Int) {
				return Type.Int;
			} else if (operandType1 == Type.Int && operandType2 == Type.Long) {
				return Type.Long;
			} else if (operandType1 == Type.Long && operandType2 == Type.Int) {
				return Type.Long;
			} else if (operandType1 == Type.Long && operandType2 == Type.Long) {
				return Type.Long;
			} else if (operandType1 == Type.Int && operandType2 == Type.Float) {
				return Type.Float;
			} else if (operandType1 == Type.Float && operandType2 == Type.Int) {
				return Type.Float;
			} else if (operandType1 == Type.Float && operandType2 == Type.Float) {
				return Type.Float;
			} else if (operandType1 == Type.Long && operandType2 == Type.Float) {
				return Type.Float;
			} else if (operandType1 == Type.Float && operandType2 == Type.Long) {
				return Type.Float;
			} else if (operandType1 == Type.Int && operandType2 == Type.Double) {
				return Type.Double;
			} else if (operandType1 == Type.Double && operandType2 == Type.Int) {
				return Type.Double;
			} else if (operandType1 == Type.Long && operandType2 == Type.Double) {
				return Type.Double;
			} else if (operandType1 == Type.Double && operandType2 == Type.Long) {
				return Type.Double;
			} else if (operandType1 == Type.Double && operandType2 == Type.Double) {
				return Type.Double;
			} else if (operandType1 == Type.Float && operandType2 == Type.Double) {
				return Type.Double;
			} else if (operandType1 == Type.Double && operandType2 == Type.Float) {
				return Type.Double;
			}
			
			if (type == InstructionType.Add) {
				printError("Addition cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.Sub) {
				printError("Subtraction cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.Mult) {
				printError("Multiplication cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.Div) {
				printError("Division cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.Power) {
				printError("Exponentiation cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (type == InstructionType.Modulo) {
			
			if (operandType1 == Type.Int && operandType2 == Type.Int) {
				return Type.Int;
			} else if (operandType1 == Type.Int && operandType2 == Type.Long) {
				return Type.Int;
			} else if (operandType1 == Type.Long && operandType2 == Type.Int) {
				return Type.Long;
			} else if (operandType1 == Type.Long && operandType2 == Type.Long) {
				return Type.Long;
			} else if (operandType1 == Type.Float && operandType2 == Type.Int) {
				return Type.Float;
			} else if (operandType1 == Type.Float && operandType2 == Type.Float) {
				return Type.Float;
			} else if (operandType1 == Type.Float && operandType2 == Type.Long) {
				return Type.Float;
			} else if (operandType1 == Type.Double && operandType2 == Type.Int) {
				return Type.Double;
			} else if (operandType1 == Type.Double && operandType2 == Type.Double) {
				return Type.Double;
			} else if (operandType1 == Type.Double && operandType2 == Type.Float) {
				return Type.Double;
			} else if (operandType1 == Type.Double && operandType2 == Type.Long) {
				return Type.Double;
			} else {
				printError("Modulus cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			}
		} else if (type == InstructionType.And || type == InstructionType.Or || type == InstructionType.Not) {
			if (operandType1 == Type.Bool && operandType2 == Type.Bool) {
				return Type.Bool;
			}
			
			if (type == InstructionType.And) {
				printError("AND cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.Or) {
				printError("OR cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.Not) {
				printError("NOT cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (type == InstructionType.BitAnd || type == InstructionType.BitOr || type == InstructionType.BitNot) {
			if (operandType1 == Type.Int && operandType2 == Type.Int) {
				return Type.Int;
			} else if (operandType1 == Type.Long && operandType2 == Type.Long) {
				return Type.Long;
			}
			
			if (type == InstructionType.BitAnd) {
				printError("Bitwise AND cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.BitOr) {
				printError("Bitwise OR cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.BitNot) {
				printError("Bitwise NOT cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (type == InstructionType.Concat) {
			if (operandType1 == Type.String && operandType2 == Type.Double) {
				return Type.String;
			} else if (operandType1 == Type.Double && operandType2 == Type.String) {
				return Type.String;
			} else if (operandType1 == Type.String && operandType2 == Type.Float) {
				return Type.String;
			} else if (operandType1 == Type.Float && operandType2 == Type.String) {
				return Type.String;
			} else if (operandType1 == Type.String && operandType2 == Type.Int) {
				return Type.String;
			} else if (operandType1 == Type.Int && operandType2 == Type.String) {
				return Type.String;
			} else if (operandType1 == Type.String && operandType2 == Type.Long) {
				return Type.String;
			} else if (operandType1 == Type.Long && operandType2 == Type.String) {
				return Type.String;
			} else if (operandType1 == Type.String && operandType2 == Type.Bool) {
				return Type.String;
			} else if (operandType1 == Type.Bool && operandType2 == Type.String) {
				return Type.String;
			} else if (operandType1 == Type.String && operandType2 == Type.String) {
				return Type.String;
			} else {
				printError("Concatenation cannot be performed on " + operandType1 + " and " + operandType2);
				return null;
			}
		} else if (type == InstructionType.Equal || type == InstructionType.NotEqual) {
			if (operandType1.isNumberType() && operandType2.isNumberType()) {
				return Type.Bool;
			} else if (operandType1 == Type.String && operandType2 == Type.String) {
				return Type.Bool;
			} else if (operandType1 == Type.Bool && operandType2 == Type.Bool) {
				return Type.Bool;
			} else {
				printError("Equality cannot be tested on " + operandType1 + " and " + operandType2);
				return null;
			}
		} else if (type == InstructionType.RefEqual || type == InstructionType.RefNotEqual) {
			if (operandType1 == Type.Void || operandType2 == Type.Void) {
				printError("Reference equality cannot be tested on " + operandType1 + " and " + operandType2);
				return null;
			} else {
				return Type.Bool;
			}
		} else if (type == InstructionType.Less || type == InstructionType.Greater ||
					type == InstructionType.LessEqual || type == InstructionType.GreaterEqual) {
			if (operandType1.isNumberType() && operandType2.isNumberType()) {
				return Type.Bool;
			}
			
			if (type == InstructionType.Less) {
				printError("Less Than cannot be tested on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.Greater) {
				printError("Greater Than cannot be tested on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.LessEqual) {
				printError("Less or Equal To cannot be tested on " + operandType1 + " and " + operandType2);
				return null;
			} else if (type == InstructionType.GreaterEqual) {
				printError("Greater or Equal To cannot be tested on " + operandType1 + " and " + operandType2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (type == InstructionType.Print) {
			return Type.Void;
		} else if (type == InstructionType.ToString) {
			return Type.String;
		} else if (type == InstructionType.Length) {
			if (operandType2 != null) {
				new Exception(InstructionType.Length + " second arg must be null");
			}
			if (operandType1.isArrayType()) {
				return Type.Int;
			} else {
				printError("ArrayLength cannot be determined for type " + operandType1);
			}
		} else {
			printError("Invalid return type: " + type);
			return null;
		}

		new Exception("This code should not be reached").printStackTrace();
		return null;
	}
	
	// Return the instruction type corresponding to the given binary operator
	static InstructionType getInstructionTypeFromOperator(String binaryOperator) {
		if (binaryOperator.equals("+") || binaryOperator.equals("+=")) {
			return InstructionType.Add;
		} else if (binaryOperator.equals("-") || binaryOperator.equals("-=")) {
			return InstructionType.Sub;
		} else if (binaryOperator.equals("/") || binaryOperator.equals("/=")) {
			return InstructionType.Div;
		} else if (binaryOperator.equals("*") || binaryOperator.equals("*=")) {
			return InstructionType.Mult;
		} else if (binaryOperator.equals("^") || binaryOperator.equals("^=")) {
			return InstructionType.Power;
		} else if (binaryOperator.equals("%") || binaryOperator.equals("%=")) {
			return InstructionType.Modulo;
		} else if (binaryOperator.equals("=")) {
			return InstructionType.Equal;
		} else if (binaryOperator.equals("!=")) {
			return InstructionType.NotEqual;
		} else if (binaryOperator.equals("<")) {
			return InstructionType.Less;
		} else if (binaryOperator.equals("@=")) {
			return InstructionType.RefEqual;
		} else if (binaryOperator.equals("!@=")) {
			return InstructionType.RefNotEqual;
		} else if (binaryOperator.equals(">")) {
			return InstructionType.Greater;
		} else if (binaryOperator.equals("<=")) {
			return InstructionType.LessEqual;
		} else if (binaryOperator.equals(">=")) {
			return InstructionType.GreaterEqual;
		} else if (binaryOperator.equals("AND") || binaryOperator.equals("AND=")) {
			return InstructionType.And;
		} else if (binaryOperator.equals("OR") || binaryOperator.equals("OR=")) {
			return InstructionType.Or;
		} else {
			printError("Invalid operator: " + binaryOperator);
			return null;
		}
	}
	
	// Determine which method declaration each method call was referencing
	static boolean findMethodCallReferences() {
		
		// Search through each method call
		for (int i = 0; i < instructions.size(); i++) {
			Instruction instr = instructions.get(i);
			
			// If this is a method call
			if (instr.instructionType == InstructionType.Call) {
				
				Instruction routineRef = findRoutineByNameAndArgs(instr);
				
				if (routineRef == null) {
					return true;
				}
				
				instr.callRoutineReference = routineRef;
			}
		}
		
		return false;
	}
	
	// Return true if there is any code located after a routine in the given parent
	static boolean searchForCodeAfterRoutine(Instruction parentInstr) {
		boolean foundCode = false;
		
		// Iterate backward to find the assignment of this variable
		for (int i = instructions.size()-1; i >= 0; i--) {
			
			Instruction instr = instructions.get(i);
			
			// Stop searching at the parent
			if (instr == parentInstr) {
				return false;
			}
			
			// Make sure this instruction is a child of the given parent
			if (instr.parentInstruction != parentInstr) {
				continue;
			}
			
			// If this is not a routine definition, and not an End-instruction
			if (instr.instructionType != InstructionType.RoutineDefinition &&
					instr.instructionType != InstructionType.EndBlock) {
				foundCode = true;
				continue;
			}
			
			// If this is an end block to a routine definition and we have already found
			//    some code, then this is invalid
			if (foundCode && instr.instructionType == InstructionType.RoutineDefinition) {
				return true;
			}
		}
		
		return false;
	}
	
	// Return true if the given routine was previously declared in the given scope
	static boolean findConflictingRoutine(Instruction parentInstr, Routine routine) {
		
		// Iterate backward to find a routine
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			InstructionType type = otherInstruction.instructionType;
			
			if (type == InstructionType.RoutineDefinition) { // If this is a routine
				
				// If this instruction defined a routine by the same name
				if (otherInstruction.routineThatWasDefined.name.equals(routine.name)) {
					
					Instruction otherParent = otherInstruction.parentInstruction; // May be null
					
					boolean isInConflictingScope = false;
					
					// If this instruction is a child of an instruction that is a ancestor of the
					//    given instruction, then it must be true that that instruction executed if the
					//    given instruction executed, so there might be a conflict
					Instruction nextParent = parentInstr;
					while (nextParent != otherParent && nextParent != null) {
						nextParent = nextParent.parentInstruction;
					}
					if (nextParent == otherParent) {
						isInConflictingScope = true;
					}
					
					// If this instruction is a descendant of the parent of the given instruction,
					//    then they might be in conflict due to the global scope of methods.
					nextParent = otherParent;
					while (nextParent != parentInstr && nextParent != null) {
						nextParent = nextParent.parentInstruction;
					}
					if (nextParent == parentInstr) {
						isInConflictingScope = true;
					}
					
					// If there might be a conflict
					if (isInConflictingScope) {
						// If they are indistinguishable
						if (!otherInstruction.routineThatWasDefined.isDistinguisable(routine)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	// Return the instruction that declared the routine of the given name and argument types
	static Instruction findRoutineByNameAndArgs(Instruction instr) {
		
		Type[] argTypes = new Type[instr.argReferences.length];
		for (int j = 0; j < argTypes.length; j++) {
			argTypes[j] = instr.argReferences[j].returnType;
		}
		
		Instruction parentInstr = instr.parentInstruction;
		String routineName = instr.routineName;
		
		// If we found a matching routine that required implicit casts to call, then record it just in case.
		Instruction implicitMatch = null;
		
		// Only used for error handling
		Routine matchingNameRoutine = null;
		
		// Iterate backward to find a routine
		outerLoop:
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			
			// If this is a routine definition
			if (otherInstruction.instructionType == InstructionType.RoutineDefinition) {
				
				// If this instruction defined a routine by the same name
				if (otherInstruction.routineThatWasDefined.name.equals(routineName)) {
					matchingNameRoutine = otherInstruction.routineThatWasDefined;
					
					Instruction otherParent = otherInstruction.parentInstruction; // May be null
					
					boolean isInValidScope = false;
					
					// If this instruction is a child of an instruction that is a ancestor of the
					//    given instruction, then it must be true that that instruction executed if the
					//    given instruction executed, so it is in scope
					Instruction nextParent = parentInstr;
					while (nextParent != otherParent && nextParent != null) {
						nextParent = nextParent.parentInstruction;
					}
					if (nextParent == otherParent) {
						isInValidScope = true;
					}
					
					// If this instruction is a descendant of the parent of the given instruction,
					//    then it might be in scope due to the global scope of methods.
					nextParent = otherParent;
					while (nextParent != parentInstr && nextParent != null) {
						nextParent = nextParent.parentInstruction;
					}
					if (nextParent == parentInstr) {
						isInValidScope = true;
					}
					
					// If this routine is in scope
					if (isInValidScope) {
						// If they have matching parameters
						Type[] otherArgTypes = otherInstruction.routineThatWasDefined.argTypes;
						
						// Make sure each of the arguments can be cast to the others
						if (otherArgTypes.length == argTypes.length) {
							boolean requiresImplicitCast = false;
							for (int j = 0; j < argTypes.length; j++) {
								if (argTypes[j] == otherArgTypes[j]) {
									// Good exact match
								} else if (argTypes[j].canImplicitlyCastTo(otherArgTypes[j])) {
									requiresImplicitCast = true;
								} else {
									continue outerLoop; // continue searching for another routine
								}
							}
							
							// The arguments matched
							if (requiresImplicitCast) {
								implicitMatch = otherInstruction;
							} else {
								// This was an exact match (with no implicit casting)
								return otherInstruction;
							}
						}
					}
				}
			}
		}

		// If we found an implicit match, then that is good enough
		if (implicitMatch != null) {
			return implicitMatch;
		}

		String argsString = "";
		for (int j = 0; j < argTypes.length; j++) {
			argsString += argTypes[j];
			if (j != argTypes.length-1) {
				argsString += ", ";
			}
		}
		
		if (matchingNameRoutine != null) {
			Type[] otherArgTypes = matchingNameRoutine.argTypes;
			String otherArgsString = "";
			for (int j = 0; j < otherArgTypes.length; j++) {
				otherArgsString += otherArgTypes[j];
				if (j != otherArgTypes.length-1) {
					otherArgsString += ", ";
				}
			}
			
			printError("Method " + matchingNameRoutine.name + "(" + otherArgsString + ") cannot take arguments "
						+ "(" + argsString + ")");
			return null;
		}
		
		printError("Method " + routineName + "(" + argsString + ") has not been declared");
		return null;
	}
	
	// Return the variable by the given name if it was previously used in the given scope
	static Variable findVariableByName(Instruction parentInstr, String varName) {
		
		// Iterate backward to find the assignment of this variable
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			
			/*
			if (type == InstructionType.RoutineDefinition) { // If this is a routine
				// We can stop searching because we hit the header of the routine the given instruction is inside.
				return null;
			}
			*/
			
			Variable var = otherInstruction.variableThatWasChanged;
			if (var == null) {
				var = otherInstruction.variableThatWasRead;
			}
			
			// If this instruction used the given variable
			if (var != null && var.name.equals(varName)) {
				
				Instruction otherParent = otherInstruction.parentInstruction; // May be null
				
				// If this instruction is a child of an instruction that is a ancestor of the
				//    given instruction, then it must be true that that instruction executed if the
				//    given instruction executed, so we can stop searching.
				Instruction nextParent = parentInstr;
				while (nextParent != otherParent && nextParent != null) {
					nextParent = nextParent.parentInstruction;
				}
				if (nextParent == otherParent) {
					return var;
				}
			}
		}
		return null;
	}
	
	// Add references to all instructions that may have declared or read the given variable
	//    until we find an instruction that is guaranteed to have declared or read the given variable.
	// Return true if a guaranteed declaration or read was found.
	static boolean addReferenceToInstructionsThatDeclaredOrRead(Instruction instr, Variable var) {
		
		// Iterate backward to find the assignment of this variable
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			InstructionType type = otherInstruction.instructionType;
			if (type == InstructionType.AllocAndAssign || type == InstructionType.Read ||
					type == InstructionType.DeclareScope) {
				
				// If this instruction wrote to the given variable
				if (otherInstruction.variableThatWasChanged == var ||
						otherInstruction.variableThatWasRead == var) {
					
					// This instruction changed the variable, so add a reference to it
					instr.addTopologicalReference(otherInstruction);
					
					Instruction otherParent = otherInstruction.parentInstruction; // May be null
					
					// If this instruction is a direct child of an instruction that is an ancestor of the
					//    given instruction, then it must be true that that instruction executed if the
					//    given instruction executed, so we can stop searching.
					Instruction nextParent = instr.parentInstruction;
					while (nextParent != otherParent && nextParent != null) {
						nextParent = nextParent.parentInstruction;
					}
					
					// If we found a guaranteed executed instruction in the parent scope
					if (nextParent == otherParent) {
						return true;
					}
				}
			}// else if (type == InstructionType.RoutineDefinition) { // If this is a routine
				// We can stop searching because we hit the header of the routine the given instruction is inside.
			//	return false;
			//}
		}
		
		return false;
	}
	
	// Add references to all instructions that may have assigned to the given variable
	//    until we find an instruction that is guaranteed to have assigned the given variable.
	// Return true if a guaranteed assignment was found.
	static boolean addReferenceToInstructionsThatAssigned(Instruction instr, Variable var) {
		
		// Iterate backward to find the assignment of this variable
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			InstructionType type = otherInstruction.instructionType;
			
			if (type == InstructionType.AllocAndAssign || type == InstructionType.Reassign) {
				
				// If this instruction wrote to the given variable
				if (otherInstruction.variableThatWasChanged == var) {
					
					// This instruction changed the variable, so add a reference to it
					instr.addTopologicalReference(otherInstruction);
					
					// Determine if this instruction was guaranteed to be executed
					
					Instruction otherParent = otherInstruction.parentInstruction; // May be null
					
					// If this instruction is a direct child of an instruction that is an ancestor of the
					//    given instruction, then it must be true that that instruction executed if the
					//    given instruction executed, so we can stop searching.
					Instruction nextParent = instr.parentInstruction;
					while (nextParent != otherParent && nextParent != null) {
						nextParent = nextParent.parentInstruction;
					}
					
					// If we found a guaranteed executed instruction
					if (nextParent == otherParent) {
						return true;
					}
					
					// Check whether an assignment was guaranteed after this instruction
					//     and before the instruction of interest.
					// Consider exhaustive if-elseif-else chains, do-while loops, and other structures
					//       for guaranteed assignments.
					if (wasAssignmentGuaranteed(i, instructions.size(), var, instr.parentInstruction)) {
						return true;
					}
				} else if (otherInstruction.variableThatWasChanged == null) {
					new Exception("Instruction variable changed must not be null").printStackTrace();
					return false;
				}
			}// else if (type == InstructionType.RoutineDefinition) { // If this is a routine
				// We can stop searching because we hit the header of the routine the given instruction is inside.
			//	return false;
			//}
		}
		
		return false;
	}
	
	// Return true if an assignment of the given variable was guaranteed between the given
	//     start and stop instruction index within the enclosing scope of parentInstruction
	static boolean wasAssignmentGuaranteed(int startIndex, int stopIndex,
				Variable var, Instruction parentInstruction) {
		
		Instruction instr = null;
		int currentInstructionIndex = -1;
		
		// Iterate forward until we find an assignment instruction
		for (int i = startIndex; i < stopIndex; i++) {
			
			// If this is a reassignment of some sort
			if (instructions.get(i).instructionType == InstructionType.AllocAndAssign ||
					instructions.get(i).instructionType == InstructionType.Reassign) {
				
				instr = instructions.get(i);
				currentInstructionIndex = i;
				break;
			}
		}
		
		// No assignment was found, so return false
		if (instr == null) {
			return false;
		}
		
		// Determine if an assignment is guaranteed in the parent instruction
		Instruction currentParent = instr.parentInstruction;
		
		// This assignment is in the same scope as the given parent,
		//   so we are done searching.
		while (currentParent != parentInstruction) {
			
			// This is not possible here
			if (currentParent == null) {
				new Exception("Parent may not be null!").printStackTrace();
				return false;
			}
			
			// If we are currently inside a loop, then check if the current instruction
			//     is guaranteed to execute at least once.
			if (currentParent.instructionType == InstructionType.Loop) {
				// Search for a break or continue statement that possibly breaks this loop
				//   before getting to the current instruction
				int i = currentInstructionIndex - 1;
				while (i >= 0) {
					Instruction currentInstr = instructions.get(i);
					
					// If we are at the parent instruction, stop searching
					if (currentInstr == currentParent) {
						break;
					}
					
					if (currentInstr.instructionType == InstructionType.Break ||
							currentInstr.instructionType == InstructionType.Continue) {
						if (currentInstr.argReferences[0] == currentParent) {
							// A possible break was found before the current instruction.
							// Therefore it may not have been executed.
							return false;
						}
					}
					
					i--;
				}
			}
			
			// If we are inside a if-elseif-else structure, but not all the way at the
			//    top of the chain, then stop searching here.
			if (currentParent.instructionType == InstructionType.Else ||
					currentParent.instructionType == InstructionType.ElseIf) {
				return false;
			}
			
			// If we are inside an if-elseif-else structure, and this instruction is
			//    inside the leading if-condition
			if (currentParent.instructionType == InstructionType.If) {
				// Check if this if-chain ends in an else (i.e. it may be exhaustive)
				Instruction currentIf = currentParent;
				do {
					currentIf = currentIf.nextChainedInstruction;
				} while (currentIf != null && currentIf.instructionType != InstructionType.Else);
				
				// If this chain did not end in an Else-block, then we are done searching
				if (currentIf == null) {
					return false;
				}
				
				// If this chain DID end in an else-block, then we need to check if
				//    a guaranteed assignment exists in each of the chained conditional sections.
				currentIf = currentParent;
				do {
					// This must be an ElseIf or an Else block
					currentIf = currentIf.nextChainedInstruction;
					Instruction endInstruction = currentIf.endInstruction;
					int startInstructionIndex = instructions.indexOf(currentIf) + 1;
					int endInstructionIndex = instructions.indexOf(endInstruction);
					
					// Recursively check for guaranteed assignment.
					// If any one was not guaranteed, then return false
					if (!wasAssignmentGuaranteed(startInstructionIndex,
							endInstructionIndex, var, currentIf)) {
						return false;
					}
				} while (currentIf.instructionType != InstructionType.Else);
			}
			
			// This is a routine, so we can't be guaranteed that it will execute
			if (currentParent.instructionType == InstructionType.RoutineDefinition) {
				return false;
			}
			
			// We are still safe, so check the next parent now
			currentInstructionIndex = instructions.indexOf(currentParent);
			currentParent = currentParent.parentInstruction;
		} 
		
		return true;
	}
	
	// Return the closest ancestor instruction that is a Loop instruction, or null if none is found
	static Instruction findNearestAncestorLoop(Instruction parent) {
		while (parent != null && parent.instructionType != InstructionType.Loop) {
			// Loops and conditional structures may not contain a routine
			if (parent.instructionType == InstructionType.RoutineDefinition) {
				break;
			}
			parent = parent.parentInstruction;
		}
		if (parent.instructionType != InstructionType.Loop) {
			return null;
		}
		return parent;
	}
	
	// Convert all the instructions into QuadInstructions
	static ArrayList<QuadInstruction> convertToQuadInstructions() {
		ArrayList<QuadInstruction> quadInstructions = new ArrayList<QuadInstruction>();
		for (int i = 0; i < instructions.size(); i++) {
			quadInstructions.add(instructions.get(i).toQuadIR());
		}
		return quadInstructions;
	}
	
	// Load some text from a file
	static String loadFile(String directory) {
		
		String textToRead = null;
		try {
			textToRead = new String(Files.readAllBytes(Paths.get(directory)));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		// Get rid of weird newline characters
		return textToRead.replace(System.getProperty("line.separator"), "\n");
	}
	
	// Save the program to a new file, and create a directory if necessary
	static void saveFile(String directory, String text) {
		
		// Make a new directory if necessary
		int lastSlashIndex = directory.lastIndexOf('\\');
		if (lastSlashIndex == -1) {
			lastSlashIndex = directory.lastIndexOf('/');
		}
		if (lastSlashIndex != -1) {
			final String folder = directory.substring(0, lastSlashIndex);
			
			// If this folder does not exist
			if (!new File(folder).exists()) {
				new File(folder).mkdirs();
			}
		}
		
		// Create and write the file
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(directory));
			writer.write(text);
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void printError(String message) {
		System.out.println(message);
		if (currentParsingLineNumber < lines.length) {
			System.out.println("'" + lines[currentParsingLineNumber] + "'");
		}
		System.out.println("(on line " + (currentParsingLineNumber + 1) + ")");
		
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
		if (debugPrintOn) {
			System.out.println(o);
		}
	}
}

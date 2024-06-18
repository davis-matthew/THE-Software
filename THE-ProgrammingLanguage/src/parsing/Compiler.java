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
	
	static final String fileToRead = "testFiles/ProgramInput.the";
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
		lines = ParseUtil.breakIntoLines(text);
		lines = ParseUtil.removeWhiteSpace(lines);
		lines = ParseUtil.stripComments(lines);
		
		// Estimate the number of instructions in this program
		instructions = new ArrayList<Instruction>(lines.length * 2 + 10);
		
		// Parse all the lines in the program
		currentParsingLineNumber = 0;
		for (int i = 0; i < lines.length; i++) {
			boolean hadError = parseLine(lines[i]);
			if (hadError) {
				return;
			}
			currentParsingLineNumber++;
		}
		
		// Check for invalid scope
		if (lines.length > currentParsingLineNumber) {
			printError("Extra ']' in program");
			return;
		}
		
		// Find which function each function call was referencing
		boolean hadError = findFunctionCallReferences();
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
	
	// Parse a single line of code.
	// Return true if error.
	static boolean parseLine(String line) {
		
		Instruction parentInstruction = findParentInstruction(instructions.size() - 1);
		
		if (line.isEmpty()) {
			// Empty line.  Nothing to do here.
			
		} else if (line.equals("break") || line.equals("continue")) { // Break or continue in loop
			
			Instruction instr;
			if (line.equals("break")) {
				instr = new Instruction(InstructionType.Break);
			} else {
				instr = new Instruction(InstructionType.Continue);
			}
			
			instr.stringRepresentation = instr.instructionType.toSymbolForm();
			instr.parentInstruction = parentInstruction;

			// Find the nearest ancestor loop that contains this statement
			Instruction parentLoop = findNearestAncestorLoop(parentInstruction);
			if (parentLoop == null) {
				printError(ParseUtil.capitalize(instr.instructionType.toSymbolForm())
						+ "statement must be within a loop");
				return true;
			}
			
			instr.setArgs(parentLoop);
			
			instructions.add(instr);
			
		} else if (line.equals("[")) { // Enclosing scope (Enscope)
			
			Instruction instr = new Instruction(InstructionType.Enscope);
			instr.stringRepresentation = "enscope";
			instr.parentInstruction = parentInstruction;
			instructions.add(instr);
			
		} else if (ParseUtil.doesLineStartWith(line, "for")) { // For-loop
			
			// This instruction restricts the scope of the whole for-loop and initialization
			Instruction enscopeInstr = new Instruction(InstructionType.Enscope);
			enscopeInstr.stringRepresentation = "for-loop enscope";
			enscopeInstr.parentInstruction = parentInstruction;
			instructions.add(enscopeInstr);
			
			// Get the contents of the for-loop header
			final int conditionsStartIndex = 3;
			String expressionContent = line.substring(conditionsStartIndex);
			String[] args = ParseUtil.separateArguments(expressionContent);
			if (args == null) {
				return true;
			}
			
			// Create the increment, start bound, and stop bound instructions
			Variable arrayVar;
			String startBoundVariableString;
			String loopBreakIfStatementString;
			String incrementString;
			String forEachAssignment = null; // Used only for for-each loop
			
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
				
				// Generate a unique loop variable name to use as the loop iterator
				final String loopVarName = "GENERATED_index" + instructions.size();
				startBoundVariableString = "int " + loopVarName + " = 0";
				incrementString = loopVarName + "++";
				
				// Get the type of variable that is being used to store the value in the loop
				String loopVarDeclarationString = forEachArgs[0].trim();
				
				int firstSpaceIndex = loopVarDeclarationString.indexOf(' ');
				if (firstSpaceIndex == -1) {
					printError("For-loop variable type missing");
					return true;
				}
				
				String forEachVarTypeString = loopVarDeclarationString.substring(0, firstSpaceIndex);
				Type forEachVarType = Type.getTypeFromString(forEachVarTypeString);
				String forEachVarName = loopVarDeclarationString.substring(firstSpaceIndex).trim();
				
				// Try to find an existing variable in this scope with the same name
				Variable existingVar = findVariableByName(enscopeInstr, forEachVarName);
				if (existingVar != null) {
					printError("Variable '" + forEachVarName +
							"' has already been declared in this scope");
					return true;
				}
				
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
				
				loopBreakIfStatementString = "if " + loopVarName + " >= #" + arrayVarName;
				forEachAssignment = loopVarDeclarationString + " = " + arrayVarName + "[" + loopVarName + "]";
				
			} else if (args.length == 2 || args.length == 3) { // Indexed for-loop
				
				// Get the assignment string (i.e. "int i = 0")
				startBoundVariableString = args[0].trim();
				
				int firstSpaceIndex = startBoundVariableString.indexOf(' ');
				if (firstSpaceIndex == -1) {
					printError("For-loop variable type missing");
					return true;
				}

				// Get the type of variable that is being used to iterate
				String loopVarTypeString = startBoundVariableString.substring(0, firstSpaceIndex);
				Type varType = Type.getTypeFromString(loopVarTypeString);
				
				// Make sure the loop variable is an integer type
				if (!varType.isNumberType()) {
					printError("For-loop variable must be a numeric type");
					return true;
				}
				
				int equalSignIndex = startBoundVariableString.indexOf('=');
				if (equalSignIndex == -1) {
					printError("Assignment must be made in For-loop variable");
					return true;
				}
				
				final String loopVarName = startBoundVariableString.substring(firstSpaceIndex, equalSignIndex).trim();
				
				// Break the loop if this condition is true
				final String startBoundExpression = args[0].substring(equalSignIndex + 1).trim();
				final String stopBoundExpression = args[1].trim();
				loopBreakIfStatementString = "if (" + loopVarName + " >= (" + stopBoundExpression + ")) " +
												"OR (" + loopVarName + " < (" + startBoundExpression + "))";
				
				// If the counting increment is explicitly given
				if (args.length == 3) {
					String incrementExpression = args[2].trim();
					incrementString = loopVarName + " += (" + incrementExpression + ")";
				} else {
					// The counting increment is not explicitly given, so assume it is 1
					incrementString = loopVarName + "++";
				}
			} else {
				printError("Too many arguments in For-loop header");
				return true;
			}
			
			// Parse all the instructions for the initial value of the loop index variable
			boolean hadError = parseLine(startBoundVariableString);
			if (hadError) {
				return true;
			}
			
			// Create the looping construct.
			// This instruction precedes all content of the loop that is repeated.
			Instruction loopStartLabel = new Instruction(InstructionType.Loop);
			loopStartLabel.stringRepresentation = "for-loop start";
			loopStartLabel.parentInstruction = enscopeInstr;
			instructions.add(loopStartLabel);
			
			// Create the loop break condition.
			// Parse all the instructions to test whether we need to break this loop.
			hadError = parseLine(loopBreakIfStatementString);
			if (hadError) {
				return true;
			}
			
			// This will be a reference to the if-statement
			Instruction lastInstructionFromIfCondition = instructions.get(instructions.size()-1);
			
			Instruction boundBreak = new Instruction(InstructionType.Break);
			boundBreak.stringRepresentation = "break for";
			boundBreak.setArgs(loopStartLabel);
			boundBreak.parentInstruction = lastInstructionFromIfCondition;
			instructions.add(boundBreak);
			
			Instruction ifBoundEnd = new Instruction(InstructionType.EndBlock);
			ifBoundEnd.stringRepresentation = "end if";
			ifBoundEnd.parentInstruction = lastInstructionFromIfCondition;
			instructions.add(ifBoundEnd);
			
			lastInstructionFromIfCondition.endInstruction = ifBoundEnd;
			
			// Reassign the for-each loop reference variable, if this is a for-each loop
			if (forEachAssignment != null) {
				hadError = parseLine(forEachAssignment);
				if (hadError) {
					return true;
				}
			}
			
			// This code will be injected when the end-of-block is reached.
			loopStartLabel.codeToInjectAtEndOfBlock = incrementString;
			
		} else if (line.equals("do")) { // Do-while loop (header part)
			
			Instruction instr = new Instruction(InstructionType.Loop);
			instr.stringRepresentation = "do loop start";
			instr.parentInstruction = parentInstruction;
			instructions.add(instr);
			
		} else if (ParseUtil.doesLineStartWith(line, "] while")) { // Do-while loop (end part)
			
			// Find the last Loop instruction that hasn't been connected to a EndBlock instruction.
			Instruction doStartInstruction = null;
			int instrIndex = instructions.size()-1;
			while (instrIndex >= 0 && (!instructions.get(instrIndex).instructionType.doesStartScope() ||
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
			notInstr.setArgs(lastInstruction);
			notInstr.returnType = Type.Bool;
			notInstr.parentInstruction = doStartInstruction;
			instructions.add(notInstr);
			
			// Build the if-statement for the while loop
			Instruction ifInstr = new Instruction(InstructionType.If);
			ifInstr.stringRepresentation = notInstr.instructionType.toSymbolForm() +
					"(" + expressionContent + ")";
			ifInstr.setArgs(notInstr);
			ifInstr.parentInstruction = doStartInstruction;
			instructions.add(ifInstr);
			
			// Create a break statement for this while loop
			Instruction breakInstr = new Instruction(InstructionType.Break);
			breakInstr.stringRepresentation = "break";
			breakInstr.parentInstruction = ifInstr;
			breakInstr.setArgs(doStartInstruction);
			instructions.add(breakInstr);
			
			// Create the EndBlock for this If-block
			Instruction endIf = new Instruction(InstructionType.EndBlock);
			endIf.stringRepresentation = "end if";
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
			doEndInstr.setArgs(lastInstruction);
			doEndInstr.parentInstruction = doStartInstruction;
			instructions.add(doEndInstr);
			
			// Mark this as the end of the DoStart instruction
			doStartInstruction.endInstruction = doEndInstr;
			
		} else if (ParseUtil.doesLineStartWith(line, "while")) { // While loop
			
			Instruction loopStartLabel = new Instruction(InstructionType.Loop);
			loopStartLabel.stringRepresentation = "while loop start";
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
			notInstr.setArgs(lastInstruction);
			notInstr.returnType = Type.Bool;
			notInstr.parentInstruction = loopStartLabel;
			instructions.add(notInstr);
			
			// Build the if-statement for the while loop
			Instruction ifInstr = new Instruction(InstructionType.If);
			ifInstr.stringRepresentation = notInstr.instructionType.toSymbolForm() +
					"(" + expressionContent + ")";
			ifInstr.setArgs(notInstr);
			ifInstr.parentInstruction = loopStartLabel;
			instructions.add(ifInstr);
			
			// Create a break statement for this while loop
			Instruction breakInstr = new Instruction(InstructionType.Break);
			breakInstr.stringRepresentation = "break";
			breakInstr.parentInstruction = ifInstr;
			breakInstr.setArgs(loopStartLabel);
			instructions.add(breakInstr);
			
			// Create the EndBlock for this If-block
			Instruction endIf = new Instruction(InstructionType.EndBlock);
			endIf.stringRepresentation = "end if";
			endIf.parentInstruction = ifInstr;
			instructions.add(endIf);
			
			ifInstr.endInstruction = endIf;
			
		} else if (ParseUtil.doesLineStartWith(line, "if")  ||
					ParseUtil.doesLineStartWith(line, "elseif")) { // If-statement or ElseIf-statement
			
			// Determine if this is an If-statement, or an ElseIf-statement
			boolean isElseIf = line.startsWith("elseif");
			
			// Find the last If or ElseIf instruction that hasn't been connected to an end instruction.
			Instruction precedingChainInstruction = null; // Only use for ElseIf
			if (isElseIf) {
				if (parentInstruction.instructionType == InstructionType.If ||
					parentInstruction.instructionType == InstructionType.ElseIf) {
					
					precedingChainInstruction = parentInstruction;
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
				previousEndInstr.parentInstruction = precedingChainInstruction;
				instructions.add(previousEndInstr);
				
				// Mark the end of the previous chaining instruction
				precedingChainInstruction.endInstruction = previousEndInstr;
				
				// Mark this as the next instruction in the chain from the previous
				precedingChainInstruction.nextChainedInstruction = ifInstr;

				ifInstr.parentInstruction = precedingChainInstruction.parentInstruction;
			} else {
				ifInstr.parentInstruction = parentInstruction;
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
			ifInstr.setArgs(lastInstruction);
			instructions.add(ifInstr);
			
		} else if (line.equals("else")) { // This is an else chained onto an if
			
			Instruction precedingChainInstruction = null;
			if (parentInstruction.instructionType == InstructionType.If ||
				parentInstruction.instructionType == InstructionType.ElseIf) {
				
				precedingChainInstruction = parentInstruction;
			} else {
				printError("Else block not preceded by If or ElseIf block");
				return true;
			}
			
			// Create the EndBlock to end of the previous chained instruction
			Instruction previousEndInstr = new Instruction(InstructionType.EndBlock);
			previousEndInstr.stringRepresentation = "end " + precedingChainInstruction.instructionType;
			previousEndInstr.parentInstruction = precedingChainInstruction;
			instructions.add(previousEndInstr);
			
			// Mark the end of the previous chaining instruction
			precedingChainInstruction.endInstruction = previousEndInstr;
			
			// Create the Else block
			Instruction elseInstr = new Instruction(InstructionType.Else);
			elseInstr.stringRepresentation = "else";
			elseInstr.parentInstruction = precedingChainInstruction.parentInstruction;
			instructions.add(elseInstr);
			
			// Mark the next instruction in the chain from the previous
			precedingChainInstruction.nextChainedInstruction = elseInstr;
		
		} else if (line.equals("]")) { // Descope (for closing a loop, if, elseif, etc.)
			
			// Find the instruction that starts this scope-block
			Instruction openingBlockInstr = findParentInstruction(instructions.size() - 1);
			
			// If the opening block has some code that needs to be injected before the closing block,
			// then parse and add it here.
			if (openingBlockInstr.codeToInjectAtEndOfBlock != null) {
				// Parse the line that increments the loop variable
				boolean hadError = parseLine(openingBlockInstr.codeToInjectAtEndOfBlock);
				if (hadError) {
					return true;
				}
				
				// No longer needed.
				openingBlockInstr.codeToInjectAtEndOfBlock = null;
			}
			
			// Create the EndBlock to end this else-statement
			Instruction endInstr = new Instruction(InstructionType.EndBlock);
			endInstr.stringRepresentation = "end " + openingBlockInstr.stringRepresentation;
			endInstr.parentInstruction = openingBlockInstr;
			instructions.add(endInstr);
			
			openingBlockInstr.endInstruction = endInstr;
			
			// TODO Throw an error if this was the end of a do-while loop.
			// TODO do-while loops need to be closed with "] while (condition)"
			
		} else if (ParseUtil.getFirstDataType(line) != null) { // If this is a declaration of some sort
			Object[] typeData = ParseUtil.getFirstDataType(line);
			Type varType = (Type)typeData[0];
			int typeEndIndex = (int)typeData[1];
			
			// Make sure there is a space after the variable type
			if (line.charAt(typeEndIndex) != ' ') {
				printError("Declaration name missing");
				return true;
			}
			
			// Get the first variable name on this line at this index, and its end index
			Object[] varData = ParseUtil.getVariableName(line, typeEndIndex+1);
			if (varData == null) {
				printError("Declaration name missing");
				return true;
			}
			String varName = (String)varData[0];
			int varEndIndex = (int)varData[1];
			
			// Determine whether this is a variable, or function declaration
			boolean isFunctionDeclaration = false;
			if (varType == Type.Void || (varEndIndex < line.length() && line.charAt(varEndIndex) == '(')) {
				isFunctionDeclaration = true;
			}
			
			if (isFunctionDeclaration) {
				
				// Cannot define a function inside anything except another function or main program
				if (parentInstruction != null &&
						parentInstruction.instructionType != InstructionType.FunctionDefinition) {
					printError("Functions may only be defined inside other functions or main program");
					return true;
				}
				
				// Parse the types of the parameters to this function
				String paramString = ParseUtil.getFunctionArguments(line, typeEndIndex);
				String[] params = ParseUtil.separateArguments(paramString);
				if (params == null) {
					return true;
				}
				Type[] paramTypes = new Type[params.length];
				String[] paramNames = new String[params.length];
				
				for (int i = 0; i < params.length; i++) {
					Object[] data = ParseUtil.getFirstDataType(params[i]);
					if (data != null) {
						paramTypes[i] = (Type)data[0];
						int index = (int)data[1];
						paramNames[i] = params[i].substring(index).trim();
					} else {
						printError("Invalid parameter declaration or type in '" + params[i] + "'");
						return true;
					}
				}
				
				boolean foundDuplicates = ParseUtil.checkForDuplicates(paramNames);
				if (foundDuplicates) {
					printError("Duplicate parameter name in function header");
					return true;
				}
				
				// Create the routine signature
				Function routine = new Function(varName, new Type[]{varType}, paramTypes);
				
				// Try to find an existing variable in this scope that is in naming conflict with this one
				boolean foundConflict = findConflictingFunction(parentInstruction, routine);
				if (foundConflict) {
					printError("Function '" + varName + "' has already been declared in this scope");
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
				Instruction routineInstr = new Instruction(InstructionType.FunctionDefinition);
				routineInstr.stringRepresentation = varName + "(" + argTypesString + ")";
				routineInstr.functionThatWasDefined = routine;
				routineInstr.routineName = varName;
				routineInstr.parentInstruction = parentInstruction;
				routineInstr.returnType = varType; // TODO add multiple returns
				instructions.add(routineInstr);
				
				Instruction[] args = new Instruction[params.length];
				for (int i = 0; i < params.length; i++) {
					
					// Create the parameter variable
					Variable var = new Variable(paramNames[i], paramTypes[i]);
					
					Instruction instr = new Instruction(InstructionType.DeclareScope);
					instr.stringRepresentation = var.type + " " + var.name;
					instr.variableThatWasChanged = var;
					instr.parentInstruction = routineInstr;
					instructions.add(instr);
					
					args[i] = instr;
				}
				routineInstr.setArgs(args);
				
			} else {
				
				// Try to find an existing variable in this scope that is in naming conflict with this one
				Variable existingVar = findVariableByName(parentInstruction, varName);
				if (existingVar != null) {
					printError("Variable '" + varName + "' has already been declared in this scope");
					return true;
				}
				
				// Create the variable
				Variable var = new Variable(varName, varType);
				
				StringStartEnd assignmentInfo = ParseUtil.getFirstAssignmentOperator(line, varEndIndex);
				String operator = null;
				int operatorEndIndex;
				String expressionContent = null;
				
				// There may not be any assignment on this line (only declaration)
				if (assignmentInfo != null) {
					operator = assignmentInfo.string;
					operatorEndIndex = assignmentInfo.endIndex;
					expressionContent = line.substring(operatorEndIndex).trim();
				}
				
				// If this is an scope declaration only (no value assigned, and no allocation)
				if (assignmentInfo == null) {
					// Make sure there aren't excess characters at the end of this line
					if (ParseUtil.checkForExcessCharacters(line, varName)) {
						return true;
					}
					
					Instruction instr = new Instruction(InstructionType.DeclareScope);
					instr.stringRepresentation = varType + " " + varName;
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
					instr.setArgs(lastInstruction);
					instr.variableThatWasChanged = var;
					instr.parentInstruction = parentInstruction;
					
					instructions.add(instr);
				} else {
					printError("Invalid assignment operator");
					return true;
				}
			}
		} else if (ParseUtil.findLowestPrecedenceOperatorAtLowestLevel(line, ParseUtil.assignmentOperators) != null) { // If this is some form of reassignment
			
			// Find the position of the assignment operator on this line
			StringStartEnd assignmentInfo = ParseUtil.findLowestPrecedenceOperatorAtLowestLevel(line, ParseUtil.assignmentOperators);
			
			if (assignmentInfo == null) {
				printError("Assignment expected");
				return true;
			}
			
			final String assignmentOp = assignmentInfo.string;
			final String leftHandString = line.substring(0, assignmentInfo.startIndex);
			final String rightHandString = line.substring(assignmentInfo.endIndex);
			
			Instruction lastInstructionFromRightHand = null;
			
			// Only parse out the right-hand side if non-empty
			if (!rightHandString.trim().isEmpty()) {
			
					// Parse out the value to assign to the variable
				boolean hadError = parseExpression(parentInstruction, rightHandString);
				if (hadError) {
					return true;
				}
				
				lastInstructionFromRightHand = instructions.get(instructions.size() - 1);
			}

			// Parse out the variable or object to assign to
			boolean hadError = parseExpression(parentInstruction, leftHandString);
			if (hadError) {
				return true;
			}
			
			Instruction lastInstructionFromLeftHand = instructions.get(instructions.size() - 1);
			
			// We expect the last instruction from the left-hand-side to read some variable
			if (lastInstructionFromLeftHand.variableThatWasRead == null) {
				printError("Left-hand side is not a variable or reference");
				return true;
			}
			
			// We expect the last instruction from the left-hand-side to be a "Read" operation
			if (lastInstructionFromLeftHand.instructionType != InstructionType.Read) {
				printError("(Internal) Left-hand-side should end in a 'Read' instruction");
				return true;
			}
			
			final Type leftHandType = lastInstructionFromLeftHand.returnType;
			
			// If this is an assignment (by value)
			if (assignmentOp.equals("=")) {
				
				final Type rightHandType = lastInstructionFromRightHand.returnType;
				
				// Check that we can put this type of value in this variable/struct
				if (!rightHandType.canImplicitlyCastTo(leftHandType)) {
					printError("Cannot implicitly cast from " + rightHandType + " to " + leftHandType);
					return true;
				}
				
				Instruction assignment = new Instruction(InstructionType.WriteToReference);
				assignment.stringRepresentation = line;
				assignment.setArgs(lastInstructionFromLeftHand, lastInstructionFromRightHand);
				assignment.parentInstruction = parentInstruction;
				instructions.add(assignment);
				
			} else if (assignmentOp.equals("++") || assignmentOp.equals("--")) { // Increment/Decrement
				
				if (!leftHandType.isNumberType()) {
					printError(assignmentOp + " can only be applied to a numeric type");
					return true;
				}
				
				if (!rightHandString.trim().isEmpty()) {
					printError(assignmentOp + " cannot be followed by an expression");
					return true;
				}
				
				InstructionType incrementType = null;
				if (assignmentOp.equals("++")) {
					incrementType = InstructionType.Add;
				} else if (assignmentOp.equals("--")) {
					incrementType = InstructionType.Sub;
				} else {
					printError("Unknown operator: " + assignmentOp);
					return true;
				}
				
				Instruction one = new Instruction(InstructionType.Given);
				one.stringRepresentation = "1";
				one.primitiveGivenValue = 1;
				one.returnType = Type.Int;
				one.parentInstruction = parentInstruction;
				instructions.add(one);
				
				Instruction addOrSubtract = new Instruction(incrementType);
				addOrSubtract.stringRepresentation = leftHandString;
				addOrSubtract.setArgs(lastInstructionFromLeftHand, one);
				addOrSubtract.returnType = getReturnTypeFromInstructionAndOperands(
							incrementType, lastInstructionFromLeftHand.returnType, one.returnType);
				addOrSubtract.parentInstruction = parentInstruction;
				instructions.add(addOrSubtract);
				
				Instruction writeToFirstHalf = new Instruction(InstructionType.WriteToReference);
				writeToFirstHalf.stringRepresentation = leftHandString;
				writeToFirstHalf.setArgs(lastInstructionFromLeftHand, addOrSubtract);
				writeToFirstHalf.parentInstruction = parentInstruction;
				instructions.add(writeToFirstHalf);
				
			} else if (assignmentOp.equals("+=")  || assignmentOp.equals("-=") || // If this is a shorthand operator
					   assignmentOp.equals("/=")  || assignmentOp.equals("*=") ||
					   assignmentOp.equals("^=")  || assignmentOp.equals("%=") ||
					   assignmentOp.equals("OR=") || assignmentOp.equals("AND=")) {
				
				final Type rightHandType = lastInstructionFromRightHand.returnType;
				
				// TODO need to do type-checking on all these arguments
				
				// TODO
				
				InstructionType shorthandInstructionType = getInstructionTypeFromOperator(assignmentOp);
				
				printError("Not implemented yet");
				return true;
				
			} else {
				printError("Invalid assignment operator");
				return true;
			}
			/*
			Object[] varData = ParseUtil.getVariableName(line, 0);
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
			Object[] arrayIndexData = ParseUtil.getArrayIndexInfo(line, varEndIndex);
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
			StringStartEnd assignmentInfo = ParseUtil.getFirstAssignmentOperator(line, varEndIndex);
			if (assignmentInfo == null) {
				printError("Missing assignment");
				return true;
			}
			String operator = assignmentInfo.string;
			int operatorEndIndex = assignmentInfo.endIndex;
			
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
					Instruction[] args = new Instruction[arrayIndices.length];
					for (int i = 0; i < arrayIndices.length; i++) {
						args[i] = arrayIndices[i];
					}
					instr.setArgs(args);
				}
				instr.stringRepresentation = expressionContent;
				instr.variableThatWasChanged = var;
				instr.parentInstruction = parentInstruction;
				
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
				if (hadError) {
					return true;
				}
			} else {
				printError("Invalid assignment operator");
				return true;
			}
			*/
		} else if (ParseUtil.isFunctionCall(line)) { // If this is a function call alone on a line
			
			String[] data = ParseUtil.getFunctionNameAndArgs(line);
			String methodName = null;
			String methodArgString = null;
			if (data != null) {
				methodName = data[0];
				methodArgString = data[1];
			}
			
			// Make sure there aren't excess characters at the end of this line
			if (ParseUtil.checkForExcessCharacters(line, methodArgString)) {
				return true;
			}
			
			String[] args = ParseUtil.separateArguments(methodArgString);
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
				instr.setArgs(lastInstruction);
				instr.parentInstruction = parentInstruction;
				instructions.add(instr);

			} else { // This must be a user-defined function
				Instruction instr = new Instruction(InstructionType.Call);
				instr.stringRepresentation = line;
				instr.routineName = methodName;
				
				// Parse each of the arguments to this function
				Instruction[] argInstructions = new Instruction[args.length];
				for (int i = 0; i < args.length; i++) {
					// Recursively parse the expressions
					boolean hadError = parseExpression(parentInstruction, args[i]);
					if (hadError) {
						return true;
					}
					
					Instruction lastInstruction = instructions.get(instructions.size()-1);
					argInstructions[i] = lastInstruction;
				}
				instr.setArgs(argInstructions);
				
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
		text = ParseUtil.removeUnnecessaryParentheses(text.trim());
		
		// TODO test function scope rules
		// TODO parse functions inside expressions
		
		if (text.isEmpty()) {
			printError("Empty expression encountered (value expected)");
			return true;
		}
		
		// If this contains a binary operator (on this level)
		StringStartEnd operatorInfo = ParseUtil.findLowestPrecedenceOperatorAtLowestLevel(text, ParseUtil.binaryOperators);
		if (operatorInfo != null) {
			String op = operatorInfo.string;
			int start = operatorInfo.startIndex;
			int end = operatorInfo.endIndex;
			
			String firstHalf = text.substring(0, start).trim();
			String lastHalf = text.substring(end).trim();
			
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
			instr.setArgs(lastInstruction1, lastInstruction2);
			instr.returnType = returnType;
			instr.parentInstruction = parentInstruction;
			
			// Add all of the new instruction
			instructions.add(instr);
		} else { // There are no more binary operators in this expression
			
			// If it's an invert, then record that instruction
			if (text.charAt(0) == '!') {
				String content = ParseUtil.getUnaryFunctionArgument(text, 0);

				// Recursively parse the expressions
				boolean hadError = parseExpression(parentInstruction, content);
				if (hadError) {
					return true;
				}
				
				Instruction lastInstruction = instructions.get(instructions.size()-1);
				
				Instruction instr = new Instruction(InstructionType.Not);
				instr.stringRepresentation = content;
				instr.setArgs(lastInstruction);
				instr.returnType = getReturnTypeFromInstructionAndOperands(instr.instructionType, Type.Bool, null);
				if (instr.returnType == null) {
					return true;
				}
				instr.parentInstruction = parentInstruction;
				
				// Add the new instruction
				instructions.add(instr);
			} else if (text.charAt(0) == '#') {
				
				String expressionContent = ParseUtil.getUnaryFunctionArgument(text, 0);

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
				
				Instruction instr = new Instruction(InstructionType.ArrayLength);
				instr.stringRepresentation = "#" + expressionContent;
				instr.setArgs(lastInstruction);
				instr.returnType = getReturnTypeFromInstructionAndOperands(
						instr.instructionType, operandType, null);
				instr.parentInstruction = parentInstruction;
				instructions.add(instr);
				
			} else {
				
				// If this is a signed integer
				if (ParseUtil.isSignedInteger(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = ParseUtil.parseInt(text);
					instr.returnType = Type.Int;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (ParseUtil.isSignedLong(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = ParseUtil.parseLong(text);
					instr.returnType = Type.Long;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (ParseUtil.isDouble(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = ParseUtil.parseDouble(text);
					instr.returnType = Type.Double;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (ParseUtil.isFloat(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = ParseUtil.parseFloat(text);
					instr.returnType = Type.Float;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (ParseUtil.isBool(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = ParseUtil.parseBool(text);
					instr.returnType = Type.Bool;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (ParseUtil.isString(text)) {
					Instruction instr = new Instruction(InstructionType.Given);
					instr.stringRepresentation = text;
					instr.primitiveGivenValue = text;
					instr.returnType = Type.String;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
				} else if (ParseUtil.isArrayDefinition(text)) {
					Object[] arrayData = ParseUtil.getArrayDefinitionInfo(text);
					if (arrayData == null) {
						printError("Malformed array definition in '" + text + "'");
						return true;
					}
					Type arrayType = (Type)arrayData[0];
					String[] dimensions = (String[])arrayData[1];
					
					Instruction instr = new Instruction(InstructionType.Alloc);
					instr.stringRepresentation = text;
					
					// Parse each of the arguments to the array dimension
					Instruction[] args = new Instruction[dimensions.length];
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
						
						args[i] = lastInstruction;
					}
					instr.setArgs(args);
					
					instr.returnType = arrayType;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
					
				} else if (ParseUtil.isArrayReference(text)) {
					Object[] arrayData = ParseUtil.getArrayReadInfo(text);
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
					boolean foundLastWriteInstruction = wasAssignmentGuaranteed(instr, var);
					if (!foundLastWriteInstruction) {
						printError("Array '" + var + "' was never initialized");
						return true;
					}
					
					// Parse each of the arguments to the array index
					Instruction[] args = new Instruction[dimensions.length];
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
						
						args[i] = lastInstruction;
					}
					
					instr.setArgs(args);
					
					instructions.add(instr);
					
				} else {
					// Check if this is a recognized variable
					Variable var = findVariableByName(parentInstruction, text);
					if (var == null) {
						printError("Undeclared variable '" + text + "'");
						return true;
					}
					
					// If this variable is a primitive type
					Instruction instr;
					if (var.type.isPrimitiveType()) {
						instr = new Instruction(InstructionType.Read);
					} else { // If it is an array, object, or structure type
						instr = new Instruction(InstructionType.ReadBuiltInProperty);
					}
					
					instr.stringRepresentation = text;
					instr.variableThatWasRead = var;
					instr.returnType = var.type;
					instr.parentInstruction = parentInstruction;
					
					// Find the all previous instructions that assigned this variable, and reference them
					boolean foundLastWriteInstruction = wasAssignmentGuaranteed(instr, var);
					if (!foundLastWriteInstruction) {
						printError("Variable '" + var + "' was never initialized");
						return true;
					}
					
					instructions.add(instr);
				}
			}
		}
		
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
		} else if (type == InstructionType.ArrayLength) {
			if (operandType2 != null) {
				new Exception(InstructionType.ArrayLength + " second arg must be null");
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
	
	// Determine which function declaration each function call was referencing
	static boolean findFunctionCallReferences() {
		
		// Search through each function call
		for (int i = 0; i < instructions.size(); i++) {
			Instruction instr = instructions.get(i);
			
			// If this is a function call
			if (instr.instructionType == InstructionType.Call) {
				
				Instruction functionRef = findFunctionByNameAndArgs(instr);
				
				if (functionRef == null) {
					return true;
				}
				
				instr.callFunctionReference = functionRef;
			}
		}
		
		return false;
	}
	
	// Return true if the given function was previously declared in the given scope
	static boolean findConflictingFunction(Instruction parentInstr, Function function) {
		
		// Iterate backward to find a function
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			InstructionType type = otherInstruction.instructionType;
			
			if (type == InstructionType.FunctionDefinition) { // If this is a routine
				
				// If this instruction defined a routine by the same name
				if (otherInstruction.functionThatWasDefined.name.equals(function.name)) {
					
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
					//    then they might be in conflict due to the global scope of functions.
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
						if (!otherInstruction.functionThatWasDefined.isDistinguisable(function)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	// Return the instruction that declared the routine of the given name and argument types
	static Instruction findFunctionByNameAndArgs(Instruction instr) {
		
		Type[] argTypes = new Type[instr.argReferences.length];
		for (int j = 0; j < argTypes.length; j++) {
			argTypes[j] = instr.argReferences[j].returnType;
		}
		
		Instruction parentInstr = instr.parentInstruction;
		String routineName = instr.routineName;
		
		// If we found a matching routine that required implicit casts to call, then record it just in case.
		Instruction implicitMatch = null;
		
		// Only used for error handling
		Function matchingNameRoutine = null;
		
		// Iterate backward to find a routine
		outerLoop:
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			
			// If this is a routine definition
			if (otherInstruction.instructionType == InstructionType.FunctionDefinition) {
				
				// If this instruction defined a routine by the same name
				if (otherInstruction.functionThatWasDefined.name.equals(routineName)) {
					matchingNameRoutine = otherInstruction.functionThatWasDefined;
					
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
					//    then it might be in scope due to the global scope of functions.
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
						Type[] otherArgTypes = otherInstruction.functionThatWasDefined.argTypes;
						
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
			
			printError("Function " + matchingNameRoutine.name + "(" + otherArgsString + ") cannot take arguments "
						+ "(" + argsString + ")");
			return null;
		}
		
		printError("Function " + routineName + "(" + argsString + ") has not been declared");
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
	static boolean wasAssignmentGuaranteed(Instruction instr, Variable var) {
		
		// Iterate backward to find the assignment of this variable
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			InstructionType type = otherInstruction.instructionType;
			
			if (type == InstructionType.AllocAndAssign || type == InstructionType.Reassign) {
				
				// If this instruction wrote to the given variable
				if (otherInstruction.variableThatWasChanged == var) {
					
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
					if (wasAssignmentGuaranteedHelper(i, instructions.size(), var, instr.parentInstruction)) {
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
	static boolean wasAssignmentGuaranteedHelper(int startIndex, int stopIndex,
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
					if (!wasAssignmentGuaranteedHelper(startInstructionIndex,
							endInstructionIndex, var, currentIf)) {
						return false;
					}
				} while (currentIf.instructionType != InstructionType.Else);
			}
			
			// This is a routine, so we can't be guaranteed that it will execute
			if (currentParent.instructionType == InstructionType.FunctionDefinition) {
				return false;
			}
			
			// We are still safe, so check the next parent now
			currentInstructionIndex = instructions.indexOf(currentParent);
			currentParent = currentParent.parentInstruction;
		} 
		
		return true;
	}
	
	// Return the previous instruction that opened the scope of the given instruction
	static Instruction findParentInstruction(int instructionIndex) {
		
		int currentScopeDepth = 1;
		
		for (int i = instructionIndex; i >= 0; i--) {
			
			Instruction instr = instructions.get(i);
			if (instr.instructionType.doesStartScope()) {
				currentScopeDepth--;
			}
			
			if (currentScopeDepth == 0) {
				return instr;
			}
			
			// It is possible for an instruction to both increase and decrease scope (such as ElseIf)
			if (instr.instructionType.doesEndScope()) {
				currentScopeDepth++;
			}
		}
		
		return null;
	}
	
	// Return the closest ancestor instruction that is a Loop instruction, or null if none is found
	static Instruction findNearestAncestorLoop(Instruction parent) {
		while (parent != null && parent.instructionType != InstructionType.Loop) {
			// Loops and conditional structures may not contain a routine
			if (parent.instructionType == InstructionType.FunctionDefinition) {
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
			QuadInstruction newQuadInstruction = instructions.get(i).toQuadIR();
			if (newQuadInstruction != null) {
				quadInstructions.add(newQuadInstruction);
			}
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

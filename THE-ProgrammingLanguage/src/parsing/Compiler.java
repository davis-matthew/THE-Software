package parsing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import passes.ArrayWritesToRefPass;
import passes.ConvertToQuadPass;

import static parsing.ErrorHandler.*;

// Created by Daniel Williams
// Created on May 31, 2020
// Last updated on June 20, 2024

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
	
	static ArrayList<Function> functions;
	
	// Whether to view debug printing or not
	static final boolean debugPrintOn = true;
	
	public static void main(String[] args) {
		
		String text = loadFile(fileToRead);
		lines = ParseUtil.breakIntoLines(text);
		lines = ParseUtil.removeWhiteSpace(lines);
		lines = ParseUtil.stripComments(lines);
		
		// Estimate the number of instructions in this program
		instructions = new ArrayList<Instruction>();
		functions = new ArrayList<Function>();
		
		// Find all functions defined in this file (and put them in 'functions' ArrayList)
		currentParsingLineNumber = 0;
		findAllDeclaredFunctions();
		
		// Parse all the lines in the program
		currentParsingLineNumber = 0;
		for (int i = 0; i < lines.length; i++) {
			parseLine(lines[i]);
			currentParsingLineNumber++;
		}
		
		// Check for invalid scope
		if (lines.length > currentParsingLineNumber) {
			printError("Extra ']' in program");
		}
		
		// Replace all Reassign-to-arrays to WriteToReferences
		ArrayWritesToRefPass.convertReferenceWritesPass(instructions);
		
		// Print out all of the instructions to the console
		for (int i = 0; i < instructions.size(); i++) {
			print(instructions.get(i));
		}
		print("");

		// Convert all instructions to QuadIR
		ArrayList<QuadInstruction> quadInstructions = ConvertToQuadPass.convertToQuadInstructions(instructions);
		
		print("Quad instructions:");
		for (int i = 0; i < quadInstructions.size(); i++) {
			print(quadInstructions.get(i));
		}
		print("");
		
		//saveFile(fileToWrite, text);
	}
	
	// Parse a single line of code.
	// Return the last instruction that was created from parsing the given line.
	static Instruction parseLine(String line) {
		
		final Instruction parentInstruction = findParentInstruction(instructions.size() - 1);
		final int previousInstructionsLength = instructions.size();
		
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
			}
			
			instr.setArgs(parentLoop);
			
			instructions.add(instr);
			
		} else if (line.equals("[")) { // Start a deeper scope
			
			Instruction instr = new Instruction(InstructionType.StartBlock);
			instr.stringRepresentation = "scope start";
			instr.parentInstruction = parentInstruction;
			instructions.add(instr);
			
		} else if (ParseUtil.doesLineStartWith(line, "for")) { // For-loop
			
			// This instruction restricts the scope of the whole for-loop and initialization
			Instruction startBlockInstr = new Instruction(InstructionType.StartBlock);
			startBlockInstr.stringRepresentation = "for-loop scope start";
			startBlockInstr.parentInstruction = parentInstruction;
			instructions.add(startBlockInstr);
			
			// Get the contents of the for-loop header
			final int conditionsStartIndex = 3;
			String expressionContent = line.substring(conditionsStartIndex);
			String[] args = ParseUtil.separateArguments(expressionContent);
			
			// Create the increment, start bound, and stop bound instructions
			Variable arrayVar;
			String startBoundVariableString;
			String loopBreakIfStatementString;
			String incrementString;
			String forEachAssignment = null; // Used only for for-each loop
			
			if (args.length == 0) { // Invalid
				printError("Missing arguments in For-loop header");
				return null;
			} else if (args.length == 1) { // For-each loop
				
				// The "in" keyword is needed in here
				if (args[0].indexOf(" in") == -1) {
					printError("For-each loop requires 'in' keyword");
				}
				
				String[] forEachArgs = args[0].split(" in ");
				if (forEachArgs.length < 2 || forEachArgs[1].trim().isEmpty()) {
					printError("For-each loop missing arguments after 'in' keyword");
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
				}
				
				String forEachVarTypeString = loopVarDeclarationString.substring(0, firstSpaceIndex);
				Type forEachVarType = new Type(forEachVarTypeString);
				String forEachVarName = loopVarDeclarationString.substring(firstSpaceIndex).trim();
				
				// Try to find an existing variable in this scope with the same name
				Variable existingVar = findVariableByName(startBlockInstr, forEachVarName);
				if (existingVar != null) {
					printError("Variable '" + forEachVarName + "' has already been declared in this scope");
				}
				
				// Try to find an existing variable in this scope with the same name
				String arrayVarName = forEachArgs[1];
				arrayVar = findVariableByName(startBlockInstr, arrayVarName);
				
				// Make sure the array variable is an array type
				if (!arrayVar.type.isArray) {
					printError("'" + arrayVarName + "' must be an array type");
				}
				
				// Make sure the array values may be cast to the reference variable type
				if (!arrayVar.type.getArrayContainedType().canImplicitlyCastTo(forEachVarType)) {
					printError("Cannot implicitly cast from " + arrayVar.type.baseType + " to " + forEachVarType);
				}
				
				loopBreakIfStatementString = "if " + loopVarName + " >= #" + arrayVarName;
				forEachAssignment = loopVarDeclarationString + " = " + arrayVarName + "[" + loopVarName + "]";
				
			} else if (args.length == 2 || args.length == 3) { // Indexed for-loop
				
				// Get the assignment string (i.e. "int i = 0")
				startBoundVariableString = args[0].trim();
				
				int firstSpaceIndex = startBoundVariableString.indexOf(' ');
				if (firstSpaceIndex == -1) {
					printError("For-loop variable type missing");
				}

				// Get the type of variable that is being used to iterate
				String loopVarTypeString = startBoundVariableString.substring(0, firstSpaceIndex);
				Type varType = new Type(loopVarTypeString);
				
				// Make sure the loop variable is an integer type
				if (!varType.isNumberType()) {
					printError("For-loop variable must be a numeric type");
				}
				
				int equalSignIndex = startBoundVariableString.indexOf('=');
				if (equalSignIndex == -1) {
					printError("Assignment must be made in For-loop variable");
				}
				
				final String loopVarName = startBoundVariableString.substring(firstSpaceIndex, equalSignIndex).trim();
				
				// Break the loop if this condition is true
				final String startBoundExpression = args[0].substring(equalSignIndex + 1).trim();
				final String stopBoundExpression = args[1].trim();
				loopBreakIfStatementString = "if (" + loopVarName + " >= (" + stopBoundExpression + ")) " +
												"|| (" + loopVarName + " < (" + startBoundExpression + "))";
				
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
				return null;
			}
			
			// Parse all the instructions for the initial value of the loop index variable
			parseLine(startBoundVariableString);
			
			// Create the looping construct.
			// This instruction precedes all content of the loop that is repeated.
			Instruction loopStartLabel = new Instruction(InstructionType.Loop);
			loopStartLabel.stringRepresentation = "for-loop start";
			loopStartLabel.parentInstruction = startBlockInstr;
			instructions.add(loopStartLabel);
			
			// Create the loop break condition.
			// Parse all the instructions to test whether we need to break this loop.
			// This will return a reference to the if-statement.
			Instruction lastInstructionFromIfCondition = parseLine(loopBreakIfStatementString);
			
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
				parseLine(forEachAssignment);
			}
			
			// This code will be injected when the end-of-block is reached.
			loopStartLabel.codeToInjectAtEndOfBlock = incrementString;
			
		} else if (line.equals("do")) { // Do-while loop (header part)
			
			// Create the loop header instruction (generic for all loops)
			Instruction instr = new Instruction(InstructionType.Loop);
			instr.stringRepresentation = "do loop start";
			instr.wasThisADoWhileLoop = true;
			instr.parentInstruction = parentInstruction;
			instructions.add(instr);
			
		} else if (ParseUtil.doesLineStartWith(line, "] while")) { // Do-while loop (end part)

			Instruction doStartInstruction = parentInstruction;
			
			// Check that this block was opened by a do-while loop header
			if (doStartInstruction.instructionType != InstructionType.Loop ||
					!doStartInstruction.wasThisADoWhileLoop) {
				printError("Do-While footer must be preceded by a Do-While header");
			}
			
			// Get the contents of the conditional
			int conditionStartIndex = 7;
			String expressionContent = line.substring(conditionStartIndex);
			
			// Detect an empty conditional statement
			if (expressionContent.trim().isEmpty()) {
				printError("Boolean expression missing in Do-While loop footer");
			}
			
			// Get the instructions for the content of this assignment
			Instruction lastInstruction = parseExpression(parentInstruction, expressionContent);
			
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
			}
			
			// Get the instructions for the content of this assignment
			Instruction lastInstruction = parseExpression(loopStartLabel, expressionContent);
			
			// Make sure the return type of the "While" condition is a boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
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
			}
			
			// Get the instructions for the content of this assignment
			Instruction lastInstruction = parseExpression(parentInstruction, expressionContent);
			
			// Make sure the return type of an "if" condition is a boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
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
			Instruction openingBlockInstr = parentInstruction;
			
			// If the opening to this block was a do-while loop, then we're missing the "while (condition)" part
			if (openingBlockInstr.wasThisADoWhileLoop) {
				printError("Do-While must be closed with 'while <condition>'.");
			}
			
			// If the opening block has some code that needs to be injected before the closing block,
			// then parse and add it here.
			if (openingBlockInstr.codeToInjectAtEndOfBlock != null) {
				// Parse the line that increments the loop variable
				parseLine(openingBlockInstr.codeToInjectAtEndOfBlock);
				
				// No longer needed.
				openingBlockInstr.codeToInjectAtEndOfBlock = null;
			}
			
			// Create the EndBlock to end this else-statement
			Instruction endInstr = new Instruction(InstructionType.EndBlock);
			endInstr.stringRepresentation = "end " + openingBlockInstr.stringRepresentation;
			endInstr.parentInstruction = openingBlockInstr;
			instructions.add(endInstr);
			
			openingBlockInstr.endInstruction = endInstr;
			
		} else if (ParseUtil.getFirstDataType(line) != null) { // If this is a declaration of some sort
			
			TypeAndEnd typeData = ParseUtil.getFirstDataType(line);
			Type varType = typeData.type;
			int typeEndIndex = typeData.endIndex;
			
			// Make sure there is a space after the variable type
			if (line.charAt(typeEndIndex) != ' ') {
				printError("Declaration name missing");
			}
			
			// Get the first variable name on this line at this index, and its end index
			Object[] varData = ParseUtil.getVariableName(line, typeEndIndex+1);
			if (varData == null) {
				printError("Declaration name missing");
			}
			String varName = (String)varData[0];
			int varEndIndex = (int)varData[1];
			
			// Determine whether this is a variable, or function declaration
			boolean isFunctionDeclaration = false;
			if (varEndIndex < line.length() && line.charAt(varEndIndex) == '(') {
				isFunctionDeclaration = true;
			}
			
			if (isFunctionDeclaration) {
				
				// Cannot define a function inside anything except another function or main program
				if (parentInstruction != null &&
						parentInstruction.instructionType != InstructionType.FunctionDefinition) {
					printError("Functions may only be defined inside other functions or main program");
				}
				
				// Parse the types of the parameters to this function
				String paramString = ParseUtil.getFunctionArguments(line, typeEndIndex);
				String[] params = ParseUtil.separateArguments(paramString);
				Type[] paramTypes = new Type[params.length];
				String[] paramNames = new String[params.length];
				
				for (int i = 0; i < params.length; i++) {
					TypeAndEnd data = ParseUtil.getFirstDataType(params[i]);
					if (data != null) {
						paramTypes[i] = data.type;
						int endIndex = data.endIndex;
						paramNames[i] = params[i].substring(endIndex).trim();
					} else {
						printError("Invalid parameter declaration or type in '" + params[i] + "'");
					}
				}
				
				boolean foundDuplicates = ParseUtil.checkForDuplicates(paramNames);
				if (foundDuplicates) {
					printError("Duplicate parameter name in function header");
				}
				
				// Create the routine signature
				Function function = null; // TODO find a reference to the original function from the list
				
				// Try to find an existing variable in this scope that is in naming conflict with this one
				boolean foundConflict = findConflictingFunction(parentInstruction, function);
				if (foundConflict) {
					printError("Function '" + varName + "' has already been declared in this scope");
				}
				
				// Create the routine definition
				Instruction routineInstr = new Instruction(InstructionType.FunctionDefinition);
				routineInstr.stringRepresentation = line;
				routineInstr.functionThatWasDefined = function;
				routineInstr.parentInstruction = parentInstruction;
				routineInstr.returnType = varType; // TODO add multiple returns
				instructions.add(routineInstr);
				
				Instruction[] args = new Instruction[params.length];
				for (int i = 0; i < params.length; i++) {
					
					// Create the parameter variable
					Variable var = new Variable(paramNames[i], paramTypes[i]);
					
					Instruction instr = new Instruction(InstructionType.Declare);
					instr.stringRepresentation = var.type + " " + var.name;
					instr.variableThatWasChanged = var;
					instr.returnType = var.type;
					instr.parentInstruction = routineInstr;
					instructions.add(instr);
					
					args[i] = instr;
				}
				routineInstr.setArgs(args);
				
			} else { // Not a function declaration
				
				// Try to find an existing variable in this scope that is in naming conflict with this one
				Variable existingVar = findVariableByName(parentInstruction, varName);
				if (existingVar != null) {
					printError("Variable '" + varName + "' has already been declared in this scope");
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
					ParseUtil.checkForExcessCharacters(line, varName);
					
					Instruction instr = new Instruction(InstructionType.Declare);
					instr.stringRepresentation = varType + " " + varName;
					instr.variableThatWasChanged = var;
					instr.parentInstruction = parentInstruction;
					instructions.add(instr);
					
				} else if (operator.equals("=")) { // If this is an allocation and assignment
					
					// Get the instructions for the content of this assignment
					Instruction lastInstruction = parseExpression(parentInstruction, expressionContent);
					
					// Get the operand type
					Type operandType = lastInstruction.returnType;
					if (!operandType.canImplicitlyCastTo(varType)) {
						printError("Cannot implicitly cast from " + operandType + " to " + varType);
					}
					
					// If this is an array, then make sure the dimension matches
					if (varType.isArray && varType.dimensions != lastInstruction.args.length) {
						printError("Unmatching array dimensions");
					}
					
					Instruction instr = new Instruction(InstructionType.Initialize);
					instr.stringRepresentation = varType + " " + varName + " = " + expressionContent;
					instr.setArgs(lastInstruction);
					instr.variableThatWasChanged = var;
					instr.parentInstruction = parentInstruction;
					
					instructions.add(instr);
				} else {
					printError("Invalid assignment operator");
				}
			}
		} else if (ParseUtil.findLowestPrecedenceOperatorAtLowestLevel(line, ParseUtil.assignmentOperators) != null) { // If this is some form of reassignment
			
			// Find the position of the assignment operator on this line
			StringStartEnd assignmentInfo = ParseUtil.findLowestPrecedenceOperatorAtLowestLevel(line, ParseUtil.assignmentOperators);
			
			if (assignmentInfo == null) {
				printError("Assignment expected");
			}
			
			final String assignmentOp = assignmentInfo.string;
			final String leftHandString = line.substring(0, assignmentInfo.startIndex).trim();
			final String rightHandString = line.substring(assignmentInfo.endIndex);
			
			Instruction lastInstructionFromRightHand = null;
			
			// Only parse out the right-hand side if non-empty
			if (!rightHandString.trim().isEmpty()) {
			
				// Parse out the value to assign to the variable
				lastInstructionFromRightHand = parseExpression(parentInstruction, rightHandString);
			}

			// Parse out the variable or object to assign to
			Instruction lastInstructionFromLeftHand = parseExpression(parentInstruction, leftHandString);
			
			// We expect the last instruction from the left-hand-side to read some variable
			if (lastInstructionFromLeftHand.variableThatWasRead == null) {
				printError("Left-hand side is not a variable or reference");
			}
			
			// We expect the last instruction from the left-hand-side to be a "Read" operation
			if (lastInstructionFromLeftHand.instructionType != InstructionType.Read) {
				printError("Left-hand-side should end in a 'Read' instruction " +
							"(not a " + lastInstructionFromLeftHand.instructionType + ")");
			}
			
			final Type leftHandType = lastInstructionFromLeftHand.returnType;
			
			// The last instruction was a Read, so get the variable that was read
			final Variable varToBeModified = lastInstructionFromLeftHand.variableThatWasRead;
			
			if (varToBeModified == null) {
				printError("Read instruction missing variable!");
			}
			
			// If this is an assignment (by value)
			if (assignmentOp.equals("=")) {
				
				final Type rightHandType = lastInstructionFromRightHand.returnType;
				
				// Check that we can put this type of value in this variable/struct
				if (!rightHandType.canImplicitlyCastTo(leftHandType)) {
					printError("Cannot implicitly cast from " + rightHandType + " to " + leftHandType);
				}
				
				// Since we are assigning over the old value without reading it, we can simply
				// delete the previous Read instruction that was created.
				if (instructions.get(instructions.size() - 1).instructionType != InstructionType.Read) {
					printError("Last generated instruction is expected to be a 'Read'");
				}
				instructions.remove(instructions.size() - 1);
				
				// If we are modifying a base type, such as an int or bool
				if (varToBeModified.type.isBaseType) {
					
					Instruction assignment = new Instruction(InstructionType.Reassign);
					assignment.stringRepresentation = line;
					assignment.variableThatWasChanged = varToBeModified;
					assignment.setArgs(lastInstructionFromRightHand);
					assignment.parentInstruction = parentInstruction;
					instructions.add(assignment);
					
				} else if (varToBeModified.type.isArray) { // Write to a value of an array
					
					Instruction assignment = new Instruction(InstructionType.Reassign);
					assignment.stringRepresentation = line;
					assignment.variableThatWasChanged = varToBeModified;
					Instruction[] args = new Instruction[lastInstructionFromLeftHand.args.length + 1];
					args[0] = lastInstructionFromRightHand;
					for (int i = 1; i < args.length; i++) {
						args[i] = lastInstructionFromLeftHand.args[i - 1];
					}
					assignment.setArgs(args);
					assignment.parentInstruction = parentInstruction;
					instructions.add(assignment);
					
				} else {
					printError("Cannot modify variable '" + varToBeModified.name + "' of type " + leftHandType + ".");
				}
				
			} else if (assignmentOp.equals("++") || assignmentOp.equals("--")) { // Increment/Decrement
				
				if (!leftHandType.isNumberType()) {
					printError(assignmentOp + " can only be applied to a numeric type");
				}
				
				if (!rightHandString.trim().isEmpty()) {
					printError(assignmentOp + " cannot be followed by an expression");
				}
				
				InstructionType incrementType = getInstructionTypeFromOperator(assignmentOp);
				
				Instruction one = new Instruction(InstructionType.Given);
				one.stringRepresentation = "1";
				one.primitiveGivenValue = 1;
				one.returnType = Type.Int;
				one.parentInstruction = parentInstruction;
				instructions.add(one);
				
				Instruction addOrSubtract = new Instruction(incrementType);
				addOrSubtract.stringRepresentation = leftHandString + " " + incrementType.toSymbolForm() + " 1";
				addOrSubtract.setArgs(lastInstructionFromLeftHand, one);
				addOrSubtract.returnType = getReturnTypeFromInstructionAndOperands(
							incrementType, leftHandType, one.returnType);
				addOrSubtract.parentInstruction = parentInstruction;
				instructions.add(addOrSubtract);
				
				// If we are modifying a base type, such as an int or bool
				if (varToBeModified.type.isBaseType) {
					
					Instruction writeToFirstHalf = new Instruction(InstructionType.Reassign);
					writeToFirstHalf.stringRepresentation = leftHandString.trim() + " = " +
										addOrSubtract.stringRepresentation;
					writeToFirstHalf.variableThatWasChanged = varToBeModified;
					writeToFirstHalf.setArgs(addOrSubtract);
					writeToFirstHalf.parentInstruction = parentInstruction;
					instructions.add(writeToFirstHalf);
					
				} else if (varToBeModified.type.isArray) { // Write to a value of an array
					
					Instruction writeToFirstHalf = new Instruction(InstructionType.Reassign);
					writeToFirstHalf.stringRepresentation = leftHandString.trim() + " = " +
										addOrSubtract.stringRepresentation;
					writeToFirstHalf.variableThatWasChanged = varToBeModified;
					Instruction[] args = new Instruction[lastInstructionFromLeftHand.args.length + 1];
					args[0] = addOrSubtract;
					for (int i = 1; i < args.length; i++) {
						args[i] = lastInstructionFromLeftHand.args[i - 1];
					}
					writeToFirstHalf.setArgs(args);
					writeToFirstHalf.parentInstruction = parentInstruction;
					instructions.add(writeToFirstHalf);
					
				} else {
					printError("Cannot modify variable '" + varToBeModified.name + "' of type " + leftHandType + ".");
				}
				
			} else if (assignmentOp.equals("+=")  || assignmentOp.equals("-=") || // If this is a shorthand operator
					   assignmentOp.equals("/=")  || assignmentOp.equals("*=") ||
					   assignmentOp.equals("^=")  || assignmentOp.equals("%=") ||
					   assignmentOp.equals("||=") || assignmentOp.equals("&&=") ||
					   assignmentOp.equals("|=")  || assignmentOp.equals("&=")) {
				
				if (rightHandString.trim().isEmpty()) {
					printError("Operator " + assignmentOp + " must be followed by an expression");
				}
				
				final Type rightHandType = lastInstructionFromRightHand.returnType;
				
				InstructionType incrementType = getInstructionTypeFromOperator(assignmentOp);
				
				Instruction binaryOp = new Instruction(incrementType);
				binaryOp.stringRepresentation = leftHandString + " " + incrementType.toSymbolForm() + rightHandString;
				binaryOp.setArgs(lastInstructionFromLeftHand, lastInstructionFromRightHand);
				binaryOp.returnType = getReturnTypeFromInstructionAndOperands(
									incrementType, leftHandType, rightHandType);
				binaryOp.parentInstruction = parentInstruction;
				instructions.add(binaryOp);
				
				// If we are modifying a base type, such as an int or bool
				if (varToBeModified.type.isBaseType) {
					
					Instruction writeToFirstHalf = new Instruction(InstructionType.Reassign);
					writeToFirstHalf.stringRepresentation = leftHandString.trim();
					writeToFirstHalf.variableThatWasChanged = varToBeModified;
					writeToFirstHalf.setArgs(binaryOp);
					writeToFirstHalf.parentInstruction = parentInstruction;
					instructions.add(writeToFirstHalf);
					
				} else if (varToBeModified.type.isArray) { // Write to a value of an array
					
					Instruction writeToFirstHalf = new Instruction(InstructionType.Reassign);
					writeToFirstHalf.stringRepresentation = leftHandString.trim();
					writeToFirstHalf.variableThatWasChanged = varToBeModified;
					Instruction[] args = new Instruction[lastInstructionFromLeftHand.args.length + 1];
					args[0] = binaryOp;
					for (int i = 1; i < args.length; i++) {
						args[i] = lastInstructionFromLeftHand.args[i - 1];
					}
					writeToFirstHalf.setArgs(args);
					writeToFirstHalf.parentInstruction = parentInstruction;
					instructions.add(writeToFirstHalf);
					
				} else {
					printError("Cannot modify variable '" + varToBeModified.name + "' of type " + leftHandType + ".");
				}
				
			} else {
				printError("Invalid assignment operator");
			}
			
		} else if (ParseUtil.isFunctionCall(line)) { // If this is a function call alone on a line
			
			parseFunctionCall(parentInstruction, line, true);
			
		} else {
			printError("Invalid line: " + line);
			return null;
		}
		
		// Return the last instruction added, if any.
		boolean didAddInstruction = instructions.size() > previousInstructionsLength;
		if (didAddInstruction) {
			return instructions.get(instructions.size() - 1);
		} else {
			return null; // No instruction was added.
		}
	}
	
	// Recursively parse an expression (no assignment allowed)
	// Return the last instruction created from parsing this expression.
	static Instruction parseExpression(Instruction parentInstruction, String text) {
		
		final int previousInstructionsLength = instructions.size();
		
		// Remove whitespace and extra parentheses around expressions
		text = ParseUtil.removeUnnecessaryParentheses(text.trim());
		
		// TODO test function scope rules
		
		if (text.isEmpty()) {
			printError("Empty expression encountered (value expected)");
			return null;
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
			Instruction lastInstruction1 = parseExpression(parentInstruction, firstHalf);
			Instruction lastInstruction2 = parseExpression(parentInstruction, lastHalf);
			
			// Get the types of each operand to this operator
			Type operandType1 = lastInstruction1.returnType;
			Type operandType2 = lastInstruction2.returnType;
			
			if (operandType1 == null) {
				printError("Expression returns nothing: '" + firstHalf + "'");
			}
			if (operandType2 == null) {
				printError("Expression returns nothing: '" + lastHalf + "'");
			}
			
			// Create the instruction for this binary operator
			InstructionType type = getInstructionTypeFromOperator(op);
			
			// Handle overloaded binary operators depending on operand type
			if (type == InstructionType.Add) {
				// This must be a string concatenation
				if (operandType1.isA(BaseType.String) || operandType2.isA(BaseType.String)) {
					type = InstructionType.Concat;
				}
			}
			
			Type returnType = getReturnTypeFromInstructionAndOperands(type, operandType1, operandType2);

			Instruction instr = new Instruction(type);
			instr.stringRepresentation = firstHalf + " " + op + " " + lastHalf;
			instr.setArgs(lastInstruction1, lastInstruction2);
			instr.returnType = returnType;
			instr.parentInstruction = parentInstruction;
			
			// Add all of the new instruction
			instructions.add(instr);
			
		} else { // There are no more binary operators in this expression
			
			final char opChar = text.charAt(0);
			
			// If it's an BitNot, Not, or ArrayLength, then add a unary instruction
			if (opChar == '!' || opChar == '~' || opChar == '#') {
				InstructionType instructionType = getInstructionTypeFromOperator(String.valueOf(opChar));
				
				String content = ParseUtil.getUnaryFunctionArgument(text, 0);

				// Recursively parse the expressions
				Instruction lastInstruction = parseExpression(parentInstruction, content);
				
				Instruction unaryInstr = new Instruction(instructionType);
				unaryInstr.stringRepresentation = content;
				unaryInstr.setArgs(lastInstruction);
				unaryInstr.returnType = getReturnTypeFromInstructionAndOperands(instructionType, lastInstruction.returnType, null);
				unaryInstr.parentInstruction = parentInstruction;
				instructions.add(unaryInstr);
				
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
					}
					Type arrayType = (Type)arrayData[0];
					String[] dimensions = (String[])arrayData[1];
					
					Instruction instr = new Instruction(InstructionType.Alloc);
					instr.stringRepresentation = text;
					
					// Parse each of the arguments to the array dimension
					Instruction[] args = new Instruction[dimensions.length];
					for (int i = 0; i < dimensions.length; i++) {
						
						// Recursively parse the expressions
						Instruction lastInstruction = parseExpression(parentInstruction, dimensions[i]);
						
						if (!lastInstruction.returnType.isA(BaseType.Int)) {
							printError("Array dimension must be of type " + Type.Int);
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
					}
					String arrName = (String)arrayData[0];
					String[] dimensions = (String[])arrayData[1];
					
					Variable var = findVariableByName(parentInstruction, arrName);
					if (var == null) {
						printError("Unknown array '" + arrName + "'");
					}
					
					// Make sure the number of dimensions accessed matches the dimension of the array
					if (var.type.dimensions != dimensions.length) {
						String plural = " index.";
						if (dimensions.length != 1) {
							plural = " indices.";
						}
						printError("'" + var.name + "' is " + var.type.dimensions + "-dimensional, but was accessed with " +
									dimensions.length + plural);
					}
					
					Instruction instr = new Instruction(InstructionType.Read);
					instr.stringRepresentation = text;
					instr.variableThatWasRead = var;
					instr.returnType = new Type(var.type.baseType);
					instr.parentInstruction = parentInstruction;
					
					// Find the all previous instructions that assigned this variable, and reference them
					boolean foundLastWriteInstruction = wasAssignmentGuaranteed(instr, var);
					if (!foundLastWriteInstruction) {
						printError("Array '" + var + "' was never initialized");
					}
					
					// Parse each of the arguments to the array index
					Instruction[] args = new Instruction[dimensions.length];
					for (int i = 0; i < dimensions.length; i++) {
						
						// Recursively parse the expressions
						Instruction lastInstruction = parseExpression(parentInstruction, dimensions[i]);
						
						if (!lastInstruction.returnType.isA(BaseType.Int)) {
							printError("Array dimension must be of type " + Type.Int);
						}
						
						args[i] = lastInstruction;
					}
					
					instr.setArgs(args);
					
					instructions.add(instr);
				
				} else if (text.indexOf('(') != -1 && text.indexOf(')') != -1) { // Function call
					
					parseFunctionCall(parentInstruction, text, false);
					
				} else { // This must be a variable, or garbage
				
					// Check if this is a recognized variable
					Variable var = findVariableByName(parentInstruction, text);
					if (var == null) {
						printError("Undeclared variable '" + text + "'");
					}
					
					Instruction instr;
					
					// If it is an array
					if (var.type.isArray) {
						instr = new Instruction(InstructionType.ReadBuiltInProperty);
					} else { // If this is a primitive type
						instr = new Instruction(InstructionType.Read);
					}
					
					instr.stringRepresentation = text;
					instr.variableThatWasRead = var;
					instr.returnType = var.type;
					instr.parentInstruction = parentInstruction;
					
					// Find the all previous instructions that assigned this variable, and reference them
					boolean foundLastWriteInstruction = wasAssignmentGuaranteed(instr, var);
					if (!foundLastWriteInstruction) {
						printError("Variable '" + var + "' was never initialized");
					}
					
					instructions.add(instr);
				}
			}
		}
		
		// Return the last instruction added, if any.
		boolean didAddInstruction = instructions.size() > previousInstructionsLength;
		if (didAddInstruction) {
			return instructions.get(instructions.size() - 1);
		} else {
			return null; // No instruction was added.
		}
	}
	
	// Parse the calling of a function.
	// 'text' should look like "myFunction(arg1, arg2, arg3)"
	static void parseFunctionCall(Instruction parentInstruction, final String text, final boolean isAloneOnALine) {
		
		String[] nameAndArgs = ParseUtil.getFunctionNameAndArgs(text);
		
		if (nameAndArgs == null) {
			printError("Cannot parse function call '" + text + "'");
		}
		
		final String functionName = nameAndArgs[0];
		final String argsString = nameAndArgs[1];
		
		// If this function is alone on a line, then make sure there are no characters after it
		if (isAloneOnALine) {
			ParseUtil.checkForExcessCharacters(text, argsString);
		}
		
		final String[] argStrings = ParseUtil.separateArguments(argsString);
		
		// If this is the built-in print function
		if (functionName.equals("print")) {
			
			if (argStrings.length != 1) {
				printError("print(args) expects one argument; Got " + argStrings.length + " arguments.");
			}
			
			Instruction lastInstruction = parseExpression(parentInstruction, argStrings[0]);
			
			Instruction instr = new Instruction(InstructionType.Print);
			instr.stringRepresentation = argStrings[0];
			instr.setArgs(lastInstruction);
			instr.parentInstruction = parentInstruction;
			instructions.add(instr);
			
		} else { // Some user-defined function
		
			// Recursively parse each function argument
			Instruction[] args = new Instruction[argStrings.length];
			for (int i = 0; i < argStrings.length; i++) {
				Instruction lastInstruction = parseExpression(parentInstruction, argStrings[i]);
				args[i] = lastInstruction;
				
				if (lastInstruction.returnType == null) {
					printError("Function argument must return a value");
				}
				
				if (lastInstruction.returnType.baseType == BaseType.Void) {
					printError("Cannot pass void into function");
				}
			}
			
			Function func = findFunctionByNameAndArgs(functionName, args);
			
			// Create the function Call instruction
			Instruction callInstr = new Instruction(InstructionType.Call);
			callInstr.stringRepresentation = text;
			callInstr.functionThatWasCalled = func;
			callInstr.returnType = func.returnTypes[0]; // TODO add multiple returns
			callInstr.setArgs(args);
			callInstr.parentInstruction = parentInstruction;
			instructions.add(callInstr);
		}
	}
	
	// Return the return data type for the given instruction type and operands
	static Type getReturnTypeFromInstructionAndOperands(InstructionType instrType, Type op1, Type op2) {
		
		if (instrType == InstructionType.Add ||
				instrType == InstructionType.Subtract ||
				instrType == InstructionType.Mult ||
				instrType == InstructionType.Divide ||
				instrType == InstructionType.Power) {
			
			if (op1.isArray || op1.isPointer()) {
				printError("Cannot perform " + instrType.name() + " on " + op1);
			}
			if (op2.isArray || op2.isPointer()) {
				printError("Cannot perform " + instrType.name() + " on " + op2);
			}
			
			if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Int) {
				return Type.Int;
			} else if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Long) {
				return Type.Long;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Int) {
				return Type.Long;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Long) {
				return Type.Long;
			} else if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Float) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.Int) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.Float) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Float) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.Long) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Double) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Int) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Double) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Long) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Double) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.Double) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Float) {
				return Type.Double;
			}
			
			if (instrType == InstructionType.Add) {
				printError("Addition cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.Subtract) {
				printError("Subtraction cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.Mult) {
				printError("Multiplication cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.Divide) {
				printError("Division cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.Power) {
				printError("Exponentiation cannot be performed on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
			
		} else if (instrType == InstructionType.Modulo) {
			
			if (op1.isArray || op1.isPointer()) {
				printError("Cannot perform " + instrType.name() + " on " + op1);
			}
			if (op2.isArray || op2.isPointer()) {
				printError("Cannot perform " + instrType.name() + " on " + op2);
			}
			
			if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Int) {
				return Type.Int;
			} else if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Long) {
				return Type.Int;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Int) {
				return Type.Long;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Long) {
				return Type.Long;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.Int) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.Float) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.Long) {
				return Type.Float;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Int) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Double) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Float) {
				return Type.Double;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.Long) {
				return Type.Double;
			} else {
				printError("Modulus cannot be performed on " + op1 + " and " + op2);
				return null;
			}
		} else if (instrType == InstructionType.And ||
				instrType == InstructionType.Or ||
				instrType == InstructionType.Not) {
			
			if (op1.baseType == BaseType.Bool && op2.baseType == BaseType.Bool) {
				return Type.Bool;
			}
			
			if (instrType == InstructionType.And) {
				printError("AND cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.Or) {
				printError("OR cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.Not) {
				printError("NOT cannot be performed on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (instrType == InstructionType.BitAnd ||
				instrType == InstructionType.BitOr ||
				instrType == InstructionType.BitNot) {
			
			if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Int) {
				return Type.Int;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Long) {
				return Type.Long;
			}
			
			if (instrType == InstructionType.BitAnd) {
				printError("Bitwise AND cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.BitOr) {
				printError("Bitwise OR cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.BitNot) {
				printError("Bitwise NOT cannot be performed on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (instrType == InstructionType.Concat) {
			if (op1.baseType == BaseType.String && op2.baseType == BaseType.Double) {
				return Type.String;
			} else if (op1.baseType == BaseType.Double && op2.baseType == BaseType.String) {
				return Type.String;
			} else if (op1.baseType == BaseType.String && op2.baseType == BaseType.Float) {
				return Type.String;
			} else if (op1.baseType == BaseType.Float && op2.baseType == BaseType.String) {
				return Type.String;
			} else if (op1.baseType == BaseType.String && op2.baseType == BaseType.Int) {
				return Type.String;
			} else if (op1.baseType == BaseType.Int && op2.baseType == BaseType.String) {
				return Type.String;
			} else if (op1.baseType == BaseType.String && op2.baseType == BaseType.Long) {
				return Type.String;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.String) {
				return Type.String;
			} else if (op1.baseType == BaseType.String && op2.baseType == BaseType.Bool) {
				return Type.String;
			} else if (op1.baseType == BaseType.Bool && op2.baseType == BaseType.String) {
				return Type.String;
			} else if (op1.baseType == BaseType.String && op2.baseType == BaseType.String) {
				return Type.String;
			} else {
				printError("Concatenation cannot be performed on " + op1 + " and " + op2);
				return null;
			}
		} else if (instrType == InstructionType.Equal || instrType == InstructionType.NotEqual) {
			if (op1.isNumberType() && op2.isNumberType()) {
				return Type.Bool;
			} else if (op1.baseType == BaseType.String && op2.baseType == BaseType.String) {
				return Type.Bool;
			} else if (op1.baseType == BaseType.Bool && op2.baseType == BaseType.Bool) {
				return Type.Bool;
			} else {
				printError("Equality cannot be tested on " + op1 + " and " + op2);
				return null;
			}
		} else if (instrType == InstructionType.RefEqual || instrType == InstructionType.RefNotEqual) {
			if (op1.baseType == BaseType.Void || op2.baseType == BaseType.Void) {
				printError("Reference equality cannot be tested on " + op1 + " and " + op2);
				return null;
			} else {
				return Type.Bool;
			}
		} else if (instrType == InstructionType.Less || instrType == InstructionType.Greater ||
					instrType == InstructionType.LessEqual || instrType == InstructionType.GreaterEqual) {
			if (op1.isNumberType() && op2.isNumberType()) {
				return Type.Bool;
			}
			
			if (instrType == InstructionType.Less) {
				printError("Less Than cannot be tested on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.Greater) {
				printError("Greater Than cannot be tested on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.LessEqual) {
				printError("Less or Equal To cannot be tested on " + op1 + " and " + op2);
				return null;
			} else if (instrType == InstructionType.GreaterEqual) {
				printError("Greater or Equal To cannot be tested on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (instrType == InstructionType.Print) {
			return Type.Void;
		} else if (instrType == InstructionType.ArrayLength) {
			if (op2 != null) {
				new Exception(InstructionType.ArrayLength + " second arg must be null");
			}
			if (op1.isArray) {
				return Type.Int;
			} else {
				printError("ArrayLength cannot be determined for type " + op1);
			}
		} else {
			printError("Invalid return type: " + instrType);
		}
		
		new Exception("This code should not be reached").printStackTrace();
		return null;
	}
	
	// Return the instruction type corresponding to the given binary operator
	static InstructionType getInstructionTypeFromOperator(String op) {
		if (op.equals("+") || op.equals("+=") || op.equals("++")) {
			return InstructionType.Add;
		} else if (op.equals("-") || op.equals("-=") || op.equals("--")) {
			return InstructionType.Subtract;
		} else if (op.equals("/") || op.equals("/=")) {
			return InstructionType.Divide;
		} else if (op.equals("*") || op.equals("*=")) {
			return InstructionType.Mult;
		} else if (op.equals("^") || op.equals("^=")) {
			return InstructionType.Power;
		} else if (op.equals("%") || op.equals("%=")) {
			return InstructionType.Modulo;
		} else if (op.equals("=")) {
			return InstructionType.Equal;
		} else if (op.equals("!=")) {
			return InstructionType.NotEqual;
		} else if (op.equals("<")) {
			return InstructionType.Less;
		} else if (op.equals("@=")) {
			return InstructionType.RefEqual;
		} else if (op.equals("!@=")) {
			return InstructionType.RefNotEqual;
		} else if (op.equals(">")) {
			return InstructionType.Greater;
		} else if (op.equals("<=")) {
			return InstructionType.LessEqual;
		} else if (op.equals(">=")) {
			return InstructionType.GreaterEqual;
		} else if (op.equals("&&") || op.equals("&&=")) {
			return InstructionType.And;
		} else if (op.equals("||") || op.equals("||=")) {
			return InstructionType.Or;
		} else if (op.equals("&") || op.equals("&=")) {
			return InstructionType.BitAnd;
		} else if (op.equals("|") || op.equals("|=")) {
			return InstructionType.BitOr;
		} else if (op.equals("~")) {
			return InstructionType.BitNot;
		} else if (op.equals("#")) {
			return InstructionType.ArrayLength;
		} else {
			printError("Invalid operator: " + op);
			return null;
		}
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
	static Function findFunctionByNameAndArgs(String functionName, Instruction[] args) {
		
		Function bestMatchingFunction = null;
		int minimumImplicitCasts = Integer.MAX_VALUE;
		
		// For error printing only
		Function nameOnlyMatchingFunction = null;
		
		// Iterate backward to find a routine
		outerLoop:
		for (int i = 0; i < functions.size(); i++) {
			Function otherFunction = functions.get(i);
			
			// If they have matching names
			if (otherFunction.name.equals(functionName)) {
				
				nameOnlyMatchingFunction = otherFunction;
				
				// If they have matching parameters
				Type[] otherArgTypes = otherFunction.argTypes;
				
				// Make sure each of the arguments can be cast to the others
				if (otherArgTypes.length == args.length) {
					int implicitCastCount = 0;
					for (int j = 0; j < args.length; j++) {
						if (args[j].returnType == otherArgTypes[j]) {
							// Good exact match
						} else if (args[j].returnType.canImplicitlyCastTo(otherArgTypes[j])) {
							implicitCastCount++; // Match, but requiring cast
						} else {
							// Doesn't match this function.
							continue outerLoop; // continue searching for another routine
						}
					}
					
					// If we found a better matching function
					if (implicitCastCount < minimumImplicitCasts) {
						
						bestMatchingFunction = otherFunction;
						minimumImplicitCasts = implicitCastCount;
						
						// If we found another function that has the same name and same implicit casting,
						// then can't tell which function to call (ambiguous case).
					} else if (implicitCastCount == minimumImplicitCasts) {
						printError("Function '" + functionName +
								"' cannot be differentiated from function '" + otherFunction.name + "'");
					}
				}
			}
		}

		// If we found an implicit match, then that is good enough
		if (bestMatchingFunction != null) {
			return bestMatchingFunction;
		}
		
		// If we didn't find an exact match, then print an argument-mismatch error.
		if (nameOnlyMatchingFunction != null) {
			
			String argsString = "";
			for (int j = 0; j < args.length; j++) {
				argsString += args[j].returnType;
				if (j != args.length - 1) {
					argsString += ", ";
				}
			}
			
			Type[] otherArgTypes = nameOnlyMatchingFunction.argTypes;
			String otherArgsString = "";
			for (int j = 0; j < otherArgTypes.length; j++) {
				otherArgsString += otherArgTypes[j];
				if (j != otherArgTypes.length-1) {
					otherArgsString += ", ";
				}
			}
			
			printError("Function " + functionName + "(" + otherArgsString + ") cannot take arguments "
						+ "(" + argsString + ")");
			return null;
		}
		
		printError("Unknown function '" + functionName + "(...)'");
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
			if (type == InstructionType.Initialize || type == InstructionType.Read ||
					type == InstructionType.Declare) {
				
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
			
			if (type == InstructionType.Initialize || type == InstructionType.Reassign) {
				
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
			if (instructions.get(i).instructionType == InstructionType.Initialize ||
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
						if (currentInstr.args[0] == currentParent) {
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
	
	// Find every function declaration in the program, and add it to the list, 'functions'
	static void findAllDeclaredFunctions() {
		
		// Iterate over every line in the program
		for (int i = 0; i < lines.length; i++) {
			currentParsingLineNumber = i;
			final String line = lines[i];
			
			// Check if this is a function declaration,
			// and get the starting index of the function's name.
			final int nameStartIndex = ParseUtil.findFunctionDeclarationNameStartIndex(line);
			if (nameStartIndex != -1) {
				TypeAndEnd typeData = ParseUtil.getFirstDataType(line);
				
				String[] nameAndArgs = ParseUtil.getFunctionNameAndArgs(line);
				final String functionName = nameAndArgs[0];
				final String argsString = nameAndArgs[1];
				
				if (typeData == null) {
					String typeString = line.substring(0, nameStartIndex - 1).trim();
					printError("Invalid type '" + typeString + "' function declaration");
				}
				
				// Check for extraneous character after the end of the function declaration
				ParseUtil.checkForExcessCharacters(line, argsString);
				
				Type returnType = typeData.type;
				
				String[] argStrings = ParseUtil.separateArguments(argsString);
				Type[] argTypes = new Type[argStrings.length];
				for (int j = 0; j < argTypes.length; j++) {
					int firstSpaceIndex = argStrings[j].indexOf(' ');
					if (firstSpaceIndex == -1) {
						printError("Missing argument name or type in function declaration");
					}
					
					String typeString = argStrings[j].substring(0, firstSpaceIndex);
					argTypes[j] = new Type(typeString);
				}
				
				Type[] returnTypes = {returnType}; // TODO add multiple returns
				
				Function function = new Function(functionName, returnTypes, argTypes);
				
				functions.add(function);
			}
		}
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
	
	static void print(Object o) {
		if (debugPrintOn) {
			System.out.println(o);
		}
	}
}

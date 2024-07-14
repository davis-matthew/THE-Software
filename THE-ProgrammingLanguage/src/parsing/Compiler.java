package parsing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import instructions.AddInstr;
import instructions.AllocArrInstr;
import instructions.ArrLengthInstr;
import instructions.BitAndInstr;
import instructions.BitNotInstr;
import instructions.BitOrInstr;
import instructions.BoolAndInstr;
import instructions.BoolNotInstr;
import instructions.BoolOrInstr;
import instructions.BreakInstr;
import instructions.ConcatInstr;
import instructions.ContinueInstr;
import instructions.DeclareInstr;
import instructions.DivideInstr;
import instructions.ElseInstr;
import instructions.EndBlockInstr;
import instructions.EqualInstr;
import instructions.FunctionCallInstr;
import instructions.FunctionDefInstr;
import instructions.GetElementInstr;
import instructions.GetPointerInstr;
import instructions.GivenInstr;
import instructions.GreaterEqualInstr;
import instructions.GreaterInstr;
import instructions.IfInstr;
import instructions.Instruction;
import instructions.LessEqualInstr;
import instructions.LessInstr;
import instructions.LoadInstr;
import instructions.LoopInstr;
import instructions.ModuloInstr;
import instructions.MultInstr;
import instructions.NotEqualInstr;
import instructions.PowerInstr;
import instructions.PrintInstr;
import instructions.QuadInstruction;
import instructions.RefEqualInstr;
import instructions.RefNotEqualInstr;
import instructions.StartBlockInstr;
import instructions.StoreInstr;
import instructions.SubInstr;
import instructions.ToStringInstr;
import passes.ArrayWritesToRefPass;
import passes.ConvertToQuadPass;

import static parsing.ErrorHandler.*;

// Created by Daniel Williams
// Created on May 31, 2020
// Last updated on July 13, 2024

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
		

		// Print out all of the instructions to the console
		print("--------- Initial Parse ---------\n");
		for (int i = 0; i < instructions.size(); i++) {
			print(instructions.get(i));
		}
		print("");
		
		// Check for invalid scope
		if (lines.length > currentParsingLineNumber) {
			printError("Extra ']' in program");
		}
		
		// Replace all Store-to-arrays to WriteToReferences
		ArrayWritesToRefPass.convertReferenceWritesPass(instructions);
		
		// Print out all of the instructions to the console
		print("--------- After ArrayWritesToRefPass ---------\n");
		for (int i = 0; i < instructions.size(); i++) {
			print(instructions.get(i));
		}
		print("");

		// Convert all instructions to QuadIR
		ArrayList<QuadInstruction> quadInstructions = ConvertToQuadPass.convertToQuadInstructions(instructions);

		print("--------- Quad Instructions ---------\n");
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
			
			// Find the nearest ancestor loop that contains this statement
			LoopInstr parentLoop = findNearestAncestorLoop(parentInstruction);
			if (parentLoop == null) {
				printError(ParseUtil.capitalize(line) + "-statement must be within a loop");
			}
			
			if (line.equals("break")) {
				BreakInstr instr = new BreakInstr(parentInstruction, "break", parentLoop);
				instructions.add(instr);
			} else {
				ContinueInstr instr = new ContinueInstr(parentInstruction, "continue", parentLoop);
				instructions.add(instr);
			}
			
		} else if (line.equals("[")) { // Start a deeper scope
			
			StartBlockInstr startBlockInstr = new StartBlockInstr(parentInstruction, "scope start");
			instructions.add(startBlockInstr);
			
		} else if (ParseUtil.doesLineStartWith(line, "for")) { // For-loop
			
			// This instruction restricts the scope of the whole for-loop and initialization
			StartBlockInstr startBlockInstr = new StartBlockInstr(parentInstruction, "for-loop scope start");
			instructions.add(startBlockInstr);
			
			// Get the contents of the for-loop header
			final int conditionsStartIndex = 3;
			String expressionContent = line.substring(conditionsStartIndex);
			String[] args = ParseUtil.separateArguments(expressionContent);
			
			// Create the increment, start bound, and stop bound instructions
			Type arrayVarType;
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
				Instruction existingVar = findInstructionThatDeclaredVariable(startBlockInstr, forEachVarName);
				if (existingVar != null) {
					printError("Variable '" + forEachVarName + "' has already been declared in this scope");
				}
				
				// Try to find an existing variable in this scope with the same name
				String arrayVarName = forEachArgs[1];
				DeclareInstr arrayVarDeclare = findInstructionThatDeclaredVariable(startBlockInstr, arrayVarName);
				
				if (arrayVarDeclare == null) {
					printError("Variable " + arrayVarName + " has not been declared");
				}
				
				arrayVarType = arrayVarDeclare.varType;
				
				// Make sure the array variable is an array type
				if (!arrayVarType.isArray) {
					printError("'" + arrayVarName + "' must be an array type");
				}
				
				// Make sure the array values may be cast to the reference variable type
				if (!arrayVarType.getArrayElementType().canImplicitlyCastTo(forEachVarType)) {
					printError("Cannot implicitly cast from " + arrayVarType.baseType + " to " + forEachVarType);
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
			LoopInstr loopStartLabel = new LoopInstr(startBlockInstr, "for-loop start", false);
			instructions.add(loopStartLabel);
			
			// Create the loop break condition.
			// Parse all the instructions to test whether we need to break this loop.
			// This will return a reference to the if-statement.
			Instruction lastInstructionFromIfConditionAny = parseLine(loopBreakIfStatementString);
			
			// Make sure we parsed an if-condition
			if (!(lastInstructionFromIfConditionAny instanceof IfInstr)) {
				printError("Auto-generated break-statement must be contained inside If-statement for a loop");
			}
			
			IfInstr lastInstructionFromIfCondition = (IfInstr)lastInstructionFromIfConditionAny;
			
			BreakInstr boundBreak = new BreakInstr(lastInstructionFromIfCondition, "break for", loopStartLabel);
			instructions.add(boundBreak);
			
			EndBlockInstr ifBoundEnd = new EndBlockInstr(lastInstructionFromIfCondition, "end if");
			instructions.add(ifBoundEnd);
			
			lastInstructionFromIfCondition.endOfBlockInstr = ifBoundEnd;
			
			// Reassign the for-each loop reference variable, if this is a for-each loop
			if (forEachAssignment != null) {
				parseLine(forEachAssignment);
			}
			
			// This code will be injected when the end-of-block is reached.
			loopStartLabel.codeToInjectAtEndOfBlock = incrementString;
			
		} else if (line.equals("do")) { // Do-while loop (header part)
			
			// Create the loop header instruction (generic for all loops)
			LoopInstr instr = new LoopInstr(parentInstruction, "do loop start", true);
			instructions.add(instr);
			
		} else if (ParseUtil.doesLineStartWith(line, "] while")) { // Do-while loop (end part)
			
			// Check that this block was opened by a do-while loop header
			if (!(parentInstruction instanceof LoopInstr) ||
					!((LoopInstr)parentInstruction).wasThisADoWhileLoop) {
				printError("Do-While footer must be preceded by a Do-While header");
			}

			LoopInstr doStartInstruction = (LoopInstr)parentInstruction;
			
			// Get the contents of the conditional
			int conditionStartIndex = 7;
			String expressionContent = line.substring(conditionStartIndex).trim();
			
			// Detect an empty conditional statement
			if (expressionContent.isEmpty()) {
				printError("Boolean expression missing in Do-While loop footer");
			}
			
			// Get the instructions for the content of this assignment
			Instruction lastInstruction = parseExpression(parentInstruction, expressionContent);
			
			// Invert the truth of the last instruction in the break condition
			BoolNotInstr notInstr = new BoolNotInstr(doStartInstruction, "!(" + expressionContent + ")", lastInstruction);
			instructions.add(notInstr);
			
			// Build the if-statement for the while loop
			IfInstr ifInstr = new IfInstr(doStartInstruction, "if " + notInstr.debugString, notInstr, false);
			instructions.add(ifInstr);
			
			// Create a break statement for this while loop
			BreakInstr breakInstr = new BreakInstr(ifInstr, "break", doStartInstruction);
			instructions.add(breakInstr);
			
			// Create the EndBlock for this If-block
			EndBlockInstr endIf = new EndBlockInstr(ifInstr, "end if");
			instructions.add(endIf);
			
			ifInstr.endOfBlockInstr = endIf;
			
			// Make sure the return type of the condition is boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
			}
			
			EndBlockInstr doEndInstr = new EndBlockInstr(doStartInstruction, "end do-while");
			instructions.add(doEndInstr);
			
			// Mark this as the end of the Loop instruction
			doStartInstruction.endInstr = doEndInstr;
			
		} else if (ParseUtil.doesLineStartWith(line, "while")) { // While loop
			
			LoopInstr loopStartLabel = new LoopInstr(parentInstruction, "while loop start", false);
			instructions.add(loopStartLabel);
			
			// Get the contents of the conditional
			int conditionStartIndex = 5;
			String expressionContent = line.substring(conditionStartIndex).trim();
			
			// Detect an empty conditional statement
			if (expressionContent.isEmpty()) {
				printError("Boolean expression missing in While loop");
			}
			
			// Get the instructions for the content of this assignment
			Instruction lastInstruction = parseExpression(loopStartLabel, expressionContent);
			
			if (lastInstruction.returnType == null) {
				printError("Loop condition must return a " + Type.Bool);
			}
			
			// Make sure the return type of the "While" condition is a boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
			}
			
			// Invert the truth of the last instruction in the break condition
			BoolNotInstr notInstr = new BoolNotInstr(loopStartLabel, "!(" + expressionContent + ")", lastInstruction);
			instructions.add(notInstr);
			
			// Build the if-statement for the while loop
			IfInstr ifInstr = new IfInstr(loopStartLabel, "if !(" + expressionContent + ")", notInstr, false);
			instructions.add(ifInstr);
			
			// Create a break statement for this while loop
			BreakInstr breakInstr = new BreakInstr(ifInstr, "break while", loopStartLabel);
			instructions.add(breakInstr);
			
			// Create the EndBlock for this If-block
			EndBlockInstr endIf = new EndBlockInstr(ifInstr, "end if");
			instructions.add(endIf);
			
			ifInstr.endOfBlockInstr = endIf;
			
		} else if (ParseUtil.doesLineStartWith(line, "if")) { // If-statement
		
			// Get the contents of the conditional
			String expressionContent = line.substring("if".length()).trim();
			
			// Detect an empty conditional statement
			if (expressionContent.isEmpty()) {
				printError("Boolean expression missing in If-condition");
			}
			
			// Get the instructions for the content of this assignment
			Instruction lastInstruction = parseExpression(parentInstruction, expressionContent);
			
			// Make sure the return type of an "if" condition is a boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
			}
			
			IfInstr ifInstr = new IfInstr(parentInstruction, expressionContent, lastInstruction, false);
			instructions.add(ifInstr);
			
		} else if (ParseUtil.doesLineStartWith(line, "elseif")) { // ElseIf-statement
			
			if (!(parentInstruction instanceof IfInstr)) {
				printError("Else-if condition must be preceded by an if-block");
			}
			
			// Inject an end-block to close the if-statement first if this is an else-if statement.
			EndBlockInstr previousEndInstr = new EndBlockInstr(parentInstruction, "end " + parentInstruction.name());
			instructions.add(previousEndInstr);
			
			ElseInstr elseInstr = new ElseInstr(parentInstruction.parentInstruction, "", (IfInstr)parentInstruction);
			instructions.add(elseInstr);
			
			// Get the contents of the conditional
			String expressionContent = line.substring("elseif".length()).trim();
			
			// Detect an empty conditional statement
			if (expressionContent.isEmpty()) {
				printError("Boolean expression missing in elseif condition");
			}
			
			// Get the instructions for the content of this assignment
			Instruction lastInstruction = parseExpression(elseInstr, expressionContent);
			
			// Make sure the return type of an "if" condition is a boolean
			Type operandType = lastInstruction.returnType;
			if (!operandType.canImplicitlyCastTo(Type.Bool)) {
				printError("Cannot implicitly cast from " + operandType + " to " + Type.Bool);
			}
			
			IfInstr ifInstr = new IfInstr(elseInstr, "elseif " + expressionContent, lastInstruction, true);
			instructions.add(ifInstr);
			
			// Mark the end of the previous chaining instruction and
			// mark this as the next instruction in the chain from the previous.
			((IfInstr)parentInstruction).endOfBlockInstr = previousEndInstr;
			((IfInstr)parentInstruction).elseInstr = elseInstr;
			
		} else if (line.equals("else")) { // This is an else chained onto an if
			
			if (!(parentInstruction instanceof IfInstr)) {
				printError("Else block not preceded by If or Else-if block");
			}
			IfInstr ifInstr = (IfInstr)parentInstruction;
			
			// Create the EndBlock to end of the previous chained instruction
			EndBlockInstr previousEndInstr = new EndBlockInstr(
					ifInstr, "end " + ifInstr.name());
			instructions.add(previousEndInstr);
			
			// Create the Else block
			ElseInstr elseInstr = new ElseInstr(parentInstruction.parentInstruction, "else", ifInstr);
			instructions.add(elseInstr);
			
			// Mark the end of the previous chaining instruction and
			// mark this as the next instruction in the chain from the previous.
			ifInstr.endOfBlockInstr = previousEndInstr;
			ifInstr.elseInstr = elseInstr;
		
		} else if (line.equals("]")) { // Descope (for closing a loop, if, elseif, etc.)
			
			// Find the instruction that starts this scope-block
			Instruction openingBlockInstr = parentInstruction;
			
			// If the opening to this block was a do-while loop, then we're missing the "while (condition)" part
			if (openingBlockInstr instanceof LoopInstr) {
				LoopInstr loopInstr = (LoopInstr)openingBlockInstr;
				
				if (loopInstr.wasThisADoWhileLoop) {
					printError("Do-While must be closed with 'while <condition>'.");
				}
			
				// If the opening block has some code that needs to be injected before the closing block,
				// then parse and add it here.
				if (loopInstr.codeToInjectAtEndOfBlock != null) {
					// Parse the line that increments the loop variable
					parseLine(loopInstr.codeToInjectAtEndOfBlock);
					
					// No longer needed.
					loopInstr.codeToInjectAtEndOfBlock = null;
				}
			}
			
			// Create the EndBlock to end this else-statement
			EndBlockInstr endInstr = new EndBlockInstr(openingBlockInstr, "end " + openingBlockInstr.debugString);
			instructions.add(endInstr);
			
			// Mark a reference to the end of the loop
			if (openingBlockInstr instanceof LoopInstr) {
				((LoopInstr)openingBlockInstr).endInstr = endInstr;
			}
			if (openingBlockInstr instanceof IfInstr) {
				IfInstr ifInstr = (IfInstr)openingBlockInstr;
				ifInstr.endOfBlockInstr = endInstr;
				
				// If this was an auto-generated if-else block (converted from an else-if),
				// then we'll need to add an extra closing bracket and the end of the chain.
				if (ifInstr.wasThisAnElseIfBlock) {
					parseLine("]");
				}
			}
			if (openingBlockInstr instanceof ElseInstr) {
				ElseInstr elseInstr = (ElseInstr)openingBlockInstr;
				elseInstr.endOfBlockInstr = endInstr;
				
				// If this was an auto-generated if-else block (converted from an else-if),
				// then we'll need to add an extra closing bracket and the end of the chain.
				if (elseInstr.previousIfInstr.wasThisAnElseIfBlock) {
					parseLine("]");
				}
			}
			
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
				if (parentInstruction != null && !(parentInstruction instanceof FunctionDefInstr)) {
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
				
				// Create the routine definition.
				// TODO add multiple returns.
				FunctionDefInstr funcDefInstr = new FunctionDefInstr(parentInstruction, line, function);
				instructions.add(funcDefInstr);
				
				// TODO study LLVM to figure out how to pass arguments into the function
				
			} else { // Not a function declaration
				
				// Try to find an existing variable in this scope that is in naming conflict with this one
				Instruction previousDeclaration = findInstructionThatDeclaredVariable(parentInstruction, varName);
				if (previousDeclaration != null) {
					printError("Variable '" + varName + "' has already been declared in this scope");
				}
				
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
					
					DeclareInstr instr = new DeclareInstr(parentInstruction, varType + " " + varName, varType, varName);
					instructions.add(instr);
					
				} else if (operator.equals("=")) { // If this is an allocation and assignment
					
					// Get the instructions for the content of this assignment
					Instruction lastInstruction = parseExpression(parentInstruction, expressionContent);
					
					// Get the operand type
					Type operandType = lastInstruction.returnType;
					if (!operandType.canImplicitlyCastTo(varType)) {
						printError("Cannot implicitly cast from " + operandType + " to " + varType);
					}
					
					DeclareInstr declareInstr = new DeclareInstr(parentInstruction, varType + " " + varName, varType, varName);
					instructions.add(declareInstr);
					
					StoreInstr storeInstr = new StoreInstr(parentInstruction,
							varName + " = " + expressionContent, declareInstr, lastInstruction);
					instructions.add(storeInstr);
				} else {
					printError("Invalid assignment operator");
				}
			}
			
			// If this is some form of reassignment
		} else if (ParseUtil.findLowestPrecedenceOperatorAtLowestLevel(line, ParseUtil.assignmentOperators) != null) {
			
			// Find the position of the assignment operator on this line
			StringStartEnd assignmentInfo = ParseUtil.findLowestPrecedenceOperatorAtLowestLevel(line, ParseUtil.assignmentOperators);
			
			if (assignmentInfo == null) {
				printError("Assignment expected");
			}
			
			final String assignmentOp = assignmentInfo.string;
			final String leftHandString = line.substring(0, assignmentInfo.startIndex).trim();
			final String rightHandString = line.substring(assignmentInfo.endIndex).trim();
			
			Instruction lastInstructionFromRightHand = null;
			
			// Only parse out the right-hand side if non-empty
			if (!rightHandString.isEmpty()) {
			
				// Parse out the value to assign to the variable
				lastInstructionFromRightHand = parseExpression(parentInstruction, rightHandString);
			}

			// Parse out the variable or object to assign to
			Instruction lastInstructionFromLeftHand = parseExpression(parentInstruction, leftHandString);
			
			// We expect the last instruction from the left-hand-side to read some variable
			if (!lastInstructionFromLeftHand.returnType.isBaseType) {
				printError("Left-hand side is not a variable or reference");
			}
			
			// We expect the last instruction from the left-hand-side to be a "Read" operation
			if (!(lastInstructionFromLeftHand instanceof LoadInstr)) {
				printError("Left-hand-side should end in a 'Load' instruction " +
							"(not a " + lastInstructionFromLeftHand.name() + ")");
			}
			
			LoadInstr loadInstr = (LoadInstr)lastInstructionFromLeftHand;
			
			final Type leftHandType = loadInstr.returnType;
			
			// The last instruction was a Read, so get the variable that was read
			final DeclareInstr instrThatDeclaredVar = loadInstr.declareInstr;
			
			if (instrThatDeclaredVar == null) {
				printError("Read instruction missing variable reference!");
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
				if (!(instructions.get(instructions.size() - 1) instanceof LoadInstr)) {
					printError("Last generated instruction is expected to be a 'Load'");
				}
				instructions.remove(instructions.size() - 1);
				
				// Write to the pointer
				StoreInstr assignment = new StoreInstr(parentInstruction, line, instrThatDeclaredVar, lastInstructionFromRightHand);
				instructions.add(assignment);
					
			} else if (assignmentOp.equals("++") || assignmentOp.equals("--")) { // Increment/Decrement
				
				if (!leftHandType.isNumberType()) {
					printError(assignmentOp + " can only be applied to a numeric type");
				}
				
				if (!rightHandString.isEmpty()) {
					printError(assignmentOp + " cannot be followed by an expression");
				}
				
				GivenInstr one = new GivenInstr(parentInstruction, "1", 1, Type.Int);
				instructions.add(one);
				
				Instruction addOrSubtract;
				if (assignmentOp.equals("++")) {
					addOrSubtract = new AddInstr(parentInstruction, leftHandString + " + 1", loadInstr, one);
					instructions.add(addOrSubtract);
				} else if (assignmentOp.equals("--")) {
					addOrSubtract = new SubInstr(parentInstruction, leftHandString + " + 1", loadInstr, one);
					instructions.add(addOrSubtract);
				} else {
					new Exception("Invalid operator: " + assignmentOp).printStackTrace();
					return null;
				}
				
				// Write to the pointer
				StoreInstr writeToFirstHalf = new StoreInstr(parentInstruction,
						leftHandString + " = " + addOrSubtract.debugString, instrThatDeclaredVar, addOrSubtract);
				instructions.add(writeToFirstHalf);
				
			} else if (assignmentOp.equals("+=")  || assignmentOp.equals("-=") || // If this is a shorthand operator
					   assignmentOp.equals("/=")  || assignmentOp.equals("*=") ||
					   assignmentOp.equals("^=")  || assignmentOp.equals("%=") ||
					   assignmentOp.equals("||=") || assignmentOp.equals("&&=") ||
					   assignmentOp.equals("|=")  || assignmentOp.equals("&=")) {
				
				if (rightHandString.isEmpty()) {
					printError("Operator " + assignmentOp + " must be followed by an expression");
				}
				
				Instruction binaryOp;
				if (assignmentOp.equals("+=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " + " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("-=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " - " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("/=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " / " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("*=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " * " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("^=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " ^ " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("%=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " % " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("||=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " || " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("&&=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " && " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("|=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " | " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else if (assignmentOp.equals("&=")) {
					binaryOp = new AddInstr(parentInstruction, leftHandString + " & " + rightHandString, loadInstr, lastInstructionFromRightHand);
				} else {
					printError("Invalid operator: " + assignmentOp);
					return null;
				}
				instructions.add(binaryOp);
				
				// Write to the pointer
				StoreInstr writeToFirstHalf = new StoreInstr(parentInstruction, leftHandString, instrThatDeclaredVar, binaryOp);
				instructions.add(writeToFirstHalf);
				
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
			
			String binaryOpStr = operatorInfo.string;
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
			
			Instruction instr;
			if (binaryOpStr.equals("=")) {
				instr = new EqualInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("!=")) {
				instr = new NotEqualInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("@=")) {
				instr = new RefEqualInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("!@=")) {
				instr = new RefNotEqualInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("<")) {
				instr = new LessInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals(">")) {
				instr = new GreaterInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("<=")) {
				instr = new LessEqualInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals(">=")) {
				instr = new GreaterEqualInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("&")) {
				instr = new BitAndInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("|")) {
				instr = new BitOrInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("&&")) {
				instr = new BoolAndInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("||")) {
				instr = new BoolOrInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("+")) {
				instr = new AddInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("-")) {
				instr = new SubInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("*")) {
				instr = new MultInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("/")) {
				instr = new DivideInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("%")) {
				instr = new ModuloInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else if (binaryOpStr.equals("^")) {
				instr = new PowerInstr(parentInstruction, firstHalf + " " + binaryOpStr + " " + lastHalf, lastInstruction1, lastInstruction2);
			} else {
				new Exception("Invalid binary operator: " + binaryOpStr).printStackTrace();
				return null;
			}
			instructions.add(instr);
			
		} else { // There are no more binary operators in this expression
			
			final char opChar = text.charAt(0);
			
			// If it's an BitNot, BoolNot, or ArrLength, then add a unary instruction
			if (opChar == '!' || opChar == '~' || opChar == '#') {

				// Recursively parse the expressions
				String content = ParseUtil.getUnaryFunctionArgument(text, 0);
				Instruction lastInstruction = parseExpression(parentInstruction, content);
				
				Instruction instr;
				if (opChar == '!') {
					instr = new BoolNotInstr(parentInstruction, opChar + content, lastInstruction);
				} else if (opChar == '~') {
					instr = new BitNotInstr(parentInstruction, opChar + content, lastInstruction);
				} else if (opChar == '#') {
					
					if (!(lastInstruction instanceof GetPointerInstr)) {
						printError("# expected a reference to an array, but got " + lastInstruction.returnType);
					}
					
					// We have to get the pointer to an array before we can get the length of it
					GetPointerInstr pointerInstr = (GetPointerInstr)lastInstruction;
					
					boolean countAllElements = true;
					instr = new ArrLengthInstr(parentInstruction, opChar + content, pointerInstr, 0, countAllElements);
				} else {
					new Exception("Invalid binary operator: " + opChar).printStackTrace();
					return null;
				}
				instructions.add(instr);
			} else {
				
				// If this is a literal
				if (ParseUtil.isSignedInteger(text)) {
					GivenInstr instr = new GivenInstr(parentInstruction, text, ParseUtil.parseInt(text), Type.Int);
					instructions.add(instr);
				} else if (ParseUtil.isSignedLong(text)) {
					GivenInstr instr = new GivenInstr(parentInstruction, text, ParseUtil.parseLong(text), Type.Long);
					instructions.add(instr);
				} else if (ParseUtil.isDouble(text)) {
					GivenInstr instr = new GivenInstr(parentInstruction, text, ParseUtil.parseDouble(text), Type.Double);
					instructions.add(instr);
				} else if (ParseUtil.isFloat(text)) {
					GivenInstr instr = new GivenInstr(parentInstruction, text, ParseUtil.parseFloat(text), Type.Float);
					instructions.add(instr);
				} else if (ParseUtil.isBool(text)) {
					GivenInstr instr = new GivenInstr(parentInstruction, text, ParseUtil.parseBool(text), Type.Bool);
					instructions.add(instr);
				} else if (ParseUtil.isString(text)) {
					GivenInstr instr = new GivenInstr(parentInstruction, text, text, Type.String);
					instructions.add(instr);
				} else if (ParseUtil.isArrayDefinition(text)) {
					
					Object[] arrayData = ParseUtil.getArrayDefinitionInfo(text);
					if (arrayData == null) {
						printError("Malformed array definition in '" + text + "'");
					}
					Type arrayElementType = (Type)arrayData[0];
					String[] dimensions = (String[])arrayData[1];
					
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
					
					AllocArrInstr instr = new AllocArrInstr(parentInstruction, text, arrayElementType, args);
					instructions.add(instr);
					
				} else if (ParseUtil.isArrayElementAccess(text)) {
					
					Object[] arrayData = ParseUtil.getArrayReadInfo(text);
					if (arrayData == null) {
						printError("Malformed array in '" + text + "'");
					}
					String arrName = (String)arrayData[0];
					String[] dimensions = (String[])arrayData[1];
					
					DeclareInstr instrThatDeclaredVar = findInstructionThatDeclaredVariable(parentInstruction, arrName);
					if (instrThatDeclaredVar == null) {
						printError("Unknown array '" + arrName + "'");
					}
					
					final String varName = instrThatDeclaredVar.varName;
					final Type varType = instrThatDeclaredVar.varType;
					
					// Make sure the number of dimensions accessed matches the dimension of the array
					if (varType.dimensions != dimensions.length) {
						String plural = " index.";
						if (dimensions.length != 1) {
							plural = " indices.";
						}
						printError("'" + varName + "' is " + varType.dimensions + "-dimensional, but was accessed with " +
									dimensions.length + plural);
					}
					
					// Find the all previous instructions that assigned this variable, and reference them
					boolean foundLastWriteInstruction = wasAssignmentGuaranteed(parentInstruction, varName);
					if (!foundLastWriteInstruction) {
						printError("Array '" + varName + "' was never initialized");
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
					
					GetElementInstr instr = new GetElementInstr(parentInstruction, text, instrThatDeclaredVar, args);
					instructions.add(instr);
				
				} else if (text.indexOf('(') != -1 && text.indexOf(')') != -1) { // Function call
					
					parseFunctionCall(parentInstruction, text, false);
					
				} else if (text.contains(" ")) { // This must be garbage
					
					if (text.contains("==")) {
						printError("Invalid expression: '" + text + "'. Did you mean '='?");
					} else {
						printError("Invalid expression: '" + text + "'");
					}
					
				} else { // This must be a variable, or garbage
				
					// Check if this is a recognized variable
					DeclareInstr instrThatDeclaredVar = findInstructionThatDeclaredVariable(parentInstruction, text);
					if (instrThatDeclaredVar == null) {
						printError("Undeclared variable '" + text + "'");
					}
					
					final String varName = instrThatDeclaredVar.varName;
					final Type varType = instrThatDeclaredVar.varType;
					
					// Find the all previous instructions that assigned this variable, and reference them
					boolean wasInitialized = wasAssignmentGuaranteed(parentInstruction, varName);
					if (!wasInitialized) {
						printError("Variable '" + varName + "' was never initialized");
					}
					
					// If it is an array
					if (varType.isArray) {
						GetPointerInstr instr = new GetPointerInstr(parentInstruction, text, instrThatDeclaredVar);
						instructions.add(instr);
						
					} else { // If this is a primitive type
						LoadInstr instr = new LoadInstr(parentInstruction, text, instrThatDeclaredVar);
						instructions.add(instr);
					}
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
			
			// First convert the argument to a string
			ToStringInstr toStringInstr = new ToStringInstr(parentInstruction, argStrings[0], lastInstruction);
			instructions.add(toStringInstr);
			
			PrintInstr instr = new PrintInstr(parentInstruction, argStrings[0], toStringInstr);
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
				
				if (lastInstruction.returnType == null) {
					printError("Cannot pass void into function");
				}
			}
			
			Function func = findFunctionByNameAndArgs(functionName, args);
			
			// Create the function Call instruction
			FunctionCallInstr callInstr = new FunctionCallInstr(parentInstruction, text, func);
			instructions.add(callInstr);
		}
	}
	
	// Return the return data type for the given instruction type and operands
	public static Type getReturnTypeFromInstructionAndOperands(Instruction instr, Type op1, Type op2) {
		
		// TODO need to change all these tests to the .isA(baseType) function.
		
		if (instr instanceof AddInstr ||
			instr instanceof SubInstr ||
			instr instanceof MultInstr ||
			instr instanceof DivideInstr ||
			instr instanceof PowerInstr) {
			
			if (op1.isArray || op1.isPointer()) {
				printError("Cannot perform " + instr.name() + " on " + op1);
			}
			if (op2.isArray || op2.isPointer()) {
				printError("Cannot perform " + instr.name() + " on " + op2);
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
			
			if (instr instanceof AddInstr) {
				printError("Addition cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof SubInstr) {
				printError("Subtraction cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof MultInstr) {
				printError("Multiplication cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof DivideInstr) {
				printError("Division cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof PowerInstr) {
				printError("Exponentiation cannot be performed on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
			
		} else if (instr instanceof ModuloInstr) {
			
			if (op1.isArray || op1.isPointer()) {
				printError("Cannot perform " + instr.name() + " on " + op1);
			}
			if (op2.isArray || op2.isPointer()) {
				printError("Cannot perform " + instr.name() + " on " + op2);
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
			
		} else if (instr instanceof BoolNotInstr) {
			
			if (op2 != null) {
				printError("NOT cannot be performed on two arguments");
			}
			
			if (op1.isA(BaseType.Bool)) {
				return Type.Bool;
			} else {
				printError("NOT cannot be performed on type " + op1);
			}
			
		} else if (instr instanceof BoolAndInstr ||
				   instr instanceof BoolOrInstr) {
			
			if (op1.baseType == BaseType.Bool && op2.baseType == BaseType.Bool) {
				return Type.Bool;
			}
			
			if (instr instanceof BoolAndInstr) {
				printError("AND cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof BoolOrInstr) {
				printError("OR cannot be performed on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		
		} else if (instr instanceof BitNotInstr) {
			
			if (op2 != null) {
				printError("Bitwise-NOT cannot be performed on two arguments");
			}
			
			if (op1.isIntegerType()) {
				return op1;
			} else {
				printError("Bitwise-NOT cannot be performed on type " + op1);
			}
			
		} else if (instr instanceof BitAndInstr ||
				   instr instanceof BitOrInstr) {
			
			if (op1.baseType == BaseType.Int && op2.baseType == BaseType.Int) {
				return Type.Int;
			} else if (op1.baseType == BaseType.Long && op2.baseType == BaseType.Long) {
				return Type.Long;
			}
			
			if (instr instanceof BitAndInstr) {
				printError("Bitwise AND cannot be performed on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof BitOrInstr) {
				printError("Bitwise OR cannot be performed on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (instr instanceof ConcatInstr) {
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
		} else if (instr instanceof EqualInstr ||
				   instr instanceof NotEqualInstr) {
			
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
		} else if (instr instanceof RefEqualInstr ||
				   instr instanceof RefNotEqualInstr) {
			
			printError("Reference equality not implemented yet");
			return Type.Bool;
			
		} else if (instr instanceof LessInstr ||
				   instr instanceof GreaterInstr ||
				   instr instanceof LessEqualInstr ||
				   instr instanceof GreaterEqualInstr) {
			
			if (op1.isNumberType() && op2.isNumberType()) {
				return Type.Bool;
			}
			
			if (instr instanceof LessInstr) {
				printError("Less Than cannot be tested on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof GreaterInstr) {
				printError("Greater Than cannot be tested on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof LessEqualInstr) {
				printError("Less or Equal To cannot be tested on " + op1 + " and " + op2);
				return null;
			} else if (instr instanceof GreaterEqualInstr) {
				printError("Greater or Equal To cannot be tested on " + op1 + " and " + op2);
				return null;
			} else {
				new Exception("This code should not be reached").printStackTrace();
			}
		} else if (instr instanceof PrintInstr) {
			return null;
		} else if (instr instanceof ArrLengthInstr) {
			if (op2 != null) {
				new Exception("ArrayLengthInstr second arg must be null");
			}
			if (op1.isArray) {
				return Type.Int;
			} else {
				printError("ArrayLength cannot be determined for type " + op1);
			}
		} else {
			printError("Invalid return type: " + instr.name());
		}
		
		new Exception("This code should not be reached").printStackTrace();
		return null;
	}
	
	/*
	// Return the instruction type corresponding to the given binary operator
	static class getInstructionTypeFromOperator(String op) {
		if (op.equals("+") || op.equals("+=") || op.equals("++")) {
			return Add;
		} else if (op.equals("-") || op.equals("-=") || op.equals("--")) {
			return Subtract;
		} else if (op.equals("/") || op.equals("/=")) {
			return Divide;
		} else if (op.equals("*") || op.equals("*=")) {
			return Mult;
		} else if (op.equals("^") || op.equals("^=")) {
			return Power;
		} else if (op.equals("%") || op.equals("%=")) {
			return Modulo;
		} else if (op.equals("=")) {
			return Equal;
		} else if (op.equals("!=")) {
			return NotEqual;
		} else if (op.equals("<")) {
			return Less;
		} else if (op.equals("@=")) {
			return RefEqual;
		} else if (op.equals("!@=")) {
			return RefNotEqual;
		} else if (op.equals(">")) {
			return Greater;
		} else if (op.equals("<=")) {
			return LessEqual;
		} else if (op.equals(">=")) {
			return GreaterEqual;
		} else if (op.equals("&&") || op.equals("&&=")) {
			return And;
		} else if (op.equals("||") || op.equals("||=")) {
			return Or;
		} else if (op.equals("&") || op.equals("&=")) {
			return BitAnd;
		} else if (op.equals("|") || op.equals("|=")) {
			return BitOr;
		} else if (op.equals("~")) {
			return BitNot;
		} else if (op.equals("#")) {
			return ArrayLength;
		} else {
			printError("Invalid operator: " + op);
			return null;
		}
	}
	*/
	
	// Return true if the given function was previously declared in the given scope
	static boolean findConflictingFunction(Instruction parentInstr, Function function) {
		
		// Iterate backward to find a function
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstruction = instructions.get(i);
			
			if (otherInstruction instanceof FunctionDefInstr) { // If this is a routine
				FunctionDefInstr funcDefInstr = (FunctionDefInstr)otherInstruction;
				
				// If this instruction defined a routine by the same name
				if (funcDefInstr.functionThatWasDefined.name.equals(function.name)) {
					
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
						if (!funcDefInstr.functionThatWasDefined.isDistinguisable(function)) {
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
	
	// Return the instruction that declared a variable by the given name
	// if it was previously used in the given scope.
	static DeclareInstr findInstructionThatDeclaredVariable(Instruction parentInstr, String varName) {
		
		// Iterate backward to find the assignment of this variable
		for (int i = instructions.size() - 1; i >= 0; i--) {
			Instruction instr = instructions.get(i);
			
			if (instr instanceof DeclareInstr) {
				DeclareInstr declareInstr = (DeclareInstr)instr;
				
				// If this instruction used the given variable
				if (declareInstr.varName.equals(varName)) {
					
					Instruction otherParent = declareInstr.parentInstruction; // May be null
					
					// If this instruction is a child of an instruction that is a ancestor of the
					//    given instruction, then it must be true that that instruction executed if the
					//    given instruction executed, so we can stop searching.
					Instruction nextParent = parentInstr;
					while (nextParent != otherParent && nextParent != null) {
						nextParent = nextParent.parentInstruction;
					}
					if (nextParent == otherParent) {
						return declareInstr;
					}
				}
			}
		}
		return null;
	}
	
	// Add references to all instructions that may have assigned to the given variable
	//    until we find an instruction that is guaranteed to have assigned the given variable.
	// Return true if a guaranteed assignment was found.
	static boolean wasAssignmentGuaranteed(Instruction parentInstruction, String varName) {
		
		// Iterate backward to find the assignment of this variable
		for (int i = instructions.size()-1; i >= 0; i--) {
			Instruction otherInstr = instructions.get(i);
			
			if (otherInstr instanceof StoreInstr) {
				StoreInstr storeInstr = (StoreInstr)otherInstr;
				
				String otherInstrVarName = storeInstr.declareInstr.varName;
				
				// If this instruction wrote to the given variable
				if (otherInstrVarName.equals(varName)) {
					
					// Determine if this instruction was guaranteed to be executed
					
					Instruction otherParent = storeInstr.parentInstruction; // May be null
					
					// If this instruction is a direct child of an instruction that is an ancestor of the
					//    given instruction, then it must be true that that instruction executed if the
					//    given instruction executed, so we can stop searching.
					Instruction nextParent = parentInstruction;
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
					if (wasAssignmentGuaranteedHelper(i, instructions.size(), varName, parentInstruction)) {
						return true;
					}
				} 
			}// else if (type == RoutineDefinition) { // If this is a routine
				// We can stop searching because we hit the header of the routine the given instruction is inside.
			//	return false;
			//}
		}
		
		return false;
	}
	
	// Return true if an assignment of the given variable was guaranteed between the given
	//     start and stop instruction index within the enclosing scope of parentInstruction
	static boolean wasAssignmentGuaranteedHelper(int startIndex, int stopIndex,
				String varName, Instruction parentInstruction) {
		
		StoreInstr storeInstr = null;
		int currentInstructionIndex = -1;
		
		// Iterate forward until we find an assignment instruction
		for (int i = startIndex; i < stopIndex; i++) {
			Instruction otherInstr = instructions.get(i);
			
			// If this is an assignment
			if (otherInstr instanceof StoreInstr) {
				storeInstr = (StoreInstr)otherInstr;
				currentInstructionIndex = i;
				break;
			}
		}
		
		// No assignment was found, so return false
		if (storeInstr == null) {
			return false;
		}
		
		// Determine if an assignment is guaranteed in the parent instruction
		Instruction currentParent = storeInstr.parentInstruction;
		
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
			if (currentParent instanceof LoopInstr) {
				// Search for a break or continue statement that possibly breaks this loop
				//   before getting to the current instruction
				int i = currentInstructionIndex - 1;
				while (i >= 0) {
					Instruction currentInstr = instructions.get(i);
					
					// If we are at the parent instruction, stop searching
					if (currentInstr == currentParent) {
						break;
					}

					LoopInstr loopStartInstr = null;
					if (currentInstr instanceof BreakInstr) {
						loopStartInstr = ((BreakInstr)currentInstr).loopStartInstr;
					} else if (currentInstr instanceof ContinueInstr) {
						loopStartInstr = ((ContinueInstr)currentInstr).loopStartInstr;
					}
					
					if (loopStartInstr != null &&
						loopStartInstr == currentParent) {
						// A possible break was found before the current instruction.
						// Therefore it may not have been executed.
						return false;
					}
					
					i--;
				}
			}
			
			// If we are inside a if-elseif-else structure, but not all the way at the
			//    top of the chain, then stop searching here.
			if (currentParent instanceof ElseInstr) {
				return false;
			}
			
			// If we are inside an if-elseif-else structure, and this instruction is
			//    inside the leading if-condition.
			if (currentParent instanceof IfInstr) {
				Instruction currentIf = currentParent;
				
				// Check if this if-chain ends in an else (i.e. it may be exhaustive)
				if (currentIf instanceof IfInstr) {
					currentIf = ((IfInstr)currentIf).elseInstr;
					
					// If this chain did not end in an Else-block, then we are done searching
					if (currentIf == null) {
						return false;
					}
				}
				
				// If this chain DID end in an else-block, then we need to check if
				//    a guaranteed assignment exists in each of the chained conditional sections.
				currentIf = currentParent;
				do {
					// This must be an ElseIf or an Else block
					if (currentIf instanceof IfInstr) {
						currentIf = ((IfInstr)currentIf).elseInstr;
					}
					
					Instruction endChainInstruction = null;
					if (currentIf instanceof IfInstr) {
						endChainInstruction = ((IfInstr)currentIf).endOfBlockInstr;
					}
					int startInstructionIndex = instructions.indexOf(currentIf) + 1;
					int endChainInstructionIndex = instructions.indexOf(endChainInstruction);
					
					// Recursively check for guaranteed assignment.
					// If any one was not guaranteed, then return false
					if (!wasAssignmentGuaranteedHelper(startInstructionIndex,
							endChainInstructionIndex, varName, currentIf)) {
						return false;
					}
				} while (!(currentIf instanceof ElseInstr));
			}
			
			// This is a routine, so we can't be guaranteed that it will execute
			if (currentParent instanceof FunctionDefInstr) {
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
			if (instr.doesStartScope()) {
				currentScopeDepth--;
			}
			
			if (currentScopeDepth == 0) {
				return instr;
			}
			
			// It is possible for an instruction to both increase and decrease scope (such as ElseIf)
			if (instr.doesEndScope()) {
				currentScopeDepth++;
			}
		}
		
		return null;
	}
	
	// Return the closest ancestor instruction that is a Loop instruction, or null if none is found
	static LoopInstr findNearestAncestorLoop(Instruction parent) {
		while (parent != null && parent instanceof LoopInstr) {
			// Loops and conditional structures may not contain a routine
			if (parent instanceof FunctionDefInstr) {
				break;
			}
			parent = parent.parentInstruction;
		}
		if (parent instanceof LoopInstr) {
			return (LoopInstr)parent;
		}
		return null;
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
				String[] argNames = new String[argStrings.length];
				for (int j = 0; j < argTypes.length; j++) {
					int firstSpaceIndex = argStrings[j].indexOf(' ');
					if (firstSpaceIndex == -1) {
						printError("Missing argument name or type in function declaration");
					}
					
					String typeString = argStrings[j].substring(0, firstSpaceIndex);
					argTypes[j] = new Type(typeString);
					
					String argName = argStrings[j].substring(firstSpaceIndex + 1).trim();
					argNames[j] = argName;
				}
				
				// TODO add multiple returns
				Function function = new Function(functionName, returnType, argTypes, argNames);
				
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

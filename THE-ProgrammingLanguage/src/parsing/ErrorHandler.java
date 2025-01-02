package parsing;

public class ErrorHandler {
	
	// Print an error message, then exit the program.
	public static void printError(String message) {
		printError(message, CompilePass.currentParsingLineNumber);
	}

	// Print an error message, the line of original code, then exit the program.
	public static void printError(String message, final int lineNumber) {
		System.out.println(message);
		
		// Print out the original line of the program, if available.
		if (lineNumber >= 0) {
			if (lineNumber < CompilePass.lines.length) {
				System.out.println("'" + CompilePass.lines[lineNumber] + "'");
			}
			System.out.println("(on line " + (lineNumber + 1) + ")");
		}
		
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		for (int i = 2; i < stackTrace.length-1; i++) {
			System.out.println(stackTrace[i]);
		}
		
		/*
		String lineNum = Thread.currentThread().getStackTrace()[2].toString();
		print(lineNum.substring(lineNum.indexOf('(')));
		*/
		
		System.exit(1);
	}
}

package parsing;

public class ErrorHandler {
	
	// Print an error message, then exit the program.
	public static void printError(String message) {
		System.out.println(message);
		if (Compiler.currentParsingLineNumber < Compiler.lines.length) {
			System.out.println("'" + Compiler.lines[Compiler.currentParsingLineNumber] + "'");
		}
		System.out.println("(on line " + (Compiler.currentParsingLineNumber + 1) + ")");
		
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

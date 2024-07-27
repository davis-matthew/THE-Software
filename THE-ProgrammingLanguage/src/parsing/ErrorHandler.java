package parsing;

public class ErrorHandler {
	
	// Print an error message, then exit the program.
	public static void printError(String message) {
		System.out.println(message);
		if (CompilePass.currentParsingLineNumber < CompilePass.lines.length) {
			System.out.println("'" + CompilePass.lines[CompilePass.currentParsingLineNumber] + "'");
		}
		System.out.println("(on line " + (CompilePass.currentParsingLineNumber + 1) + ")");
		
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

package parsing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import instructions.Instruction;
import passes.DeleteUnusedInstructionsPass;

// Created by Daniel Williams
// Created on May 31, 2020
// Last updated on July 27, 2024

// A fast programming language that maybe is good.

public class Main {
	
	static final String fileToRead = "testFiles/ProgramInput.the";
	static final String fileToWrite = "testFiles/ProgramOutput.the";
	
	public static void main(String[] args) {
		
		// Run all the compilation passes.
		
		String text = loadFile(fileToRead);
		
		final ArrayList<Instruction> instructions = CompilePass.initialParsingPass(text);
		
		// Print out all of the instructions to the console
		print("----------- Initial Parse -----------\n");
		for (int i = 0; i < instructions.size(); i++) {
			print(instructions.get(i));
		}
		print("");
		
		DeleteUnusedInstructionsPass.deleteUnusedInstructions(instructions);
		
		// Print out all of the instructions to the console
		print("------- Delete Unused Instructions Pass -------\n");
		for (int i = 0; i < instructions.size(); i++) {
			print(instructions.get(i));
		}
		print("");
		
		// Stringify the final output.
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < instructions.size(); i++) {
			stringBuilder.append(instructions.get(i).toString());
			stringBuilder.append("\n");
		}
		saveFile(fileToWrite, stringBuilder.toString());
	}
	
	// Load some text from a file
	public static String loadFile(String directory) {
		
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
	public static void saveFile(String directory, String text) {
		
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
		System.out.println(o);
	}
	
}

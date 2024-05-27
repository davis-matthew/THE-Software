package utils;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.JFileChooser;
import javax.swing.JFrame;

public class FileIOUtils {
	public static final File chooseFile(String defaultFolder, String defaultName, boolean save, JFrame frame) {
		File defaultNameFile = null;
		if(!defaultName.equals("")) {
			defaultNameFile = new File(defaultFolder + defaultName);
		}
		return chooseFileOrDirectory(defaultFolder, defaultNameFile, true, false, save, frame);
	}
	public static final File chooseDirectory(String defaultFolder, String defaultName, boolean save, JFrame frame) {
		File defaultNameFile = null;
		if(!defaultName.equals("")) {
			defaultNameFile = new File(defaultFolder + defaultName);
		}
		return chooseFileOrDirectory(defaultFolder, defaultNameFile, false, true, save, frame);
	}
	public static final File chooseFileOrDirectory(String defaultFolder, String defaultName, boolean save, JFrame frame) {
		File defaultNameFile = null;
		if(!defaultName.equals("")) {
			defaultNameFile = new File(defaultFolder + defaultName);
		}
		return chooseFileOrDirectory(defaultFolder, defaultNameFile, true, true, save, frame);
	}
	private static final File chooseFileOrDirectory(String defaultLocation, File defaultFileName, boolean file, boolean directory, boolean save, JFrame frame) {
		final Dimension chooserSize = new Dimension(1000,700);
		
		JFileChooser chooser = new JFileChooser(new File(defaultLocation));
		chooser.setPreferredSize(chooserSize);
		
		// File Selection Mode
		if(file && directory) {
			chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		}
		else if(file) {
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		}
		else if(directory) {
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		}

		// Default File
		if(defaultFileName != null){
			chooser.setSelectedFile(defaultFileName);
		}
		
		// Save or Open
		if(save && chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		else if(chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION){
			return chooser.getSelectedFile();
		}
		
		return null; // canceled
	}
	public static final String getContentsOfFile(File f) throws IOException {
		return Files.readString(f.toPath());
	}
}
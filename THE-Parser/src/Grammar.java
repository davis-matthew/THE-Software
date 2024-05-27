import java.util.Map;

import utils.FileIOUtils;

import java.io.File;
import java.util.List;

public class Grammar {
	Map<Token,List<List<Token>>> derivationRules;
	
	public Grammar(File g) {
		this(FileIOUtils.getContentsOfFile(g));
	}
	public Grammar(String g) {
		for(String line : g.split("\n")) {
			line = line.split("")
		}
	}
	
	public void dump(File out) {
		String grammar;
		FileIOUtils.writeFile(out, grammar);
	}
}

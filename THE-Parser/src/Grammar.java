import java.util.Map;
import java.io.File;
import java.util.List;

public class Grammar {
	Map<Token,List<List<Token>>> derivationRules;
	
	public Grammar(File g) {
		this(FileIOUtils.getContent(g));
	}
	public Grammar(String g) {
		for(String line : g.split("\n")) {
			line = line.split("")
		}
	}
	
	public void dumpGrammar(File out) {
		String grammar;
		FileIOUtils.writeFile(out, grammar);
	}
}

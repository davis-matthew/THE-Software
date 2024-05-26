import java.util.ArrayList;
import java.util.Arrays;

/*
 *  https://dl.acm.org/doi/pdf/10.1145/3385412.3386032
 *  https://dl.acm.org/doi/pdf/10.1145/3591269
 *  EXT: https://www.sciencedirect.com/science/article/pii/S2590118422000697?via%3Dihub
 */

class State {
	ArrayList<Symbol> symbols;
	public State() {
		symbols = new ArrayList<Symbol>();
	}
	public State(ArrayList<Symbol> sym) {
		symbols = sym;
	}
}

public class ParserMain {
	Grammar grammar;
	
	State start;
	Symbol[] terminals;
	Symbol[] nonterminals;
	Derivation[] rules;
	
	void initialize(Grammar g) {
		grammar = g;
	}
	State unit(){
		return new State();
	}
	State prepend(State elements, State target) {
		ArrayList<Symbol> prepended = elements.symbols;
		prepended.addAll(target.symbols);
		return new State(prepended);
	}
	void derivative() {
		
	}
	State empty() {
		return new State();
	}
	State union() {
		return new State();
	}
	boolean epsilon(State sigma) { 
		return true;
	}
	
	/*
		Σ := prepend(Σϵ , unit())
		for i = 1, . . . , n:
			Σ' := empty()
			for each s ∈ S:
				Σ' := union(Σ′, prepend([s]^(t_i), derivative(Σ,s)))
			Σ := Σ'
		return epsilon(Σ)
	*/
	
	ArrayList<Token> parse(String text, File grammar) {
		parse(text.toCharArray(), grammar);
	}
	ArrayList<Token> parse(String text, String grammar) {
		parse(text.toCharArray(), grammar);
	}
	ArrayList<Token> parse(char[] text, File grammar) {
		parse(text, getContentsOfFile(grammar));
	}
	ArrayList<Token> parse(char[] text, String grammar) {
		parse(text, grammar.toCharArray());
	}
	ArrayList<Token> parse(char[] text, char[] grammar) {
		
		ArrayList<Symbol> symbolList = (ArrayList<Symbol>) Arrays.asList(terminals);
		symbolList.addAll(Arrays.asList(nonterminals));
		Symbol[] symbols = (Symbol[]) symbolList.toArray();
		
		
		//State sigma = prepend(start, unit());
		State sigma = start;
		for(Symbol terminal : terminals) {
			State sigmaPrime = empty();
			for(Symbol s : symbols) {
				sigmaPrime = union(sigmaPrime, prepend([s]^(terminal),derivative(sigma,s)));
			}
			sigma = sigmaPrime;
		}
		return epsilon(sigma);
	}
}

# Parsing
We aim to implement a fused lexer/parser solution for general context-free language grammars (and eventually context-sensitive grammars?).

## Components: 
- Tools:
    - Grammar Converter: a tool to convert over antlr4 grammars to a DGNF form.
    - Grammar Tester: a tool to visualize how the grammar matches a text
- Parser: the actual parser of the grammar
- Tokenizer Interface: an interface of what to do with the parsed tokens
## Referencing:
- flap for fusing the lexer & parser (https://dl.acm.org/doi/pdf/10.1145/3385412.3386032)
- relational derivative-based general parsing (https://dl.acm.org/doi/pdf/10.1145/3385412.3386032)
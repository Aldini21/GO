/*
NOMBRES
*/

package act4_analisis_lexico;

import java_cup.runtime.Symbol;

%%

%class GoLexer
%public
%unicode
%cup
%line
%column

%{
  private Symbol token(int type) {
    System.out.println("["+(yyline+1)+"-"+(yycolumn+1)+"] token: "+yytext());
    return new Symbol(type, yyline+1, yycolumn+1, yytext());
  }

  private Symbol token(int type, Object value) {
    return new Symbol(type, yyline+1, yycolumn+1, value);
  }
%}

LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
WhiteSpace     = {LineTerminator} | [ \t\f]

/* comments */
Comment = {TraditionalComment} | {EndOfLineComment} | {DocumentationComment}

TraditionalComment   = "/*" [^*] ~"*/" | "/*" "*"+ "/"
EndOfLineComment     = "//" {InputCharacter}* {LineTerminator}?
DocumentationComment = "/**" {CommentContent} "*"+ "/"
CommentContent       = ( [^*] | \*+ [^/*] )*

Identifier = [:jletter:] [:jletterdigit:]*
DecIntegerLiteral = 0 | [1-9][0-9]*

%%

{Comment}       { /* Ignorar */ }
{WhiteSpace}    { /* Ignorar */ }

/* PALABRAS RESERVADAS DE GO */

"break"          { return token(sym.BREAK); }
"default"        { return token(sym.DEFAULT); }
"func"           { return token(sym.FUNC); }
"interface"      { return token(sym.INTERFACE); }
"select"         { return token(sym.SELECT); }

"case"           { return token(sym.CASE); }
"defer"          { return token(sym.DEFER); }
"go"             { return token(sym.GO); }
"map"             { return token(sym.MAP); }
"struct"         { return token(sym.STRUCT); }

"chan"           { return token(sym.CHAN); }
"else"           { return token(sym.ELSE); }
"goto"           { return token(sym.GOTO); }
"package"        { return token(sym.PACKAGE); }
"switch"         { return token(sym.SWITCH); }

"const"          { return token(sym.CONST); }
"fallthrough"    { return token(sym.FALLTHROUGH); }
"if"             { return token(sym.IF); }
"range"           { return token(sym.RANGE); }
"type"            { return token(sym.TYPE); }

"continue"       { return token(sym.CONTINUE); }
"for"            { return token(sym.FOR); }
"import"         { return token(sym.IMPORT); }
"return"         { return token(sym.RETURN); }
"var"            { return token(sym.VAR); }

/* IDENTIFICADORES */

{Identifier}     { return token(sym.ID); }

/* NÚMEROS */

{DecIntegerLiteral} { return token(sym.NUMERO); }

/* CADENAS */

\"([^\"\\]|\\.)*\" { return token(sym.CADENA); }

/* OPERADORES Y SÍMBOLOS */

";"              { return token(sym.PUNTO_COMA); }
","              { return token(sym.COMA); }
"."              { return token(sym.PUNTO); }
"="              { return token(sym.IGUAL); }
"+"              { return token(sym.SUMA); }
"-"              { return token(sym.RESTA); }
"*"              { return token(sym.MULTIPLICACION); }
"/"              { return token(sym.DIVISION); }
"("              { return token(sym.PARENTESIS_ABRE); }
")"              { return token(sym.PARENTESIS_CIERRA); }
"{"              { return token(sym.LLAVE_ABRE); }
"}"              { return token(sym.LLAVE_CIERRA); }

/* TOKEN DESCONOCIDO */

. {
    System.out.println("TOKEN DESCONOCIDO: " + yytext());
}

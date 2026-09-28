/*
NOMBRES COMPLETOS}
*/

package act2_analisis_lexico;

import java_cup.runtime.Symbol;

%%

%class GoLexer
%public
%unicode
%cup
%line
%column

%{

    private boolean huboErrores = false;

    public boolean getHuboErrores() {
        return huboErrores;
    }

    private Symbol token(int type) {

        System.out.println(
            "[" + (yyline + 1) + "-" + (yycolumn + 1) + "] "
            + "token: " + yytext()
        );

        return new Symbol(
            type,
            yyline + 1,
            yycolumn + 1,
            yytext()
        );
    }

    private void errorLexico(String tipo, String descripcion) {

        huboErrores = true;

        System.out.println(
            "[ERROR LEXICO] "
            + "Linea: " + (yyline + 1)
            + ", Columna: " + (yycolumn + 1)
            + " | Tipo: " + tipo
            + " | Lexema: " + yytext()
            + " | " + descripcion
        );
    }

%}


/* =========================================
   ESPACIOS
   ========================================= */

SaltoLinea = \r|\n|\r\n
EspacioBlanco = {SaltoLinea}|[ \t\f]+


/* =========================================
   IDENTIFICADORES
   ========================================= */

Identificador = [a-zA-Z_][a-zA-Z0-9_]*


/* =========================================
   NUMEROS VALIDOS
   ========================================= */

Entero = [0-9]+
Flotante = [0-9]+\.[0-9]+


/* =========================================
   ERRORES
   ========================================= */

/* Ejemplo: 123nombre */

IdentificadorIncorrecto = [0-9]+[a-zA-Z_][a-zA-Z0-9_]*

/* Ejemplos: 12.5.8, 10..5 */

NumeroIncorrecto = [0-9]+\.[0-9]*\.[0-9.]*

/* Cadena que comienza pero no cierra */

CadenaIncorrecta = \"([^\"\\\r\n]|\\.)*


/* =========================================
   COMENTARIOS
   ========================================= */

ComentarioLinea = "//"[^\r\n]*

ComentarioBloque = "/*"([^*]|\*+[^*/])*\*+"/"


/* =========================================
   CADENA VALIDA
   ========================================= */

Cadena = \"([^\"\\\r\n]|\\.)*\"


%%


/* =========================================
   ESPACIOS Y COMENTARIOS
   ========================================= */

{EspacioBlanco} {
    /* Ignorar */
}

{ComentarioLinea} {
    /* Ignorar */
}

{ComentarioBloque} {
    /* Ignorar */
}


/* =========================================
   PALABRAS CLAVE DE GO
   ========================================= */

"break"         { return token(sym.BREAK); }
"default"       { return token(sym.DEFAULT); }
"func"          { return token(sym.FUNC); }
"interface"     { return token(sym.INTERFACE); }
"select"        { return token(sym.SELECT); }

"case"          { return token(sym.CASE); }
"defer"         { return token(sym.DEFER); }
"go"            { return token(sym.GO); }
"map"           { return token(sym.MAP); }
"struct"        { return token(sym.STRUCT); }

"chan"          { return token(sym.CHAN); }
"else"          { return token(sym.ELSE); }
"goto"          { return token(sym.GOTO); }
"package"       { return token(sym.PACKAGE); }
"switch"        { return token(sym.SWITCH); }

"const"         { return token(sym.CONST); }
"fallthrough"   { return token(sym.FALLTHROUGH); }
"if"            { return token(sym.IF); }
"range"         { return token(sym.RANGE); }
"type"          { return token(sym.TYPE); }

"continue"      { return token(sym.CONTINUE); }
"for"           { return token(sym.FOR); }
"import"        { return token(sym.IMPORT); }
"return"        { return token(sym.RETURN); }
"var"           { return token(sym.VAR); }


/* =========================================
   OPERADORES RELACIONALES
   ========================================= */

"<="            { return token(sym.MENOR_IGUAL); }
">="            { return token(sym.MAYOR_IGUAL); }
"=="            { return token(sym.IGUAL_IGUAL); }
"!="            { return token(sym.DIFERENTE); }

"<"             { return token(sym.MENOR); }
">"             { return token(sym.MAYOR); }


/* =========================================
   OPERADORES LOGICOS
   ========================================= */

"&&"            { return token(sym.AND); }
"||"            { return token(sym.OR); }
"!"             { return token(sym.NOT); }


/* =========================================
   OPERADORES ARITMETICOS
   ========================================= */

"+"             { return token(sym.SUMA); }
"-"             { return token(sym.RESTA); }
"*"             { return token(sym.MULTIPLICACION); }
"/"             { return token(sym.DIVISION); }

"="             { return token(sym.ASIGNACION); }


/* =========================================
   SIMBOLOS
   ========================================= */

"("             { return token(sym.PARENTESIS_ABRE); }
")"             { return token(sym.PARENTESIS_CIERRA); }

"["             { return token(sym.CORCHETE_ABRE); }
"]"             { return token(sym.CORCHETE_CIERRA); }

"{"             { return token(sym.LLAVE_ABRE); }
"}"             { return token(sym.LLAVE_CIERRA); }

","             { return token(sym.COMA); }
";"             { return token(sym.PUNTO_COMA); }
"."             { return token(sym.PUNTO); }


/* =========================================
   ERRORES LEXICOS ESPECIFICOS
   Deben aparecer antes de los patrones
   validos correspondientes.
   ========================================= */

{IdentificadorIncorrecto} {

    errorLexico(
        "IDENTIFICADOR MAL FORMADO",
        "Un identificador no puede comenzar con un numero."
    );
}

{NumeroIncorrecto} {

    errorLexico(
        "LITERAL NUMERICO INCORRECTO",
        "El numero contiene un formato decimal incorrecto."
    );
}


/* =========================================
   TOKENS VALIDOS
   ========================================= */

{Flotante} {
    return token(sym.FLOTANTE);
}

{Entero} {
    return token(sym.ENTERO);
}

{Cadena} {
    return token(sym.CADENA);
}

{Identificador} {
    return token(sym.IDENTIFICADOR);
}


/* =========================================
   CADENA MAL FORMADA

   Se coloca despues de Cadena para que una
   cadena valida sea reconocida primero.
   ========================================= */

{CadenaIncorrecta} {

    errorLexico(
        "CADENA MAL FORMADA",
        "La cadena de texto no fue cerrada con comillas dobles."
    );
}


/* =========================================
   CARACTER NO RECONOCIDO
   ========================================= */

. {

    errorLexico(
        "CARACTER NO RECONOCIDO",
        "El caracter no pertenece a los componentes lexicos definidos."
    );
}
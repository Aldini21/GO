/*
NOMBRES COMPLETOS


*/

package act8_analisis_lexico;

import java_cup.runtime.Symbol;

%%

%class GoLexer
%public
%unicode
%cup
%line
%column

%{

    /* =========================================
       CONTROL DE ERRORES LEXICOS
       ========================================= */

    private boolean huboErrores = false;

    public boolean getHuboErrores() {
        return huboErrores;
    }


    /* =========================================
       CREACION E IMPRESION DE TOKENS
       ========================================= */

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


    /* =========================================
       MANEJO DE ERRORES LEXICOS
       ========================================= */

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

Entero = 0|[1-9][0-9]*
Decimal = [0-9]+\.[0-9]+


/* =========================================
   CARACTER
   ========================================= */

Caracter = "'"([^'\\]|\\.)"'"


/* =========================================
   CADENA VALIDA
   ========================================= */

Cadena = \"([^\"\\\r\n]|\\.)*\"


/* =========================================
   ERRORES LEXICOS ESPECIFICOS
   ========================================= */

/* Ejemplo: 123nombre */

IdentificadorIncorrecto = [0-9]+[a-zA-Z_][a-zA-Z0-9_]*


/* Ejemplos:
   12.5.8
   10..5
*/

NumeroIncorrecto = [0-9]+\.[0-9]*\.[0-9.]*


/* Cadena que comienza pero nunca cierra */

CadenaIncorrecta = \"([^\"\\\r\n]|\\.)*


/* =========================================
   COMENTARIOS
   ========================================= */

ComentarioLinea = "//"[^\r\n]*

ComentarioBloque = "/*"([^*]|\*+[^*/])*\*+"/"


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
   PALABRAS RESERVADAS DE GO
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
   TIPOS DE DATOS DE GO
   Actividad 5
   ========================================= */

"int"           { return token(sym.INT); }
"float32"       { return token(sym.FLOAT32); }
"float64"       { return token(sym.FLOAT64); }
"string"        { return token(sym.STRING); }
"bool"          { return token(sym.BOOL); }
"byte"          { return token(sym.BYTE); }
"rune"          { return token(sym.RUNE); }


/* =========================================
   VALORES BOOLEANOS
   ========================================= */

"true"          { return token(sym.TRUE); }
"false"         { return token(sym.FALSE); }


/* =========================================
   OPERADORES RELACIONALES
   Actividad 2
   ========================================= */

"<="            { return token(sym.MENOR_IGUAL); }
">="            { return token(sym.MAYOR_IGUAL); }
"=="            { return token(sym.IGUAL_IGUAL); }
"!="            { return token(sym.DIFERENTE); }

"<"             { return token(sym.MENOR); }
">"             { return token(sym.MAYOR); }


/* =========================================
   OPERADORES LOGICOS
   Actividad 2
   ========================================= */

"&&"            { return token(sym.AND); }
"||"            { return token(sym.OR); }
"!"             { return token(sym.NOT); }


/* =========================================
   SENTENCIAS DE INCREMENTO/DECREMENTO
   Actividad 8 - MEJORA
   ========================================= */

"++"    { return token(sym.INCREMENTO); }
"--"    { return token(sym.DECREMENTO); }

/* =========================================
   OPERADORES ARITMETICOS
   ========================================= */

"+"             { return token(sym.SUMA); }
"-"             { return token(sym.RESTA); }
"*"             { return token(sym.MULTIPLICACION); }
"/"             { return token(sym.DIVISION); }
"%"             { return token(sym.MODULO); }


/* =========================================
   ASIGNACION
   Conservamos el nombre usado por Act. 5
   ========================================= */

"="             { return token(sym.IGUAL); }


/* =========================================
   OPERADOR DE DIRECCION DE GO
   Necesario para lectura con fmt.Scan(&id)
   ========================================= */

"&"             { return token(sym.AMPERSAND); }


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
":"             { return token(sym.DOS_PUNTOS); }
"."             { return token(sym.PUNTO); }


/* =========================================
   ERRORES LEXICOS ESPECIFICOS

   IMPORTANTE:
   Estos patrones aparecen antes de los
   numeros e identificadores validos.
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
   LITERALES VALIDOS
   ========================================= */

{Decimal} {
    return token(sym.NUMERO_DECIMAL);
}


{Entero} {
    return token(sym.NUMERO);
}


{Cadena} {
    return token(sym.CADENA);
}


{Caracter} {
    return token(sym.CARACTER);
}


{Identificador} {
    return token(sym.ID);
}


/* =========================================
   CADENA MAL FORMADA

   Debe colocarse despues de Cadena para
   permitir que JFlex priorice primero
   una cadena valida.
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
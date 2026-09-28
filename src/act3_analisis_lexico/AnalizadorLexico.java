package act3_analisis_lexico;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;

public class AnalizadorLexico {

    public static void main(String[] args) {

        try {

            Reader lector = new BufferedReader(
                new FileReader(
                    "src/act3_analisis_lexico/tokens_java.txt"
                )
            );

            GoLexer lexer = new GoLexer(lector);
            ParserJava parser = new ParserJava(lexer);

            System.out.println("===== INICIO DEL ANÁLISIS =====");

            parser.parse();

            System.out.println("Análisis sintáctico finalizado correctamente.");

        } catch (Exception e) {

            System.out.println("Error durante el análisis sintáctico.");
            System.out.println(e.getMessage());
        }
    }
}
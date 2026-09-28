package act1_analisis_lexico;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java_cup.runtime.Symbol;

public class AnalizadorLexico {

    public static void main(String[] args) {

        System.out.println("===== ANALIZADOR LEXICO DE GO =====");
        System.out.println();

        try {

            Reader lector = new BufferedReader(
                new FileReader(
                    "src/act1_analisis_lexico/tokens_go.txt"
                )
            );

            JavaLexer lexer = new JavaLexer(lector);

            while (true) {

                Symbol token = lexer.next_token();

                if (token.sym == sym.EOF) {
                    System.out.println();
                    System.out.println(
                        "Analisis lexico finalizado correctamente."
                    );
                    break;
                }
            }

            lector.close();

        } catch (IOException ex) {

            System.out.println(
                "Error al leer el archivo: " + ex.getMessage()
            );
        }
    }
}
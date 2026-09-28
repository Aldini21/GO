package act2_analisis_lexico;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java_cup.runtime.Symbol;

public class AnalizadorLexico {

    public static void main(String[] args) {

        System.out.println(
            "===== ANALIZADOR LEXICO DE GO - MANEJO DE ERRORES ====="
        );

        System.out.println();

        try {

            Reader lector = new BufferedReader(
                new FileReader(
                    "src/act2_analisis_lexico/tokens_go.txt"
                )
            );

            GoLexer lexer = new GoLexer(lector);

            while (true) {

                Symbol token = lexer.next_token();

                if (token.sym == sym.EOF) {
                    break;
                }
            }

            System.out.println();

            if (lexer.getHuboErrores()) {

                System.out.println(
                    "Analisis lexico finalizado con errores."
                );

            } else {

                System.out.println(
                    "Analisis lexico finalizado correctamente."
                );
            }

            lector.close();

        } catch (IOException ex) {

            System.out.println(
                "Error al leer el archivo: "
                + ex.getMessage()
            );
        }
    }
}
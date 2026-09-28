
/*
NOMBRES

*/

package act6_analisis_lexico;

import java.io.FileReader;
import java.io.Reader;
import java.util.Scanner;

public class AnalizadorLexico {

    public static void main(String[] args) {

        Scanner teclado = new Scanner(System.in);

        System.out.println(
            "===== ANALIZADOR SINTÁCTICO DE GO ====="
        );

        System.out.println(
            "1. Analizar programa válido"
        );

        System.out.println(
            "2. Analizar programa con errores"
        );

        System.out.println(
            "3. Analizar programa mixto"
        );

        System.out.print(
            "Seleccione una opción: "
        );

        int opcion = teclado.nextInt();

        String archivo;

        switch (opcion) {

            case 1:
                archivo =
                    "src/act6_analisis_lexico/"
                    + "programa_valido.txt";
                break;

            case 2:
                archivo =
                    "src/act6_analisis_lexico/"
                    + "programa_errores.txt";
                break;

            case 3:
                archivo =
                    "src/act6_analisis_lexico/"
                    + "programa_mixto.txt";
                break;

            default:
                System.out.println(
                    "Opción no válida."
                );
                teclado.close();
                return;
        }

        System.out.println();
        System.out.println(
            "===== INICIO DEL ANÁLISIS ====="
        );

        try {

            Reader lector =
                new FileReader(archivo);

            GoLexer lexer =
                new GoLexer(lector);

            ParserGo parser =
                new ParserGo(lexer);

            parser.parse();

            lector.close();

        } catch (Exception e) {

            System.out.println(
                "Error durante el análisis: "
                + e.getMessage()
            );
        }

        teclado.close();
    }
}

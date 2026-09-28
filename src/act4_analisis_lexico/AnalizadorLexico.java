/* 

*/

package act4_analisis_lexico;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.util.Scanner;

public class AnalizadorLexico {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("===== ANALIZADOR SINTÁCTICO DE GO =====");
        System.out.println("1. Analizar programa válido");
        System.out.println("2. Analizar programa con errores");
        System.out.print("Seleccione una opción: ");

        int opcion = scanner.nextInt();

        String rutaArchivo;

        switch (opcion) {

            case 1:
                rutaArchivo =
                    "src/act4_analisis_lexico/programa_valido.txt";
                break;

            case 2:
                rutaArchivo =
                    "src/act4_analisis_lexico/programa_errores.txt";
                break;

            default:
                System.out.println("Opción no válida.");
                return;
        }

        try {

            Reader lector = new BufferedReader(
                new FileReader(rutaArchivo)
            );

            GoLexer lexer = new GoLexer(lector);
            ParserJava parser = new ParserJava(lexer);

            System.out.println();
            System.out.println("===== INICIO DEL ANÁLISIS =====");

            parser.parse();

        } catch (Exception e) {

            System.out.println("Error durante el análisis sintáctico.");
            System.out.println(e.getMessage());
        }

        scanner.close();
    }
}
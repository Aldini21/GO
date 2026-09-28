package act1_analisis_lexico;

import java.io.IOException;



public class GeneradorCupSintantico {
    public static void main(String[] args) throws IOException, Exception {
        
        String[] parametros = {"-destdir", "src\\act1_analisis_lexico",
            "-parser", "ParserJava", 
            "-progress", "src\\act1_analisis_lexico\\java_lexico.cup"};
        java_cup.Main.main(parametros);      
    }
    
   
}

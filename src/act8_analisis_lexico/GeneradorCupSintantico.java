package act8_analisis_lexico;


import java.io.IOException;



public class GeneradorCupSintantico {
    public static void main(String[] args) throws IOException, Exception {
        
        String[] parametros = {"-destdir", "src\\act8_analisis_lexico",
            "-parser", "ParserGo", 
            "-progress", "src\\act8_analisis_lexico\\go_lexico.cup"};
        java_cup.Main.main(parametros);      
    }
    
   
}

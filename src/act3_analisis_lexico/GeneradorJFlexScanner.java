/*
NOMBRES DE LOS INTEGRANTGES
 */
package act3_analisis_lexico;

import jflex.Main;

/**
 *
 * @author Profesor
 */
public class GeneradorJFlexScanner {
    public static void main(String[] args) {
        
        try {
            String rutaflex="src/act3_analisis_lexico/java_lexico.jflex";
            
            String datos[]={rutaflex};
            
            Main.generate(datos);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            System.getLogger(GeneradorJFlexScanner.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        
    }
}

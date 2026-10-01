/*
NOMBRES DE LOS INTEGRANTGES
 */
package act8_analisis_lexico;

import jflex.Main;

/**
 *
 * @author Profesor
 */
public class GeneradorJFlexScanner {
    public static void main(String[] args) {
        
        try {
            String rutaflex="src/act8_analisis_lexico/go_lexico.jflex";
            
            String datos[]={rutaflex};
            
            Main.generate(datos);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            System.getLogger(GeneradorJFlexScanner.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        
    }
}

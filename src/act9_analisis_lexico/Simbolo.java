/*
NOMBRES COMPLETOS

*/

package act9_analisis_lexico;

/* =========================================================
   ACTIVIDAD 9 - CLASE SIMBOLO
   Representa una entrada de la tabla de simbolos.
   ========================================================= */

public class Simbolo {

    /* Categorias posibles de un identificador */
    public static final String CLASE = "CLASE";
    public static final String METODO = "METODO";
    public static final String VARIABLE = "VARIABLE";
    public static final String ATRIBUTO = "ATRIBUTO";
    public static final String CONSTANTE = "CONSTANTE";

    private String id;
    private String tiposimbolo;
    private String tipodato;
    private String valor;
    private String ambito;

    /* Ubicacion de origen */
    private int linea;
    private int columna;
    private String token;

    public Simbolo(
            String id,
            String tiposimbolo,
            String tipodato,
            String valor,
            String ambito,
            int linea,
            int columna,
            String token) {

        this.id = id;
        this.tiposimbolo = tiposimbolo;
        this.tipodato = tipodato;
        this.valor = valor;
        this.ambito = ambito;
        this.linea = linea;
        this.columna = columna;
        this.token = token;
    }

    public String getId() {
        return id;
    }

    public String getTiposimbolo() {
        return tiposimbolo;
    }

    public String getTipodato() {
        return tipodato;
    }

    public void setTipodato(String tipodato) {
        this.tipodato = tipodato;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getAmbito() {
        return ambito;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }

    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return String.format(
            "%-20s %-12s %-10s %-20s %-18s %-7d %-9d %s",
            id, tiposimbolo, tipodato, valor, ambito, linea, columna, token
        );
    }
}

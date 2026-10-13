/*
NOMBRES COMPLETOS

*/

package act9_analisis_lexico;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* =========================================================
   ACTIVIDAD 9 - TABLA DE SIMBOLOS
   Estructura dinamica:
   - LinkedHashMap de ambitos -> cada ambito tiene su propio
     LinkedHashMap de simbolos (respeta el orden de insercion).
   - Pila de ambitos para saber en donde estamos declarando
     y para buscar del ambito mas interno al GLOBAL.
   ========================================================= */

public class TablaSimbolos {

    public static final String GLOBAL = "GLOBAL";

    private final Map<String, Map<String, Simbolo>> ambitos =
        new LinkedHashMap<>();

    private final Deque<String> pilaAmbitos = new ArrayDeque<>();

    /* Llamadas a metodos que se validan al final, porque en Go
       un metodo puede llamarse antes de estar declarado. */
    private final List<Object[]> llamadasPendientes = new ArrayList<>();

    private int erroresSemanticos = 0;

    public TablaSimbolos() {
        abrirAmbito(GLOBAL);
    }


    /* =========================================
       GESTION DE AMBITOS
       ========================================= */

    public void abrirAmbito(String nombre) {
        pilaAmbitos.push(nombre);
        ambitos.putIfAbsent(nombre, new LinkedHashMap<>());
    }

    public void cerrarAmbito() {
        if (pilaAmbitos.size() > 1) {
            pilaAmbitos.pop();
        }
    }

    public String getAmbitoActual() {
        return pilaAmbitos.peek();
    }


    /* =========================================
       INSERCION Y BUSQUEDA
       ========================================= */

    /*
     * Declara un identificador en el ambito actual.
     * valor == null significa que no se inicializo.
     */
    public Simbolo declarar(
            String id,
            String categoria,
            String tipodato,
            String valor,
            int linea,
            int columna) {

        String ambito = getAmbitoActual();

        validarNomenclatura(id, categoria, linea, columna);

        if (valor == null) {
            validarInicializacion(id, categoria, ambito, linea, columna);
            valor = categoria.equals(Simbolo.ATRIBUTO)
                ? "null"
                : "sin_inicializar";
        }

        if (ambitos.get(ambito).containsKey(id)) {
            errorSemantico(
                "El identificador '" + id
                + "' ya ha sido declarado en el ambito '" + ambito + "'.",
                linea,
                columna
            );
            return ambitos.get(ambito).get(id);
        }

        Simbolo simbolo = new Simbolo(
            id, categoria, tipodato, valor, ambito, linea, columna, "ID"
        );

        ambitos.get(ambito).put(id, simbolo);

        System.out.println(
            "[TABLA] Simbolo insertado: " + id
            + " (" + categoria + ") en ambito " + ambito
        );

        return simbolo;
    }

    /* Busca desde el ambito mas interno hasta GLOBAL. */
    public Simbolo buscar(String id) {
        for (String ambito : pilaAmbitos) {
            Simbolo s = ambitos.get(ambito).get(id);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    public Simbolo buscarEnAmbito(String ambito, String id) {
        Map<String, Simbolo> simbolos = ambitos.get(ambito);
        return simbolos == null ? null : simbolos.get(id);
    }

    /* Reporta error si el identificador no existe en ningun ambito visible. */
    public Simbolo verificarDeclarado(String id, int linea, int columna) {
        Simbolo s = buscar(id);
        if (s == null) {
            errorSemantico(
                "El identificador '" + id
                + "' NO ha sido declarado en el ambito '"
                + getAmbitoActual() + "'.",
                linea,
                columna
            );
        }
        return s;
    }

    /* Actualiza dinamicamente el valor de un simbolo al asignarlo. */
    public void actualizarValor(String id, String valor, int linea, int columna) {
        Simbolo s = verificarDeclarado(id, linea, columna);
        if (s == null) {
            return;
        }
        if (s.getTiposimbolo().equals(Simbolo.CONSTANTE)) {
            errorSemantico(
                "La constante '" + id + "' no puede modificarse.",
                linea,
                columna
            );
            return;
        }
        s.setValor(valor);
    }

    public void registrarLlamada(String metodo, int linea, int columna) {
        llamadasPendientes.add(
            new Object[] { metodo, linea, columna, getAmbitoActual() }
        );
    }

    public void verificarLlamadasPendientes() {
        for (Object[] llamada : llamadasPendientes) {
            String metodo = (String) llamada[0];
            Simbolo s = ambitos.get(GLOBAL).get(metodo);

            if (s == null || !s.getTiposimbolo().equals(Simbolo.METODO)) {
                errorSemantico(
                    "El identificador '" + metodo
                    + "' NO ha sido declarado en el ambito '"
                    + llamada[3] + "'.",
                    (Integer) llamada[1],
                    (Integer) llamada[2]
                );
            }
        }
        llamadasPendientes.clear();
    }


    /* =========================================
       VALIDACIONES
       ========================================= */

    public static boolean esCamelCase(String id) {
        return id.matches("[a-z][a-zA-Z0-9]*");
    }

    public static boolean esPascalCase(String id) {
        return id.matches("[A-Z][a-zA-Z0-9]*");
    }

    public static boolean esSnakeCaseMayusculas(String id) {
        return id.matches("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");
    }

    private void validarNomenclatura(
            String id, String categoria, int linea, int columna) {

        String nombreCategoria;
        String convencion;
        boolean valido;

        switch (categoria) {
            case Simbolo.CLASE:
                nombreCategoria = "clase";
                convencion = "PascalCase";
                valido = esPascalCase(id);
                break;
            case Simbolo.CONSTANTE:
                nombreCategoria = "constante";
                convencion = "MAYUSCULAS separadas con _ (SNAKE_CASE)";
                valido = esSnakeCaseMayusculas(id);
                break;
            case Simbolo.METODO:
                nombreCategoria = "metodo";
                convencion = "camelCase";
                valido = esCamelCase(id);
                break;
            case Simbolo.ATRIBUTO:
                nombreCategoria = "atributo";
                convencion = "camelCase";
                valido = esCamelCase(id);
                break;
            default:
                nombreCategoria = "variable";
                convencion = "camelCase";
                valido = esCamelCase(id);
        }

        if (!valido) {
            errorSintacticoSemantico(
                "El identificador de " + nombreCategoria + " '" + id
                + "' debe utilizar la convencion " + convencion + ".",
                linea,
                columna
            );
        }
    }

    private void validarInicializacion(
            String id, String categoria, String ambito, int linea, int columna) {

        if (categoria.equals(Simbolo.VARIABLE)) {
            String local = ambito.equals(GLOBAL) ? "" : " local";
            errorSintacticoSemantico(
                "La variable" + local + " '" + id
                + "' debe ser inicializada en su declaracion.",
                linea,
                columna
            );
        } else if (categoria.equals(Simbolo.CONSTANTE)) {
            errorSintacticoSemantico(
                "La constante '" + id
                + "' requiere una asignacion obligatoria de valor inicial.",
                linea,
                columna
            );
        }
    }


    /* =========================================
       REPORTE DE ERRORES
       ========================================= */

    private void errorSintacticoSemantico(String causa, int linea, int columna) {
        erroresSemanticos++;
        System.out.println(
            "[ERROR] Error sintactico/semantico en linea " + linea
            + ", columna " + columna + ":\n        " + causa
        );
    }

    private void errorSemantico(String causa, int linea, int columna) {
        erroresSemanticos++;
        System.out.println(
            "[ERROR] Error semantico en linea " + linea
            + ", columna " + columna + ":\n        " + causa
        );
    }

    public int getErroresSemanticos() {
        return erroresSemanticos;
    }


    /* =========================================
       IMPRESION DE LA TABLA (ORGANIZADA POR AMBITOS)
       ========================================= */

    public void imprimir() {

        String doble = "=".repeat(120);
        String simple = "-".repeat(120);
        int total = 0;

        System.out.println();
        System.out.println(doble);
        System.out.println(" ".repeat(50) + "TABLA DE SIMBOLOS");
        System.out.println(doble);
        System.out.println(String.format(
            "%-20s %-12s %-10s %-20s %-18s %-7s %-9s %s",
            "ID", "Categoria", "Tipo Dato", "Valor", "Ambito",
            "Linea", "Columna", "Token Originador"
        ));

        for (Map.Entry<String, Map<String, Simbolo>> ambito : ambitos.entrySet()) {
            if (ambito.getValue().isEmpty()) {
                continue;
            }
            System.out.println(simple);
            for (Simbolo s : ambito.getValue().values()) {
                System.out.println(s);
                total++;
            }
        }

        System.out.println(doble);
        System.out.println("Total de simbolos registrados: " + total);
        System.out.println("Errores de tabla de simbolos: " + erroresSemanticos);
    }
}

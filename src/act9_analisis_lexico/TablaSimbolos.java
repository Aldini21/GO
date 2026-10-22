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

    /* ACTIVIDAD 9: tipos de los parametros de cada metodo, en orden. */
    private final Map<String, List<String>> parametrosMetodos =
        new LinkedHashMap<>();

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

    /* Actualiza dinamicamente el valor de un simbolo al asignarlo.
       ACTIVIDAD 9: tambien revisa que el tipo del valor coincida. */
    public void actualizarValor(
            String id, Simbolo e, int linea, int columna) {

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
        if (!verificarAsignacion(id, s.getTipodato(), e)) {
            return;
        }
        s.setValor(e.getValor());
    }

    /* Los argumentos se revisan en cuanto se conoce el metodo.
       Si el metodo se declara mas abajo, la llamada queda pendiente
       y se revisa al terminar el programa. */
    public void registrarLlamada(
            String metodo, List<?> argumentos, int linea, int columna) {

        Simbolo s = ambitos.get(GLOBAL).get(metodo);

        if (s != null && s.getTiposimbolo().equals(Simbolo.METODO)) {
            verificarArgumentos(metodo, argumentos, linea, columna);
            return;
        }

        llamadasPendientes.add(
            new Object[] { metodo, linea, columna, getAmbitoActual(), argumentos }
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
            } else {
                verificarArgumentos(
                    metodo,
                    (List<?>) llamada[4],
                    (Integer) llamada[1],
                    (Integer) llamada[2]
                );
            }
        }
        llamadasPendientes.clear();
    }


    /* =========================================
       ACTIVIDAD 9 - VERIFICACION DE TIPOS
       Cada expresion se representa con un Simbolo temporal
       (no se guarda en la tabla):
       - valor: el texto de la expresion.
       - tipodato: su tipo (null = desconocido, por ejemplo un
         identificador no declarado).
       - categoria LITERAL: valor sin tipo, como en Go
         (10, 10.5, 'a', "hola", true). Un literal entero puede
         guardarse en int, float32, float64, byte o rune.
       - categoria EXPRESION: valor que ya tiene tipo (variables,
         llamadas, operaciones con variables).
       - linea y columna: posicion del primer token.
       ========================================= */

    public static final String LITERAL = "LITERAL";
    public static final String EXPRESION = "EXPRESION";

    /* Tipo del valor que evalua cada switch abierto. */
    private final Deque<Simbolo> pilaSwitch = new ArrayDeque<>();

    public static Simbolo nuevaExpresion(
            String texto, String tipo, boolean literal, int linea, int columna) {

        return new Simbolo(
            texto, literal ? LITERAL : EXPRESION, tipo, texto,
            "N/A", linea, columna, EXPRESION
        );
    }

    public static boolean esLiteral(Simbolo e) {
        return e.getTiposimbolo().equals(LITERAL);
    }

    public static boolean esNumerico(String tipo) {
        return tipo.equals("int") || tipo.equals("float32")
            || tipo.equals("float64") || tipo.equals("byte")
            || tipo.equals("rune");
    }

    public static boolean esEntero(String tipo) {
        return tipo.equals("int") || tipo.equals("byte")
            || tipo.equals("rune");
    }

    /*
     * Reglas de Go para saber si un valor cabe en un tipo:
     * - Un valor con tipo solo entra en una variable del mismo tipo.
     * - Literal entero (10): int, float32, float64, byte, rune.
     * - Literal decimal (10.5): float32, float64.
     * - Literal caracter ('a'): rune, byte, int.
     * - Literal cadena ("hola"): string. Literal bool: bool.
     * Si algun tipo es desconocido no se reporta nada, para no
     * repetir un error que ya se marco antes.
     */
    public static boolean esAsignable(Simbolo e, String destino) {
        if (e == null || e.getTipodato() == null || destino == null) {
            return true;
        }

        String tipo = e.getTipodato();

        if (!esLiteral(e)) {
            return tipo.equals(destino);
        }

        switch (tipo) {
            case "int":
                return esNumerico(destino);
            case "float64":
                return destino.equals("float32") || destino.equals("float64");
            case "rune":
                return destino.equals("rune") || destino.equals("byte")
                    || destino.equals("int");
            default:
                return tipo.equals(destino);
        }
    }

    /* Revisa una declaracion o asignacion: id = e */
    public boolean verificarAsignacion(
            String id, String tipoDestino, Simbolo e) {

        if (esAsignable(e, tipoDestino)) {
            return true;
        }

        errorSemantico(
            "Tipos incompatibles: no se puede asignar un valor "
            + e.getTipodato() + " a '" + id + "' de tipo " + tipoDestino + ".",
            e.getLinea(),
            e.getColumna()
        );
        return false;
    }

    /* Tipo de un identificador usado dentro de una expresion. */
    public Simbolo tipoIdentificador(String id, int linea, int columna) {
        Simbolo s = verificarDeclarado(id, linea, columna);
        String tipo = null;

        if (s != null
                && !s.getTiposimbolo().equals(Simbolo.METODO)
                && !s.getTiposimbolo().equals(Simbolo.CLASE)) {
            tipo = s.getTipodato();
        }

        return nuevaExpresion(id, tipo, false, linea, columna);
    }

    /* Tipo que regresa una llamada usada dentro de una expresion. */
    public Simbolo tipoLlamada(String metodo, int linea, int columna) {
        Simbolo s = ambitos.get(GLOBAL).get(metodo);
        String tipo = null;

        if (s != null && s.getTiposimbolo().equals(Simbolo.METODO)) {
            if (s.getTipodato().equals("void")) {
                errorSemantico(
                    "El metodo '" + metodo
                    + "' no retorna ningun valor y no puede usarse en una expresion.",
                    linea,
                    columna
                );
            } else {
                tipo = s.getTipodato();
            }
        }

        return nuevaExpresion(metodo + "()", tipo, false, linea, columna);
    }

    /* Combina dos literales: entero < caracter < decimal. */
    private static String combinarConstantes(String a, String b) {
        if (a.equals(b)) {
            return a;
        }
        if (esNumerico(a) && esNumerico(b)) {
            if (a.equals("float64") || b.equals("float64")) {
                return "float64";
            }
            return "rune";
        }
        return null;
    }

    /* Tipo en el que se pueden combinar dos valores (null si no se puede). */
    private static String tipoComun(Simbolo a, Simbolo b) {
        if (esLiteral(a) && esLiteral(b)) {
            return combinarConstantes(a.getTipodato(), b.getTipodato());
        }
        if (esLiteral(a)) {
            return esAsignable(a, b.getTipodato()) ? b.getTipodato() : null;
        }
        if (esLiteral(b)) {
            return esAsignable(b, a.getTipodato()) ? a.getTipodato() : null;
        }
        return a.getTipodato().equals(b.getTipodato()) ? a.getTipodato() : null;
    }

    /* Revisa operaciones aritmeticas, relacionales y logicas. */
    public Simbolo operacionBinaria(
            Simbolo a, String op, Simbolo b) {

        String texto = a.getValor() + " " + op + " " + b.getValor();
        int linea = a.getLinea();
        int columna = a.getColumna();

        boolean logico = op.equals("&&") || op.equals("||");
        boolean relacional = op.equals("<") || op.equals(">")
            || op.equals("<=") || op.equals(">=")
            || op.equals("==") || op.equals("!=");

        if (logico) {
            verificarBool(a, op);
            verificarBool(b, op);
            return nuevaExpresion(texto, "bool", false, linea, columna);
        }

        String tipoError = relacional ? "bool" : null;

        if (a.getTipodato() == null || b.getTipodato() == null) {
            return nuevaExpresion(texto, tipoError, false, linea, columna);
        }

        String comun = tipoComun(a, b);
        boolean constante = esLiteral(a) && esLiteral(b);

        if (comun == null) {
            errorSemantico(
                "Tipos incompatibles en la operacion '" + texto + "': "
                + a.getTipodato() + " y " + b.getTipodato() + ".",
                linea,
                columna
            );
            return nuevaExpresion(texto, tipoError, false, linea, columna);
        }

        boolean valido;
        switch (op) {
            case "+":
            case "<":
            case ">":
            case "<=":
            case ">=":
                valido = esNumerico(comun) || comun.equals("string");
                break;
            case "-":
            case "*":
            case "/":
                valido = esNumerico(comun);
                break;
            case "%":
                valido = esEntero(comun);
                break;
            default:
                valido = true;
        }

        if (!valido) {
            errorSemantico(
                "El operador '" + op
                + "' no se puede aplicar a valores de tipo " + comun + ".",
                linea,
                columna
            );
            return nuevaExpresion(texto, tipoError, false, linea, columna);
        }

        if (relacional) {
            return nuevaExpresion(texto, "bool", constante, linea, columna);
        }
        return nuevaExpresion(texto, comun, constante, linea, columna);
    }

    public Simbolo negacion(Simbolo e, int linea, int columna) {
        verificarBool(e, "!");
        return nuevaExpresion(
            "!" + e.getValor(), "bool", esLiteral(e), linea, columna
        );
    }

    private void verificarBool(Simbolo e, String op) {
        if (e.getTipodato() != null && !e.getTipodato().equals("bool")) {
            errorSemantico(
                "El operador '" + op + "' requiere valores bool, pero se recibio "
                + e.getTipodato() + ".",
                e.getLinea(),
                e.getColumna()
            );
        }
    }

    /* Las condiciones de if y for deben ser bool. */
    public void verificarCondicion(Simbolo e, String estructura) {
        if (e.getTipodato() != null && !e.getTipodato().equals("bool")) {
            errorSemantico(
                "La condicion del '" + estructura
                + "' debe ser de tipo bool, pero se recibio " + e.getTipodato() + ".",
                e.getLinea(),
                e.getColumna()
            );
        }
    }

    /* switch: cada case debe ser del mismo tipo que el valor evaluado. */
    public void abrirSwitch(Simbolo e) {
        pilaSwitch.push(e);
    }

    public void cerrarSwitch() {
        if (!pilaSwitch.isEmpty()) {
            pilaSwitch.pop();
        }
    }

    public void verificarCaso(Simbolo caso) {
        Simbolo evaluado = pilaSwitch.peek();

        if (evaluado == null
                || evaluado.getTipodato() == null
                || caso.getTipodato() == null) {
            return;
        }

        if (tipoComun(evaluado, caso) == null) {
            errorSemantico(
                "Tipos incompatibles: el 'case' es de tipo " + caso.getTipodato()
                + ", pero el 'switch' evalua un valor " + evaluado.getTipodato() + ".",
                caso.getLinea(),
                caso.getColumna()
            );
        }
    }

    /* ++ y -- solo se aplican a variables numericas. */
    public void verificarIncremento(String id, String op, int linea, int columna) {
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
        } else if (!esNumerico(s.getTipodato())) {
            errorSemantico(
                "El operador '" + op + "' no se puede aplicar a '" + id
                + "' de tipo " + s.getTipodato() + ".",
                linea,
                columna
            );
        }
    }

    /* Tipo de retorno del metodo que se esta declarando. Se fija
       antes de leer el cuerpo para poder revisar cada return. */
    public void establecerTipoRetorno(String tipo) {
        Simbolo s = ambitos.get(GLOBAL).get(getAmbitoActual());
        if (s != null && s.getTiposimbolo().equals(Simbolo.METODO)) {
            s.setTipodato(tipo);
        }
    }

    public void verificarRetorno(Simbolo e) {
        String metodo = getAmbitoActual();
        Simbolo s = ambitos.get(GLOBAL).get(metodo);

        if (s == null || !s.getTiposimbolo().equals(Simbolo.METODO)) {
            return;
        }

        if (s.getTipodato().equals("void")) {
            errorSemantico(
                "El metodo '" + metodo
                + "' no tiene tipo de retorno, por lo que no puede retornar un valor.",
                e.getLinea(),
                e.getColumna()
            );
        } else if (!esAsignable(e, s.getTipodato())) {
            errorSemantico(
                "Tipos incompatibles: el metodo '" + metodo + "' debe retornar "
                + s.getTipodato() + ", pero se retorna un valor " + e.getTipodato() + ".",
                e.getLinea(),
                e.getColumna()
            );
        }
    }

    /* Guarda el tipo de cada parametro en orden. */
    public void agregarParametro(String metodo, String tipo) {
        parametrosMetodos
            .computeIfAbsent(metodo, k -> new ArrayList<>())
            .add(tipo);
    }

    /* Revisa cantidad y tipo de los argumentos de una llamada. */
    private void verificarArgumentos(
            String metodo, List<?> argumentos, int linea, int columna) {

        List<String> parametros =
            parametrosMetodos.getOrDefault(metodo, new ArrayList<>());

        if (parametros.size() != argumentos.size()) {
            errorSemantico(
                "El metodo '" + metodo + "' espera " + parametros.size()
                + " argumento(s), pero se recibieron " + argumentos.size() + ".",
                linea,
                columna
            );
            return;
        }

        for (int i = 0; i < parametros.size(); i++) {
            Simbolo arg = (Simbolo) argumentos.get(i);

            if (!esAsignable(arg, parametros.get(i))) {
                errorSemantico(
                    "El argumento " + (i + 1) + " de '" + metodo
                    + "' debe ser " + parametros.get(i)
                    + ", pero se recibio " + arg.getTipodato() + ".",
                    arg.getLinea(),
                    arg.getColumna()
                );
            }
        }
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

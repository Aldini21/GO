/*
NOMBRES COMPLETOS

*/

package act10_analisis_lexico;

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
                columna,
                id
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
                "No se puede reasignar un valor a la constante '" + id + "'.",
                linea,
                columna,
                id
            );
            return;
        }
        if (!verificarAsignacion(id, s.getTipodato(), e)) {
            return;
        }
        /* ACTIVIDAD 10: si la expresion tuvo un error se conserva
           el valor anterior. */
        if (e.getTipodato() == null || esError(e)) {
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

    /* ACTIVIDAD 10: expresion que ya reporto un error de tipos. */
    public static final String CON_ERROR = "ERROR";

    private static Simbolo expresionConError(
            String texto, String tipo, int linea, int columna) {

        return new Simbolo(
            texto, CON_ERROR, tipo, texto, "N/A", linea, columna, EXPRESION
        );
    }

    public static boolean esError(Simbolo e) {
        return e.getTiposimbolo().equals(CON_ERROR);
    }

    /* ( expresion ): conserva el tipo y si tuvo error. */
    public static Simbolo parentesis(Simbolo e, int linea, int columna) {
        String texto = "(" + e.getValor() + ")";
        if (esError(e)) {
            return expresionConError(texto, e.getTipodato(), linea, columna);
        }
        return nuevaExpresion(texto, e.getTipodato(), esLiteral(e), linea, columna);
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

    /* Nombre de la categoria para los mensajes de error. */
    private String nombreCategoria(String id) {
        Simbolo s = buscar(id);
        if (s == null) {
            return "variable";
        }
        switch (s.getTiposimbolo()) {
            case Simbolo.CONSTANTE:
                return "constante";
            case Simbolo.ATRIBUTO:
                return "atributo";
            default:
                return "variable";
        }
    }

    /* Revisa una declaracion o asignacion: id = e */
    public boolean verificarAsignacion(
            String id, String tipoDestino, Simbolo e) {

        return verificarAsignacion(id, nombreCategoria(id), tipoDestino, e);
    }

    private boolean verificarAsignacion(
            String id, String categoria, String tipoDestino, Simbolo e) {

        if (esAsignable(e, tipoDestino)) {
            return true;
        }

        errorSemantico(
            "Incompatibilidad de tipos: No se puede asignar un valor de tipo '"
            + e.getTipodato() + "' " + (categoria.equals("atributo") ? "al " : "a la ")
            + categoria + " '" + id
            + "' de tipo '" + tipoDestino + "'.",
            e.getLinea(),
            e.getColumna(),
            e.getValor()
        );
        return false;
    }

    /* =========================================
       ACTIVIDAD 10 - STRUCTS Y ATRIBUTOS
       ========================================= */

    public boolean esStruct(String tipo) {
        Simbolo s = tipo == null ? null : ambitos.get(GLOBAL).get(tipo);
        return s != null && s.getTiposimbolo().equals(Simbolo.CLASE);
    }

    /* var alumno Alumno = Alumno{}; */
    public void declararInstancia(
            String id, String tipo, String literal,
            int linea, int columna, int lineaLit, int columnaLit) {

        if (!esStruct(tipo)) {
            errorSemantico(
                "El tipo '" + tipo + "' no ha sido declarado como struct.",
                linea,
                columna,
                tipo
            );
        } else if (!tipo.equals(literal)) {
            errorSemantico(
                "Incompatibilidad de tipos: No se puede asignar un valor de tipo '"
                + literal + "' a la variable '" + id + "' de tipo '" + tipo + "'.",
                lineaLit,
                columnaLit,
                literal + "{}"
            );
        }

        declarar(id, Simbolo.VARIABLE, tipo, literal + "{}", linea, columna);
    }

    /* Busca el atributo 'campo' del struct de la variable 'objeto'. */
    private Simbolo buscarAtributo(
            String objeto, String campo, int linea, int columna) {

        Simbolo s = verificarDeclarado(objeto, linea, columna);
        if (s == null) {
            return null;
        }

        if (!esStruct(s.getTipodato())) {
            errorSemantico(
                "El identificador '" + objeto + "' es de tipo '" + s.getTipodato()
                + "' y no tiene atributos.",
                linea,
                columna,
                objeto + "." + campo
            );
            return null;
        }

        Simbolo atributo = buscarEnAmbito(s.getTipodato(), campo);
        if (atributo == null) {
            errorSemantico(
                "El struct '" + s.getTipodato() + "' no tiene un atributo llamado '"
                + campo + "'.",
                linea,
                columna,
                objeto + "." + campo
            );
        }
        return atributo;
    }

    /* objeto.campo dentro de una expresion */
    public Simbolo tipoAtributo(String objeto, String campo, int linea, int columna) {
        Simbolo atributo = buscarAtributo(objeto, campo, linea, columna);
        String tipo = atributo == null ? null : atributo.getTipodato();
        return nuevaExpresion(objeto + "." + campo, tipo, false, linea, columna);
    }

    /* objeto.campo = valor; */
    public void asignarAtributo(
            String objeto, String campo, Simbolo e, int linea, int columna) {

        Simbolo atributo = buscarAtributo(objeto, campo, linea, columna);
        if (atributo != null) {
            verificarAsignacion(
                objeto + "." + campo, "atributo", atributo.getTipodato(), e
            );
        }
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
                    columna,
                    metodo + "()"
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

    /* Tipo de operador para los mensajes de error. */
    private static String claseOperador(String op) {
        switch (op) {
            case "&&":
            case "||":
            case "!":
                return "logico";
            case "<":
            case ">":
            case "<=":
            case ">=":
            case "==":
            case "!=":
                return "relacional";
            default:
                return "aritmetico";
        }
    }

    /* Revisa si un operando puede usarse con el operador. */
    private static boolean aplicaOperador(String op, String tipo) {
        switch (op) {
            case "+":
                return esNumerico(tipo) || tipo.equals("string");
            case "-":
            case "*":
            case "/":
                return esNumerico(tipo);
            case "%":
                return esEntero(tipo);
            case "<":
            case ">":
            case "<=":
            case ">=":
                return esNumerico(tipo) || tipo.equals("string");
            case "&&":
            case "||":
            case "!":
                return tipo.equals("bool");
            default:
                return true;
        }
    }

    /*
     * ACTIVIDAD 10 - Reglas de verificacion en expresiones (Go):
     * 1. Cada operando debe ser valido para el operador
     *    (aritmeticos: numericos, + tambien string, % solo enteros;
     *    <, >, <=, >=: numericos o string; ==, !=: cualquier tipo;
     *    &&, ||, !: solo bool).
     * 2. Ambos operandos deben tener un tipo comun. Go no convierte
     *    variables automaticamente (int + float64 es error), pero los
     *    literales si se adaptan (10 + 2.5 da float64).
     * 3. Relacionales y logicos siempre dan bool.
     * Si hay error el tipo queda desconocido para no repetir errores.
     */
    public Simbolo operacionBinaria(
            Simbolo a, String op, Simbolo b) {

        String texto = a.getValor() + " " + op + " " + b.getValor();
        int linea = a.getLinea();
        int columna = a.getColumna();
        String clase = claseOperador(op);
        boolean daBool = !clase.equals("aritmetico");
        String tipoError = daBool ? "bool" : null;

        if (a.getTipodato() == null || b.getTipodato() == null) {
            return expresionConError(texto, tipoError, linea, columna);
        }

        if (!aplicaOperador(op, a.getTipodato())
                || !aplicaOperador(op, b.getTipodato())) {
            errorSemantico(
                "El operador " + clase + " '" + op
                + "' no es aplicable a los operandos de tipo '"
                + a.getTipodato() + "' y '" + b.getTipodato() + "'.",
                linea,
                columna,
                texto
            );
            return expresionConError(texto, tipoError, linea, columna);
        }

        String comun = tipoComun(a, b);
        boolean constante = esLiteral(a) && esLiteral(b);

        if (comun == null) {
            errorSemantico(
                "Incompatibilidad de tipos: los operandos de tipo '"
                + a.getTipodato() + "' y '" + b.getTipodato()
                + "' no se pueden combinar con el operador '" + op + "'.",
                linea,
                columna,
                texto
            );
            return expresionConError(texto, tipoError, linea, columna);
        }

        if (esError(a) || esError(b)) {
            return expresionConError(
                texto, daBool ? "bool" : comun, linea, columna
            );
        }

        if (daBool) {
            return nuevaExpresion(texto, "bool", constante, linea, columna);
        }
        return nuevaExpresion(texto, comun, constante, linea, columna);
    }

    public Simbolo negacion(Simbolo e, int linea, int columna) {
        String texto = "!" + e.getValor();

        if (e.getTipodato() != null && !aplicaOperador("!", e.getTipodato())) {
            errorSemantico(
                "El operador logico '!' no es aplicable al operando de tipo '"
                + e.getTipodato() + "'.",
                linea,
                columna,
                texto
            );
            return expresionConError(texto, "bool", linea, columna);
        }
        if (esError(e) || e.getTipodato() == null) {
            return expresionConError(texto, "bool", linea, columna);
        }
        return nuevaExpresion(texto, "bool", esLiteral(e), linea, columna);
    }

    /* Las condiciones de if y for deben ser bool. */
    public void verificarCondicion(Simbolo e, String estructura) {
        if (e.getTipodato() != null && !e.getTipodato().equals("bool")) {
            errorSemantico(
                "La expresion dentro de la condicion '" + estructura
                + "' debe ser de tipo 'bool', se encontro '" + e.getTipodato() + "'.",
                e.getLinea(),
                e.getColumna(),
                e.getValor()
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
                "Incompatibilidad de tipos: el 'case' es de tipo '" + caso.getTipodato()
                + "', pero el 'switch' evalua un valor de tipo '"
                + evaluado.getTipodato() + "'.",
                caso.getLinea(),
                caso.getColumna(),
                caso.getValor()
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
                "No se puede reasignar un valor a la constante '" + id + "'.",
                linea,
                columna,
                id + op
            );
        } else if (!esNumerico(s.getTipodato())) {
            errorSemantico(
                "El operador aritmetico '" + op + "' no es aplicable a '" + id
                + "' de tipo '" + s.getTipodato() + "'.",
                linea,
                columna,
                id + op
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
                e.getColumna(),
                e.getValor()
            );
        } else if (!esAsignable(e, s.getTipodato())) {
            errorSemantico(
                "Incompatibilidad de tipos: el metodo '" + metodo + "' debe retornar '"
                + s.getTipodato() + "', pero se retorna un valor de tipo '"
                + e.getTipodato() + "'.",
                e.getLinea(),
                e.getColumna(),
                e.getValor()
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
                    "Incompatibilidad de tipos: el argumento " + (i + 1) + " de '"
                    + metodo + "' debe ser de tipo '" + parametros.get(i)
                    + "', pero se recibio un valor de tipo '" + arg.getTipodato() + "'.",
                    arg.getLinea(),
                    arg.getColumna(),
                    arg.getValor()
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

    /* ACTIVIDAD 10: tambien muestra el token u operando involucrado. */
    private void errorSemantico(
            String causa, int linea, int columna, String token) {

        errorSemantico(causa + "\n        Token: " + token, linea, columna);
    }

    public int getErroresSemanticos() {
        return erroresSemanticos;
    }

    /* Errores de las otras fases, para el reporte final. */
    private int erroresLexicos = 0;
    private int erroresSintacticos = 0;

    public void setErroresLexicos(int n) {
        erroresLexicos = n;
    }

    public void setErroresSintacticos(int n) {
        erroresSintacticos = n;
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

        imprimirResultado();
    }

    /* ACTIVIDAD 10 - REPORTE DEL ANALISIS SEMANTICO */
    private void imprimirResultado() {

        String doble = "=".repeat(120);
        int total = erroresLexicos + erroresSintacticos + erroresSemanticos;

        System.out.println();
        System.out.println(doble);
        System.out.println(" ".repeat(42) + "RESULTADO DEL ANALISIS SEMANTICO");
        System.out.println(doble);
        System.out.println("Lenguaje: Go (tipado estatico)");
        System.out.println("Errores lexicos:     " + erroresLexicos);
        System.out.println("Errores sintacticos: " + erroresSintacticos);
        System.out.println("Errores semanticos:  " + erroresSemanticos);
        System.out.println("Total de errores:    " + total);

        if (total == 0) {
            System.out.println(
                "Resultado: programa VALIDO, todos los tipos de datos son compatibles."
            );
        } else {
            System.out.println(
                "Resultado: programa con ERRORES, revise los mensajes anteriores."
            );
        }
        System.out.println(doble);
    }
}

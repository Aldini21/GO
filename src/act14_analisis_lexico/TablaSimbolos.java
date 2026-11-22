package act14_analisis_lexico;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /* ACTIVIDAD 11: contexto del metodo que se esta analizando. */
    private Simbolo metodoActual = null;
    private String claveMetodoActual = null;
    private int contadorBloques = 0;

    /* ACTIVIDAD 11: estructuras abiertas que permiten 'break'
       ("for" o "switch") y si ya tuvieron un 'break'. */
    private final Deque<String[]> pilaControl = new ArrayDeque<>();

    /* ACTIVIDAD 11: paquetes importados (import "fmt"). */
    private final Set<String> paquetesImportados = new HashSet<>();

    /* ACTIVIDAD 11: revisiones que se hacen al final, porque en Go
       el orden de las declaraciones globales no importa. */
    private final List<Object[]> receptoresPendientes = new ArrayList<>();
    private final List<Object[]> llamadasStructPendientes = new ArrayList<>();

    /* =========================================
       ACTIVIDAD 12 - CODIGO INTERMEDIO (TRES DIRECCIONES)
       - codigo: cada instruccion con su comentario.
       - lugares: nombre (variable o temporal tN) donde queda el
         resultado de cada expresion.
       ========================================= */
    private final List<String[]> codigo = new ArrayList<>();
    private final Map<Simbolo, String> lugares = new IdentityHashMap<>();
    private int contadorTemporales = 0;

    /* ACTIVIDAD 13: etiquetas (L1, L2...) y pilas de estructuras
       abiertas para generar los saltos del control de flujo.
       pilaIf: {etiqueta falso/else, etiqueta fin}.
       pilaCodigo (for y switch):
       [0] "for" o "switch", [1] inicio, [2] fin, [3] continue,
       [4] posicion de la etiqueta de inicio, [5] posicion donde
       empieza la actualizacion, [6] instrucciones de la actualizacion,
       [7] etiqueta del siguiente case, [8] temporal del switch. */
    private int contadorEtiquetas = 0;
    private final Deque<String[]> pilaIf = new ArrayDeque<>();
    private final Deque<Object[]> pilaCodigo = new ArrayDeque<>();

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
            errorSemantico(causaNoDeclarado(id), linea, columna, id);
        }
        return s;
    }

    /* ACTIVIDAD 11: explica por que un identificador no es visible.
       - Si existe en otro ambito (otro metodo o un bloque ya cerrado)
         esta fuera de ambito.
       - Si es un miembro de un struct, en Go se accede con el
         receptor u objeto (c.base), nunca solo por su nombre. */
    private String causaNoDeclarado(String id) {
        for (Map.Entry<String, Map<String, Simbolo>> a : ambitos.entrySet()) {
            if (pilaAmbitos.contains(a.getKey())) {
                continue;
            }
            Simbolo otro = a.getValue().get(id);
            if (otro == null) {
                continue;
            }
            if (otro.getTiposimbolo().equals(Simbolo.ATRIBUTO)
                    || (otro.getTiposimbolo().equals(Simbolo.METODO) && esStruct(a.getKey()))) {
                return "El identificador '" + id + "' es miembro del struct '"
                    + a.getKey() + "'; en Go debe accederse mediante un objeto o receptor ("
                    + "objeto." + id + ").";
            }
            return "El identificador '" + id + "' no esta disponible en el ambito actual '"
                + getAmbitoActual() + "'; fue declarado en el ambito '" + a.getKey()
                + "', que no es visible desde aqui.";
        }
        return "El identificador '" + id
            + "' NO ha sido declarado en el ambito '" + getAmbitoActual() + "'.";
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
        generarAsignacion(id, e);
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
                    "El metodo '" + metodo
                    + "' no esta declarado o no puede resolverse en este contexto.",
                    (Integer) llamada[1],
                    (Integer) llamada[2],
                    metodo + "()"
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

        /* ACTIVIDAD 11: llamadas objeto.metodo() a metodos declarados
           mas abajo en el archivo. */
        for (Object[] llamada : llamadasStructPendientes) {
            String tipo = (String) llamada[0];
            String metodo = (String) llamada[1];
            Simbolo m = buscarEnAmbito(tipo, metodo);

            if (m == null || !m.getTiposimbolo().equals(Simbolo.METODO)) {
                errorSemantico(
                    "El metodo '" + metodo + "' no esta declarado en el struct '" + tipo
                    + "' o no puede resolverse en este contexto.",
                    (Integer) llamada[3],
                    (Integer) llamada[4],
                    (String) llamada[5]
                );
            } else {
                verificarArgumentos(
                    tipo + "." + metodo, (List<?>) llamada[2],
                    (Integer) llamada[3], (Integer) llamada[4]
                );
            }
        }
        llamadasStructPendientes.clear();

        /* ACTIVIDAD 11: el tipo de cada receptor debe ser un struct. */
        for (Object[] r : receptoresPendientes) {
            if (!esStruct((String) r[0])) {
                errorSemantico(
                    "El tipo '" + r[0] + "' del receptor no ha sido declarado como struct.",
                    (Integer) r[1],
                    (Integer) r[2],
                    (String) r[0]
                );
            }
        }
        receptoresPendientes.clear();
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
    public Simbolo parentesis(Simbolo e, int linea, int columna) {
        String texto = "(" + e.getValor() + ")";
        if (esError(e)) {
            return expresionConError(texto, e.getTipodato(), linea, columna);
        }
        Simbolo r = nuevaExpresion(texto, e.getTipodato(), esLiteral(e), linea, columna);
        /* ACTIVIDAD 12: los parentesis no generan instruccion */
        String l = lugar(e);
        if (l != null) {
            lugares.put(r, l);
        }
        return r;
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
        emitir(
            "error \"Type mismatch\"",
            "no se asigna " + e.getTipodato() + " a " + id + " (" + tipoDestino + ")"
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

        /* ACTIVIDAD 13: creacion del objeto */
        if (esStruct(tipo) && tipo.equals(literal)) {
            emitir(id + " = create " + tipo, "crea un objeto de la clase " + tipo);
        }
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
                "El atributo '" + campo + "' no esta declarado en el struct '"
                + s.getTipodato() + "'.",
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
        if (atributo != null
                && verificarAsignacion(
                    objeto + "." + campo, "atributo", atributo.getTipodato(), e)) {
            generarAsignacion(objeto + "." + campo, e);
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
            emitir("error \"Type mismatch\"",
                "operador '" + op + "' invalido para " + a.getTipodato()
                + " y " + b.getTipodato());
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
            emitir("error \"Type mismatch\"",
                a.getTipodato() + " " + op + " " + b.getTipodato() + " no se pueden combinar");
            return expresionConError(texto, tipoError, linea, columna);
        }

        if (esError(a) || esError(b)) {
            return expresionConError(
                texto, daBool ? "bool" : comun, linea, columna
            );
        }

        /* ACTIVIDAD 12: division entre cero */
        if (op.equals("/") || op.equals("%")) {
            if (esCeroConstante(b)) {
                errorSemantico(
                    "Division por cero: el divisor de la operacion '" + op
                    + "' es la constante " + b.getValor() + ".",
                    linea,
                    columna,
                    texto
                );
                emitir("error \"Division by zero\"", "el divisor es la constante 0");
                return expresionConError(texto, comun, linea, columna);
            }
            if (!esLiteral(b)) {
                emitir("if " + lugar(b) + " == 0 then error \"Division by zero\"",
                    "valida que no se divida entre cero");
            }
        }

        Simbolo r = daBool
            ? nuevaExpresion(texto, "bool", constante, linea, columna)
            : nuevaExpresion(texto, comun, constante, linea, columna);

        /* ACTIVIDAD 12: tN = a op b */
        String t = nuevoTemporal();
        emitir(t + " = " + lugar(a) + " " + op + " " + lugar(b),
            t + " es una etiqueta temporal para " + nombreOperacion(op)
            + " " + lugar(a) + " " + op + " " + lugar(b));
        lugares.put(r, t);
        return r;
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
        Simbolo r = nuevaExpresion(texto, "bool", esLiteral(e), linea, columna);

        /* ACTIVIDAD 12: tN = !a */
        String t = nuevoTemporal();
        emitir(t + " = !" + lugar(e),
            t + " es una etiqueta temporal para la negacion de " + lugar(e));
        lugares.put(r, t);
        return r;
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
    /* ACTIVIDAD 11: valores literales usados en los case de cada switch. */
    private final Deque<Set<String>> pilaCasos = new ArrayDeque<>();

    public void abrirSwitch(Simbolo e) {
        pilaSwitch.push(e);
        pilaCasos.push(new HashSet<>());
        pilaControl.push(new String[] { "switch", "no" });
    }

    /* Regresa true si dentro del switch hubo un 'break'. */
    public boolean cerrarSwitch() {
        if (!pilaSwitch.isEmpty()) {
            pilaSwitch.pop();
            pilaCasos.pop();
        }
        return salirControl();
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
            return;
        }

        /* ACTIVIDAD 11: en Go no se puede repetir un valor constante
           en los case de un mismo switch. */
        if (esLiteral(caso) && !pilaCasos.isEmpty()
                && !pilaCasos.peek().add(caso.getValor())) {
            errorSemantico(
                "El valor " + caso.getValor()
                + " esta duplicado en otro 'case' del mismo 'switch'.",
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
        } else {
            /* ACTIVIDAD 12: i++ -> tN = i + 1 ; i = tN */
            String signo = op.equals("++") ? "+" : "-";
            String t = nuevoTemporal();
            emitir(t + " = " + id + " " + signo + " 1",
                t + " es una etiqueta temporal para " + id + " " + signo + " 1");
            emitir(id + " = " + t, "asigna el valor de " + t + " a " + id);
        }
    }

    /* =========================================
       ACTIVIDAD 11 - METODOS Y FUNCIONES
       ========================================= */

    /* func nombre(...): se registra en GLOBAL y se abre su ambito. */
    public void iniciarFuncion(String nombre, int linea, int columna) {
        Simbolo s = ambitos.get(GLOBAL).get(nombre);

        if (s != null) {
            validarNomenclatura(nombre, Simbolo.METODO, linea, columna);
            errorSemantico(
                "El identificador '" + nombre + "' ya esta declarado en el ambito 'GLOBAL'"
                + (s.getTiposimbolo().equals(Simbolo.METODO)
                    ? "; Go no admite sobrecarga de metodos." : "."),
                linea, columna, nombre
            );
            /* Go no tiene sobrecarga: el cuerpo se analiza aparte
               para no mezclar sus parametros con el original. */
            metodoActual = new Simbolo(nombre, Simbolo.METODO, "void", "func()",
                GLOBAL, linea, columna, "ID");
            claveMetodoActual = nombre + "#" + linea;
        } else {
            metodoActual = declarar(nombre, Simbolo.METODO, "void", "func()", linea, columna);
            claveMetodoActual = nombre;
        }
        entrarMetodo();
    }

    /* func (c Calculadora) nombre(...): el metodo pertenece al struct
       del receptor y se guarda dentro del ambito de ese struct. */
    public void iniciarMetodoStruct(
            String receptor, String tipo, String nombre,
            int lineaRec, int columnaRec, int lineaTipo, int columnaTipo,
            int linea, int columna) {

        validarNomenclatura(nombre, Simbolo.METODO, linea, columna);
        ambitos.putIfAbsent(tipo, new LinkedHashMap<>());
        Map<String, Simbolo> miembros = ambitos.get(tipo);
        Simbolo existente = miembros.get(nombre);

        metodoActual = new Simbolo(nombre, Simbolo.METODO, "void", "func()",
            tipo, linea, columna, "ID");
        claveMetodoActual = tipo + "." + nombre;

        if (existente != null) {
            String causa = existente.getTiposimbolo().equals(Simbolo.ATRIBUTO)
                ? "El struct '" + tipo + "' ya tiene un atributo llamado '" + nombre
                    + "'; un metodo no puede tener el mismo nombre que un atributo."
                : "El metodo '" + nombre + "' ya esta declarado para el struct '"
                    + tipo + "'.";
            errorSemantico(causa, linea, columna, nombre);
            claveMetodoActual = claveMetodoActual + "#" + linea;
        } else {
            miembros.put(nombre, metodoActual);
            System.out.println(
                "[TABLA] Simbolo insertado: " + nombre
                + " (" + Simbolo.METODO + ") en ambito " + tipo
            );
        }

        receptoresPendientes.add(new Object[] { tipo, lineaTipo, columnaTipo });
        entrarMetodo();
        declarar(receptor, Simbolo.VARIABLE, tipo, "N/A (Receptor)", lineaRec, columnaRec);
        emitir(receptor + " = this", "recibe el objeto " + tipo + " que llama al metodo");
    }

    private void entrarMetodo() {
        emitir("", "");
        emitir(claveMetodoActual.replaceAll("#.*", "") + ":",
            "inicio de " + claveMetodoActual.replaceAll("#.*", ""));
        contadorBloques = 0;
        pilaControl.clear();
        pilaIf.clear();
        pilaCodigo.clear();
        parametrosMetodos.putIfAbsent(claveMetodoActual, new ArrayList<>());
        abrirAmbito(claveMetodoActual);
    }

    /* Al cerrar el metodo se guarda su firma en la tabla. */
    public void finalizarFuncion() {
        if (metodoActual != null) {
            List<String> ps = parametrosMetodos.get(claveMetodoActual);
            StringBuilder firma = new StringBuilder("func(");
            for (int i = 0; i < ps.size(); i++) {
                firma.append(i > 0 ? ", " : "").append(ps.get(i));
            }
            firma.append(")");
            if (!metodoActual.getTipodato().equals("void")) {
                firma.append(" ").append(metodoActual.getTipodato());
            }
            metodoActual.setValor(firma.toString());

            /* ACTIVIDAD 13: fin del metodo */
            String nombre = claveMetodoActual.replaceAll("#.*", "");
            emitir("end " + nombre, "fin del metodo " + nombre);
        }
        while (pilaAmbitos.size() > 1) {
            pilaAmbitos.pop();
        }
        metodoActual = null;
        claveMetodoActual = null;
        pilaControl.clear();
    }

    private String nombreMetodoActual() {
        return metodoActual.getAmbito().equals(GLOBAL)
            ? metodoActual.getId()
            : metodoActual.getAmbito() + "." + metodoActual.getId();
    }

    /* Tipo de retorno del metodo que se esta declarando. Se fija
       antes de leer el cuerpo para poder revisar cada return. */
    public void establecerTipoRetorno(String tipo) {
        if (metodoActual != null) {
            metodoActual.setTipodato(tipo);
        }
    }

    /* return expresion; */
    public void verificarRetorno(Simbolo e) {
        if (metodoActual == null) {
            errorSemantico(
                "La instruccion 'return' no puede utilizarse fuera de un metodo o funcion.",
                e.getLinea(), e.getColumna(), "return"
            );
            return;
        }

        String metodo = nombreMetodoActual();
        String tipo = metodoActual.getTipodato();

        if (tipo.equals("void")) {
            errorSemantico(
                "El metodo '" + metodo
                + "' no declara tipo de retorno, por lo que no puede retornar un valor.",
                e.getLinea(),
                e.getColumna(),
                e.getValor()
            );
        } else if (!esAsignable(e, tipo)) {
            errorSemantico(
                "El metodo '" + metodo + "' declara un retorno de tipo '" + tipo
                + "', pero la expresion retornada es de tipo '" + e.getTipodato() + "'.",
                e.getLinea(),
                e.getColumna(),
                e.getValor()
            );
        } else if (lugar(e) != null && !esError(e)) {
            emitir("return " + lugar(e), "regresa el valor de " + lugar(e));
        }
    }

    /* return; solo es valido en metodos sin tipo de retorno. */
    public void verificarRetornoVacio(int linea, int columna) {
        if (metodoActual == null) {
            errorSemantico(
                "La instruccion 'return' no puede utilizarse fuera de un metodo o funcion.",
                linea, columna, "return"
            );
        } else if (!metodoActual.getTipodato().equals("void")) {
            errorSemantico(
                "El metodo '" + nombreMetodoActual() + "' declara un retorno de tipo '"
                + metodoActual.getTipodato() + "', por lo que 'return' debe devolver un valor.",
                linea,
                columna,
                "return"
            );
        } else {
            emitir("return", "regresa sin valor");
        }
    }

    /* En Go un metodo con tipo de retorno debe terminar con una
       sentencia terminal: return, if-else donde ambas ramas terminan,
       for sin condicion y sin break, o switch con default donde
       todos los casos terminan. */
    public void verificarTerminacion(Boolean termina, int linea, int columna) {
        if (metodoActual != null
                && !metodoActual.getTipodato().equals("void")
                && !Boolean.TRUE.equals(termina)) {
            errorSemantico(
                "El metodo '" + nombreMetodoActual()
                + "' debe devolver un valor compatible con el tipo '"
                + metodoActual.getTipodato()
                + "' en todos los caminos de ejecucion (falta 'return').",
                linea,
                columna,
                "}"
            );
        }
    }

    /* Registra un parametro con su tipo y su posicion. */
    public void declararParametro(String nombre, String tipo, int linea, int columna) {
        List<String> ps = parametrosMetodos.computeIfAbsent(
            claveMetodoActual, k -> new ArrayList<>());
        ps.add(tipo);
        if (tipo != null) {
            declarar(nombre, Simbolo.VARIABLE, tipo, "Parametro " + ps.size(), linea, columna);
            emitir(nombre + " = arg" + ps.size(),
                "recibe el parametro " + ps.size() + " en " + nombre);
        }
    }

    /* objeto.metodo(args): el metodo debe existir en el struct del
       objeto y recibir los argumentos correctos. */
    public Simbolo llamadaMetodoStruct(
            String objeto, String metodo, List<?> argumentos,
            boolean enExpresion, int linea, int columna) {

        String texto = objeto + "." + metodo + "()";
        Simbolo s = verificarDeclarado(objeto, linea, columna);

        if (s == null) {
            return nuevaExpresion(texto, null, false, linea, columna);
        }
        if (!esStruct(s.getTipodato())) {
            errorSemantico(
                "El identificador '" + objeto + "' es de tipo '" + s.getTipodato()
                + "' y no tiene metodos.",
                linea, columna, texto
            );
            return nuevaExpresion(texto, null, false, linea, columna);
        }

        String tipo = s.getTipodato();
        Simbolo m = buscarEnAmbito(tipo, metodo);

        if (m != null && !m.getTiposimbolo().equals(Simbolo.METODO)) {
            errorSemantico(
                "'" + metodo + "' es un atributo del struct '" + tipo
                + "', no un metodo; no puede invocarse con '()'.",
                linea, columna, texto
            );
            return nuevaExpresion(texto, null, false, linea, columna);
        }
        if (m == null) {
            llamadasStructPendientes.add(
                new Object[] { tipo, metodo, argumentos, linea, columna, texto }
            );
            return nuevaExpresion(texto, null, false, linea, columna);
        }

        verificarArgumentos(tipo + "." + metodo, argumentos, linea, columna);

        String retorno = null;
        if (m.getTipodato().equals("void")) {
            if (enExpresion) {
                errorSemantico(
                    "El metodo '" + tipo + "." + metodo
                    + "' no retorna ningun valor y no puede usarse en una expresion.",
                    linea, columna, texto
                );
            }
        } else {
            retorno = m.getTipodato();
        }
        return nuevaExpresion(texto, retorno, false, linea, columna);
    }


    /* =========================================
       ACTIVIDAD 11 - AMBITOS DE BLOQUE Y CONTROL
       ========================================= */

    /* Cada bloque { }, cada for y cada case abre su propio ambito. */
    public void abrirBloque(String tipo) {
        contadorBloques++;
        String base = claveMetodoActual == null ? GLOBAL : claveMetodoActual;
        String nombre = base + "." + tipo + contadorBloques;
        pilaAmbitos.push(nombre);
        ambitos.put(nombre, new LinkedHashMap<>());
    }

    /* for: abre el ambito de la inicializacion y permite break/continue. */
    public void entrarCiclo() {
        abrirBloque("for");
        pilaControl.push(new String[] { "for", "no" });
    }

    /* Regresa true si dentro del ciclo hubo un 'break'. */
    public boolean salirCiclo() {
        cerrarAmbito();
        return salirControl();
    }

    private boolean salirControl() {
        if (pilaControl.isEmpty()) {
            return false;
        }
        return pilaControl.pop()[1].equals("si");
    }

    /* break: solo dentro de un for o un switch. */
    public void verificarBreak(int linea, int columna) {
        if (pilaControl.isEmpty()) {
            errorSemantico(
                "La instruccion 'break' no se encuentra dentro de una estructura"
                + " que permita su utilizacion ('for' o 'switch').",
                linea, columna, "break"
            );
            return;
        }
        pilaControl.peek()[1] = "si";
    }

    /* continue: solo dentro de un for. */
    public void verificarContinue(int linea, int columna) {
        for (String[] estructura : pilaControl) {
            if (estructura[0].equals("for")) {
                return;
            }
        }
        errorSemantico(
            "La instruccion 'continue' solo puede utilizarse dentro de un ciclo 'for'.",
            linea, columna, "continue"
        );
    }

    /* x := expresion; el tipo se toma de la expresion (en Go un
       literal entero da int, decimal float64, caracter rune). */
    public void declaracionCorta(String id, Simbolo e, int linea, int columna) {
        String tipo = esError(e) ? null : e.getTipodato();
        String valor = tipo == null ? "N/A" : e.getValor();
        declarar(id, Simbolo.VARIABLE, tipo, valor, linea, columna);
        if (tipo != null) {
            generarAsignacion(id, e);
        }
    }


    /* =========================================
       ACTIVIDAD 12 - GENERACION DE CODIGO INTERMEDIO
       ========================================= */

    private String nuevoTemporal() {
        contadorTemporales++;
        return "t" + contadorTemporales;
    }

    private void emitir(String instruccion, String comentario) {
        codigo.add(new String[] { instruccion, comentario });
    }

    /* Nombre donde queda el valor de una expresion:
       - operacion: su temporal tN.
       - variable o atributo: su nombre.
       - constante: su valor (5, 2.5, "hola", true).
       null si la expresion tuvo un error. */
    public String lugar(Simbolo e) {
        if (e == null) {
            return null;
        }
        if (lugares.containsKey(e)) {
            return lugares.get(e);
        }
        if (esError(e) || e.getTipodato() == null) {
            return null;
        }
        return e.getValor();
    }

    /* destino = expresion. Una constante primero se guarda en un
       temporal (t1 = 5 ; x = t1), como pide la actividad. */
    public void generarAsignacion(String destino, Simbolo e) {
        String l = lugar(e);
        if (l == null || esError(e)) {
            return;
        }
        if (esLiteral(e) && !lugares.containsKey(e)) {
            String t = nuevoTemporal();
            emitir(t + " = " + l,
                t + " es una etiqueta temporal que representa la constante " + l);
            l = t;
        }
        emitir(destino + " = " + l, "asigna el valor de " + l + " a " + destino);
    }

    /* param a ; param b ; tN = call f, 2 */
    public void generarLlamada(String metodo, List<?> argumentos, Simbolo resultado) {
        for (Object o : argumentos) {
            String l = lugar((Simbolo) o);
            if (l == null) {
                return;
            }
            emitir("param " + l, "envia " + l + " como argumento de " + metodo);
        }
        if (resultado != null && resultado.getTipodato() != null) {
            String t = nuevoTemporal();
            emitir(t + " = call " + metodo + ", " + argumentos.size(),
                t + " guarda el resultado de " + metodo);
            lugares.put(resultado, t);
        } else {
            emitir("call " + metodo + ", " + argumentos.size(), "llama a " + metodo);
        }
    }

    private static boolean esCeroConstante(Simbolo e) {
        if (!esLiteral(e) || e.getTipodato() == null
                || !esNumerico(e.getTipodato()) || e.getTipodato().equals("rune")) {
            return false;
        }
        try {
            return Double.parseDouble(e.getValor()) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String nombreOperacion(String op) {
        switch (op) {
            case "+": return "la suma";
            case "-": return "la resta";
            case "*": return "la multiplicacion";
            case "/": return "la division";
            case "%": return "el modulo";
            case "&&": return "la operacion logica AND";
            case "||": return "la operacion logica OR";
            default: return "la comparacion";
        }
    }

    /* =========================================
       ACTIVIDAD 13 - CODIGO INTERMEDIO PARA CONTROL DE FLUJO
       ========================================= */

    private String nuevaEtiqueta() {
        contadorEtiquetas++;
        return "L" + contadorEtiquetas;
    }

    private void etiqueta(String nombre, String comentario) {
        emitir(nombre + ":", comentario);
    }

    private String condicion(Simbolo e) {
        String l = lugar(e);
        return l == null ? (e == null ? "?" : e.getValor()) : l;
    }

    /* Una condicion que no es bool ya se reporto como error semantico;
       en el codigo queda marcada antes del salto. */
    private void validarCondicion(Simbolo e) {
        if (e != null && e.getTipodato() != null && !e.getTipodato().equals("bool")) {
            emitir("error \"Type mismatch\"",
                "la condicion " + e.getValor() + " es " + e.getTipodato() + ", no bool");
        }
    }

    /* if: si la condicion es falsa salta al else o al final. */
    public void codigoIf(Simbolo e) {
        String falso = nuevaEtiqueta();
        pilaIf.push(new String[] { falso, null });
        validarCondicion(e);
        String c = condicion(e);
        emitir("if not " + c + " goto " + falso,
            "si " + c + " es falso, salta a " + falso);
    }

    /* Al terminar el bloque if, salta al final y empieza el else. */
    public void codigoElse() {
        String[] f = pilaIf.peek();
        if (f == null) {
            return;
        }
        f[1] = nuevaEtiqueta();
        emitir("goto " + f[1], "al terminar el bloque if salta a " + f[1]);
        etiqueta(f[0], "etiqueta " + f[0] + " para el bloque else");
    }

    public void codigoFinIf() {
        String[] f = pilaIf.poll();
        if (f == null) {
            return;
        }
        if (f[1] == null) {
            etiqueta(f[0], "etiqueta " + f[0] + " marca el final del if");
        } else {
            etiqueta(f[1], "etiqueta " + f[1] + " marca el final del if-else");
        }
    }

    /* for: la etiqueta de inicio se pone al leer 'for'. */
    public void codigoInicioCiclo() {
        Object[] f = new Object[] {
            "for", nuevaEtiqueta(), nuevaEtiqueta(), null,
            codigo.size(), -1, new ArrayList<String[]>(), null, null
        };
        pilaCodigo.push(f);
        etiqueta((String) f[1], "etiqueta " + f[1] + " marca el inicio del ciclo");
    }

    /* for i := 0; ... la inicializacion va antes de la etiqueta de
       inicio, por eso la etiqueta se mueve despues de ella. */
    public void codigoMoverInicioCiclo() {
        Object[] f = pilaCodigo.peek();
        if (f == null) {
            return;
        }
        codigo.add(codigo.remove((int) (Integer) f[4]));
    }

    public void codigoCondicionCiclo(Simbolo e) {
        Object[] f = pilaCodigo.peek();
        if (f == null) {
            return;
        }
        validarCondicion(e);
        String c = condicion(e);
        emitir("if not " + c + " goto " + f[2],
            "si " + c + " es falso, salta a " + f[2] + " (fin del ciclo)");
        f[5] = codigo.size();
    }

    /* La actualizacion (i++) se lee antes del cuerpo, pero debe
       ejecutarse al final de cada vuelta: se guarda para despues. */
    @SuppressWarnings("unchecked")
    public void codigoGuardarActualizacion() {
        Object[] f = pilaCodigo.peek();
        if (f == null || (Integer) f[5] < 0) {
            return;
        }
        List<String[]> actualizacion = (List<String[]>) f[6];
        int desde = (Integer) f[5];
        while (codigo.size() > desde) {
            actualizacion.add(codigo.remove(desde));
        }
    }

    @SuppressWarnings("unchecked")
    public void codigoFinCiclo() {
        Object[] f = pilaCodigo.poll();
        if (f == null) {
            return;
        }
        if (f[3] != null) {
            etiqueta((String) f[3], "etiqueta " + f[3] + " para continue");
        }
        codigo.addAll((List<String[]>) f[6]);
        emitir("goto " + f[1], "salta de vuelta al inicio del ciclo");
        etiqueta((String) f[2], "etiqueta " + f[2] + " marca el final del ciclo");
    }

    /* switch: el valor evaluado se guarda en un temporal. */
    public void codigoInicioSwitch(Simbolo e) {
        String t = nuevoTemporal();
        String c = condicion(e);
        emitir(t + " = " + c, t + " es una etiqueta temporal para el valor de " + c);
        pilaCodigo.push(new Object[] {
            "switch", null, nuevaEtiqueta(), null, -1, -1, null, null, t
        });
    }

    public void codigoCaso(Simbolo valor) {
        Object[] f = pilaCodigo.peek();
        if (f == null || !"switch".equals(f[0])) {
            return;
        }
        if (f[7] != null) {
            etiqueta((String) f[7], "etiqueta " + f[7] + " para el siguiente case");
        }
        String siguiente = nuevaEtiqueta();
        f[7] = siguiente;
        String v = condicion(valor);
        emitir("if " + f[8] + " != " + v + " goto " + siguiente,
            "si " + f[8] + " no es " + v + ", salta a " + siguiente);
    }

    public void codigoFinCaso() {
        Object[] f = pilaCodigo.peek();
        if (f != null && "switch".equals(f[0])) {
            emitir("goto " + f[2], "termina el case y salta a " + f[2]);
        }
    }

    public void codigoDefault() {
        Object[] f = pilaCodigo.peek();
        if (f != null && "switch".equals(f[0]) && f[7] != null) {
            etiqueta((String) f[7], "etiqueta " + f[7] + " para el caso default");
            f[7] = null;
        }
    }

    public void codigoFinSwitch() {
        Object[] f = pilaCodigo.poll();
        if (f == null) {
            return;
        }
        if (f[7] != null) {
            etiqueta((String) f[7], "ningun case coincidio");
        }
        etiqueta((String) f[2], "etiqueta " + f[2] + " marca el final del switch");
    }

    /* break: salta al final del for o switch mas cercano. */
    public void codigoBreak() {
        Object[] f = pilaCodigo.peek();
        if (f != null) {
            emitir("goto " + f[2], "break: salta a " + f[2]);
        }
    }

    /* continue: salta a la actualizacion del for mas cercano. */
    public void codigoContinue() {
        for (Object[] f : pilaCodigo) {
            if ("for".equals(f[0])) {
                if (f[3] == null) {
                    f[3] = nuevaEtiqueta();
                }
                emitir("goto " + f[3], "continue: salta a " + f[3]);
                return;
            }
        }
    }

    /* Definicion de la clase (struct) y sus atributos. */
    public void codigoStruct(String nombre) {
        StringBuilder atributos = new StringBuilder();
        Map<String, Simbolo> miembros = ambitos.get(nombre);
        if (miembros != null) {
            for (Simbolo s : miembros.values()) {
                if (s.getTiposimbolo().equals(Simbolo.ATRIBUTO)) {
                    atributos.append(atributos.length() > 0 ? ", " : "")
                        .append(s.getId()).append(" ").append(s.getTipodato());
                }
            }
        }
        emitir("class " + nombre,
            "definicion de la clase " + nombre + " (" + atributos + ")");
    }


    /* =========================================================
       ACTIVIDAD 14 - OPTIMIZACION DEL CODIGO INTERMEDIO
       Se trabaja sobre una copia del codigo de tres direcciones.
       Cada instruccion se guarda como { instruccion, comentario,
       numero de la instruccion en el codigo original }.
       Reglas (se aplican en orden y se repiten hasta que ya no
       hay cambios):
       R1. Codigo inalcanzable: lo que sigue a un goto, return o
           error, hasta la siguiente etiqueta.
       R2. Saltos innecesarios: goto a la etiqueta que sigue.
       R3. Etiquetas sin referencia: ningun salto llega a ellas.
       R4. Plegado de constantes: operaciones entre constantes y
           simplificacion de calculos (x + 5 + 10 -> x + 15).
       R5. Calculos repetidos: misma operacion con los mismos
           operandos en el mismo bloque.
       R6. Asignaciones sobrescritas: el valor se reemplaza antes
           de usarse.
       R7. Variables y temporales no utilizados.
       R8. Asignaciones innecesarias (copias): t1 = 5 ; x = t1
           se convierte en x = 5.
       Al final se ejecutan el codigo original y el optimizado con
       un interprete sencillo para validar que den el mismo
       resultado.
       ========================================================= */

    private final List<String[]> optimizado = new ArrayList<>();
    private final List<String[]> bitacora = new ArrayList<>();
    private final Map<String, Integer> cambiosPorRegla = new LinkedHashMap<>();

    private static final String[] REGLAS = {
        "R1. Codigo inalcanzable",
        "R2. Saltos innecesarios",
        "R3. Etiquetas sin referencia",
        "R4. Plegado y simplificacion de constantes",
        "R5. Calculos repetidos",
        "R6. Asignaciones sobrescritas",
        "R7. Variables y temporales no utilizados",
        "R8. Asignaciones innecesarias (copias)"
    };

    private void registrarCambio(int regla, String detalle) {
        bitacora.add(new String[] { REGLAS[regla - 1], detalle });
        cambiosPorRegla.merge(REGLAS[regla - 1], 1, Integer::sum);
    }

    /* Separa una instruccion en partes respetando las cadenas "..." */
    private static List<String> partes(String s) {
        List<String> r = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ') {
                i++;
            } else if (c == '"' || c == '\'') {
                int j = s.indexOf(c, i + 1);
                j = j < 0 ? s.length() - 1 : j;
                r.add(s.substring(i, j + 1));
                i = j + 1;
            } else {
                int j = i;
                while (j < s.length() && s.charAt(j) != ' ') {
                    j++;
                }
                r.add(s.substring(i, j));
                i = j;
            }
        }
        return r;
    }

    private static boolean esIdentificador(String s) {
        return s.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?")
            && !s.equals("true") && !s.equals("false");
    }

    private static boolean esTemporal(String s) {
        return s.matches("t[0-9]+");
    }

    private static boolean esEtiqueta(String ins) {
        return ins.endsWith(":");
    }

    private static boolean esEtiquetaSalto(String ins) {
        return ins.matches("L[0-9]+:");
    }

    private static boolean esLimite(String ins) {
        return ins.isEmpty() || esEtiqueta(ins) || ins.startsWith("end ")
            || ins.startsWith("class ");
    }

    private static boolean esSalto(String ins) {
        return ins.startsWith("goto ") || ins.startsWith("if ");
    }

    /* Variable que recibe el valor (null si la instruccion no asigna). */
    private static String destino(String ins) {
        List<String> p = partes(ins);
        if (p.size() >= 3 && p.get(1).equals("=") && !p.get(0).equals("if")) {
            return p.get(0);
        }
        return null;
    }

    private static String ladoDerecho(String ins) {
        return ins.substring(ins.indexOf(" = ") + 3);
    }

    /* Variables que la instruccion lee. p.edad tambien cuenta como uso de p. */
    private static List<String> usos(String ins) {
        List<String> r = new ArrayList<>();
        List<String> p = partes(ins);
        List<String> leidos = new ArrayList<>();

        if (ins.isEmpty() || esEtiqueta(ins) || ins.startsWith("end ")
                || ins.startsWith("class ") || ins.startsWith("goto ")
                || ins.startsWith("error ")) {
            return r;
        }
        if (ins.startsWith("call ")) {
            leidos.add(objetoDeLlamada(p.get(1)));
        } else if (ins.startsWith("param ") || ins.startsWith("return ")) {
            leidos.add(ins.substring(ins.indexOf(' ') + 1));
        } else if (ins.startsWith("if not ")) {
            leidos.add(p.get(2));
        } else if (ins.startsWith("if ")) {
            leidos.add(p.get(1));
            leidos.add(p.get(3));
        } else if (destino(ins) != null) {
            String d = p.get(0);
            if (d.contains(".")) {
                leidos.add(d.substring(0, d.indexOf('.')));
            }
            List<String> der = p.subList(2, p.size());
            if (der.get(0).equals("call")) {
                leidos.add(objetoDeLlamada(der.get(1).replace(",", "")));
            } else if (!der.get(0).equals("create") && !der.get(0).equals("this")
                    && !der.get(0).matches("arg[0-9]+")) {
                for (String x : der) {
                    leidos.add(x.startsWith("!") ? x.substring(1) : x);
                }
            }
        }
        for (String x : leidos) {
            if (x != null && esIdentificador(x)) {
                r.add(x);
                if (x.contains(".")) {
                    r.add(x.substring(0, x.indexOf('.')));
                }
            }
        }
        return r;
    }

    private static String objetoDeLlamada(String metodo) {
        metodo = metodo.replace(",", "");
        if (metodo.contains(".") && !metodo.startsWith("fmt.")) {
            return metodo.substring(0, metodo.indexOf('.'));
        }
        return null;
    }

    private int contarUsos(List<String[]> cod, String var) {
        int n = 0;
        for (String[] i : cod) {
            for (String u : usos(i[0])) {
                if (u.equals(var)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean esNumeroEntero(String s) {
        return s.matches("-?[0-9]+");
    }

    private static boolean esConstante(String s) {
        return s.matches("-?[0-9]+(\\.[0-9]+)?") || s.startsWith("\"")
            || s.startsWith("'") || s.equals("true") || s.equals("false");
    }

    private static String etiquetaDestino(String ins) {
        List<String> p = partes(ins);
        return p.get(p.size() - 1);
    }

    private static String texto(String[] i) {
        return "'" + i[0] + "' (instr. " + i[2] + ")";
    }

    /* ---------------- reglas ---------------- */

    private boolean reglaInalcanzable(List<String[]> cod) {
        boolean cambio = false;
        for (int i = 0; i < cod.size(); i++) {
            String ins = cod.get(i)[0];
            boolean corta = ins.startsWith("goto ") || ins.equals("return")
                || ins.startsWith("return ") || ins.startsWith("error ");
            if (!corta) {
                continue;
            }
            while (i + 1 < cod.size() && !esLimite(cod.get(i + 1)[0])) {
                registrarCambio(1, "Se elimina " + texto(cod.get(i + 1))
                    + ": nunca se ejecuta porque esta despues de '" + ins + "'.");
                cod.remove(i + 1);
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean reglaSaltoInnecesario(List<String[]> cod) {
        boolean cambio = false;
        for (int i = 0; i < cod.size(); i++) {
            String ins = cod.get(i)[0];
            if (!ins.startsWith("goto ")) {
                continue;
            }
            String l = etiquetaDestino(ins) + ":";
            for (int j = i + 1; j < cod.size() && esEtiquetaSalto(cod.get(j)[0]); j++) {
                if (cod.get(j)[0].equals(l)) {
                    registrarCambio(2, "Se elimina " + texto(cod.get(i))
                        + ": salta a la etiqueta que sigue, el flujo llega ahi solo.");
                    cod.remove(i);
                    i--;
                    cambio = true;
                    break;
                }
            }
        }
        return cambio;
    }

    private boolean reglaEtiquetasSinReferencia(List<String[]> cod) {
        Set<String> usadas = new HashSet<>();
        for (String[] i : cod) {
            if (esSalto(i[0])) {
                usadas.add(etiquetaDestino(i[0]));
            }
        }
        boolean cambio = false;
        for (int i = 0; i < cod.size(); i++) {
            String ins = cod.get(i)[0];
            if (esEtiquetaSalto(ins) && !usadas.contains(ins.substring(0, ins.length() - 1))) {
                registrarCambio(3, "Se elimina " + texto(cod.get(i))
                    + ": ningun salto llega a esta etiqueta.");
                cod.remove(i);
                i--;
                cambio = true;
            }
        }
        return cambio;
    }

    private static String operar(String a, String op, String b) {
        if (!esNumeroEntero(a) || !esNumeroEntero(b)) {
            return null;
        }
        long x = Long.parseLong(a);
        long y = Long.parseLong(b);
        switch (op) {
            case "+": return String.valueOf(x + y);
            case "-": return String.valueOf(x - y);
            case "*": return String.valueOf(x * y);
            case "/": return y == 0 ? null : String.valueOf(x / y);
            case "%": return y == 0 ? null : String.valueOf(x % y);
            case "<": return String.valueOf(x < y);
            case ">": return String.valueOf(x > y);
            case "<=": return String.valueOf(x <= y);
            case ">=": return String.valueOf(x >= y);
            case "==": return String.valueOf(x == y);
            case "!=": return String.valueOf(x != y);
            default: return null;
        }
    }

    private boolean reglaPlegado(List<String[]> cod) {
        boolean cambio = false;
        for (int i = 0; i < cod.size(); i++) {
            String[] inst = cod.get(i);
            String d = destino(inst[0]);
            if (d == null) {
                continue;
            }
            List<String> der = partes(ladoDerecho(inst[0]));

            /* tN = constante usada una sola vez dentro de una operacion:
               la constante se pone directo en la operacion. */
            if (esTemporal(d) && der.size() == 1 && esNumeroEntero(der.get(0))
                    && contarUsos(cod, d) == 1) {
                boolean hecho = false;
                for (int j = i + 1; j < cod.size() && !hecho; j++) {
                    String[] uso = cod.get(j);
                    String dj = destino(uso[0]);
                    if (dj != null && partes(ladoDerecho(uso[0])).size() == 3
                            && usos(uso[0]).contains(d)) {
                        String antes = uso[0];
                        uso[0] = reemplazarUso(uso[0], d, der.get(0));
                        registrarCambio(4, "La constante de '" + inst[0] + "' (instr. " + inst[2]
                            + ") se sustituye en '" + antes + "' -> '" + uso[0] + "'.");
                        hecho = true;
                    }
                    if (esLimite(uso[0]) || esSalto(uso[0])) {
                        break;
                    }
                }
                if (hecho) {
                    cod.remove(i);
                    i--;
                    cambio = true;
                }
                continue;
            }
            if (der.size() != 3) {
                continue;
            }
            String a = der.get(0), op = der.get(1), b = der.get(2);

            /* a op b con dos constantes enteras */
            String r = operar(a, op, b);
            if (r != null) {
                String antes = inst[0];
                inst[0] = d + " = " + r;
                inst[1] = "optimizado: " + a + " " + op + " " + b + " se calcula en compilacion";
                registrarCambio(4, "'" + antes + "' (instr. " + inst[2] + ") se reemplaza por '"
                    + inst[0] + "'.");
                cambio = true;
                continue;
            }
            /* x + 0, x - 0, x * 1, x / 1 */
            if ((op.equals("+") || op.equals("-")) && b.equals("0")
                    || (op.equals("*") || op.equals("/")) && b.equals("1")) {
                String antes = inst[0];
                inst[0] = d + " = " + a;
                inst[1] = "optimizado: operacion neutra con " + b;
                registrarCambio(4, "'" + antes + "' (instr. " + inst[2] + ") se reemplaza por '"
                    + inst[0] + "'.");
                cambio = true;
                continue;
            }
            /* t1 = x + c1 ; t2 = t1 + c2  ->  t2 = x + (c1 + c2) */
            if (i + 1 < cod.size() && esTemporal(d)
                    && (op.equals("+") || op.equals("-")) && esNumeroEntero(b)
                    && contarUsos(cod, d) == 1) {
                String[] sig = cod.get(i + 1);
                String d2 = destino(sig[0]);
                if (d2 == null) {
                    continue;
                }
                List<String> der2 = partes(ladoDerecho(sig[0]));
                if (der2.size() == 3 && der2.get(0).equals(d)
                        && (der2.get(1).equals("+") || der2.get(1).equals("-"))
                        && esNumeroEntero(der2.get(2))) {
                    long c1 = Long.parseLong(b) * (op.equals("-") ? -1 : 1);
                    long c2 = Long.parseLong(der2.get(2)) * (der2.get(1).equals("-") ? -1 : 1);
                    long c = c1 + c2;
                    String antes = inst[0] + " ; " + sig[0];
                    sig[0] = d2 + " = " + a + (c < 0 ? " - " + (-c) : " + " + c);
                    sig[1] = "optimizado: " + a + " " + op + " " + b + " " + der2.get(1)
                        + " " + der2.get(2) + " se simplifica";
                    registrarCambio(4, "'" + antes + "' (instr. " + inst[2] + " y " + sig[2]
                        + ") se simplifica en '" + sig[0] + "'.");
                    cod.remove(i);
                    i--;
                    cambio = true;
                }
            }
        }
        return cambio;
    }

    private boolean reglaCalculosRepetidos(List<String[]> cod) {
        boolean cambio = false;
        Map<String, String> calculos = new LinkedHashMap<>();
        for (int i = 0; i < cod.size(); i++) {
            String ins = cod.get(i)[0];
            if (esLimite(ins) || esSalto(ins) || ins.startsWith("call ")
                    || ins.contains(" = call ")) {
                calculos.clear();
                continue;
            }
            String d = destino(ins);
            if (d == null) {
                continue;
            }
            List<String> der = partes(ladoDerecho(ins));
            String clave = der.size() == 3 ? String.join(" ", der) : null;

            if (clave != null && esTemporal(d) && calculos.containsKey(clave)) {
                String previo = calculos.get(clave);
                registrarCambio(5, "Se elimina " + texto(cod.get(i)) + ": '" + clave
                    + "' ya se calculo en " + previo + "; se usa " + previo + " en lugar de " + d + ".");
                cod.remove(i);
                for (int j = i; j < cod.size(); j++) {
                    String nuevo = reemplazarUso(cod.get(j)[0], d, previo);
                    if (!nuevo.equals(cod.get(j)[0])) {
                        cod.get(j)[0] = nuevo;
                        cod.get(j)[1] = "optimizado: usa " + previo + " (calculo repetido)";
                    }
                }
                i--;
                cambio = true;
                continue;
            }
            /* al cambiar una variable dejan de valer los calculos que la usan */
            String base = d.contains(".") ? d.substring(0, d.indexOf('.')) : d;
            calculos.entrySet().removeIf(e ->
                partes(e.getKey()).contains(d) || partes(e.getKey()).contains(base)
                || e.getValue().equals(d));
            if (clave != null && esTemporal(d) && !der.contains(d)) {
                calculos.put(clave, d);
            }
        }
        return cambio;
    }

    /* Cambia un operando completo (no partes de otro nombre). */
    private static String reemplazarUso(String ins, String viejo, String nuevo) {
        List<String> p = partes(ins);
        int desde = destino(ins) != null ? 1 : 0;
        boolean cambio = false;
        for (int k = desde; k < p.size(); k++) {
            if (p.get(k).equals(viejo)) {
                p.set(k, nuevo);
                cambio = true;
            } else if (p.get(k).equals("!" + viejo)) {
                p.set(k, "!" + nuevo);
                cambio = true;
            }
        }
        return cambio ? String.join(" ", p) : ins;
    }

    private boolean reglaSobrescritas(List<String[]> cod) {
        boolean cambio = false;
        for (int i = 0; i < cod.size(); i++) {
            String ins = cod.get(i)[0];
            String d = destino(ins);
            if (d == null || ins.contains(" = call ")) {
                continue;
            }
            for (int j = i + 1; j < cod.size(); j++) {
                String sig = cod.get(j)[0];
                if (esLimite(sig) || esSalto(sig) || sig.startsWith("call ")
                        || sig.startsWith("param ") || sig.startsWith("return")
                        || sig.startsWith("error") || usos(sig).contains(d)) {
                    break;
                }
                if (d.equals(destino(sig))) {
                    registrarCambio(6, "Se elimina " + texto(cod.get(i)) + ": " + d
                        + " se sobrescribe en '" + sig + "' antes de usarse.");
                    cod.remove(i);
                    i--;
                    cambio = true;
                    break;
                }
            }
        }
        return cambio;
    }

    private boolean reglaNoUtilizadas(List<String[]> cod) {
        boolean cambio = false;
        Set<String> avisadas = new HashSet<>();
        for (int i = 0; i < cod.size(); i++) {
            String ins = cod.get(i)[0];
            String d = destino(ins);
            if (d == null || d.contains(".") || ins.contains(" = call ")) {
                continue;
            }
            if (contarUsos(cod, d) == 0) {
                String tipo = esTemporal(d) ? "el temporal" : "la variable";
                registrarCambio(7, "Se elimina " + texto(cod.get(i)) + ": " + tipo + " '" + d
                    + "' recibe un valor pero nunca se usa.");
                avisadas.add(d);
                cod.remove(i);
                i--;
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean reglaCopias(List<String[]> cod) {
        boolean cambio = false;
        for (int i = 0; i + 1 < cod.size(); i++) {
            String[] a = cod.get(i);
            String[] b = cod.get(i + 1);
            String d = destino(a[0]);
            String d2 = destino(b[0]);
            if (d == null || d2 == null || d.contains(".") || d.equals(d2)) {
                continue;
            }
            String der = ladoDerecho(a[0]);
            if (der.equals("this") || der.matches("arg[0-9]+") || der.startsWith("create ")) {
                continue;
            }
            if (!ladoDerecho(b[0]).equals(d) || contarUsos(cod, d) != 1) {
                continue;
            }
            if (der.startsWith("call ") && d2.contains(".")) {
                continue;
            }
            String antes = a[0] + " ; " + b[0];
            b[0] = d2 + " = " + der;
            b[1] = "optimizado: se asigna directo, sin pasar por " + d;
            registrarCambio(8, "'" + antes + "' (instr. " + a[2] + " y " + b[2]
                + ") se combinan en '" + b[0] + "'.");
            cod.remove(i);
            i--;
            cambio = true;
        }
        return cambio;
    }

    private void optimizar() {
        optimizado.clear();
        bitacora.clear();
        cambiosPorRegla.clear();
        int n = 0;
        for (String[] inst : codigo) {
            if (inst[0].isEmpty()) {
                optimizado.add(new String[] { "", "", "" });
            } else {
                n++;
                optimizado.add(new String[] { inst[0], inst[1], String.valueOf(n) });
            }
        }
        boolean cambio = true;
        int vueltas = 0;
        while (cambio && vueltas < 20) {
            cambio = false;
            cambio |= reglaInalcanzable(optimizado);
            cambio |= reglaSaltoInnecesario(optimizado);
            cambio |= reglaEtiquetasSinReferencia(optimizado);
            cambio |= reglaPlegado(optimizado);
            cambio |= reglaCalculosRepetidos(optimizado);
            cambio |= reglaSobrescritas(optimizado);
            cambio |= reglaNoUtilizadas(optimizado);
            cambio |= reglaCopias(optimizado);
            vueltas++;
        }
    }

    /* ---------------- interprete para validar ---------------- */

    private static class FinEjecucion extends RuntimeException {
        FinEjecucion(String m) {
            super(m);
        }
    }

    private int pasos;

    private Object valor(String s, Map<String, Object> marco, Map<String, Object> globales) {
        if (s.startsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        if (s.startsWith("'")) {
            return (long) s.charAt(1);
        }
        if (s.equals("true") || s.equals("false")) {
            return Boolean.valueOf(s);
        }
        if (s.matches("-?[0-9]+")) {
            return Long.valueOf(s);
        }
        if (s.matches("-?[0-9]+\\.[0-9]+")) {
            return Double.valueOf(s);
        }
        if (s.startsWith("!")) {
            return !((Boolean) valor(s.substring(1), marco, globales));
        }
        if (s.contains(".")) {
            Object obj = valor(s.substring(0, s.indexOf('.')), marco, globales);
            if (obj instanceof Map) {
                Object v = ((Map<?, ?>) obj).get(s.substring(s.indexOf('.') + 1));
                return v == null ? 0L : v;
            }
            return 0L;
        }
        if (marco.containsKey(s)) {
            return marco.get(s);
        }
        if (globales.containsKey(s)) {
            return globales.get(s);
        }
        return 0L;
    }

    private static double num(Object o) {
        return o instanceof Long ? (Long) o : o instanceof Double ? (Double) o : 0;
    }

    private static Object calcular(Object a, String op, Object b) {
        if (op.equals("&&")) {
            return (Boolean) a && (Boolean) b;
        }
        if (op.equals("||")) {
            return (Boolean) a || (Boolean) b;
        }
        if (op.equals("==")) {
            return igual(a, b);
        }
        if (op.equals("!=")) {
            return !igual(a, b);
        }
        if (a instanceof String || b instanceof String) {
            String x = String.valueOf(a), y = String.valueOf(b);
            switch (op) {
                case "+": return x + y;
                case "<": return x.compareTo(y) < 0;
                case ">": return x.compareTo(y) > 0;
                case "<=": return x.compareTo(y) <= 0;
                case ">=": return x.compareTo(y) >= 0;
                default: return x;
            }
        }
        boolean enteros = a instanceof Long && b instanceof Long;
        double x = num(a), y = num(b);
        switch (op) {
            case "+": return enteros ? (Object) ((Long) a + (Long) b) : x + y;
            case "-": return enteros ? (Object) ((Long) a - (Long) b) : x - y;
            case "*": return enteros ? (Object) ((Long) a * (Long) b) : x * y;
            case "/":
                if (y == 0) {
                    throw new FinEjecucion("error \"Division by zero\"");
                }
                return enteros ? (Object) ((Long) a / (Long) b) : x / y;
            case "%":
                if (y == 0) {
                    throw new FinEjecucion("error \"Division by zero\"");
                }
                return (Long) a % (Long) b;
            case "<": return x < y;
            case ">": return x > y;
            case "<=": return x <= y;
            case ">=": return x >= y;
            default: return 0L;
        }
    }

    private static boolean igual(Object a, Object b) {
        if (a instanceof Long || a instanceof Double) {
            return num(a) == num(b);
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private Object llamar(List<String[]> cod, Map<String, Integer> etiquetas, String metodo,
            List<Object> args, Object esto, Map<String, Object> globales, List<String> salida) {

        Integer inicio = etiquetas.get(metodo + ":");
        if (inicio == null) {
            return 0L;
        }
        Map<String, Object> marco = new LinkedHashMap<>();
        List<Object> pendientes = new ArrayList<>();
        int pc = inicio + 1;

        while (pc < cod.size()) {
            if (++pasos > 200000) {
                throw new FinEjecucion("limite de pasos alcanzado");
            }
            String ins = cod.get(pc)[0];
            List<String> p = partes(ins);
            pc++;

            if (ins.isEmpty() || esEtiqueta(ins) || ins.startsWith("class ")) {
                continue;
            }
            if (ins.startsWith("end ") || ins.equals("return")) {
                return null;
            }
            if (ins.startsWith("return ")) {
                return valor(ins.substring(7), marco, globales);
            }
            if (ins.startsWith("goto ")) {
                pc = etiquetas.get(p.get(1) + ":");
                continue;
            }
            if (ins.startsWith("error ")) {
                throw new FinEjecucion(ins);
            }
            if (ins.startsWith("if not ")) {
                if (!Boolean.TRUE.equals(valor(p.get(2), marco, globales))) {
                    pc = etiquetas.get(p.get(4) + ":");
                }
                continue;
            }
            if (ins.startsWith("if ") && ins.contains(" then error ")) {
                if (num(valor(p.get(1), marco, globales)) == 0) {
                    throw new FinEjecucion("error \"Division by zero\"");
                }
                continue;
            }
            if (ins.startsWith("if ")) {
                if (!igual(valor(p.get(1), marco, globales), valor(p.get(3), marco, globales))) {
                    pc = etiquetas.get(p.get(5) + ":");
                }
                continue;
            }
            if (ins.startsWith("param ")) {
                pendientes.add(valor(ins.substring(6), marco, globales));
                continue;
            }

            String d = destino(ins);
            if (d == null && !ins.startsWith("call ")) {
                continue;
            }
            String der = d != null ? ladoDerecho(ins) : ins;
            Object v;

            if (der.startsWith("call ")) {
                List<String> c = partes(der);
                String m = c.get(1).replace(",", "");
                int n = Integer.parseInt(c.get(2));
                List<Object> a = new ArrayList<>(
                    pendientes.subList(pendientes.size() - n, pendientes.size()));
                pendientes.subList(pendientes.size() - n, pendientes.size()).clear();
                if (m.equals("fmt.Println") || m.equals("fmt.Print")) {
                    StringBuilder sb = new StringBuilder();
                    for (Object o : a) {
                        sb.append(sb.length() > 0 ? " " : "").append(o);
                    }
                    salida.add(sb.toString());
                    v = null;
                } else if (m.contains(".")) {
                    Object obj = valor(m.substring(0, m.indexOf('.')), marco, globales);
                    String tipo = obj instanceof Map ? String.valueOf(((Map<?, ?>) obj).get("#tipo")) : "";
                    v = llamar(cod, etiquetas, tipo + m.substring(m.indexOf('.')), a, obj,
                        globales, salida);
                } else {
                    v = llamar(cod, etiquetas, m, a, null, globales, salida);
                }
                if (d == null) {
                    continue;
                }
            } else if (der.startsWith("create ")) {
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("#tipo", der.substring(7));
                v = obj;
            } else if (der.equals("this")) {
                v = esto;
            } else if (der.matches("arg[0-9]+")) {
                int k = Integer.parseInt(der.substring(3)) - 1;
                v = k < args.size() ? args.get(k) : 0L;
            } else {
                List<String> e = partes(der);
                v = e.size() == 3
                    ? calcular(valor(e.get(0), marco, globales), e.get(1), valor(e.get(2), marco, globales))
                    : valor(der, marco, globales);
            }

            if (d.contains(".")) {
                Object obj = valor(d.substring(0, d.indexOf('.')), marco, globales);
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> o = (Map<String, Object>) obj;
                    o.put(d.substring(d.indexOf('.') + 1), v);
                }
            } else if (globales.containsKey(d) && !marco.containsKey(d)) {
                globales.put(d, v);
            } else {
                marco.put(d, v);
            }
        }
        return null;
    }

    /* Ejecuta main y regresa lo que imprime fmt.Println. */
    private List<String> ejecutar(List<String[]> cod) {
        List<String> salida = new ArrayList<>();
        Map<String, Integer> etiquetas = new LinkedHashMap<>();
        for (int i = 0; i < cod.size(); i++) {
            if (esEtiqueta(cod.get(i)[0])) {
                etiquetas.put(cod.get(i)[0], i);
            }
        }
        /* constantes globales: instrucciones fuera de los metodos */
        Map<String, Object> globales = new LinkedHashMap<>();
        boolean dentro = false;
        for (String[] i : cod) {
            String ins = i[0];
            if (esEtiqueta(ins) && !esEtiquetaSalto(ins)) {
                dentro = true;
            } else if (ins.startsWith("end ")) {
                dentro = false;
            } else if (!dentro && destino(ins) != null) {
                List<String> e = partes(ladoDerecho(ins));
                globales.put(destino(ins), e.size() == 3
                    ? calcular(valor(e.get(0), globales, globales), e.get(1), valor(e.get(2), globales, globales))
                    : valor(ladoDerecho(ins), globales, globales));
            }
        }
        pasos = 0;
        try {
            llamar(cod, etiquetas, "main", new ArrayList<>(), null, globales, salida);
        } catch (FinEjecucion | ClassCastException | NullPointerException | IndexOutOfBoundsException ex) {
            salida.add("[ejecucion detenida: " + ex.getMessage() + "]");
        }
        return salida;
    }

    /* ---------------- impresion ---------------- */

    private static int contarInstrucciones(List<String[]> cod) {
        int n = 0;
        for (String[] i : cod) {
            if (!i[0].isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private void imprimirOptimizacion() {
        String doble = "=".repeat(136);
        String simple = "-".repeat(136);
        int total = erroresLexicos + erroresSintacticos + erroresSemanticos;

        System.out.println();
        System.out.println(doble);
        System.out.println(" ".repeat(40) + "OPTIMIZACION DEL CODIGO INTERMEDIO (ACTIVIDAD 14)");
        System.out.println(doble);

        if (total > 0) {
            System.out.println("El programa tiene " + total + " error(es); el codigo intermedio no se"
                + " optimiza porque contiene instrucciones de error y no representa un programa valido.");
            System.out.println(doble);
            return;
        }

        optimizar();

        System.out.println("Reglas aplicadas:");
        for (int r = 0; r < REGLAS.length; r++) {
            String regla = REGLAS[r];
            System.out.println();
            System.out.println(regla + "  (" + cambiosPorRegla.getOrDefault(regla, 0) + " cambio(s))");
            System.out.println(simple);
            boolean alguno = false;
            for (String[] c : bitacora) {
                if (c[0].equals(regla)) {
                    System.out.println("  - " + c[1]);
                    alguno = true;
                }
            }
            if (!alguno) {
                System.out.println("  (no se encontraron casos para esta regla)");
            }
        }

        System.out.println();
        System.out.println(doble);
        System.out.println(" ".repeat(48) + "CODIGO INTERMEDIO OPTIMIZADO");
        System.out.println(doble);
        int n = 0;
        for (String[] inst : optimizado) {
            if (inst[0].isEmpty()) {
                System.out.println();
                continue;
            }
            n++;
            System.out.println(String.format("%-4d %-42s # %s", n, inst[0], inst[1]));
        }
        System.out.println(doble);

        int antes = contarInstrucciones(codigo);
        int despues = contarInstrucciones(optimizado);
        System.out.println("Instrucciones originales: " + antes
            + " | Instrucciones optimizadas: " + despues
            + " | Eliminadas: " + (antes - despues)
            + String.format(" (%.1f%%)", antes == 0 ? 0.0 : (antes - despues) * 100.0 / antes));
        System.out.println("Cambios por regla:");
        for (String regla : REGLAS) {
            System.out.println(String.format("  %-45s %d", regla, cambiosPorRegla.getOrDefault(regla, 0)));
        }

        System.out.println();
        System.out.println(doble);
        System.out.println(" ".repeat(34) + "VALIDACION: EJECUCION DEL CODIGO ORIGINAL Y DEL OPTIMIZADO");
        System.out.println(doble);
        List<String> original = ejecutar(codigo);
        int pasosOriginal = pasos;
        List<String> nueva = ejecutar(optimizado);
        int pasosOptimizado = pasos;
        int filas = Math.max(original.size(), nueva.size());
        System.out.println(String.format("%-8s %-50s %s", "Salida", "Codigo original", "Codigo optimizado"));
        System.out.println(simple);
        for (int i = 0; i < filas; i++) {
            System.out.println(String.format("%-8d %-50s %s", i + 1,
                i < original.size() ? original.get(i) : "-",
                i < nueva.size() ? nueva.get(i) : "-"));
        }
        if (filas == 0) {
            System.out.println("(el programa no imprime nada)");
        }
        System.out.println(simple);
        System.out.println("Instrucciones ejecutadas: codigo original " + pasosOriginal
            + " | codigo optimizado " + pasosOptimizado);
        if (original.equals(nueva)) {
            System.out.println("Resultado de la validacion: CORRECTO, el codigo optimizado produce"
                + " exactamente los mismos resultados que el codigo original.");
        } else {
            System.out.println("Resultado de la validacion: DIFERENTE, la optimizacion cambio el resultado.");
        }
        System.out.println(doble);
    }

    private void imprimirCodigo() {
        String doble = "=".repeat(136);
        System.out.println();
        System.out.println(doble);
        System.out.println(" ".repeat(40) + "CODIGO INTERMEDIO ORIGINAL (CODIGO DE TRES DIRECCIONES)");
        System.out.println(doble);
        int n = 0;
        for (String[] inst : codigo) {
            if (inst[0].isEmpty()) {
                System.out.println();
                continue;
            }
            n++;
            System.out.println(String.format("%-4d %-42s # %s", n, inst[0], inst[1]));
        }
        System.out.println(doble);
        System.out.println("Instrucciones generadas: " + n
            + " | Temporales utilizados: " + contadorTemporales
            + " | Etiquetas utilizadas: " + contadorEtiquetas);
    }


    /* =========================================
       ACTIVIDAD 11 - PAQUETES Y VISIBILIDAD
       En Go no existen public/private: un nombre que inicia con
       mayuscula es exportado (visible desde otros paquetes) y uno
       con minuscula solo es visible dentro de su propio paquete.
       ========================================= */

    public void registrarImportacion(String ruta) {
        String paquete = ruta.replace("\"", "");
        paquete = paquete.substring(paquete.lastIndexOf('/') + 1);
        paquetesImportados.add(paquete);
    }

    public static boolean esExportado(String id) {
        return Character.isUpperCase(id.charAt(0));
    }

    /* El identificador es una variable (objeto) y no un paquete. */
    public boolean esObjeto(String id) {
        Simbolo s = buscar(id);
        return s != null
            && !s.getTiposimbolo().equals(Simbolo.METODO)
            && !s.getTiposimbolo().equals(Simbolo.CLASE);
    }

    /* paquete.Nombre: el paquete debe estar importado y el nombre
       debe ser exportado. */
    public boolean verificarPaquete(
            String paquete, String nombre, int linea, int columna) {

        if (!paquetesImportados.contains(paquete)) {
            errorSemantico(
                causaNoDeclarado(paquete)
                + " Tampoco es un paquete importado.",
                linea, columna, paquete + "." + nombre
            );
            return false;
        }
        if (!esExportado(nombre)) {
            errorSemantico(
                "No se permite acceder al miembro '" + nombre + "' del paquete '"
                + paquete + "' debido a sus restricciones de visibilidad: en Go"
                + " solo los nombres que inician con mayuscula son exportados.",
                linea, columna, paquete + "." + nombre
            );
            return false;
        }
        return true;
    }

    /* Recorta valores largos para que la tabla no se desacomode. */
    private static String recortar(String valor) {
        return valor != null && valor.length() > 20
            ? valor.substring(0, 17) + "..."
            : valor;
    }

    private String visibilidad(Simbolo s) {
        if (!s.getAmbito().equals(GLOBAL) && !esStruct(s.getAmbito())) {
            return "local";
        }
        return esExportado(s.getId()) ? "exportado" : "no exportado";
    }

    /* Revisa cantidad y tipo de los argumentos de una llamada. */
    private void verificarArgumentos(
            String metodo, List<?> argumentos, int linea, int columna) {

        List<String> parametros =
            parametrosMetodos.getOrDefault(metodo, new ArrayList<>());

        if (parametros.size() != argumentos.size()) {
            errorSemantico(
                "El metodo '" + metodo.replaceAll("#.*", "") + "' requiere "
                + parametros.size() + " argumento(s), pero se proporcionaron "
                + argumentos.size() + ".",
                linea,
                columna,
                metodo.replaceAll("#.*", "") + "()"
            );
            return;
        }

        for (int i = 0; i < parametros.size(); i++) {
            Simbolo arg = (Simbolo) argumentos.get(i);

            if (!esAsignable(arg, parametros.get(i))) {
                errorSemantico(
                    "El argumento " + (i + 1) + " del metodo '" + metodo
                    + "' es de tipo '" + arg.getTipodato()
                    + "'; se esperaba un tipo compatible con '" + parametros.get(i) + "'.",
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

    /* Convencion de nombres del profesor: structs en PascalCase,
       constantes en MAYUSCULAS con _ y lo demas en camelCase.
       ACTIVIDAD 11: por esta convencion la visibilidad de Go queda
       fija por categoria (structs y constantes exportados, metodos,
       atributos y variables no exportados). */
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

        String doble = "=".repeat(136);
        String simple = "-".repeat(136);
        int total = 0;

        System.out.println();
        System.out.println(doble);
        System.out.println(" ".repeat(50) + "TABLA DE SIMBOLOS");
        System.out.println(doble);
        System.out.println(String.format(
            "%-20s %-12s %-12s %-20s %-24s %-13s %-6s %-8s %s",
            "ID", "Categoria", "Tipo Dato", "Valor", "Ambito",
            "Visibilidad", "Linea", "Columna", "Token"
        ));

        for (Map.Entry<String, Map<String, Simbolo>> ambito : ambitos.entrySet()) {
            if (ambito.getValue().isEmpty()) {
                continue;
            }
            System.out.println(simple);
            for (Simbolo s : ambito.getValue().values()) {
                System.out.println(String.format(
                    "%-20s %-12s %-12s %-20s %-24s %-13s %-6d %-8d %s",
                    s.getId(), s.getTiposimbolo(), s.getTipodato(), recortar(s.getValor()),
                    s.getAmbito(), visibilidad(s), s.getLinea(), s.getColumna(),
                    s.getToken()
                ));
                total++;
            }
        }

        System.out.println(doble);
        System.out.println("Total de simbolos registrados: " + total);
        System.out.println("Errores de tabla de simbolos: " + erroresSemanticos);

        imprimirCodigo();
        /* ACTIVIDAD 14 */
        imprimirOptimizacion();
        imprimirResultado();
    }

    /* ACTIVIDAD 10 - REPORTE DEL ANALISIS SEMANTICO */
    private void imprimirResultado() {

        String doble = "=".repeat(136);
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
                "Resultado: programa VALIDO, estructuras de control, metodos, structs"
                + " y ambitos correctos."
            );
        } else {
            System.out.println(
                "Resultado: programa con ERRORES, revise los mensajes anteriores."
            );
        }
        System.out.println(doble);
    }
}

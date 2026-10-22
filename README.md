# Analizador del lenguaje Go en Java (JFlex + CUP)

Proyecto de la materia **Traductores de Lenguajes**. Simula el análisis del lenguaje **Go** usando **Java**, con **JFlex** para el análisis léxico y **CUP** para el análisis sintáctico y semántico. Se desarrolla en **NetBeans**.

Cada actividad está en su propio paquete dentro de `src/`, y cada una conserva todo lo de las anteriores:

| Paquete | Actividad |
|---|---|
| `act1` – `act5` | Análisis léxico y primeras reglas sintácticas |
| `act6_analisis_lexico` | Lectura (`fmt.Scan`), escritura (`fmt.Println`), asignaciones y expresiones |
| `act7_analisis_lexico` | Estructuras de control: `if/else`, `switch`, `for`, `break` |
| `act8_analisis_lexico` | Métodos: parámetros, retorno, llamadas y validación de argumentos |
| `act9_analisis_lexico` | Análisis semántico: gestión de la tabla de símbolos y tipos de datos |
| `act10_analisis_lexico` | **Verificación de tipos y análisis semántico de expresiones aritméticas, relacionales y lógicas** |

## Requisitos

- JDK 17 o superior
- NetBeans (proyecto Ant)
- Las librerías ya vienen en la carpeta `lib/` y el proyecto las usa con ruta relativa, así que no hay que configurarlas:
  - `lib/jflex-full-1.9.1.jar`
  - `lib/java-cup-11b.jar`
  - `lib/java-cup-11b-runtime.jar`

## Compilación

Los archivos `GoLexer.java`, `ParserGo.java` y `sym.java` se generan; no se editan a mano.

1. Abrir el proyecto en NetBeans.
2. Generar el analizador léxico: ejecutar `src/act10_analisis_lexico/GeneradorJFlexScanner.java` (*Run File*). Lee `go_lexico.jflex` y crea `GoLexer.java`.
3. Generar el analizador sintáctico: ejecutar `src/act10_analisis_lexico/GeneradorCupSintantico.java`. Lee `go_lexico.cup` y crea `ParserGo.java` y `sym.java`. Debe terminar con `0 conflicts detected`.
4. *Clean and Build* del proyecto.

Para otra actividad se usa el mismo proceso cambiando `act10` por el paquete correspondiente.

## Ejecución

Ejecutar `src/act10_analisis_lexico/AnalizadorLexico.java` y elegir una opción del menú:

| Opción | Archivo | Qué demuestra | Resultado |
|---|---|---|---|
| 1 | `programa_valido.txt` | Expresiones válidas con variables, constantes, atributos, métodos, `if`, `for` y `switch` | 0 errores, 22 símbolos, programa VALIDO |
| 2 | `programa_errores.txt` | Los 5 errores de la práctica y otros 8 casos de tipos | 13 errores semánticos |
| 3 | `programa_mixto.txt` | Código correcto mezclado con errores; el análisis continúa hasta el final | 5 errores semánticos |

La salida muestra los tokens, las reglas reconocidas, los errores con línea, columna, token y causa, la **tabla de símbolos** y, al final, el **resultado del análisis semántico**.

## Verificación de tipos (Actividad 10)

### Go es un lenguaje de tipado estático

- **Declaración:** cada variable, atributo y constante declara su tipo (`var edad int = 20;`) y ese tipo no cambia.
- **Asignación:** el analizador revisa que el valor sea del tipo declarado. Una variable `int` no puede recibir después un `string` o un `bool`.
- **Sin conversiones automáticas:** Go no convierte variables de un tipo a otro. `entero + decimal` (int con float64) es un error.
- **Literales sin tipo:** los literales sí se adaptan, como en Go. `10` puede usarse como `int`, `float32`, `float64`, `byte` o `rune`; `2.5` como `float32` o `float64`; `'a'` como `rune`, `byte` o `int`. Por eso `factor + 10` es válido y da `float64`.

En un lenguaje dinámico el tipo va con el valor y puede cambiar en cada asignación. En Go, y en este analizador, el tipo se fija en la declaración y se guarda en la tabla de símbolos.

### Cómo se calcula el tipo

Cada no terminal de expresión en `go_lexico.cup` regresa un `Simbolo` temporal con su texto, su tipo, si es literal, y la línea y columna del primer token. `TablaSimbolos` aplica las reglas y propaga el tipo resultante hacia arriba. Cuando una expresión tiene error, el error se reporta una sola vez y la variable conserva su valor anterior.

### Matriz de compatibilidad

| Izquierda | Operador | Derecha | Resultado | Estado |
|---|---|---|---|---|
| int | `+ - * / %` | int | int | Válido |
| float64 | `+ - * /` | float64 | float64 | Válido |
| float64 | `+ - * /` | literal entero | float64 | Válido (el literal se adapta) |
| int | `+ - * /` | float64 | ERROR | Inválido (Go no convierte variables) |
| float64 | `%` | int / float64 | ERROR | Inválido (`%` solo con enteros) |
| string | `+` | string | string | Válido (concatenación) |
| string | `- * / %` | cualquiera | ERROR | Operador no aplicable |
| int / float64 | `< > <= >=` | mismo tipo | bool | Válido |
| bool | `< > <= >=` | bool | ERROR | Operador no aplicable |
| cualquiera | `== !=` | mismo tipo | bool | Válido |
| bool | `&& \|\|` | bool | bool | Válido |
| int | `&& \|\| !` | cualquiera | ERROR | Operador no aplicable |
| bool | `*` | int | ERROR | Operador no aplicable |

### Qué se valida

- Tipo del valor en declaraciones y asignaciones a variables, atributos (`objeto.campo = valor;`) y constantes.
- Operaciones aritméticas, relacionales y lógicas según la matriz.
- Identificadores dentro de expresiones: se buscan en la tabla de símbolos, deben estar declarados y se usa su tipo de dato.
- Constantes: se pueden leer, pero no reasignar.
- Condiciones de `if` y `for` de tipo `bool`, y cada `case` del mismo tipo que el `switch`.
- Argumentos de las llamadas, valor del `return` y uso de `++` y `--` solo en variables numéricas.

### Structs

Se pueden declarar variables de tipo struct y usar sus atributos:

```go
var evaluador Evaluador = Evaluador{};
evaluador.nombre = "Principal";
evaluador.intentos = resultado + 1;
```

Se revisa que el struct exista, que tenga el atributo y que el valor sea del tipo del atributo.

### Ejemplos de errores

```
[ERROR] Error semantico en linea 23, columna 9:
        Incompatibilidad de tipos: No se puede asignar un valor de tipo 'string' a la variable 'a' de tipo 'int'.
        Token: "Hola mundo"
[ERROR] Error semantico en linea 26, columna 16:
        El operador aritmetico '*' no es aplicable a los operandos de tipo 'bool' y 'int'.
        Token: bandera * 10
[ERROR] Error semantico en linea 29, columna 8:
        La expresion dentro de la condicion 'if' debe ser de tipo 'bool', se encontro 'int'.
        Token: contador + 5
[ERROR] Error semantico en linea 34, columna 5:
        No se puede reasignar un valor a la constante 'LIMITE'.
        Token: LIMITE
```

### Resultado del análisis semántico

Al final se imprime un resumen con los errores de cada fase:

```
RESULTADO DEL ANALISIS SEMANTICO
Lenguaje: Go (tipado estatico)
Errores lexicos:     0
Errores sintacticos: 0
Errores semanticos:  13
Total de errores:    13
Resultado: programa con ERRORES, revise los mensajes anteriores.
```

## Tabla de símbolos (Actividad 9)

### Clases

- **`Simbolo.java`**: representa cada entrada de la tabla con `id`, `tiposimbolo` (categoría), `tipodato`, `valor`, `ambito`, `linea`, `columna` y `token`. Es distinta de `sym.java`, que genera CUP.
- **`TablaSimbolos.java`**: estructura dinámica. Usa un `LinkedHashMap` de ámbitos, donde cada ámbito tiene su propio `LinkedHashMap` de símbolos, y una **pila de ámbitos** (`GLOBAL` → struct o función actual). Permite insertar, buscar del ámbito más interno al global, actualizar valores, validar reglas e imprimir la tabla. Desde la Actividad 10 también contiene las reglas de verificación de tipos.

La línea y la columna llegan desde JFlex (`yyline + 1`, `yycolumn + 1`) dentro de cada `Symbol`. Las acciones de `go_lexico.cup` registran cada declaración y revisan cada uso.

### Clasificación de identificadores en Go

| Categoría | En Go | Convención | Inicialización |
|---|---|---|---|
| CLASE | `type Nombre struct { ... }` | PascalCase | N/A |
| ATRIBUTO | campos del struct | camelCase | Opcional (se registra `null`) |
| METODO | `func nombre(...)` | camelCase | N/A |
| VARIABLE | `var nombre tipo = valor;` y parámetros | camelCase | Obligatoria (los parámetros se registran como `N/A (Parametro)`) |
| CONSTANTE | `const NOMBRE tipo = valor;` | MAYÚSCULAS separadas con `_` | Obligatoria |

El tipo de dato de cada símbolo se guarda en *Tipo Dato*. En los métodos es el tipo de retorno, o `void` si no tienen.

### Validaciones

- Nomenclatura incorrecta según la categoría.
- Variable o constante sin valor inicial.
- Identificador declarado dos veces en el mismo ámbito.
- Identificador o método usado sin declarar.
- Asignación a una constante.

### Ejemplo de salida

```
ID                   Categoria    Tipo Dato  Valor                Ambito             Linea   Columna   Token Originador
------------------------------------------------------------------------------------------------------------------------
PI                   CONSTANTE    float64    3.1416               GLOBAL             6       7         ID
Evaluador            CLASE        struct     N/A                  GLOBAL             12      6         ID
promedio             METODO       float64    N/A                  GLOBAL             18      6         ID
------------------------------------------------------------------------------------------------------------------------
nombre               ATRIBUTO     string     null                 Evaluador          13      5         ID
------------------------------------------------------------------------------------------------------------------------
suma                 VARIABLE     float64    N/A (Parametro)      promedio           18      15        ID
evaluador            VARIABLE     Evaluador  Evaluador{}          ejecutar           30      9         ID
```

# Analizador del lenguaje Go en Java (JFlex + CUP)

Proyecto de la materia **Traductores de Lenguajes**. Simula el análisis del lenguaje **Go** usando **Java**, con **JFlex** para el análisis léxico y **CUP** para el análisis sintáctico y semántico. Se desarrolla en **NetBeans**.

Cada actividad está en su propio paquete dentro de `src/`, y cada una conserva todo lo de las anteriores:

| Paquete | Actividad |
|---|---|
| `act1` – `act5` | Análisis léxico y primeras reglas sintácticas |
| `act6_analisis_lexico` | Lectura (`fmt.Scan`), escritura (`fmt.Println`), asignaciones y expresiones |
| `act7_analisis_lexico` | Estructuras de control: `if/else`, `switch`, `for`, `break` |
| `act8_analisis_lexico` | Métodos: parámetros, retorno, llamadas y validación de argumentos |
| `act9_analisis_lexico` | **Análisis semántico: gestión de la tabla de símbolos y tipos de datos** |

## Requisitos

- JDK 17 o superior
- NetBeans (proyecto Ant)
- Librerías agregadas al proyecto (*Properties → Libraries*):
  - `jflex-full-1.9.1.jar`
  - `java-cup-11b.jar`
  - `java-cup-11b-runtime.jar`

## Compilación

Los archivos `GoLexer.java`, `ParserGo.java` y `sym.java` se generan; no se editan a mano.

1. Abrir el proyecto en NetBeans.
2. Generar el analizador léxico: ejecutar `src/act9_analisis_lexico/GeneradorJFlexScanner.java` (*Run File*). Lee `go_lexico.jflex` y crea `GoLexer.java`.
3. Generar el analizador sintáctico: ejecutar `src/act9_analisis_lexico/GeneradorCupSintantico.java`. Lee `go_lexico.cup` y crea `ParserGo.java` y `sym.java`. Debe terminar con `0 conflicts detected`.
4. *Clean and Build* del proyecto.

## Ejecución

Ejecutar `src/act9_analisis_lexico/AnalizadorLexico.java` y elegir una opción del menú:

| Opción | Archivo | Qué demuestra |
|---|---|---|
| 1 | `programa_valido.txt` | Programa correcto: 0 errores, tabla completa (18 símbolos) |
| 2 | `programa_errores.txt` | Errores de nomenclatura, inicialización, duplicados y no declarados (12 errores) |
| 3 | `programa_mixto.txt` | Código correcto mezclado con errores; el análisis continúa hasta el final |

La salida muestra los tokens, las reglas reconocidas, los errores con línea y columna y, al terminar, la **tabla de símbolos**.

## Tabla de símbolos (Actividad 9)

### Clases

- **`Simbolo.java`**: representa cada entrada de la tabla con `id`, `tiposimbolo` (categoría), `tipodato`, `valor`, `ambito`, `linea`, `columna` y `token`. Es distinta de `sym.java`, que genera CUP.
- **`TablaSimbolos.java`**: estructura dinámica. Usa un `LinkedHashMap` de ámbitos, donde cada ámbito tiene su propio `LinkedHashMap` de símbolos, y una **pila de ámbitos** (`GLOBAL` → struct o función actual). Permite insertar, buscar del ámbito más interno al global, actualizar valores, validar reglas e imprimir la tabla.

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

Ejemplo de error:

```
[ERROR] Error semantico en linea 12, columna 9:
        El identificador 'x' ya ha sido declarado en el ambito 'proceso'.
```

### Ejemplo de salida

```
ID                   Categoria    Tipo Dato  Valor                Ambito             Linea   Columna   Token Originador
------------------------------------------------------------------------------------------------------------------------
MAX_CALIFICACION     CONSTANTE    int        100                  GLOBAL             4       7         ID
SistemaEscolar       CLASE        struct     N/A                  GLOBAL             7       6         ID
calcularPromedio     METODO       int        N/A                  GLOBAL             16      6         ID
------------------------------------------------------------------------------------------------------------------------
totalAlumnos         ATRIBUTO     int        null                 SistemaEscolar     8       5         ID
------------------------------------------------------------------------------------------------------------------------
nota1                VARIABLE     int        N/A (Parametro)      calcularPromedio   16      23        ID
sumaNotas            VARIABLE     int        nota1 + nota2        calcularPromedio   17      9         ID
```

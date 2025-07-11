# Jack-Decompiler
A VM to Jack Decompiler

Each .vm file represents a class. We have a table to associate classes with their metadata.

## How it works
Class name ->

    List of Functions
    Amount of Static Variables
    Amount of fields

For each function, we need to store their metadata

Function name ->

    Amount of Arguments
    Amount of local variables
    isConstructor
    isMethod
    isFunction 
    isVoid
    TypeValue (default is int, execpt when void)

All of this information can be determined by viewing the VM code for the function, except for the amount of arguments, which is determined when encountering the "call" VM command. Functions that are never called are not decompiled. We can also detect when functions are void by checking for "call foo x" "pop temp 0" patterns.

There is a global class hashtable which assigns class names to their metadata. The class metadata has a function hashtable, which assigns functions to their function metadata. 

### Types

By default all identifiers are ints and all functions return an int or void. After all, Jack is weakly typed language; everything is just a signed 16-bit int. However, this is one issue. When calling a method on an identifier, Jack needs to know what class that identifier belongs too.

```
var int foo;
do foo.new();
```

is not valid Jack, since it doesn't know what to call. Theoretically, multiple classes could have  a new() method. The VM code generated would be something like this 

```
push local 0
call Foo.new 1
pop temp 0
```

Thus, the VM code knows that the variable is a Foo object. Calling methods on objects is the only time we need to know a variables's type. 

Could a variable have different types? After all, "objects" are just pointers to an array, where each element is its field.

```
let x = Foo.new(); //x is now a Foo object
do x.foo_something();
let x = Bar.new(); //x is now a Bar object
do x.bar_something();
```
What should we declare x's type to be? I'm not exactly sure, so in this case, I'll just assign it to int. I might come back to this with something smarter.



In the Class metadata, we have a class symbol table, where we assign names in the form "static/field_n" to a type. For functions, their symbol table assigns names in the form "argument/local_n" to a type. 

## Summary

**Step 1: Parse All VM Files**

* For each `.vm` file (representing a class), extract:

    * Class name
    * List of functions defined
    * Maximum `static n` and `this n` (fields)
* For each `function` in the VM file:

    * Store the number of locals
    * Detect whether the function is a constructor (`Memory.alloc` pattern), method (`push argument 0 / pop pointer 0`), or plain function
    * Store its VM code lines
* For each `call` instruction:

    * Record the callee and number of arguments
    * Mark function as `void` if followed by `pop temp 0`

**Step 2: Filter and Prepare Metadata**

* Only decompile functions that are actually called (including `Sys.init`)
* Resolve class-level and function-level variable types and symbol tables
* Assign default type `int` to all variables unless improved inference is possible (e.g., class names inferred from `call ClassName.func`)

**Step 3: Decompile to Jack**

* For each class:

    * Emit `class ClassName {`
    * Emit all detected `static` and `field` variables
    * For each used function:

        * Emit `constructor`, `method`, or `function`
        * Emit return type (`int` or `void`)
        * Emit argument list (`int argument_n`)
        * Emit local variables (`var int local_n`)
        * Emit translated VM code as Jack statements using the `VMToJackTranslator`

**Step 4: Special Handling and Optimizations**

* Recognize idiomatic VM patterns (e.g., `call Memory.alloc` + `pop pointer 0`) and avoid emitting redundant or compiler-injected Jack code
* Simplify expressions (e.g., nested dereferencing, string constants)

**Step 5: Output**

* Write each decompiled `.jack` file into the output directory with class structure, method declarations, and function bodies, suitable for inspection or recompilation with the Jack compiler.

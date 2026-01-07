---
title: "JavaScript (No, Not That One): Modern Automation with Java"
description: "Discover how modern Java has evolved into a powerful scripting language, eliminating boilerplate and enabling instant execution for automation tasks"
tags: [java, jbang, picolli]
author: Loïc
image: java-script-cover.png
---

## The Scripting Dilemma

If you're like me, as a Java developer, every time you want or need to write a quick automation script, you struggle to remember how to properly write some bash or python. 
On those days, you probably end up "vibe coding" it and struggle once again when you want to adapt it.
If only you could write it in Java!

You might say "but Java is not for scripting" and I don't want to bother with maven or Gradle.
Modern Java has quietly eliminated the traditional barriers that made it unsuitable for scripting.
With instant execution, shebang support, and zero-setup automation, Java has evolved into a lean scripting language that lets you write precise, maintainable code without the "vibe coding" that often comes with unfamiliar languages.

In this article, I'll show you how Java became a first-class scripting language and why it might just become your new favorite tool for automation.

## The Death of Manual Compilation

The first problem if you want to write a Java script is compilation. You probably don't want to run `javac` and manage `.class` files for a simple script. 
But since Java 11, you're able to run single-file source-code programs directly, using the `java` launcher.

### Single-File Execution
Let's say you want to rewrite `ls` in Java using good old Java you might do something like this:
```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DecimalFormat;

public class ListFiles {

    static void main(String[] args) throws IOException {
        try (var stream = Files.list(Path.of("."))) {
            stream
                .filter(path -> !path.getFileName().toString().startsWith("."))
                .sorted()
                .forEach(ListFiles::printEntry);
        }
    }

    private static void printEntry(Path path) {
        var isDir = Files.isDirectory(path);
        var name = path.getFileName().toString();
        var display = isDir ? "\u001B[34m" + name + "\u001B[0m" : name;

        IO.println("%-12s %-10s %s".formatted(
            permissions(path, isDir),
            size(path, isDir),
            display
        ));
    }

    private static String size(Path path, boolean isDir) {
        if (isDir) return "-";
        try {
            long bytes = Files.size(path);
            if (bytes == 0) return "0 B";

            var units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int group = (int) (Math.log(bytes) / Math.log(1024));
            return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, group)) + " " + units[group];
        } catch (IOException _) {
            return "?";
        }
    }

    private static String permissions(Path path, boolean isDir) {
        try {
            return (isDir ? "d" : "-") + PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
        } catch (Exception _) {
            return (isDir ? "d" : "-") +
                   (Files.isReadable(path) ? "r" : "-") +
                   (Files.isWritable(path) ? "w" : "-") +
                   (Files.isExecutable(path) ? "x" : "-") + "------";
        }
    }
}
```

If you want to run it, no need for `javac`, you can simply do this:
```bash
java ListFiles.java
```
No `javac` needed. No `.class` files cluttering your directory. Just instant execution.

### Multi-File Support

But since **Java 22**, you're not limited to single-file programs. You can now write multi-file programs and run them directly. The Java launcher  locates and compiles related source files in subdirectories.
For instance if you have something like this:

```java
public class Greet {

    public static void main(String[] args) {
        var message = new Message("Hello", "Folks");
        new MessagingService().sendMessage(message);

    }
}
```

```java
record Message(String welcomingWord, String name) {
}

```

```java
class MessagingService {

    public void sendMessage(Message message){
        IO.println(message.welcomingWord() + " " + message.name());
    }

}
```

This app is just an overcomplicated "Hello World!" but it shows you that you can write multi-file programs and run them directly just by running:
```bash
java Greet.java  # Automatically finds and compiles dependencies
```

This feature is especially useful for complex scripts that require multiple classes but it's also great for people who want to learn Java and don't have to deal with project setup.

Behind the scenes, the launcher invokes the compiler automatically and stores the compiled result in memory. There's no build cruft, no intermediate files, just pure execution. For scripting purposes, Java now feels as immediate as Python or Ruby.

## Why so much ceremony?

One key thing for a good scripting language is to eliminate boilerplate. Let's be honest, Java can be a bit verbose. The most infamous example is the `public static void main(String[] args)` signature. If you just want a simple script, that's a lot of text that doesn't serve much purpose. Just to print "Hello World!" you need to write at least 5 lines, and most of it is boilerplate. 
```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

### The Java Evolution

Java 25, building upon preview features introduced in Java 21 and 22, introduced several features that directly tackle the verbosity problem.
For instance, **Compact Source Files** (evolving the concept of Unnamed Classes) and **Instance Main Methods** eliminate the need for boilerplate signatures.
- Compact Source Files: Allows you to write methods and fields directly at the top-level of a source file, without an explicit class declaration.
- Instance Main Methods: Allows you to simplify the traditional `public static void main(String[] args)` signature to a simple `void main()`.

With these features, a "Hello World" can become as simple as this:
```java
void main() {
    System.out.println("Java");
}

```
The JVM automatically chooses the starting point, prioritizing instance `main()` methods when available. This makes your scripts feel more natural and less like traditional enterprise Java.

### Easier console interaction

In the past interacting with the console was a bit cumbersome.
First everytime you want to print something to the console, you end up typing `System.out.println("...")`.
But it get worse every time you want to read input from the console you need to go to either throught the gymnastics of using either the `BufferedReader` or `Scanner`.
So you quickly end up with code like this:
```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

void main() {
    System.out.println("Please enter your name:");
    String name = "";
    try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        name = reader.readLine();
    } catch (IOException ioe) {
        ioe.printStackTrace();
    }
    System.out.println("Hello, " + name);
}


```

Thanks to the `Compact Source Files`, you can now use the `IO` class to simplify your console interaction:
```java
void main() {
    String name = IO.readln("Please enter your name:");
    IO.print("Hello, ");
    IO.println(name);
}

```
By the way, the IO class is part of the `java.lang` package, thus it's implicitly imported by every source file.

If you want to learn about all this, I recommend reading the [JEP-512](https://openjdk.org/jeps/512).

## Java as a Native Script: Shebang Support

One of the defining features of a script is the ability to run it directly from the command line, like `./myscript`, without explicitly calling an interpreter. Many scripting languages achieve this with a "shebang" line (`#!`). This special line at the start of a file tells the operating system which program to use for execution.

Since **Java 11** (via [JEP 330](https://openjdk.org/jeps/330), which also introduced single-file execution), the `java` launcher supports this convention.

Let's refactor our `ListFiles` example into a proper, executable script using the modern Java features we've discussed:

```java
#!/usr/bin/java --source 25

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DecimalFormat;

void main(String[] args) throws IOException {
    var dir = Path.of(args.length > 0 ? args[0] : ".");
    if (!Files.exists(dir)) {
        IO.println("Error: Directory '" + dir + "' does not exist.");
        System.exit(1);
    }
    if (!Files.isDirectory(dir)) {
        IO.println("Error: '" + dir + "' is not a directory.");
        System.exit(1);
    }
    
    try (var stream = Files.list(dir)) {
        stream
            .filter(path -> !path.getFileName().toString().startsWith("."))
            .sorted()
            .forEach(path -> printEntry(path));
    }
}

void printEntry(Path path) {
    var isDir = Files.isDirectory(path);
    var name = path.getFileName().toString();
    var display = isDir ? "\u001B[34m" + name + "\u001B[0m" : name;

    IO.println("%-12s %-10s %s".formatted(
        permissions(path, isDir),
        size(path, isDir),
        display
    ));
}

String size(Path path, boolean isDir) {
    if (isDir) return "-";
    try {
        long bytes = Files.size(path);
        if (bytes == 0) return "0 B";

        var units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int group = (int) (Math.log(bytes) / Math.log(1024));
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, group)) + " " + units[group];
    } catch (IOException _) {
        return "?";
    }
}

String permissions(Path path, boolean isDir) {
    try {
        return (isDir ? "d" : "-") + PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    } catch (Exception _) {
        return (isDir ? "d" : "-") +
               (Files.isReadable(path) ? "r" : "-") +
               (Files.isWritable(path) ? "w" : "-") +
               (Files.isExecutable(path) ? "x" : "-") + "------";
    }
}
```

To make it executable, save the file (e.g., as `ls`) without a `.java` extension and set the permissions:

```bash
chmod +x ls
./ls
```
Your Java code is now a first-class CLI command, no different from a Bash or Python script.

**Portability Tip:** You can move your script to a directory in your system's `PATH`, like `/usr/local/bin/`, to invoke it from anywhere:

```bash
sudo mv ls /usr/local/bin/
ls  # Run from any directory
```

Suddenly, Java feels like a native scripting language.

## Advanced Automation with JBang

While the built-in features are impressive, [JBang](https://www.jbang.dev/) takes Java scripting to the next level by handling setup overhead and enabling advanced features.

### What is JBang?

JBang is a tool that facilitates Java scripting by eliminating friction. It's like having a personal assistant that handles all the boring setup work.

### Zero-Setup Management

JBang can automatically download and install required Java versions if they're missing:

```bash
jbang MyScript.java  # Installs Java if needed
```

No more "Java version not found" errors. JBang handles it.

### Dependency Management

This is where JBang truly shines. Unlike pure Java scripts that require manual classpath management, JBang lets you declare dependencies directly in your file using special comments:

```java
//DEPS com.google.code.gson:gson:2.10.1
//DEPS org.apache.commons:commons-lang3:3.14.0

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;

void main() {
    Gson gson = new Gson();
    String json = gson.toJson(new Person("John", 30));
    System.out.println(StringUtils.capitalize(json));
}
```

JBang automatically downloads and manages these dependencies. No Maven. No Gradle. Just declare and use.

### Powerful Features

JBang offers several advanced capabilities:

**IDE Integration**: Generate a temporary project structure for full IntelliSense support:

```bash
jbang edit MyScript.java  # Opens in your IDE with full autocomplete
```

**Native Binaries**: Generate standalone native binaries using GraalVM for near-instant startup:

```bash
jbang export native MyScript.java
./MyScript  # Blazing fast native execution
```

**Templates**: Quickly scaffold CLI tools or web servers:

```bash
jbang init --template=cli mycli.java
jbang init --template=qcli myserver.java
```

## Elevating Your Scripts: CLI Richness with Picocli

While simple scripts are great, some automation tasks require robust CLI interfaces with options, positional parameters, and help menus. That's where [Picocli](https://picocli.info/) comes in.

### Pro-Grade Tools

Picocli integrates perfectly with JBang to provide ANSI-colored help messages and strongly-typed argument parsing with minimal effort:

```java
//DEPS info.picocli:picocli:4.7.5

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "greet", mixinStandardHelpOptions = true, version = "1.0",
         description = "Greets users with style")
class Greet implements Runnable {
    @Option(names = {"-c", "--count"}, description = "Number of greetings")
    int count = 1;

    @Parameters(description = "Name(s) to greet")
    String[] names;

    public void run() {
        for (int i = 0; i < count; i++) {
            for (String name : names) {
                System.out.println("Hello, " + name + "!");
            }
        }
    }
}

void main(String[] args) {
    new CommandLine(new Greet()).execute(args);
}
```

Run it:

```bash
jbang greet.java --help
jbang greet.java -c 3 Alice Bob
```

You get professional CLI behavior: auto-generated help, version info, type checking, and beautiful error messages. All with minimal code.

### Zero-Code CLI Experience

Picocli handles the complexity:
- Automatic help generation with `--help`
- Version display with `--version`
- Type conversion and validation
- ANSI colors for better readability
- Subcommands and command hierarchies

Your scripts can rival professionally built CLI tools.

## Java's New Era

We've come full circle. Java, once synonymous with enterprise complexity and verbose boilerplate, has transformed into a lean scripting language.

### Why This Matters

**Maintainability**: Java scripts offer types and structure that Bash often lacks. When you return to a script six months later, strong typing and IDE support make understanding and modifying code dramatically easier.

**Familiarity**: You already know Java. Why context-switch to another language for automation when you can use the expertise you've already built?

**Power**: You have access to the entire Java ecosystem—millions of libraries, battle-tested tools, and robust frameworks—all available in a simple script.

**Performance**: With GraalVM native images, your scripts can compile to native binaries with near-instant startup times, rivaling Go or Rust.

### The Bottom Line

With shebang support, JBang, unnamed classes, and instance main methods, Java has become a lean, mean automation machine. The ceremony is gone. The friction is eliminated. What remains is a powerful, typed, maintainable scripting language that happens to be Java.

So the next time you need to write a quick automation script, don't reach for Bash or Python out of habit. Give Java a try. You might be surprised at how good it feels to script in a language where your IDE actually helps you, where types catch bugs before runtime, and where "vibe coding" is replaced with precision.

Java scripting isn't the future. It's already here. And it's spectacular.

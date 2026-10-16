package io.github.apertechprojects.queryforge.verify;

import io.github.apertechprojects.queryforge.expression.QueryModel;
import io.github.apertechprojects.queryforge.internal.expression.LambdaTranslator;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checks, without a database, that every where, sort and select lambda in compiled classes can be translated.
 * Lambdas are normally translated when the query runs; this finds unsupported expressions at build time:
 * <pre>{@code
 * List<String> problems = QueryForgeVerifier.verify(Path.of("target/classes"), getClass().getClassLoader());
 * }</pre>
 * Every method that takes a generated query model is checked: methods returning {@code boolean} as where
 * lambdas, the others as select lambdas or sort keys. Captured values are treated as unknown.
 */
public final class QueryForgeVerifier {

    private QueryForgeVerifier() {
    }

    /**
     * @param checked  number of lambda bodies checked
     * @param problems one entry per lambda that can not be translated, naming class, line and cause
     */
    public record Report(int checked, List<String> problems) {
    }

    /** Problems found in the {@code .class} files under {@code classesDirectory}; each names class, line and cause. */
    public static List<String> verify(Path classesDirectory, ClassLoader classLoader) {
        return report(classesDirectory, classLoader).problems();
    }

    public static Report report(Path classesDirectory, ClassLoader classLoader) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(classesDirectory)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                checked += verifyClass(file, classLoader, problems);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Report(checked, List.copyOf(problems));
    }

    /** Usage: {@code QueryForgeVerifier <classes directory>...}; the classes must be on the class path. Exits with 1 on problems. */
    public static void main(String[] args) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (String directory : args) {
            Report report = report(Path.of(directory), classLoader);
            checked += report.checked();
            problems.addAll(report.problems());
        }
        problems.forEach(System.out::println);
        System.out.println("jpa-query-forge: " + checked + " lambda(s) checked, " + problems.size() + " problem(s)");
        if (!problems.isEmpty()) {
            System.exit(1);
        }
    }

    private static int verifyClass(Path file, ClassLoader classLoader, List<String> problems) throws IOException {
        ClassReader reader;
        try (InputStream input = Files.newInputStream(file)) {
            reader = new ClassReader(input);
        }
        String className = reader.getClassName();
        if (isQueryModel(Type.getObjectType(className), classLoader)) {
            return 0;
        }
        int checked = 0;
        List<String[]> methods = new ArrayList<>();
        reader.accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_ABSTRACT) == 0) {
                    methods.add(new String[]{name, descriptor, String.valueOf(access)});
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);

        for (String[] method : methods) {
            Type[] parameters = Type.getArgumentTypes(method[1]);
            for (int i = 0; i < parameters.length; i++) {
                if (!isQueryModel(parameters[i], classLoader)) {
                    continue;
                }
                Class<?> modelClass = load(parameters[i], classLoader);
                boolean isStatic = (Integer.parseInt(method[2]) & Opcodes.ACC_STATIC) != 0;
                checked++;
                try {
                    problems.addAll(LambdaTranslator.verify(classLoader, className, method[0], method[1], isStatic, i, modelClass));
                } catch (RuntimeException e) {
                    problems.add(className.replace('/', '.') + "." + method[0] + ": verification failed: " + e);
                }
                break;
            }
        }
        return checked;
    }

    private static boolean isQueryModel(Type type, ClassLoader classLoader) {
        Class<?> loaded = load(type, classLoader);
        return loaded != null && loaded != QueryModel.class && QueryModel.class.isAssignableFrom(loaded);
    }

    private static Class<?> load(Type type, ClassLoader classLoader) {
        if (type.getSort() != Type.OBJECT) {
            return null;
        }
        try {
            return Class.forName(type.getClassName(), false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}

package io.github.apertechprojects.queryforge.expression;

import io.github.apertechprojects.queryforge.annotation.ForgeModel;
import io.github.apertechprojects.queryforge.processor.QueryForgeModelProcessor;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeModelCompileCheckTest {

    private static final String ENTITY = """
            package sample;
            @jakarta.persistence.Entity
            public class Shop {
                @jakarta.persistence.Id private String id;
                private String name;
                private java.time.LocalDate openedAt;
                @jakarta.persistence.ManyToOne private Shop parent;
            }
            """;

    private static List<Diagnostic<? extends JavaFileObject>> compile(String model, String... extra) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path output = Files.createTempDirectory("forge-model-check");
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            files.setLocation(StandardLocation.CLASS_PATH, List.of(codeSource(ForgeModel.class), codeSource(Entity.class)));
            files.setLocation(StandardLocation.CLASS_OUTPUT, List.of(output.toFile()));
            files.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(output.toFile()));
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, List.of("-proc:full"), null,
                    sources(model, extra));
            task.setProcessors(List.of(new QueryForgeModelProcessor()));
            task.call();
        }
        return diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
    }

    private static List<JavaFileObject> sources(String model, String... extra) {
        List<JavaFileObject> sources = new java.util.ArrayList<>(List.of(source("sample.Shop", ENTITY), source("sample.ShopRow", model)));
        for (String code : extra) {
            sources.add(source("sample." + code.replaceAll("(?s).*public class (\\w+).*", "$1"), code));
        }
        return sources;
    }

    private static File codeSource(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static JavaFileObject source(String className, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }

    @Test
    void validModelCompiles() throws Exception {
        List<Diagnostic<? extends JavaFileObject>> errors = compile("""
                package sample;
                import io.github.apertechprojects.queryforge.annotation.*;
                @ForgeModel(entity = Shop.class)
                public class ShopRow {
                    private String id;
                    private String name;
                    @ForgeField("parent.name") private String parentName;
                    @ForgeField("openedAt") private String openedAtText;
                    @ForgeIgnore private int lineNumber;
                }
                """);
        assertEquals(List.of(), errors.stream().map(d -> d.getMessage(null)).toList());
    }

    @Test
    void inheritedFieldsAreChecked() throws Exception {
        String base = """
                package sample;
                public class Base {
                    private String name;
                    private Integer openedAt;
                }
                """;
        List<String> errors = compile("""
                package sample;
                import io.github.apertechprojects.queryforge.annotation.*;
                @ForgeModel(entity = Shop.class, inherited = {"name", "openedAt", "absent"})
                public class ShopRow extends Base {
                    private String id;
                }
                """, base).stream().map(d -> d.getMessage(null)).toList();

        assertEquals(2, errors.size(), String.join("\n", errors));
        assertTrue(errors.stream().anyMatch(e -> e.contains("openedAt") && e.contains("can not be assigned to java.lang.Integer")), String.join("\n", errors));
        assertTrue(errors.stream().anyMatch(e -> e.contains("inherited field absent")), String.join("\n", errors));
    }

    @Test
    void wrongPathsAndTypesFailTheBuild() throws Exception {
        List<String> errors = compile("""
                package sample;
                import io.github.apertechprojects.queryforge.annotation.*;
                @ForgeModel(entity = Shop.class)
                public class ShopRow {
                    @ForgeField("parent.nmae") private String parentName;
                    @ForgeField("name.length") private Integer nameLength;
                    private java.math.BigDecimal openedAt;
                    private String missing;
                }
                """).stream().map(d -> d.getMessage(null)).toList();

        assertEquals(4, errors.size(), String.join("\n", errors));
        assertTrue(errors.stream().anyMatch(e -> e.contains("parentName") && e.contains("no attribute 'nmae' in Shop")), String.join("\n", errors));
        assertTrue(errors.stream().anyMatch(e -> e.contains("nameLength") && e.contains("'name' is not an association")), String.join("\n", errors));
        assertTrue(errors.stream().anyMatch(e -> e.contains("openedAt") && e.contains("can not be assigned to java.math.BigDecimal")), String.join("\n", errors));
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing") && e.contains("no attribute 'missing'")), String.join("\n", errors));
    }
}

package io.github.apertechprojects.queryforge.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates a query model class ({@code QPurchaseOrder} for {@code PurchaseOrder}) for every JPA entity and
 * embeddable. The model mirrors the persistent fields as public fields, so where lambdas can be written with
 * plain Java operators: {@code o -> o.amount.compareTo(limit) > 0 && o.supplier.name.equals(name)}.
 * <p>
 * Models are never instantiated; where lambdas are translated from their bytecode.
 */
@SupportedAnnotationTypes({QueryForgeModelProcessor.ENTITY, QueryForgeModelProcessor.EMBEDDABLE, QueryForgeModelProcessor.FORGE_MODEL})
public class QueryForgeModelProcessor extends AbstractProcessor {

    static final String ENTITY = "jakarta.persistence.Entity";
    static final String EMBEDDABLE = "jakarta.persistence.Embeddable";
    static final String FORGE_MODEL = "io.github.apertechprojects.queryforge.annotation.ForgeModel";
    private static final String FORGE_FIELD = "io.github.apertechprojects.queryforge.annotation.ForgeField";
    private static final String FORGE_IGNORE = "io.github.apertechprojects.queryforge.annotation.ForgeIgnore";
    private static final String FORGE_SUB_MODEL = "io.github.apertechprojects.queryforge.annotation.ForgeSubModel";
    private static final String MAPPED_SUPERCLASS = "jakarta.persistence.MappedSuperclass";
    private static final String TRANSIENT = "jakarta.persistence.Transient";
    private static final String QUERY_MODEL = "io.github.apertechprojects.queryforge.expression.QueryModel";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            boolean forgeModel = annotation.getQualifiedName().contentEquals(FORGE_MODEL);
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (forgeModel && (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.RECORD)) {
                    checkForgeModel((TypeElement) element);
                } else if (!forgeModel && element.getKind() == ElementKind.CLASS) {
                    generate((TypeElement) element);
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ @ForgeModel check

    /** Compile errors for fields of a {@code @ForgeModel(entity = ...)} whose path or type does not fit the entity. */
    private void checkForgeModel(TypeElement model) {
        TypeMirror entity = annotationValue(model, FORGE_MODEL, "entity");
        if (entity == null || entity.getKind() != TypeKind.DECLARED) {
            return;
        }
        checkFields(model, (DeclaredType) entity, "");
    }

    private void checkFields(TypeElement model, DeclaredType entity, String prefix) {
        List<TypeElement> hierarchy = new ArrayList<>();
        for (TypeElement type = model; type != null; type = superclass(type)) {
            if (type != model && !hasAnnotation(type, FORGE_MODEL)) {
                break;
            }
            hierarchy.add(type);
        }
        for (String name : inheritedNames(model)) {
            Element field = inheritedField(superclass(hierarchy.get(hierarchy.size() - 1)), name);
            if (field == null) {
                error(model, "@ForgeModel inherited field " + name + " is not declared in a superclass of " + model.getSimpleName());
            } else {
                checkValue(model, field, entity, join(prefix, name));
            }
        }
        for (TypeElement type : hierarchy) {
            for (Element member : type.getEnclosedElements()) {
                Set<Modifier> modifiers = member.getModifiers();
                if (member.getKind() != ElementKind.FIELD || modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.TRANSIENT)
                        || hasAnnotation(member, FORGE_IGNORE)) {
                    continue;
                }
                if (hasAnnotation(member, FORGE_SUB_MODEL)) {
                    String subPrefix = join(prefix, stringValue(member, FORGE_SUB_MODEL, ""));
                    if (member.asType().getKind() == TypeKind.DECLARED) {
                        checkFields((TypeElement) ((DeclaredType) member.asType()).asElement(), entity, subPrefix);
                    }
                    continue;
                }
                if (!isValueType(member.asType())) {
                    continue;
                }
                checkValue(member, member, entity, join(prefix, stringValue(member, FORGE_FIELD, member.getSimpleName().toString())));
            }
        }
    }

    private void checkValue(Element reportOn, Element field, DeclaredType entity, String path) {
        Object resolved = resolve(entity, path);
        if (resolved instanceof String error) {
            error(reportOn, "@ForgeModel field " + field.getSimpleName() + ": " + error);
        } else if (!convertible((TypeMirror) resolved, field.asType())) {
            error(reportOn, "@ForgeModel field " + field.getSimpleName() + ": " + path + " is " + resolved
                    + ", which can not be assigned to " + field.asType());
        }
    }

    private Element inheritedField(TypeElement type, String name) {
        for (TypeElement current = type; current != null; current = superclass(current)) {
            for (Element member : current.getEnclosedElements()) {
                if (member.getKind() == ElementKind.FIELD && member.getSimpleName().contentEquals(name) && !member.getModifiers().contains(Modifier.STATIC)) {
                    return member;
                }
            }
        }
        return null;
    }

    private static List<String> inheritedNames(TypeElement model) {
        List<String> names = new ArrayList<>();
        for (AnnotationMirror mirror : model.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(FORGE_MODEL)) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals("inherited") && entry.getValue().getValue() instanceof List<?> values) {
                        for (Object value : values) {
                            names.add(((AnnotationValue) value).getValue().toString());
                        }
                    }
                }
            }
        }
        return names;
    }

    /** Type of {@code path} in {@code entity}, or an error message. */
    private Object resolve(DeclaredType entity, String path) {
        DeclaredType current = entity;
        String[] segments = path.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            Element field = findField((TypeElement) current.asElement(), segments[i]);
            if (field == null) {
                return "no attribute '" + segments[i] + "' in " + current.asElement().getSimpleName() + " (path " + path + ")";
            }
            TypeMirror type = processingEnv.getTypeUtils().asMemberOf(current, field);
            if (i == segments.length - 1) {
                return type;
            }
            Element target = type.getKind() == TypeKind.DECLARED ? ((DeclaredType) type).asElement() : null;
            if (target == null || !(hasAnnotation(target, ENTITY) || hasAnnotation(target, EMBEDDABLE))) {
                return "'" + segments[i] + "' is not an association or embeddable (path " + path + ")";
            }
            current = (DeclaredType) type;
        }
        return "empty path";
    }

    private Element findField(TypeElement type, String name) {
        for (TypeElement current = type; current != null; current = superclass(current)) {
            for (Element member : current.getEnclosedElements()) {
                if (member.getKind() == ElementKind.FIELD && member.getSimpleName().contentEquals(name) && !member.getModifiers().contains(Modifier.STATIC)) {
                    return member;
                }
            }
        }
        return null;
    }

    private TypeElement superclass(TypeElement type) {
        TypeMirror superclass = type.getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED) {
            return null;
        }
        TypeElement element = (TypeElement) ((DeclaredType) superclass).asElement();
        return element.getQualifiedName().contentEquals("java.lang.Object") ? null : element;
    }

    private boolean isValueType(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return true;
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) type).getComponentType().getKind() == TypeKind.BYTE;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        if (((DeclaredType) type).asElement().getKind() == ElementKind.ENUM) {
            return true;
        }
        for (String name : List.of("java.lang.String", "java.lang.Number", "java.lang.Boolean", "java.lang.Character",
                "java.time.temporal.Temporal", "java.util.Date", "java.util.UUID")) {
            if (isSubtype(type, name)) {
                return true;
            }
        }
        return false;
    }

    /** Same rules as at runtime: assignable after boxing, anything to String, number to number. */
    private boolean convertible(TypeMirror source, TypeMirror target) {
        TypeMirror from = box(source);
        TypeMirror to = box(target);
        TypeMirror string = processingEnv.getElementUtils().getTypeElement("java.lang.String").asType();
        return processingEnv.getTypeUtils().isAssignable(from, to) || processingEnv.getTypeUtils().isSameType(to, string)
                || isSubtype(from, "java.lang.Number") && isSubtype(to, "java.lang.Number");
    }

    private TypeMirror box(TypeMirror type) {
        return type.getKind().isPrimitive() ? processingEnv.getTypeUtils().boxedClass((javax.lang.model.type.PrimitiveType) type).asType() : type;
    }

    private boolean isSubtype(TypeMirror type, String className) {
        TypeElement element = processingEnv.getElementUtils().getTypeElement(className);
        return element != null && processingEnv.getTypeUtils().isSubtype(processingEnv.getTypeUtils().erasure(type),
                processingEnv.getTypeUtils().erasure(element.asType()));
    }

    private static String join(String prefix, String path) {
        if (prefix.isEmpty()) {
            return path;
        }
        return path.isEmpty() ? prefix : prefix + "." + path;
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private static TypeMirror annotationValue(Element element, String annotationName, String attribute) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotationName)) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals(attribute) && entry.getValue().getValue() instanceof TypeMirror type) {
                        return type;
                    }
                }
            }
        }
        return null;
    }

    private static String stringValue(Element element, String annotationName, String defaultValue) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotationName)) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals("value")) {
                        return entry.getValue().getValue().toString();
                    }
                }
                return "";
            }
        }
        return defaultValue;
    }

    private void generate(TypeElement entity) {
        String packageName = packageOf(entity).getQualifiedName().toString();
        String modelName = modelSimpleName(entity);
        String qualifiedName = packageName.isEmpty() ? modelName : packageName + "." + modelName;

        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("/**\n")
                .append(" * Query model of {@link ").append(entity.getQualifiedName()).append("} for jpa-query-forge where lambdas.\n")
                .append(" * Generated at build time; never instantiated.\n")
                .append(" */\n")
                .append("@javax.annotation.processing.Generated(\"").append(getClass().getName()).append("\")\n")
                .append("public final class ").append(modelName).append(" implements ").append(QUERY_MODEL).append(" {\n\n");

        for (Map.Entry<String, String> field : persistentFields(entity).entrySet()) {
            source.append("    public ").append(field.getValue()).append(' ').append(field.getKey()).append(";\n");
        }

        source.append("\n    private ").append(modelName).append("() {\n    }\n}\n");

        Filer filer = processingEnv.getFiler();
        try (Writer writer = filer.createSourceFile(qualifiedName, entity).openWriter()) {
            writer.write(source.toString());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Could not generate query model " + qualifiedName + ": " + e.getMessage(), entity);
        }
    }

    /**
     * Field name to model type, including fields inherited from entity and mapped superclasses.
     * Superclass fields come first; type variables are resolved against the entity.
     */
    private Map<String, String> persistentFields(TypeElement entity) {
        DeclaredType entityType = (DeclaredType) entity.asType();
        List<TypeElement> hierarchy = new ArrayList<>();
        for (TypeElement type = entity; type != null; type = jpaSuperclass(type)) {
            hierarchy.add(0, type);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (TypeElement type : hierarchy) {
            for (Element member : type.getEnclosedElements()) {
                if (member.getKind() != ElementKind.FIELD) {
                    continue;
                }
                Set<Modifier> modifiers = member.getModifiers();
                if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.TRANSIENT) || hasAnnotation(member, TRANSIENT)) {
                    continue;
                }
                TypeMirror fieldType = processingEnv.getTypeUtils().asMemberOf(entityType, member);
                fields.put(member.getSimpleName().toString(), modelType(fieldType));
            }
        }
        return fields;
    }

    private TypeElement jpaSuperclass(TypeElement type) {
        TypeMirror superclass = type.getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED) {
            return null;
        }
        TypeElement element = (TypeElement) ((DeclaredType) superclass).asElement();
        return hasAnnotation(element, ENTITY) || hasAnnotation(element, MAPPED_SUPERCLASS) ? element : null;
    }

    /** Source name of the model field type: entities and embeddables become their query models. */
    private String modelType(TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE:
                return type.getKind().name().toLowerCase();
            case ARRAY:
                return modelType(((ArrayType) type).getComponentType()) + "[]";
            case WILDCARD: {
                TypeMirror bound = ((WildcardType) type).getExtendsBound();
                return bound != null ? modelType(bound) : "java.lang.Object";
            }
            case DECLARED: {
                DeclaredType declared = (DeclaredType) type;
                TypeElement element = (TypeElement) declared.asElement();
                if (hasAnnotation(element, ENTITY) || hasAnnotation(element, EMBEDDABLE)) {
                    String packageName = packageOf(element).getQualifiedName().toString();
                    return packageName.isEmpty() ? modelSimpleName(element) : packageName + "." + modelSimpleName(element);
                }
                String name = element.getQualifiedName().toString();
                if (declared.getTypeArguments().isEmpty()) {
                    return name;
                }
                return name + declared.getTypeArguments().stream().map(this::modelType).collect(Collectors.joining(", ", "<", ">"));
            }
            default:
                return "java.lang.Object";
        }
    }

    /** {@code QPurchaseOrder}; nested classes are flattened: {@code QOuter_Inner}. */
    private static String modelSimpleName(TypeElement element) {
        StringBuilder name = new StringBuilder(element.getSimpleName());
        for (Element enclosing = element.getEnclosingElement(); enclosing instanceof TypeElement; enclosing = enclosing.getEnclosingElement()) {
            name.insert(0, enclosing.getSimpleName() + "_");
        }
        return "Q" + name;
    }

    private static PackageElement packageOf(Element element) {
        Element current = element;
        while (!(current instanceof PackageElement)) {
            current = current.getEnclosingElement();
        }
        return (PackageElement) current;
    }

    private static boolean hasAnnotation(Element element, String annotationName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotationName)) {
                return true;
            }
        }
        return false;
    }
}

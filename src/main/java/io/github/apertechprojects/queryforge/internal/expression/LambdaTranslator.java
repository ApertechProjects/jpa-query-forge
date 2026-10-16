package io.github.apertechprojects.queryforge.internal.expression;

import io.github.apertechprojects.queryforge.exception.QueryForgeUnsupportedExpressionException;
import io.github.apertechprojects.queryforge.expression.ForgePredicate;
import io.github.apertechprojects.queryforge.expression.ForgeSelector;
import io.github.apertechprojects.queryforge.expression.QueryModel;
import io.github.apertechprojects.queryforge.internal.expression.Formula.Operator;
import io.github.apertechprojects.queryforge.internal.expression.MethodCode.Insn;
import io.github.apertechprojects.queryforge.internal.expression.Operand.Call;
import io.github.apertechprojects.queryforge.internal.expression.Operand.FieldRef;
import io.github.apertechprojects.queryforge.internal.expression.Operand.Function;
import io.github.apertechprojects.queryforge.internal.expression.Operand.Value;
import org.springframework.asm.Type;

import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.asm.Opcodes.*;

/**
 * Translates a {@link ForgePredicate} lambda into a {@link Formula} by interpreting its bytecode.
 * <p>
 * The model parameter is symbolic: field reads on it become attribute paths. Everything else (constants,
 * captured variables, calls on them) is evaluated, so conditions that do not depend on the model are decided
 * while translating. Each conditional jump on a symbolic value becomes a node of a decision graph; the graph is
 * then folded back into {@code &&}/{@code ||} the way javac compiled them, so the predicate keeps the structure
 * written in the source.
 */
public final class LambdaTranslator {

    private static final Map<String, MethodCode> CODE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_STEPS = 100_000;
    private static final String F_CLASS = "io/github/apertechprojects/queryforge/expression/F";
    private static final Set<String> BOX_OWNERS = Set.of("java/lang/Integer", "java/lang/Long", "java/lang/Short",
            "java/lang/Byte", "java/lang/Double", "java/lang/Float", "java/lang/Boolean", "java/lang/Character", "java/lang/Number");
    private static final Set<String> UNBOX_METHODS = Set.of("intValue", "longValue", "shortValue", "byteValue",
            "doubleValue", "floatValue", "booleanValue", "charValue");
    private static final Set<String> COLLECTION_OWNERS = Set.of("java/util/Collection", "java/util/List", "java/util/Set",
            "java/util/SortedSet", "java/util/NavigableSet", "java/util/Queue", "java/util/Deque");
    private static final Type BOOLEAN_OBJECT = Type.getObjectType("java/lang/Boolean");
    private static final Type STRING = Type.getObjectType("java/lang/String");
    private static final Type OBJECT = Type.getObjectType("java/lang/Object");
    /** Captured value whose content is not known: only used by {@link #verify}. */
    private static final Object UNKNOWN = new Object() {
        @Override
        public String toString() {
            return "<unknown>";
        }
    };
    private static final Formula UNKNOWN_CONDITION = new Formula.IsNull(new Value(UNKNOWN));

    private LambdaTranslator() {
    }

    public static Formula translate(ForgePredicate<?> predicate) {
        SerializedLambda lambda = serialize(predicate);
        return interpreter(lambda, predicate.getClass().getClassLoader()).run(locals(lambda));
    }

    /**
     * Translates a sort key: a lambda over the query model ({@code o -> o.supplier.name}) or an entity getter
     * reference ({@code PurchaseOrder::getCreatedAt}). A getter whose bytecode can not be interpreted (e.g.
     * rewritten by bytecode enhancement) falls back to the property name of the getter.
     */
    public static Operand translateSelector(Serializable selector) {
        SerializedLambda lambda = serialize(selector);
        try {
            return interpreter(lambda, selector.getClass().getClassLoader()).value(locals(lambda));
        } catch (QueryForgeUnsupportedExpressionException e) {
            String property = getterProperty(lambda);
            if (property == null) {
                throw e;
            }
            return new FieldRef(property);
        }
    }

    /**
     * Splits a select lambda into the attribute paths it reads and a row mapper. The paths are the field chains
     * read from the model parameter ({@code o.supplier.name}); the lambda itself runs once per row on a model
     * filled with the selected values, so it may build its result any way (constructor, builder, setters).
     */
    public static <R> ModelProjection<R> projection(ForgeSelector<?, R> selector) {
        SerializedLambda lambda = serialize(selector);
        ClassLoader classLoader = selector.getClass().getClassLoader();
        MethodCode code = code(lambda, classLoader);
        Class<?> modelClass = loadClass(Type.getArgumentTypes(lambda.getInstantiatedMethodType())[0].getClassName(), classLoader);
        int modelSlot = modelSlot(lambda);

        return new ModelProjection<>(selector, modelClass, projectionPaths(code, modelClass, modelSlot, classLoader));
    }

    private static List<String> projectionPaths(MethodCode code, Class<?> modelClass, int modelSlot, ClassLoader classLoader) {
        Set<String> paths = new LinkedHashSet<>();
        List<Insn> instructions = code.instructions;
        for (int i = 0; i < instructions.size(); i++) {
            Insn insn = instructions.get(i);
            if (insn.opcode != ALOAD || insn.operand != modelSlot) {
                continue;
            }
            StringBuilder path = new StringBuilder();
            Class<?> type = modelClass;
            for (int j = i + 1; j < instructions.size() && instructions.get(j).opcode == GETFIELD && QueryModel.class.isAssignableFrom(type); j++) {
                Insn field = instructions.get(j);
                path.append(path.isEmpty() ? "" : ".").append(field.name);
                type = loadClass(Type.getType(field.descriptor).getClassName(), classLoader);
            }
            String location = code.className + (insn.line > 0 ? " line " + insn.line : "");
            if (path.isEmpty()) {
                throw new QueryForgeUnsupportedExpressionException("Unsupported select lambda in " + location
                        + ": the model can only be used to read its fields");
            }
            if (QueryModel.class.isAssignableFrom(type)) {
                throw new QueryForgeUnsupportedExpressionException("Unsupported select lambda in " + location + ": " + path
                        + " is a navigation, select one of its fields (e.g. " + path + ".id)");
            }
            if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
                throw new QueryForgeUnsupportedExpressionException("Unsupported select lambda in " + location + ": collection " + path + " can not be selected");
            }
            paths.add(path.toString());
        }
        if (paths.isEmpty()) {
            throw new QueryForgeUnsupportedExpressionException("The select lambda in " + code.className + " does not read any field of the model");
        }
        return new ArrayList<>(paths);
    }

    /**
     * Checks a compiled lambda body without running it: captured values are unknown, so conditions on them are
     * followed both ways. A method taking the query model and returning {@code boolean} is checked as a where
     * lambda; any other one must be usable as a select or as a sort key.
     *
     * @return the problems found, empty when the lambda translates
     */
    public static List<String> verify(ClassLoader classLoader, String internalClassName, String methodName, String descriptor,
                                      boolean isStatic, int modelParameter, Class<?> modelClass) {
        MethodCode code = MethodCode.read(classLoader, internalClassName, methodName, descriptor);
        Type[] parameterTypes = Type.getArgumentTypes(descriptor);
        List<Type> types = new ArrayList<>();
        if (!isStatic) {
            types.add(Type.getObjectType(internalClassName));
        }
        types.addAll(Arrays.asList(parameterTypes));
        int model = modelParameter + (isStatic ? 0 : 1);
        Sym[] locals = new Sym[types.stream().mapToInt(Type::getSize).sum()];
        int slot = 0;
        int modelSlot = 0;
        for (int i = 0; i < types.size(); i++) {
            if (i == model) {
                modelSlot = slot;
                locals[slot] = new Expr(new FieldRef(""), types.get(i));
            } else {
                locals[slot] = new Val(UNKNOWN, types.get(i));
            }
            slot += types.get(i).getSize();
        }
        try {
            if (Type.getReturnType(descriptor).getSort() == Type.BOOLEAN) {
                new Interpreter(code, classLoader, true).run(locals);
                return List.of();
            }
        } catch (QueryForgeUnsupportedExpressionException e) {
            return List.of(e.getMessage());
        }
        try {
            projectionPaths(code, modelClass, modelSlot, classLoader);
            return List.of();
        } catch (QueryForgeUnsupportedExpressionException asSelect) {
            try {
                new Interpreter(code, classLoader, true).value(locals);
                return List.of();
            } catch (QueryForgeUnsupportedExpressionException asSortKey) {
                return List.of(asSelect.getMessage() + " (and as sort key: " + asSortKey.getMessage() + ")");
            }
        }
    }

    private static Interpreter interpreter(SerializedLambda lambda, ClassLoader classLoader) {
        return new Interpreter(code(lambda, classLoader), classLoader, false);
    }

    private static MethodCode code(SerializedLambda lambda, ClassLoader classLoader) {
        String key = System.identityHashCode(classLoader) + ":" + lambda.getImplClass() + "#" + lambda.getImplMethodName() + lambda.getImplMethodSignature();
        return CODE_CACHE.computeIfAbsent(key, k -> MethodCode.read(classLoader, lambda.getImplClass(), lambda.getImplMethodName(), lambda.getImplMethodSignature()));
    }

    /** Local variable slot of the model parameter: after the receiver and the captured values. */
    private static int modelSlot(SerializedLambda lambda) {
        if (lambda.getImplMethodKind() != MethodHandleInfo.REF_invokeStatic && lambda.getCapturedArgCount() == 0) {
            return 0; // unbound method reference: the model is the receiver
        }
        int slot = lambda.getImplMethodKind() != MethodHandleInfo.REF_invokeStatic ? 1 : 0;
        Type[] parameterTypes = Type.getArgumentTypes(lambda.getImplMethodSignature());
        int captured = lambda.getImplMethodKind() != MethodHandleInfo.REF_invokeStatic ? lambda.getCapturedArgCount() - 1 : lambda.getCapturedArgCount();
        for (int i = 0; i < captured; i++) {
            slot += parameterTypes[i].getSize();
        }
        return slot;
    }

    private static Class<?> loadClass(String name, ClassLoader classLoader) {
        try {
            return switch (name) {
                case "boolean" -> boolean.class;
                case "byte" -> byte.class;
                case "short" -> short.class;
                case "char" -> char.class;
                case "int" -> int.class;
                case "long" -> long.class;
                case "float" -> float.class;
                case "double" -> double.class;
                default -> Class.forName(name, false, classLoader);
            };
        } catch (ClassNotFoundException e) {
            throw new QueryForgeUnsupportedExpressionException("Class " + name + " not found", e);
        }
    }

    /**
     * Arguments of the implementation method: the captured values followed by the model. For instance methods
     * the first of them is the receiver (the capturing object, or the model for an unbound getter reference).
     */
    private static Sym[] locals(SerializedLambda lambda) {
        List<Object> arguments = new ArrayList<>();
        for (int i = 0; i < lambda.getCapturedArgCount(); i++) {
            arguments.add(lambda.getCapturedArg(i));
        }
        Type[] parameterTypes = Type.getArgumentTypes(lambda.getImplMethodSignature());
        boolean instanceMethod = lambda.getImplMethodKind() != MethodHandleInfo.REF_invokeStatic;
        List<Type> types = new ArrayList<>();
        if (instanceMethod) {
            types.add(Type.getObjectType(lambda.getImplClass()));
        }
        types.addAll(Arrays.asList(parameterTypes));

        Sym[] locals = new Sym[types.stream().mapToInt(Type::getSize).sum()];
        int slot = 0;
        for (int i = 0; i < types.size(); i++) {
            Type type = types.get(i);
            locals[slot] = i < arguments.size()
                    ? new Val(normalize(arguments.get(i), type), type)
                    : new Expr(new FieldRef(""), type);
            slot += type.getSize();
        }
        return locals;
    }

    private static String getterProperty(SerializedLambda lambda) {
        String name = lambda.getImplMethodName();
        boolean unboundGetter = lambda.getCapturedArgCount() == 0
                && lambda.getImplMethodKind() != MethodHandleInfo.REF_invokeStatic
                && Type.getArgumentTypes(lambda.getImplMethodSignature()).length == 0;
        if (!unboundGetter) {
            return null;
        }
        int prefix = name.startsWith("get") ? 3 : name.startsWith("is") ? 2 : 0;
        if (prefix == 0 || name.length() == prefix || !Character.isUpperCase(name.charAt(prefix))) {
            return null;
        }
        String property = name.substring(prefix);
        // JavaBeans rule: getURL -> URL, getName -> name
        if (property.length() > 1 && Character.isUpperCase(property.charAt(1))) {
            return property;
        }
        return Character.toLowerCase(property.charAt(0)) + property.substring(1);
    }

    private static SerializedLambda serialize(Serializable function) {
        try {
            Method writeReplace = function.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replacement = writeReplace.invoke(function);
            if (replacement instanceof SerializedLambda lambda) {
                return lambda;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new QueryForgeUnsupportedExpressionException("Expected a lambda or method reference, not " + function.getClass().getName(), e);
        }
        throw new QueryForgeUnsupportedExpressionException("Expected a lambda or method reference, not " + function.getClass().getName());
    }

    // ------------------------------------------------------------------ symbolic values

    private sealed interface Sym {
        Type type();
    }

    /** Concrete value. Booleans, chars, bytes and shorts are held as {@link Integer}, like on the JVM stack. */
    private record Val(Object value, Type type) implements Sym {
    }

    /** Value depending on the model. */
    private record Expr(Operand operand, Type type) implements Sym {
    }

    /** Boolean depending on the model. */
    private record Cond(Formula formula) implements Sym {
        public Type type() {
            return Type.BOOLEAN_TYPE;
        }
    }

    /** Result of {@code compareTo} or a long/float/double comparison, to be compared with zero. */
    private record Cmp(Operand left, Operand right) implements Sym {
        public Type type() {
            return Type.INT_TYPE;
        }
    }

    /** Object created by {@code NEW} whose constructor has not run yet. */
    private record Uninit(String internalName, int id) implements Sym {
        public Type type() {
            return Type.getObjectType(internalName);
        }
    }

    private static final class Frame {
        final Sym[] locals;
        final List<Sym> stack;

        Frame(Sym[] locals, List<Sym> stack) {
            this.locals = locals;
            this.stack = stack;
        }

        Frame copy() {
            return new Frame(locals.clone(), new ArrayList<>(stack));
        }

        Object key(int pc) {
            return List.of(pc, Arrays.asList(locals.clone()), new ArrayList<>(stack));
        }

        void push(Sym sym) {
            stack.add(sym);
        }

        Sym pop() {
            return stack.remove(stack.size() - 1);
        }

        Sym peek() {
            return stack.get(stack.size() - 1);
        }
    }

    // ------------------------------------------------------------------ decision graph

    private static class Node {
    }

    private static final Node TRUE_NODE = new Node();
    private static final Node FALSE_NODE = new Node();

    private static final class Branch extends Node {
        Formula condition;
        Node whenTrue;
        Node whenFalse;

        Branch(Formula condition, Node whenTrue, Node whenFalse) {
            this.condition = condition;
            this.whenTrue = whenTrue;
            this.whenFalse = whenFalse;
        }
    }

    private static Node node(Formula formula) {
        if (formula instanceof Formula.Constant constant) {
            return constant.value() ? TRUE_NODE : FALSE_NODE;
        }
        return new Branch(formula, TRUE_NODE, FALSE_NODE);
    }

    /**
     * Merges branch chains produced by short-circuit evaluation: a branch whose false successor is only reached
     * from it and shares its true successor is an {@code ||}, one whose true successor shares its false successor
     * is an {@code &&}.
     */
    private static Formula fold(Node root) {
        boolean changed = true;
        while (changed && root instanceof Branch rootBranch) {
            if (rootBranch.whenTrue == rootBranch.whenFalse) {
                root = rootBranch.whenTrue;
                continue;
            }
            changed = false;
            Map<Node, Integer> predecessors = new IdentityHashMap<>();
            List<Branch> branches = new ArrayList<>();
            collect(root, predecessors, branches);
            for (Branch node : branches) {
                if (node.whenTrue instanceof Branch child && child.whenTrue == child.whenFalse) {
                    node.whenTrue = child.whenTrue;
                } else if (node.whenFalse instanceof Branch child && child.whenTrue == child.whenFalse) {
                    node.whenFalse = child.whenTrue;
                } else if (node.whenFalse instanceof Branch child && predecessors.get(child) == 1 && child.whenTrue == node.whenTrue) {
                    node.condition = Formula.or(node.condition, child.condition);
                    node.whenFalse = child.whenFalse;
                } else if (node.whenFalse instanceof Branch child && predecessors.get(child) == 1 && child.whenFalse == node.whenTrue) {
                    node.condition = Formula.or(node.condition, Formula.not(child.condition));
                    node.whenFalse = child.whenTrue;
                } else if (node.whenTrue instanceof Branch child && predecessors.get(child) == 1 && child.whenFalse == node.whenFalse) {
                    node.condition = Formula.and(node.condition, child.condition);
                    node.whenTrue = child.whenTrue;
                } else if (node.whenTrue instanceof Branch child && predecessors.get(child) == 1 && child.whenTrue == node.whenFalse) {
                    node.condition = Formula.and(node.condition, Formula.not(child.condition));
                    node.whenTrue = child.whenFalse;
                } else {
                    continue;
                }
                changed = true;
                break;
            }
        }
        return toFormula(root, new IdentityHashMap<>());
    }

    private static void collect(Node node, Map<Node, Integer> predecessors, List<Branch> branches) {
        if (node instanceof Branch branch && !predecessors.containsKey(branch)) {
            predecessors.put(branch, 0);
            branches.add(branch);
            for (Node child : List.of(branch.whenTrue, branch.whenFalse)) {
                collect(child, predecessors, branches);
                predecessors.merge(child, 1, Integer::sum);
            }
        }
    }

    private static Formula toFormula(Node node, Map<Node, Formula> done) {
        if (node == TRUE_NODE) {
            return Formula.TRUE;
        }
        if (node == FALSE_NODE) {
            return Formula.FALSE;
        }
        Formula formula = done.get(node);
        if (formula == null) {
            Branch branch = (Branch) node;
            formula = Formula.choose(branch.condition, toFormula(branch.whenTrue, done), toFormula(branch.whenFalse, done));
            done.put(node, formula);
        }
        return formula;
    }

    // ------------------------------------------------------------------ interpreter

    private static final class Interpreter {
        private final MethodCode code;
        private final ClassLoader classLoader;
        private final Map<Object, Node> visited = new HashMap<>();
        private final Set<Object> inProgress = new HashSet<>();
        private int steps;
        private int nextUninitId;
        private final boolean verifying;
        private Insn current;

        Interpreter(MethodCode code, ClassLoader classLoader, boolean verifying) {
            this.code = code;
            this.classLoader = classLoader;
            this.verifying = verifying;
        }

        private static boolean unknown(Sym... values) {
            for (Sym value : values) {
                if (value instanceof Val val && val.value() == UNKNOWN) {
                    return true;
                }
            }
            return false;
        }

        private static boolean allValues(Sym... values) {
            for (Sym value : values) {
                if (!(value instanceof Val)) {
                    return false;
                }
            }
            return true;
        }

        /** Verify mode: an instruction that only combines values, at least one unknown, gives an unknown value. */
        private boolean unknownResult(Frame frame, int pops, Type result) {
            if (!verifying || frame.stack.size() < pops) {
                return false;
            }
            Sym[] operands = frame.stack.subList(frame.stack.size() - pops, frame.stack.size()).toArray(new Sym[0]);
            if (!allValues(operands) || !unknown(operands)) {
                return false;
            }
            for (int i = 0; i < pops; i++) {
                frame.pop();
            }
            if (result != null) {
                frame.push(new Val(UNKNOWN, result));
            }
            return true;
        }

        Formula run(Sym[] arguments) {
            return fold(explore(0, frame(arguments)));
        }

        /** Result of a lambda that returns a value; only conditions decided while translating are allowed. */
        Operand value(Sym[] arguments) {
            Frame frame = frame(arguments);
            int pc = 0;
            while (true) {
                if (++steps > MAX_STEPS) {
                    throw unsupported("the lambda is too complex (loops are not supported)");
                }
                Insn insn = code.instructions.get(pc);
                current = insn;
                int opcode = insn.opcode;
                if (opcode == GOTO) {
                    pc = insn.target;
                } else if ((opcode >= IFEQ && opcode <= IF_ACMPNE) || opcode == IFNULL || opcode == IFNONNULL) {
                    Formula condition = condition(insn, frame);
                    if (condition == UNKNOWN_CONDITION) {
                        pc++;
                        continue;
                    }
                    if (!(condition instanceof Formula.Constant constant)) {
                        throw unsupported("conditions on model fields are not supported in sort keys");
                    }
                    pc = constant.value() ? insn.target : pc + 1;
                } else if (opcode >= IRETURN && opcode <= ARETURN) {
                    Sym result = frame.pop();
                    if (result instanceof Val) {
                        throw unsupported("the sort key does not use the model");
                    }
                    if (result instanceof Expr expr && expr.operand() instanceof FieldRef field && field.path().isEmpty()) {
                        throw unsupported("the model itself can not be used, use one of its fields");
                    }
                    return operand(result);
                } else if (opcode == RETURN) {
                    throw unsupported("the sort key must return a value");
                } else {
                    execute(insn, frame);
                    pc++;
                }
            }
        }

        private Frame frame(Sym[] arguments) {
            return new Frame(Arrays.copyOf(arguments, Math.max(code.maxLocals, arguments.length)), new ArrayList<>());
        }

        private Node explore(int pc, Frame frame) {
            while (true) {
                if (++steps > MAX_STEPS) {
                    throw unsupported("the lambda is too complex (loops are not supported)");
                }
                Insn insn = code.instructions.get(pc);
                current = insn;
                int opcode = insn.opcode;

                if (opcode == GOTO) {
                    pc = insn.target;
                } else if ((opcode >= IFEQ && opcode <= IF_ACMPNE) || opcode == IFNULL || opcode == IFNONNULL) {
                    Object key = frame.key(pc);
                    Formula condition = condition(insn, frame);
                    if (condition instanceof Formula.Constant constant) {
                        pc = constant.value() ? insn.target : pc + 1;
                        continue;
                    }
                    Node known = visited.get(key);
                    if (known != null) {
                        return known;
                    }
                    if (!inProgress.add(key)) {
                        throw unsupported("loops are not supported");
                    }
                    Node node = new Branch(condition, explore(insn.target, frame.copy()), explore(pc + 1, frame.copy()));
                    inProgress.remove(key);
                    visited.put(key, node);
                    return node;
                } else if (opcode == IRETURN) {
                    return result(frame.pop());
                } else if (opcode >= IRETURN && opcode <= RETURN) {
                    throw unsupported("the lambda must return boolean");
                } else {
                    execute(insn, frame);
                    pc++;
                }
            }
        }

        private Node result(Sym value) {
            if (value instanceof Val val) {
                return unknown(val) || truthy(val) ? TRUE_NODE : FALSE_NODE;
            }
            if (value instanceof Cond cond) {
                return node(cond.formula());
            }
            if (value instanceof Expr expr && isBoolean(expr.type())) {
                return node(new Formula.IsTrue(expr.operand()));
            }
            throw unsupported("the lambda does not return a condition");
        }

        /** Condition under which the jump is taken. */
        private Formula condition(Insn insn, Frame frame) {
            int opcode = insn.opcode;
            if (opcode >= IFEQ && opcode <= IFLE) {
                Operator operator = operator(opcode - IFEQ);
                Sym value = frame.pop();
                if (unknown(value)) {
                    return UNKNOWN_CONDITION;
                }
                if (value instanceof Val val) {
                    return constant(operator.test(Integer.compare(intValue(val), 0)));
                }
                if (value instanceof Cmp cmp) {
                    return new Formula.Comparison(operator, cmp.left(), cmp.right());
                }
                if (value instanceof Cond cond && (operator == Operator.EQ || operator == Operator.NE)) {
                    return operator == Operator.NE ? cond.formula() : Formula.not(cond.formula());
                }
                if (value instanceof Expr expr && isBoolean(expr.type()) && (operator == Operator.EQ || operator == Operator.NE)) {
                    Formula isTrue = new Formula.IsTrue(expr.operand());
                    return operator == Operator.NE ? isTrue : Formula.not(isTrue);
                }
                return new Formula.Comparison(operator, operand(value), new Value(0));
            }
            if (opcode >= IF_ICMPEQ && opcode <= IF_ICMPLE) {
                Operator operator = operator(opcode - IF_ICMPEQ);
                Sym right = frame.pop();
                Sym left = frame.pop();
                if (allValues(left, right) && unknown(left, right)) {
                    return UNKNOWN_CONDITION;
                }
                if (left instanceof Val l && right instanceof Val r) {
                    return constant(operator.test(Integer.compare(intValue(l), intValue(r))));
                }
                return new Formula.Comparison(operator, operand(left), operand(right));
            }
            if (opcode == IF_ACMPEQ || opcode == IF_ACMPNE) {
                Operator operator = opcode == IF_ACMPEQ ? Operator.EQ : Operator.NE;
                Sym right = frame.pop();
                Sym left = frame.pop();
                if (allValues(left, right) && unknown(left, right)) {
                    return UNKNOWN_CONDITION;
                }
                if (left instanceof Val l && right instanceof Val r) {
                    return constant((l.value() == r.value()) == (operator == Operator.EQ));
                }
                return new Formula.Comparison(operator, operand(left), operand(right));
            }
            Sym value = frame.pop();
            boolean ifNull = opcode == IFNULL;
            if (unknown(value)) {
                return UNKNOWN_CONDITION;
            }
            if (value instanceof Val val) {
                return constant((val.value() == null) == ifNull);
            }
            Formula isNull = new Formula.IsNull(operand(value));
            return ifNull ? isNull : Formula.not(isNull);
        }

        private static Operator operator(int index) {
            return switch (index) {
                case 0 -> Operator.EQ;
                case 1 -> Operator.NE;
                case 2 -> Operator.LT;
                case 3 -> Operator.GE;
                case 4 -> Operator.GT;
                default -> Operator.LE;
            };
        }

        private void execute(Insn insn, Frame frame) {
            int opcode = insn.opcode;
            if (verifying && unknownInstruction(insn, frame)) {
                return;
            }
            switch (opcode) {
                case NOP -> {
                }
                case ACONST_NULL -> frame.push(new Val(null, OBJECT));
                case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 ->
                        frame.push(new Val(opcode - ICONST_0, Type.INT_TYPE));
                case LCONST_0, LCONST_1 -> frame.push(new Val((long) (opcode - LCONST_0), Type.LONG_TYPE));
                case FCONST_0, FCONST_1, FCONST_2 -> frame.push(new Val((float) (opcode - FCONST_0), Type.FLOAT_TYPE));
                case DCONST_0, DCONST_1 -> frame.push(new Val((double) (opcode - DCONST_0), Type.DOUBLE_TYPE));
                case BIPUSH, SIPUSH -> frame.push(new Val(insn.operand, Type.INT_TYPE));
                case LDC -> frame.push(ldc(insn.constant));
                case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> {
                    Sym value = frame.locals[insn.operand];
                    if (value == null) {
                        throw unsupported("unexpected local variable access");
                    }
                    frame.push(value);
                }
                case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> frame.locals[insn.operand] = frame.pop();
                case IINC -> {
                    Val value = concrete(frame.locals[insn.operand], "arithmetic on model fields in a loop");
                    frame.locals[insn.operand] = new Val(intValue(value) + insn.increment, Type.INT_TYPE);
                }
                case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> {
                    Val index = concrete(frame.pop(), "array access with a model index");
                    Val array = concrete(frame.pop(), "array access on a model field");
                    Object element = Array.get(array.value(), intValue(index));
                    Type type = array.type().getSort() == Type.ARRAY ? array.type().getElementType() : OBJECT;
                    if (array.type().getSort() == Type.ARRAY && array.type().getDimensions() > 1) {
                        type = Type.getType(array.type().getDescriptor().substring(1));
                    }
                    frame.push(new Val(normalize(element, type), type));
                }
                case IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE -> {
                    Sym value = frame.pop();
                    Val index = concrete(frame.pop(), "array access with a model index");
                    Val array = concrete(frame.pop(), "array access on a model field");
                    Val element = concrete(value, "model values inside arrays (use a collection value with contains instead)");
                    Object target = array.value();
                    Array.set(target, intValue(index), toJava(element.value(), Type.getType(target.getClass().getComponentType())));
                }
                case POP -> frame.pop();
                case POP2 -> {
                    if (frame.pop().type().getSize() == 1) {
                        frame.pop();
                    }
                }
                case DUP -> frame.push(frame.peek());
                case DUP_X1 -> {
                    Sym v1 = frame.pop();
                    Sym v2 = frame.pop();
                    frame.push(v1);
                    frame.push(v2);
                    frame.push(v1);
                }
                case DUP_X2 -> {
                    Sym v1 = frame.pop();
                    Sym v2 = frame.pop();
                    if (v2.type().getSize() == 2) {
                        frame.push(v1);
                        frame.push(v2);
                    } else {
                        Sym v3 = frame.pop();
                        frame.push(v1);
                        frame.push(v3);
                        frame.push(v2);
                    }
                    frame.push(v1);
                }
                case DUP2 -> {
                    Sym v1 = frame.pop();
                    if (v1.type().getSize() == 2) {
                        frame.push(v1);
                        frame.push(v1);
                    } else {
                        Sym v2 = frame.pop();
                        frame.push(v2);
                        frame.push(v1);
                        frame.push(v2);
                        frame.push(v1);
                    }
                }
                case SWAP -> {
                    Sym v1 = frame.pop();
                    Sym v2 = frame.pop();
                    frame.push(v1);
                    frame.push(v2);
                }
                case INEG, LNEG, FNEG, DNEG -> {
                    Sym value = frame.pop();
                    if (value instanceof Val val) {
                        frame.push(new Val(negate((Number) val.value()), val.type()));
                    } else {
                        frame.push(new Expr(new Call(Function.NEG, operand(value)), value.type()));
                    }
                }
                case LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> {
                    Sym right = frame.pop();
                    Sym left = frame.pop();
                    if (left instanceof Val l && right instanceof Val r) {
                        int result = compareNumbers((Number) l.value(), (Number) r.value());
                        if (Double.isNaN(((Number) l.value()).doubleValue()) || Double.isNaN(((Number) r.value()).doubleValue())) {
                            result = opcode == FCMPG || opcode == DCMPG ? 1 : -1;
                        }
                        frame.push(new Val(result, Type.INT_TYPE));
                    } else {
                        frame.push(new Cmp(operand(left), operand(right)));
                    }
                }
                case GETSTATIC -> {
                    Object value = readField(loadClass(insn.owner), insn.name, null);
                    Type type = Type.getType(insn.descriptor);
                    frame.push(new Val(normalize(value, type), type));
                }
                case GETFIELD -> {
                    Sym target = frame.pop();
                    Type type = Type.getType(insn.descriptor);
                    if (target instanceof Val val) {
                        if (val.value() == null) {
                            throw unsupported("field " + insn.name + " is read from a null value");
                        }
                        frame.push(new Val(normalize(readField(val.value().getClass(), insn.name, val.value()), type), type));
                    } else if (target instanceof Expr expr && expr.operand() instanceof FieldRef field) {
                        String path = field.path().isEmpty() ? insn.name : field.path() + "." + insn.name;
                        frame.push(new Expr(new FieldRef(path), type));
                    } else {
                        throw unsupported("field " + insn.name + " can only be read from the model or from values");
                    }
                }
                case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE -> invoke(insn, frame);
                case INVOKEDYNAMIC -> invokeDynamic(insn, frame);
                case NEW -> frame.push(new Uninit(insn.owner, nextUninitId++));
                case NEWARRAY -> {
                    int length = intValue(concrete(frame.pop(), "arrays sized by model fields"));
                    Class<?> component = switch (insn.operand) {
                        case T_BOOLEAN -> boolean.class;
                        case T_CHAR -> char.class;
                        case T_FLOAT -> float.class;
                        case T_DOUBLE -> double.class;
                        case T_BYTE -> byte.class;
                        case T_SHORT -> short.class;
                        case T_INT -> int.class;
                        default -> long.class;
                    };
                    Object array = Array.newInstance(component, length);
                    frame.push(new Val(array, Type.getType(array.getClass())));
                }
                case ANEWARRAY -> {
                    int length = intValue(concrete(frame.pop(), "arrays sized by model fields"));
                    Object array = Array.newInstance(loadClass(insn.owner), length);
                    frame.push(new Val(array, Type.getType(array.getClass())));
                }
                case ARRAYLENGTH -> frame.push(new Val(Array.getLength(concrete(frame.pop(), "arrays of the model").value()), Type.INT_TYPE));
                case CHECKCAST -> {
                    Sym value = frame.pop();
                    frame.push(value instanceof Expr expr ? new Expr(expr.operand(), Type.getObjectType(insn.owner)) : value);
                }
                case INSTANCEOF -> {
                    Val value = concrete(frame.pop(), "instanceof on model fields");
                    frame.push(new Val(loadClass(insn.owner).isInstance(value.value()) ? 1 : 0, Type.INT_TYPE));
                }
                default -> {
                    if (opcode >= IADD && opcode <= LXOR) {
                        arithmetic(opcode, frame);
                    } else if (opcode >= I2L && opcode <= I2S) {
                        convert(opcode, frame);
                    } else if (opcode == ATHROW) {
                        throw unsupported("throwing exceptions");
                    } else if (opcode == TABLESWITCH || opcode == LOOKUPSWITCH) {
                        throw unsupported("switch statements");
                    } else {
                        throw unsupported("instruction " + opcode);
                    }
                }
            }
        }

        private boolean unknownInstruction(Insn insn, Frame frame) {
            int opcode = insn.opcode;
            if (opcode == IINC) {
                return unknown(frame.locals[insn.operand]);
            }
            if ((opcode >= IADD && opcode <= DREM) || (opcode >= ISHL && opcode <= LXOR)) {
                return unknownResult(frame, 2, frame.stack.get(frame.stack.size() - 2).type());
            }
            if (opcode >= INEG && opcode <= DNEG) {
                return unknownResult(frame, 1, frame.peek().type());
            }
            if (opcode >= I2L && opcode <= I2S) {
                return unknownResult(frame, 1, conversionType(opcode));
            }
            if (opcode >= LCMP && opcode <= DCMPG) {
                return unknownResult(frame, 2, Type.INT_TYPE);
            }
            if (opcode == GETFIELD) {
                return unknownResult(frame, 1, Type.getType(insn.descriptor));
            }
            if (opcode >= IALOAD && opcode <= SALOAD) {
                Type element = switch (opcode) {
                    case LALOAD -> Type.LONG_TYPE;
                    case FALOAD -> Type.FLOAT_TYPE;
                    case DALOAD -> Type.DOUBLE_TYPE;
                    case AALOAD -> OBJECT;
                    default -> Type.INT_TYPE;
                };
                return unknownResult(frame, 2, element);
            }
            if (opcode >= IASTORE && opcode <= SASTORE) {
                return unknownResult(frame, 3, null);
            }
            if (opcode == ARRAYLENGTH || opcode == INSTANCEOF) {
                return unknownResult(frame, 1, Type.INT_TYPE);
            }
            if (opcode == NEWARRAY || opcode == ANEWARRAY) {
                return unknownResult(frame, 1, OBJECT);
            }
            return false;
        }

        private Val ldc(Object constant) {
            if (constant instanceof Integer) {
                return new Val(constant, Type.INT_TYPE);
            }
            if (constant instanceof Long) {
                return new Val(constant, Type.LONG_TYPE);
            }
            if (constant instanceof Float) {
                return new Val(constant, Type.FLOAT_TYPE);
            }
            if (constant instanceof Double) {
                return new Val(constant, Type.DOUBLE_TYPE);
            }
            if (constant instanceof String) {
                return new Val(constant, STRING);
            }
            if (constant instanceof Type type && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) {
                return new Val(loadClass(type.getInternalName()), Type.getObjectType("java/lang/Class"));
            }
            throw unsupported("constant " + constant);
        }

        private void arithmetic(int opcode, Frame frame) {
            boolean shiftOrLogic = opcode >= ISHL;
            int group = shiftOrLogic ? -1 : (opcode - IADD) / 4;
            Type type = switch (shiftOrLogic ? (opcode - ISHL) % 2 : (opcode - IADD) % 4) {
                case 0 -> Type.INT_TYPE;
                case 1 -> Type.LONG_TYPE;
                case 2 -> Type.FLOAT_TYPE;
                default -> Type.DOUBLE_TYPE;
            };
            Sym right = frame.pop();
            Sym left = frame.pop();
            if (left instanceof Val l && right instanceof Val r) {
                frame.push(new Val(compute(opcode, (Number) l.value(), (Number) r.value()), type));
                return;
            }
            Function function = switch (group) {
                case 0 -> Function.SUM;
                case 1 -> Function.DIFF;
                case 2 -> Function.PROD;
                case 3 -> Function.QUOT;
                case 4 -> Function.MOD;
                default -> throw unsupported("bitwise operations on model fields");
            };
            frame.push(new Expr(new Call(function, operand(left), operand(right)), type));
        }

        private static Type conversionType(int opcode) {
            return switch (opcode) {
                case I2L, F2L, D2L -> Type.LONG_TYPE;
                case I2F, L2F, D2F -> Type.FLOAT_TYPE;
                case I2D, L2D, F2D -> Type.DOUBLE_TYPE;
                default -> Type.INT_TYPE;
            };
        }

        private void convert(int opcode, Frame frame) {
            Type type = conversionType(opcode);
            Sym value = frame.pop();
            if (value instanceof Val val) {
                Number number = (Number) val.value();
                Object converted = switch (opcode) {
                    case I2L, F2L, D2L -> number.longValue();
                    case I2F, L2F, D2F -> number.floatValue();
                    case I2D, L2D, F2D -> number.doubleValue();
                    case I2B -> (int) (byte) number.intValue();
                    case I2C -> (int) (char) number.intValue();
                    case I2S -> (int) (short) number.intValue();
                    default -> number.intValue();
                };
                frame.push(new Val(converted, type));
            } else {
                frame.push(new Expr(operand(value), type));
            }
        }

        private void invoke(Insn insn, Frame frame) {
            Type[] argumentTypes = Type.getArgumentTypes(insn.descriptor);
            Type returnType = Type.getReturnType(insn.descriptor);
            Sym[] arguments = new Sym[argumentTypes.length];
            for (int i = arguments.length - 1; i >= 0; i--) {
                arguments[i] = frame.pop();
            }
            Sym receiver = insn.opcode == INVOKESTATIC ? null : frame.pop();

            if (insn.opcode == INVOKESPECIAL && insn.name.equals("<init>")) {
                if (verifying && unknown(arguments)) {
                    Val value = new Val(UNKNOWN, Type.getObjectType(insn.owner));
                    frame.stack.replaceAll(sym -> sym.equals(receiver) ? value : sym);
                    for (int i = 0; i < frame.locals.length; i++) {
                        if (receiver.equals(frame.locals[i])) {
                            frame.locals[i] = value;
                        }
                    }
                    return;
                }
                construct(insn, frame, (Uninit) receiver, arguments, argumentTypes);
                return;
            }
            if (verifying && !insn.owner.equals(F_CLASS) && (receiver == null || receiver instanceof Val)
                    && allValues(arguments) && (unknown(arguments) || (receiver != null && unknown(receiver)))) {
                if (returnType.getSort() != Type.VOID) {
                    frame.push(new Val(UNKNOWN, returnType));
                }
                return;
            }

            Sym result;
            if (insn.owner.equals(F_CLASS)) {
                result = function(insn.name, arguments);
            } else if ((receiver == null || receiver instanceof Val) && Arrays.stream(arguments).allMatch(Val.class::isInstance)) {
                Object value = call(insn, (Val) receiver, arguments, argumentTypes);
                if (returnType.getSort() == Type.VOID) {
                    return;
                }
                result = new Val(normalize(value, returnType), returnType);
            } else {
                result = symbolicCall(insn, receiver, arguments, returnType);
            }
            frame.push(result);
        }

        private Sym function(String name, Sym[] arguments) {
            return switch (name) {
                case "like" -> new Cond(new Formula.Like(operand(arguments[0]), patternValue(arguments[1]), null));
                case "notLike" -> new Cond(Formula.not(new Formula.Like(operand(arguments[0]), patternValue(arguments[1]), null)));
                case "between" -> new Cond(new Formula.Between(operand(arguments[0]), operand(arguments[1]), operand(arguments[2])));
                default -> throw unsupported("F." + name);
            };
        }

        private Sym symbolicCall(Insn insn, Sym receiver, Sym[] arguments, Type returnType) {
            String owner = insn.owner;
            String name = insn.name;

            if (receiver instanceof Expr expr && arguments.length == 0 && UNBOX_METHODS.contains(name) && BOX_OWNERS.contains(owner)) {
                return new Expr(expr.operand(), returnType);
            }
            if (receiver == null && name.equals("valueOf") && BOX_OWNERS.contains(owner) && arguments.length == 1 && arguments[0] instanceof Expr expr) {
                return new Expr(expr.operand(), returnType);
            }
            if (name.equals("equals") && receiver != null && insn.descriptor.equals("(Ljava/lang/Object;)Z")) {
                return new Cond(new Formula.Comparison(Operator.EQ, operand(receiver), operand(arguments[0])));
            }
            if (owner.equals("java/util/Objects") && receiver == null) {
                switch (name) {
                    case "equals":
                        return new Cond(new Formula.Comparison(Operator.EQ, operand(arguments[0]), operand(arguments[1])));
                    case "isNull":
                        return new Cond(new Formula.IsNull(operand(arguments[0])));
                    case "nonNull":
                        return new Cond(Formula.not(new Formula.IsNull(operand(arguments[0]))));
                    default:
                        break;
                }
            }
            if (receiver != null && arguments.length == 1 && name.equals("compareTo") && returnType.getSort() == Type.INT) {
                return new Cmp(operand(receiver), operand(arguments[0]));
            }
            if (receiver != null && arguments.length == 1 && returnType.getSort() == Type.BOOLEAN) {
                Operator operator = switch (name) {
                    case "isAfter" -> Operator.GT;
                    case "isBefore" -> Operator.LT;
                    case "isEqual" -> Operator.EQ;
                    default -> null;
                };
                if (operator != null) {
                    return new Cond(new Formula.Comparison(operator, operand(receiver), operand(arguments[0])));
                }
            }
            if (owner.equals("java/math/BigDecimal") && receiver != null) {
                Function function = switch (name) {
                    case "add" -> Function.SUM;
                    case "subtract" -> Function.DIFF;
                    case "multiply" -> Function.PROD;
                    case "divide" -> Function.QUOT;
                    default -> null;
                };
                if (function != null && arguments.length == 1) {
                    return new Expr(new Call(function, operand(receiver), operand(arguments[0])), returnType);
                }
                if (name.equals("negate") && arguments.length == 0) {
                    return new Expr(new Call(Function.NEG, operand(receiver)), returnType);
                }
            }
            if (owner.equals("java/lang/String") && receiver instanceof Expr expr) {
                Operand string = expr.operand();
                switch (name) {
                    case "startsWith":
                        if (arguments.length == 1) {
                            return new Cond(new Formula.Like(string, escapeLike(stringValue(arguments[0])) + "%", '\\'));
                        }
                        break;
                    case "endsWith":
                        return new Cond(new Formula.Like(string, "%" + escapeLike(stringValue(arguments[0])), '\\'));
                    case "contains":
                        return new Cond(new Formula.Like(string, "%" + escapeLike(stringValue(arguments[0])) + "%", '\\'));
                    case "equalsIgnoreCase":
                        return new Cond(new Formula.Comparison(Operator.EQ, new Call(Function.LOWER, string), lower(arguments[0])));
                    case "isEmpty":
                        return new Cond(new Formula.Comparison(Operator.EQ, new Call(Function.LENGTH, string), new Value(0)));
                    case "isBlank":
                        return new Cond(new Formula.Comparison(Operator.EQ, new Call(Function.LENGTH, new Call(Function.TRIM, string)), new Value(0)));
                    case "toLowerCase":
                        if (arguments.length == 0) {
                            return new Expr(new Call(Function.LOWER, string), STRING);
                        }
                        break;
                    case "toUpperCase":
                        if (arguments.length == 0) {
                            return new Expr(new Call(Function.UPPER, string), STRING);
                        }
                        break;
                    case "trim":
                    case "strip":
                        return new Expr(new Call(Function.TRIM, string), STRING);
                    case "length":
                        return new Expr(new Call(Function.LENGTH, string), Type.INT_TYPE);
                    default:
                        break;
                }
            }
            if (isCollection(owner)) {
                if (receiver instanceof Expr expr) {
                    switch (name) {
                        case "isEmpty":
                            return new Cond(new Formula.IsEmpty(expr.operand()));
                        case "size":
                            return new Expr(new Call(Function.SIZE, expr.operand()), Type.INT_TYPE);
                        case "contains":
                            return new Cond(new Formula.IsMember(concrete(arguments[0], "contains with a model argument on a model collection").value(), expr.operand()));
                        default:
                            break;
                    }
                } else if (receiver instanceof Val val && name.equals("contains") && arguments[0] instanceof Expr expr) {
                    if (val.value() == UNKNOWN) {
                        return new Cond(new Formula.In(expr.operand(), List.of()));
                    }
                    if (val.value() == null) {
                        throw unsupported("contains is called on a null collection");
                    }
                    return new Cond(new Formula.In(expr.operand(), (Collection<?>) val.value()));
                }
            }
            throw unsupported("call to " + owner.replace('/', '.') + "." + name + " with model values");
        }

        private boolean isCollection(String owner) {
            if (COLLECTION_OWNERS.contains(owner)) {
                return true;
            }
            try {
                return Collection.class.isAssignableFrom(Class.forName(owner.replace('/', '.'), false, classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }

        private Operand lower(Sym value) {
            if (value instanceof Val val) {
                return new Value(val.value() == null ? null : val.value().toString().toLowerCase(Locale.ROOT));
            }
            return new Call(Function.LOWER, operand(value));
        }

        private String stringValue(Sym value) {
            Val val = concrete(value, "string methods with a model argument");
            if (val.value() == UNKNOWN) {
                return "unknown";
            }
            if (val.value() == null) {
                throw unsupported("string method called with null");
            }
            return val.value().toString();
        }

        private String patternValue(Sym value) {
            return stringValue(value);
        }

        private void construct(Insn insn, Frame frame, Uninit instance, Sym[] arguments, Type[] argumentTypes) {
            Object[] values = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                values[i] = toJava(concrete(arguments[i], "constructors with model arguments").value(), argumentTypes[i]);
            }
            Object created;
            try {
                Constructor<?> constructor = null;
                for (Constructor<?> candidate : loadClass(insn.owner).getDeclaredConstructors()) {
                    if (Type.getConstructorDescriptor(candidate).equals(insn.descriptor)) {
                        constructor = candidate;
                    }
                }
                if (constructor == null) {
                    throw unsupported("constructor of " + insn.owner.replace('/', '.') + " not found");
                }
                constructor.trySetAccessible();
                created = constructor.newInstance(values);
            } catch (InvocationTargetException e) {
                throw unsupported("new " + insn.owner.replace('/', '.') + " threw " + e.getCause(), e.getCause());
            } catch (ReflectiveOperationException e) {
                throw unsupported("new " + insn.owner.replace('/', '.') + " could not be evaluated", e);
            }
            Val value = new Val(created, Type.getObjectType(insn.owner));
            frame.stack.replaceAll(sym -> sym.equals(instance) ? value : sym);
            for (int i = 0; i < frame.locals.length; i++) {
                if (instance.equals(frame.locals[i])) {
                    frame.locals[i] = value;
                }
            }
        }

        private Object call(Insn insn, Val receiver, Sym[] arguments, Type[] argumentTypes) {
            if (receiver != null && receiver.value() == null) {
                throw unsupported(insn.name + "() is called on a null value");
            }
            Object[] values = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                values[i] = toJava(((Val) arguments[i]).value(), argumentTypes[i]);
            }
            Method method = findMethod(loadClass(insn.owner), insn.name, insn.descriptor);
            if (method == null) {
                throw unsupported("method " + insn.owner.replace('/', '.') + "." + insn.name + " not found");
            }
            try {
                if (!method.canAccess(java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? null : receiver.value())) {
                    method.trySetAccessible();
                }
                return method.invoke(receiver == null ? null : receiver.value(), values);
            } catch (InvocationTargetException e) {
                throw unsupported(insn.owner.replace('/', '.') + "." + insn.name + " threw " + e.getCause(), e.getCause());
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                throw unsupported(insn.owner.replace('/', '.') + "." + insn.name + " could not be evaluated", e);
            }
        }

        private void invokeDynamic(Insn insn, Frame frame) {
            if (!insn.bootstrap.getOwner().equals("java/lang/invoke/StringConcatFactory")) {
                throw unsupported("nested lambdas and method references");
            }
            Type[] argumentTypes = Type.getArgumentTypes(insn.descriptor);
            Sym[] arguments = new Sym[argumentTypes.length];
            for (int i = arguments.length - 1; i >= 0; i--) {
                arguments[i] = frame.pop();
            }
            String recipe = insn.bootstrap.getName().equals("makeConcatWithConstants")
                    ? (String) insn.bootstrapArguments[0]
                    : "\u0001".repeat(arguments.length);
            int argument = 0;
            int constant = 1;
            List<Operand> parts = new ArrayList<>();
            StringBuilder literal = new StringBuilder();
            for (char c : recipe.toCharArray()) {
                if (c == '\u0001') {
                    Sym value = arguments[argument];
                    if (value instanceof Val val) {
                        literal.append(val.value() == UNKNOWN ? UNKNOWN : toJava(val.value(), argumentTypes[argument]));
                    } else {
                        if (!literal.isEmpty()) {
                            parts.add(new Value(literal.toString()));
                            literal.setLength(0);
                        }
                        parts.add(operand(value));
                    }
                    argument++;
                } else if (c == '\u0002') {
                    literal.append(insn.bootstrapArguments[constant++]);
                } else {
                    literal.append(c);
                }
            }
            if (parts.isEmpty()) {
                frame.push(new Val(literal.toString(), STRING));
                return;
            }
            if (!literal.isEmpty()) {
                parts.add(new Value(literal.toString()));
            }
            frame.push(new Expr(new Call(Function.CONCAT, parts), STRING));
        }

        private Operand operand(Sym value) {
            if (value instanceof Val val) {
                return new Value(val.value());
            }
            if (value instanceof Expr expr) {
                return expr.operand();
            }
            throw unsupported("conditions can not be used as values");
        }

        private Val concrete(Sym value, String what) {
            if (value instanceof Val val) {
                return val;
            }
            throw unsupported(what);
        }

        private Class<?> loadClass(String internalName) {
            try {
                return Class.forName(internalName.replace('/', '.'), true, classLoader);
            } catch (ClassNotFoundException e) {
                throw unsupported("class " + internalName.replace('/', '.') + " not found", e);
            }
        }

        private Object readField(Class<?> type, String name, Object target) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.trySetAccessible();
                    return field.get(target);
                } catch (NoSuchFieldException e) {
                    // continue with the superclass
                } catch (IllegalAccessException e) {
                    throw unsupported("field " + name + " is not accessible", e);
                }
            }
            for (Class<?> interfaceType : type.getInterfaces()) {
                try {
                    return interfaceType.getField(name).get(target);
                } catch (ReflectiveOperationException e) {
                    // not declared here
                }
            }
            throw unsupported("field " + type.getName() + "." + name + " not found");
        }

        private QueryForgeUnsupportedExpressionException unsupported(String what) {
            return new QueryForgeUnsupportedExpressionException(message(what));
        }

        private QueryForgeUnsupportedExpressionException unsupported(String what, Throwable cause) {
            return new QueryForgeUnsupportedExpressionException(message(what), cause);
        }

        private String message(String what) {
            String location = code.className + (current != null && current.line > 0 ? " line " + current.line : "");
            return "Unsupported where lambda in " + location + ": " + what;
        }
    }

    // ------------------------------------------------------------------ value helpers

    private static Method findMethod(Class<?> type, String name, String descriptor) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                return method;
            }
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean isBoolean(Type type) {
        return type.getSort() == Type.BOOLEAN || type.equals(BOOLEAN_OBJECT);
    }

    private static Formula constant(boolean value) {
        return value ? Formula.TRUE : Formula.FALSE;
    }

    private static boolean truthy(Val value) {
        return value.value() instanceof Boolean b ? b : intValue(value) != 0;
    }

    private static int intValue(Val value) {
        Object v = value.value();
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Character c) {
            return c;
        }
        return ((Number) v).intValue();
    }

    /** JVM stack representation of a Java value of the given type. */
    private static Object normalize(Object value, Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN -> value instanceof Boolean b ? (b ? 1 : 0) : value;
            case Type.CHAR -> value instanceof Character c ? (int) c : value;
            case Type.BYTE, Type.SHORT -> value instanceof Number n ? n.intValue() : value;
            default -> value;
        };
    }

    /** Java value of the given type from its JVM stack representation. */
    private static Object toJava(Object value, Type type) {
        if (value == null) {
            return null;
        }
        return switch (type.getSort()) {
            case Type.BOOLEAN -> value instanceof Number n ? n.intValue() != 0 : value;
            case Type.CHAR -> value instanceof Number n ? (char) n.intValue() : value;
            case Type.BYTE -> ((Number) value).byteValue();
            case Type.SHORT -> ((Number) value).shortValue();
            case Type.INT -> value instanceof Character c ? (int) c : ((Number) value).intValue();
            case Type.LONG -> ((Number) value).longValue();
            case Type.FLOAT -> ((Number) value).floatValue();
            case Type.DOUBLE -> ((Number) value).doubleValue();
            default -> value;
        };
    }

    private static Number negate(Number value) {
        if (value instanceof Integer i) {
            return -i;
        }
        if (value instanceof Long l) {
            return -l;
        }
        if (value instanceof Float f) {
            return -f;
        }
        return -value.doubleValue();
    }

    private static int compareNumbers(Number left, Number right) {
        if (left instanceof Long l && right instanceof Long r) {
            return Long.compare(l, r);
        }
        return Double.compare(left.doubleValue(), right.doubleValue());
    }

    private static Number compute(int opcode, Number left, Number right) {
        return switch (opcode) {
            case IADD -> left.intValue() + right.intValue();
            case LADD -> left.longValue() + right.longValue();
            case FADD -> left.floatValue() + right.floatValue();
            case DADD -> left.doubleValue() + right.doubleValue();
            case ISUB -> left.intValue() - right.intValue();
            case LSUB -> left.longValue() - right.longValue();
            case FSUB -> left.floatValue() - right.floatValue();
            case DSUB -> left.doubleValue() - right.doubleValue();
            case IMUL -> left.intValue() * right.intValue();
            case LMUL -> left.longValue() * right.longValue();
            case FMUL -> left.floatValue() * right.floatValue();
            case DMUL -> left.doubleValue() * right.doubleValue();
            case IDIV -> left.intValue() / right.intValue();
            case LDIV -> left.longValue() / right.longValue();
            case FDIV -> left.floatValue() / right.floatValue();
            case DDIV -> left.doubleValue() / right.doubleValue();
            case IREM -> left.intValue() % right.intValue();
            case LREM -> left.longValue() % right.longValue();
            case FREM -> left.floatValue() % right.floatValue();
            case DREM -> left.doubleValue() % right.doubleValue();
            case ISHL -> left.intValue() << right.intValue();
            case LSHL -> left.longValue() << right.intValue();
            case ISHR -> left.intValue() >> right.intValue();
            case LSHR -> left.longValue() >> right.intValue();
            case IUSHR -> left.intValue() >>> right.intValue();
            case LUSHR -> left.longValue() >>> right.intValue();
            case IAND -> left.intValue() & right.intValue();
            case LAND -> left.longValue() & right.longValue();
            case IOR -> left.intValue() | right.intValue();
            case LOR -> left.longValue() | right.longValue();
            case IXOR -> left.intValue() ^ right.intValue();
            default -> left.longValue() ^ right.longValue();
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

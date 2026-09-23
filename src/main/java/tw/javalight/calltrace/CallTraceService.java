package tw.javalight.calltrace;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 只追蹤索引來源檔；無法唯一確認的呼叫會保留在結果中並標示原因。 */
public final class CallTraceService {
    private final Map<String, List<MethodInfo>> methodsByClass = new HashMap<>();
    private final Map<String, Set<String>> classesBySimpleName = new HashMap<>();
    private final Map<String, Path> classSources = new HashMap<>();

    public void index(Collection<Path> sourceFiles) throws IOException {
        methodsByClass.clear();
        classesBySimpleName.clear();
        classSources.clear();
        Set<Path> visitedFiles = new HashSet<>();
        for (Path sourceFile : sourceFiles) {
            Path normalizedFile = sourceFile.toAbsolutePath().normalize();
            if (!visitedFiles.add(normalizedFile)) continue;

            CompilationUnit unit = StaticJavaParser.parse(normalizedFile);
            String packageName = unit.getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString()).orElse("");
            Map<String, String> imports = explicitImports(unit);
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                String simpleName = type.getNameAsString();
                String qualifiedName = type.getFullyQualifiedName()
                        .orElse(packageName.isEmpty() ? simpleName : packageName + "." + simpleName);
                Path previousFile = classSources.putIfAbsent(qualifiedName, sourceFile);
                if (previousFile != null) {
                    throw new IllegalArgumentException("重複的類別 " + qualifiedName + "："
                            + previousFile + "、" + sourceFile);
                }
                classesBySimpleName.computeIfAbsent(simpleName, ignored -> new HashSet<>())
                        .add(qualifiedName);
                for (MethodDeclaration method : type.getMethods()) {
                    MethodInfo info = new MethodInfo(qualifiedName, simpleName, packageName,
                            imports, method, sourceFile);
                    methodsByClass.computeIfAbsent(qualifiedName, ignored -> new ArrayList<>()).add(info);
                }
            }
        }
    }

    public String trace(String entryClass, String entryMethod) {
        MethodInfo entry = findEntry(entryClass, entryMethod);
        StringBuilder output = new StringBuilder();
        trace(entry, "1", new HashSet<>(), output);
        return output.toString();
    }

    private MethodInfo findEntry(String className, String methodSelection) {
        String qualifiedClass = className;
        if (className.startsWith(".")) {
            qualifiedClass = className.substring(1);
            if (qualifiedClass.contains(".") || !classSources.containsKey(qualifiedClass)) {
                throw new IllegalArgumentException("找不到預設 package 的入口類別：" + className);
            }
        } else if (!className.contains(".")) {
            Set<String> matches = classesBySimpleName.getOrDefault(className, Set.of());
            if (matches.size() > 1) {
                throw new IllegalArgumentException("入口類別名稱不唯一，請使用完整 package 名稱："
                        + matches.stream().sorted().collect(Collectors.joining("、")));
            }
            if (matches.isEmpty()) throw new IllegalArgumentException("找不到入口類別：" + className);
            qualifiedClass = matches.iterator().next();
        } else if (!classSources.containsKey(className)) {
            throw new IllegalArgumentException("找不到入口類別：" + className);
        }

        boolean signatureProvided = methodSelection.contains("(") && methodSelection.endsWith(")");
        String requested = methodSelection.replaceAll("\\s+", "");
        List<MethodInfo> matches = methodsByClass.getOrDefault(qualifiedClass, List.of()).stream()
                .filter(method -> signatureProvided
                        ? method.signature().replaceAll("\\s+", "").equals(requested)
                        : method.methodName().equals(methodSelection))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("找不到入口 method：" + qualifiedClass + "." + methodSelection);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("入口 method 有多個多載，請指定參數型別，例如 --entry-method "
                    + matches.get(0).signature() + "；可選：" + matches.stream()
                    .map(MethodInfo::signature).collect(Collectors.joining("、")));
        }
        return matches.get(0);
    }

    private void trace(MethodInfo method, String order, Set<MethodInfo> activePath, StringBuilder output) {
        appendMethod(output, order, method, false);
        Set<MethodInfo> nextActivePath = new HashSet<>(activePath);
        nextActivePath.add(method);

        int childNumber = 1;
        for (MethodCallExpr call : callsInSourceOrder(method)) {
            String childOrder = order + "." + childNumber++;
            Resolution resolution = resolve(method, call);
            if (resolution.target == null) {
                appendUnresolved(output, childOrder, method, call, resolution.reason);
            } else if (nextActivePath.contains(resolution.target)) {
                appendMethod(output, childOrder, resolution.target, true);
            } else {
                trace(resolution.target, childOrder, nextActivePath, output);
            }
        }
    }

    private void appendMethod(StringBuilder output, String order, MethodInfo method, boolean cycle) {
        appendIndent(output, order);
        boolean showPackage = classesBySimpleName.getOrDefault(method.simpleClassName(), Set.of()).size() > 1;
        output.append(order).append("  ").append(method.displayName(showPackage))
                .append("  L").append(method.line());
        if (cycle) output.append("  [循環，停止展開]");
        output.append(System.lineSeparator());
    }

    private void appendUnresolved(StringBuilder output, String order, MethodInfo caller,
                                  MethodCallExpr call, String reason) {
        appendIndent(output, order);
        output.append(order).append("  ").append(call)
                .append("  @ ").append(caller.sourceFile()).append(":L")
                .append(call.getBegin().map(position -> position.line).orElse(-1))
                .append("  [未解析：").append(reason).append("]")
                .append(System.lineSeparator());
    }

    private void appendIndent(StringBuilder output, String order) {
        int depth = (int) order.chars().filter(character -> character == '.').count();
        output.append("  ".repeat(depth));
    }

    private List<MethodCallExpr> callsInSourceOrder(MethodInfo method) {
        List<MethodCallExpr> calls = method.declaration().findAll(MethodCallExpr.class);
        calls.sort(Comparator.comparingInt((MethodCallExpr call) -> call.getBegin()
                        .map(position -> position.line).orElse(Integer.MAX_VALUE))
                .thenComparingInt(call -> call.getBegin()
                        .map(position -> position.column).orElse(Integer.MAX_VALUE)));
        return calls;
    }

    private Resolution resolve(MethodInfo caller, MethodCallExpr call) {
        String targetClass = targetClass(caller, call);
        if (targetClass == null) {
            return Resolution.unresolved("無法確認接收者類別（例如變數、繼承或動態派送）");
        }
        if (!classSources.containsKey(targetClass)) {
            return Resolution.unresolved("分析範圍未包含類別 " + targetClass);
        }

        List<MethodInfo> candidates = methodsByClass.getOrDefault(targetClass, List.of()).stream()
                .filter(method -> method.methodName().equals(call.getNameAsString()))
                .filter(method -> method.acceptsArity(call.getArguments().size()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return Resolution.unresolved("找不到 " + targetClass + "." + call.getNameAsString()
                    + " 的 " + call.getArguments().size() + " 個參數版本（可能為繼承或外部 method）");
        }
        if (candidates.size() > 1) {
            return Resolution.unresolved("多載目標不唯一：" + candidates.stream()
                    .map(method -> method.qualifiedClassName() + "." + method.signature())
                    .collect(Collectors.joining("、")));
        }
        MethodInfo target = candidates.get(0);
        if (target.declaration().getBody().isEmpty()) {
            return Resolution.unresolved("目標 method 無實作，無法確認執行時版本");
        }
        return Resolution.resolved(target);
    }

    private String targetClass(MethodInfo caller, MethodCallExpr call) {
        if (call.getScope().isEmpty()) return caller.qualifiedClassName();
        Expression scope = call.getScope().get();
        if (scope.isThisExpr() && "this".equals(scope.toString())) return caller.qualifiedClassName();
        if (scope.isSuperExpr()) return null;

        String scopeName = scope.toString();
        if (scope.isNameExpr()) {
            if (isVariableName(caller, scopeName)) return null;
            String samePackageClass = caller.packageName().isEmpty()
                    ? scopeName : caller.packageName() + "." + scopeName;
            if (classSources.containsKey(samePackageClass)) return samePackageClass;
            String importedClass = caller.imports().get(scopeName);
            return importedClass != null ? importedClass : samePackageClass;
        }
        return classSources.containsKey(scopeName) ? scopeName : null;
    }

    private boolean isVariableName(MethodInfo caller, String name) {
        if (caller.declaration().getParameters().stream()
                .anyMatch(parameter -> parameter.getNameAsString().equals(name))) return true;
        boolean fieldWithName = caller.declaration().getParentNode()
                .filter(ClassOrInterfaceDeclaration.class::isInstance)
                .map(ClassOrInterfaceDeclaration.class::cast)
                .map(type -> type.getFields().stream()
                        .flatMap(field -> field.getVariables().stream())
                        .anyMatch(variable -> variable.getNameAsString().equals(name)))
                .orElse(false);
        if (fieldWithName) return true;
        return caller.declaration().findAll(VariableDeclarator.class).stream()
                .anyMatch(variable -> variable.getNameAsString().equals(name));
    }

    private Map<String, String> explicitImports(CompilationUnit unit) {
        Map<String, String> imports = new HashMap<>();
        for (ImportDeclaration imported : unit.getImports()) {
            if (imported.isAsterisk() || imported.isStatic()) continue;
            String qualifiedName = imported.getNameAsString();
            imports.put(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1), qualifiedName);
        }
        return imports;
    }

    private static final class Resolution {
        private final MethodInfo target;
        private final String reason;

        private Resolution(MethodInfo target, String reason) {
            this.target = target;
            this.reason = reason;
        }

        private static Resolution resolved(MethodInfo target) { return new Resolution(target, null); }
        private static Resolution unresolved(String reason) { return new Resolution(null, reason); }
    }
}

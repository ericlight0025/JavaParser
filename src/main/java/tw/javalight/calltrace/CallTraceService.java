package tw.javalight.calltrace;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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
import java.util.Optional;
import java.util.Set;

/**
 * 最短 MVP 的靜態呼叫追蹤器。它只索引使用者提供的來源檔，不使用 Symbol Solver 或 JDT。
 */
public final class CallTraceService {
    private final Map<String, List<MethodInfo>> methodsByName = new HashMap<>();
    private final Map<String, List<MethodInfo>> methodsByClass = new HashMap<>();

    public void index(Collection<Path> sourceFiles) throws IOException {
        methodsByName.clear();
        methodsByClass.clear();
        for (Path sourceFile : sourceFiles) {
            CompilationUnit unit = StaticJavaParser.parse(sourceFile);
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                String className = type.getNameAsString();
                for (MethodDeclaration method : type.getMethods()) {
                    MethodInfo info = new MethodInfo(className, method, sourceFile);
                    methodsByName.computeIfAbsent(info.methodName(), ignored -> new ArrayList<>()).add(info);
                    methodsByClass.computeIfAbsent(className, ignored -> new ArrayList<>()).add(info);
                }
            }
        }
    }

    public String trace(String entryClass, String entryMethod) {
        MethodInfo entry = findEntry(entryClass, entryMethod)
                .orElseThrow(() -> new IllegalArgumentException(
                        "找不到入口 method：" + entryClass + "." + entryMethod + "()"));
        StringBuilder output = new StringBuilder();
        trace(entry, "1", new HashSet<>(), output);
        return output.toString();
    }

    private Optional<MethodInfo> findEntry(String className, String methodName) {
        return methodsByClass.getOrDefault(className, List.of()).stream()
                .filter(method -> method.methodName().equals(methodName))
                .findFirst();
    }

    private void trace(MethodInfo method, String order, Set<MethodInfo> activePath, StringBuilder output) {
        append(output, order, method, false);
        Set<MethodInfo> nextActivePath = new HashSet<>(activePath);
        nextActivePath.add(method);

        int childNumber = 1;
        for (MethodCallExpr call : callsInSourceOrder(method)) {
            for (MethodInfo target : resolve(method, call)) {
                String childOrder = order + "." + childNumber++;
                if (nextActivePath.contains(target)) {
                    append(output, childOrder, target, true);
                } else {
                    trace(target, childOrder, nextActivePath, output);
                }
            }
        }
    }

    private void append(StringBuilder output, String order, MethodInfo method, boolean cycle) {
        output.append(order)
                .append("  ")
                .append(method.displayName())
                .append("  L")
                .append(method.line());
        if (cycle) {
            output.append("  [循環，停止展開]");
        }
        output.append(System.lineSeparator());
    }

    private List<MethodCallExpr> callsInSourceOrder(MethodInfo method) {
        List<MethodCallExpr> calls = method.declaration().findAll(MethodCallExpr.class);
        calls.sort(Comparator.comparingInt(call -> call.getBegin()
                .map(position -> position.line)
                .orElse(Integer.MAX_VALUE)));
        return calls;
    }

    private List<MethodInfo> resolve(MethodInfo caller, MethodCallExpr call) {
        List<MethodInfo> candidates = new ArrayList<>();
        if (call.getScope().isPresent()) {
            String scopeName = simpleScopeName(call.getScope().get().toString());
            for (MethodInfo target : methodsByClass.getOrDefault(scopeName, List.of())) {
                if (target.methodName().equals(call.getNameAsString())) candidates.add(target);
            }
        } else {
            for (MethodInfo target : methodsByClass.getOrDefault(caller.className(), List.of())) {
                if (target.methodName().equals(call.getNameAsString())) candidates.add(target);
            }
            if (candidates.isEmpty()) {
                candidates.addAll(methodsByName.getOrDefault(call.getNameAsString(), List.of()));
            }
        }
        candidates.sort(Comparator.comparing(MethodInfo::className)
                .thenComparingInt(MethodInfo::line));
        return candidates;
    }

    private String simpleScopeName(String scope) {
        String cleaned = scope.replace("()", "");
        int lastDot = cleaned.lastIndexOf('.');
        return lastDot >= 0 ? cleaned.substring(lastDot + 1) : cleaned;
    }
}

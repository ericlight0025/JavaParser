package tw.javalight.calltrace;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/** 單一 method 的完整類別名稱、宣告參數與來源資訊。 */
final class MethodInfo {
    private final String qualifiedClassName;
    private final String simpleClassName;
    private final String packageName;
    private final Map<String, String> imports;
    private final Path sourceFile;
    private final MethodDeclaration declaration;

    MethodInfo(String qualifiedClassName, String simpleClassName, String packageName,
               Map<String, String> imports, MethodDeclaration declaration, Path sourceFile) {
        this.qualifiedClassName = qualifiedClassName;
        this.simpleClassName = simpleClassName;
        this.packageName = packageName;
        this.imports = Map.copyOf(imports);
        this.sourceFile = sourceFile;
        this.declaration = declaration;
    }

    String qualifiedClassName() { return qualifiedClassName; }
    String simpleClassName() { return simpleClassName; }
    String packageName() { return packageName; }
    Map<String, String> imports() { return imports; }
    String methodName() { return declaration.getNameAsString(); }
    int line() { return declaration.getBegin().map(position -> position.line).orElse(-1); }
    Path sourceFile() { return sourceFile; }
    MethodDeclaration declaration() { return declaration; }

    String signature() {
        return methodName() + "(" + declaration.getParameters().stream()
                .map(this::parameterType)
                .collect(Collectors.joining(",")) + ")";
    }

    String displayName(boolean showPackage) {
        return (showPackage ? qualifiedClassName : simpleClassName) + "." + signature();
    }

    boolean acceptsArity(int argumentCount) {
        int parameterCount = declaration.getParameters().size();
        boolean variableArguments = parameterCount > 0
                && declaration.getParameter(parameterCount - 1).isVarArgs();
        return variableArguments ? argumentCount >= parameterCount - 1 : argumentCount == parameterCount;
    }

    private String parameterType(Parameter parameter) {
        return parameter.getType().asString() + (parameter.isVarArgs() ? "..." : "");
    }
}

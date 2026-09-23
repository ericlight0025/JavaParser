package tw.javalight.calltrace;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.nio.file.Path;
import java.util.Objects;

/** 單一可追蹤的 method 定義及其來源資訊。 */
final class MethodInfo {
    private final String className;
    private final String methodName;
    private final int line;
    private final Path sourceFile;
    private final MethodDeclaration declaration;

    MethodInfo(String className, MethodDeclaration declaration, Path sourceFile) {
        this.className = className;
        this.methodName = declaration.getNameAsString();
        this.line = declaration.getBegin().map(position -> position.line).orElse(-1);
        this.sourceFile = sourceFile;
        this.declaration = declaration;
    }

    String className() { return className; }
    String methodName() { return methodName; }
    int line() { return line; }
    Path sourceFile() { return sourceFile; }
    MethodDeclaration declaration() { return declaration; }
    String displayName() { return className + "." + methodName + "()"; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MethodInfo)) return false;
        MethodInfo that = (MethodInfo) other;
        return declaration == that.declaration;
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(declaration));
    }
}

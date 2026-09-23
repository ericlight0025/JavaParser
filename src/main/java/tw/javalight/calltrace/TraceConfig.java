package tw.javalight.calltrace;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** YAML 設定及相對路徑解析。 */
final class TraceConfig {
    private static final Set<String> ALLOWED_KEYS = Set.of("sources", "entryClass", "entryMethod");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".gradle", ".idea", "build", "node_modules", "out", "target");

    private final List<Path> sourceFiles;
    private final String entryClass;
    private final String entryMethod;

    TraceConfig(List<Path> sourceFiles, String entryClass, String entryMethod) {
        this.sourceFiles = List.copyOf(sourceFiles);
        this.entryClass = entryClass;
        this.entryMethod = entryMethod;
    }

    List<Path> sourceFiles() { return sourceFiles; }
    String entryClass() { return entryClass; }
    String entryMethod() { return entryMethod; }

    static TraceConfig load(Path configFile) throws IOException {
        Path normalizedConfig = configFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedConfig)) {
            throw new IllegalArgumentException("找不到 YAML 設定檔：" + normalizedConfig);
        }

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(1_000_000);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object document;
        try (Reader reader = Files.newBufferedReader(normalizedConfig, StandardCharsets.UTF_8)) {
            document = yaml.load(reader);
        } catch (YAMLException exception) {
            throw new IllegalArgumentException("YAML 格式錯誤：" + normalizedConfig + "："
                    + exception.getMessage(), exception);
        }
        if (!(document instanceof Map)) {
            throw new IllegalArgumentException("YAML 最外層必須是物件：" + normalizedConfig);
        }

        Map<?, ?> values = (Map<?, ?>) document;
        for (Object key : values.keySet()) {
            if (!(key instanceof String) || !ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("YAML 不支援的欄位：" + key);
            }
        }
        Object sourcesValue = values.get("sources");
        if (values.containsKey("sources") && !(sourcesValue instanceof List)) {
            throw new IllegalArgumentException("YAML 的 sources 必須是檔案清單；省略此欄位可自動掃描");
        }

        List<Path> sourceFiles = new ArrayList<>();
        if (sourcesValue instanceof List && !((List<?>) sourcesValue).isEmpty()) {
            for (Object sourceValue : (List<?>) sourcesValue) {
                String sourceText = requiredText(sourceValue, "sources 項目");
                Path source = Path.of(sourceText);
                Path resolved = (source.isAbsolute() ? source : normalizedConfig.getParent().resolve(source))
                        .normalize();
                if (!Files.isRegularFile(resolved)) {
                    throw new IllegalArgumentException("YAML 指定的 Java 檔不存在：" + resolved);
                }
                sourceFiles.add(resolved);
            }
        } else {
            Path projectRoot = findProjectRoot(normalizedConfig.getParent());
            sourceFiles.addAll(discoverJavaSources(projectRoot));
        }

        return new TraceConfig(sourceFiles,
                requiredText(values.get("entryClass"), "entryClass"),
                requiredText(values.get("entryMethod"), "entryMethod"));
    }

    private static Path findProjectRoot(Path configDirectory) {
        for (Path directory = configDirectory; directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("pom.xml"))
                    || Files.exists(directory.resolve(".git"))
                    || Files.isRegularFile(directory.resolve("build.gradle"))
                    || Files.isRegularFile(directory.resolve("build.gradle.kts"))) {
                return directory;
            }
        }
        if (configDirectory.getFileName() != null
                && "config".equalsIgnoreCase(configDirectory.getFileName().toString())
                && configDirectory.getParent() != null) {
            return configDirectory.getParent();
        }
        return configDirectory;
    }

    private static List<Path> discoverJavaSources(Path projectRoot) throws IOException {
        List<Path> discovered = new ArrayList<>();
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(projectRoot)
                        && SKIPPED_DIRECTORIES.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (isTestSourceDirectory(projectRoot, directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    discovered.add(file.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        discovered.sort(Comparator.comparing(Path::toString));
        if (discovered.isEmpty()) {
            throw new IllegalArgumentException("在專案目錄找不到可分析的 Java 原始碼：" + projectRoot);
        }
        return discovered;
    }

    private static boolean isTestSourceDirectory(Path projectRoot, Path directory) {
        Path relative = projectRoot.relativize(directory);
        if (relative.toString().isEmpty()) return false;
        for (int index = 0; index < relative.getNameCount(); index++) {
            String name = relative.getName(index).toString();
            if (index == 0 && Set.of("test", "tests").contains(name)) return true;
            if (index > 0 && "src".equals(relative.getName(index - 1).toString())
                    && Set.of("test", "integrationTest", "testFixtures").contains(name)) {
                return true;
            }
            if (Set.of("test", "tests").contains(name)
                    && index + 1 < relative.getNameCount()
                    && "java".equals(relative.getName(index + 1).toString())) {
                return true;
            }
        }
        return false;
    }

    private static String requiredText(Object value, String name) {
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new IllegalArgumentException("YAML 的 " + name + " 必須是非空文字");
        }
        return ((String) value).trim();
    }
}

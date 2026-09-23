package tw.javalight.calltrace;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** YAML 設定及相對路徑解析。 */
final class TraceConfig {
    private static final Set<String> ALLOWED_KEYS = Set.of("sources", "entryClass", "entryMethod");

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
        if (!(sourcesValue instanceof List) || ((List<?>) sourcesValue).isEmpty()) {
            throw new IllegalArgumentException("YAML 的 sources 必須是非空的檔案清單");
        }

        List<Path> sourceFiles = new ArrayList<>();
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

        return new TraceConfig(sourceFiles,
                requiredText(values.get("entryClass"), "entryClass"),
                requiredText(values.get("entryMethod"), "entryMethod"));
    }

    private static String requiredText(Object value, String name) {
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new IllegalArgumentException("YAML 的 " + name + " 必須是非空文字");
        }
        return ((String) value).trim();
    }
}

package tw.javalight.calltrace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesSourcesRelativeToYamlFile() throws IOException {
        Path configDirectory = Files.createDirectories(temporaryDirectory.resolve("settings"));
        Path sourceDirectory = Files.createDirectories(configDirectory.resolve("src"));
        Path first = Files.writeString(sourceDirectory.resolve("Entry.java"), "class Entry {}\n");
        Path second = Files.writeString(sourceDirectory.resolve("Service.java"), "class Service {}\n");
        Path configFile = Files.writeString(configDirectory.resolve("trace.yaml"),
                "sources:\n  - src/Entry.java\n  - src/Service.java\n"
                        + "entryClass: demo.Entry\nentryMethod: start(int)\n");

        TraceConfig config = TraceConfig.load(configFile);

        assertEquals(List.of(first, second), config.sourceFiles());
        assertEquals("demo.Entry", config.entryClass());
        assertEquals("start(int)", config.entryMethod());
    }

    @Test
    void rejectsUnknownDuplicateAndMissingValues() throws IOException {
        Files.writeString(temporaryDirectory.resolve("Entry.java"), "class Entry {}\n");
        Path configFile = temporaryDirectory.resolve("trace.yaml");

        Files.writeString(configFile, "sources:\n  - Entry.java\nentryClass: Entry\nentryMethod: start\nextra: true\n");
        assertThrows(IllegalArgumentException.class, () -> TraceConfig.load(configFile));

        Files.writeString(configFile, "sources:\n  - Entry.java\nentryClass: Entry\nentryClass: Other\nentryMethod: start\n");
        assertThrows(IllegalArgumentException.class, () -> TraceConfig.load(configFile));

        Files.writeString(configFile, "sources:\n  - Missing.java\nentryClass: Entry\nentryMethod: start\n");
        assertThrows(IllegalArgumentException.class, () -> TraceConfig.load(configFile));
    }
}

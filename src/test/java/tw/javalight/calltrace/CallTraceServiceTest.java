package tw.javalight.calltrace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallTraceServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void tracesCallsInSourceOrderAndStopsCycles() throws IOException {
        Path entry = write("Entry.java", "class Entry {\n"
                + "  static void start() {\n"
                + "    Entry.validate();\n"
                + "    Service.check();\n"
                + "    Log.write();\n"
                + "  }\n"
                + "  static void validate() { Service.normalize(); }\n"
                + "}\n");
        Path service = write("Service.java", "class Service {\n"
                + "  static void check() { Repository.query(); Service.notifyUser(); }\n"
                + "  static void normalize() { Repository.sanitize(); }\n"
                + "  static void notifyUser() { Log.audit(); Entry.start(); }\n"
                + "}\n");
        Path repository = write("Repository.java", "class Repository {\n"
                + "  static void query() { }\n"
                + "  static void sanitize() { }\n"
                + "}\n");
        Path log = write("Log.java", "class Log {\n"
                + "  static void write() { }\n"
                + "  static void audit() { }\n"
                + "}\n");

        CallTraceService tracer = new CallTraceService();
        tracer.index(List.of(entry, service, repository, log));

        assertEquals(String.join(System.lineSeparator(),
                "1  Entry.start()  L2",
                "  1.1  Entry.validate()  L7",
                "    1.1.1  Service.normalize()  L3",
                "      1.1.1.1  Repository.sanitize()  L3",
                "  1.2  Service.check()  L2",
                "    1.2.1  Repository.query()  L2",
                "    1.2.2  Service.notifyUser()  L4",
                "      1.2.2.1  Log.audit()  L3",
                "      1.2.2.2  Entry.start()  L2  [循環，停止展開]",
                "  1.3  Log.write()  L2",
                ""), tracer.trace("Entry", "start"));
    }

    private Path write(String name, String content) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}

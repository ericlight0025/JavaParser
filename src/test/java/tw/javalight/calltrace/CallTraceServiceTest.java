package tw.javalight.calltrace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void distinguishesPackagesAndReportsUnresolvedCalls() throws IOException {
        Path entry = write("a/Entry.java", "package a;\n"
                + "import b.Service;\n"
                + "class Entry { static void start() {\n"
                + "  Service.run(1);\n"
                + "  Missing.work();\n"
                + "  Service.run();\n"
                + "  b.Service.run();\n"
                + "  Service service = null;\n"
                + "  service.run();\n"
                + "} }\n");
        Path selected = write("b/Service.java", "package b;\n"
                + "class Service {\n"
                + "  static void run(int value) { }\n"
                + "  static void run(String value) { }\n"
                + "  static void run() { }\n"
                + "}\n");
        Path other = write("c/Service.java", "package c;\n"
                + "class Service { static void run() { } }\n");
        Path defaultPackage = write("Service.java", "class Service { static void run() { } }\n");

        CallTraceService tracer = new CallTraceService();
        tracer.index(List.of(entry, selected, other, defaultPackage));
        String output = tracer.trace("a.Entry", "start");

        assertTrue(output.contains("1.1  Service.run(1)  @ " + entry + ":L4  [未解析：多載目標不唯一"));
        assertTrue(output.contains("1.2  Missing.work()  @ " + entry + ":L5  [未解析：來源清單未包含類別 a.Missing]"));
        assertTrue(output.contains("1.3  b.Service.run()  L5"));
        assertTrue(output.contains("1.4  b.Service.run()  L5"));
        assertTrue(output.contains("1.5  service.run()  @ " + entry + ":L9  [未解析：無法確認接收者類別"));
        assertThrows(IllegalArgumentException.class, () -> tracer.trace("Service", "run"));
        assertThrows(IllegalArgumentException.class, () -> tracer.trace("b.Service", "run"));
        assertEquals("1  b.Service.run(int)  L3" + System.lineSeparator(),
                tracer.trace("b.Service", "run(int)"));
        assertEquals("1  Service.run()  L1" + System.lineSeparator(),
                tracer.trace(".Service", "run"));
    }

    private Path write(String name, String content) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}

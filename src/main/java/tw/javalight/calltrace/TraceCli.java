package tw.javalight.calltrace;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CLI 入口。 */
public final class TraceCli {
    private TraceCli() { }

    public static void main(String[] args) throws Exception {
        if (Arguments.hasHelp(args)) {
            printUtf8(Arguments.usage() + System.lineSeparator());
            return;
        }
        TraceConfig parsed;
        if (args.length > 0 && "--config".equals(args[0])) {
            if (args.length != 2) {
                throw new IllegalArgumentException("--config 只接受一個 YAML 檔案，不能與其他參數混用\n"
                        + Arguments.usage());
            }
            parsed = TraceConfig.load(Path.of(args[1]));
        } else {
            parsed = Arguments.parse(args).toConfig();
        }
        CallTraceService service = new CallTraceService();
        service.index(parsed.sourceFiles());
        printUtf8(service.trace(parsed.entryClass(), parsed.entryMethod()));
    }

    private static void printUtf8(String text) {
        // 明確輸出 UTF-8，避免 Windows 預設字碼頁讓中文狀態標記變亂碼。
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        output.print(text);
        output.flush();
    }

    private static final class Arguments {
        private final List<Path> sourceFiles = new ArrayList<>();
        private String entryClass;
        private String entryMethod;

        private static boolean hasHelp(String[] args) {
            for (String arg : args) {
                if ("--help".equals(arg) || "-h".equals(arg)) return true;
            }
            return false;
        }

        private static Arguments parse(String[] args) {
            Arguments result = new Arguments();
            for (int index = 0; index < args.length; index++) {
                String option = args[index];
                if (index + 1 >= args.length) throw new IllegalArgumentException("缺少 " + option + " 的值\n" + usage());
                String value = args[++index];
                switch (option) {
                    case "--source": result.sourceFiles.add(Path.of(value)); break;
                    case "--entry-class": result.entryClass = value; break;
                    case "--entry-method": result.entryMethod = value; break;
                    default: throw new IllegalArgumentException("不支援的選項：" + option + "\n" + usage());
                }
            }
            if (result.sourceFiles.isEmpty() || result.entryClass == null || result.entryMethod == null) {
                throw new IllegalArgumentException(usage());
            }
            return result;
        }

        private TraceConfig toConfig() {
            return new TraceConfig(sourceFiles, entryClass, entryMethod);
        }

        private static String usage() {
            return "用法：mvn exec:java \"-Dexec.args=--config <設定.yaml>\"\n"
                    + "選項：\n"
                    + "  --config <設定.yaml>    從 YAML 載入來源檔及入口；相對路徑以 YAML 所在目錄為基準\n"
                    + "  --source <Java檔>       要分析的 Java 檔案，可重複指定\n"
                    + "  --entry-class <類別名>  起點類別；同名時用完整 package 名稱\n"
                    + "  --entry-method <方法名> 起點 method；多載時用簽名，例如 start(int)\n"
                    + "  --help, -h              顯示此說明";
        }
    }
}

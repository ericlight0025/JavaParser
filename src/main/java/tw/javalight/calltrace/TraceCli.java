package tw.javalight.calltrace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CLI 入口。 */
public final class TraceCli {
    private TraceCli() { }

    public static void main(String[] args) throws Exception {
        if (Arguments.hasHelp(args)) {
            System.out.println(Arguments.usage());
            return;
        }
        Arguments parsed = Arguments.parse(args);
        CallTraceService service = new CallTraceService();
        service.index(parsed.sourceFiles);
        System.out.print(service.trace(parsed.entryClass, parsed.entryMethod));
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

        private static String usage() {
            return "用法：mvn exec:java \"-Dexec.args=--source <Java檔> [--source <Java檔> ...] --entry-class <類別名> --entry-method <method名>\"\n"
                    + "選項：\n"
                    + "  --source <Java檔>       要分析的 Java 檔案，可重複指定\n"
                    + "  --entry-class <類別名>  呼叫追蹤的起點類別\n"
                    + "  --entry-method <方法名> 呼叫追蹤的起點 method\n"
                    + "  --help, -h              顯示此說明";
        }
    }
}

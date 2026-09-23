# JavaParser Call Trace MVP

以 **JavaParser** 從指定入口 method 往下追蹤呼叫，輸出符合原始碼順序的階層清單與各 method 的定義行號。

這是刻意縮小的 Java 11 CLI MVP：不做 HTML、GUI、JDT、全 repository 掃描或 Spring DI 解析。

## 功能

- 可輸入多支 Java 原始碼檔（重複 `--source`）
- 指定入口類別與入口 method
- 以呼叫在原始碼中的位置排序
- 支援一個 method 內的多個呼叫，並依階層輸出
- 輸出 method 定義行號
- 偵測目前遞迴路徑中的循環並停止展開
- 無法在輸入檔中解析的呼叫（例如 JDK、外部函式庫）會略過

## 需求

- JDK 11+
- Maven 3.8+

## 執行範例

先跑測試：

```powershell
mvn test
```

再使用內附範例：

```powershell
mvn exec:java "-Dexec.args=--source examples/Entry.java --source examples/Service.java --source examples/Repository.java --source examples/Log.java --entry-class Entry --entry-method start"
```

輸出：

```text
1  Entry.start()  L2
1.1  Service.check()  L2
1.1.1  Repository.query()  L2
1.1.2  Entry.start()  L2  [循環，停止展開]
1.2  Log.write()  L2
```

## MVP 的解析規則與限制

這一版故意不使用 Symbol Solver，因此以可預期的簡單規則解析：

1. `ClassName.method()` 會對應輸入檔中同名類別的同名 method。
2. 無 scope 的 `method()` 先找同類別 method；若找不到，才查所有輸入檔中的同名 method。
3. 同名候選 method 都會輸出（依類別名、定義行排序），方便先驗證呼叫圖是否有用。
4. 變數呼叫、介面實作、多載參數、import、繼承與 DI 尚未精準解析；這是下一階段才考慮加入 JavaParser Symbol Solver 的範圍。

## 專案結構

```text
src/main/java/tw/javalight/calltrace/
  TraceCli.java           CLI 入口
  CallTraceService.java   解析、索引、追蹤
  MethodInfo.java         Method 資訊
src/test/.../CallTraceServiceTest.java
examples/                 可直接執行的四支範例 Java
```

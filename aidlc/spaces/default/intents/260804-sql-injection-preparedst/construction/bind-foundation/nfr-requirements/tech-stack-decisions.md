# Tech Stack Decisions — U1 `bind-foundation`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U1）— § 2〜§ 8 のアルゴリズムが使う言語機能と JDBC API。§ 10 の 2.6 契約からの差分（新規 public 型 `LikeEscape` / `ResultSetHandler<R>`）。本文書はこれらが Java 8 に収まることを全数で確認する。
- **`business-rules.md`**（同上）— BR-3（`isNumeric` の正規表現を逐語的に維持）、BR-16 / BR-17（JDBC setter と `java.sql.Types`）、BR-33〜BR-37（mock 拡張）。使用 API の出所。
- **`requirements.md`**（requirements-analysis 2.3）— CON-1 / NFR-4（Java 8 の維持）、CON-6（compile スコープの第三者依存）、NFR-5（`mvn test` が Failures = 0 / Errors = 0）、NFR-6（テスト件数 >= 138）、NFR-7 / OOS-2（最適化を持ち込まない）、**OQ-4**（本ステージで解決）。
- **`technology-stack.md`**（codekb）— 現行のビルド構成（Maven、compiler 3.8.1 の source/target 1.8、surefire 2.22.2）、compile スコープ依存が `tamacat-core` 1.5 と `javax.json` 1.1.4 のみであること、**カバレッジ計測ツールの不在**、ビルド JVM が JDK 25 Corretto であること、EasyMock 5.1.0 が宣言済みで未使用であること。

`security-requirements.md` は本文書と対になる成果物で、SEC-8（新規 compile 依存を追加しない）が本文書の TSD-1 に対応する。

---

## この文書の範囲

**U1 は技術選定をほとんど行わない。** tamacat-dao は既存のスタックが確定した brownfield ライブラリであり、本取り組みは「SQL の組み立て方を変える」ものであってスタックを選び直すものではない。したがって本文書の中身は次の 2 つである。

1. **変えないことの確認**（TSD-1、TSD-2、TSD-4、TSD-5）——何を変えないかを機械的に検査できる形で固定する
2. **唯一の追加**（TSD-3）——`requirements.md` OQ-4 の解として JaCoCo を入れる

---

## TSD-1. compile スコープの第三者依存を追加しない

| 項目 | 内容 |
|---|---|
| 決定 | U1 は compile スコープの第三者依存を 1 つも追加しない。`org.tamacat:tamacat-core:1.5`、`javax.json:javax.json-api:1.1.4`、`org.glassfish:javax.json:1.1.4` の 3 件という現状を維持する |
| 判定基準 | `mvn dependency:tree` の compile スコープ出力が変更前と一致する |
| 由来 | CON-6、`technology-stack.md`「Frameworks and Libraries」、SEC-8 |

U1 が新設する 8 クラスはすべて JDK 標準 API と既存の `tamacat-core`（`StringUtils`）だけで書ける。`decisions.md` ADR-009 が検証基盤を「既存 mock スタックの拡張」に決めたのも同じ理由による。

**test スコープも追加しない。** 宣言済みで未使用の EasyMock 5.1.0 を使う案は ADR-009 が却下している（TSD-4）。

---

## TSD-2. Java 8 を維持する

| 項目 | 内容 |
|---|---|
| 決定 | `maven.compiler.source` / `target` を `1.8` のまま維持し、U1 のコードは post-8 の言語機能・API を使用しない |
| 判定基準 | `pom.xml:17-18` と `pom.xml:101-102` が `1.8` のまま。`mvn -q compile` が成功する |
| 由来 | CON-1、NFR-4 |

### U1 が使う言語機能・API の全数確認

`business-logic-model.md` § 2〜§ 8 と `domain-entities.md` に現れる構成要素を漏れなく列挙し、最低要求バージョンを確かめる。

| 構成要素 | 使用箇所 | 最低要求 | 判定 |
|---|---|---|---|
| `@FunctionalInterface` ＋ ラムダ対象のインターフェース | `ResultSetHandler<R>`（§ 10 A-7） | **Java 8** | ✅ |
| `ThreadLocal.withInitial(Supplier)` | `MockConnection` の記録リスト（`security-requirements.md` SEC-6 のコード片）**のみ** | **Java 8** | ✅ |
| `new ThreadLocal<>()` ＋ 手動の遅延初期化 | `executedStatements`（§ 7.3）。既存 `executedQuery`（`DBAccessManager.java:35, :181-188`）と同形にするため `withInitial` を**使わない** | Java 1.2（ダイヤモンドは Java 7） | ✅ |
| インターフェースの `default` メソッド | `Query` の新メソッド（M-1。U2 の担当だが U1 の型を引数・戻り値に取る） | **Java 8** | ✅ |
| try-with-resources | § 7.1 の `executeQuery` / `executeUpdate` | Java 7 | ✅ |
| ダイヤモンド演算子 `<>` | 各所 | Java 7 | ✅ |
| メソッド型引数 `<R> R executeQuery(...)` | § 7.1 | Java 5 | ✅ |
| `switch` on enum | § 2.4 `isNullValue`、§ 6 `sqlTypeOf` | Java 5 | ✅ |
| `Collections.unmodifiableList` / `singletonList` / `emptyList` | `Param` / `PreparedSql` / `ExecutedStatement` の不変ラップ、§ 4.4 / § 4.5 | Java 1.2 | ✅ |
| `java.util.regex.Pattern` / `Matcher` | § 2.1 `isNumeric`（BR-3 が逐語的維持を定める） | Java 1.4 | ✅ |
| `java.sql.Types` の定数 | § 6 `sqlTypeOf`（BR-17） | Java 1.1 | ✅ |
| `PreparedStatement.setNull(int, int)` | § 6（BR-17） | Java 1.1 | ✅ |
| `PreparedStatement.setString(int, String)` | § 6（BR-16） | Java 1.1 | ✅ |
| `PreparedStatement.setBinaryStream(int, InputStream)`（2 引数形） | § 6（BR-16） | **JDBC 4.0 / Java 6** | ✅ 現行 `BlobUtils.java:18` が既に同じ形を使う |
| `Connection.prepareStatement(String)` | § 7.1 | Java 1.1 | ✅ |

### 使用しないことを明示する post-8 の機能

次はいずれも U1 のどのアルゴリズムにも現れない。実装時にうっかり混入しないよう列挙する。

`var`（Java 10）、`record`（16）、`List.of` / `Map.of` / `Set.of`（9）、`Optional.isEmpty()`（11）、`String.isBlank` / `strip` / `repeat`（11）、テキストブロック（15）、`switch` 式（14）、`sealed`（17）、`Stream.toList()`（16）、`instanceof` パターン（16）。

**`Stream` API そのものは Java 8 で使えるが、U1 では使わない。** `PreparedStatementBinder.bind` は位置（インデックス）を必要とするため素の `for` が素直であり、`ValueRules` は純関数の集合でコレクション変換を持たない。

---

## TSD-3. JaCoCo を追加する（OQ-4 の解）

`requirements.md` OQ-4「`org.md` が課す『line coverage 80% 以上』を本取り組みに適用するか。計測手段の導入可否を含む」に対する決定である。

| 項目 | 内容 |
|---|---|
| 決定 | `org.jacoco:jacoco-maven-plugin` を**ビルドプラグイン**として追加し、**U1 が新設する 8 クラスに限って** line coverage 80% をビルドゲートにする。既存クラスは対象外 |
| 由来 | 本ステージ Q1 = C、`requirements.md` OQ-4 |

### CON-6 も CON-1 も掛からないことの確認

| 制約 | 文面が掛かる対象 | JaCoCo への適用 |
|---|---|---|
| CON-6 | 「**compile スコープ**の第三者依存は `tamacat-core` と `javax.json` のみ」 | **掛からない。** Maven のビルドプラグインは `<dependencies>` ではなく `<build><plugins>` に宣言され、compile / test いずれのスコープにも入らず、生成される jar にも含まれない |
| CON-1 / NFR-4 | 「Java 8 を source / target として維持し、post-8 の言語機能・API を使用しない」 | **掛からない。** 生成物のバイトコードと使用 API に掛かる制約であり、ビルドツールの実行環境には掛からない。JaCoCo が解析するクラスファイルは target 1.8（class file v52）のまま |
| `org.md` `## Testing Posture` の 80% floor | `mvp` / `enterprise` / `feature` / `infra` の各 scope | **本 scope `sql-parameterization` は列挙外。** したがって 80% は課されていない。C は org.md が課していない水準を**自発的に、範囲を限って**課すものであり、広い方針に反しない |

### 設定

```xml
<!-- <build><plugins> に追加。surefire の既存設定は <includes> のみで
     <argLine> を持たないため（pom.xml:129-135）、prepare-agent が設定する
     ${argLine} と衝突しない -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version><!-- ビルド JVM 上で動作する版を選ぶ。下記「未解決事項」参照 --></version>
  <executions>
    <execution>
      <id>prepare-agent</id>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
    <execution>
      <id>check-new-classes</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>CLASS</element>
            <includes>
              <include>org.tamacat.dao.Param</include>
              <include>org.tamacat.dao.PreparedSql</include>
              <include>org.tamacat.dao.BindValue</include>
              <include>org.tamacat.sql.ValueRules</include>
              <include>org.tamacat.sql.BindSqlBuilder</include>
              <include>org.tamacat.sql.PreparedStatementBinder</include>
              <include>org.tamacat.sql.ExecutedStatement</include>
              <include>org.tamacat.sql.LikeEscape</include>
            </includes>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.80</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### ゲートの対象範囲

| 対象 | 理由 |
|---|---|
| 上記 8 クラス（`Param` / `PreparedSql` / `BindValue` / `ValueRules` / `BindSqlBuilder` / `PreparedStatementBinder` / `ExecutedStatement` / `LikeEscape`） | U1 が新設する実装コード。SM-1 の達成がこれらの正しさに依存する |
| **`ResultSetHandler<R>` は対象外** | 実装を持たない functional interface であり、計測すべき行がない |
| **mock スタック（`org.tamacat.mock.sql`）は対象外** | テスト用の検証基盤であり、それ自体が被覆率の対象になる性質のものではない |
| **既存クラスはすべて対象外** | カバレッジ規律が存在しないレガシーに遡って課すと初回から失敗する。`org.md` も本 scope に 80% を課していない |

### `mvn test` と `mvn verify` の違いを明示する

`report` は `test` フェーズ、`check`（ゲート）は `verify` フェーズに束ねている。したがって:

- `mvn test`（`technology-stack.md` が記録する現行の検証済みコマンド）——エージェントが動きレポートが出るが、**ゲートは発火しない**
- `mvn verify` / `mvn install`——ゲートが発火し、8 クラスのいずれかが 80% を下回るとビルドが失敗する

`check` を `test` フェーズに束ねると同一フェーズ内の実行順が POM の宣言順に依存して脆くなるため、標準的な `verify` 束ねを採る。**CI がどのコマンドを走らせるかは Build and Test（3.6）が決める。** NFR-5（`mvn test` が Failures = 0 / Errors = 0）はゲートの有無にかかわらず維持される。

---

## TSD-4. EasyMock は使わない（宣言は残す）

| 項目 | 内容 |
|---|---|
| 決定 | test スコープに宣言済みで参照されていない EasyMock 5.1.0（および推移依存の Objenesis 3.3）を、U1 では使わない。**削除もしない** |
| 由来 | ADR-009、`technology-stack.md`「EasyMock 5.1.0 / test / **Declared but referenced nowhere in `src/`**」 |

使わない理由は ADR-009 のとおり——既存 e2e テストが `org.tamacat.mock.sql` に依存しており、EasyMock を併用するとテストの書き方が 2 系統になる。削除しない理由は、未使用依存の整理が `requirements.md` OOS-6（変更経路上にない技術的負債）に該当しスコープ外であるためである。

---

## TSD-5. テストの構成を変えない

| 項目 | 決定 | 由来 |
|---|---|---|
| テストフレームワーク | JUnit 4 のまま。JUnit 5 への移行はしない | OOS-6（変更経路上にない） |
| surefire | 2.22.2 のまま。`<includes>**/*Test.java</include>` も変更しない | `technology-stack.md`。ただし `QueryImplTest02` / `QueryImplTest03` / `UserDaoTest2` の取り込み（FR-8.3）は **Build and Test 3.6** の担当であり、そこで includes が変わりうる |
| JUnit 3 継承の残存 | `SearchTest.java:19` と `SQLParserTest.java:19` は `junit.framework.TestCase` を継承したまま。**変更しない** | `SQLParserTest` は BR-31 が「1 行も変更してはならない」と定める挙動固定装置である。継承形を変えることはその不変性を壊す |
| 検証チャネル | `DBAccessManager.getExecutedQuery()` に加え、`getExecutedStatements()` と mock の記録が加わる | FR-4.1、FR-8.1 |

---

## 却下した選択肢

| 選択肢 | 却下理由 |
|---|---|
| Mockito 等の新規テストライブラリを追加する | CON-6 の趣旨（依存を増やさない）に反する。既存 mock スタックの拡張で足りる（ADR-009 代替 2） |
| mock を `src/test` へ移してから拡張する | `requirements.md` OOS-6 が明示的にスコープ外とした。公開パッケージの移動は `org.tamacat.mock.sql` を production で使う利用者を壊しうる（ADR-009 代替 3、FR-3.1） |
| カバレッジ計測を入れない（Q1 = A） | FR-8.1 が検証可能性を Must にしているセキュリティ改修で、量的な裏付けが件数（NFR-6）だけになる |
| リポジトリ全体に 80% を課す（Q1 = D） | 既存コードにカバレッジ規律がなく初回から失敗する見込みが高い。`org.md` も本 scope には課していない |
| `isNumeric` の `Pattern` を `static final` に持ち上げる | 挙動は同一だが NFR-7 / OOS-2（最適化を持ち込まない）に照らして必須としない。BR-3 は逐語的な移設を求めている。**禁止はしない**——実装者の判断に委ねる |
| PostgreSQL 専用の `Dao` / `Search` を追加する | FR-5.2 / OOS-4。U1 の範囲外でもあり、方言は U4 の担当 |

---

## 未解決事項（Build and Test 3.6 へ）

| # | 事項 | 由来 |
|---|---|---|
| 1 | **JaCoCo の版**。ビルド JVM は JDK 25 Corretto（`technology-stack.md`）。JaCoCo のエージェントは実行 JVM のクラスファイル版に追随が必要で、版が古いと JDK 25 で起動に失敗しうる。解析対象のクラスファイル自体は target 1.8（v52）なので問題にならない。JDK 25 を扱える版を選ぶか、カバレッジを走らせる JVM を toolchains で下げるかを 3.6 が決める | Q1 = C |
| 2 | **CI がどのコマンドを走らせるか**。ゲートは `verify` に束ねてあるため `mvn test` では発火しない | TSD-3 |
| 3 | **Derby 依存の追加可否**（`UserDaoTest2` の有効化）。ビルドの前提条件が増える | **OQ-6**（`requirements.md`）、FR-8.3 |
| 4 | **CodeQL の測定方法**。`master` への push / PR と週次 cron でのみ動作し `v2.0` では走らない | **OQ-7**、CON-8 |
| 5 | **surefire の `<includes>` 変更**。FR-8.3 が `QueryImplTest02` / `QueryImplTest03` / `UserDaoTest2` の取り込みを求めるが、これらは `**/*Test.java` に一致しない命名である | FR-8.3 |
| 6 | `MockDriver.acceptsURL` が URL を問わず `true` を返すため、Derby を classpath に加えると `DriverManager` が返すドライバが登録順に依存しうる | BR-37、`security-requirements.md` R-7 |

---

## 現行スタックの確認（変更なし）

`technology-stack.md` が記録する構成のうち、U1 が**触れない**ことを確認した項目。

| 項目 | 状態 |
|---|---|
| ビルドツール | Apache Maven、`jar` パッケージング。変更なし |
| `maven-compiler-plugin` 3.8.1（source/target 1.8） | 変更なし |
| `maven-source-plugin` / `maven-install-plugin` / `maven-jar-plugin` / `versions-maven-plugin` / `cyclonedx-maven-plugin` / `site-maven-plugin` | 変更なし |
| compile スコープ依存 3 件 | 変更なし（TSD-1） |
| test スコープ依存（JUnit / EasyMock / MySQL Connector/J / SLF4J / Logback / Hamcrest） | 変更なし（TSD-4、TSD-5） |
| JDBC ドライバは利用側が供給する | 変更なし（CON-5） |
| ORM フレームワーク非依存 | 変更なし（CON-6） |
| `META-INF/MANIFEST.MF` のバージョン乖離（`1.6.1` 対 pom の `2.0`） | **変更しない。** OOS-6 が技術的負債の整理をスコープ外としている |
| CodeQL ワークフローのブランチ設定 | **U1 では変更しない。** OQ-7 として 3.6 が判断する |

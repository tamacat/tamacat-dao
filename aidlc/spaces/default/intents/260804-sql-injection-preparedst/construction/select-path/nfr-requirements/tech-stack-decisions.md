# Tech Stack Decisions — U2 `select-path`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U2）— § 2（`Search` の追加フィールドと private `append`）、§ 3（`bindFragments` と private static `WhereFragment`）、§ 4（`getSelectPreparedSql()` と private ヘルパ 2 つ）、§ 5（サブクエリ）、§ 6（`Query` の `default` 9 個）、§ 7（`LinkedHashMap` による FROM 句の 2 状態）、§ 8（ラムダによるコールバック実行と「Java 8 適合」の注記）、§ 10（新規 public / protected メンバの全量）。本文書はこれらが使う言語機能・API を全数で確認する。
- **`business-rules.md`**（同上）— BR-10（旧メソッドの戻り値不変 ＝ 既存テストが無変更で緑）、BR-12（**本ステージで修正**。`security-requirements.md` SEC-11）、BR-19（値ゼロの `Param`）、BR-20（U2 が壊すテストと移行対象）、F 節 R-1〜R-6。
- **`requirements.md`**（requirements-analysis 2.3）— CON-1 / NFR-4（Java 8 の維持）、CON-3（ローカルのみ、`git push` しない）、CON-6（compile スコープの第三者依存）、NFR-5（`mvn test` が Failures = 0 / Errors = 0）、NFR-6（テスト件数 >= 138）、NFR-7 / OOS-2（最適化を持ち込まない）、**FR-7.2**（文書化。Must。本ステージで形を確定する）、FR-7.3、FR-8.2 / FR-8.3。
- **`technology-stack.md`**（codekb）— Maven、`maven-compiler-plugin` 3.8.1（source / target 1.8）、`maven-surefire-plugin` 2.22.2 の `**/*Test.java`、compile スコープ依存 3 件、テストスタックが JUnit 4 ＋ `org.tamacat.mock.sql`、ビルド JVM が JDK 25 Corretto、リポジトリルートに README / docs が存在しないこと。
- **U1 `bind-foundation` の `tech-stack-decisions.md`** — TSD-1（compile 依存を追加しない）、TSD-2（Java 8 の全数確認）、TSD-3（JaCoCo を U1 の新規 8 クラスに限ってゲート化）、TSD-4（EasyMock は使わない）、TSD-5（テスト構成を変えない）。U2 はこれらを継承し、TSD-3 の適用範囲だけを本文書で確定する。

`security-requirements.md` は本文書と対になる成果物で、Q1（`andOuterJoin` のバインド化）と Q3（同期テスト）を扱う。SEC-17（新規 compile 依存を追加しない）が本文書の TSD-7 に対応する。

---

## この文書の範囲

**U2 は技術選定を 1 件も行わない。** U1 が「変えないことの確認 4 件 ＋ 唯一の追加 1 件（JaCoCo）」だったのに対し、U2 はさらに小さい——スタックへの追加は **Markdown ファイル 1 つ**（Q4 = B の移行ガイド）だけである。

本文書の中身は 3 つである。

1. **U1 の決定の適用範囲を確定する**（TSD-6）——JaCoCo ゲートを U2 に拡張するかの判断
2. **変えないことの確認**（TSD-7、TSD-8、TSD-10）——何を変えないかを機械的に検査できる形で固定する
3. **唯一の追加**（TSD-9）——FR-7.2 の成果物としての移行ガイド

---

## TSD-6. カバレッジゲート（U1 TSD-3）を U2 に拡張しない

| 項目 | 内容 |
|---|---|
| 決定 | `jacoco-maven-plugin` の `check` ルールの `<includes>` を**変更しない**。ゲート対象は U1 が新設した 8 クラス（`Param` / `PreparedSql` / `BindValue` / `ValueRules` / `BindSqlBuilder` / `PreparedStatementBinder` / `ExecutedStatement` / `LikeEscape`）のままとする |
| 判定基準 | U2 の変更後も `pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` が 8 件のままである |
| 由来 | 本ステージ Q2 = A |

### U1 の形をそのまま適用できない理由

| 項目 | U1 | U2 |
|---|---|---|
| 新設するクラス | 8 個 | **0 個**（`WhereFragment` は `QueryImpl` の private static 内部型） |
| 変更するクラス | なし | `Query` / `QueryImpl` / `Search` / `Dao` / `DaoAdapter` の 5 個（いずれも既存レガシー） |
| ゲートの表現 | `<element>CLASS</element>` ＋ 新規クラスの列挙 | 同じ形にすると**既存の未被覆コードごと**測ることになる |
| 現在の被覆率 | 0%（クラス自体が存在しない） | **不明**。U1 が JaCoCo を入れるまで計測手段が存在しなかった |

JaCoCo には「クラスのうち今回変更した部分だけ」を測る機能がない。5 クラスを `<includes>` に加えると、ゲートは U2 と無関係な既存コードの被覆率に支配され、**数字がゲートとしての意味を持たなくなる**。

### 被覆率が U2 の主要な失敗様式を検出しないこと

これが決定の中心的な根拠である。`business-rules.md` H 節 1 が「U2 で最も危険」と名指しした失敗様式——同期規則（BR-1 / BR-3 / BR-5）の破れ——は、**行が実行されない**のではなく**別の行が実行される**形で現れる。

```java
// 欠陥のある実装。3 状態のうち 2 つしか更新しない
private void append(String connector, String literalSql, Param bindParam) {
    if (search.length() > 0) {
        search.append(" ").append(connector).append(" ");
        bindSearch.append(" ").append(connector).append(" ");
    }
    search.append(literalSql);
    bindSearch.append(bindParam.getSql());
    // bindValues.addAll(bindParam.getValues());  ← 書き忘れ
}
```

このメソッドの line coverage は **100%** になりうる。それでも `bindValues` は空のままで、`Param.of` の個数検査は `?` の個数と値の個数の不一致を捕まえる——**この例では捕まる**。しかし `search.append` と `bindSearch.append` の**両方**を条件分岐で飛ばした場合は個数が一致したまま述語が丸ごと欠落し、どの検査にも掛からない。被覆率はどちらのケースでも高いままである。

**規律をどこに投じるかの問題である。** U2 が払える検証コストは、被覆率のパーセンテージではなく `security-requirements.md` **SEC-13** が定める同期テスト（16 経路の述語数・連結子一致アサート）に投じる。そちらは失敗様式に正面から対応し、追加の実行時コストも API 表面もゼロである。

### 却下した形

| 選択肢 | 却下理由 |
|---|---|
| 5 クラスを 80% でゲート | 既存の未被覆コードごと測ることになり、数字がゲートの意味を持たない |
| 5 クラスを低いしきい値（60% 程度）で | 同じ問題。しきい値を下げても「U2 の変更部分が測れていない」ことは変わらない |
| `<element>METHOD</element>` で U2 のメソッドだけを対象に | `<include>` にメソッドシグネチャを列挙する形になり、実装中にシグネチャが変わるたびに POM を直すことになる。しきい値の意味も薄い（新規メソッドの多くは 3〜5 行の委譲である） |

### `org.md` との関係

`org.md` `## Testing Posture` の「line coverage 80% 以上」は `mvp` / `enterprise` / `feature` / `infra` に課される。本 scope **`sql-parameterization` は列挙外**である（U1 Q1 で確認済み）。したがって A は規約違反ではない。U1 の TSD-3 は org.md が課していない水準を**自発的に、範囲を限って**課したものであり、U2 がその範囲を広げないことも同じ性質の判断である。

---

## TSD-7. compile スコープの第三者依存を追加しない

| 項目 | 内容 |
|---|---|
| 決定 | U2 は compile スコープの第三者依存を 1 つも追加しない。`org.tamacat:tamacat-core:1.5`、`javax.json:javax.json-api:1.1.4`、`org.glassfish:javax.json:1.1.4` の 3 件という現状を維持する。**test スコープも追加しない** |
| 判定基準 | `mvn dependency:tree` の出力が変更前と一致する |
| 由来 | CON-6、U1 TSD-1、`security-requirements.md` SEC-17 |

U2 が追加するメンバはすべて JDK 標準 API（`java.util` / `java.sql`）と U1 が新設した型（`Param` / `PreparedSql` / `BindValue` / `ResultSetHandler`）だけで書ける。TSD-9 が追加する移行ガイドは Markdown ファイルであり、依存でもビルド設定でもない。

**EasyMock は U2 でも使わない**（U1 TSD-4 をそのまま継承する）。宣言は残す。

---

## TSD-8. Java 8 を維持する

| 項目 | 内容 |
|---|---|
| 決定 | `maven.compiler.source` / `target` を `1.8` のまま維持し、U2 のコードは post-8 の言語機能・API を使用しない |
| 判定基準 | `pom.xml:17-18`（プロパティ）と `pom.xml:101-102`（`maven-compiler-plugin` 3.8.1 の `<source>` / `<target>`）が `1.8` のまま。`mvn -q compile` が成功する |
| 由来 | CON-1、NFR-4、U1 TSD-2 |

### U2 が使う言語機能・API の全数確認

`business-logic-model.md` § 2〜§ 8 と `security-requirements.md` SEC-11 のコード片に現れる構成要素を漏れなく列挙し、最低要求バージョンを確かめる。

| 構成要素 | 使用箇所 | 最低要求 | 判定 |
|---|---|---|---|
| インターフェースの `default` メソッド | `Query` の新メソッド 9 個（§ 6.1）。**U2 で最も新しい機能である** | **Java 8** | ✅ |
| ラムダ式（`ResultSetHandler<R>` の実装） | § 8.2 の `Dao.search` / `searchList` の `rs -> { ... }` | **Java 8** | ✅ |
| 実質的 final の捕捉（ラムダのキャプチャ） | § 8.2 が捕捉する `query` / `columns` / `start` / `max`。いずれもメソッド引数、または再代入しないローカル | **Java 8** | ✅ § 8.2 の「Java 8 適合」注記が明示している |
| `@Deprecated` | 旧 6 メソッド（§ 6.2）、`Search.getSearchString()`（§ 2.4） | Java 5 | ✅ |
| private static ネストクラス | `QueryImpl.WhereFragment`（§ 3.1） | Java 1.1 | ✅ |
| ダイヤモンド演算子 `<>` | `new ArrayList<>()` / `new LinkedHashMap<>()` 各所 | Java 7 | ✅ |
| メソッド型引数 `<R> R executeQuery(...)` | § 8.2、§ 8.3 の `DaoAdapter` 転送 | Java 5 | ✅ |
| `java.util.LinkedHashMap` | `bindOuterJoinTables`（§ 7.1）。既存 `outerJoinTables`（`QueryImpl.java:45`）と同形 | Java 1.4 | ✅ |
| `java.util.ArrayList` / `List` / `Collections.emptyList()` | `bindValues`（§ 2.1）、`bindFragments`（§ 3.1）、値ゼロの `Param`（BR-19） | Java 1.2 | ✅ |
| `java.lang.StringBuilder` | `bindSearch`（§ 2.1）、`getSelectPreparedSql()` の WHERE 描画（§ 4.2） | Java 5 | ✅ |
| 拡張 for 文 | `buildFromClause` の `for (Table tab : tables)`（§ 7.4） | Java 5 | ✅ |
| 添字 for 文 | § 4.2 の `for (int i = 0; i < bindFragments.size(); i++)`（先頭断片の判定に添字が要る） | Java 1.0 | ✅ |
| 三項演算子 | § 4.2 の `i == 0 ? " " + WHERE + " " : " " + f.connector + " "` | Java 1.0 | ✅ |
| try-with-resources の**削除** | § 8.2 で `Dao` 側の `try (ResultSet rs = ...)` が消える（close は `DBAccessManager` が行う） | — | ✅ 機能を減らす方向 |

### 使用しないことを明示する post-8 の機能

次はいずれも U2 のどのアルゴリズムにも現れない。実装時にうっかり混入しないよう列挙する（U1 TSD-2 と同じ一覧）。

`var`（Java 10）、`record`（16）、`List.of` / `Map.of` / `Set.of`（9）、`Optional.isEmpty()`（11）、`String.isBlank` / `strip` / `repeat`（11）、テキストブロック（15）、`switch` 式（14）、`sealed`（17）、`Stream.toList()`（16）、`instanceof` パターン（16）。

**`Stream` API そのものは Java 8 で使えるが、U2 では使わない。** § 4.2 の WHERE 描画は先頭断片の判定に添字を必要とし、§ 7.4 の FROM 句はテキスト組み立てと値収集を同一ループで行う（BR-8）。どちらも素の `for` が素直であり、`Stream` に置き換えると BR-8 が守る「テキストの出力順と値の蓄積順がずれない」構造が読み取りにくくなる。

**インターフェースの `static` メソッドは使わない。** `Query` に足すのは `default` のみである（§ 6.1）。

---

## TSD-9. FR-7.2 の文書化を Javadoc ＋ 移行ガイドの 2 本立てにする

| 項目 | 内容 |
|---|---|
| 決定 | (1) 該当する各メソッドの Javadoc（非推奨メソッドは `@deprecated` タグ）に、SM-1 対象外である旨と代替経路を記す。(2) リポジトリルートに **`MIGRATION.md`** を新設し、旧経路 → 新経路の対応表を 1 か所にまとめる |
| 判定基準 | (a) 下表の全メソッドの Javadoc に代替経路の記述がある。(b) `MIGRATION.md` が存在し、下記 4 節を含む |
| 由来 | 本ステージ Q4 = B、FR-7.2（Must）、`unit-of-work.md` が FR-7.2 を U2 の担当 FR に含めている |

### なぜ 2 本立てか

| 手段 | 満たす FR-7.2 の要素 | 弱点 |
|---|---|---|
| Javadoc | (a) 各経路が SM-1 対象外であること、(b) 代替経路の存在 | **(c) 移行方法の全体像**が見えない。メソッド単位の断片に分かれる |
| `MIGRATION.md` | (c) 旧経路 → 新経路の移行方法を 1 か所で示す | リポジトリに文書ファイルを置く慣行が現状ないため、更新されずに腐るリスクがある |

Javadoc の強みは**到達率**である——非推奨メソッドを呼んだ利用者の IDE にその場で表示される。移行ガイドの強みは**全体像**である。FR-7.2 は 3 要素すべてを Must としており、片方だけでは満たせない。

**腐敗リスクへの対処**: `MIGRATION.md` の各行に対応する Javadoc が存在することを 3.6 の確認項目に含める（下記「未解決事項」2）。

### Javadoc を書くメソッド

| 分類 | メソッド | 記述内容 |
|---|---|---|
| FR-7.1 の raw 経路（非推奨にしない） | `Query.where(String)` / `and(String)` / `or(String)`、`Dao.param(Column, Conditions, String...)` | 呼び出し側の SQL テキストをそのまま連結すること、SM-1 の判定対象外であること、代替経路（`where(Param)` / `and(Param)` / `or(Param)` / `Dao.prepare(...)`） |
| 非推奨にする 6 メソッド | `Query.getSelectSQL()` / `getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)` / `getDeleteAllSQL(Table)` / `andOuterJoin(Table, Search)` | `@deprecated` タグに代替の `getXxxPreparedSql(...)` / `andOuterJoin(Table, Param)` を明記 |
| 非推奨にする 1 メソッド | `Search.getSearchString()` | `@deprecated` タグに `getSearchParam()` を明記 |
| 新 `default` のうち 5 個 | `Query.getSelectPreparedSql()` / `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)` | 既定実装が `PreparedSql.ofLiteral(getXxxSQL(...))` であり、**旧メソッドのリテラル埋め込み SQL をそのまま包む**こと。外部の `Query` 実装を使うと SM-1 が達成されない旨（`security-requirements.md` R-13 の形 1） |
| 新 `default` のうち 3 個 | `Query.where(Param)` / `and(Param)` / `or(Param)` | 既定実装が対応する `String` 形に委譲し、**`Param` が運ぶ値は捨てられる**こと。`PreparedSql.ofLiteral` は関与しない。結果として未束縛の `?` を含む SQL になり、実行すれば失敗する（R-13 の形 2、R-8） |
| 新 `default` のうち 1 個 | `Query.andOuterJoin(Table, Param)` | 既定実装が `return this;`（**何もしない**）であること。委譲先が存在しないため（BR-11）。外部実装では **outer join 条件が黙って落ちる**——サイレントな挙動であることを明記する（R-13 の形 3） |

**`andOuterJoin(Table, Search)` の記述は Q1 = B により変わる。** 「SM-1 対象外」の注記は**不要になる**（`security-requirements.md` SEC-11 によりこの経路はバインドされる）。残るのは「非推奨であること」と「代替経路 `andOuterJoin(Table, Param)`」の 2 点である。

### `MIGRATION.md` の構成

| 節 | 内容 |
|---|---|
| 1. 何が変わったか | SELECT 経路が `PreparedStatement` によるバインドに切り替わったこと。**旧 API の戻り値は 1 文字も変わらない**こと（BR-10）。再コンパイルは不要（NFR-3 / AC-11） |
| 2. 旧経路 → 新経路の対応表 | 上の Javadoc 表と同じ組を、利用者視点の 1 枚の表にする |
| 3. SM-1 の対象外に残る経路 | FR-7.1 の 4 経路 ＋ 外部 `Query` 実装のフォールバック（R-13）＋ 識別子位置（R-12、U5 で扱う予定）。**なぜ対象外なのか**（呼び出し側が渡すのは開発者が書いた SQL である、という Q5 = A の判断）を添える |
| 4. 注意点 | `where(Param)` 等で組み立てた `Query` の `getSelectSQL()` は未束縛の `?` を含む（R-8）。`where(String)` 等に引用符の外の `?` を含むテキストを渡すと `InvalidParameterException` になる（BR-19） |

**CON-3 と抵触しない。** CON-3 が禁じるのは `git push` であってコミットではない。`MIGRATION.md` はリポジトリに置くが v2.0 ブランチのローカルに留まる。

**FR-8.2 の対応表とは別物である。** あれは「旧アサーション（生成 SQL 文字列）→ 新アサーション（バインド値）」の等価性を示す内部成果物で、集約先は Build and Test（3.6）である（BR-20）。`MIGRATION.md` は利用者向けの文書であり、混同しない。

### 却下した形

| 選択肢 | 却下理由 |
|---|---|
| Javadoc のみ | FR-7.2 の (c)（移行方法）がメソッド単位の断片に分かれ、全体像が示せない |
| Javadoc ＋ `maven-javadoc-plugin` で Javadoc jar を生成 | 配布物の構成を変える決定になる。CON-6 は掛からない（ビルドプラグイン）が、`technology-stack.md` が記録する現行の配布構成（`maven-source-plugin` によるソース jar のみ）を変えることの是非は本取り組みの範囲外である。**禁止はしない**——3.6 が必要と判断すれば追加できる |
| 3.6 に委ねる | FR-7.2 は Must であり `unit-of-work.md` が U2 の担当 FR に置いている。形を決めずに渡すと担当が曖昧になる |

---

## TSD-10. テストの構成を変えない

| 項目 | 決定 | 由来 |
|---|---|---|
| テストフレームワーク | JUnit 4 のまま。JUnit 5 への移行はしない | OOS-6、U1 TSD-5 |
| surefire | 2.22.2 のまま。`<includes>**/*Test.java</include>` も U2 では変更しない。`QueryImplTest02` / `QueryImplTest03` / `UserDaoTest2` の取り込み（FR-8.3）は **Build and Test 3.6** の担当 | U1 TSD-5 |
| JUnit 3 継承の残存 | `SearchTest.java:18` は `junit.framework.TestCase` を継承したまま。**変更しない**。U2 はこのファイルに**バインド版のアサートを追加する**が、継承形には触れない | BR-10、BR-20 |
| `SQLParserTest` | **1 行も変更しない**（U1 BR-31）。U2 でも同じ | BR-20 |
| `SearchTest` / `QueryImplTest` の既存アサート | **変更しない**。BR-10 により旧メソッドの戻り値が不変であるため、変更が必要になったらそれは戻り値が変わった証拠である | BR-10、BR-20 |
| 検証チャネル | U1 が用意した mock の位置・値アサート（`MockPreparedStatement` の記録）と `DBAccessManager.getExecutedStatements()` を使う。U2 は新しい検証チャネルを追加しない | FR-8.1、U1 TSD-5 |

**U2 が書き換えるテスト**（BR-20）: `src/test/java/org/tamacat/dao/test/UserDao.java:22, :43` と `FileDataDao.java:12, :30` の `param()` → `prepare()`、`src/test/java/org/tamacat/dao/impl/MySQLDaoTest.java:83` の同様の切り替え、`UserDaoTest` の SELECT 経路のアサート文字列。**これらはすべて `src/test` にあり production jar には同梱されない**——`requirements.md` は `UserDao` / `FileDataDao` を「サンプル DAO」と呼ぶが、実際にはテストフィクスチャである。したがって BR-20 の移行は利用者に見える変更ではない。

---

## 却下した選択肢

| 選択肢 | 却下理由 |
|---|---|
| JaCoCo のゲート対象に U2 の変更 5 クラスを加える | TSD-6。既存の未被覆コードごと測ることになり数字が意味を持たない。U2 の主要な失敗様式は被覆率で検出できない |
| `getSelectPreparedSql()` に実行時の同期検査を入れる | `security-requirements.md` SEC-13 の論点。全クエリに走査コストが乗り NFR-7 / OOS-2 に照らして正当化しにくい。リテラル面の述語数を数えるパーサ自体が新しい欠陥源になる |
| `Search` / `QueryImpl` にテスト専用の診断メソッド（`isSynchronized()`）を置く | API 表面が増える。`org.tamacat.dao` と `org.tamacat.dao.impl` は別パッケージであり package-private では届かない（§ 2.4 の `getSearchParam()` が public になったのと同じ理由）。SEC-13 のテストは既存の public アクセサだけで書けるため不要 |
| `WhereFragment` を public にする | 内部表現であり公開する理由がない。public にすると NFR-3 の互換維持対象が増える（`business-logic-model.md` § 10） |
| `bindValues` / `bindFragments` の走査に `Stream` を使う | TSD-8。BR-8 が守る「テキストの出力順と値の蓄積順がずれない」構造が読み取りにくくなる。Java 8 で使えるが選ばない |
| `maven-javadoc-plugin` を追加して Javadoc jar を生成する | TSD-9。配布物の構成を変える決定であり本取り組みの範囲外。禁止はしない |
| `Dao` の `SQLParser` を廃して `BindSqlBuilder` に一本化する | BR-14 が「2 つの生成器を持つ冗長を受け入れる」と定めている。`param(...)` は FR-7.3 により無変更で残す必要がある |

---

## 未解決事項（Build and Test 3.6 へ）

U1 が送った 6 項目に加えて、U2 から次を送る。

| # | 事項 | 由来 |
|---|---|---|
| 7 | **SEC-13 の同期テストの実装**。16 経路それぞれの述語数・連結子一致アサート。**U2 で最も危険な失敗様式に対する唯一の検出手段**であり、被覆率ゲートの代わりに置く規律である | Q2 = A、Q3 = A |
| 8 | **`MIGRATION.md` と Javadoc の整合確認**。移行ガイドの各行に対応する Javadoc が存在すること。文書が腐るリスクへの対処 | Q4 = B、TSD-9 |
| 9 | **surefire の `<includes>` 変更**（FR-8.3）。`QueryImplTest02` / `QueryImplTest03` / `UserDaoTest2` は `**/*Test.java` に一致しない命名である。U1 の未解決事項 5 と同じ項目であり、U2 でも解決していない | FR-8.3、U1 TSD-5 |
| 10 | **NFR-6 の再確認**。U2 は既存テストを 1 件も削らずアサートを追加する方向であるため件数は増えるが、`UserDaoTest` の SELECT 経路の書き換えが件数に影響しないことを確認する | NFR-6、BR-20 |
| 11 | **`maven-javadoc-plugin` の要否**。TSD-9 は Javadoc jar を生成しない決定をしたが、FR-7.2 の到達性を高めるために 3.6 が追加を判断してよい | TSD-9 |
| 12 | **3.1 § 7.1 の引用行番号のずれ**。`business-logic-model.md` § 7.1 は `outerJoinTables` を `QueryImpl.java:44` と引用するが、実ソースは **`:45`**（`:44` は `removeFromTables`、`:43` は `tables`）。3.1 の成果物は編集しないため、実装時はこの訂正に従う | 本ステージのレビュー iteration 2（non-blocking） |

---

## 現行スタックの確認（変更なし）

`technology-stack.md` が記録する構成のうち、U2 が**触れない**ことを確認した項目。

| 項目 | 状態 |
|---|---|
| ビルドツール | Apache Maven、`jar` パッケージング。変更なし |
| `maven-compiler-plugin` 3.8.1（source / target 1.8） | 変更なし（TSD-8） |
| `maven-surefire-plugin` 2.22.2 | 変更なし（TSD-10） |
| `jacoco-maven-plugin`（U1 が追加） | **設定を変更しない**（TSD-6）。`<includes>` は U1 の 8 クラスのまま |
| `maven-source-plugin` / `maven-install-plugin` / `maven-jar-plugin` / `versions-maven-plugin` / `cyclonedx-maven-plugin` / `site-maven-plugin` | 変更なし |
| compile スコープ依存 3 件 | 変更なし（TSD-7） |
| test スコープ依存（JUnit / EasyMock / MySQL Connector/J / SLF4J / Logback / Hamcrest） | 変更なし（TSD-7、TSD-10） |
| JDBC ドライバは利用側が供給する | 変更なし（CON-5） |
| ORM フレームワーク非依存 | 変更なし（CON-6） |
| `META-INF/MANIFEST.MF` のバージョン乖離（`1.6.1` 対 pom の `2.0`） | **変更しない。** OOS-6 |
| CodeQL ワークフローのブランチ設定 | **U2 では変更しない。** OQ-7 として 3.6 が判断する |
| リポジトリルートの構成 | **`MIGRATION.md` を 1 件追加する**（TSD-9）。それ以外は変更なし |

---

## Review

<!-- reviewer が記入する -->

# Tech Stack Decisions — U3 `write-path`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U3）— § 2〜§ 4（INSERT / UPDATE / DELETE のバインド版、メソッドローカルの `BindSqlBuilder` と値アキュムレータ）、§ 5 / § 5.1 / § 5.2（BLOB 実行の `DBAccessManager` / `Dao` / `DaoAdapter` 側）、§ 9（新規 public / protected メンバ、Pr-1〜Pr-10）。本文書はこれらが使う言語機能・API を全数で確認する。
- **`business-rules.md`**（同上）— BR-2（既存ロジックを 1 文字も変えない）、BR-12（FR-6.2 のリテラル版修正、**本ステージが変更範囲を確認**）、BR-15〜BR-17（`BindSqlBuilder` のローカル変数化、`Dao` / `DaoAdapter` の独立追加）、失敗様式 1〜3。
- **`requirements.md`**（requirements-analysis 2.3）— CON-1 / NFR-4（Java 8 の維持）、CON-3（ローカルのみ、`git push` しない）、CON-6（compile スコープの第三者依存）、NFR-5、NFR-6、FR-1.5（BLOB）、FR-8.2 / FR-8.3。
- **`technology-stack.md`**（codekb）— `maven-compiler-plugin` 3.8.1（source / target 1.8）、compile スコープ依存 3 件、テストスタックが JUnit 4 ＋ `org.tamacat.mock.sql`、ビルド JVM が JDK 25 Corretto。
- **U1 `bind-foundation` の `tech-stack-decisions.md`** — TSD-1〜TSD-5、とくに TSD-3（JaCoCo を U1 の新規 8 クラスに限ってゲート化）。
- **U2 `select-path` の `tech-stack-decisions.md`** — TSD-6〜TSD-10、とくに TSD-9（`MIGRATION.md` の新設と 4 節構成、Javadoc との 2 本立て）。U3 はこれを継承し新設ファイルへ追記する。

`security-requirements.md` は本文書と対になる成果物で、Q1（カバレッジゲート）と Q2（BLOB override の文書化）を扱う。

---

## この文書の範囲

**U3 は技術選定を 1 件も行わない。** U2 と同様、スタックへの追加は既存の `MIGRATION.md`（U2 が新設）への追記だけである。

本文書の中身は 3 つである。

1. **U1・U2 の決定の適用範囲を確定する**（TSD-20）——JaCoCo ゲートを U3 に拡張するかの判断
2. **変えないことの確認**（TSD-21、TSD-22、TSD-23）——何を変えないかを機械的に検査できる形で固定する
3. **唯一の追加**（TSD-24）——`MIGRATION.md` への BLOB override セクションの追記

---

## TSD-20. カバレッジゲート（U1 TSD-3、U2 TSD-6）を U3 に拡張しない

| 項目 | 内容 |
|---|---|
| 決定 | `jacoco-maven-plugin` の `check` ルールの `<includes>` を変更しない。ゲート対象は U1 が新設した 8 クラスのままとする |
| 判定基準 | **U3 の変更によって** `pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` が 1 件も増減しない（U1 の 8 クラス基準からの差分がゼロ）。**「8 件のまま」という絶対数では判定しない**——U5 TSD-16 が新規クラス `IdentifierRules` をこの `<includes>` に追加するため、U5 適用後の全 Unit 適用後の件数は 9 件になる。「8 件」は U1〜U4 単体の判定基準の字面であり、全 Unit 適用後の状態ではない。**前提**: この判定は U1 の Code Generation（3.5）が JaCoCo 設定を実際に追加した後にのみ検査できる——現時点の `pom.xml` に `jacoco` の記述は 1 件も無い（surefire 設定は `:127-136`） |
| 由来 | 本ステージ Q1 = A |

### U2 の判断をそのまま適用できる理由

| 項目 | U2 | U3 |
|---|---|---|
| 新設するクラス | 0 個 | **0 個** |
| 変更するクラス | `Query` / `QueryImpl` / `Search` / `Dao` / `DaoAdapter` の 5 個 | `QueryImpl` / `Dao` / `DaoAdapter` / `DBAccessManager` の 4 個（いずれも既存レガシー） |
| 現在の被覆率 | 不明 | **不明**（U1 が JaCoCo を入れて以降、U2・U4・U5 のいずれもこの 4 クラスへのゲート拡張を選ばなかった） |
| 主要な失敗様式が line coverage で検出できるか | できない（同期破れ） | **できない**（失敗様式 1、値の位置ずれ） |

U2 TSD-6 が確立した論拠——JaCoCo にはクラスの変更部分だけを測る機能がなく、既存クラス全体を `<includes>` に加えると数字がゲートの意味を持たなくなる——は U3 にもそのまま成立する。**U4・U5 も同じ判断を踏襲しており（U5 の TSD-16 は新規クラス `IdentifierRules` を追加する場合のみ拡張した例外であり、既存クラスへの拡張ではない）、U3 で判断を変える根拠はない。**

### 被覆率が U3 の主要な失敗様式を検出しないこと

`business-rules.md` 失敗様式 1（SET と WHERE の値が 1 つずつずれる）は、行が実行されない形ではなく**別の順序で同じ行が実行される**形で現れる。`insertValues` / `setValues` と `bindFragments` 由来の WHERE 値を誤った順序で結合しても、行はすべて通過し `?` の個数も値の個数も変わらない。line coverage は 100% になりうる。

**規律は AC-3b / AC-7 の mock 位置・値アサートに投じる**（`security-requirements.md` SEC-28 / SEC-29）。そちらは失敗様式に正面から対応し、追加の実行時コストも API 表面も増やさない。

### 却下した形

| 選択肢 | 却下理由 |
|---|---|
| 4 クラスを 80% でゲート | 既存の未被覆コードごと測ることになり、数字がゲートの意味を持たない |
| 4 クラスを低いしきい値で | 同じ問題。しきい値を下げても「U3 の変更部分が測れていない」ことは変わらない |

### `org.md` との関係

本 scope `sql-parameterization` は `## Testing Posture` の 80% floor の列挙外である（U1 Q1 で確認済み、U2〜U5 で継続して確認）。

---

## TSD-21. compile スコープの第三者依存を追加しない

| 項目 | 内容 |
|---|---|
| 決定 | U3 は compile スコープの第三者依存を 1 つも追加しない。`org.tamacat:tamacat-core:1.5`、`javax.json:javax.json-api:1.1.4`、`org.glassfish:javax.json:1.1.4` の 3 件という現状を維持する。test スコープも追加しない |
| 判定基準 | `mvn dependency:tree` の出力のうち、**U3 の変更に起因する差分が 0 件**である（`business-logic-model.md` § 10 が OQ-6 として残す Derby 導入検討など、他 Unit 由来の test スコープ追加はこの判定基準の対象外） |
| 由来 | CON-6、U1 TSD-1、U2 TSD-7、`security-requirements.md` SEC-32（同じ「U3 由来の差分」の粒度で揃える） |

U3 が追加するメンバはすべて JDK 標準 API（`java.util` / `java.io.InputStream` / `java.sql`）と U1・U2 が新設した型（`Param` / `PreparedSql` / `BindValue` / `BindSqlBuilder` / `PreparedStatementBinder`）だけで書ける。`MIGRATION.md` への追記は Markdown ファイルであり依存ではない。

**EasyMock は U3 でも使わない**（U1 TSD-4 を継承する）。

---

## TSD-22. Java 8 を維持する

| 項目 | 内容 |
|---|---|
| 決定 | `maven.compiler.source` / `target` を `1.8` のまま維持し、U3 のコードは post-8 の言語機能・API を使用しない |
| 判定基準 | `pom.xml:17-18` / `:101-102` が `1.8` のまま。`mvn -q compile` が成功する |
| 由来 | CON-1、NFR-4、U1 TSD-2、U2 TSD-8 |

### U3 が使う言語機能・API の全数確認

`business-logic-model.md` § 2〜§ 7 のコード片に現れる構成要素を漏れなく列挙する。

| 構成要素 | 使用箇所 | 最低要求 | 判定 |
|---|---|---|---|
| メソッドローカル変数の `new`（`BindSqlBuilder`） | § 2、§ 3.1、§ 4（BR-15） | Java 1.0 | ✅ 現行 `SQLParser parser` と同形 |
| try-with-resources | § 5 の `DBAccessManager.executeUpdate(PreparedSql,int,InputStream)`（`try (PreparedStatement ps = ...)`） | **Java 7** | ✅ |
| ダイヤモンド演算子 `<>` | `new ArrayList<>()` 各所（§ 2、§ 3.1、§ 4） | Java 7 | ✅ |
| `java.util.ArrayList` / `List` | `insertValues` / `setValues` / `bindWhereValues` / `combined` | Java 1.2 | ✅ |
| `java.lang.StringBuilder` | § 2 の `columns` / `values`、§ 3 の `setText` | Java 5 | ✅ |
| 拡張 for 文 | `for (Column col : updateColumns.toArray(...))`（§ 2、§ 3） | Java 5 | ✅ |
| 添字 for 文 | § 4 `buildBindWhere` の `for (int i = 0; i < bindFragments.size(); i++)` | Java 1.0 | ✅ U2 § 4.2 と同形（BR-9） |
| 三項演算子 | § 4 `buildBindWhere` の `i == 0 ? ... : ...` | Java 1.0 | ✅ |
| `java.io.InputStream` | § 5 の `executeUpdate(PreparedSql,int,InputStream)` | Java 1.0 | ✅ 現行 `Dao.executeUpdate(String,int,InputStream)` と同じ型 |
| `PreparedStatement.setBinaryStream(int,InputStream)` | § 5（BR-10） | JDBC 1.0（Java 1.1） | ✅ |
| private ヘルパメソッド（`buildBindWhere`） | § 4 | Java 1.0 | ✅ |

### 使用しないことを明示する post-8 の機能

U1 TSD-2 / U2 TSD-8 と同じ一覧を再確認する。`var`（10）、`record`（16）、`List.of`（9）、`Optional.isEmpty()`（11）、テキストブロック（15）、`switch` 式（14）、`Stream.toList()`（16）、`instanceof` パターン（16）——いずれも U3 のアルゴリズムに現れない。

**`Stream` API は使わない。** § 4 の `buildBindWhere` は先頭断片の判定に添字を必要とし（U2 § 4.2 と同型、BR-9）、素の `for` が BR-7 の「結合を 1 箇所に限定する」構造を最も明確に表す。

---

## TSD-23. テストの構成を変えない

| 項目 | 決定 | 由来 |
|---|---|---|
| テストフレームワーク | JUnit 4 のまま | OOS-6、U1 TSD-5 |
| surefire | 2.22.2 のまま。`<includes><include>**/*Test.java</include></includes>`（`pom.xml:132-134`）も U3 では変更しない | U1 TSD-5、U2 TSD-10 |
| `QueryImplTest` の既存アサート | 書き込み系バインド版アサートを追加する。既存のリテラル版アサートは変更しない（BR-2 により旧ロジックは 1 文字も変わらないため） | BR-2、NFR-5 |
| 検証チャネル | U1 が用意した mock の位置・値アサート（`MockPreparedStatement` の記録）を使う。U3 は新しい検証チャネルを追加しない | FR-8.1、U1 TSD-5 |

**U3 が書き換えるテスト**（FR-8.2、`security-requirements.md` R-29 の移行が前提）: `UserDao` / `FileDataDao` を `getInsertPreparedSql(T)` 等の override へ移行した上で、`UserDaoTest` の INSERT / UPDATE / DELETE 経路のアサート文字列を `?` 入りに書き換える。**この移行を行わない限り、既定フォールバック（BR-14）により `UserDaoTest` の該当アサートはリテラルのまま変化しない**（R-29）。移行対象はすべて `src/test` にあり production jar には同梱されない。

---

## TSD-24. `MIGRATION.md`（U2 が新設）に BLOB override のセクションを追記する

| 項目 | 内容 |
|---|---|
| 決定 | U2 が新設した `MIGRATION.md` に「BLOB を扱う書き込みの移行」セクションを追加する。新規ファイルは作らない |
| 判定基準 | `MIGRATION.md` に BLOB override のセクションが存在し、`business-logic-model.md` § 6 のコード片相当の実装例（`create`/`update` の override、`getBindIndexOf(DataType.OBJECT, 1)` の使用、`executeUpdate(PreparedSql, int, InputStream)` の呼び出し）を含む |
| 由来 | 本ステージ Q2 = B、`security-requirements.md` SEC-30 |

### 追記するセクションの構成

| 項目 | 内容 |
|---|---|
| 何が変わったか | `Dao` / `DaoAdapter` の既定 `create`/`update`/`delete` が `executeUpdate(PreparedSql)` を経由するようになったこと。**BLOB を扱わないサブクラスは何もする必要がない**（既定実装のみで新経路に乗る） |
| BLOB を扱うサブクラスが必要な変更 | **このリポジトリの実利用サブクラスはすべて `DaoAdapter` を継承する**（`Dao` の直接継承者は方言専用の `MySQLDao` / `OracleDao` のみ）ため、override 先は `DaoAdapter` である。`create(T)` / `update(T)` を override し、(1) `getInsertPreparedSql(data)` / `getUpdatePreparedSql(data)` で `PreparedSql` を得る、(2) `sql.getBindIndexOf(DataType.OBJECT, 1)`（または `getBlobIndex()`）で位置を得る、(3) `data` から取り出した `InputStream` とともに `executeUpdate(PreparedSql, int, InputStream)` を呼ぶ、の 3 手順。実装例は `business-logic-model.md` § 6 / § 5.2 のコード片を利用者向けに簡略化したもの |
| 注意点 | `Dao.getInsertPreparedSql(T)` **および `DaoAdapter.getInsertPreparedSql(T)`**（BR-17 により独立、`business-logic-model.md` § 5.2）の既定実装（`PreparedSql.ofLiteral(...)`）のまま override せずに BLOB を扱うと `getBindIndexOf` が常に `-1` を返す（`security-requirements.md` R-27）。BLOB override では、override 先のクラス（実際には `DaoAdapter`）で `QueryImpl` 由来のバインド版 `PreparedSql` を使うこと |
| 呼び出し元 0 件であることの明記 | リポジトリ内に BLOB override の既存実装がないため、この情報の到達経路は本文書と Javadoc のみである旨を明記する |

**Javadoc との役割分担は U2 TSD-9 と同型。** Javadoc は該当メソッド（`Dao.executeUpdate(PreparedSql,int,InputStream)`、`Dao.getInsertPreparedSql(T)` 等）に個別の記述を持ち、`MIGRATION.md` は override の手順全体を 1 か所にまとめる。

### 却下した形

| 選択肢 | 却下理由 |
|---|---|
| Javadoc のみ | 呼び出し元が 0 件であるため、実装例という形の情報は Javadoc の断片的な記述だけでは伝わりにくい（Q2 の論点） |
| 新規ファイル（例: `BLOB_MIGRATION.md`）を作る | U2 が確立した「移行情報は `MIGRATION.md` に集約する」慣行を破る。既存ファイルへの追記で十分 |

---

## 却下した選択肢

| 選択肢 | 却下理由 |
|---|---|
| JaCoCo のゲート対象に U3 の変更 4 クラスを加える | TSD-20。既存の未被覆コードごと測ることになり数字が意味を持たない。U3 の主要な失敗様式（値の位置ずれ）は被覆率で検出できない |
| `Dao` のデフォルト `create`/`update` に BLOB の自動判定を組み込む | `business-rules.md` BR-11。`InputStream` の取得元を汎用的に知る方法がなく実現不可能（iteration 1 の blocking 指摘 B-6 の是正で確定済み） |
| `insertValues` / `setValues` を `QueryImpl` のインスタンスフィールドにする | BR-1。単回使用の呼び出しパターン（各ビルドメソッドは呼ばれるたびに新しい値集合を作る）にインスタンスフィールドは適さず、再入時に前回の値が残留するリスクを生む |

---

## 未解決事項（Build and Test 3.6 へ）

U1・U2 が送った項目に加えて、U3 から次を送る。

| # | 事項 | 由来 |
|---|---|---|
| 13 | **AC-3b / AC-7 の mock アサートの実装**。U3 で最も危険な失敗様式（値の位置ずれ）に対する唯一の検出手段であり、被覆率ゲートの代わりに置く規律である | Q1 = A、`security-requirements.md` SEC-28 / SEC-29 |
| 14 | **BLOB override の統合テスト**。`DaoAdapter` を継承したサブクラスで override した `create`/`update` が正しく `executeUpdate(PreparedSql,int,InputStream)` を呼べることを確認する | Q2 = B、`security-requirements.md` SEC-30 / R-24 / R-27 |
| 15 | **`MIGRATION.md` の BLOB override セクションと Javadoc の整合確認**（U2 未解決事項 8 の延長） | Q2 = B、TSD-24 |
| 16 | **失敗様式 2 の検出方法の書き換え**。`business-rules.md` の記述を「`getBlobIndex()` と `getBindIndexOf` の突き合わせ」から「列の並びから手で導いた絶対位置との突き合わせ」に改める（R-28、恒真になっている） | `security-requirements.md` R-28 |
| 17 | **FR-8.2 の対応表**の U3 該当行 | `security-requirements.md` |

---

## 現行スタックの確認（変更なし）

| 項目 | 状態 |
|---|---|
| ビルドツール | Apache Maven、`jar` パッケージング。変更なし |
| `maven-compiler-plugin` 3.8.1（source / target 1.8） | 変更なし（TSD-22） |
| `maven-surefire-plugin` 2.22.2 | 変更なし（TSD-23） |
| `jacoco-maven-plugin`（U1 が追加） | U3 は増減させない（TSD-20）。U5 が `IdentifierRules` を追加するため、全 Unit 適用後の `<includes>` は 9 件になる |
| compile スコープ依存 3 件 | 変更なし（TSD-21） |
| test スコープ依存 | 変更なし（TSD-21、TSD-23） |
| JDBC ドライバは利用側が供給する | 変更なし（CON-5） |
| リポジトリルートの構成 | `MIGRATION.md`（U2 新設）に BLOB override セクションを追記する（TSD-24）。新規ファイルの追加はなし |

---

## Review

<!-- reviewer が記入する -->

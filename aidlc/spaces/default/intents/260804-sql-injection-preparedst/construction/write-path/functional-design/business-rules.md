# Business Rules — U3 `write-path`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U3 の責務・境界・依存 U1・U2、「最大の設計リスク」、実装上の制約（ADR-004、ADR-012）。BR-1 / BR-7 はここに直接対応する。
- **`unit-of-work-story-map.md`**（同上）— U3 が担う FR-1.4 / 1.5 / 3.2 / 6.2、判定する AC-3b / AC-7、実装順序 1〜7、cross-cutting 要件。
- **`requirements.md`**（requirements-analysis 2.3）— FR-1.4、FR-1.5、FR-3.2、FR-6.2、AC-3b、AC-7、NFR-1、NFR-3、NFR-5、NFR-6、CON-1。
- **`component-methods.md`**（application-design 2.6）— M-2 の値アキュムレータ表、FR-6.2 の修正指示、M-4 の追加・変更・不変。
- **U1 `bind-foundation` の 3.1 成果物** — `BindSqlBuilder.placeholder`、OBJECT カラムの扱い、`DBAccessManager` の実行機構。
- **U2 `select-path` の 3.1 成果物** — `bindFragments`（WHERE のバインド断片）、`addWhere(String, String, Param)` の 3 引数内部形、`Dao.executeQuery(PreparedSql, ResultSetHandler)`。

`domain-entities.md` が状態と不変条件（WV-n / BI-n）、`business-logic-model.md` がアルゴリズムを扱う。**本文書は「どの経路でも守られなければならない規則」を列挙する。**

---

## 規則一覧

| # | 規則 | 由来 |
|---|---|---|
| BR-1 | `insertValues` / `setValues` は `QueryImpl` の**インスタンスフィールドではなく**、各ビルドメソッド呼び出しのローカル変数にする。WHERE 側の値は U2 の `bindFragments`（インスタンス）から都度収集する（U2 は `whereValues` という名のフィールドを持たない、iteration 1 の是正） | `component-methods.md` M-2、`domain-entities.md` |
| BR-2 | `getInsertPreparedSql` の値取得ロジック（`isUpdate` / `isAutoGenerateId` / `isAutoTimestamp` の分岐、`UniqueCodeGenerator` の使用）は現行 `getInsertSQL` から 1 文字も変えない。変わるのは値の適用先（テキスト埋め込み → `BindValue`）だけである | 現行維持、NFR-3 |
| BR-3 | INSERT / UPDATE のテキストトークンは `bindSqlBuilder.placeholder(col, value)` が返す `p.getSql()` を**そのまま**使う。`"?"` を固定で書かない——U1 `tokenFor` は `DataType.FUNCTION` や `current_timestamp` 等の `isSqlFunction` 値に対して `?` を伴わないテキストを返す（値ゼロ）。対応する `insertValues` / `setValues` への追加も `p.getValues()`（0 個または 1 個）に従う。列を数える独立したカウンタは持たない（iteration 1 の是正。以前は `"?"` を固定で書き `position` を列ごとに 1 ずつ進めていたため、自動タイムスタンプ列を持つ UPDATE がすべて失敗する欠陥だった） | U1 § 4.1 `tokenFor` |
| BR-4 | UPDATE / DELETE の主キー述語は `bindSqlBuilder.value(Column, Conditions, String...)`（述語形、テーブル修飾あり）を使い、`addWhere(String, String, Param)`（U2 の 3 引数内部形）経由で `where`（リテラル）と `bindFragments`（バインド）の両方に積む。`appendWhere`（U2 § 3.2）が両者を同時更新する | U2 § 3.3 の踏襲 |
| BR-4a | UPDATE / DELETE の WHERE 句のバインドテキストと値は `bindFragments` から描画する（`buildBindWhere`、U2 `getSelectPreparedSql()` § 4.2 と同じパターン）。`where.toString()`（リテラル）を SQL テキストに使わない——リテラル埋め込みの値が実行経路に残り（NFR-1 違反）、かつ主キー述語のような値ゼロのリテラルテキストでは `?` の個数と値の個数が食い違い `PreparedSql.of` が必ず落ちる（iteration 1 の blocking 指摘 B-1 の是正） | U2 § 4.2、`business-logic-model.md` § 4 |
| BR-5 | UPDATE の SET 句は `col.getColumnName()`（テーブル修飾なし）でテキストを直接組み立てる。`bindSqlBuilder.value(...)` が返す述語形（テーブル修飾付き）を使ってから事後的に剥がす現行の設計（`.replaceFirst(tableName + ".", "")`）は採らない | FR-6.2 の根本原因の除去 |
| BR-6 | 新経路の `blobIndex` は「個数」ではなく「バインドパラメータ位置」（1 始まり、OBJECT がなければ `-1`）を保持する。値は `PreparedSql` 組み立て後に `getBindIndexOf(DataType.OBJECT, 1)`（U1）を呼んで得る——列を数える独立したカウンタで計算しない（iteration 1 の是正。列カウンタは `p.getValues()` が 0 個になる列があると位置がずれる、`domain-entities.md` BI-2 にも反していた） | FR-3.2、`component-methods.md` M-2、BI-2 |
| BR-7 | `getUpdatePreparedSql` が `PreparedSql.of` に渡す値リストの結合順は **`setValues` ++（`bindFragments` から `buildBindWhere` が収集する値）** で固定する。この結合は 1 箇所（`PreparedSql` 組み立て直前）でのみ行う | ADR-011、WV-3 |
| BR-8 | `getDeletePreparedSql` / `getDeleteAllPreparedSql` の値は `buildBindWhere` が `bindFragments` から収集する値のみで構成する。DELETE に SET 句はないため `setValues` は関与しない | § 4 |
| BR-9 | `getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql` は WHERE の描画に共通の private ヘルパ `buildBindWhere(List<BindValue>)` を使う。U2 `getSelectPreparedSql()`（既にレビュー済みの成果物）自体は変更せず、U3 が新設する 3 メソッドの間でのみこのヘルパを共有する | § 4、AC-11（U2 への非介入） |
| BR-9a | `DBAccessManager.executeUpdate(PreparedSql, int, InputStream)` は `record` / `checkBindable` を U1 の `executeUpdate(PreparedSql)` / `executeQuery(PreparedSql, ResultSetHandler)` と同じ形で行う。`Dao` 側の転送メソッドは `DaoEvent` の生成、`ExecuteHandler` の前後呼び出し、`TransactionStateManager.getInstance().executed()`、`event.setResult` を省略しない——既存の 3 変種（`executeUpdate(String)` 等）が共通して行う手順であり、BLOB 経路だからといって省略しない（iteration 1 の blocking 指摘 B-7 の是正） | U1 § 7.1〜7.3 の踏襲、`Dao.java:257-274` |
| BR-10 | `setBinaryStream(blobIndex, in)` は `PreparedStatementBinder.bind` の**後**、`executeUpdate()` の**前**に呼ぶ。同一位置への複数回の `set*` 呼び出しは JDBC 仕様上「最後が有効」であり、`setNull` を確実に上書きする | Q1 = A の核心、JDBC 仕様 |
| BR-11 | `Dao.create` / `update` / `delete` のデフォルト実装は BLOB の有無を自動判定しない。BLOB を扱うサブクラスは現行と同じく `create(T)` / `update(T)` 自体を override し、`getInsertPreparedSql(data)` 等で `PreparedSql` を得て `getBindIndexOf(DataType.OBJECT, 1)` で位置を得て、`data` から取り出した `InputStream` とともに新しい `executeUpdate(PreparedSql, int, InputStream)` を呼ぶ。**現行も `Dao.create`/`update` のデフォルト実装は BLOB を扱っておらず**、サブクラスの override が必須である——この責任分担は変えない（iteration 1 の blocking 指摘 B-6 の是正。当初は `Dao` のデフォルト実装が自動的に分岐する設計だったが、`InputStream` の取得元を汎用的に知る方法がなく実現不可能だった） | 現行維持（`Dao.java:224-234` のデフォルトが `String` 版 `executeUpdate` のみを呼ぶ）、責任分担の保存 |
| BR-12 | リテラル版 `getUpdateSQL` の `:268`（OBJECT 分岐）に `.replaceFirst(tableName + ".", "")` を追加し、`:257` / `:262` と同じ形に揃える。これが U3 が触れる唯一のリテラル版コード変更である | FR-6.2、`component-methods.md` M-2 |
| BR-13 | `getDeletePreparedSql` / `getDeleteSQL` の例外送出（`updateColumns == null` のとき、テーブル名が解決できないとき）とテーブル名解決ロジックは現行のまま変えない | 現行維持 |
| BR-14 | `Dao.getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)`（`Dao` 独自の protected 拡張点）の既定実装は `PreparedSql.ofLiteral(getInsertSQL(data))` 等——旧い override 済みの `getXxxSQL(T)` をそのまま包む（ADR-004）。**`QueryImpl` の同名メソッドとは別のクラスの別のメンバ**であり、`FileDataDao.java:17-22` と同じ形の拡張点である | ADR-004、`component-methods.md` M-4、`FileDataDao.java` |
| BR-15 | `QueryImpl` の `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` は、`BindSqlBuilder` を**インスタンスフィールドとしてではなく各メソッドのローカル変数**として `new` する（`BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();`）。既存のリテラル版メソッドが `SQLParser parser` をローカル変数として使うパターンと同じ形であり、`Dao.bindSqlBuilder`（U2 のインスタンスフィールド、別物）と混同しない | `domain-entities.md`「この文書の範囲」、iteration 2 の blocking 指摘 B-3 の是正（未宣言の `bindSqlBuilder` 参照） |
| BR-16 | `Dao.executeUpdate(PreparedSql)`（1 引数版）は BR-9a が定める手順（`DaoEvent` 生成、`ExecuteHandler` 前後呼び出し、`TransactionStateManager.getInstance().executed()`、`event.setResult`）を **BLOB 版 `executeUpdate(PreparedSql, int, InputStream)` と独立に、両方とも本体を持つ**。片方だけ実装して他方を空のままにしない | iteration 2 の blocking 指摘 B-1 の是正（1 引数版が本体を持たなかった） |
| BR-17 | `DaoAdapter` は `Dao` を**継承しない**（`delegate: Dao<T>` フィールドを持つラッパークラス）ため、`executeUpdate(PreparedSql)` / `executeUpdate(PreparedSql, int, InputStream)` / `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` を `Dao` 側と**独立に**追加し、`delegate` へ転送する。`create` / `update` / `delete` も `delegate.executeUpdate(getXxxPreparedSql(data))` を呼ぶよう書き換える——`Dao` 側の変更だけでは実際の DAO（`DaoAdapter` を継承するもの）に新経路が届かない | `DaoAdapter.java:26`（`class DaoAdapter<T> implements AutoCloseable`）、`FileDataDao.java`、iteration 2 の blocking 指摘 B-2 の是正 |

---

## 失敗様式

### 失敗様式 1 — SET と WHERE の値が 1 つずつずれる

`unit-of-work.md` が「最大の設計リスク」と呼んだもの。値リストを 1 本にし、かつ生成順とテキスト連結順が食い違う実装をすると、`?` の個数は一致したまま**全ての値が 1 つずつずれてバインドされる**。個数検査（U1 PS-2）はこれを検出できない——個数は一致しているためである。

**検出**: `setValues` と `bindFragments` 由来の値を最後まで分離し、結合を 1 箇所（BR-7）に限定することで、この失敗様式が構造的に発生しなくなる。Build and Test（3.6）は「UPDATE の SET + WHERE 混在ケース」で、mock の位置・値アサート（AC-3b）により検出する。

### 失敗様式 2 — BLOB の位置ずれ

`getInsertPreparedSql` / `getUpdatePreparedSql` が `blobIndex` を誤った位置（例えば 0 始まりで計算する、または `position` のインクリメントを OBJECT 列自体でスキップする）で設定すると、`setBinaryStream` が誤った `?` を上書きし、意図しない列に BLOB が書き込まれるか、`IndexOutOfBoundsException` になる。

**検出**: `getBlobIndex()` が返す値と、`PreparedSql.getBindIndexOf(DataType.OBJECT, 1)`（U1）が独立に計算する値を突き合わせるテストで検出できる（両者は同じ値を返すべきという不変条件）。AC-7 がこれを判定する。

### 失敗様式 3 — 主キー述語のテキストと `Param` が食い違う

BR-4 の 3 引数 `addWhere` に渡すリテラル面（`getColumnName(col) + Condition.EQUAL.getCondition()`）とバインド面（`bindSqlBuilder.value(...)` の `Param`）は独立に導出される。両者が同じ列・同じ条件から作られていることを人間が確認する以外の機械的な保証はない——U2 の同期規則（単一の private メソッドで両面を同時更新する）ほど強い構造的保証ではなく、**呼び出し側が同じ入力を両方に渡す規律に依存する**。

**検出**: `getSelectSQL()` 相当の比較（`getUpdateSQL()` と `getUpdatePreparedSql()` の WHERE 述語数の突き合わせ、U2 の同期テストと同じ手法）を Build and Test（3.6）に送る。

---

## 残存リスク

| ID | 残存リスク | 由来 | 扱い |
|---|---|---|---|
| **R-1** | BLOB の `InputStream` 取得方法が利用側の責務のまま残る。U3 が正しく `executeUpdate(PreparedSql, int, InputStream)` を用意しても、呼び出し側が誤った列から `InputStream` を取り出せば意味をなさない | BR-11 | **受容。** 現行と同じ責任分担であり、U3 が新たに作るリスクではない |
| **R-2** | `getDeletePreparedSql` の主キー述語ロジック（BR-4）が `getUpdatePreparedSql` と重複している（コードの重複） | § 3.1、§ 4 の疑似コード | **受容。** U2 の `compose` 重複（U4 も同じ判断）と同種のトレードオフ。共通化には `Dao` / `QueryImpl` の構造変更が要り、局所パッチの範囲を超える |
| **R-3** | `bindSqlBuilder.placeholder` が OBJECT 列に対して `value` 引数を無視すること（U1 の `tokenFor` の挙動）に、U3 のコードが暗黙に依存している。この前提が変わると `getUpdatePreparedSql` の OBJECT 分岐が壊れる | U1 § 4.1 の `tokenFor` | **受容。** U1 の契約として `domain-entities.md` C-3「OBJECT カラムの扱い」に既に明記されている。U3 はこの契約に依存するが、U1 の成果物を変更しない |
| **R-4** | `getDeletePreparedSql` / `getUpdatePreparedSql` / `getDeleteAllPreparedSql` の 3 メソッドが共有する `buildBindWhere` ヘルパは、U2 `getSelectPreparedSql()` が内部で行う WHERE 描画ループ（U2 § 4.2）と**同じロジックを別の場所に複製したもの**である | BR-9、`business-logic-model.md` § 4 | **受容。** U2 の成果物（レビュー済み）を変更せずに再利用する術がなく、U4 の `compose` 重複と同種のトレードオフとして受け入れる。Build and Test（3.6）で両方に同じテスト観点を当てることで乖離を検出する |

---

## 判定できる受け入れ条件

| AC | 内容 | 本 Unit のどこが満たすか | 判定できる時点 |
|---|---|---|---|
| **AC-3b** | INSERT VALUES / UPDATE SET — カラム並び順どおりのバインド値。**不変条件 1（値リスト分離）を検出する唯一の AC** | `business-logic-model.md` § 2、§ 3（BR-1、BR-3、BR-7） | U3 完了時 |
| **AC-7** | OBJECT ＋ STRING 2 列の UPDATE — `getBlobIndex()` がバインド位置を返す | § 3、§ 7（BR-6） | U3 完了時 |

---

## Unit をまたぐ要件の遵守

| ID | 内容 | U3 の状態 |
|---|---|---|
| NFR-1 | 実行経路に値の文字列連結が 0 件 | ✅ SET 句・VALUES 句・WHERE 句のいずれも `bindSqlBuilder` / `Param` / `bindFragments` 経由でのみ値を運ぶ。WHERE のバインドテキストを `bindFragments` から描画する（BR-4a）ことで、リテラル埋め込みの `where` が実行経路に混入しない |
| NFR-3 | public API に破壊的変更なし | ✅ 新規 public / protected メンバのみ（`domain-entities.md` P-1、Pr-1〜Pr-10。`Dao` 側 5 件 + `DaoAdapter` 側 5 件、BR-17）。削除・シグネチャ変更なし |
| NFR-5 | 変更後の全テストがグリーン | `QueryImplTest` は書き込み系バインド版アサートを追加する必要がある（移行、BR-13 とは別に Build and Test 3.6 が担当） |
| NFR-6 | テスト件数が変更前を下回らない | ✅ 純増（AC-3b / AC-7 のテスト追加） |
| CON-1 | Java 8 | ✅ 使用する API は既存の `ArrayList` / `Collections` と U1 / U2 の型のみ |

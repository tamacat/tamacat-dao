# Domain Entities — U3 `write-path`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U3 の責務「INSERT / UPDATE / DELETE をバインド経路に切り替える。既存サブクラスが無変更で動き続ける互換シムを設ける」、境界（SELECT 経路・方言クラスには触れない）、依存 U1・U2、「最大の設計リスク」＝ SET → WHERE のテキスト連結順と値リストの生成順の対応。
- **`unit-of-work-story-map.md`**（同上）— U3 が担う FR-1.4 / 1.5 / 3.2 / 6.2、判定する AC-3b / AC-7。
- **`requirements.md`**（requirements-analysis 2.3）— FR-1.4、FR-1.5、FR-3.2、FR-6.2、AC-3b、AC-7、NFR-1、NFR-3、CON-1。
- **`component-methods.md`**（application-design 2.6）— M-2 の値アキュムレータ表（`setValues` / `insertValues` はメソッド呼び出しごとのローカル変数）、M-4 の追加・変更・不変。
- **U1 `bind-foundation` の 3.1 成果物** — `Param` / `PreparedSql` / `BindValue` の契約、`DBAccessManager` の実行機構、OBJECT カラムの扱い（`BindValue.ofNull(OBJECT)`、`PreparedStatementBinder` の `setNull`、呼び出し側の `setBinaryStream` による上書き）。
- **U2 `select-path` の 3.1 成果物** — `Query` の `default` 9 個の一括宣言、`bindFragments`（WHERE のバインド断片。`whereValues` という名のフィールドは存在しない）、`addWhere(String, String, Param)` の 3 引数内部形。

`functional-design-questions.md` の Q1 = A（`DBAccessManager.executeUpdate(PreparedSql, int, InputStream)` を新設）が本文書の前提である。

---

## この文書の範囲

**U3 は新しい public 型を 1 つも導入しない。** U1 の `Param` / `PreparedSql` / `BindValue`、U1 が新設した `BindSqlBuilder`（`org.tamacat.sql`）をそのまま使い、`QueryImpl` に override を足し、`DBAccessManager` / `Dao` / `DaoAdapter` に新規メンバを追加する。

**`QueryImpl` にフィールドは追加しないが、ビルドメソッドの内部にローカル変数として `BindSqlBuilder` を持つ（BR-15、iteration 2 の是正）。** `SQLParser parser = new SQLParser(valueConvertFilter);`（現行 `getInsertSQL`/`getUpdateSQL` の冒頭、`QueryImpl.java:186`、`:237`）と同じ流儀——呼び出しのたびに `new BindSqlBuilder()` する。U1 `BindSqlBuilder` は状態を持たない（U1 `components.md` C-5「所有する状態: なし」）ため、インスタンスフィールド化する必然性がなく、既存の `parser` ローカル変数パターンと形を揃える。**`Dao.bindSqlBuilder`（U2 が `Dao` に置いたインスタンスフィールド、U2 § 8.1）とは別の変数である**——`QueryImpl` と `Dao` は別のクラスであり、`QueryImpl` のビルドメソッドは `Dao` のフィールドにアクセスできない。

| 対象 | 新規フィールド | 新規メンバ |
|---|---|---|
| `QueryImpl` | なし（`blobIndex` は既存フィールドの意味を拡張するのみ、後述） | `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)`（`Query` の `default` を override するのみ。**`Query` 自体への追加は U2 が済ませ済み**）。`buildBindWhere(List<BindValue>)`（private ヘルパ） |
| `DBAccessManager` | なし | `executeUpdate(PreparedSql, int, InputStream)`（public） |
| `Dao` | なし | `executeUpdate(PreparedSql)`（protected。iteration 2 で追加——`component-methods.md` M-4 の定義済みメンバだが本体が未指定だった）。`executeUpdate(PreparedSql, int, InputStream)`（protected）。`getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)`（protected。**`QueryImpl` の同名メソッドとは別物**——`Dao` 独自の拡張点。`component-methods.md` M-4 が既に定義済み） |
| `DaoAdapter` | なし | `executeUpdate(PreparedSql)` / `executeUpdate(PreparedSql, int, InputStream)`（`delegate` へ転送）。`getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)`（protected。**`DaoAdapter` は `Dao` を継承しないため独立に必要**、iteration 2 で追加。`DaoAdapter` 自身の `getInsertSQL(T)` 等、`:152-162` と対になる） |

---

## `QueryImpl` が保持する書き込み系の値アキュムレータ（iteration 1 の是正）

U2 は WHERE の値を**インスタンスフィールド `whereValues` としては持たせていない**——WHERE のバインド面は `bindFragments`（`List<WhereFragment>`。`connector` と `Param` の対のリスト、U2 § 3.1）としてインスタンスに保持し、値は `getSelectPreparedSql()` 等が `bindFragments` を**描画するときにその場で収集する**（U2 § 4.2）。**「`whereValues` という名のインスタンスフィールド」は存在しない。** iteration 1 の本節はこの点を誤って前提していた。

**INSERT / UPDATE の値は各ビルドメソッドの呼び出しごとにローカルな `List<BindValue>` として持つ**（`component-methods.md` M-2）。WHERE 側の値は U3 が新設する `buildBindWhere(List<BindValue>)`（`business-logic-model.md` § 4）が `bindFragments` から都度収集する。

| 値リスト | 対になるテキスト | 生存期間 | 由来 |
|---|---|---|---|
| `insertValues` | `getInsertPreparedSql` の VALUES 句ローカル `values` | メソッド呼び出しごと | 新規（U3） |
| `setValues` | `getUpdatePreparedSql` の SET 句ローカル `setText` | メソッド呼び出しごと | 新規（U3） |
| `bindFragments` の値（`buildBindWhere` が収集） | `where` のバインド版（U2 が確立した `bindFragments`） | インスタンス（単回使用、`bindFragments` 自体の生存期間） | U2。U3 は読み取るのみで新設しない |

**`insertValues` / `setValues` をインスタンスフィールドにしない理由**: `getInsertSQL` / `getUpdateSQL`（リテラル版）は現行、呼び出しのたびに `StringBuilder` をローカルに新規生成しテキストを組み立てる（インスタンスの `where` のような蓄積型ではない）。バインド版もこの形を踏襲する——INSERT / UPDATE は SELECT の WHERE 句のように複数回の `and`/`or` 呼び出しで段階的に構築されるものではなく、`updateColumns` の 1 回のループで一括生成されるためである。

### 不変条件

| # | 不変条件 | 守り方 |
|---|---|---|
| WV-1 | `insertValues` の要素数と、`getInsertPreparedSql` が返す `PreparedSql` の VALUES 句の `?` の個数が一致する | `PreparedSql.of(...)` の生成時検査（U1 PS-2） |
| WV-2 | `setValues` の要素数と、SET 句の `?` の個数が一致する | 同上（`buildBindWhere` が返す WHERE の値との結合後、`PreparedSql.of` が結合後の全体で検査する） |
| WV-3 | **`getUpdatePreparedSql` の値の結合順は `setValues` ++（`bindFragments` から `buildBindWhere` が収集する値）**——テキストの連結順（`SET <values>` + `buildBindWhere` が返す WHERE テキスト）と一致する | ADR-011。`component-methods.md` M-2「この分離が必須である理由」 |
| WV-4 | `insertValues` / `setValues` は `updateColumns` を走査する**単一のループ**の中で、対応するテキストアキュムレータと**同一の `Param`（`p.getSql()` / `p.getValues()`）から**同時に生成される | § 2 / § 3（`business-logic-model.md`）。テキストと値がずれる余地を構造的に排除する。トークンが `"?"` とは限らない（`current_timestamp` 等）ため、`p.getValues()` の要素数（0 個または 1 個）に従う |
| WV-5 | WHERE のバインドテキストと値は、`bindFragments` を先頭から走査する**単一のループ**（`buildBindWhere`）の中で同時に生成される。`where.toString()`（リテラル）を SQL テキストに使わない | `business-logic-model.md` § 4「iteration 1 の是正」 |

**WV-3 が「最大の設計リスク」を解消する仕組み**: `unit-of-work.md` が指摘したリスク——`updateColumns` の登録順で主キーが 1 番目になる（`User.java`、`DefaultTable.java` の `LinkedHashSet`）ため WHERE の値が SET の値より先に生成される——は、値リストを 1 本にした場合にのみ発生する。`setValues` と `bindFragments` 由来の値を**別々に**保持し、結合を `PreparedSql` 組み立て時の 1 箇所（`setValues.addAll(bindWhereValues)` の順）に限定することで、生成順とテキスト連結順が食い違う経路がそもそも作られない。

---

## `blobIndex` — 二重の意味を持つ既存フィールド

`QueryImpl.blobIndex`（`int`、既存フィールド）は U3 で意味が拡張される。**フィールド自体は追加しない。**

| 呼び出し順 | 戻り値の意味 | 由来 |
|---|---|---|
| `getInsertSQL` / `getUpdateSQL`（旧・リテラル版）の後 | OBJECT カラムの**個数**（現行と同じ） | 現行 |
| `getInsertPreparedSql` / `getUpdatePreparedSql`（新・バインド版）の後 | OBJECT カラムのバインドパラメータ**位置**（1 始まり）。OBJECT がなければ `-1` | 新規（U3、FR-3.2） |
| いずれのビルドメソッドも呼ばれる前 | `0`（現行の初期値） | 現行 |

**同じフィールドが由来（どちらのビルドメソッドを最後に呼んだか）によって異なる意味を持つ、という設計は `PreparedSql` の checked/unchecked（U1 C-2）と同じ構造の「由来に依存する状態」である。** `component-methods.md` M-2 が既に「新旧で戻り値の意味が異なる点を明示する」と定めており、U3 はその契約をそのまま実装する。

### 不変条件

| # | 不変条件 |
|---|---|
| BI-1 | `getBlobIndex()` を呼ぶ前に、対応するビルドメソッド（新旧いずれか）を呼んでいなければならない。呼んでいない場合の戻り値 `0` は「BLOB なし」と区別できない（現行と同じ制約） |
| BI-2 | 新経路で `blobIndex` を設定するのは `PreparedSql.getBindIndexOf(DataType.OBJECT, 1)`（U1）の結果をそのまま使う。走査ロジックを U3 が再実装しない |
| BI-3 | `blobIndex == -1`（BLOB なし）のとき、通常の `executeUpdate(PreparedSql)`（U1）を使う。この判定と分岐は `Dao` のデフォルト実装では行わない——BLOB を扱うサブクラスが `create`/`update` を override して自ら判定する（`business-logic-model.md` § 6、iteration 1 の是正） |

---

## `DBAccessManager.executeUpdate(PreparedSql, int, InputStream)` — 新規実行メソッド

状態を持たない（`DBAccessManager` インスタンスの既存フィールドを使うのみ）。

### 契約

| 項目 | 内容 |
|---|---|
| 事前条件 | `sql` の `blobIndex` 番目の `BindValue` が `DataType.OBJECT` かつ `isNull() == true`（U1 の `BindSqlBuilder` が積んだプレースホルダ） |
| 処理 | `record(sql)` → `checkBindable(sql)` → `prepareStatement` → `PreparedStatementBinder.bind`（OBJECT 列は `setNull` される）→ `setBinaryStream(blobIndex, in)`（**NULL を上書き**）→ `executeUpdate()` → close（try-with-resources） |
| 事後条件 | 実行後 `PreparedStatement` は close 済み。呼び出し側は再利用できない（現行の `executeUpdate(String,int,InputStream)` と同じ制約） |

### `executeUpdate(PreparedSql)`（U1、BLOB なし版）との関係

**別メソッドとして共存する。**共通化（`InputStream` を `Optional` にする等）は行わない——U1 が確定した `executeUpdate(PreparedSql)` のシグネチャ・実装（`business-logic-model.md` § 7.1）は U3 が変更しない（`unit-of-work.md` U3 の境界「U1〜U2 が触るクラスへの変更は局所パッチに限る」）。

---

## 2.6 / 2.7 契約からの差分

**この節が差分の全量である。**

| # | 上流の記述 | 修正後 | 由来 |
|---|---|---|---|
| D-1 | `component-methods.md` M-5「追加」表が `executeQuery(PreparedSql)`（後に U1 が `ResultSetHandler` 付きに変更）と `executeUpdate(PreparedSql)` の 2 件のみを挙げる | `executeUpdate(PreparedSql, int, InputStream)` を 3 件目として追加する（BLOB 対応。Q1 = A） | Q1 = A |
| D-2 | 同上、`Dao` の M-4「追加」表は `executeUpdate(PreparedSql)` を「不変」の一部としてではなく前提として扱っており、本体を規定した成果物がなかった | `Dao.executeUpdate(PreparedSql)` の本体を本ステージで確定する（iteration 2 の是正） | iteration 2 の blocking B-1 |
| D-3 | `component-methods.md` M-4「追加」表・「不変」表のいずれも `DaoAdapter` を主語にした記述を持たない（`DaoAdapter` が `Dao` を継承しないという実装上の事実に触れていない） | `DaoAdapter` に `Dao` と対になる 5 メンバ（`executeUpdate(PreparedSql)` / `executeUpdate(PreparedSql,int,InputStream)` / `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)`）を独立に追加する | iteration 2 の blocking B-2 |

**新規 public メンバ**（FR-3.1 の互換維持対象に加わるもの）:

| # | メンバ | 所在 |
|---|---|---|
| P-1 | `DBAccessManager.executeUpdate(PreparedSql, int, InputStream)` | `org.tamacat.sql.DBAccessManager` |

**新規 protected メンバ**（AC-11 の判定対象）:

| # | メンバ | 所在 |
|---|---|---|
| Pr-1 | `Dao.executeUpdate(PreparedSql, int, InputStream)` | `org.tamacat.dao.Dao` |
| Pr-2 | `Dao.executeUpdate(PreparedSql)` | `org.tamacat.dao.Dao`（iteration 2 で追加） |
| Pr-3 | `Dao.getInsertPreparedSql(T)` | `org.tamacat.dao.Dao`（`component-methods.md` M-4「追加」が既に予告済み。本ステージが実装する） |
| Pr-4 | `Dao.getUpdatePreparedSql(T)` | 同上 |
| Pr-5 | `Dao.getDeletePreparedSql(T)` | 同上 |
| Pr-6 | `DaoAdapter.executeUpdate(PreparedSql, int, InputStream)` | `org.tamacat.dao.DaoAdapter`（iteration 2 で追加） |
| Pr-7 | `DaoAdapter.executeUpdate(PreparedSql)` | 同上 |
| Pr-8 | `DaoAdapter.getInsertPreparedSql(T)` | 同上 |
| Pr-9 | `DaoAdapter.getUpdatePreparedSql(T)` | 同上 |
| Pr-10 | `DaoAdapter.getDeletePreparedSql(T)` | 同上 |

`QueryImpl` の `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql` は `Query` の `default` の override であり、**新規メンバではない**（U2 が `Query` に一括宣言済み）。`Dao` / `DaoAdapter` の同名メソッド（Pr-3〜Pr-5、Pr-8〜Pr-10）はこれとは**別のクラスの別のメンバ**であり、`FileDataDao.java:17-22` の現行 `getInsertSQL(T)` override と同じ形の拡張点である。**`DaoAdapter` は `Dao` を継承しない**（`DaoAdapter.java:26`、`implements AutoCloseable` のみ）ため、Pr-6〜Pr-10 は Pr-1〜Pr-5 の継承による自動反映ではなく、独立に追加が必要なメンバである。

**削除・シグネチャ変更**: なし。

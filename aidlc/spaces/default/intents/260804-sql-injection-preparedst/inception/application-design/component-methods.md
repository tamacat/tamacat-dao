# Component Methods — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

本文書は `components.md` が定めたコンポーネント境界に対して、**公開インターフェースのメソッドシグネチャ**を与える。詳細な業務規則は Functional Design（3.1）の担当であり、ここでは目的・入出力・エラー処理方針までを定める。

- **`requirements.md`**（requirements-analysis, 2.3）— FR-1〜FR-8 と NFR-3（再コンパイル不要）。本文書のシグネチャはすべて「追加のみ、既存の削除・変更なし」という FR-3.1 の制約下にある。
- **`architecture.md`**（codekb）— 現行の実行面（Execution Surface）と `SQLParser` の 2 プリミティブ。新メソッドはこれに並置される。
- **`component-inventory.md`**（codekb）— 既存メソッドの所在（行番号）。「不変」と記した既存シグネチャはこの一覧のものを指す。
- **`stories.md`**（user-stories, 2.4）/ **`team-practices`**（practices-discovery, 2.2）— 本スコープで **SKIP** のため存在しない。

型名は設計上の呼称であり、実装時に確定する（`decisions.md` ADR-001 参照）。

---

## 記法

- **不変** — 現行のシグネチャと戻り値の意味を一切変更しない
- **非推奨** — `@Deprecated` を付けるが、シグネチャも戻り値の中身も変更しない
- **追加** — 新規メソッド。既存メソッドの削除・変更を伴わない

エラー処理は現行の funnel に従う。値の検証違反は `InvalidParameterException`（`IllegalArgumentException` のサブクラス）、JDBC 由来の失敗は `DaoException`（`RuntimeException` のサブクラス）に包む。この方針は現行と同一である。

---

## C-1. `Param`（`org.tamacat.dao`）

述語断片とその値。不変オブジェクト。

| メソッド | 目的 | 入出力 | エラー |
|---|---|---|---|
| `String getSql()` | `?` を含む述語断片テキストを返す | → `String`（非 null） | なし |
| `List<BindValue> getValues()` | 断片中の `?` に対応する値を出現順に返す | → 不変 `List`（空リストあり） | なし |
| `int size()` | 値の個数を返す | → `int` | なし |
| `static Param of(String sql, List<BindValue> values)` | 生成する | → `Param` | `sql` が null、または `?` の個数と `values` の要素数が不一致なら `InvalidParameterException` |
| `Param and(Param other)` | 2 つの断片を `and` で連結し、値の並びも連結する | → 新しい `Param` | `other` が null なら自身を返す |
| `Param or(Param other)` | 同上（`or`） | → 新しい `Param` | 同上 |

**`?` 個数と値個数の整合検査**を生成時に行うのが、この型を設ける主目的である。この検査があるため、FR-1 の実装ミス（テキストと値のずれ）がテスト以前に検出される。

---

## C-2. `PreparedSql`（`org.tamacat.dao`）

実行できる完成文とその値。不変オブジェクト。

| メソッド | 目的 | 入出力 | エラー |
|---|---|---|---|
| `String getSql()` | `?` を含む完成した SQL 文を返す | → `String`（非 null） | なし |
| `List<BindValue> getValues()` | `?` に対応する値を出現順に返す | → 不変 `List` | なし |
| `int getBindIndexOf(DataType type, int occurrence)` | 指定の型の n 番目のバインド位置（1 始まり）を返す | → `int`、該当なしは `-1` | なし |
| `static PreparedSql of(String sql, List<BindValue> values)` | 生成する | → `PreparedSql` | `?` の個数と `values` の要素数が不一致なら `InvalidParameterException` |
| `static PreparedSql ofLiteral(String sql)` | **値ゼロ**の `PreparedSql` を作る互換シム。`?` の個数検査を**行わない** | → `PreparedSql` | `sql` が null なら `InvalidParameterException` |

`ofLiteral` は F2 の互換経路で使う。旧 `getInsertSQL(T)` だけを override している既存サブクラスの戻り値をそのまま包み、新しい実行経路に流す。**この経路を通った SQL はリテラル埋め込みのままであり、SM-1 の達成対象ではない**（`decisions.md` ADR-004 参照）。

**`ofLiteral` が `?` 個数検査を行わない理由と、それが生む危険**:

旧経路が生成する SQL は、BLOB カラム（`DataType.OBJECT`）を含む場合に**未バインドの `?` を含む**（`SQLParser.java:112-113` が `"?"` を返し、`QueryImpl.java:268` がそれを SET 句に置く）。この `?` は、現行では `Query.getBlobIndex()` で位置を取り `Dao.executeUpdate(String, int, InputStream)` 経由で `setBinaryStream` により束縛される設計である。したがって `ofLiteral` が「`?` の個数 = 値の個数」を強制すると、BLOB を含む旧経路の SQL がすべて `InvalidParameterException` で落ちる。

一方、検査を行わないと、**BLOB を含む旧 SQL が `Dao.create(T)` / `update(T)` 経由で `prepareStatement` に渡り、`?` が未束縛のまま `execute` される**。現行はこれを `Statement.executeUpdate` に渡しており、実 DB では構文または実行時エラーになる（`architecture.md` の Execution Surface が記録するとおり、旧 `Dao.update(T)` は BLOB を扱えない）。挙動は「実 DB でエラー」から「JDBC ドライバがパラメータ未設定を報告」に変わるが、**どちらも失敗する**という点は同じである。

**設計上の扱い**: `PreparedSql` に `hasUnboundPlaceholders()`（テキストの `?` 個数 > 値の個数 なら真）を持たせ、`DBAccessManager` の `PreparedSql` 版実行メソッドが真の場合に `DaoException` を投げて**早期に失敗させる**。エラーメッセージは、BLOB を含む更新は `Dao.executeUpdate(String, int, InputStream)`（旧 BLOB 経路）か、新しい `getUpdatePreparedSql` の override を使うべき旨を示す。現行の「実 DB まで到達してから失敗する」より診断しやすくなる。

なお `?` の数え方については、テキスト中の `?` が常にプレースホルダである（引用符内のリテラル `?` が現れない）という前提が要る。この前提の根拠は `component-dependency.md` の「`?` 個数の数え方に関する注意」を参照。**旧経路の SQL はこの前提を満たさない可能性がある**（値がリテラルとして埋め込まれており、値の中に `?` を含みうる）。したがって `hasUnboundPlaceholders()` の判定は、**`ofLiteral` で作られた `PreparedSql`（値ゼロかつ検査未実施）に限り、引用符の外にある `?` のみを数える**。この走査規則は Functional Design（3.1）で確定する。

`getBindIndexOf` は FR-3.2（`getBlobIndex()`）の実装基盤である。BLOB は `DataType.OBJECT` の 1 番目として引ける。

---

## C-3. `BindValue`（`org.tamacat.dao`）

1 個のバインド値。不変オブジェクト。

| メソッド | 目的 | 入出力 | エラー |
|---|---|---|---|
| `DataType getType()` | 値の型を返す | → `DataType` | なし |
| `String getValue()` | 文字列表現の値を返す（`DataType.OBJECT` では null） | → `String`（null 可） | なし |
| `InputStream getStream()` | バイナリ値を返す（`DataType.OBJECT` のみ） | → `InputStream`（null 可） | なし |
| `boolean isNull()` | SQL NULL としてバインドすべきかを返す | → `boolean` | なし |
| `static BindValue of(DataType type, String value)` | 生成する | → `BindValue` | `type` が null なら `InvalidParameterException` |
| `static BindValue ofStream(InputStream in)` | `DataType.OBJECT` の値を生成する | → `BindValue` | `in` が null なら `InvalidParameterException` |
| `static BindValue ofNull(DataType type)` | SQL NULL を表す値を生成する | → `BindValue` | なし |

`isNull()` を型に持たせる理由: 現行は `DataType` ごとに「空文字を `null` として扱う」「`"NULL"` を `null` として扱う」といった規則がある（FR-2.4）。この判定を `ValueRules` で行い、結果を `BindValue` に固定することで、バインド時に再判定しない。

---

## C-4. `ValueRules`（`org.tamacat.sql`）

型検証と LIKE エスケープの共通規則。状態を持たない。

| メソッド | 目的 | 入出力 | エラー |
|---|---|---|---|
| `static void validate(Column column, String value)` | FR-2.1 の型検証。NUMERIC / FLOAT に非数値が来たら拒否する | → なし | 非数値なら `InvalidParameterException("value is not numeric.")` |
| `static boolean isNumeric(String value)` | 数値判定（現行 `SQLParser.isNumeric` と同一の正規表現） | → `boolean` | なし |
| `static boolean isNullValue(Column column, String value)` | FR-2.4 の空値・`NULL` 判定 | → `boolean` | なし |
| `static boolean isSqlFunction(Column column, String value)` | FR-2.4 の `current_timestamp` 判定（値ではなく SQL 関数として発行すべきか） | → `boolean` | なし |
| `static boolean isRequiredButEmpty(Column column, String value)` | not-null カラムに空値が来たかを判定（現行 `SQLParser.value:45-47` の規則） | → `boolean` | なし |
| `static LikeEscape escapeLike(Conditions condition, Column column, String value)` | FR-2.3 の LIKE 値の組み立て — ワイルドカードのラップ、`%` / `_` のエスケープ、エスケープ文字の選択 | → `LikeEscape`（バインドすべき値 + エスケープ文字、または「エスケープ不要」） | なし |

**引数に `Conditions` が必要な理由**: `%` の位置は条件ごとに異なる。`Condition.java:11-13` が定める `LIKE_HEAD("#{value1}%")` / `LIKE_PART("%#{value1}%")` / `LIKE_TAIL("%#{value1}")` の 3 種があり、現行 `SQLParser.parseLikeStringValue` は `condition.getReplaceHolder().replace(VALUE1, val)`（`:129`、および `:138`）でこのラップを適用している。`value` だけを受け取る設計では `LIKE_PART` に対して `%Tama%` を作れない。

**現行の処理順序と、バインド版での対応**（`SQLParser.java:124-138`）:

| 現行の順序 | 処理 | バインド版での扱い |
|---|---|---|
| 1（`:124`） | 値に `%` / `_` が含まれるかを判定 | 同じ |
| 2（`:125-127`） | `$ # ~ ! ^` から、値に含まれない最初の文字をエスケープ文字 `e` に選ぶ | 同じ |
| 3（`:128`） | 値の中の `%` → `e%`、`_` → `e_` に置換 | 同じ |
| 4（`:129`） | 条件の `replaceHolder` を適用し、**フレームワーク自身の `%` ラッパー**を外側に付ける | 同じ。**このラッパーはエスケープしない**（現行と同じ） |
| 5（`:130`） | `ValueConvertFilter` でクォートをエスケープ | **行わない**（FR-2.2） |
| 6（`:131-133`） | STRING / BOOLEAN なら `'...'` で囲む | **行わない**（バインドするため） |
| 7（`:134`） | `ESCAPE.replace('?', e)` で `escape 'e'` 句を作る | テキスト側に置く（`BindSqlBuilder` が使う） |

**バインドされる値は、手順 4 まで適用した文字列である。** すなわち `LIKE_PART` に `"Tam%a"` を渡すと、エスケープ文字 `$` が選ばれ、バインド値は `%Tam$%a%` になり、テキストは `col like ? escape '$'` になる。外側の `%` はフレームワークのラッパーであり、現行どおりエスケープされない。

**「エスケープ不要」ケース**: 値に `%` / `_` が含まれない場合は現行の `:138` の経路に相当し、`escape` 句を付けない。この場合でも**条件のラップは適用する**（`:138` が `condition.getReplaceHolder().replace(VALUE1, value)` を渡していることに対応）。`LIKE_PART` に `"Tama"` を渡せばバインド値は `%Tama%` である。

**5 候補枯渇のケース**: 値が `$ # ~ ! ^` のすべてを含む場合、現行はループを抜けて `:138` に落ち、`%` / `_` が未エスケープのまま出力される。バインド版もこの挙動に従う（FR-2.3 の「現行の挙動を維持する」）。この挙動を明示的に固定するテストは FR-8.5 が追加する。

**型ゲートの維持**: 現行の LIKE 分岐は `column.getType()` が STRING または BOOLEAN のときにのみ入る（`SQLParser.java:48-50`）。NUMERIC / DATE / TIME カラムに `LIKE_*` を渡すと汎用分岐に落ちてワイルドカードエスケープを受けない。この型ゲートは `BindSqlBuilder` 側で維持する（FR-2.3）。

---

## C-5. `BindSqlBuilder`（`org.tamacat.sql`）

プレースホルダ形式の述語生成。`SQLParser.value(...)` のバインド版。

| メソッド | 目的 | 入出力 | エラー |
|---|---|---|---|
| `Param value(Column column, Conditions condition, String... values)` | 1 つの述語を `?` 付きテキストと値の対で生成する | → `Param` | not-null カラムに空値なら `InvalidParameterException`、型検証違反も同様 |
| `Param placeholder(Column column, String value)` | 値 1 個ぶんの `?` と `BindValue` を生成する（INSERT の VALUES 用） | → `Param` | 同上 |
| `Param sqlFunction(Column column, String function)` | `current_timestamp` 等、値ではなく SQL 関数として発行する要素を生成する（値ゼロ） | → `Param` | なし |

**`sqlFunction` を分ける理由**（FR-2.4）: `current_timestamp` はバインドしてはならない。バインドすると文字列リテラルとして解釈され、意味が変わる。呼び出し側が `ValueRules.isSqlFunction` で判定し、このメソッドに振り分ける。

**`Conditions` の扱い**（FR-5.3）: `condition.getReplaceHolder()` のトークン（`#{value1}` / `#{value2}` / `#{values}`）を `?` に置換する。`IN` の `(#{values})` は値の個数ぶんの `?` をカンマ区切りで展開する（FR-1.3）。`BETWEEN` の `#{value1} and #{value2}` は `? and ?` になる。`IS_NULL` / `NOT_NULL` は `replaceHolder` が null であり、値ゼロの `Param` を返す。

**`ValueConvertFilter` を呼ばない**（FR-2.2）: バインド経路ではクォートエスケープは不要かつ有害である。`BindSqlBuilder` はこのフィルタを保持しない。

---

## C-6. `PreparedStatementBinder`（`org.tamacat.sql`、package-private）

| メソッド | 目的 | 入出力 | エラー |
|---|---|---|---|
| `static void bind(PreparedStatement stmt, List<BindValue> values)` | 値を 1 始まりの位置に順に適用する | → なし | `SQLException` を `DaoException` に包む |

`DataType` から JDBC の setter を選ぶ対応:

| `DataType` | 適用する setter |
|---|---|
| STRING, BOOLEAN | `setString` |
| NUMERIC, FLOAT | `setString`（ドライバに型解釈を委ねる。現行のリテラル経路と同じ挙動を保つため） |
| DATE, TIME | `setString` |
| OBJECT | `setBinaryStream` |
| FUNCTION | 到達しない（`sqlFunction` で値ゼロとして扱われるため） |
| いずれも `isNull()` が真 | `setNull` |

**NUMERIC / FLOAT に `setString` を使う理由**: 現行のリテラル経路は数値を検証したうえで**引用符なしのまま**テキストに埋め込んでおり、実際の型変換は DB エンジンが行っている。`setBigDecimal` 等に変えると、現行と異なる型変換が起きうる。FR-2.1 の型検証は `ValueRules.validate` が担うため、setter 側で型を絞る必要はない。この選択は `decisions.md` ADR-005 に記録する。

---

## C-7. `ExecutedStatement`（`org.tamacat.sql`）

| メソッド | 目的 | 入出力 | エラー |
|---|---|---|---|
| `String getSql()` | 実行された SQL テキストを返す | → `String` | なし |
| `List<BindValue> getValues()` | その実行に渡された値を返す | → 不変 `List` | なし |

---

## M-1. `Query`（interface, `org.tamacat.dao`）

### 追加（`default` メソッド）

| メソッド | 目的 | 既定実装 |
|---|---|---|
| `default PreparedSql getSelectPreparedSql()` | パラメータ化された SELECT を返す | `PreparedSql.ofLiteral(getSelectSQL())` |
| `default PreparedSql getInsertPreparedSql(T data)` | 同 INSERT | `PreparedSql.ofLiteral(getInsertSQL(data))` |
| `default PreparedSql getUpdatePreparedSql(T data)` | 同 UPDATE | `PreparedSql.ofLiteral(getUpdateSQL(data))` |
| `default PreparedSql getDeletePreparedSql(T data)` | 同 DELETE | `PreparedSql.ofLiteral(getDeleteSQL(data))` |
| `default PreparedSql getDeleteAllPreparedSql(Table table)` | 同 DELETE ALL | `PreparedSql.ofLiteral(getDeleteAllSQL(table))` |
| `default Query<T> where(Param param)` | 述語断片を WHERE に追加する | `where(param.getSql())`（値は失われる） |
| `default Query<T> and(Param param)` | 同（`and` 連結） | `and(param.getSql())` |
| `default Query<T> or(Param param)` | 同（`or` 連結） | `or(param.getSql())` |

**既定実装が値を捨てる設計の理由**: `Query` を実装する外部クラス（リポジトリ内には存在しないが、OQ-5 により存在可能性は否定できない）は、これらの `default` を override していない。既定実装が例外を投げると、そうした実装は新しい `Dao` の実行経路で必ず失敗する。リテラルにフォールバックすれば**現行と同じ挙動で動き続ける**。安全側に倒すのではなく互換側に倒す判断であり、`decisions.md` ADR-006 に記録する。

### 非推奨（シグネチャ・戻り値とも不変）

`getSelectSQL()` / `getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)` / `getDeleteAllSQL(Table)` に `@Deprecated`。

### 不変

上記以外の 25 メソッド（`select` / `distinct` / `addUpdateColumn(s)` / `removeUpdateColumns` / `getSelectColumns` / `getUpdateColumns` / `addTable` / `removeFromTables` / `join` / `outerJoin` / `andOuterJoin` / `where(Search,Sort)` / `and(Search,Sort)` / `or(Search,Sort)` / `where(String)` / `and(String)` / `or(String)` / `andIn` / `andNotIn` / `andExists` / `andNotExists` / `groupBy` / `orderBy` / `getBlobIndex` / `getTimestampString` / `setUseAutoPrimaryKeyUpdate` / `autoPrimaryKeyUpdate`）。

`getBlobIndex()` の**戻り値の意味**は FR-3.2 により「BLOB カラムのバインドパラメータ位置」と定義される。実装は `getUpdatePreparedSql` / `getInsertPreparedSql` が組み立てた `PreparedSql` に対する `getBindIndexOf(DataType.OBJECT, 1)` の結果を返す。F6 によりリポジトリ内に呼び出し元がないため、この定義がリポジトリ内の何かを壊すことはない。

---

## M-2. `QueryImpl`（`org.tamacat.dao.impl`）

`Query` の新 `default` メソッドをすべて override する。加えて内部メソッドを追加・変更する。

| メソッド | 種別 | 目的 |
|---|---|---|
| `getSelectPreparedSql()` ほか 4 つ | override | 節ごとの値リストをテキストの連結順に結合して `PreparedSql` を返す（下記） |
| `where(Param)` / `and(Param)` / `or(Param)` | override | テキストを `where` に、値を `whereValues` に、同じ順序で追加する |
| `protected Query<T> addWhere(String condition, Param param)` | 追加 | 既存 `addWhere(String,String)`（`:442-453`）のバインド版。`useAutoPrimaryKeyUpdate = false` の副作用も同じく持つ |
| `protected Query<T> addSearch(String condition, Search search, Sort sort)` | 変更 | `search.getSearchString()` ではなく `getSearchParam()`（テキストと値の対）を使う（F4） |
| `andIn` / `andNotIn` / `andExists` / `andNotExists` | 変更 | 子 `Query` の `getSelectPreparedSql()` を使い、子の値を `whereValues` の**差し込み位置**に連結する（FR-1.6） |

### 値アキュムレータ（OQ-2 の解、順序保証の中核）

`QueryImpl` は値リストを **節ごとに分けて**保持する。テキストアキュムレータと 1 : 1 で対応させる。

| 値リスト | 対になるテキスト | 生存期間 |
|---|---|---|
| `whereValues` | `where`（`QueryImpl.java:48`） | インスタンス。リセットされない（CON-7 の単回使用と同じ） |
| `setValues` | `getUpdatePreparedSql` の SET 句ローカル `values` | メソッド呼び出しごと |
| `insertValues` | `getInsertPreparedSql` の VALUES 句ローカル `values` | メソッド呼び出しごと |

`PreparedSql` を組むときは、**テキストの連結順と同じ順で値リストを結合する**。

| メソッド | テキストの連結 | 値の結合 |
|---|---|---|
| `getSelectPreparedSql()` | SELECT 句 + FROM/JOIN + `where` | `whereValues` |
| `getInsertPreparedSql(T)` | `INSERT ... VALUES (<values>)` | `insertValues` |
| `getUpdatePreparedSql(T)` | `UPDATE ... SET <values>` + `where` | `setValues` ++ `whereValues` |
| `getDeletePreparedSql(T)` | `DELETE ...` + `where` | `whereValues` |
| `getDeleteAllPreparedSql(Table)` | `DELETE ...` + `where` | `whereValues` |

**この分離が必須である理由**: `getUpdateSQL`（`:236-274`）は SET 句と WHERE 句を単一のループで組み立てるが、テキストは最後に `query + where.toString()`（SET → WHERE）で連結する。ループの反復順は `updateColumns` の登録順であり、**ライブラリ自身のフィクスチャでは主キーが 1 番目**（`User.java:17, :23-24`、`DefaultTable.java:16` の `LinkedHashSet`）。したがって WHERE の値が SET の値より先に生成される。値リストを 1 本にすると `?` の順序と逆になり、**個数は一致したまま全ての値が 1 つずつずれる**。詳細と失敗例は `component-dependency.md` の「順序の不変条件」を参照。

**FR-6.2 の修正**: `:268` の OBJECT ブランチに `.replaceFirst(tableName + ".", "")` を適用し、SET 句のテーブル修飾を他カラムと揃える。バインド版・リテラル版の両方で修正する。

### `getBlobIndex()` の生成タイミング（FR-3.2）

現行の `blobIndex` フィールドは `getInsertSQL` / `getUpdateSQL` の実行中に設定され（`:189, :212-214, :240, :269`）、`getBlobIndex()`（`:469-471`）がそれを返す。すなわち**ビルドメソッドを呼んだ後でなければ意味のある値が得られない**という順序依存が現行から存在する。

新設計もこの契約を維持する。`getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` が `PreparedSql` を組んだ時点で、その `PreparedSql` に対する `getBindIndexOf(DataType.OBJECT, 1)` の結果を `blobIndex` フィールドにキャッシュし、`getBlobIndex()` はそれを返す。

| 呼び出し順 | 戻り値 |
|---|---|
| ビルドメソッドより前 | `0`（現行の初期値と同じ） |
| `getInsertPreparedSql` / `getUpdatePreparedSql` の後 | その文における BLOB カラムのバインドパラメータ位置（1 始まり）、BLOB がなければ `-1` |
| 旧 `getInsertSQL` / `getUpdateSQL` の後 | 現行と同じ挙動（OBJECT カラムの個数） |

**旧経路と新経路で戻り値の意味が異なる点を明示する。** 旧メソッドは現行どおり `blobIndex++` によるカウントを設定し、新メソッドはパラメータ位置を設定する。F6 によりリポジトリ内に呼び出し元がないため、この差異がリポジトリ内の何かを壊すことはない。旧経路の意味は現行と同一であるため FR-3.1 も満たす。この二重の意味は Javadoc で明示する。

---

## M-3. `Search`（`org.tamacat.dao`）

| メソッド | 種別 | 目的 |
|---|---|---|
| `and(Column, Conditions, String...)` | **シグネチャ不変**、内部変更 | `SQLParser` でリテラル系テキストを、`BindSqlBuilder` でバインド系テキストと値を得て、**3 つすべてを 1 回の呼び出しで蓄積する** |
| `or(Column, Conditions, String...)` | 同上 | 同上 |
| `and(Search)` / `or(Search)` | 変更 | 相手の**リテラル系テキスト・バインド系テキスト・値リストの 3 つすべて**を、同一の連結規則で取り込む |
| `Param getSearchParam()` | 追加（**public**） | バインド系テキストと値を対で返す。`QueryImpl.addSearch` が使う。`getSearchString()` のバインド版に相当する |
| `getSearchString()` | 非推奨、**戻り値不変** | リテラル埋め込みの WHERE 断片を返し続ける |
| `unique` / `isUnique` / `start` / `max` / `getStart` / `getMax` / `setStart` / `setMax` | 不変 | — |

### `Search` が保持する 3 つの状態と、その同期規則

`Search` は次の 3 つを保持する。

| 状態 | 内容 | 参照元 |
|---|---|---|
| `search`（既存、`Search.java:23`） | リテラル埋め込みの述語テキスト | `getSearchString()`（非推奨） |
| `bindSearch`（新規） | `?` を含む述語テキスト | `getSearchParam()`（public） |
| `bindValues`（新規） | `bindSearch` 中の `?` に対応する値 | `getSearchParam()` |

**同期規則（不変条件）**: `search` を変更するすべての操作は、同じ呼び出しの中で `bindSearch` と `bindValues` も変更する。3 つのうち 1 つだけを変更する経路を作らない。

これが破られると、`bindSearch` の `?` 個数と `bindValues` の要素数がずれる。`Param.of(...)` の個数検査（後述の検査 1）はこのずれを**検出する**が、検出できるのは個数の不一致だけであり、「両方とも append し忘れた」場合は個数が一致したまま述語が欠落する。したがって同期規則は検査ではなく**構造で**守る。具体的には、`search` / `bindSearch` / `bindValues` を直接触るのは 1 つの private メソッド（`append(String literal, Param bind, String connector)`）だけとし、`and` / `or` / `and(Search)` / `or(Search)` はすべてそれを経由する。

**連結規則の対応**（`Search.java:40-66` の 4 メソッド）:

| メソッド | リテラル系 | バインド系テキスト | 値リスト |
|---|---|---|---|
| `and(Column, Conditions, String...)` | ` and ` + 述語 | ` and ` + `?` 付き述語 | 述語の値を末尾に連結 |
| `or(Column, Conditions, String...)` | ` or ` + 述語 | ` or ` + `?` 付き述語 | 同上 |
| `and(Search)` | ` and (` + 相手の `search` + `)` | ` and (` + 相手の `bindSearch` + `)` | 相手の `bindValues` を末尾に連結 |
| `or(Search)` | ` or (` + 相手の `search` + `)` | ` or (` + 相手の `bindSearch` + `)` | 同上 |

括弧の付与位置がリテラル系とバインド系で同一であるため、`?` の出現順と値の順序は一致する。

**テキストを 2 系統保持する代償**: メモリ上の重複は述語テキストぶんであり、実用上の問題にはならない。この冗長性は FR-3.1 を厳密に守るために受け入れる（`decisions.md` ADR-003）。

---

## M-4. `Dao` / `DaoAdapter`（`org.tamacat.dao`）

### 追加

| メソッド | 可視性 | 目的 | エラー |
|---|---|---|---|
| `Param prepare(Column column, Conditions condition, String... values)` | public | `param()` のバインド版。`BindSqlBuilder.value(...)` に委譲する | `InvalidParameterException` |
| `PreparedSql getInsertPreparedSql(T data)` | protected | 新しい拡張点。既定実装は `PreparedSql.ofLiteral(getInsertSQL(data))` | 旧 override 未実装なら `RuntimeException(NoSuchMethodException)`（現行と同じ） |
| `PreparedSql getUpdatePreparedSql(T data)` | protected | 同上 | 同上 |
| `PreparedSql getDeletePreparedSql(T data)` | protected | 同上 | 同上 |
| `ResultSet executeQuery(PreparedSql sql)` | protected | `DBAccessManager` の `PreparedSql` 版に委譲し、実行イベントを発火する | `DaoException` |
| `int executeUpdate(PreparedSql sql)` | protected | 同上 | `DaoException` |

`DaoAdapter` は同名の public / protected メソッドを持ち、`delegate` に転送する（現行の `param` / `getInsertSQL` 等と同じ形）。

### 変更（シグネチャ不変）

| メソッド | 変更内容 |
|---|---|
| `search(Query<T>)` | `query.getSelectPreparedSql()` を使う |
| `searchList(Query<T>, int, int)` | 同上 |
| `create(T)` / `update(T)` / `delete(T)` | `getInsertPreparedSql(data)` 等を呼び、`executeUpdate(PreparedSql)` で実行する |

### 不変

`param(Column, Conditions, String...)`、旧 `getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)`、`executeQuery(String)` / `executeUpdate(String)` / `executeUpdate(String,int,InputStream)`、`createQuery` / `createSearch` / `createSort`、`mapping`、`handleException`、イベント系。

**既存サブクラスの経路（NFR-3 の担保）**:
```
Dao.create(data)
  → getInsertPreparedSql(data)            [新・既定実装]
      → getInsertSQL(data)                [旧・サブクラスの override]
      → PreparedSql.ofLiteral(...)        [値ゼロ]
  → executeUpdate(PreparedSql)            [新]
      → DBAccessManager.executeUpdate(PreparedSql)
          → 値ゼロなので prepareStatement して即 execute
```
再コンパイルは不要であり、生成される SQL も現行と同一である。

---

## M-5. `DBAccessManager`（`org.tamacat.sql`）

### 追加

| メソッド | 可視性 | 目的 | エラー |
|---|---|---|---|
| `ResultSet executeQuery(PreparedSql sql)` | public | `prepareStatement` → `PreparedStatementBinder.bind` → `executeQuery` | `DaoException` |
| `int executeUpdate(PreparedSql sql)` | public | 同（`executeUpdate`） | `DaoException` |
| `List<ExecutedStatement> getExecutedStatements()` | public | バインド値を含む実行記録を返す（FR-4.1） | なし |

新しい実行メソッドは、先頭で `getExecutedQuery().add(sql.getSql())` と `getExecutedStatements().add(...)` の**両方**に記録する。これにより既存 e2e テストのアサーション経路（`getExecutedQuery()`）が生き続ける。

### 不変

`executeQuery(String)` / `executeUpdate(String)` / `preparedStatement(String)` / `getStatement()` / `createStatement()` / `getExecutedQuery()` / `commit` / `rollback` / `release` / `getInstance` / `close`。

`getExecutedQuery()` は `List<String>` を返し続け、SQL テキストのみを保持する（Q6 = B）。

---

## M-6. `SQLParser`（`org.tamacat.sql`）

| メソッド | 種別 | 変更内容 |
|---|---|---|
| `value(Column, Conditions, String...)` | 非推奨、**戻り値不変** | 内部で `ValueRules` に委譲する。出力は現行と 1 文字も変わらない |
| `parseValue(Column, String)` | 非推奨、**戻り値不変** | 同上 |
| `parseLikeStringValue(Conditions, Column, String)` | 内部変更 | `ValueRules.escapeLike` に委譲する |
| `parseMultiValue(Column, String, String...)` | 不変 | — |
| `isNumeric(String)` | 内部変更 | `ValueRules.isNumeric` に委譲する |
| コンストラクタ 2 種 | 不変 | `ValueConvertFilter` の保持と適用は現行どおり |

---

## M-7. 方言コンポーネント

| コンポーネント | メソッド | 変更内容 |
|---|---|---|
| `MySQLDao` | `searchList(Query,int,int)` | `PreparedSql` 経路を使う。LIMIT を `" limit ?,?"` にしてオフセットと件数をバインドする（FR-6.3） |
| `MySQLDao` | `createQuery()` / `createSearch()` | 不変。フィルタの注入は旧経路のために残す |
| `OracleDao` | `searchListForOracle` | 修正して有効化し、`searchList` から呼ばれるようにする。rownum の `?` をバインドし、`max` をページング範囲の決定に使う（FR-6.1） |
| `OracleSearch.OracleValueConvertFilter` | `convertValue(String)` | null ガードを追加。null なら null を返す（他 2 実装と同じ挙動、FR-6.4） |
| `MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition` | — | 不変 |

---

## C-8. mock スタック拡張（`org.tamacat.mock.sql`）

| コンポーネント | メソッド | 変更内容 |
|---|---|---|
| `MockConnection` | `prepareStatement(String sql)`（`:168-170`） | `sql` を破棄せず `MockPreparedStatement` に渡す |
| `MockPreparedStatement` | 各 `setXxx(int, ...)` | 位置と値を内部に記録する |
| `MockPreparedStatement` | `getPreparedSql()` | 追加。`prepareStatement` に渡された SQL を返す |
| `MockPreparedStatement` | `getBoundValue(int index)` | 追加。指定位置にバインドされた値を返す |
| `MockPreparedStatement` | `getBoundValues()` | 追加。位置順の値一覧を返す |
| `MockPreparedStatement` | `executeUpdate()`（`:41-44`） | `0` を返す現行挙動は変更しない（既存テストへの影響を避けるため） |

これにより FR-8.1 の受け入れ条件（バインド位置と値をアサートできる）が満たされる。

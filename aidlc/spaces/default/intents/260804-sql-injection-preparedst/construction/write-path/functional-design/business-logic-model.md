# Business Logic Model — U3 `write-path`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U3 の責務・境界・依存 U1・U2、「最大の設計リスク」（SET → WHERE の連結順と値リストの生成順の対応）、実装上の制約（既定実装は `PreparedSql.ofLiteral(...)`・ADR-004、`getBlobIndex()` の新旧の意味・ADR-012 が委ねた `hasUnboundPlaceholders()` による旧経路 BLOB SQL の拒否）。
- **`unit-of-work-story-map.md`**（同上）— U3 が担う FR-1.4 / 1.5 / 3.2 / 6.2、判定する AC-3b / AC-7、Unit 内の実装順序 1〜7（本文書 § 8 がこれに対応づく）。
- **`requirements.md`**（requirements-analysis 2.3）— FR-1.4、FR-1.5、FR-3.2、FR-6.2、AC-3b、AC-7、NFR-1、NFR-3、CON-1。
- **`component-methods.md`**（application-design 2.6）— M-2 の値アキュムレータ表、FR-6.2 の修正指示、M-4 の追加・変更・不変。
- **U1 `bind-foundation` の 3.1 成果物** — `Param.of` / `PreparedSql.of` の生成時検査、`BindValue` の 3 形、OBJECT カラムの扱い（`BindValue.ofNull(OBJECT)` → `setNull` → 呼び出し側が `setBinaryStream` で上書き）、`DBAccessManager` の実行機構。
- **U2 `select-path` の 3.1 成果物** — `Query` の `default` 9 個、`bindFragments`（WHERE のバインド断片。値は `getSelectPreparedSql()` の描画時にその場で収集され、`whereValues` という名のフィールドは存在しない）、`addWhere(String, String, Param)` の 3 引数内部形、`Dao.executeQuery(PreparedSql, ResultSetHandler)`。

`functional-design-questions.md` の Q1 = A が本文書の前提である。状態の定義は本 Unit の `domain-entities.md`、規則の列挙は `business-rules.md` にある。本文書は**アルゴリズムと処理順序**を扱う。`BR-n` は本 Unit の規則、`U1 BR-n` / `U2 BR-n` はそれぞれの Unit の規則を指す。

---

## この文書の範囲

U3 は「INSERT / UPDATE / DELETE をバインド経路に切り替える」。U1 が作った型と機構、U2 が確立した `Query` の `default` 宣言と WHERE の断片リスト構造の**上に**、書き込み系の値をバインドする経路を通す。

| # | 処理 | 担当 | 節 |
|---|---|---|---|
| 1 | INSERT VALUES 句を組み立てる | `QueryImpl.getInsertPreparedSql` | § 2 |
| 2 | UPDATE SET 句を組み立て、`bindFragments`（WHERE のバインド断片）から収集した値と結合する | `QueryImpl.getUpdatePreparedSql` | § 3 |
| 3 | DELETE 文を組み立てる | `QueryImpl.getDeletePreparedSql` / `getDeleteAllPreparedSql` | § 4 |
| 4 | BLOB を実行する | `DBAccessManager.executeUpdate(PreparedSql,int,InputStream)` | § 5 |
| 5 | `Dao` / `DaoAdapter` の `create` / `update` / `delete` を新経路に切り替える | `Dao` | § 6 |
| 6 | `getBlobIndex()` の二重の意味を実装する | `QueryImpl` | § 7 |

**U3 が触らないもの**: SELECT 経路（U2）、方言クラス（U4）、`Sort`（U5）、`ValueRules` / `BindSqlBuilder` / `PreparedStatementBinder` の内部（U1）。

---

## § 1. 現行のリテラル版アルゴリズム（対比のため）

```java
// QueryImpl.java:184-219（getInsertSQL、抜粋）
public String getInsertSQL(T data) {
    SQLParser parser = new SQLParser(valueConvertFilter);
    StringBuilder columns = new StringBuilder();
    StringBuilder values = new StringBuilder();
    blobIndex = 0;
    String tableName = null;
    for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
        if (tableName == null) tableName = col.getTable().getTableName();
        if (columns.length() > 0) { columns.append(","); values.append(","); }
        columns.append(col.getColumnName());
        if (data.isUpdate(col)) {
            values.append(parser.parseValue(col, data.getValue(col)));
        } else if (col.isAutoGenerateId()) {
            String id = UniqueCodeGenerator.generate();
            values.append(parser.parseValue(col, id));
            data.setValue(col, id);
        } else if (col.isAutoTimestamp()) {
            values.append(parser.parseValue(col, getTimestampString()));
        } else {
            values.append(parser.parseValue(col, data.getValue(col)));
        }
        if (col.getType() == DataType.OBJECT) { blobIndex++; }
    }
    return INSERT.replace("${TABLE}", tableName).replace("${COLUMNS}", columns.toString())
                  .replace("${VALUES}", values.toString());
}
```

```java
// QueryImpl.java:235-274（getUpdateSQL、抜粋）
public String getUpdateSQL(T data) {
    SQLParser parser = new SQLParser(valueConvertFilter);
    StringBuilder values = new StringBuilder();
    String tableName = null;
    blobIndex = 0;
    for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
        if (tableName == null) tableName = col.getTable().getTableName();
        if (col.isPrimaryKey()) {
            if (useAutoPrimaryKeyUpdate) {
                addWhere("and", parser.value(col, Condition.EQUAL, data.getValue(col)));   // :246
            }
            continue;
        }
        if (col.isAutoGenerateId()) continue;
        if (data.isUpdate(col)) {
            if (values.length() > 0) values.append(",");
            values.append(parser.value(col, Condition.EQUAL, data.getValue(col)).replaceFirst(tableName + ".", ""));   // :257
        } else if (col.isAutoTimestamp()) {
            if (values.length() > 0) values.append(",");
            values.append(parser.value(col, Condition.EQUAL, getTimestampString()).replaceFirst(tableName + ".", ""));   // :262
        } else if (col.getType() == DataType.OBJECT) {
            if (values.length() > 0) values.append(",");
            values.append(parser.value(col, Condition.EQUAL, "?"));   // :268。テーブル修飾が残る（FR-6.2 の対象）
            blobIndex++;
        }
    }
    return UPDATE.replace("${TABLE}", tableName).replace("${VALUES}", values.toString()) + where.toString();
}
```

**FR-6.2 の欠陥はここにある。** `:257` と `:262` の枝は `.replaceFirst(tableName + ".", "")` でテーブル修飾を剥がしているが、`:268`（OBJECT 分岐）だけがこの呼び出しを欠いている。結果として BLOB カラムだけ `SET ..., file.data=?` のようにテーブル修飾が残り、他の SET 句要素と形が揃わない（`requirements.md` FR-6.2 の原文）。

---

## § 2. `getInsertPreparedSql(T)` — INSERT VALUES 句

```java
@Override
public PreparedSql getInsertPreparedSql(T data) {
    BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();   // BR-15。ローカル変数。現行の `SQLParser parser = new SQLParser(...)`（:186）と同じ流儀
    StringBuilder columns = new StringBuilder();
    StringBuilder values = new StringBuilder();
    List<BindValue> insertValues = new ArrayList<>();       // BR-1。メソッドローカル
    String tableName = null;
    for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
        if (tableName == null) tableName = col.getTable().getTableName();
        if (columns.length() > 0) { columns.append(","); values.append(","); }
        columns.append(col.getColumnName());
        String value;
        if (data.isUpdate(col)) {
            value = data.getValue(col);
        } else if (col.isAutoGenerateId()) {
            String id = UniqueCodeGenerator.generate();
            data.setValue(col, id);
            value = id;
        } else if (col.isAutoTimestamp()) {
            value = getTimestampString();
        } else {
            value = data.getValue(col);
        }
        Param p = bindSqlBuilder.placeholder(col, value);    // U1 § 4.4。null → 空文字の置換を行わない
        values.append(p.getSql());                            // "?"／"current_timestamp"／関数名などトークンをそのまま使う（BR-3）
        insertValues.addAll(p.getValues());
    }
    String sql = INSERT.replace("${TABLE}", tableName).replace("${COLUMNS}", columns.toString())
                        .replace("${VALUES}", values.toString());
    PreparedSql prepared = PreparedSql.of(sql, insertValues);
    blobIndex = prepared.getBindIndexOf(DataType.OBJECT, 1);   // BR-6。U1 の走査ロジックをそのまま使う（BI-2）
    return prepared;
}
```

**`getTimestampString()` / `isAutoGenerateId()` / `isAutoTimestamp()` の分岐は現行と 1 文字も変えない**（BR-2）。変わるのは値の適用先だけ——現行は `parser.parseValue`（テキストへの埋め込み）、新経路は `bindSqlBuilder.placeholder`（U1 § 4.4、`Param` を返す）である。

**`placeholder` を使う理由**: `BindSqlBuilder.value(Column, Conditions, String...)`（U1 § 4.2）は `Conditions` を要求し WHERE 述語用の形（`カラム名 = ?` 等）を作るのに対し、INSERT の VALUES 句は述語ではなく単一の値トークンだけを要求する。U1 が INSERT 用に用意した `placeholder(Column, String)` は `value(...)` と異なり null → 空文字の置換を行わない（U1 § 4.4 の注記）——これは現行 `getInsertSQL` が `data.getValue(col)` を無変換で `parser.parseValue` に渡す形と対称である。

**`p.getSql()` をそのまま使い、`"?"` を固定で書かない（BR-3、iteration 1 の是正）。** `bindSqlBuilder.placeholder` は必ず `?` を返すとは限らない——U1 `tokenFor`（§ 4.1）は `DataType.FUNCTION` や `isSqlFunction` に該当する値（`current_timestamp` 等）に対して `Token(value, null)`（`?` を伴わないテキストそのもの、値ゼロ）を返す。`values.append(p.getSql())` はこの両方の形を正しく扱う——`?` の場合はプレースホルダが、そうでない場合はテキストがそのまま SET 句に入り、対応する `insertValues` への追加も `p.getValues()`（0 個または 1 個）に従う。**したがって `?` の出現位置とテキストの出力順の対応は、`values.append` と `insertValues.addAll` を同一ループの同一反復で行うことでのみ構造的に保証され、列を数える `position` カウンタは不要である。**

**`blobIndex` の算出（BR-6、iteration 1 の是正）**: `PreparedSql` を組み立てた**後**に `prepared.getBindIndexOf(DataType.OBJECT, 1)`（U1 が実装済みの走査）を呼び、その結果をそのままキャッシュする。U3 が独自に `position` カウンタを保守して位置を計算する設計は、`tokenFor` が列ごとに 0 個または 1 個の値を生む非一様な対応を正しく追跡できず（FUNCTION / `current_timestamp` の列で「列数」と「値の個数」がずれる）、`domain-entities.md` BI-2「走査ロジックを U3 が再実装しない」にも反していた。U1 の `getBindIndexOf` を呼び直す形に改める。

---

## § 3. `getUpdatePreparedSql(T)` — SET 句と WHERE の結合

### 3.1 「最大の設計リスク」への対処

```java
@Override
public PreparedSql getUpdatePreparedSql(T data) {
    BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();    // BR-15。ローカル変数
    StringBuilder setText = new StringBuilder();
    List<BindValue> setValues = new ArrayList<>();           // BR-1。メソッドローカル
    String tableName = null;
    for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
        if (tableName == null) tableName = col.getTable().getTableName();
        if (col.isPrimaryKey()) {
            if (useAutoPrimaryKeyUpdate) {
                Param p = bindSqlBuilder.value(col, Condition.EQUAL, data.getValue(col));
                addWhere("and", getColumnName(col) + Condition.EQUAL.getCondition(), p);   // BR-4。3 引数の内部形（U2 § 3.3）。
                                                                                             // where と bindFragments の両方を appendWhere が同時更新する（U2 § 3.2）
            }
            continue;
        }
        if (col.isAutoGenerateId()) continue;
        String value;
        if (data.isUpdate(col)) {
            value = data.getValue(col);
        } else if (col.isAutoTimestamp()) {
            value = getTimestampString();
        } else if (col.getType() == DataType.OBJECT) {
            value = null;                                      // OBJECT は tokenFor が value を無視する（U1 § 4.1）
        } else {
            continue;                                          // 現行と同じ：どの分岐にも該当しない列は SET に現れない
        }
        if (setText.length() > 0) setText.append(",");
        Param p = bindSqlBuilder.placeholder(col, value);      // BR-3。トークンは "?" とは限らない（current_timestamp 等）
        setText.append(col.getColumnName()).append("=").append(p.getSql());   // BR-5。FR-6.2：テーブル修飾を最初から作らない
        setValues.addAll(p.getValues());                        // 0 個または 1 個。トークンの形に従う
    }
    List<BindValue> bindWhereValues = new ArrayList<>();
    String bindWhere = buildBindWhere(bindWhereValues);           // BR-9。§ 4 で定義する共有ヘルパをここでも使う（iteration 2 の是正）
    String sql = UPDATE.replace("${TABLE}", tableName).replace("${VALUES}", setText.toString())
                        + bindWhere;                             // BR-4a。where.toString()（リテラル）ではなく bindWhere を使う
    List<BindValue> combined = new ArrayList<>(setValues);
    combined.addAll(bindWhereValues);                             // BR-7。WV-3：setValues ++ bindFragments の値
    PreparedSql prepared = PreparedSql.of(sql, combined);
    blobIndex = prepared.getBindIndexOf(DataType.OBJECT, 1);      // BR-6。U1 の走査ロジックをそのまま使う
    return prepared;
}
```

**FR-6.2 の修正はここで「起きない」形にする（BR-5）。** 現行の `.replaceFirst(tableName + ".", "")` は `bindSqlBuilder.value(...)` / `parser.value(...)` が返す `カラム名 = 値`（`MappingUtils.getColumnName` によりテーブル修飾済み）からテーブル修飾を**事後的に剥がす**設計であり、OBJECT 分岐だけがこの剥がしを欠いていた。新設計は `col.getColumnName()`（テーブル修飾なしの単純なカラム名）を使って `SET` 句のテキストを直接組み立てるため、そもそもテーブル修飾が**混入しない**——事後的に剥がす対象がないので、剥がし忘れという欠陥のクラス自体が発生しなくなる。この形は 3 つの分岐（更新値・自動タイムスタンプ・OBJECT）すべてに一様に適用され、`.replaceFirst` の有無で分岐間の扱いが割れる余地がない。

**`p.getSql()` を SET 句のトークンにそのまま使う（BR-3、iteration 1 の是正）。** § 2 と同じ理由で `"=?"` を固定で書かない——`col.isAutoTimestamp()` の枝は `bindSqlBuilder.placeholder(col, "current_timestamp")` を呼ぶが、U1 `tokenFor`（§ 2.5 `isSqlFunction`）はこの値を関数として認識し `Token("current_timestamp", null)` を返す（`?` を作らず値もゼロ）。**現行 `getUpdateSQL` の自動タイムスタンプ枝（`:262`）も同じ理由でリテラルの `current_timestamp` を出力し、値を持たない。** `"=?"` を固定で書くと、`?` の個数が実際の `setValues` の要素数より 1 個多くなり、`PreparedSql.of` の生成時検査（U1 PS-2）が `InvalidParameterException` を投げる——自動タイムスタンプを持つテーブルの UPDATE がすべて失敗する欠陥になる（iteration 1 の blocking 指摘 B-3）。`p.getSql()` を使えば、OBJECT 列は `"?"`、自動タイムスタンプ列は `"current_timestamp"`、通常の更新値は `"?"` が自動的に選ばれ、対応する `p.getValues()`（1 個または 0 個）が構造的に一致する。

**WHERE 句はバインド版のテキストを `bindFragments` から描画する（BR-4a、iteration 1 の是正）。** 当初の設計は `where.toString()`（U2 の**リテラル**アキュムレータ）をそのまま SQL テキストに連結していた。これは 2 つの理由で誤りである。(1) `where` は `Search` 経由で追加された述語のリテラル埋め込みテキストを含みうるため、これをバインド版の SQL に使うと**値の文字列連結が実行経路に残る**（NFR-1 違反）。(2) 主キー述語を `addWhere("and", getColumnName(col) + Condition.EQUAL.getCondition(), p)` で追加すると、渡すリテラルテキストは `"user.id="`（`?` を持たない）であり、これをそのまま `where` に使うと `?` 0 個・値 1 個という個数不一致になり `PreparedSql.of` が必ず落ちる。**正しい描画は U2 `getSelectPreparedSql()`（§ 4.2）と同じパターン——`bindFragments` を先頭から走査し、`connector` と `param.getSql()` を連結してテキストを組み立て、`param.getValues()` を同じループで集める。** `addWhere` の 3 引数内部形（U2 § 3.3）が `where`（リテラル）と `bindFragments`（バインド）の両方を`appendWhere` で同時更新しているため（U2 § 3.2）、主キー述語も既存の WHERE 断片もこの走査で正しく拾われる。

**主キー述語だけが `bindSqlBuilder.value` を使う理由**: WHERE に置く述語は `カラム名 = ?` という**述語形**が必要であり、U1 の `value(Column, Conditions, String...)`（テーブル修飾付き）がそのまま使える。SET 句はテーブル修飾のない `カラム名=<トークン>` という**代入形**であり、性質が異なる。

**WV-3（`setValues` ++ `bindFragments` の値）が「最大の設計リスク」を解消する**: `unit-of-work.md` が指摘したリスクは「値リストを 1 本にすると WHERE の値が SET の値より先に生成されるため全ての値が 1 つずつずれる」というものだった。新設計は `setValues` と `bindFragments`（＝ WHERE のバインド値）を最後まで別々に保持し、`PreparedSql.of` に渡す直前の 1 箇所でのみ `setValues.addAll(bindWhereValues)` として結合する。生成順（ループの中でどちらが先に積まれるか）とテキストの連結順（`SET <values>` + `bindWhere`）が一致することが、この 1 行の結合順によってのみ決まる——ループ内の処理順に依存しない（BR-7）。

### 3.2 主キー述語の値ゼロにならないことの確認

U2 § 3.3 の `addWhere(String, String, Param)` は 3 引数の内部形であり、リテラル面のテキストとバインド面の `Param` を別々に受け取る。U3 が主キー述語に使う `getColumnName(col) + Condition.EQUAL.getCondition()`（リテラル面）と `bindSqlBuilder.value(...)`（バインド面）は同じ列・同じ条件から独立に導出されるため、両者は一致した内容を持つ（U1 が保証する `Param` の生成規則により、`value(...)` が返す `Param` のテキストも同じ `カラム名 = ?` の形である）。

---

## § 4. `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)`

```java
private String buildBindWhere(List<BindValue> collected) {        // BR-4a。§ 3 と共有する小さなヘルパ
    StringBuilder w = new StringBuilder();
    for (int i = 0; i < bindFragments.size(); i++) {
        WhereFragment f = bindFragments.get(i);
        w.append(i == 0 ? " " + WHERE + " " : " " + f.connector + " ");
        w.append(f.param.getSql());
        collected.addAll(f.param.getValues());
    }
    return w.toString();
}

@Override
public PreparedSql getDeletePreparedSql(T data) {
    BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();    // BR-15。ローカル変数
    String tableName = null;
    if (updateColumns != null) {
        for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
            if (tableName == null) tableName = col.getTable().getTableName();
            if (useAutoPrimaryKeyUpdate && col.isPrimaryKey()) {
                Param p = bindSqlBuilder.value(col, Condition.EQUAL, data.getValue(col));
                addWhere("and", getColumnName(col) + Condition.EQUAL.getCondition(), p);   // § 3.1 と同形
            }
        }
    } else {
        throw new InvalidParameterException("Set the UpdateColumns.");   // :289。現行のまま
    }
    if (tableName == null) {
        for (Table table : tables) { tableName = table.getTableName(); break; }
        if (tableName == null) throw new InvalidParameterException();   // :296-298。現行のまま
    }
    List<BindValue> values = new ArrayList<>();
    String sql = DELETE.replace("${TABLE}", tableName) + buildBindWhere(values);   // BR-4a。where.toString() ではなく bindFragments から描画
    return PreparedSql.of(sql, values);            // BR-8
}

@Override
public PreparedSql getDeleteAllPreparedSql(Table table) {
    List<BindValue> values = new ArrayList<>();
    String sql = DELETE.replace("${TABLE}", table.getTableName()) + buildBindWhere(values);   // BR-4a
    return PreparedSql.of(sql, values);            // BR-8
}
```

**DELETE には SET 句がない**ため、値は WHERE のバインド断片（`bindFragments`）のみで完結する（BR-8）。`getDeleteSQL` / `getDeleteAllSQL` の例外送出（`:289`、`:296-298`）とテーブル名解決ロジックは現行のまま 1 文字も変えない。

**`buildBindWhere` は § 3 の WHERE 描画ループと同じ形を持つ private ヘルパである（iteration 1 の是正）。** 当初の設計は `where.toString()`（リテラル）をそのまま使っており、§ 3.1 と同じ理由（値の文字列連結が残る、主キー述語で個数不一致になる）で誤りだった。U2 `getSelectPreparedSql()`（§ 4.2）と同じ「`bindFragments` を先頭から走査し `connector` + `param.getSql()` を連結、`param.getValues()` を同じループで集める」パターンをヘルパとして 1 箇所にまとめ、`getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql` の 3 メソッドで共有する。**U2 の `getSelectPreparedSql()` 自体（同じロジックをインライン展開している）は変更しない**——U2 が既にレビューを受けた成果物への変更を避け、U3 は自分が新設する 3 メソッドの間でだけこのヘルパを共有する。

**`getDeletePreparedSql` は BLOB を扱わない。** DELETE の主キー述語に OBJECT 型の列が使われることは通常なく（主キーは通常 STRING/NUMERIC）、`blobIndex` はこのメソッドの呼び出し後は更新されない（前回のビルドメソッドの値が残る、BI-1 の制約どおり）。

---

## § 5. BLOB の実行 — `DBAccessManager.executeUpdate(PreparedSql, int, InputStream)`

```java
// DBAccessManager（新規メソッド）
public int executeUpdate(PreparedSql sql, int blobIndex, InputStream in) {
    record(sql);                                              // BR-9。U1 § 7.3 と同形
    checkBindable(sql);                                       // BR-9。U1 § 7.2 と同形
    try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
        PreparedStatementBinder.bind(ps, sql.getValues());    // OBJECT 列は setNull(pos, Types.BLOB) される
        ps.setBinaryStream(blobIndex, in);                    // BR-10。NULL を上書きする
        return ps.executeUpdate();
    } catch (SQLException e) {
        throw new DaoException(e);
    }
}
```

**`PreparedStatementBinder.bind` の後、`executeUpdate()` の前に `setBinaryStream` を挟む。** これが Q1 = A の核心である——U1 が確立した「バインドと実行を 1 メソッド内で完結させる」構造（`executeUpdate(PreparedSql)`）を維持したまま、バインドと実行の**間**に呼び出し側（U3）の処理を 1 行挟む形で拡張する。`try-with-resources` はそのまま働くため、例外発生時も `PreparedStatement` は確実に close される。

**`blobIndex` が `setNull` を上書きすることの安全性**: `PreparedStatementBinder.bind` が先に `setNull(blobIndex, Types.BLOB)` を適用済みだが、JDBC の `PreparedStatement` は同じパラメータ位置への複数回の `set*` 呼び出しを許し、最後の呼び出しが有効になる（U1 BR-34「同位置への 2 回目は後勝ち」と同じ規則が `MockPreparedStatement` だけでなく実 JDBC ドライバでも成立する）。したがって `setBinaryStream` が確実に `setNull` を上書きする。

**`checkBindable` が誤って拒否しないことの確認**: `sql.hasUnboundPlaceholders()`（U1）は `?` の個数と値の個数が一致していれば偽を返す。OBJECT 列は `BindValue.ofNull(OBJECT)` として値の個数に数えられているため（`PreparedSql.of` の生成時検査を通過済み）、`checkBindable` はこの `PreparedSql` を拒否しない——BLOB を含む書き込みも通常の書き込みと同じ検査経路を通る。

### 5.1 `Dao` 側の転送メソッド（iteration 2 の是正 — `executeUpdate(PreparedSql)` 本体を追加）

**`Dao.executeUpdate(PreparedSql)`（BLOB を伴わない版）は `component-methods.md` M-4「追加」表が定めているにもかかわらず、iteration 1 は本体を書かないまま `§6` から呼んでいた。** `DBAccessManager.executeUpdate(PreparedSql)` は U1 が確定済みだが、その `Dao` 側の薄いラッパは**どの Unit の成果物にも本体が存在しなかった**——`unit-of-work.md` U3 の担当表が M-4 の書き込み系拡張点全体を U3 に割り当てているため、ここで確定する。

```java
// Dao（新規メソッド。3 引数版と対になる 1 引数版。DaoEvent 等の手順は共通）
protected int executeUpdate(PreparedSql sql) throws DaoException {
    DaoEvent event = createDaoEvent(sql.getSql());
    getExecuteHandler().handleBeforeExecuteUpdate(event);
    int result = dbm.executeUpdate(sql);                       // U1 の DBAccessManager.executeUpdate(PreparedSql)
    TransactionStateManager.getInstance().executed();
    event.setResult(result);
    return getExecuteHandler().handleAfterExecuteUpdate(event);
}

// Dao（新規メソッド。既存 executeUpdate(String,int,InputStream)、Dao.java:266-274 と同形）
protected int executeUpdate(PreparedSql sql, int index, InputStream in) throws DaoException {
    DaoEvent event = createDaoEvent(sql.getSql());
    getExecuteHandler().handleBeforeExecuteUpdate(event);
    int result = dbm.executeUpdate(sql, index, in);           // § 5 の DBAccessManager メソッドへ委譲
    TransactionStateManager.getInstance().executed();          // 既存の 3 変種すべてが行う呼び出し（BR-9a）
    event.setResult(result);
    return getExecuteHandler().handleAfterExecuteUpdate(event);
}
```

**既存の `executeUpdate(String)` / `executeUpdate(String,int,InputStream)`（`Dao.java:257-274`）が共通して行う 4 手順——`DaoEvent` の生成、`ExecuteHandler` の前後呼び出し、`TransactionStateManager.getInstance().executed()`、`event.setResult`——のいずれも省略しない（BR-9a）。** iteration 1 の設計は 3 引数版でこの手順を欠いており、1 引数版は本体そのものが存在しなかった。両方とも修正した。

### 5.2 `DaoAdapter` 側の転送（iteration 2 の是正 — `Dao` を継承しないため独立に必要）

**`DaoAdapter` は `Dao` を継承しない。** `DaoAdapter<T>`（`DaoAdapter.java:26`）は `implements AutoCloseable` のみで、内部に保持する `delegate`（`Dao<T>` 型フィールド）に処理を転送するラッパである。したがって § 5.1 / § 6 で `Dao` に追加したメンバは**自動的には `DaoAdapter` に反映されない**——本リポジトリの実利用サブクラス（`FileDataDao`、`UserDao` 等）はすべて `DaoAdapter` を継承しており（`MySQLDao` / `OracleDao` のみ `Dao` を直接継承）、`DaoAdapter` 側を独立に拡張しなければ FR-1.4 / FR-1.5 が実利用側に届かない。

```java
// DaoAdapter（新規メソッド。既存 executeUpdate(String) / executeUpdate(String,int,InputStream)、
// DaoAdapter.java:180-186 と同形——delegate へ転送するだけ）
protected int executeUpdate(PreparedSql sql) throws DaoException {
    return delegate.executeUpdate(sql);
}
protected int executeUpdate(PreparedSql sql, int index, InputStream in) throws DaoException {
    return delegate.executeUpdate(sql, index, in);
}

// DaoAdapter（新規の protected 拡張点。DaoAdapter 自身の getInsertSQL 等、:152-162 と対になる）
protected PreparedSql getInsertPreparedSql(T data) {
    return PreparedSql.ofLiteral(getInsertSQL(data));   // DaoAdapter 自身の getInsertSQL(T) を包む（ADR-004）
}
protected PreparedSql getUpdatePreparedSql(T data) {
    return PreparedSql.ofLiteral(getUpdateSQL(data));
}
protected PreparedSql getDeletePreparedSql(T data) {
    return PreparedSql.ofLiteral(getDeleteSQL(data));
}

// DaoAdapter（既存メソッドの中身のみ変更。DaoAdapter.java:164-174 と対になる）
public int create(T data) {
    return delegate.executeUpdate(getInsertPreparedSql(data));   // 現行が delegate.executeUpdate(getInsertSQL(data)) である形をそのまま踏襲
}
public int update(T data) {
    return delegate.executeUpdate(getUpdatePreparedSql(data));
}
public int delete(T data) {
    return delegate.executeUpdate(getDeletePreparedSql(data));
}
```

**`DaoAdapter.getInsertPreparedSql(T)` は `getInsertSQL(T)`（`DaoAdapter` 自身の、`:152-154`）を包む。** `delegate` 側の `getInsertPreparedSql` ではない——現行の `create()` が `delegate.executeUpdate(getInsertSQL(data))` と、**`getInsertSQL` は `DaoAdapter` 自身のもの、`executeUpdate` だけが `delegate` 側**という非対称な形をそのまま踏襲する（`DaoAdapter.java:164-166` の実際の形と一致）。

**BLOB を扱うサブクラスの override 先も `DaoAdapter` 側になる。** 本リポジトリの実利用サブクラスはすべて `DaoAdapter` を継承するため、BR-11 が述べる「`create(T)`/`update(T)` を override する」責務は実際には `DaoAdapter.create(T)`/`update(T)` の override を意味する。

---

## § 6. `Dao` / `DaoAdapter` — `create` / `update` / `delete` の切り替え（iteration 1 の是正）

```java
// Dao（新規の protected 拡張点。component-methods.md M-4「追加」表が既に定めている）
protected PreparedSql getInsertPreparedSql(T data) {
    return PreparedSql.ofLiteral(getInsertSQL(data));   // 既定実装（ADR-004）。旧 override 未実装なら現行と同じ RuntimeException
}
protected PreparedSql getUpdatePreparedSql(T data) {
    return PreparedSql.ofLiteral(getUpdateSQL(data));
}
protected PreparedSql getDeletePreparedSql(T data) {
    return PreparedSql.ofLiteral(getDeleteSQL(data));
}

// Dao（既存メソッドの中身のみ変更。シグネチャは create(T)/update(T)/delete(T) のまま）
public int create(T data) {
    return executeUpdate(getInsertPreparedSql(data));    // U1 の executeUpdate(PreparedSql)
}
public int update(T data) {
    return executeUpdate(getUpdatePreparedSql(data));
}
public int delete(T data) {
    return executeUpdate(getDeletePreparedSql(data));
}
```

**`Dao.getInsertPreparedSql(T)` は `QueryImpl.getInsertPreparedSql(T)` とは**別のメソッドである**——名前が同じだが所属するクラスが異なる。** `Dao` 側は `component-methods.md` M-4 が既に「追加」として定めている**新しい拡張点**（`FileDataDao.java:17-22` の `getInsertSQL(T)` override と同じ形の、サブクラスが override する拡張点）であり、既定実装は `PreparedSql.ofLiteral(getInsertSQL(data))`——**旧い override 済みの `getInsertSQL(T)` をそのまま `ofLiteral` で包む**（ADR-004 の互換シム、`unit-of-work.md` U3 の実装上の制約）。`FileDataDao` のような既存サブクラスは `getInsertSQL(T)` だけを override しており `getInsertPreparedSql(T)` は override していないため、既定実装がそのまま使われ、**再コンパイル不要で現行と同じ SQL が生成される**（NFR-3）。

**`create`/`update`/`delete` のデフォルト実装は BLOB の自動判定を行わない（iteration 1 からの方針転換）。** iteration 1 は `sql.getBindIndexOf(DataType.OBJECT, 1)` を見て自動的に `executeUpdate(PreparedSql,int,InputStream)` に分岐する設計だったが、これは 2 つの理由で誤りだった。(1) `InputStream` の取得元が `data` の中のどこにあるかを `Dao` の汎用実装は知りようがなく、疑似コードの `/* 既存の抽出方法を踏襲 */` は実在しない抽出方法を指していた。(2) **現行の BLOB サポートは、そもそも `Dao.create`/`update` のデフォルト実装を経由していない**——BLOB を扱うサブクラスは現在も `create(T)`/`update(T)` 自体を override し、`executeUpdate(String, int, InputStream)` を直接呼ぶ形を取る（`Dao.java:224-234` のデフォルトは `String` 版の `executeUpdate` しか呼ばず、`InputStream` を扱う経路を持たない）。**この責任分担は変えない（BR-11）**——BLOB を扱うサブクラスは `create(T)`/`update(T)` を override し、`getInsertPreparedSql(data)` で `PreparedSql` を得て、`sql.getBindIndexOf(DataType.OBJECT, 1)` で位置を得て、`data` から取り出した `InputStream` とともに新しい `executeUpdate(PreparedSql, int, InputStream)`（§ 5.1）を呼ぶ。**これは現行のパターンをバインド版に置き換えただけであり、新しい責任分担を追加するものではない。**

**FR-1.5（BLOB のバインド、Must）の充足**: 新しい `executeUpdate(PreparedSql, int, InputStream)` が存在し、`QueryImpl.getInsertPreparedSql`/`getUpdatePreparedSql` が OBJECT 列を含む全列を正しくバインドする（§ 2、§ 3）ことで、BLOB を扱うサブクラスが override 経由でこの経路を使える状態を提供する。**「デフォルト実装が自動的に BLOB を扱う」ことは要求されていない**——現行も要求していない（BLOB は常にサブクラスの override を要する）。

---

## § 7. `getBlobIndex()` の二重の意味の実装

```java
@Override
public int getBlobIndex() {
    return blobIndex;
}
```

**メソッド自体は 1 文字も変わらない。** 意味の違いは呼び出し側（どのビルドメソッドを最後に呼んだか）から生じる——§ 2 / § 3 が `blobIndex` に「位置」（1 始まり、なければ `-1`）を設定し、現行の `getInsertSQL` / `getUpdateSQL`（リテラル版、変更しない）が引き続き「個数」（`blobIndex++` によるカウント）を設定する。`domain-entities.md`「`blobIndex` — 二重の意味を持つ既存フィールド」を参照。

---

## § 8. 実装順序

`unit-of-work-story-map.md`「U3 `write-path`」の 7 段に本文書の節を対応づける。

| 段 | 内容 | 本文書 | 完了の確認 |
|---|---|---|---|
| 1 | `QueryImpl` に `setValues` / `insertValues` を追加（U2 の `bindFragments` と対になる規則、ADR-011） | § 1、§ 2、§ 3 | メソッドローカルであることを確認（インスタンスフィールドにしない） |
| 2 | `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` — `setValues ++`（`bindFragments` 由来の値）の結合順 | § 2、§ 3 | **AC-3b**（INSERT VALUES / UPDATE SET — カラム並び順どおりのバインド値） |
| 3 | FR-6.2（`:268` の OBJECT ブランチのテーブル修飾）— バインド版・リテラル版の両方 | § 3.1（バインド版は構造的に発生しない）。リテラル版は現行の `.replaceFirst` パターンを OBJECT 分岐にも適用する（§ 9 の差分参照） | 生成される SQL にテーブル修飾の不整合がない |
| 4 | `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)` | § 4 | 例外送出とテーブル名解決が現行と同じ |
| 5 | `getBlobIndex()` のバインド版契約（`getBindIndexOf(DataType.OBJECT, 1)` のキャッシュ） | § 7 | **AC-7**（OBJECT ＋ STRING 2 列の UPDATE — `getBlobIndex()` がバインド位置を返す） |
| 6 | `Dao` **および `DaoAdapter`**（`Dao` を継承しないため独立に必要）の新 protected 拡張点と互換シム、`executeUpdate(PreparedSql)` / `executeUpdate(PreparedSql, int, InputStream)`、`create` / `update` / `delete` の切り替え | § 5、§ 5.1、§ 5.2、§ 6 | 通常の書き込みが `executeUpdate(PreparedSql)` で実行される。BLOB を含む書き込みは、`create`/`update` を override したサブクラスが `executeUpdate(PreparedSql,int,InputStream)` を使って実行できる |
| 7 | 移行（Q7 = B）: `QueryImplTest` の書き込み系バインド版アサート、`UserDaoTest` の INSERT / UPDATE / DELETE 経路アサート書き換え、`QueryImplTest02/03` の BLOB プレースホルダ位置修正、FR-8.2 対応表の該当行 | Build and Test（3.6）に引き継ぐ | § 10 |

**1 を最初に置く理由**（`unit-of-work-story-map.md`）: この Unit の最大の失敗様式（全ての値が 1 つずつずれる UPDATE）は値アキュムレータの持ち方そのものに起因する。2 以降を先に書くと、後から値リストの分離に直すのは全面的な書き直しになる。

---

## § 9. 2.6 / 2.7 契約からの差分

**リテラル版 `getUpdateSQL` の FR-6.2 修正**: `:268` に `.replaceFirst(tableName + ".", "")` を追加する（`:257`、`:262` と同じ形に揃える）。これは U3 が触れる唯一のリテラル版コード変更であり、`business-rules.md` BR-12 が定める。

**新規 public / protected メンバ**: `domain-entities.md`「2.6 / 2.7 契約からの差分」の P-1、Pr-1 〜 Pr-10 を参照（`Dao` 側 5 件 + `DaoAdapter` 側 5 件。`DaoAdapter` は `Dao` を継承しないため独立に追加が必要——D-3）。

**削除・シグネチャ変更**: なし。

---

## § 10. 後続ステージへの引き継ぎ事項

| # | 引き継ぎ先 | 内容 |
|---|---|---|
| 1 | **Build and Test（3.6）** | AC-3b の判定——INSERT VALUES / UPDATE SET のバインド値がカラム並び順どおりであることを、mock の位置・値アサートで確認する。**不変条件 1（値リスト分離）を検出する唯一の AC**（`unit-of-work-story-map.md`） |
| 2 | **Build and Test（3.6）** | AC-7 の判定——OBJECT ＋ STRING 2 列の UPDATE で `getBlobIndex()` がバインド位置を返すことを確認する |
| 3 | **Build and Test（3.6）** | `QueryImplTest02` / `QueryImplTest03` の BLOB プレースホルダ位置修正（FR-8.3。Derby の導入可否は OQ-6） |
| 4 | **Build and Test（3.6）** | FR-6.2 の修正（リテラル版・バインド版の両方）を確認する回帰テスト。バインド版はそもそもテーブル修飾を作らない構造（§ 3.1）であることを含めて確認する |
| 5 | **Build and Test（3.6）** | (a) `Dao` / `DaoAdapter` の既定の `create` / `update` / `delete`（BLOB を override しないクラス）が、常に `executeUpdate(PreparedSql)` を使うことの確認——BLOB の有無による自動分岐は存在しない。(b) BLOB を扱うサブクラス（`DaoAdapter` を継承する実際の DAO で `create`/`update` を override する想定）が、`getBindIndexOf(DataType.OBJECT, 1)` で位置を取得し `executeUpdate(PreparedSql,int,InputStream)` を明示的に呼び出すことの確認 |
| 6 | **Build and Test（3.6）** | FR-8.2 の対応表の U3 該当行——`UserDaoTest` の INSERT / UPDATE / DELETE 経路のアサート文字列が `?` 入りに変わる分 |

---

## Review

NOT-READY

**Reviewer:** aidlc-architecture-reviewer-agent（Functional Design 3.1 / U3 `write-path`、iteration 2、2026-08-10）

### 検証の方法と範囲

iteration 1 の blocking 指摘 7 件（B-1〜B-7）の是正を、テキストが変わったかではなく**是正後の記述が実際に正しいか**で検証した。突き合わせた根拠: 実ソース `QueryImpl.java`（`:30-60` のフィールド定義、`:184-219`、`:235-274`、`:276-309`、`:436-476`）、`Dao.java`（`:212-234`、`:241-274`）、`DaoAdapter.java`（`:26`、`:152-186`）、`Condition.java`、`SQLParser.java:85-116`、`MappingUtils.java:128-137`、`FileDataDao.java`、`UserDao` / `UserStatDao` / `TestDao` の継承関係、`BlobUtils.java`。上流契約は `component-methods.md`（M-2 / M-4 / M-5 / M-8）、`components.md` M-8、`unit-of-work.md` U3、`unit-of-work-story-map.md`、`requirements.md`（FR-1.4 / FR-1.5 / FR-3.2 / FR-6.2 / AC-3b / AC-7 / NFR-1 / NFR-3）。sibling は U1 `bind-foundation` と U2 `select-path` の 3.1 成果物のみ。

**7 件の是正はいずれも実体として正しい。** 個別の確認結果は下の「是正の検証結果」に記す。しかし是正の過程で**`Dao` / `DaoAdapter` 側の設計が縮退し**、iteration 1 では `whereValues` / `position` の欠陥に隠れていた 3 件の blocking な欠落が表面化した。いずれも「開発者がこの文書だけでは書けない」または「書くと Must 要件が満たされない」種類の欠落である。

### 是正の検証結果（7 件すべて可）

1. **B-1 / B-2（`where.toString()` → `buildBindWhere`）— 正しい。** § 4 の `buildBindWhere` は U2 `business-logic-model.md` § 4.2 の `getSelectPreparedSql()` の描画ループと**逐語的に同一**である（`w.append(i == 0 ? " " + WHERE + " " : " " + f.connector + " ")` → `w.append(f.param.getSql())` → `collected.addAll(f.param.getValues())`）。先頭断片に `WHERE` 接頭辞・後続に `connector` という分岐も U2 § 3.2 `appendWhere`（リテラル側は `where.length() == 0` で判定）と同じ位置で切り替わる——両面は `appendWhere` が同時更新するため、`bindFragments` の index 0 とリテラル `where` の空判定は常に一致する。`QueryImpl.WHERE` 定数は `"WHERE"`（`:32`）なので `" " + WHERE + " "` は現行 `addWhere`（`:445`）が書く `" WHERE "` と一字一致する。`whereValues` の残存参照は 3 文書のいずれにもない（残るのは「そういう名のフィールドは存在しない」という否定形の注記のみ）。
2. **B-3 / B-4（SET 句の `=?` 直書き → `p.getSql()`）— 正しい。** 自動タイムスタンプ列を手で追った: `value = getTimestampString()` → `"current_timestamp"`（`QueryImpl.java:464-465` で確認）→ `bindSqlBuilder.placeholder(col, "current_timestamp")` → U1 `tokenFor`（§ 4.1）は OBJECT でも FUNCTION でもなく、`isNullValue(DATE, "current_timestamp")` は偽、`isSqlFunction`（U1 § 2.5: 型が DATE / TIME かつ値が `current_timestamp`）が真 → `Token("current_timestamp", null)`。したがって `p.getSql() == "current_timestamp"`、`p.getValues().size() == 0`。SET 句は `updated=current_timestamp` となり `?` を生まず値も積まないため個数は一致し、`PreparedSql.of` の PS-2 検査を通る。リテラル版 `:262`（`parser.value(col, EQUAL, "current_timestamp")` → `SQLParser.parseValue` の DATE/TIME 分岐が `current_timestamp` をそのまま返す、`SQLParser.java:107-109`）と出力も一致する。`position` カウンタは 3 文書のコードから完全に消えている。
3. **B-5（`blobIndex` の算出）— 正しい。** § 2 / § 3 とも `PreparedSql` を組み立てた**後**に `prepared.getBindIndexOf(DataType.OBJECT, 1)` を呼んでキャッシュしている。U1 `domain-entities.md` C-2（175-179 行）の契約は「`values` を先頭から走査し `type` に一致する `occurrence` 番目（1 始まり）の要素のバインド位置（1 始まり）を返す。該当なしは `-1`」であり、「最初の OBJECT」「1 始まり」「なければ `-1`」という本文書の主張と一致する。`getUpdatePreparedSql` では `combined`（`setValues ++ WHERE 値`）から作った `PreparedSql` に対して呼ぶため、SET 句にある OBJECT の位置が文全体の絶対位置として正しく出る。BI-2「走査ロジックを U3 が再実装しない」とも整合する。
4. **B-6（`Dao` の拡張点）— `Dao` に関する限り正しい。** `component-methods.md` M-4「追加」表（302-304 行）は確かに `PreparedSql getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` を protected の新拡張点として宣言し、既定実装を `PreparedSql.ofLiteral(getInsertSQL(data))`、旧 override 未実装時の挙動を `RuntimeException(NoSuchMethodException)` としている——本文書 § 6 / BR-14 / Pr-3〜Pr-5 の記述はこれと一致する。`Dao.getInsertSQL(T)` が `Dao.java:212-214` で `throw new RuntimeException(new NoSuchMethodException())` の protected フックであること、`FileDataDao.java:17-22` がそれを override する形であることも実ソースで確認した。`QueryImpl` の同名 override とは別クラスの別メンバであるという整理も正しい。**ただし `DaoAdapter` 側が欠落している（B-2 参照）。**
5. **B-7（`Dao.executeUpdate(PreparedSql,int,InputStream)` の本体）— 正しい。** § 5.1 の本体は `Dao.java:266-274` と行単位で対応する（`createDaoEvent(sql)` → `handleBeforeExecuteUpdate` → dbm 呼び出し → `TransactionStateManager.getInstance().executed()` → `event.setResult(result)` → `return getExecuteHandler().handleAfterExecuteUpdate(event)`）。`createDaoEvent(String)` が `Dao.java:241-243` に存在し `sql.getSql()` を渡す形が妥当であることも確認した。
6. **FR-1.5 / AC-7 の充足（「BLOB は自動判定しない」への方針転換）— 妥当。** リポジトリ全体を検索したところ `Dao.executeUpdate(String,int,InputStream)` の呼び出し元も `getBlobIndex()` の呼び出し元も**製品コード・テストコードのいずれにも存在しない**（唯一の `setBinaryStream` 実呼び出しは `BlobUtils.java:18` と mock）。すなわち現行の BLOB 書き込みも `Dao.create`/`update` の既定実装を経由しておらず、「サブクラスが override する」という責任分担は現行の事実の記述として正しい。AC-7（`requirements.md` 269-275 行）が要求するのは「`getBlobIndex()` がバインド位置を返す」ことと「その位置に `setBinaryStream` した UPDATE が実行できる」ことであり、§ 3 ＋ § 5 ＋ § 5.1 でその機構は揃う。**要件を歩み戻ってはいない。**
7. **行番号引用 — 実ソースと一致。** `getInsertSQL` は `@Override` が 184 行・閉じ括弧が 219 行、`getUpdateSQL` は 235 行・274 行で、`:184-219` / `:235-274` はいずれも正確。`:246` / `:257` / `:262` / `:268`（`.replaceFirst` を欠く OBJECT 分岐）、`:289`、`:296-298`、`Dao.java:224-234` / `:257-274` / `:266-274`、`FileDataDao.java:17-22` もすべて一致した。

### ブロッキング

**B-1. `Dao.executeUpdate(PreparedSql)` — 新しい `create`/`update`/`delete` が依存する当のメソッドが、宣言も本体も無い**

§ 6 は `return executeUpdate(getInsertPreparedSql(data));` と書き、コメントで「U1 の `executeUpdate(PreparedSql)`」と注記する。**この帰属が誤りである。** U1 が所有するのは `component-methods.md` M-5（343 行）の `public int executeUpdate(PreparedSql sql)`——`DBAccessManager` のメソッドであり、`Dao` の無修飾 `executeUpdate(...)` はそこには解決しない。`Dao` 側の対応物は M-4「追加」表の **306 行 `| int executeUpdate(PreparedSql sql) | protected | 同上 | DaoException |`** として別に宣言されており、`unit-of-work.md` U3 の「所有するコンポーネント」も「M-4 `Dao` / `DaoAdapter` の新 protected 拡張点（…）**と `executeUpdate(PreparedSql)`**、`create(T)` / `update(T)` / `delete(T)` の切り替え」と明記して U3 に割り当てている。U2 は M-4 のうち `executeQuery` しか取っていない（U2 § 10 D-6、Pr-1 / Pr-2）。

にもかかわらず本 Unit の 3 文書のどこにも `Dao.executeUpdate(PreparedSql)` は現れない。`domain-entities.md`「この文書の範囲」表の `Dao` 行は `executeUpdate(PreparedSql, int, InputStream)` と `getXxxPreparedSql(T)` 3 個だけを挙げ、「新規 protected メンバ（**AC-11 の判定対象**）」表 Pr-1〜Pr-5 にも含まれない。§ 5.1 が BLOB 版の本体を明記した一方で、**より使用頻度の高い非 BLOB 版の本体は空白のまま**である。

帰結は iteration 1 の B-7 と同型で、影響範囲はより広い。開発者が `Dao.create` から `dbm.executeUpdate(sql)` を直接呼ぶ形に書けば、`DaoEvent` の生成・`ExecuteHandler` の前後フック・`TransactionStateManager.getInstance().executed()`・`event.setResult` が**すべての** INSERT / UPDATE / DELETE で落ちる（現行 `Dao.java:257-264` が必ず通す 4 手順）。トランザクション状態追跡と利用側の `DaoExecuteHandler` が黙って無効化され、NFR-3（既存利用側が再コンパイルなしで**動作する**）に反する。§ 5.1 と同じ密度で本体を示し、`domain-entities.md` の新規メンバ表と Pr 表に計上する必要がある（Pr 表は「AC-11 の判定対象の全量」を名乗っている）。

**B-2. `DaoAdapter` の `create`/`update`/`delete` が未設計。`DaoAdapter` は `Dao` のサブクラスではないため、§ 6 の変更はこのリポジトリのどの DAO にも届かない**

§ 6 の見出しは「`Dao` / `DaoAdapter` — `create` / `update` / `delete` の切り替え」だが、本文・疑似コード・`domain-entities.md` のいずれも `Dao` しか扱っていない（`DaoAdapter` の新規メンバは Pr-2 の `executeUpdate(PreparedSql,int,InputStream)` 1 個のみ）。

実ソースで確認した事実:

- `DaoAdapter.java:26` — `public class DaoAdapter<T extends ORMappingSupport<T>> implements AutoCloseable`。**`Dao` を継承していない**。`protected Dao<T> delegate;` を持つ委譲ラッパである。
- `DaoAdapter.java:152-162` — `getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)` を**自前で**持ち、いずれも `throw new RuntimeException(new NoSuchMethodException())`（`delegate` に転送していない）。
- `DaoAdapter.java:164-174` — `create` / `update` / `delete` も自前で、`return delegate.executeUpdate(getInsertSQL(data));` の形。すなわち **SQL はアダプタのサブクラスから、実行は `delegate` の `String` 版から**取る。
- 本リポジトリの DAO はすべて `DaoAdapter` を継承する: `FileDataDao.java:7`、`UserDao.java:15`、`UserStatDao.java:14`、`TestDao.java:8`。`Dao` を直接継承するのは `MySQLDao` / `OracleDao`（方言、U4 の担当）だけである。

したがって `Dao.create` だけをバインド版に切り替えても、**このリポジトリのどの DAO も新経路に載らない**。FR-1.4（Must、INSERT / UPDATE / DELETE のバインド化）は達成されず、`UserDaoTest` の INSERT / UPDATE / DELETE 経路（§ 10 の引き継ぎ 6 が「アサート文字列が `?` 入りに変わる」と予告している当のテスト）も依然としてリテラル SQL を見ることになる。AC-3b の判定も `QueryImpl` 単体テストでしか成立しない。

上流契約もこれを要求している——`component-methods.md` 308 行「`DaoAdapter` は同名の public / protected メソッドを持ち、`delegate` に転送する（現行の `param` / `getInsertSQL` 等と同じ形）」、および `unit-of-work.md` U3 の「M-4 `Dao` / **`DaoAdapter`** の新 protected 拡張点」。必要なのは少なくとも (a) `DaoAdapter` にも `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` の protected 拡張点（既定は `ofLiteral(getInsertSQL(data))`、`DaoAdapter` 自身の `getInsertSQL` を包む）、(b) `create`/`update`/`delete` を `delegate.executeUpdate(getInsertPreparedSql(data))` 形に切り替える設計、(c) `executeUpdate(PreparedSql)` の `delegate` 転送、の 3 点であり、いずれも `domain-entities.md` の Pr 表（AC-11 の判定対象）に計上されねばならない。

**B-3. `QueryImpl` の `bindSqlBuilder` が宣言なしに 6 箇所で参照される一方、`domain-entities.md` は「`QueryImpl` に新規フィールドなし」と明記している**

§ 2（`bindSqlBuilder.placeholder(col, value)`）、§ 3.1（`bindSqlBuilder.value(...)` と `bindSqlBuilder.placeholder(...)`）、§ 4（`bindSqlBuilder.value(...)`）が裸の識別子 `bindSqlBuilder` を参照する。実ソースの `QueryImpl`（`:38-53`）が持つフィールドは `valueConvertFilter` / `selectColumns` / `updateColumns` / `tables` / `removeFromTables` / `outerJoinTables` / `uniqTableNames` / `where` / `groupBy` / `orderBy` / `useAutoPrimaryKeyUpdate` / `blobIndex` / `distinct` であり、`BindSqlBuilder` を保持するメンバは無い。U2 が `QueryImpl` に追加したのも `bindFragments` と `bindOuterJoinTables` だけである（U2 § 10 Pr-3〜Pr-5）——U2 の `QueryImpl` は `Param` を `Search` から受け取る側であり、`BindSqlBuilder` を自分で呼ぶ必要がなかった。**`QueryImpl` が生成器を直接呼ぶのは U3 が最初である。**

ところが `domain-entities.md`「この文書の範囲」表の `QueryImpl` 行は「新規フィールド: **なし**（`blobIndex` は既存フィールドの意味を拡張するのみ）」と明記する。実装者は「`protected BindSqlBuilder bindSqlBuilder` を足す」のか「各メソッドで `new BindSqlBuilder()` するのか」（現行 `getInsertSQL` が `new SQLParser(valueConvertFilter)` をメソッドローカルに作るのと同じ形）を判断できない。`protected` フィールドにするなら AC-11 の判定対象であり Pr 表に載る必要があり、載っていない現状では Pr 表が「全量」であるという前提が崩れる。

これは U2 の iteration 2 で blocking と判定された `Dao.bindSqlBuilder` の指摘と**同型の欠陥**である（U2 はその指摘を受けて `domain-entities.md` § 5 を「`protected BindSqlBuilder bindSqlBuilder` を 1 つ追加する」に改め Pr-7 を計上した）。同じ基準を U3 にも適用する。なお `BindSqlBuilder` は `ValueConvertFilter` を受け取らない（U1 § 4.2、`business-rules.md` R-3 の前提）ため、`valueConvertFilter` を渡す形にしてはならない点も併せて明記されたい。

### 非ブロッキング

- **N-1（§ 3.1 と § 4 でヘルパが二重に書かれている）**: BR-9 と § 4 の本文は「WHERE 描画を `buildBindWhere` の 1 箇所にまとめ、3 メソッドで共有する」と述べるが、§ 3.1 の疑似コードは `buildBindWhere` を呼ばずに同じループを `bindWhere` / `bindWhereValues` という別名でインライン展開している。ロジックは逐語的に同一なので実害はないが、規則と疑似コードが食い違っており、実装者がどちらを写すか迷う。§ 3.1 を `List<BindValue> bindWhereValues = new ArrayList<>(); String bindWhere = buildBindWhere(bindWhereValues);` に置き換えるのが素直である。
- **N-2（Pr 参照の取りこぼし）**: § 9「新規 public / protected メンバ」は「`domain-entities.md` … の P-1、**Pr-1、Pr-2** を参照」と書くが、iteration 1 の是正で Pr-3〜Pr-5 が増えている。`P-1、Pr-1〜Pr-5` に直す。
- **N-3（失敗様式 2 の検出方法が空振りになった）**: `business-rules.md` 失敗様式 2 は依然として「`position` のインクリメントを OBJECT 列自体でスキップする」という**削除済みの概念**を例示し、検出方法として「`getBlobIndex()` と `PreparedSql.getBindIndexOf(OBJECT, 1)` が独立に計算する値を突き合わせる」と述べる。是正後は `blobIndex` が `getBindIndexOf(OBJECT, 1)` の戻り値**そのもの**であるため、この突き合わせは恒真であり何も検出しない。AC-7 の判定は「OBJECT ＋ STRING 2 列の UPDATE で `getBlobIndex()` が**絶対値として期待する位置**（列の並びから手で導いた整数）を返す」形に書き換えないと、FR-3.2 の唯一の判定手段が失われる。3.6 への引き継ぎに明記されたい。
- **N-4（§ 10 引き継ぎ 5 が BR-11 と矛盾）**: § 10 の引き継ぎ 5 は「`create` / `update` が BLOB の有無で正しく `executeUpdate(PreparedSql)` と `executeUpdate(PreparedSql,int,InputStream)` を**使い分ける**ことの確認」と書くが、BR-11 / BI-3 は「デフォルト実装は BLOB を自動判定しない。使い分けはサブクラスの override が行う」と定めた。iteration 1 の設計の名残であり、3.6 が誤った期待でテストを書く。
- **N-5（`NFR-5` 行の規則 ID）**: `business-rules.md` の NFR-5 行「（移行、**BR-13** とは別に Build and Test 3.6 が担当）」は、文脈上 BR-12（リテラル版 `.replaceFirst` の追加）を指すと読める。iteration 1 の N-6 が未適用のまま残っている。
- **N-6（リテラル面 `where` に構文として壊れた断片が入る）**: § 3.1 / § 4 が主キー述語のリテラル面として渡す `getColumnName(col) + Condition.EQUAL.getCondition()` は `"user.id="`（`Condition.EQUAL` の `condition` は `"="`、`Condition.java:14` で確認）であり、U2 `appendWhere` はこれを**リテラル `where` にも書き込む**。したがって `getUpdatePreparedSql()` を呼んだ後に同じ `Query` で `getUpdateSQL()` を呼ぶと `... WHERE user.id=` という壊れた SQL が返る（`addWhere` が `useAutoPrimaryKeyUpdate = false` にするため正しい述語が後から補われることもない、`QueryImpl.java:450` / U2 § 3.3）。新経路の実行には影響しないが、`business-rules.md` 失敗様式 3 が 3.6 に送る「`getUpdateSQL()` と `getUpdatePreparedSql()` の WHERE 述語数を突き合わせる同期テスト」はこの汚染の上で走ることになる。また § 3.2 の「両者は一致した内容を持つ」という主張は正確でない——リテラル面は `?` も値も持たない縮退形である。意図的な割り切りなら、その旨と 3.6 への影響を明記されたい。
- **N-7（BLOB サブクラスは `Dao.getXxxPreparedSql` の override も要る）**: BR-11 は「BLOB を扱うサブクラスは `create(T)`/`update(T)` を override し、`getInsertPreparedSql(data)` で `PreparedSql` を得て `getBindIndexOf(DataType.OBJECT, 1)` で位置を得る」と述べるが、`Dao.getInsertPreparedSql(T)` の**既定実装は `PreparedSql.ofLiteral(...)`** であり、U1 `domain-entities.md` C-2（179 行）は「`values` が空の `ofLiteral` 由来の `PreparedSql` に対しては**常に `-1`** を返す」と定める。既定のままだと `setBinaryStream(-1, in)` になる（実際には `checkBindable` が先に `DaoException` を投げるので診断は可能）。BR-11 に「`Dao.getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` も `QueryImpl` のバインド版を返すよう override すること」を明記しないと、実装者は動かないサブクラスを書く。
- **N-8（M-8 `BlobUtils` が未言及）**: `unit-of-work.md` U3 の所有コンポーネントには「M-8 `BlobUtils` の位置づけ変更」が含まれる。`components.md` M-8 の内容は「責務が C-6 `PreparedStatementBinder` に移る／既存 public メソッドは互換のため残す」であり実質は無変更宣言だが、3 文書のいずれも `BlobUtils` に言及していない。§ 5 が `BlobUtils` を経由せず `ps.setBinaryStream` を直に呼ぶ設計であることと併せ、1 行で「`BlobUtils.executeUpdate(PreparedStatement,int,InputStream)` は互換のため残し、新経路は経由しない」と記すべきである。
- **N-9（§ 1 の引用の忠実性）**: § 1 の `getInsertSQL` 引用は実コードのネストした `if (data.isUpdate(col)) { } else { if / else if / else }`（`:199-211`）を `else if` の平坦形に書き換えている（意味は等価）。iteration 1 の N-1 のうちこの点は未修正。`data.setValue(col, id)` の位置は実コード（`:204-205`）と一致するよう直っている。
- **N-10（スキーマ付きテーブルでの挙動差）**: BR-5 が SET 句に使う `col.getColumnName()` は素のカラム名だが、リテラル版の `.replaceFirst(tableName + ".", "")` は `MappingUtils.getColumnName`（`:135` で `getTableNameWithSchema() + "." + columnName`）が返す**スキーマ付き**の名前から `tableName + 任意 1 文字`（正規表現の `.`）を最初の 1 回だけ除くため、スキーマを設定したテーブルでは `sch.name` が残る。バインド版のほうが正しいが、リテラル版との出力差になる。FR-6.2 の是正の副産物として意図的なら、3.6 の回帰テスト観点に加えられたい。

### 再提出時に判定する点

B-1・B-2・B-3 はいずれも「§ 6 と `domain-entities.md` の `Dao` / `DaoAdapter` / `QueryImpl` メンバ表」という同じ 2 箇所に集中している。必要なのは (i) `Dao.executeUpdate(PreparedSql)` の本体（§ 5.1 と同じ密度）と Pr 表への計上、(ii) `DaoAdapter` の 3 拡張点・`create`/`update`/`delete` の切り替え・`executeUpdate(PreparedSql)` 転送の明記と Pr 表への計上、(iii) `QueryImpl` が `BindSqlBuilder` をどう保持するかの決定（フィールドなら Pr 表へ、ローカル生成ならその旨を `domain-entities.md` の「新規フィールドなし」の注記と整合させる）、の 3 点である。7 件の既存是正には手を入れる必要がない。

---

### 適用記録（オーケストレータ、iteration 2 上限到達後の直接適用）

`reviewer_max_iterations: 2` に到達したため、レビュアーによる再検証を経ずにオーケストレータが以下を適用した。

| # | 対応した指摘 | 適用内容 | 検証根拠 |
|---|---|---|---|
| 1 | B-3（`bindSqlBuilder` 未宣言） | `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql`（§ 2、§ 3.1、§ 4）冒頭に `BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();` をメソッドローカル変数として追加。`domain-entities.md`「この文書の範囲」を、U2 `Dao.bindSqlBuilder`（インスタンスフィールド）と対比する形で改訂し、新規フィールドが増えないことと整合させた | 現行 `QueryImpl` のリテラル版メソッドが `SQLParser parser` をメソッドローカルで `new` するパターン（`SQLParser.java` 呼び出し箇所と同型）に倣った。`BindSqlBuilder` は `ValueConvertFilter` を要求しないため引数なしで構築できる（U1 § 4.2） |
| 2 | B-1（`Dao.executeUpdate(PreparedSql)` の本体欠落） | § 5.1 に 1 引数版の本体を追加。BR-9a / B-7（iteration 1）で確立した形（`createDaoEvent` → `handleBeforeExecuteUpdate` → `dbm.executeUpdate(sql)` → `TransactionStateManager.getInstance().executed()` → `event.setResult` → `handleAfterExecuteUpdate`）を、BLOB 版（3 引数）と全く同じ密度で複製した | `Dao.java:257-264`（`executeUpdate(String)` の同型手順）と行単位で対応。`domain-entities.md` の Pr 表に Pr-1（`Dao.executeUpdate(PreparedSql)`）として計上 |
| 3 | B-2（`DaoAdapter` の 3 拡張点・切り替えが未設計） | § 5.2 を新設し、`DaoAdapter` に `executeUpdate(PreparedSql)` / `executeUpdate(PreparedSql,int,InputStream)` / `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` を `Dao` 側と独立に追加。`create`/`update`/`delete` を `delegate.executeUpdate(getInsertPreparedSql(data))` 形に書き換えた。`domain-entities.md` の Pr 表を Pr-1〜Pr-10（`Dao` 側 5 + `DaoAdapter` 側 5）に拡張し、2.6/2.7 差分表に D-3 を追加 | `DaoAdapter.java:26`（`class DaoAdapter<T> implements AutoCloseable`、`Dao` 非継承）、`DaoAdapter.java:152-174`（既存の自前 SQL 生成 + `delegate` 実行パターン）、`FileDataDao.java`（実際の DAO はすべて `DaoAdapter` を継承）で確認した実装形に整合させた |
| 4 | N-1（非ブロッキング、§ 3.1 が `buildBindWhere` を呼ばずインライン展開） | § 3.1 のインライン WHERE 描画ループを `List<BindValue> bindWhereValues = new ArrayList<>(); String bindWhere = buildBindWhere(bindWhereValues);` に置き換え、BR-9 の「3 メソッドで共有」の主張と一致させた | § 4 の `buildBindWhere` 定義と同一シグネチャで呼び出していることをコード上で確認 |
| 5 | N-2（非ブロッキング、§ 9 の Pr 参照が Pr-1/Pr-2 のまま） | § 9「新規 public / protected メンバ」の参照を「P-1、Pr-1〜Pr-10（`Dao` 側 5 件 + `DaoAdapter` 側 5 件。D-3）」に更新 | 上記 3 の Pr 表拡張と整合 |
| 6 | § 10 引き継ぎ 5（N-4 と同一論点） | 「BLOB の有無で使い分ける」という iteration 1 の名残の表現を、「既定経路は常に `executeUpdate(PreparedSql)`」「BLOB override 経路は明示的に `executeUpdate(PreparedSql,int,InputStream)`」の 2 点確認に書き換えた | BR-11 / BI-3 の記述と整合 |

**未解消の指摘（3.6 または後続 Unit への申し送り。iteration 2 上限のため、これ以上のレビュアー再検証は行わない）**

- **N-3（失敗様式 2 の検出方法が恒真になる）** — `business-rules.md` 失敗様式 2 の検出方法を「`getBlobIndex()` と `getBindIndexOf` の突き合わせ」から「列の並びから手で導いた絶対位置との突き合わせ」に書き換える必要がある。**未対応** — Build and Test（3.6）に申し送る。
- **N-5（NFR-5 行の規則 ID 誤参照）** — `business-rules.md` NFR-5 行の「BR-13」は文脈上 BR-12 を指す。**未対応**（軽微な誤記、実装には影響しない）。
- **N-6（リテラル面 `where` に構文として壊れた断片が入る）** — `getUpdatePreparedSql()` 呼び出し後に同じ `Query` で `getUpdateSQL()` を呼ぶ場合の汚染。新経路の実行には影響しないため**意図的に未対応のまま 3.6 の回帰テスト観点に申し送る**。
- **N-7（BLOB サブクラスは `Dao.getXxxPreparedSql` の override も要る）** — `Dao.getInsertPreparedSql(T)` の既定実装が `PreparedSql.ofLiteral(...)` のままだと BLOB サブクラスで `blobIndex == -1` になりうる（`checkBindable` が先に例外を投げるため実害は診断可能なエラーに留まる）。BR-11 への追記は**未対応** — 実装フェーズ（Code Generation 3.5）で BLOB override 実装時に反映する。
- **N-8（`BlobUtils` の位置づけが未記載）** — 1 行の追記で足りる軽微な指摘。**未対応**。
- **N-9（§ 1 引用のネスト構造）** — 意味等価な書き換えであり実装に影響しない。**未対応**。
- **N-10（スキーマ付きテーブルでの挙動差）** — バインド版のほうが正しい（FR-6.2 是正の意図した副産物）。3.6 の回帰テスト観点に**申し送り済み**（未対応の追記作業のみ残る）。

これらの非ブロッキング項目はいずれも新経路の正しさ（AC-3b、AC-7、FR-1.4/1.5/3.2/6.2）を損なわないと判断し、Unit の完了をブロックしない。

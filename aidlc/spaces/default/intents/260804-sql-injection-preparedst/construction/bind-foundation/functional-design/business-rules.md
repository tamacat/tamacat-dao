# Business Rules — U1 `bind-foundation`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U1 の境界「SQL 文の組み立て（節の連結）を行わない。断片（`Param`）と完成文（`PreparedSql`）の**型と規則**を提供するところまで」。本文書はその「規則」を列挙する。
- **`unit-of-work-story-map.md`**（同上）— U1 が担う FR-2.1〜FR-2.4 / FR-4.1 / FR-4.2 / FR-8.1 と AC-5 / AC-6 / AC-8。末尾の対応表でどの規則がどれを満たすかを示す。
- **`requirements.md`**（requirements-analysis, 2.3）— FR-2 群（「現行の挙動を維持する」が Must）、FR-4 群、FR-8.1、NFR-3、CON-1、CON-6。
- **`components.md`**（application-design, 2.6）— C-4 `ValueRules` を切り出す理由「これらの規則が旧新の 2 か所に実装されると挙動が乖離しうる」。本文書はその 1 か所の中身である。
- **`component-methods.md`**（同上）— C-4 の現行処理順序の表、C-6 の setter 対応表、C-8 の mock 拡張。
- **`services.md`**（同上）— R-1（値の運搬形態の変更）、R-5（実行の観測は平文で `ThreadLocal` に残る）。

**「現行の挙動を維持する」の判定基準は `SQLParser.java` の実コードと `SQLParserTest.java` の 30 件のアサーションである。** 以下の規則はすべてその 2 つから導出し、根拠欄に行番号を示す。

---

## 規則の読み方

- **根拠**列の `SQLParser.java:NN` は現行実装の該当行、`SQLParserTest.java:NN` は挙動を固定している既存テストを指す。
- 「バインド経路」は `BindSqlBuilder` → `Param` / `PreparedSql` → `PreparedStatementBinder` の新経路、「リテラル経路」は `SQLParser` の旧経路を指す。
- 規則が**両経路に適用される**ものは `ValueRules` に置く。片方だけのものはその経路のコンポーネントに置く。

---

## A. 値の分類規則（`ValueRules`、両経路共通）

### BR-1. 必須チェックは単一値のときだけ適用する

`values.length == 1` のとき、値が空（null または空文字）かつ `column.isNotNull()` が真なら `InvalidParameterException("Column [" + colName + "] is required.")` を投げる。`colName` は `MappingUtils.getColumnName(column)`（テーブル修飾付き）。

**`values.length >= 2`（BETWEEN の 2 値、IN の複数値）ではこのチェックを行わない。** 現行の必須チェックは `if (values.length == 1)` ブロックの内側にあり、複数値の分岐（`SQLParser.java:60-70`）には存在しない。この非対称は意図的か偶然かを問わず**現行の挙動**であり、FR-2 の「現行の挙動を維持する」に従って維持する。

根拠: `SQLParser.java:43-47`。

### BR-2. NUMERIC / FLOAT の型検証は「空でない値」にだけ適用する

`column.getType()` が `NUMERIC` または `FLOAT` のとき:

| 値 | 挙動 |
|---|---|
| 空（null または空文字） | 検証しない。BR-4 により SQL NULL として扱う |
| 空でなく `isNumeric` が真 | そのままバインドする |
| 空でなく `isNumeric` が偽 | `InvalidParameterException("value is not numeric.")` を投げる |

例外メッセージは 1 文字も変えない。根拠: `SQLParser.java:93-103`、`SQLParserTest.java:72-89`（`"abc"` / `"-"` / `";"` が例外）。

これが **AC-5**（NUMERIC に `"';select * from dual --'"` → `InvalidParameterException`）を満たす規則である。

### BR-3. `isNumeric` の判定は現行の正規表現をそのまま使う

```
空（null または空文字） → false
それ以外 → 正規表現 ^\-?[0-9]*\.?[0-9]+$ に find() で一致するか
```

既存テストが固定している境界: `"0"` → 真、`"1234567890.1234567890"` → 真、`"-1234567890"` → 真、`""` / `null` / `"abc"` / `"'"` / `";"` / `"."` / `"-"` → 偽。

根拠: `SQLParser.java:145-150`、`SQLParserTest.java:143-157`。

`Pattern` を毎回コンパイルする現行の形をそのまま移してよい。`static final` に持ち上げても挙動は同一（`Pattern` はスレッドセーフ）だが、NFR-7（最適化を持ち込まない）に従い**必須としない**。

### BR-4. SQL NULL として扱う条件は型ごとに異なる

| `DataType` | SQL NULL とする条件 | 根拠 |
|---|---|---|
| `NUMERIC`, `FLOAT` | 値が空（null または空文字） | `SQLParser.java:94-95` |
| `DATE`, `TIME` | 値が空、または `"NULL"`（大文字小文字を無視） | `:105-106` |
| `OBJECT` | **該当しない**（BR-8 が別に扱う） | `:112-113` |
| `STRING`, `BOOLEAN`, `FUNCTION` | 値が **null のとき**だけ（空文字は NULL ではない） | `:87-92`、`:114-116` |

STRING / BOOLEAN の空文字が SQL NULL にならないことは `SQLParserTest.java:60`（`test1.name=''`）が固定している。

### BR-5. 単一値経路では null を空文字に置き換えてからバインドする

`values.length == 1` かつ値が null のとき、現行は条件テンプレートの `#{value1}` を**空文字**で置換してから値を組み立てる（`SQLParser.java:54-56`）。したがって STRING カラムに null を渡すと `''`（空文字リテラル）になり、SQL NULL にはならない。

バインド経路もこれに従う。**STRING カラムに `null` を渡したときバインドされる値は `""` である。**

根拠: `SQLParser.java:54-56`、`SQLParserTest.java:61`（`value(column1, EQUAL, (String)null)` → `test1.name=''`）。

### BR-6. 複数値経路（IN / BETWEEN）では null が SQL NULL になる

BR-5 の null → 空文字の置換は単一値経路にしか存在しない。IN / BETWEEN の各要素は `parseValue` に生の値が渡るため、STRING カラムでも null は BR-4 により SQL NULL になる。

根拠: `SQLParser.java:75-83`（`parseMultiValue`）、`SQLParserTest.java:130`（`value(column1, IN, (String)null)` → `test1.name in (null)`）と `:129`（`value(column1, IN, "")` → `test1.name in ('')`）の対比。

**BR-5 と BR-6 は同じ null に対して異なる結果を出す。** これは現行の非対称であり、FR-2 に従って維持する。

### BR-7. `current_timestamp` は値ではなく SQL 関数として出す

`column.getType()` が `DATE` または `TIME` で、値が `"current_timestamp"`（大文字小文字を無視）のとき、**バインドせずテキストにそのまま出す**。

判定順序は **BR-4（NULL 判定）→ BR-7（SQL 関数判定）→ 通常のバインド**である。現行 `parseValue` の DATE / TIME 分岐がこの順に並んでいる（`:105` の空・`NULL` 判定 → `:107` の `current_timestamp` 判定 → `:110` の通常）。

この値は `QueryImpl.getTimestampString()`（`QueryImpl.java:465` が `"current_timestamp"` を返す）から AUTO_TIMESTAMP カラムに対して流れ込む。バインドすると文字列リテラルとして解釈され意味が変わるため、FR-2.4 が現行維持を Must としている。

根拠: `SQLParser.java:104-111`、`QueryImpl.java:465`。

### BR-8. `OBJECT` カラムは値を無視して 1 つのバインド枠を占める

`column.getType()` が `OBJECT` のとき、渡された文字列の内容にかかわらず:

- テキストに `?` を 1 つ置く
- 値として `BindValue.ofNull(DataType.OBJECT)` を積む

現行のリテラル経路も値を無視して `"?"` を返す（`SQLParser.java:112-113`）。実際のバイナリは `PreparedSql.getBindIndexOf(DataType.OBJECT, 1)`（＝ `getBlobIndex()`）で位置を得て、呼び出し側が `setBinaryStream` で上書きする（`domain-entities.md` C-3「`OBJECT` カラムの扱い」）。

### BR-9. `FUNCTION` 型の値はバインドせずテキストにそのまま出す

`column.getType()` が `FUNCTION` のとき、現行は値をフィルタに通すだけで引用符も付けずテキストに出す（`SQLParser.java:114-116` の else 分岐）。`requirements.md` FR-7.1 は `DataType.FUNCTION` の値を SM-1 の判定対象外と明示している。

バインド経路も**バインドしない**。テキストに値をそのまま出し、`BindValue` を積まない。

**BR-9 は BR-4 に優先する。** BR-4 の表は `FUNCTION` を「値が null のときだけ SQL NULL」の行に含めているが、それはリテラル経路の記述である。バインド経路では `FUNCTION` の枝が先に確定し、値が null のときはテキストに文字列 `null` を出す（現行 `parseValue` の else 分岐が `convertValue(null)` すなわち null を返し、`StringBuffer.append((String) null)` が `"null"` を書き込むのと同じ結果。`SQLParser.java:41, :114-116`）。判定順序は `business-logic-model.md` § 2.7 の決定木に示す。

**この経路は呼び出し側の文字列が SQL 構文として生きたままテキストに入る。** FR-7.1 が対象外と定めた 4 経路の 1 つであり、U1 はこれを変えない。FR-7.2 が求める文書化の対象である。

---

## B. LIKE の規則（`ValueRules.escapeLike`）

### BR-10. LIKE の特別扱いには型ゲートがかかる

LIKE のエスケープ処理に入るのは、次の**両方**が成り立つときだけである。

1. `column.getType()` が `STRING` または `BOOLEAN`
2. `condition.getCondition().indexOf(" like ") >= 0`

NUMERIC / DATE / TIME カラムに `LIKE_*` を渡すと汎用分岐に落ち、ワイルドカードのエスケープを受けない。この型ゲートを維持する。

根拠: `SQLParser.java:48-50`。

### BR-11. LIKE 値の null は空文字に置き換える

`escapeLike` は最初に `value == null` を `""` に置き換える。根拠: `SQLParser.java:120-123`、`SQLParserTest.java:121`（`LIKE_PART, null` → `like '%%'`）。

### BR-12. エスケープ文字は「値に含まれない最初の候補」を選ぶ

値に `%` または `_` が **1 つでも含まれる**とき、候補 `$` `#` `~` `!` `^` をこの順に走査し、**値に含まれない最初の 1 文字**をエスケープ文字とする。走査対象は条件のラップを適用する**前**の生の値である。

根拠: `SQLParser.java:124-127`、`SQLParserTest.java:116`（値が `$ta_ma` のとき `$` が使われず `#` が選ばれる）。

### BR-13. エスケープの適用順序と、バインドされる値の範囲

エスケープ文字 `e` が選ばれたとき:

| 順 | 処理 | バインド値に含まれるか |
|---|---|---|
| 1 | 値の中の `%` → `e%`、`_` → `e_` に置換 | **含む** |
| 2 | 条件の `replaceHolder` を適用し、フレームワーク自身の `%` ラッパーを外側に付ける（この `%` は**エスケープしない**） | **含む** |
| 3 | `ValueConvertFilter` でクォートをエスケープ | **含まない**（FR-2.2 によりバインド経路では適用しない） |
| 4 | STRING / BOOLEAN なら `'...'` で囲む | **含まない**（バインドするため） |
| 5 | `escape 'e'` 句を付ける | テキスト側（バインド値ではない） |

**バインドされる値は手順 2 まで適用した文字列である。**

検算（既存テストの 5 件すべて）:

| 入力（`LIKE_PART`） | 現行のリテラル出力 | バインド版のテキスト | バインド値 |
|---|---|---|---|
| `"tama"` | `test1.name like '%tama%'` | `test1.name like ?` | `%tama%` |
| `"ta_ma"` | `test1.name like '%ta$_ma%' escape '$'` | `test1.name like ? escape '$'` | `%ta$_ma%` |
| `"$ta_ma"` | `test1.name like '%$ta#_ma%' escape '#'` | `test1.name like ? escape '#'` | `%$ta#_ma%` |
| `"%_%_"` | `test1.name like '%$%$_$%$_%' escape '$'` | `test1.name like ? escape '$'` | `%$%$_$%$_%` |
| `""` / `null` | `test1.name like '%%'` | `test1.name like ?` | `%%` |

根拠: `SQLParser.java:128-138`、`SQLParserTest.java:58, 114-121`、`component-methods.md` C-4「現行の処理順序と、バインド版での対応」。

### BR-14. 5 候補が枯渇したら、エスケープせず条件のラップだけを適用する

値が `$ # ~ ! ^` の**すべて**を含む場合、現行はループを抜けて `escape` 句なしの経路に落ち、`%` / `_` が有効なワイルドカードのまま出力される。バインド版もこの挙動に従う。

この経路に現在テストはない。FR-8.5（Should）が Build and Test（3.6）で挙動を明示的に固定する。**U1 はこの欠陥を「維持」するのであって、修正しない。**

根拠: `SQLParser.java:125-137` のループ脱出、`:138`、`requirements.md` FR-2.3 の補足。

---

## C. バインド適用の規則（`PreparedStatementBinder`）

### BR-15. バインド位置は 1 始まりで、リストの順序どおりに適用する

`values` の i 番目（0 始まり）を JDBC の `i + 1` 番目のパラメータに適用する。並べ替えを行わない。

**この規則が守られるかどうかは `PreparedStatementBinder` の外で決まる。** 値リストの順序がテキストの `?` の順序と一致することは `QueryImpl` / `Search`（U2 / U3）が節ごとの値アキュムレータで保証する（ADR-011、`component-dependency.md`「順序の不変条件」）。バインダは受け取った順に適用するだけである。

### BR-16. setter は `DataType` から選ぶ

| `DataType` | 適用する setter |
|---|---|
| `STRING`, `BOOLEAN` | `setString` |
| `NUMERIC`, `FLOAT` | `setString` |
| `DATE`, `TIME` | `setString` |
| `OBJECT` | `setBinaryStream` |
| `FUNCTION` | 到達しない（BR-9 により `BindValue` が作られない） |
| `isNull()` が真（型を問わず） | `setNull`（BR-17） |

NUMERIC / FLOAT に `setString` を使うのは ADR-007 の決定である。現行のリテラル経路が数値を引用符なしで埋め込み、型変換を DB エンジンに委ねている挙動を変えないための選択であり、`ValueRules.validate`（BR-2）が型検証を担うため setter で型を絞る必要がない。

### BR-17. `setNull` の SQL 型は `DataType` の意味に沿って割り当てる

| `DataType` | `setNull` の第 2 引数 |
|---|---|
| `STRING`, `BOOLEAN` | `java.sql.Types.VARCHAR` |
| `NUMERIC`, `FLOAT` | `java.sql.Types.NUMERIC` |
| `DATE` | `java.sql.Types.DATE` |
| `TIME` | `java.sql.Types.TIMESTAMP` |
| `OBJECT` | `java.sql.Types.BLOB` |
| `FUNCTION` | `java.sql.Types.OTHER`（到達しない） |

ADR-007 が値に一律 `setString` を選んだ根拠は「現行の DB 側の型変換を維持する」ことであり、NULL には変換すべきテキストがないためこの根拠が及ばない。一方、型に厳格なドライバは NUMERIC カラムへの `Types.VARCHAR` を拒否しうる。

`TIME` → `TIMESTAMP` とする根拠: `MappingUtils.TIME_FORMAT`（`MappingUtils.java:24`）が `"yyyy-MM-dd HH:mm:ss"` であり、TIME カラムが時刻ではなく日時を運んでいる。`MappingUtils.mapping` の `TIME` 分岐（`:44-45`）にも「2014-01-01 00:00:00.0 (MySQL)」というコメントがある。

**この規則はモックテストでは検証できない**（`MockPreparedStatement.setNull` は空実装、`MockPreparedStatement.java:47-48`）。実 DB での確認は FR-8.3（`UserDaoTest2` / Derby）が最初の機会になる（ADR-007 の Negative、OQ-6）。

### BR-18. バインド経路で `ValueConvertFilter` を呼ばない

`BindSqlBuilder` は `ValueConvertFilter` を保持せず、呼ばない。クォートのエスケープは JDBC ドライバの責務に移る。

これが **AC-6**（シングルクォートを含む値が `''` に二重化されない）を満たす規則である。

根拠: FR-2.2、`components.md` C-5「`ValueConvertFilter` を呼ばない」。

### BR-19. ストリーム値の `BindValue` は 1 回しかバインドできない

`BindValue.ofStream(in)` が保持する `InputStream` は 1 回しか読めない。同じ `BindValue` を含む `PreparedSql` を 2 回実行してはならない。

現行 `BlobUtils.executeUpdate(PreparedStatement, int, InputStream)`（`BlobUtils.java:15-23`）が持つ制約と同じであり、新しい論点ではない。テキスト値・NULL 値の `BindValue` にはこの制約はなく、何度でも再バインドできる。

### BR-20. `SQLException` は `DaoException` に包む

`PreparedStatementBinder.bind` および `DBAccessManager` の `PreparedSql` 版実行メソッドは、`SQLException` を `DaoException` に包んで投げる。値の検証違反は `InvalidParameterException`（`IllegalArgumentException` のサブクラス）である。この 2 系統の使い分けは現行と同一である（`component-methods.md`「記法」節）。

---

## D. プレースホルダ計数と実行前検査

### BR-21. プレースホルダの数え方は 1 か所に定める

`Param.of` / `PreparedSql.of` / `PreparedSql.getPlaceholderCount()` はすべて同じ走査を使う。

```
引用符の外にある ? だけを数える。
'' は引用符の内側にあるエスケープされた ' として読み飛ばす。
走査終了時に引用符が閉じていなければ「走査不能」（-1）を返す。
```

### BR-22. この走査が現行のすべての `ValueConvertFilter` に対して正確であることを前提とする

現行の 3 実装はいずれも `'` → `''` の倍化を行い、**単独の `\'` を生成しない**。

| 実装 | 変換 | 根拠 |
|---|---|---|
| `Search.DefaultValueConvertFilter` | `'` → `''` のみ | `Search.java:99-107` |
| `OracleSearch.OracleValueConvertFilter` | `'` → `''` のみ（null ガードなし。FR-6.4 / U4 の担当） | `OracleSearch.java:11-15` |
| `MySQLSearch.MySQLValueConvertFilter` | `'` → `''` を**先に**適用してから `\` → `\\` を適用 | `MySQLSearch.java:11-20` |

MySQL 版は `'` を先に倍化するため、後段の `\` 倍化で `\'` という組み合わせが生じない。したがって「`''` をエスケープとして読み飛ばし、バックスラッシュを通常文字として扱う」走査で引用符の境界を正しく判定できる。

**利用側が独自の `ValueConvertFilter` を実装している場合、この前提は保証されない。** その場合の挙動は BR-23 が定める。

### BR-23. 走査が判定不能なら実行を拒否する

走査終了時に引用符が閉じていない場合、`getPlaceholderCount()` は `-1` を返し、`hasUnboundPlaceholders()` は `true` を返す。`DBAccessManager` の `PreparedSql` 版実行メソッドはこれを `DaoException` で拒否する。

**エラーメッセージは「未束縛のプレースホルダがある」場合と区別する。** 走査不能の場合は、SQL テキスト中の引用符が閉じていないため検査できなかったことを明示する。呼び出し側が独自の `ValueConvertFilter` を使っている場合の唯一の手がかりになる。

この判断は推奨（走査不能なら通す）と異なるユーザーの選択である。安全側に倒すことで、走査が想定外の SQL を受け取った場合に既存の呼び出しを拒否しうる。到達経路は `ofLiteral` 由来の `PreparedSql`（旧 override 経由）に限られる。

### BR-24. 生成時の個数検査は `of(...)` にだけ適用する

| ファクトリ | 個数検査 | 根拠 |
|---|---|---|
| `Param.of(sql, values)` | **行う。** 不一致なら `InvalidParameterException` | ADR-001 |
| `PreparedSql.of(sql, values)` | **行う。** 不一致なら `InvalidParameterException` | ADR-001 |
| `PreparedSql.ofLiteral(sql)` | **行わない** | ADR-012 |

`ofLiteral` が検査しない理由は、旧経路が生成する SQL が BLOB カラムを含む場合に未バインドの `?` を持つためである。検査を強制すると既存サブクラスがすべて生成時に落ちる（NFR-3 違反）。

`of(...)` の走査が判定不能（`-1`）だった場合も `InvalidParameterException` とする。新経路が生成するテキストは引用符が閉じないことがありえず（値がテキストに入らないため）、判定不能は組み立て側の欠陥を意味する。

### BR-25. 個数検査は順序のずれを検出しない

`?` の個数と値の個数が一致していても順序が違う場合、BR-24 の検査は通る。JDBC のパラメータ数検査も通る。**誤った値が誤ったカラムに適用された SQL が正常終了する。**

この失敗様式を検出できるのは FR-8.1（テストからの位置と値のアサート）だけである。両者は補完関係にあり、どちらか一方では足りない。

根拠: `component-dependency.md`「順序の検査が行われる 3 か所」、ADR-011 の Negative。

### BR-26. 実行前検査は `DBAccessManager` が行う

`PreparedSql` 版の実行メソッドは、`prepareStatement` を呼ぶ**前に** `hasUnboundPlaceholders()` を検査し、真なら `DaoException` を投げる。ドライバに渡す前に落とすことで、エラーが「BLOB を含む旧経路を新実行経路に流した」ことに起因すると分かるメッセージを出せる（ADR-012）。

---

## E. 実行記録の規則（`DBAccessManager`）

### BR-27. 記録は実行の前に行う

現行 `executeQuery(String)` / `executeUpdate(String)` は `getExecutedQuery().add(sql)` を実行の前に行っている（`DBAccessManager.java:98-100, :107-109`）。`PreparedSql` 版もこれに揃える。実行が失敗しても、何を実行しようとしたかが記録に残る。

### BR-28. 新経路は 2 つのリストの両方に記録する

| リスト | 記録内容 | 型 |
|---|---|---|
| `getExecutedQuery()` | SQL テキストのみ（`?` を含む） | `List<String>`（**不変**） |
| `getExecutedStatements()` | SQL テキスト + バインド値 | `List<ExecutedStatement>`（新規） |

`getExecutedQuery()` の型もアサーションの形も変えない（ADR-008、FR-3.1）。既存 e2e テストの経路が生き続ける。ただし**記録される SQL の中身は `?` 入りに変わる**ため、実行経路を通るテストのアサーション文字列は書き換えが必要である（FR-8.2 の対応表の対象。U2 / U3 / U4 の担当）。

これが **AC-8**（実行直後の `getExecutedQuery()` に SQL、記録にバインド値）を満たす規則である。

### BR-29. バインド値をマスクしない

`ExecutedStatement` は渡された値をそのまま保持する。既定オフのスイッチもマスク手段も設けない（FR-4.2）。

この判断の根拠は「現行もリテラルが埋め込まれた SQL を記録しており、値は既に平文で含まれているため露出面は増えない」ことである。`services.md` R-5 が記録するとおり、この記録は `ThreadLocal` のリストとして利用側プロセスのメモリ内に平文で存在する。

### BR-30. `getExecutedStatements()` のライフサイクルは `getExecutedQuery()` に揃える

| 操作 | `executedQuery` の現行挙動 | `executedStatements` |
|---|---|---|
| 初回参照 | null なら空リストを作って `set` | 同じ |
| `release()` | **削除しない** | 削除しない（揃える） |
| `shutdown()` | `remove()` する（`:209`） | `remove()` する |

根拠: `DBAccessManager.java:181-188`（`getExecutedQuery`）、`:206-215`（`shutdown`）。

---

## F. 旧経路（`SQLParser`）の不変性

### BR-31. `SQLParser` の公開メソッドの出力を 1 文字も変えない

`value(...)` / `parseValue(...)` / `parseLikeStringValue(...)` / `parseMultiValue(...)` / `isNumeric(...)` は `ValueRules` に委譲するが、**戻り値の中身は現行と完全に一致する**。

検証手段は既存の `SQLParserTest`（30 アサーション）である。**このテストを 1 行も変更してはならない。** 変更が必要になったら、それは委譲が挙動を変えた証拠である。

根拠: ADR-003、ADR-005、`bolt-plan.md` Bolt 2 の Definition of Done「`SQLParser` の公開メソッドの戻り値が 1 文字も変わらない（`SQLParserTest` が無変更で緑）」。

### BR-32. `ValueConvertFilter` はリテラル経路にだけ残す

`ValueRules` は `ValueConvertFilter` を保持しない。クォートのエスケープは `SQLParser` 側に残る。

`ValueRules` に含めると、バインド経路が `ValueRules` 経由でフィルタを適用してしまい FR-2.2（二重エスケープの回避）に反する。根拠: ADR-005 の Neutral。

---

## G. 検証基盤の規則（mock 拡張）

### BR-33. `MockConnection.prepareStatement(String)` は SQL を保持して渡す

現行は引数を破棄している（`MockConnection.java:168-170`）。拡張後は生成する `MockPreparedStatement` に SQL を渡し、`getPreparedSql()` で取り出せるようにする。

### BR-34. `MockPreparedStatement` の各 `setXxx(int, ...)` は位置と値を記録する

記録は 1 始まりの位置をキーとする。同じ位置に 2 回 set された場合は**後勝ち**とする（実ドライバの挙動と揃える）。

- `getPreparedSql()` — `prepareStatement` に渡された SQL
- `getBoundValue(int index)` — 指定位置にバインドされた値
- `getBoundValues()` — 位置順の値一覧

後勝ちの規則は BR-8 の BLOB 経路（`setNull` の後に `setBinaryStream` で上書きする）が正しくアサートできるために必要である。

### BR-35. `MockPreparedStatement.executeUpdate()` は `0` を返し続ける

現行挙動（`MockPreparedStatement.java:41-44`）を変更しない。既存テストへの影響を避けるためである（`component-methods.md` C-8）。

**注意**: 親クラス `MockStatement.executeUpdate(String)` は `1` を返す（`MockStatement.java:71-74`）。`UserDaoTest.testCreate` は `assertEquals(1, result)` をアサートしており（`UserDaoTest.java:39`）、現行は `Statement` 経路を通るため `1` が返っている。**新経路は `PreparedStatement` を通るため `0` が返り、このアサーションは失敗する。** これは U2 / U3 が `Dao` の実行を切り替えたときに顕在化する既知の影響であり、FR-8.2 の対応表の対象である。U1 の範囲では `MockPreparedStatement.executeUpdate()` を変更しない。

### BR-36. テストが `MockPreparedStatement` に到達する経路を用意する

`MockDriver` は static 初期化子で 1 インスタンスを `DriverManager` に登録し、そのインスタンスは 1 本の `MockConnection` を保持して `connect()` で常に同じものを返す（`MockDriver.java:21-43`）。この登録済みインスタンスへの参照を static フィールドに保持し、`MockDriver.getInstance()` で取得できるようにする。

`MockConnection` には次を追加する。

- `getPreparedStatements()` — 生成順の `MockPreparedStatement` 一覧
- `getLastPreparedStatement()` — 最後に生成されたもの
- `clearPreparedStatements()` — 記録をクリアする

**`MockConnection` は全テストで共有されるため、記録はテスト間で蓄積する。** アサートが最後の 1 本だけを見る場合は `getLastPreparedStatement()` で足りるが、複数本を数えるテストは `@Before` で `clearPreparedStatements()` を呼ぶ必要がある。

### BR-37. `MockDriver.acceptsURL` の挙動は変えない（ただし前提として記録する）

`MockDriver.acceptsURL` は URL を問わず常に `true` を返す（`MockDriver.java:37-39`）。BR-36 が `DriverManager` 経由ではなく `getInstance()` を使う理由がここにある——FR-8.3 / OQ-6 で Derby を classpath に加えると、`DriverManager.getConnection` が返すドライバが登録順に依存しうる。

この挙動自体は U1 の変更対象ではない。Build and Test（3.6）で Derby を有効化する際に確認を要する事項として記録する。

---

## 規則と要件・受け入れ条件の対応

| 規則 | 満たす要件 | 判定する AC |
|---|---|---|
| BR-1, BR-2, BR-3 | FR-2.1（NUMERIC / FLOAT の型検証を維持） | **AC-5** |
| BR-18 | FR-2.2（バインド経路でクォートエスケープを適用しない） | **AC-6** |
| BR-10〜BR-14 | FR-2.3（LIKE の `%` / `_` エスケープと `escape 'X'` 句の維持） | AC-2（判定は U2 完了時） |
| BR-4, BR-5, BR-6, BR-7 | FR-2.4（DATE / TIME の空値・`NULL`・`current_timestamp` の維持） | — |
| BR-27, BR-28, BR-30 | FR-4.1（実行記録にバインド値を含める） | **AC-8** |
| BR-29 | FR-4.2（記録に機微データ上の追加条件を設けない） | — |
| BR-33〜BR-36 | FR-8.1（バインド位置と値をテストからアサートできる） | — （Bolt 1 の DoD が判定） |
| BR-8, BR-16, BR-17 | FR-1.5（BLOB のバインド） | AC-7（判定は U3 完了時） |
| BR-31, BR-32 | FR-3.1 / NFR-3（public API の後方互換性） | AC-11（判定は U3 完了時） |
| BR-21〜BR-26 | ADR-012（未束縛プレースホルダの早期拒否） | — |
| BR-9 | FR-7.1（`DataType.FUNCTION` は SM-1 対象外、現行維持） | — |

**U1 が単独で判定できる AC は AC-5 / AC-6 / AC-8 の 3 件である**（`unit-of-work-story-map.md`「AC → Unit のマッピング」）。AC-2 / AC-7 / AC-11 は U1 が規則を提供し、判定は後続 Unit の完了時に行われる。

---

## 規則が破れたときの失敗様式

設計の弱い箇所を明示する。実装とテストはここを重点的に守る必要がある。

| # | 破れる規則 | 症状 | 検出する手段 |
|---|---|---|---|
| 1 | BR-15 の前提（値リストの順序がテキストの `?` の順序と一致する） | **個数が一致したまま全ての値がずれた SQL が正常終了する。** 例外も出ない | FR-8.1 の位置・値アサート（BR-34）**のみ**。BR-24 の個数検査では捕まらない |
| 2 | BR-13 の手順 2（フレームワークの `%` ラッパーをエスケープしてしまう） | LIKE の前方一致・部分一致が一致しなくなる | 既存 `SQLParserTest` は**捕まえない**（リテラル経路のテストであるため）。バインド版のアサートを別途置く必要がある |
| 3 | BR-5 と BR-6 の非対称を取り違える | STRING カラムへの null が、単一値では `''`、IN では `null` になるはずが片方に揃ってしまう | `SQLParserTest.java:61, 129-130` がリテラル経路を固定する。バインド版は同等のテストを追加する必要がある |
| 4 | BR-31（`SQLParser` の出力が変わる） | 既存の広範なテストが落ちる | `SQLParserTest` が即座に検出する。**この規則だけは既存テストが十分に守っている** |
| 5 | BR-22 の前提（独自 `ValueConvertFilter` が閉じない引用符を生成する） | BR-23 により `DaoException` で実行が拒否される | 現行 3 実装では再現しない。利用側の独自実装でのみ到達しうる |

# Security Requirements — U2 `select-path`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U2）— § 2（`Search` の 3 状態と private `append`）、§ 3（`QueryImpl` の 2 状態と `appendWhere` / `addWhere` 3 形）、§ 4.2（`getSelectPreparedSql()` の組み立てと値の結合順）、§ 5（サブクエリ）、§ 6（`Query` の `default` 9 個と非推奨 6 個）、§ 7.3（`andOuterJoin` の 3 つの入口）、§ 8（`Dao` / `DaoAdapter` の実行経路）、§ 10（新規 public / protected メンバ）、§ 11（後続 Unit への引き継ぎ）。本文書の脅威モデルはこの経路を対象にする。
- **`business-rules.md`**（同上）— BR-1 / BR-3 / BR-5（同期規則）、BR-2（連結規則）、BR-6 / BR-7 / BR-8 / BR-9（順序規則）、BR-10（旧戻り値の不変）、BR-11（`default` のフォールバック）、BR-12（**本ステージで修正**）、BR-13（キー不在時の欠陥の温存）、BR-16（`handleException` の funnel）、BR-19（値ゼロの `Param` と早期失敗。**列挙を本ステージで修正**）、BR-20（移行）、F 節の R-1〜R-6、H 節の失敗様式 1〜6。本文書はこれらのうちセキュリティに効くものを要件として固定する。
- **`requirements.md`**（requirements-analysis 2.3）— NFR-1（判定基準は「実行経路に値の文字列連結が 0 件」）、NFR-2、NFR-3、NFR-5、NFR-6、FR-1.1 / 1.2 / 1.3 / 1.6 / 1.7、FR-3.1 / 3.3、FR-7.1 / 7.2 / 7.3、CON-3 / CON-4 / CON-6 / CON-7、AC-1〜AC-4 / AC-11、OQ-9（U5 へ）。
- **`technology-stack.md`**（codekb）— compile スコープ依存が `tamacat-core` と `javax.json` だけであること、テストスタックが JUnit 4 ＋ `org.tamacat.mock.sql` であること、ビルド JVM が JDK 25 Corretto であること。
- **U1 `bind-foundation` の `security-requirements.md` / `tech-stack-decisions.md`** — SEC-1〜SEC-9、R-1〜R-7、TSD-1〜TSD-5。U2 はこれらを継承し、`Search` / `QueryImpl` / `Dao` の経路について同じ判定基準を適用する。

`tech-stack-decisions.md` は本文書と対になる成果物で、Q2（カバレッジゲート）と Q4（移行ガイド）を扱う。

---

## この文書の範囲

**U2 も `kind: library` である**（`unit-of-work.md` U2 の表）。したがって U1 と同じく、認証・認可・暗号化・規制フレームワーク・データ所在地はいずれも**該当なし**である（U1 `security-requirements.md`「この文書の範囲」の表をそのまま継承する。tamacat-dao はデプロイ可能なプロセスを 1 つも持たない）。

**U1 と U2 でセキュリティ面の性質が変わる。**

| | U1 `bind-foundation` | U2 `select-path` |
|---|---|---|
| Unit の性質 | 新規クラス 8 個を足す | 新規クラス 0 個。既存 5 クラス（`Query` / `QueryImpl` / `Search` / `Dao` / `DaoAdapter`）を書き換える |
| 主な問い | 新しく作る面（値の平文保持、mock の記録、例外メッセージ）をどう閉じるか | **既存経路のどれをバインド化の対象に含めるか**と、**書き換えの正しさをどう保証するか** |
| 中心的な脅威 | 値が SQL 構文として解釈される（T）＋ 値がプロセス内に平文で露出する（I） | 値が SQL 構文として解釈される（T）＋ **述語が黙って欠落し WHERE が緩くなる（T の別形）** |

U2 が新たに作る「情報の露出面」は**ない**——`Search.getSearchParam()` は値を `List<BindValue>` として返すが、同じ値は既に `getSearchString()` がリテラル埋め込みのテキストとして返している（SEC-14 で扱う）。代わりに U2 は、**正しく書けていないと安全性がその場では見えない形で失われる**という新しい性質を持ち込む。本文書の中心はそこにある。

---

## 脅威モデル（STRIDE）

`threat-modelling-stride.md` の 6 カテゴリを U2 に当てる。**該当しないカテゴリはその旨を書く**——空欄にすると「見落とし」と「非該当」の区別が付かないためである。

| カテゴリ | U2 での評価 | 対応 |
|---|---|---|
| **S** Spoofing | **該当なし。** ライブラリは同一性を主張する主体を持たない | — |
| **T** Tampering | **中心的関心。2 つの形を持つ。** (1) 値が SQL 構文として解釈される——`Search` / `QueryImpl` / サブクエリの各経路をバインド化することで構造的に解消する。(2) **述語の欠落**——同期規則（BR-1 / BR-3 / BR-5）が破れると WHERE 条件が緩い SQL が正常に実行される。認可条件が述語で表現されている利用側では行の漏洩に直結する | SEC-10、SEC-11、SEC-12、SEC-13、SEC-16 |
| **R** Repudiation | **該当なし。** U2 は実行記録の仕組みに触れない。`DBAccessManager` の `getExecutedQuery()` / `getExecutedStatements()` は U1 が定義し、U2 はそこに `PreparedSql` を渡すだけである。U1 SEC-9（実行記録を監査ログとして位置づけない）をそのまま継承する | 継承 |
| **I** Information Disclosure | **U2 は新しい面を作らない**（`Search.getSearchParam()` の public 化は既存の `getSearchString()` と同じ値を別表現で返すだけ）。ただし**例外メッセージ**には新しい経路が加わる（BR-19 の早期失敗、`Param.of` / `PreparedSql.of` の個数検査） | SEC-14、SEC-15 |
| **D** Denial of Service | **限定的。** U2 は繰り返しやバッファリングを持ち込まない。`Search` / `QueryImpl` の蓄積は単回使用（CON-7）で呼び出し回数に線形である | R-11 |
| **E** Elevation of Privilege | **該当なし。** 権限モデルを持たない。識別子位置（FR-1.7、`orderBy` と `col.getFunctionName()`）は「値から構文への昇格」に相当するが **U5 `identifier-safety` の担当**であり U2 の範囲外である（BR-7、`business-rules.md` R-4） | R-12 |

---

## セキュリティ要件

判定基準は**機械的に確認できる形**で書く。`nfr-requirements-guide.md`「Security Anti-Requirements」に従い、「安全であること」のような測れない表現は使わない。番号は U1 の SEC-1〜SEC-9 に続けて SEC-10 から振る。

### SEC-10. SELECT の実行経路に値の文字列連結を残さない

| 項目 | 内容 |
|---|---|
| 要件 | `Dao.search(Query)` / `Dao.searchList(Query, int, int)` から `java.sql.PreparedStatement` に至る経路で、呼び出し側から渡された値が SQL テキストに文字列連結されないこと。値は `BindValue` としてのみ運ばれる |
| 対象経路 | `Search.and` / `or`（`Column` 形と `Search` 形）→ `Search.getSearchParam()` → `QueryImpl.addSearch` → `appendWhere` → `getSelectPreparedSql()` → `Dao.executeQuery(PreparedSql, ResultSetHandler)` → `DBAccessManager` |
| 例外 | (1) `DataType.FUNCTION` と SQL 関数（U1 SEC-1 の例外をそのまま継承）。(2) `where(String)` / `and(String)` / `or(String)` の**直接利用**（FR-7.1、R-10）。(3) 外部の `Query` 実装が `default` のフォールバックを使う場合（BR-11、R-13） |
| 判定基準 | `getSelectPreparedSql()` が返す `PreparedSql` のテキストに、`Search` / `QueryImpl` の値位置に渡された文字列が現れない。**AC-1**（STRING の EQUAL）、**AC-2**（LIKE）、**AC-3**（IN の `(?,?,?)`）、**AC-4**（サブクエリ）が mock のバインド値アサートで判定する |
| 由来 | NFR-1、FR-1.1 / 1.2 / 1.3 / 1.6、`business-logic-model.md` § 2〜§ 5 |

### SEC-11. 非推奨 `andOuterJoin(Table, Search)` もバインド経路に載せる

| 項目 | 内容 |
|---|---|
| 要件 | `andOuterJoin(Table, Search)` を `@Deprecated` にしたうえで、**バインド面には `search.getSearchParam()` を使う**こと。リテラル面（`outerJoinTables`）は `search.getSearchString()` のまま維持し、`getSelectSQL()` の戻り値を変更しないこと |
| 判定基準 | 値を持つ `Search` を `andOuterJoin(Table, Search)` に渡して組み立てた `Query` について、(a) `getSelectSQL()` の戻り値が変更前と一致し、(b) `getSelectPreparedSql()` の FROM 句に値のリテラルが含まれず該当位置に `?` が現れ、(c) その値が FROM 句の値として WHERE の値より前に渡されている（BR-6） |
| 由来 | **本ステージ Q1 = B。3.1 の BR-12 / § 7.3 / R-1 を修正する**（下記「3.1 の規則に対する修正」） |

**この要件が閉じるもの。** 3.1 の設計（BR-12）は、非推奨版のバインド面にも `search.getSearchString()`（値をリテラルとして埋め込んだテキスト）を積み、この経路を「SM-1 対象外」としていた。しかしその免除を与えている上流要件は存在しない——**FR-7.1 の列挙は閉じており `andOuterJoin` を含まず、FR-1 の列挙にも含まれない**。加えて FR-7.1 の免除理由（「渡されるのは開発者が書いた SQL であり実行時のユーザー入力ではない」、Q5 = A）は、`String` ではなく **`Search` を受け取る**この経路には成立しない。これは 2.6 の追跡 F3 が `Dao.param(...)` について発見したのと同じ構図であり、そのとき FR-7.1 の範囲を限定して FR-7.3 の代替経路を用意した判断（ADR-002）が、そのままこの経路にも当てはまる。

**コストが実質ゼロであることが選択を決めた。** `getSearchParam()` は `Search` に public として既に存在し（§ 2.4）、値の合成は新設の `andOuterJoin(Table, Param)`（§ 7.3）と同一の 4 行である。**旧 API の観測可能な挙動は変わらない**——`getSelectSQL()` はリテラル面（`buildFromClause(false, ...)`）から作られるため BR-10 を満たす。変わるのは `getSelectPreparedSql()` の出力だけであり、これは U2 が新設する API で保存すべき旧挙動を持たない。

**呼び出し元は 0 件である**（`grep -rn "andOuterJoin" src/` の結果は `Query.java:101` の宣言と `QueryImpl.java:351` の実装のみ）。したがってリポジトリ内のテストは 1 件も動かない。この決定は外部利用者に対する保証の話である。

### SEC-12. 同期規則を構造で守る

| 項目 | 内容 |
|---|---|
| 要件 | `Search` の 3 状態（`search` / `bindSearch` / `bindValues`）、`QueryImpl` の WHERE 2 状態（`where` / `bindFragments`）、FROM 2 状態（`outerJoinTables` / `bindOuterJoinTables`）を直接変更するのは、それぞれ**単一の private メソッド**（`Search.append` / `QueryImpl.appendWhere` / `QueryImpl.putOuterJoin`）だけであること |
| 判定基準 | ソース検査で確認できる。`search` / `bindSearch` / `bindValues` / `bindFragments` / `outerJoinTables` / `bindOuterJoinTables` への書き込み（`append` / `add` / `put` / `addAll`）が、上記 3 メソッドの本体以外に現れない |
| 由来 | BR-1 / BR-3 / BR-5、`unit-of-work.md` U2「最大の設計リスク」 |

**なぜ検査ではなく構造か。** `Param.of(...)` / `PreparedSql.of(...)` の個数検査は「`?` の個数 ≠ 値の個数」を捕まえるが、**リテラル面とバインド面の両方に append し忘れた場合は個数が一致したまま述語が欠落する**。個数検査も JDBC のパラメータ数検査も通り抜ける。

### SEC-13. 同期破れを検出するテストを置く

| 項目 | 内容 |
|---|---|
| 要件 | 同一の `Search` / `QueryImpl` からリテラル面とバインド面の両方を取得し、**トップレベルの述語数と連結子の並びが一致すること**をアサートするテストを置くこと |
| 対象メソッド | `Search.and(Column, Conditions, String...)` / `Search.or(同)` / `Search.and(Search)` / `Search.or(Search)` / `QueryImpl.where(String)` / `and(String)` / `or(String)` / `join(Column, Column)` / `where(Search, Sort)` / `andIn` / `andNotIn` / `andExists` / `andNotExists` / `outerJoin(Column, Column)` / `andOuterJoin(Table, Search)` / `andOuterJoin(Table, Param)` |
| 検証手段 | 既存の public アクセサのみで書ける——`Search.getSearchString()` 対 `Search.getSearchParam().getSql()`、`QueryImpl.getSelectSQL()` 対 `QueryImpl.getSelectPreparedSql().getSql()`。**新しい API を足す必要はない** |
| 判定基準 | 上記 16 経路それぞれについて、両面の述語数（トップレベルの `and` / `or` の数 ＋ 1）と連結子の並びが一致するアサートが存在すること |
| 実施先 | **Build and Test（3.6）**。本ステージは形と対象を確定し、判定可能な要件として引き渡す |
| 由来 | 本ステージ Q3 = A、`business-rules.md` H 節 1 / 2、`business-logic-model.md` § 11-7 |

**対象 16 経路の網羅性。** 3 つの同期状態対（`Search` の 3 状態、`QueryImpl` の WHERE 2 状態、FROM 2 状態）に書き込む public / protected の入口を全数で数えた結果である。**`where(Param)` / `and(Param)` / `or(Param)` を含まないのは意図的である**——これら 3 つは単一の `Param` からリテラル面とバインド面の両方を導出する（`addWhere(String, Param)` が `addWhere(condition, param.getSql(), param)` に委譲する。`business-logic-model.md` § 3.3）。両面が同一の値から作られるため、**構造的に同期が破れえない**。同じ理由で `addSearch` は `where(Search, Sort)` として 1 経路に数えてある（`where` / `and` / `or` の `Search` 形は同じ `addSearch` を通る）。

**この要件が埋める穴。** 同期破れは既存の検査器のどれにも掛からない。

| 検査 | 検出するか | 理由 |
|---|---|---|
| `Param.of` / `PreparedSql.of` の個数検査 | **しない** | 両面を append し忘れると `?` の個数と値の個数は一致したままである |
| JDBC のパラメータ数検査 | **しない** | 同上 |
| line coverage | **しない** | 行が実行されないのではなく、別の行が実行される（`tech-stack-decisions.md` TSD-6） |
| `SearchTest` / `QueryImplTest`（既存） | **しない** | リテラル面だけをアサートしており、BR-10 によりその戻り値は不変である |

症状は「**WHERE 条件が緩い SQL が正常に実行される**」——例外も警告も出ず、意図より多い行が返る。**U2 で最も危険な失敗様式である。**

**実行時検査（`getSelectPreparedSql()` 内での突き合わせ）を採らない理由**: (1) すべてのクエリ実行に文字列走査のコストが乗り、NFR-7 / OOS-2（性能特性に触れない）の趣旨に照らして正当化しにくい。(2) リテラル面の述語数を数えるには `where` の文字列をパースする必要があり、その走査器自体が新しい欠陥源になる。(3) 本番で発火したときの挙動が「クエリが実行時例外で落ちる」になり、ライブラリのバージョンアップで既存アプリが壊れうる。

### SEC-14. `Search.getSearchParam()` の public 化は新しい露出面を作らない

| 項目 | 内容 |
|---|---|
| 要件 | `Search.getSearchParam()` が返す `Param` は、`getSearchString()` が既に返している同じ値を別表現で運ぶものであること。**値のマスク・秘匿は行わない**（U1 SEC-5 の方針を継承する） |
| 判定基準 | ソース検査で確認できる。`getSearchParam()` は `Param.of(bindSearch.toString(), bindValues)` を返すのみで、追加の情報（接続情報・呼び出し元・スタック等）を含まない |
| 由来 | 本ステージ（STRIDE-I）、`business-logic-model.md` § 2.4 |

**public にする必然性**: `Search` は `org.tamacat.dao`、`QueryImpl` は `org.tamacat.dao.impl` であり、Java の package-private は別パッケージに及ばない（§ 2.4、`decisions.md` レビュー指摘 F-1）。

**露出が増えないことの根拠**: 同じ述語の値は `getSearchString()` が返すテキストに**リテラルとして既に埋め込まれている**。`getSearchParam()` はそれを `?` ＋ `List<BindValue>` に分けて返すだけである。値の平文がプロセス内に存在すること自体は U1 の R-4 として既に受容済みであり、U2 はその範囲を広げない。

### SEC-15. 例外メッセージにバインド値を含めない（U1 SEC-4 を U2 の経路に適用する）

| 項目 | 内容 |
|---|---|
| 要件 | U2 が新たに例外を投げうる経路（BR-19 の早期失敗、`Param.of` / `PreparedSql.of` の個数検査、`Search.append` の引数評価時の `InvalidParameterException`）のメッセージに、`BindValue` の内容を含めないこと。**SQL テキストは含めてよい** |
| 判定基準 | ソース検査で確認できる。U2 が組み立てる例外メッセージに `getValues()` 由来の文字列が現れない |
| 由来 | U1 SEC-4 の継承、`security-guide.md` OWASP #9 |

**U2 が加える経路は 1 つだけである。** `Param.of` / `PreparedSql.of` の検査は U1 が実装しており（U1 BR-21 / BR-24）、メッセージも U1 が定める。U2 が新たに作るのは **BR-19 の帰結**——`where(String)` 等に渡されたテキストが引用符の外に `?` を含むと、`Param.of` の個数検査が `InvalidParameterException` を投げる。このとき渡されたテキストは呼び出し側が書いた SQL であり、バインド値ではない。したがってメッセージに含めても U1 SEC-4 に抵触しない。

**BR-19 は「より早く、より明確に失敗する」変更である。** 現行はその SQL がそのまま DB に渡り構文エラーになる。どちらも失敗する点は同じで、失敗する場所と例外の型が変わる。

### SEC-16. サブクエリの値は連続ブロックとして正しい位置に入る

| 項目 | 内容 |
|---|---|
| 要件 | `andIn` / `andNotIn` / `andExists` / `andNotExists` が子 `Query` の `getSelectPreparedSql()` を使い、子のテキストと子の値を**単一の `WhereFragment`** にまとめて親の WHERE に差し込むこと |
| 判定基準 | **AC-4** — 子 Query の値が、親のバインドパラメータ列のうち子クエリの差し込み位置に対応する位置に渡されている |
| 由来 | FR-1.6、BR-9、`business-logic-model.md` § 5 |

**この失敗様式も例外を出さない。** 子の値が親の誤った位置に入っても個数は一致するため、`PreparedSql.of` の検査を通り抜ける。検出できるのは U1 の mock による位置・値アサート（U1 BR-34）だけである（`business-rules.md` H 節 4）。断片リスト構造（Q1 = C）を採ったのはこの一致を構造的に成立させるためであり、値リストが 1 本の設計なら差し込み位置を明示的に管理する必要があった。

### SEC-17. 新規の第三者 compile 依存を追加しない（U1 SEC-8 の継承）

| 項目 | 内容 |
|---|---|
| 要件 | U2 は compile スコープの第三者依存を 1 つも追加しない。`tamacat-core` と `javax.json` のみという現状を維持する |
| 判定基準 | `mvn dependency:tree` の compile スコープが変更前と一致する |
| 由来 | CON-6、U1 SEC-8、`tech-stack-decisions.md` TSD-7 |

U2 が新設するメンバはすべて JDK 標準 API（`java.util` / `java.sql`）だけで書ける。Q4 = B が追加する移行ガイドは Markdown ファイルであり依存ではない。

---

## 残存リスク

**受容するもの**と**後段に送るもの**を分けて記録する。いずれも隠さない。番号は U1 の R-1〜R-7 に続けて R-8 から振る。

| ID | 残存リスク | 由来 | 扱い |
|---|---|---|---|
| **R-8** | `where(Param)` / `and(Param)` / `or(Param)` / `andOuterJoin(Table, Param)` で組み立てた `Query` の `getSelectSQL()` は、**未束縛の `?` を含む SQL** を返す。これを `ofLiteral` 経由で実行すると U1 の `hasUnboundPlaceholders()` が `DaoException` で拒否する | `business-logic-model.md` § 3.3、ADR-012 | **受容。** これらは FR-7.3 で新設するメソッドであり保存すべき旧挙動を持たない。診断可能な失敗であり、値が漏れる方向の失敗ではない |
| **R-9** | 非推奨 `andOuterJoin(Table, Search)` は `outerJoinTables` にキーがないと**黙って何もしない**。新メソッド `andOuterJoin(Table, Param)` も同じ | BR-13、`QueryImpl.java:350-356` | **受容（温存）。** 是正すると `outerJoin(...)` を呼ばずに `andOuterJoin(...)` を呼んだ場合の挙動が変わる。FR-6 群が列挙した是正対象 4 件に含まれない。**呼び出し元 0 件** |
| **R-10** | `where(String)` / `and(String)` / `or(String)` の**直接利用**は SM-1 対象外。呼び出し側の文字列がそのまま連結される | FR-7.1、BR-19 | **受容。** 代替経路 `where(Param)` / `and(Param)` / `or(Param)` を FR-7.3 として提供し、FR-7.2 の文書化対象に加える（`tech-stack-decisions.md` TSD-9） |
| **R-11** | `Search` / `QueryImpl` の蓄積は単回使用（CON-7）であり、`bindValues` / `bindFragments` は呼び出し回数に線形に伸びる。上限はない | CON-7、NFR-7 | **受容。** 現行の `search` / `where` の `StringBuilder` と同じ成長特性である。上限の導入は OOS-2 が排した最適化に当たる |
| **R-12** | `orderBy(Sort)` と SELECT 句の `col.getFunctionName()`（`QueryImpl.java:152`）は識別子位置であり、呼び出し側の文字列が構文として入る | FR-1.7、BR-7、`business-rules.md` R-4 | **U5 `identifier-safety` に送る。** U2 は `Sort` に触れない。NFR-1 の対象範囲は FR-1.7 を含まない |
| **R-13** | `Query` の新 `default` メソッド 9 個の既定実装はいずれも値をバインドしない。**フォールバックの形は 3 種類ある**（下表）。外部の `Query` 実装を使うと SM-1 が達成されない | BR-11、ADR-006、`business-logic-model.md` § 6.1 | **受容。** NFR-3（外部実装が新しい経路で必ず失敗しないこと）との引き換え。Javadoc で 3 種類を書き分ける（TSD-9） |
| **R-14** | `handleException` の funnel が広がる。現行は `rs.next()` / `mapping()` の失敗だけが通っていたが、コールバック化により実行そのものの失敗も通る | BR-16 | **受容。** **通知を失う方向の変化はない。** 差が出るのは独自の `DaoTransactionHandler` を差している利用者のみ。リポジトリ内の実装は `Logging` と `None` の 2 つで既定は `None` である |
| **R-15** | `andIn` 等が子 `Query` の `getSelectSQL()` と `getSelectPreparedSql()` を**両方**呼ぶため、子の `blobIndex` が 2 回設定され、子の `tables` / `uniqTableNames` に 2 回追加される | `business-logic-model.md` § 5 | **受容。** `blobIndex` はどちらも同じ値になり、`tables` / `uniqTableNames` は `Set` で冪等である。実害はないが記録する |

**R-13 のフォールバックの内訳**（`business-logic-model.md` § 6.1 の既定実装表を再掲し、形ごとに分ける）。

| # | メソッド | 既定実装 | フォールバックの形 |
|---|---|---|---|
| 1 | `getSelectPreparedSql()` / `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)`（**5 個**） | `PreparedSql.ofLiteral(getXxxSQL(...))` | 旧メソッドが返すリテラル埋め込み SQL をそのまま `PreparedSql` に包む。**値は元から SQL テキストの中にある** |
| 2 | `where(Param)` / `and(Param)` / `or(Param)`（**3 個**） | `where(param.getSql())` / `and(...)` / `or(...)` | 対応する `String` 形に委譲する。**`Param` が運ぶ値は捨てられる**。`PreparedSql.ofLiteral` は関与しない。結果としてリテラル面にもバインド面にも `?` を含むテキストが入り、値が伴わない（R-8 と同じ帰結） |
| 3 | `andOuterJoin(Table, Param)`（**1 個**） | `return this;` | **何もしない。** 委譲先が存在しないため（旧 `andOuterJoin(Table, Search)` は `Param` から復元できない `Search` を要求する）。外部実装では outer join 条件が追加されない（BR-11、BR-13） |

**3 種類を書き分けることが Javadoc の要件である**（TSD-9）——利用者から見た帰結が異なる。1 は「安全にならないが SQL は正しい」、2 は「未束縛の `?` を含む SQL になり、実行すれば失敗する」、3 は「条件が黙って落ちる」。とくに 3 は**サイレントな挙動**であり、SEC-13 が扱う述語欠落と同じ性質の失敗を外部実装側で起こしうる。

**U1 の R-1〜R-7 は U2 でも有効である。** とくに R-2（`ofLiteral` 互換シム経路はリテラル埋め込みのまま実行される）は、U2 が `Query` の `default` を宣言することで**適用範囲が確定する**——R-13 がその U2 側の記述である。

---

## 3.1 の規則に対する修正

本ステージの Q1 = B により、Functional Design（3.1）の記述 4 箇所が古くなる。**3.1 の成果物は編集しない**——`business-logic-model.md` は `reviewer_max_iterations` 到達後の適用記録を持ち、その記述が後段の参照先である。U1 が同じ扱いをした先例に従う。修正内容をここに記録し、本文書を後段の参照先とする。

| # | 3.1 の記述 | 修正後 | 由来 |
|---|---|---|---|
| 1 | `business-rules.md` **BR-12**「`andOuterJoin(Table, Search)` はリテラルのまま残す。非推奨にするが、シグネチャも挙動も変えない。`getSelectPreparedSql()` の FROM 句には**リテラル埋め込みのテキスト**が出る。**この経路は SM-1 の対象外である**」 | 非推奨にする点と、**リテラル面（`outerJoinTables`）を `search.getSearchString()` のまま維持する点は変わらない**。変わるのはバインド面で、`bindOuterJoinTables` には `search.getSearchParam()` を使う。したがって **この経路は SM-1 の対象に入る**。旧 API の観測可能な挙動（`getSelectSQL()` の戻り値）は不変であり BR-10 を満たす | Q1 = B、SEC-11 |
| 2 | `business-logic-model.md` **§ 7.3** の非推奨 `andOuterJoin(Table, Search)` の実装が `Param.of(prev.getSql() + " and " + search.getSearchString(), prev.getValues())` である | 下記のコード片に置き換える。値は `prev.getValues()` に子の値を連結する（新設の `andOuterJoin(Table, Param)` と同一の形） | Q1 = B、SEC-11 |
| 3 | `business-rules.md` F 節 **R-1**「非推奨 `andOuterJoin(Table, Search)` の値は FROM 句にリテラルとして残る。**受容。** SM-1 対象外」 | **解消する。** 残存リスクではなくなる。FR-7.2 の文書化対象には残るが、記載内容は「非推奨であること」と「代替経路 `andOuterJoin(Table, Param)`」のみで、「SM-1 対象外」の注記は不要になる | Q1 = B、SEC-11 |
| 4 | `business-rules.md` **BR-19**「リテラル専用経路は値ゼロの `Param` を作る。`where(String)` / `and(String)` / `or(String)` / `join(...)` / **非推奨 `andOuterJoin(Table, Search)`** は値を持たない」 | **列挙から `andOuterJoin(Table, Search)` を外す。** 修正後の同メソッドは `search.getSearchParam()` の値を `prev.getValues()` に連結するため、値ゼロの `Param` を作らない。**BR-19 の他の 4 経路（`where(String)` / `and(String)` / `or(String)` / `join`）は不変**であり、規則そのもの（リテラル専用経路は値ゼロの `Param` を積む）も不変である。BR-19 が定める帰結——引用符の外の `?` を含むテキストで `Param.of` の個数検査に落ちる早期失敗——も、`andOuterJoin(Table, Search)` については適用されなくなる（値を持つ `Param` になるため `?` の個数と値の個数が一致する） | Q1 = B、SEC-11、SEC-15 |

```java
// 修正後の § 7.3 非推奨版
@Deprecated
@Override
public Query<T> andOuterJoin(Table tab1, Search search) {
    if (outerJoinTables.containsKey(tab1)) {          // BR-13。現行の条件をそのまま
        Param prev = bindOuterJoinTables.get(tab1);
        Param add  = search.getSearchParam();          // ? 付きテキスト ＋ 値
        List<BindValue> merged = new ArrayList<>(prev.getValues());
        merged.addAll(add.getValues());
        putOuterJoin(tab1,
            outerJoinTables.get(tab1) + " and " + search.getSearchString(),  // リテラル面は不変
            Param.of(prev.getSql() + " and " + add.getSql(), merged));
    }
    return this;
}
```

**影響を受けない 3.1 の規則**: BR-5（FROM の 2 状態も単一の private メソッドを経由する）は形が変わらない——`putOuterJoin` を経由する点は同じである。BR-6 / BR-8（値の結合順は FROM ++ WHERE、FROM 句の値はテキスト組み立てと同一ループで収集）も不変であり、**むしろこの修正によって空振りでなくなる**（従来は非推奨経路が値ゼロだったため FROM 句に値が出るのは新 `andOuterJoin(Table, Param)` 経由だけだった）。BR-13（キー不在時に何もしない）も条件をそのまま保つため不変である。§ 10 の「新規 public / protected メンバ」表（P-1〜P-12、Pr-1〜Pr-7）も**変わらない**——修正は既存メソッドの本体だけに掛かり、シグネチャを 1 つも増やさない。したがって AC-11 の判定対象は不変である。

---

## 要件と上流の対応

| 要件 | 満たす上流要件 | 判定する AC | 検証手段 |
|---|---|---|---|
| SEC-10 | NFR-1、FR-1.1 / 1.2 / 1.3 / 1.6 | **AC-1**, **AC-2**, **AC-3**, **AC-4** | U1 の mock によるバインド位置・値アサート |
| SEC-11 | NFR-1、FR-3.1（旧戻り値の不変） | — | `getSelectSQL()` の戻り値一致 ＋ `getSelectPreparedSql()` の FROM 句アサート |
| SEC-12 | NFR-1（構造的な担保） | — | ソース検査 |
| SEC-13 | NFR-1、NFR-6 | — | 述語数・連結子の一致テスト（16 経路）。**3.6 で実施** |
| SEC-14 | FR-3.1、NFR-3 | AC-11（判定は U3 完了時） | ソース検査 |
| SEC-15 | 本ステージで新規（U1 SEC-4 の継承） | — | ソース検査 |
| SEC-16 | FR-1.6 | **AC-4** | mock の位置・値アサート |
| SEC-17 | CON-6 | — | `mvn dependency:tree` の差分 |

**U2 が単独で判定できるのは AC-1 / AC-2 / AC-3 / AC-4 の 4 件**（`unit-of-work-story-map.md`「AC → Unit のマッピング」）。AC-11 は U3 完了時、SEC-13 の実施は 3.6 である。

---

## Build and Test（3.6）に送る検証項目

U1 が送った 6 項目に加えて、U2 から次を送る。

| # | 項目 | 由来 |
|---|---|---|
| 7 | **SEC-13 の同期テスト**。16 経路それぞれについて、リテラル面とバインド面の述語数・連結子の並びの一致をアサートする。**U2 で最も危険な失敗様式に対する唯一の検出手段である** | Q3 = A、SEC-13 |
| 8 | **SEC-11 の検証**。値を持つ `Search` を非推奨 `andOuterJoin(Table, Search)` に渡し、(a) `getSelectSQL()` が変更前と一致、(b) `getSelectPreparedSql()` の FROM 句に `?` が出る、(c) FROM 句の値が WHERE の値より前に来る（BR-6）ことを確認する | Q1 = B、SEC-11 |
| 9 | **AC-4 の差し込み位置**。サブクエリの子の値が親の値リストの正しい位置に入ること。個数が一致すれば例外は出ないため、mock の位置アサートでしか捕まらない | SEC-16、`business-rules.md` H 節 4 |
| 10 | **BR-17 ① の DISTINCT 伝播**。`addSearch` の `if (distinct == false) { distinct(search.isUnique()); }` が維持されていることのアサートを `QueryImplTest` に追加する。現行テストは網羅していない | `business-rules.md` H 節 6 |
| 11 | **BR-19 の早期失敗**。`where(String)` 等に引用符の外の `?` を含むテキストを渡したとき、DB に届く前に `InvalidParameterException` になること。**現行と失敗する場所・例外の型が変わる**ため、期待挙動としてテストで固定する | BR-19、SEC-15 |
| 12 | **`SearchTest` / `QueryImplTest` が無変更で緑であること**（BR-10）。変更が必要になったら旧戻り値が変わった証拠である | BR-10、BR-20 |
| 13 | **FR-8.2 の対応表**の U2 該当行——`UserDaoTest` の SELECT 経路のアサート文字列が `?` 入りに変わる分 | BR-20 |

**移行対象のファイルはすべて `src/test` にある**（`src/test/java/org/tamacat/dao/test/UserDao.java`、同 `FileDataDao.java`、`src/test/java/org/tamacat/dao/impl/MySQLDaoTest.java`）。`requirements.md` はこれらを「サンプル DAO」と呼ぶが production jar には同梱されない。したがって BR-20 の移行は**利用者に見える変更ではなく**、ライブラリの正典的な使い方を新経路に合わせる内部作業である。

---

## Review

READY

### iteration 2 — 検証の方法と範囲

読んだもの: 本文書と対の `tech-stack-decisions.md`、`nfr-requirements-questions.md`（回答済み Q1〜Q4）。上流 `business-logic-model.md` / `business-rules.md`（functional-design 3.1, U2 全文、とくに § 6.1・§ 3.3・§ 3.5・§ 7.3・BR-19・F 節）を独立に再走査し、修正後の各主張を突き合わせた。実ソースも独立に再取得した——`QueryImpl.java`（1〜50 行、351〜353 行、152 行）、`Query.java`（101 行）、`pom.xml`（1〜25 行、90〜109 行）、`SearchTest.java`（1〜20 行）、`UserDao.java` / `FileDataDao.java` / `MySQLDaoTest.java`（該当行）。sibling unit の参照は本イテレーションでは不要だった（U1 の成果物への新規言及がなかったため開いていない）。

検証の焦点は指示どおり (1) blocking 1（BR-19 の 4 件目の修正行の正確性と、5 件目の見落としがないかの独立再走査）、(2) blocking 2（R-13 の 5/3/1 分割と各帰結の正確性、`tech-stack-decisions.md` TSD-9 との整合）、(3) non-blocking（SEC-13 の 16 経路の網羅性説明の正確性）、(4) 回帰確認（Q1 = B の中心的主張、SEC-11 修正コードの BR-10/BR-13/BR-6/BR-8 保存、`File.java:NN` 引用全件）の 4 点。

### 指摘 1（blocking, iteration 1）の解消状況 — 解消

「3.1 の規則に対する修正」表に BR-19 を第 4 行として追加し、導入文も「4 箇所が古くなる」に修正されている（186〜194 行目）。

- **(a) 引用の逐語性**: 追加された BR-19 の引用（「リテラル専用経路は値ゼロの `Param` を作る。`where(String)` / `and(String)` / `or(String)` / `join(...)` / 非推奨 `andOuterJoin(Table, Search)` は値を持たない」）を `business-rules.md:204-206` と突き合わせたところ、見出しと本文 1 文目が 1 字違わず一致する。逐語性は確認できた。
- **(b) 他 4 経路が不変であることの正しさ**: `where(String)` / `and(String)` / `or(String)`（`business-logic-model.md:186-188`）と `join`（`:217-221`）はいずれも `Param.of(sql, Collections.<BindValue>emptyList())` で値ゼロの `Param` を積む実装のままであり、Q1 = B の修正コードはこれらに一切触れない。修正後も規則そのもの（リテラル専用経路は値ゼロの `Param` を積む）が成立し続けることも確認できた。
- **(c) 早期失敗の帰結が andOuterJoin に適用されなくなるという主張**: 旧経路（BR-12 のまま）では `Param.of(prev.getSql() + " and " + search.getSearchString(), prev.getValues())` であり、リテラルテキスト中の `?`（値の中に紛れ込む場合を除き通常 0 個）と `prev.getValues()`（変化なし）の個数一致が値ゼロの経路として保たれる。Q1 = B のコード（`merged.addAll(add.getValues())` ＋ `Param.of(..., add.getSql())`）では `add.getSql()` の `?` の個数と `add.getValues()` の個数は `Search.getSearchParam()` の構成上一致するため、`merged` との個数不一致は生じない。したがって BR-19 が定める「引用符の外の `?` で早期失敗する」という帰結はこの経路について文字どおり成立しなくなるという主張は論理的に正しい。
- **(d) 独立再走査で 5 件目の見落としがないか**: `business-rules.md` 全体と `business-logic-model.md` 全体を「andOuterJoin」「BR-12」「SM-1 の対象外」「R-1」「値ゼロ」で再検索した。§ 7.3 のコード直後（`business-logic-model.md:402`）に「この経路は SM-1 の対象外である（BR-12、R-1）」という一文があるが、これは BR-12・R-1 を引用するだけの重複記述であり、この 2 つは既に本文書の修正表 1・3 行目で是正済みである。新しい情報を持つ独立の記述ではないため、5 件目として追加報告する必要はない。G 節の対応表の行（`BR-12, BR-19 | FR-7.1 / FR-7.2`）も、要件対応の記録であって「値ゼロ」「対象外」の主張を追加していないため対象外。**5 件目のブロッキング相当の見落としは見つからなかった。**

指摘 1 は実体的に解消されている。

### 指摘 2（blocking, iteration 1）の解消状況 — 解消

R-13 の記述が 3 分割に書き改められ（166〜167 行目）、「R-13 のフォールバックの内訳」表（171〜177 行目）が残存リスク表の直後に追加されている。`tech-stack-decisions.md` TSD-9 の Javadoc 対象表も対応する 3 行に分割済みである（`tech-stack-decisions.md:162-164`）。

`business-logic-model.md` § 6.1（307〜319 行目）の表を数え直し、次の 3 分割が正確であることを確認した。

1. **5 個**（`getSelectPreparedSql()` / `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)`）——既定実装はいずれも `PreparedSql.ofLiteral(getXxxSQL(...))` である。表現「旧メソッドが返すリテラル埋め込み SQL をそのまま `PreparedSql` に包む」は正確。
2. **3 個**（`where(Param)` / `and(Param)` / `or(Param)`）——既定実装は `where(param.getSql())` 等であり `PreparedSql.ofLiteral` を一切呼ばない。「`Param` が運ぶ値は捨てられる」も正確——`param.getValues()` はどこにも渡らない。
3. **1 個**（`andOuterJoin(Table, Param)`）——既定実装は `return this;`。§ 6.1 の該当行が太字で明記しており、一致を確認した。

各帰結の正確性も裏取りした。1 は「安全にならないが SQL は正しい」——旧メソッドの戻り値をそのまま包むだけなので値は元から埋め込まれており、SQL テキスト自体は現行と同じ（誤りはない）。2 は「未束縛の `?` を含む SQL になり、実行すれば失敗する」——`business-logic-model.md:211` が「`where(Param)` で組み立てた `Query` の `getSelectSQL()` は未束縛の `?` を含む SQL を返す…`ofLiteral` 経由で実行すると U1 の `hasUnboundPlaceholders()` が `DaoException` で拒否する」と明記しており一致する（`business-rules.md` R-8 とも整合）。3 は「条件が黙って落ちる」——`return this;` で何も追加されないため正しい。

R-13 の一般文（「いずれも値をバインドしない…外部の `Query` 実装を使うと SM-1 が達成されない」）は 3 形いずれにも共通して成立するため、一般文自体は書き換え不要で問題ない。

指摘 2 も実体的に解消されている。修正が新しい矛盾を持ち込んでいないかも確認した——`tech-stack-decisions.md` の「新 `default` のうち 5 個 / 3 個 / 1 個」の 3 行と `security-requirements.md` の R-13 内訳表は語彙・数字とも一致しており、両文書間の食い違いはない。

### 非ブロッキング所見（iteration 1）の解消状況 — 解消

「対象 16 経路の網羅性」の段落（95 行目）が追加され、`where(Param)` / `and(Param)` / `or(Param)` を除外する理由が明記された。`business-logic-model.md:195-196`（`addWhere(String, Param)` が `addWhere(condition, param.getSql(), param)` に委譲する実装）を独立に再読し、リテラル面（`param.getSql()`）とバインド面（`param` そのもの）が単一の `Param` インスタンスから導出されることを確認した——追記の主張どおり、この 3 経路は構造的に片面だけ更新し忘れる余地がない。`addSearch` を `where(Search, Sort)` の 1 経路に数える説明も、`business-logic-model.md:229-239`（`addSearch` が `where` / `and` / `or` の `Search` 形から共通に呼ばれる設計）と整合する。16 という総数の内訳も SEC-13 の「対象メソッド」列（89 行目）を数え直して 4＋3＋1＋1＋4＋1＋2＝16 で一致することを確認した。

### 新たな所見（非ブロッキング）— `tech-stack-decisions.md` に軽微な行番号の誤りが 2 件ある

`File.java:NN` 引用を全件突き合わせた結果、大半（`QueryImpl.java:350-356` / `:351`、`Query.java:101`、`QueryImpl.java:152`、`pom.xml:17-18` / `:101-102`、`UserDao.java:22,:43`、`FileDataDao.java:12,:30`、`MySQLDaoTest.java:83`）は実ソースと一致したが、`tech-stack-decisions.md` の 2 件が実ソースと 1 行ずれていた。

- `tech-stack-decisions.md:116`「既存 `outerJoinTables`（`QueryImpl.java:44`）」——実際のフィールド宣言は `QueryImpl.java:45`。
- `tech-stack-decisions.md:197`「`SearchTest.java:19` は `junit.framework.TestCase` を継承したまま」——実際の継承宣言（`public class SearchTest extends TestCase`）は `SearchTest.java:18`。

いずれも iteration 1 の修正対象外の既存記述であり、参照先の特定を妨げるほどの誤りではない（1 行差）。判断や実装の正しさに影響しないため非ブロッキングとするが、次に本文書を触る機会があれば直しておくとよい。

### 結論

iteration 1 の blocking 指摘 2 件はいずれも実体的に解消されており、修正自体が新しい矛盾や過小申告を持ち込んでもいない。non-blocking 所見も解消された。今回新たに見つかったのは軽微な行番号の 1 行ずれ 2 件のみで、いずれも判定基準や設計判断に影響しない。`reviewer_max_iterations` に達したことを踏まえ、READY と判定する。

### iteration 1 — 検証の方法と範囲

読んだもの: 本文書、対の `tech-stack-decisions.md`、`nfr-requirements-questions.md`（Q1〜Q4 の全文と論点・選択肢）、上流 `business-logic-model.md` / `business-rules.md`（functional-design 3.1, U2 全文）、`requirements.md`（FR-1 / FR-7 / NFR-1 / AC 全体）。加えて実ソース `QueryImpl.java`（:130-190、:340-360）、`Search.java`（:1-80）、`pom.xml`（依存・compiler・surefire 設定）を独立に読み、行番号・コード形状の引用を突き合わせた。sibling unit の参照は許可された U1 `bind-foundation` の `security-requirements.md` / `tech-stack-decisions.md` のみに限定した。

検証の焦点は指示どおり (1) Q1 = B の中心的主張（BR-12 に上流の免除根拠がないこと、SEC-11 の修正コードの妥当性）、(2) 3.1 への修正が「3 箇所」で尽きるかの独立再走査、(3) 行番号・コード引用の正確性、(4) TSD-8 の Java 8 全数確認の網羅性、(5) SEC-13 の 16 経路の完全性、(6) TSD-6 の line coverage 論証、(7) 判定基準の機械的検証可能性、の 7 点。

**Q1 = B の中心的主張は検証できた。** `requirements.md:41-47`（FR-1.1〜1.7）と `:114`（FR-7.1 の列挙）を独立に再読したが、いずれも `andOuterJoin` を含まない。免除の上流根拠がないという SEC-11 の主張は正しい。SEC-11 の修正コード（§ 7.3 の `andOuterJoin(Table, Search)`）は `business-logic-model.md` § 7.3 の新設 `andOuterJoin(Table, Param)` と同形であり、`outerJoinTables.get(tab1)` へのリテラル面書き込み式が現行実装（`QueryImpl.java:353`、実測で行番号一致を確認）と 1 字も変わらないため BR-10 を満たす。BR-13 の条件（`containsKey`）も温存されている。呼び出し元 0 件の主張は `grep -rn "andOuterJoin" src/` で独立に再現し、`Query.java:101` の宣言と `QueryImpl.java:351` の実装のみであることを確認した。

しかし 3.1 への修正の完全性（論点 2）と R-13 の記述精度（論点 7）で、それぞれ独立のブロッキング指摘が見つかった。

### 指摘 1（blocking）— Q1 = B が生じさせる「3.1 への修正」は 3 箇所で尽きていない。`business-rules.md` BR-19 の列挙も古くなる

「3.1 の規則に対する修正」節は、Q1 = B により古くなる 3.1 の記述を BR-12 / § 7.3 / F 節 R-1 の 3 箇所と数え、「本ステージ Q1 = B により、Functional Design（3.1）の記述 3 箇所が古くなる」と明言している（173〜175 行目）。しかし `business-rules.md` の **BR-19**（「リテラル専用経路は値ゼロの `Param` を作る。`where(String)` / `and(String)` / `or(String)` / `join(...)` / **非推奨 `andOuterJoin(Table, Search)`** は値を持たない。バインド側には `Param.of(literalText, emptyList())` を積む」）も、この経路を名指しして「値を持たない」と明記しており、Q1 = B の下ではこれも古くなる。修正後の § 7.3（本文書自身が 179〜199 行目で示すコード片）は、非推奨版のバインド面に `search.getSearchParam()` の値を合成して積む（`merged.addAll(add.getValues())`）——すなわち値を持つ `Param` になる。BR-19 の「値ゼロ」という前提はこの経路については成立しなくなる。

これは iteration の指示が名指しした失敗モード（「未選択を『不要と決定された』と扱わず 1 回だけ確認する」の逆側——ここでは「影響を受ける記述の数え上げの過小申告」）そのものである。`business-logic-model.md` の直近の Review（iteration 2）で同種の過小申告（新規 protected メンバ表の Pr-7 漏れ）が単独で NOT-READY の根拠にされた前例があり、本文書は「3.1 の成果物は編集しない…修正内容をここに記録し、本文書を後段の参照先とする」と自ら宣言している以上、この記録が漏れていることは実装者が BR-19 を古い前提のまま参照しうるという実害を持つ。さらに「G. 規則と要件・受け入れ条件の対応」表の `BR-12, BR-19 | FR-7.1 / FR-7.2（raw 経路の維持と文書化）` という行も、BR-12 側は本文書が是正済みだが BR-19 側の是正が欠けているため、対応表としても中途半端な状態のまま後段に渡ることになる。

「3.1 の規則に対する修正」表に BR-19 を 4 件目として追加し、「非推奨 `andOuterJoin(Table, Search)` は値ゼロの `Param` を作る」という前提が Q1 = B の下では成立しないことを明記する必要がある。

### 指摘 2（blocking）— R-13 が「9 個の `default` すべてが `PreparedSql.ofLiteral(...)` でリテラルにフォールバックする」と一般化しているが、`business-logic-model.md` § 6.1 自身の記述と矛盾する

残存リスク表の **R-13** は次のように書く。

> `Query` の新 `default` メソッド 9 個の既定実装は、値を捨ててリテラルにフォールバックする（`PreparedSql.ofLiteral(...)`）。外部の `Query` 実装を使うと SM-1 が達成されない

しかし `business-logic-model.md` § 6.1（本文書が § 2 で「上流成果物との関係」として明示的に引用する節）の表を数え直すと、9 個のうち `PreparedSql.ofLiteral(...)` を使うのは **5 個だけ**（`getSelectPreparedSql` / `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql`）である。残り 4 個は形がまったく違う。

- `where(Param)` / `and(Param)` / `or(Param)`（3 個）——既定実装は `where(param.getSql())` 等であり、戻り値型は `PreparedSql` ではなく `Query<T>` である。**`PreparedSql.ofLiteral(...)` を一切呼ばない。**値を捨てて `String` を経由するという点では「値を失う」ことは共通するが、機構は R-13 が名指しする `ofLiteral` とは別物である。
- `andOuterJoin(Table, Param)`（1 個）——既定実装は `return this;`（何もしない）であり、これは本文書自身が SEC-11 の直後および「Build and Test（3.6）に送る検証項目」表・`tech-stack-decisions.md` TSD-9 の Javadoc 対象表で明記している事実である。「リテラルにフォールバックする」という表現はこのメソッドには**まったく当てはまらない**——フォールバック先のリテラルという概念自体が存在しない（`Search` を持たないため作りようがない、というのが BR-11 の理由である）。

`tech-stack-decisions.md` TSD-9 の Javadoc 対象表は「新 `default` 9 個」の行で R-13 をそのまま引用しつつ、`andOuterJoin(Table, Param)` だけは別行で「既定実装が `return this;`（何もしない）であること」と正しく書き分けている。**しかし `where(Param)` / `and(Param)` / `or(Param)` の 3 個については、TSD-9 も R-13 の「`ofLiteral` でリテラルにフォールバックする」という誤った説明のまま Javadoc 対象にしてしまっている。** この誤りが実装時の Javadoc 文面（TSD-9 が定める記述内容そのもの）に混入する経路になっており、判定基準を「ソース検査で確認できる」としている以上、ソースと食い違う記述は機械的に検証すれば落ちる。

R-13 を「5 個（`getXxxPreparedSql` 系）は `PreparedSql.ofLiteral(...)` で包む。3 個（`where(Param)` / `and(Param)` / `or(Param)`）は対応する `String` 版に委譲し値を失う。1 個（`andOuterJoin(Table, Param)`）は何もしない」の 3 分割に書き直す必要がある。TSD-9 の Javadoc 対象表も同じ 3 分割に合わせて修正すること。

### 非ブロッキングの所見

- **SEC-13 の「16 経路」に `where(Param)` / `and(Param)` / `or(Param)`（`QueryImpl` が override する新 3 メソッド、§ 6.4）が含まれていない。** `business-logic-model.md` § 3.3 によれば、これらは 2 引数 `addWhere(String, Param)` を経由し最終的に `appendWhere` を呼ぶため、WHERE の 2 状態（`where` / `bindFragments`）に触れる正規の入口である。ただし、これらは `literalSql = param.getSql()` を**唯一の情報源**として渡す（リテラル面とバインド面が独立に生成される `append()` / `appendWhere` の他の入口とは異なり、単一の `Param` から両面が導出される）ため、構造的に「片面だけ更新し忘れる」余地がなく、SEC-13 が検出しようとする失敗様式が原理的に起こりえない、という理由で除外されている可能性が高い。しかし本文書にはその除外理由が一言も書かれていない。「16 経路」が「3 つの同期状態ペアに触れる全入口」であるという完全性の主張（SEC-12・SEC-13 の判定基準）を字義どおり読むと、この 3 経路の不在は説明を要する。除外するなら 1 文でよいので理由を明記されたい。
- TSD-6 の line coverage 論証（BR-1 破れの 2 パターンの切り分け）は、示された欠陥コード片（`bindValues.addAll(...)` のコメントアウト）について独立に追跡した——`Param.of` の個数検査に捕まるという主張は正しい（`bindSearch` には `?` が積まれるが対応する値が `bindValues` に入らないため）。「両方を条件分岐で飛ばす」パターンが検査をすり抜けるという主張も、`Param.of` が個々の呼び出し単位ではなく `getSearchParam()` 時点の累積値に対して働く以上、成立する。この論証自体に欠陥はない。

以上、指摘 1・2 はいずれも本文書が自ら引用する上流成果物（`business-rules.md` BR-19、`business-logic-model.md` § 6.1）との食い違いであり、体裁の修正では済まない実体的な過小申告・誤記述である。NOT-READY と判定する。

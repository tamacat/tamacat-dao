# Business Rules — U2 `select-path`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U2 の境界、「最大の設計リスク」＝ `Search` の 3 状態同期を**構造で**守ること、実装上の制約（CON-7、サブクエリの差し込み、旧 `getSelectSQL()` / `getSearchString()` の戻り値不変）。
- **`unit-of-work-story-map.md`**（同上）— U2 が担う FR と AC-1 / AC-2 / AC-3 / AC-4、Unit 内の実装順序 1〜8。
- **`requirements.md`**（requirements-analysis, 2.3）— FR-1.1 / 1.2 / 1.3 / 1.6、FR-3.1 / 3.3、FR-7.1 / 7.2 / 7.3、NFR-1、NFR-3、CON-7。
- **`components.md`**（application-design, 2.6）— M-1 / M-2 / M-3 / M-4 の責務と境界、「新旧が合流する点は 2 つだけ」という構造。
- **`component-methods.md`**（同上）— M-3 の同期規則と連結規則の対応表、M-1 の `default` 既定実装、M-4 の追加メソッド。
- **`services.md`**（同上）— 実行フローの変化（`Search` → `QueryImpl` → `Dao` → `DBAccessManager`）と「利用側のコードは変わらない」という帰結。

**規則番号の系統**: 本文書の `BR-n` は **U2 の規則番号**であり、U1 `bind-foundation` の `business-rules.md` の `BR-n` とは別系統である。U1 の規則を参照するときは `U1 BR-n` と書く。

---

## A. 同期規則（U2 の中核）

### BR-1. `Search` の 3 状態を直接触るのは単一の private メソッドだけ

`search` / `bindSearch` / `bindValues` を変更してよいのは、次の 1 メソッドのみとする。

```java
private void append(String connector, String literalSql, Param bindParam)
```

`and(Column, Conditions, String...)` / `or(...)` / `and(Search)` / `or(Search)` はすべてこれを経由する。**3 つのうち 1 つだけを変更する経路を作らない。**

由来: `component-methods.md` M-3「同期規則（不変条件）」、`unit-of-work.md` U2「最大の設計リスク」。

**なぜ検査ではなく構造か**: `Param.of(...)` の個数検査は個数のずれを検出するが、「リテラル側とバインド側の両方に append し忘れた」場合は個数が一致したまま述語が欠落し、**WHERE 条件が緩い SQL が正常に実行される**。検査では捕まらない（U1 BR-25 と同じ構図）。

### BR-2. 連結規則はリテラル側とバインド側で同一にする

| メソッド | `search`（リテラル） | `bindSearch`（バインド） | `bindValues` |
|---|---|---|---|
| `and(Column, Conditions, String...)` | ` and ` ＋ 述語 | ` and ` ＋ `?` 付き述語 | 述語の値を末尾に連結 |
| `or(Column, Conditions, String...)` | ` or ` ＋ 述語 | ` or ` ＋ `?` 付き述語 | 同上 |
| `and(Search)` | ` and (` ＋ 相手の `search` ＋ `)` | ` and (` ＋ 相手の `bindSearch` ＋ `)` | 相手の `bindValues` を末尾に連結 |
| `or(Search)` | ` or (` ＋ 相手の `search` ＋ `)` | ` or (` ＋ 相手の `bindSearch` ＋ `)` | 同上 |

**先頭の断片には連結子を付けない**（現行 `Search.java:41-42` の `if (search.length() > 0)` と同じ）。括弧の付与位置が両者で同一であるため、`?` の出現順と値の順序が一致する。

由来: `Search.java:40-66`、`component-methods.md` M-3「連結規則の対応」。

### BR-3. `QueryImpl` の `where` と `bindFragments` を直接触るのは単一の private メソッドだけ

```java
private void appendWhere(String connector, String literalSql, Param bindParam)
```

`addWhere(String, String)` / `addWhere(String, Param)` / `join(Column, Column)` はすべてこれを経由する。

由来: 本ステージ Q1 = C。

### BR-4. `join(Column, Column)` も同期メソッドを経由する

現行 `join`（`QueryImpl.java:312-322`）は `addWhere` を**経由せず** `where` に直接書き、`useAutoPrimaryKeyUpdate = false` も**設定しない**。

- バインド側を取りこぼさないため、`appendWhere(...)` を経由させる（値ゼロの `Param`）
- **`useAutoPrimaryKeyUpdate` を設定しないという非対称は維持する**。現行の挙動であり、変更すると `getUpdateSQL` の主キー述語の付き方が変わる（U3 の範囲に波及する）

由来: `QueryImpl.java:312-322` と `:442-453` の差分。

### BR-5. FROM / JOIN 句の 2 状態も単一の private メソッドを経由する

`outerJoinTables` と `bindOuterJoinTables` を触るのは 1 メソッドのみ。`outerJoin(Column, Column)` / `andOuterJoin(Table, Search)` / `andOuterJoin(Table, Param)` がこれを経由する。

由来: 本ステージ Q2 = C。

---

## B. 順序規則

### BR-6. `getSelectPreparedSql()` の値の結合順はテキストの出力順に従う

`getSelectSQL()` のテキスト構成は `select + from + where + groupBy + orderBy`（`QueryImpl.java:181`）である。値が現れうるのは **`from`（outer join）と `where`** の 2 箇所だけであり、テキスト上 `from` が先に出る。

```
テキスト:  SELECT ... FROM <outer join を含む> <WHERE 句>  GROUP BY ...  ORDER BY ...
値リスト:  <FROM 句の値>                      ++ <bindFragments の値>
```

`select` 句・`groupBy`・`orderBy` は値を持たない（BR-7）。

由来: ADR-011（値リストは対になるテキストアキュムレータと 1 : 1 で持ち、テキストの連結順に結合する）、`component-dependency.md`「順序の不変条件」。**FROM 句の追加は本ステージの差分である**（Q2 = C）。

### BR-7. `select` 句・`groupBy`・`orderBy` は値を持たない

| 句 | 内容 | 値 |
|---|---|---|
| `select` | カラム名、または `col.getFunctionName() + " " + col.getColumnName()`（`:152`） | なし |
| `groupBy` | カラム名（`:410-421`） | なし |
| `orderBy` | `sort.getSortString()`（`:424-435`） | なし |

**`orderBy` と `select` の `getFunctionName()` は識別子位置であり、呼び出し側の文字列が構文として入りうる。** FR-1.7 の対象であり **U5 `identifier-safety` の担当**である。U2 は `Sort` に触れない（`unit-of-work.md` の境界）。

### BR-8. FROM 句の値はテキスト組み立てと同一のループで収集する

FROM 句の出力順は `getSelectSQL()` の `for (Table tab : tables)` ループ（`:159-180`）が決める。値も同じループで収集し、独立した蓄積リストを持たない。**テキストの出力順と値の蓄積順がずれる余地を作らない。**

### BR-9. サブクエリの値は親の値リストの差し込み位置に連続ブロックとして入る

`andIn` / `andNotIn` / `andExists` / `andNotExists`（`:388-406`）は子 `Query` の `getSelectPreparedSql()` を呼び、そのテキストを親の WHERE の**一点に**差し込む。子の値は連続したブロックであるため、親の値リストの**その位置に**そのまま挿入すれば順序が一致する。**並び替えは発生しない。**

Q1 = C の断片リスト構造では、サブクエリは 1 つの `WhereFragment` になる——その `Param` が子のテキストと子の値をまとめて保持するため、差し込み位置の一致が自動的に成立する。

由来: FR-1.6、`component-dependency.md`「サブクエリの差し込み」。

---

## C. 互換規則

### BR-10. 旧メソッドの戻り値の中身を 1 文字も変えない

| メソッド | 扱い |
|---|---|
| `Query.getSelectSQL()` | 戻り値不変、`@Deprecated` |
| `Search.getSearchString()` | 戻り値不変、`@Deprecated` |
| `Query.where(String)` / `and(String)` / `or(String)` | **不変**（非推奨にもしない。FR-7.1 が正当な用途を認めるため。ADR-002 代替 2 の却下理由） |
| `Dao.param(Column, Conditions, String...)` | **不変**（非推奨にもしない。同上） |

検証手段は既存テストである。`SearchTest`（`getSearchString()` に対するアサート 12 件）と `QueryImplTest`（旧 `getSelectSQL()` に対するアサート）は**変更なしで緑であること**。変更が必要になったら、それは戻り値が変わった証拠である。

由来: ADR-003、`component-dependency.md`「テストへの影響」。

### BR-11. `Query` の新 `default` メソッドはリテラルにフォールバックする

ADR-006 に従い、例外を投げず対応する旧メソッドの結果を `PreparedSql.ofLiteral(...)` で包む。

**例外は `andOuterJoin(Table, Param)` である**（本ステージで追加）。この既定実装は **`return this;`（何もしない）** とする。

理由: 他の `default` は「旧メソッドに委譲する」形で書けるが、`andOuterJoin(Table, Param)` には委譲先がない——旧 `andOuterJoin(Table, Search)` は `Search` を要求し、`Param` から `Search` を作る手段がない（`Search` は蓄積器であり、テキストから復元できない）。例外を投げると外部の `Query` 実装が新しい `Dao` の経路で失敗する（ADR-006 の Negative が排した挙動）。**何もしないことで、外部実装は「outer join 条件が追加されない」という現行の `andOuterJoin` がキー不在時に示すのと同じ挙動になる**（BR-13）。

### BR-12. `andOuterJoin(Table, Search)` はリテラルのまま残す

非推奨にするが、シグネチャも挙動も変えない。`getSelectPreparedSql()` の FROM 句には**リテラル埋め込みのテキスト**が出る。

**この経路は SM-1 の対象外である。** `Query` の他の 5 つの非推奨メソッドと同じ扱いであり、残存リスクとして記録する（下記 R-1）。FR-7.2 が求める文書化の対象に加える——代替経路は `andOuterJoin(Table, Param)` である。

由来: 本ステージ Q2 = C、ADR-003 の形。

### BR-13. `andOuterJoin` の「キーがなければ何もしない」挙動を新メソッドも再現する

現行実装は `outerJoinTables.containsKey(tab1)` が偽のとき**黙って何もしない**（`QueryImpl.java:350-356`）。

```java
public Query<T> andOuterJoin(Table tab1, Search search) {
    if (outerJoinTables.containsKey(tab1)) {           // ← 偽なら何もせず this を返す
        outerJoinTables.put(tab1, outerJoinTables.get(tab1) + " and " + search.getSearchString());
    }
    return this;
}
```

新しい `andOuterJoin(Table, Param)` も**同じ条件で同じく何もしない**。

**これは欠陥の温存である。** 是正しない理由: (1) 欠陥の是正は FR-6 群が列挙した 4 件に限られ、この経路は含まれない、(2) 是正すると `outerJoin(...)` を呼ばずに `andOuterJoin(...)` を呼んだ場合の挙動が変わり、現行と異なる SQL が出る。**呼び出し元が 0 件であるため実害はないが、記録する。**

### BR-14. `Dao.param(...)` と `SQLParser` の保持は不変

`Dao` は `param(...)` 用の `SQLParser` を保持し続け、`prepare(...)` は別途 `BindSqlBuilder` を使う。**2 つの生成器を持つ冗長を受け入れる。**

`Search` がリテラル系とバインド系の 2 つのテキストを持つのと同じ性質の代償であり、FR-3.1 を厳密に守るためである（ADR-003）。

---

## D. 実行経路の規則

### BR-15. `Dao.search` / `searchList` はコールバック形を使う

U1 の Q1 = E により `DBAccessManager` は `<R> R executeQuery(PreparedSql, ResultSetHandler<R>)` を提供する。`Dao` にも `protected <R> R executeQuery(PreparedSql, ResultSetHandler<R>)` を追加し、`search` / `searchList` はこれを使う。

**`component-methods.md` M-4 が宣言していた `protected ResultSet executeQuery(PreparedSql sql)` は追加しない**（U1 `business-logic-model.md` § 11-1 の引き継ぎ）。

### BR-16. `handleException` funnel を維持する（適用範囲は広がる）

`Dao` 側のラッパで `DaoException` を捕まえ `handleException(cause)` に流す。

**現行との差**: 現行の `catch (SQLException e)`（`Dao.java:163-165`）が実際に捕まえるのは `rs.next()` / `mapping()` の失敗だけである——`dbm.executeQuery(String)` が既に `SQLException` を `DaoException` に包んでいるため、実行そのものの失敗は `handleException` を通らずに伝播する。コールバック化により実行の失敗も `handleException` を通るようになる。

**通知を失う方向の変化はない。** リポジトリ内の `DaoTransactionHandler` 実装は `LoggingDaoTransactionHandler` と `NoneDaoTransactionHandler` の 2 つで、`Dao` の既定は `None` であるため、リポジトリ内のテストで差は観測されない。差が出るのは独自ハンドラを差している利用者のみである。

由来: 本ステージ Q3 = A、U1 `business-logic-model.md` § 11-2。

### BR-17. `addSearch` の 3 つの副作用をすべて維持する

```java
// QueryImpl.java:455-461
protected Query<T> addSearch(String condition, Search search, Sort sort) {
    if (distinct == false) { distinct(search.isUnique()); }   // ① DISTINCT の伝播
    addWhere(condition, search.getSearchString());            // ② WHERE への連結
    return orderBy(sort);                                     // ③ ORDER BY の付与
}
```

バインド版は ② を `search.getSearchParam()` に切り替えるが、**① と ③ は現行のまま**である。① の `if (distinct == false)` という条件（一度 true になった `distinct` を `false` で上書きしない）も維持する。

### BR-18. `addWhere` の `useAutoPrimaryKeyUpdate = false` 副作用を維持する

`addWhere(String, Param)`（新規）も `addWhere(String, String)`（既存）と同じく `useAutoPrimaryKeyUpdate = false` を設定する。空文字・null の `sql` では何もしない（`:443` の `if (sql != null && sql.trim().length() > 0)`）という条件も維持する。

由来: `component-methods.md` M-2「既存 `addWhere(String,String)` のバインド版。`useAutoPrimaryKeyUpdate = false` の副作用も同じく持つ」。

### BR-19. リテラル専用経路は値ゼロの `Param` を作る

`where(String)` / `and(String)` / `or(String)` / `join(...)` / 非推奨 `andOuterJoin(Table, Search)` は値を持たない。バインド側には `Param.of(literalText, emptyList())` を積む。

**帰結（挙動変更）**: そのテキストに引用符の外の `?` が含まれていると、`Param.of` の個数検査（U1 BR-21 の走査規則を使う）で `InvalidParameterException` になる。**現行はこの SQL がそのまま DB に渡り構文エラーになる**ため、どちらも失敗する点は同じだが、**失敗する場所と例外の型が変わる**。

本設計はこれを「より早く、より明確に失敗する」変更として受け入れる。U1 の走査は引用符の外の `?` だけを数える（U1 BR-21）ため、値がクォートで囲まれた通常のリテラル SQL は 0 個と数えられ、この経路には落ちない。

---

## E. 移行規則（Q7 = B）

### BR-20. 各 Unit は自分が壊したテストを自分で直す

U2 が壊すテストと移行対象:

| 対象 | 内容 | 理由 |
|---|---|---|
| `UserDao.java:22, :43` | `param()` → `prepare()` | ADR-002。`param()` が運ぶのは実行時の値であり、この経路がリテラルのままだと SM-1 が主要経路を素通りする |
| `FileDataDao.java:12, :30` | 同上 | 同上 |
| `MySQLDaoTest.java:83` | `dao.param(...)` → `dao.prepare(...)` | 同上 |
| `UserDaoTest` の SELECT 経路 | `getExecutedQuery().get(0)` のアサート文字列を `?` 入りに書き換え | 実行経路が新経路に切り替わり、記録される SQL が変わる |
| `SearchTest` / `QueryImplTest` | **変更しない。** バインド版のアサートを**追加**する | BR-10。旧メソッドの戻り値が変わらないため |
| FR-8.2 の対応表 | U2 が該当行を起こす | 集約は Build and Test（3.6） |

**`SQLParserTest` は U2 でも 1 行も変更しない**（U1 BR-31）。

---

## F. 残存リスクと既知の欠陥

| ID | 内容 | 扱い |
|---|---|---|
| **R-1** | 非推奨 `andOuterJoin(Table, Search)` の値は FROM 句にリテラルとして残る | **受容。** SM-1 対象外。代替経路 `andOuterJoin(Table, Param)` を提供し FR-7.2 の文書化対象に加える |
| **R-2** | `andOuterJoin` は `outerJoinTables` にキーがないと黙って何もしない。新メソッドも同じ | **受容（温存）。** BR-13。呼び出し元 0 件 |
| **R-3** | `where(String)` / `and(String)` / `or(String)` の直接利用は SM-1 対象外 | **受容。** FR-7.1。代替は `where(Param)` / `and(Param)` / `or(Param)`（FR-7.3） |
| **R-4** | `orderBy(Sort)` と `select` 句の `getFunctionName()` は識別子位置であり、呼び出し側の文字列が構文として入る | **U5 `identifier-safety` に送る。** U2 は `Sort` に触れない |
| **R-5** | `Query` の新 `default` の既定実装は値を捨ててリテラルにフォールバックする。外部の `Query` 実装を使うと SM-1 が達成されない | **受容。** ADR-006 が互換側に倒した判断。Javadoc で明示する |
| **R-6** | `handleException` の適用範囲が広がる（BR-16） | **受容。** 通知を失う方向の変化はない |

---

## G. 規則と要件・受け入れ条件の対応

| 規則 | 満たす要件 | 判定する AC |
|---|---|---|
| BR-1, BR-2, BR-3, BR-19 | FR-1.1 / 1.2 / 1.3（単一値・LIKE・IN のバインド） | **AC-1**, **AC-2**, **AC-3** |
| BR-9 | FR-1.6（サブクエリ） | **AC-4** |
| BR-6, BR-7, BR-8 | FR-1（値と `?` の順序一致） | AC-1 / AC-3 / AC-4 が共通に依存 |
| BR-10, BR-11, BR-14 | FR-3.1 / FR-3.3（API 互換、旧戻り値契約） | AC-11（判定は U3 完了時） |
| BR-12, BR-19 | FR-7.1 / FR-7.2（raw 経路の維持と文書化） | — |
| BR-14, BR-20 | FR-7.3（`prepare()` + `where(Param)` の代替経路） | — |
| BR-15, BR-16, BR-17 | FR-1（実行経路の切り替え） | AC-1〜AC-4 の実行面 |

**U2 が単独で判定できる AC は AC-1 / AC-2 / AC-3 / AC-4 の 4 件**（`unit-of-work-story-map.md`）。AC-2 の LIKE 規則そのものは U1 の `ValueRules` が実現し、U2 は経路として通す。

---

## H. 規則が破れたときの失敗様式

| # | 破れる規則 | 症状 | 検出手段 |
|---|---|---|---|
| 1 | BR-1（`Search` の 3 状態同期） | 両方に append し忘れると**述語が丸ごと欠落し、WHERE 条件が緩い SQL が正常に実行される**。個数は一致するため `Param.of` を通り抜ける | `getSearchString()` と `getSearchParam().getSql()` の述語数を突き合わせるテスト。**U2 で最も危険な失敗様式** |
| 2 | BR-3 / BR-4（`QueryImpl` の 2 状態同期） | `getSelectSQL()` と `getSelectPreparedSql()` が**異なる条件の SQL** を返す。`join()` を取りこぼすと JOIN 条件が消える | 同じ `QueryImpl` から両方を取り、述語数と JOIN 句の有無を突き合わせるテスト |
| 3 | BR-6（値の結合順） | FROM 句と WHERE 句の値が入れ替わる。**個数が一致すれば例外は出ない** | U1 の mock による位置・値アサート（U1 BR-34）のみ |
| 4 | BR-9（サブクエリの差し込み位置） | 子の値が親の誤った位置に入る。同上、例外は出ない | **AC-4** |
| 5 | BR-10（旧戻り値の不変） | 既存テストが落ちる | `SearchTest` / `QueryImplTest` / `SQLParserTest` が即座に検出する。**この規則は既存テストが十分に守っている** |
| 6 | BR-17 ①（DISTINCT の伝播） | `search.isUnique()` が反映されず重複行が返る | `QueryImplTest` に DISTINCT のアサートを追加する必要がある |

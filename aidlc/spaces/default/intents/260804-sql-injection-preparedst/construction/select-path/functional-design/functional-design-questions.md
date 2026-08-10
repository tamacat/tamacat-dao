# Functional Design Questions — U2 `select-path`

Unit: **U2 `select-path`**（kind: `library`）
Stage: Functional Design（3.1）/ Construction
Depth: Standard（`aidlc-state.md`）

## Sources

- `unit-of-work.md` — U2 の責務・境界・所有コンポーネント（M-1 / M-2 の一部 / M-3 / M-4 の一部）、「この Unit が抱える最大の設計リスク」＝ `Search` の 3 状態同期、移行作業（Q7 = B）
- `unit-of-work-story-map.md` — U2 が担う FR-1.1 / 1.2 / 1.3 / 1.6 / 3.1 / 3.3 / 7.1 / 7.2 / 7.3、判定する AC-1 / AC-2 / AC-3 / AC-4、Unit 内の実装順序 1〜8
- `requirements.md` — FR-1、FR-3.1 / 3.3、FR-7.1〜7.3、NFR-1、NFR-3、CON-7（`QueryImpl` は単回使用）
- `components.md` / `component-methods.md` — M-1 `Query`、M-2 `QueryImpl`、M-3 `Search`、M-4 `Dao` / `DaoAdapter`
- `services.md` — 実行フローの変化（`Search` → `QueryImpl` → `Dao` → `DBAccessManager`）
- **U1 `bind-foundation` の 3.1 / 3.2 成果物** — `Param` / `PreparedSql` の契約、`business-logic-model.md` § 11 の U2 への引き継ぎ事項 2 件
- 実ソース — `Query.java`、`QueryImpl.java`、`Search.java`、`Dao.java`、`DaoAdapter.java`

設問は 3 件。**Q1 と Q2 は実ソースを読んで初めて判明した 2.6 の規定漏れ**、Q3 は U1 が明示的に U2 へ引き継いだ論点である。

---

## Q1. `QueryImpl` はバインド版の WHERE テキストをどう保持するか

**2.6 の規定に穴がある。** `component-methods.md` M-2 の「値アキュムレータ」表は `whereValues` を **`where`（`QueryImpl.java:48`）と対**にすると書き、`getSelectPreparedSql()` のテキスト連結を「SELECT 句 + FROM/JOIN + **`where`**」としている。

しかし ADR-003 により **`where` はリテラル埋め込みのテキストを保持し続ける**（`getSelectSQL()` の戻り値を 1 文字も変えないため）。したがって:

```
where          = " WHERE users.user_id='admin'"   ← ? が 0 個
whereValues    = [BindValue(STRING, "admin")]     ← 値が 1 個
PreparedSql.of(select + from + where, whereValues) → InvalidParameterException（個数不一致）
```

**`QueryImpl` にはバインド版のテキストを保持する場所が必要だが、2.6 はそれを規定していない。**

### `where` に書き込む経路（実ソースで全数確認）

| # | 経路 | 値を持つか | 備考 |
|---|---|---|---|
| 1 | `addWhere(String condition, String sql)`（`:442-453`） | 経路による | **唯一の funnel。** `where(String)` / `and(String)` / `or(String)`（`:375-385`）、`andIn` / `andNotIn` / `andExists` / `andNotExists`（`:390-405`）、`addSearch`（`:459`）、`getUpdateSQL` / `getDeleteSQL` の主キー述語（`:246`、`:285`）がすべてここを通る。**副作用として `useAutoPrimaryKeyUpdate = false` を設定する** |
| 2 | `join(Column col1, Column col2)`（`:315-320`） | **持たない**（カラム名同士の比較） | **`addWhere` を経由せず `where` に直接書く。** かつ `useAutoPrimaryKeyUpdate = false` を**設定しない** |

**経路 2 の存在が重要である。** バインド版のテキストが `join()` の出力を取りこぼすと、`getSelectPreparedSql()` が JOIN 条件のない SQL を返す。

### 選択肢

**A. `Search` と同じ 3 状態構造にする**

`where`（リテラル）/ `bindWhere`（`?` 入り）/ `whereValues` の 3 つを持ち、**単一の private メソッドだけ**が触る。`join()` もそれを経由させる。

```java
protected StringBuilder where = new StringBuilder();      // 既存・不変（:48）
protected StringBuilder bindWhere = new StringBuilder();  // 追加
protected List<BindValue> whereValues = new ArrayList<>(); // 追加

private void appendWhere(String connector, String literalSql, String bindSql, List<BindValue> values) {
    if (where.length() == 0) { where.append(" WHERE "); bindWhere.append(" WHERE "); }
    else { where.append(" " + connector + " "); bindWhere.append(" " + connector + " "); }
    where.append(literalSql);
    bindWhere.append(bindSql);
    whereValues.addAll(values);
}
```

M-3 `Search` について 2.6 が承認した構造をそのまま `QueryImpl` にも当てる形。

**B. `where` を廃し、断片のリスト 1 本にする**

```java
protected List<WhereFragment> whereFragments = new ArrayList<>();  // connector + literal + Param
// getSelectSQL() はリテラル面を、getSelectPreparedSql() はバインド面を都度組み立てる
```

テキストと値が離れないため同期の問題が原理的に消える。

**C. `where` を残し、バインド側だけを断片のリストで持つ（推奨）**

```java
protected StringBuilder where = new StringBuilder();       // 既存・不変（:48）
protected List<WhereFragment> bindFragments = new ArrayList<>();  // 追加。各要素は connector + Param

// getSelectSQL()          → 現行どおり where.toString() を使う（1 文字も変わらない）
// getSelectPreparedSql()  → bindFragments を連結してテキストと値を同時に組み立てる
```

`WhereFragment` は `QueryImpl` 内部の private static クラスで足りる。**バインド側の各断片が `Param` であるため、`Param` の不変条件 P-2（`?` の個数 = 値の個数）が断片単位で成立し、テキストと値のずれが構造的に起きない。**

**X.** Other (please specify)

### 論点

| 観点 | A | B | C |
|---|---|---|---|
| `protected StringBuilder where`（`:48`）を残すか | **残す** | **廃する** | **残す** |
| NFR-3（既存サブクラスが再コンパイルなしで動く） | ✅ 追加のみ | ⚠️ **`QueryImpl` を継承する外部クラスが `where` を読んでいると壊れる。** `protected` フィールドは public クラスの互換維持対象である | ✅ 追加のみ |
| テキストと値のずれ | 単一 private メソッドという**規律**で守る（`Search` と同じ。3.1 U1 の BR-25 が「個数検査では捕まらない」と記録した失敗様式が残る） | 断片が `Param` なので**構造**で守る | 断片が `Param` なので**構造**で守る |
| 同期すべき状態の数 | 3（`where` / `bindWhere` / `whereValues`） | 1 | **2**（`where` と `bindFragments`） |
| `join()`（`addWhere` を迂回する経路）の扱い | private メソッド経由に書き換える | 断片を 1 つ足す | 断片を 1 つ足す（値ゼロ） |

C は B の「構造で守る」利点を取りつつ、B の NFR-3 リスクを負わない。A は 2.6 が `Search` について承認した形との一貫性が最も高いが、同期対象が 3 つに増え、U1 の BR-25 が記録した「個数が一致したまま値がずれる」失敗様式を `QueryImpl` 側にも残す。

**いずれの案でも決める必要がある付随規則**: `addWhere(String, String)` のリテラル専用経路（`where("col = ?")` のような raw SQL）は値を持たない。この経路のテキストに `?` が含まれていると、最終的な `PreparedSql.of` の個数検査で `InvalidParameterException` になる。**現行はこの SQL がそのまま DB に渡り構文エラーになる**ため、どちらも失敗する点は同じだが、失敗する場所と例外の型が変わる。本設計はこれを「より早く、より明確に失敗する」変更として受け入れ、`business-rules.md` に記録する。

[Answer]: C（`where` を残し、バインド側を connector + `Param` の断片リストで保持する）— 2026-08-09、**Mode:** guided

---

## Q2. `andOuterJoin(Table, Search)` が FROM 句に埋めるリテラル値をどうするか

**これも 2.6 が扱っていない経路である。**

```java
// QueryImpl.java:350-356
@Override
public Query<T> andOuterJoin(Table tab1, Search search) {
    if (outerJoinTables.containsKey(tab1)) {
        outerJoinTables.put(tab1, outerJoinTables.get(tab1) + " and " + search.getSearchString());
    }
    return this;
}
```

`search.getSearchString()` は**リテラル埋め込み**の述語テキストであり、それが `outerJoinTables` に入って **FROM / JOIN 句**に出力される（`getSelectSQL()` の `select + from + where + groupBy + orderBy` のうち `from`）。WHERE ではない。

**帰結**: `andOuterJoin` に渡された `Search` の値は、U2 が WHERE 経路をバインド化しても**リテラルのまま SQL に残る**。呼び出し側アプリケーションの値であり、FR-7.1 が対象外とした「開発者が書いた SQL」ではない。

**`andOuterJoin` は `unit-of-work.md` の U2 所有コンポーネント一覧に含まれていない。** M-2 のうち U2 が持つのは `whereValues` / `addWhere(String, Param)` / `addSearch` / `getSelectPreparedSql()` / `andIn` / `andNotIn` / `andExists` / `andNotExists` である。

**`component-dependency.md` の「順序が作られる場所」表にも `andOuterJoin` は現れない。** バインド化する場合、FROM 句の値は `where` より**前**に出るため、値リストの結合順は `outerJoinValues ++ whereValues` になる。

- **A.** バインド化する。`andOuterJoin` を U2 の範囲に取り込み、`outerJoinTables` にバインド版テキストを保持する経路を追加して、値を `whereValues` より前に結合する
- **B.** リテラルのまま残し、**残存リスクとして記録する**。`security-requirements.md` の R-1 / R-2 と同じ扱いにし、FR-7.2 の文書化対象に加える（**推奨**）
- **C.** `andOuterJoin` を `@Deprecated` にし、バインド版の代替（`andOuterJoin(Table, Param)` 等）を追加する
- **X.** Other (please specify)

論点: A は Unit の境界を広げる。FROM 句用の値アキュムレータが増え、`getSelectPreparedSql()` の結合順が 2 系統になる。B は境界を守るが SM-1 に穴が残る——ただし**この経路には既存の呼び出し元もテストも存在しない**（リポジトリ内の `andOuterJoin` 呼び出しは 0 件）。かつ現行実装には `outerJoinTables.containsKey(tab1)` が偽のとき**黙って何もしない**という欠陥もある。C は API を増やすうえ、呼び出し元がない経路に移行先を用意することになる。

[Answer]: C（`andOuterJoin(Table, Search)` を `@Deprecated` にし、バインド版 `andOuterJoin(Table, Param)` を追加する）— 2026-08-09、**Mode:** guided

**推奨（B）と異なる選択である。押し返さず決定として扱う。** C は本設計にとって一貫している——ADR-003 が確立した形（旧 API をシグネチャ・戻り値とも無変更で残して `@Deprecated` にし、バインド版を新メソッドとして追加する）を `andOuterJoin` にも当てるものであり、`Query` の他の 5 メソッドと同じ扱いになる。呼び出し元が 0 件であるため移行コストも実質ゼロである。

**C を採ることの帰結（本設計が扱う）**:

1. **U2 の境界が `unit-of-work.md` の所有コンポーネント一覧を超える。** 同文書の U2 は M-2 のうち `whereValues` / `addWhere(String, Param)` / `addSearch` / `getSelectPreparedSql()` / `andIn` / `andNotIn` / `andExists` / `andNotExists` を持つとし、`andOuterJoin` を含めていない。本ステージの決定として明示的に取り込む
2. **`Query` に追加する `default` メソッドが 8 個から 9 個になる**（`component-methods.md` M-1 の一覧に `andOuterJoin(Table, Param)` を加える）。非推奨にする旧メソッドは 5 個から 6 個になる
3. **FROM 句用の値アキュムレータが必要になる。** FROM 句はテキスト上 `where` より前に出る（`getSelectSQL()` の `select + from + where + groupBy + orderBy`）ため、値の結合順は `outerJoinValues ++ whereValues` になる。`component-dependency.md`「順序が作られる場所」表への追加である
4. **現行の欠陥（`outerJoinTables.containsKey(tab1)` が偽なら黙って何もしない）を新メソッドが再現するかを決める必要がある。** 本設計は**再現する**——旧メソッドと挙動を揃えることを優先し、欠陥の是正は FR-6 群（U4 の担当範囲）にも本 Unit の担当 FR にも含まれないため。`business-rules.md` に既知の欠陥として記録する

---

## Q3. `Dao.search` / `searchList` の `handleException` funnel をどう扱うか

**U1 が明示的に U2 に引き継いだ論点である**（`business-logic-model.md` § 11-2）。

### 現行

```java
// Dao.java:155-167
public T search(Query<T> query) {
    T o = null;
    try (ResultSet rs = executeQuery(query.getSelectSQL())) {
        if (rs.next()) { o = mapping(query.getSelectColumns(), rs); }
        else { o = orm.getMappedObject(); }
    } catch (SQLException e) {
        handleException(e);          // ← トランザクションハンドラを発火させ DaoException を投げる
    }
    return o;
}
```

**この `catch (SQLException e)` が実際に捕まえるのは `rs.next()` / `mapping()` の失敗だけである。** `dbm.executeQuery(String)`（`DBAccessManager.java:97-104`）が既に `SQLException` を `DaoException` に包んでいるため、実行そのものの失敗は `SQLException` として現れず、`handleException` を通らずに伝播する。

### U1 の決定による変化

U1 の Q1 = E により `DBAccessManager` はコールバック形になる。`rs.next()` / `mapping()` もコールバックの内側、すなわち `DBAccessManager` の `try` の内側で走るため、**これらの失敗も `DaoException` に包まれる**。

```java
// U2 が書く形（Dao.search）
public T search(Query<T> query) {
    return executeQuery(query.getSelectPreparedSql(), rs -> {
        if (rs.next()) return mapping(query.getSelectColumns(), rs);
        else return orm.getMappedObject();
    });
}
```

- **A.** funnel を維持する。`Dao` 側のラッパで `DaoException` を捕まえて `handleException(cause)` に流す。**帰結: 実行そのものの失敗も `handleException` を通るようになり、現行より適用範囲が広がる**
- **B.** funnel を維持しない。`DaoException` をそのまま伝播させる。**帰結: `rs.next()` / `mapping()` の失敗で `DaoTransactionHandler.handleException` が発火しなくなり、現行より狭まる**
- **C.** 現行の適用範囲を厳密に再現する。`ResultSetHandler` の内側で起きた `SQLException` と実行時の `SQLException` を区別できる形にし、前者だけを `handleException` に流す
- **X.** Other (please specify)

論点: A も B も観測可能な挙動変更である。既定の `DaoTransactionHandler` は `NoneDaoTransactionHandler`（何もしない）だが、利用側が独自ハンドラを差している場合に差が出る。C は現行を厳密に保つが、`DBAccessManager` 側に区別のための機構（専用の例外型など）が必要になり、U1 が確定した M-5 の契約に追加が生じる。

**リポジトリ内に独自の `DaoTransactionHandler` 実装は `LoggingDaoTransactionHandler` と `NoneDaoTransactionHandler` の 2 つのみで、`Dao` の既定は `NoneDaoTransactionHandler` である。** したがってリポジトリ内のテストで差は観測されない。差が出るのはリポジトリ外の利用者のみである。

[Answer]: A（funnel を維持する。適用範囲は現行より広がる）— 2026-08-09、**Mode:** guided

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。3 件とも単一の選択肢が確定している。

**矛盾**: なし。ただし **2.6 / 2.7 の成果物に対する修正が 4 件**生じる（下表 2〜5）。

| # | 組み合わせ | 確認内容 | 判定 |
|---|---|---|---|
| 1 | Q1=C ＋ NFR-3 | `protected StringBuilder where`（`QueryImpl.java:48`）を残し、断片リストを**追加**するのみ | **矛盾なし。** 既存の `protected` フィールドを削除・改名しないため、`QueryImpl` を継承する外部クラスは壊れない |
| 2 | Q1=C ＋ `component-methods.md` M-2 | 2.6 の「値アキュムレータ」表は `whereValues` を `where` と対にし、`getSelectPreparedSql()` のテキストを「SELECT 句 + FROM/JOIN + `where`」としている | **2.6 への修正。** `where` はリテラルを保持し続ける（ADR-003）ため `?` の個数が値と一致しない。バインド側は connector + `Param` の断片リストで持ち、`getSelectPreparedSql()` はそこから組み立てる |
| 3 | Q2=C ＋ `unit-of-work.md` U2 の所有コンポーネント一覧 | 一覧に `andOuterJoin` が含まれていない | **2.7 への修正（境界の拡大）。** 本ステージの決定として明示的に取り込む。担当 FR は変わらない（FR-1 系の適用範囲が 1 経路広がる） |
| 4 | Q2=C ＋ `component-methods.md` M-1 | 2.6 は `Query` に追加する `default` メソッドを 8 個、非推奨にする旧メソッドを 5 個としている | **2.6 への修正。** `andOuterJoin(Table, Param)` を加えて 9 個、`andOuterJoin(Table, Search)` を加えて 6 個になる |
| 5 | Q2=C ＋ `component-dependency.md`「順序が作られる場所」表 | 表に `andOuterJoin` が現れない | **2.6 への修正。** FROM 句用の値アキュムレータを追加し、結合順を `outerJoinValues ++ whereValues` とする（FROM 句はテキスト上 `where` より前に出るため） |
| 6 | Q2=C ＋ FR-7.1 / FR-7.2 | 旧 `andOuterJoin(Table, Search)` がリテラル経路として残る | **矛盾なし。むしろ FR-7.2 の充足が強まる。** FR-7.2 は raw 経路とその代替経路の文書化を求めており、C は代替経路を実際に提供する |
| 7 | Q3=A ＋ 現行挙動 | `handleException` の適用範囲が広がる（実行そのものの失敗も通るようになる） | **観測可能な挙動変更。** ただし通知を**失う**方向の変化はない。リポジトリ内の `DaoTransactionHandler` 実装は `None` と `Logging` の 2 つのみで、`Dao` の既定は `None` であるため、リポジトリ内のテストで差は観測されない |
| 8 | Q3=A ＋ U1 が確定した M-5 の契約 | A は `DBAccessManager` 側に追加を要求するか | **要求しない。** 区別が必要なのは選択肢 C だけである。A は `Dao` 側のラッパで `DaoException` を捕まえるだけで成立する |

**上流成果物は編集しない。** 表 2〜5 の修正は本ステージの `business-logic-model.md`「2.6 / 2.7 契約からの差分」節に記録する。U1 で確立した扱いと同じ方針である。

**追加の設問**: 不要。

---

## Consolidated Summary Confirmation

**Prompt**: この内容で `business-logic-model.md` / `business-rules.md` / `domain-entities.md` を生成してよいか。

**Options**:
- Looks correct — この回答から成果物を生成する
- Request changes — 生成前に回答を修正する

**回答の要約**

| # | 決定 | 帰結 |
|---|---|---|
| Q1 | **C** — `where` を残し、バインド側を connector + `Param` の断片リストで保持 | 各断片が `Param` の不変条件 P-2 を満たすため、テキストと値のずれが構造的に起きない。同期対象は 2 つ。2.6 の M-2 を修正 |
| Q2 | **C** — `andOuterJoin(Table, Search)` を非推奨にし `andOuterJoin(Table, Param)` を追加 | 推奨（B）と異なる選択。ADR-003 の形を `andOuterJoin` にも当てる。U2 の境界が 1 経路広がり、`Query` の追加 default が 9 個・非推奨が 6 個になり、FROM 句用の値アキュムレータが増える |
| Q3 | **A** — `handleException` funnel を維持 | 適用範囲が現行より広がる（通知を失う方向の変化はない）。M-5 の契約に追加は生じない |

[Answer]:

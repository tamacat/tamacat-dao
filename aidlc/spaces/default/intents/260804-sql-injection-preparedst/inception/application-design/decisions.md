# Architecture Decisions — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

- **`requirements.md`**（requirements-analysis, 2.3）— 各 ADR は FR / NFR / CON / OQ を Context に引用する。とくに OQ-1（戻り値契約）、OQ-2（値の保持と順序）、OQ-3（検証基盤）、OQ-5（リポジトリ外利用者）を本ステージで解き、OQ-9（FR-1.7 の機構）は未解決のまま残す。
- **`architecture.md`**（codekb）— 現行の中心的設計事実（SQL は文字列連結）と実行面。すべての ADR の出発点である。
- **`component-inventory.md`**（codekb）— 変更対象コンポーネントの現在の責務と依存。
- **`stories.md`**（user-stories, 2.4）/ **`team-practices`**（practices-discovery, 2.2）— 本スコープで **SKIP** のため存在しない。方法論上の既定は `aidlc/spaces/default/memory/org.md` に従う。

決定の根拠となった設問の回答は `application-design-questions.md` の Q0-a / Q0-b / Q1〜Q9、コード追跡の事実は同ファイルの F1〜F6 を指す。

---

## ADR 索引

| ADR | 表題 | Status | 可逆性 |
|---|---|---|---|
| ADR-001 | 値と SQL テキストを対で運ぶ 2 つの型を導入する | Accepted | 中（型を消すのは破壊的） |
| ADR-002 | `param()` + `where(String)` 経路をバインド化の対象に含める | Accepted | 高（新 API を使わなければ現行のまま） |
| ADR-003 | 旧 API の戻り値を変えず、非推奨にしたうえで新メソッドを追加する | Accepted | 中（非推奨の解除は容易、戻り値変更は後からでも可能） |
| ADR-004 | INSERT / UPDATE / DELETE に新しい protected 拡張点を追加する | Accepted | 低（拡張点は一度公開すると外せない） |
| ADR-005 | 型検証と LIKE エスケープを共通コンポーネントに切り出す | Accepted | 高（内部構造のみ） |
| ADR-006 | `Query` の新 `default` メソッドは既定でリテラルにフォールバックする | Accepted | 高（既定実装は後から変更可能） |
| ADR-007 | バインド値の JDBC 適用は `DataType` から setter を選び、数値も `setString` を使う | Accepted | 高（内部実装） |
| ADR-008 | 実行記録は `getExecutedQuery()` を変えず、別メソッドで値を提供する | Accepted | 高（追加のみ） |
| ADR-009 | 検証基盤は既存 mock スタックの拡張で実現する | Accepted | 高（後から差し替え可能） |
| ADR-010 | FR-1.7（識別子位置の注入不能化）の機構は本ステージで決めない | Accepted | 高（先送りの解除は容易） |
| ADR-011 | バインド値は節ごとのアキュムレータに分けて保持し、テキストの連結順に結合する | Accepted | 高（内部実装） |
| ADR-012 | 未束縛のプレースホルダを持つ `PreparedSql` は実行前に拒否する | Accepted | 高（内部実装） |

---

## ADR-001: 値と SQL テキストを対で運ぶ 2 つの型を導入する

### Status
Accepted

### Date
2026-08-05

### Context

現行の tamacat-dao は「値を SQL リテラルに描画してから 1 本の文字列として `java.sql.Statement` に渡す」構造である（`architecture.md` の中心的設計事実）。`Query` は SQL を `String` で返し、`Dao` と `DBAccessManager` は `String` を受け取る。`String` は値を運べないため、FR-1（値のパラメータ化）を満たすには「SQL テキスト + 値の並び」を対で運ぶ手段が要る（OQ-2）。

運ぶべきものは 2 種類ある。WHERE に渡す**述語断片**（実行できない）と、実行できる**完成した文**である。現行の `Dao.param()` は前者を、`Query.getSelectSQL()` は後者を返している。

制約: Java 8（CON-1）、新規の第三者依存を増やさない（CON-6）。

### Decision

`org.tamacat.dao` に 3 つの不変型を導入する。

- **`Param`** — 述語断片（`?` を含むテキスト）とその値の並び
- **`PreparedSql`** — 実行できる完成文（`?` を含むテキスト）とその値の並び
- **`BindValue`** — 1 個のバインド値（`DataType` + 値、または `InputStream`）

`Param` と `PreparedSql` は**別の型とする**。生成時に「テキスト中の `?` の個数と値の要素数が一致すること」を検査し、不一致なら `InvalidParameterException` を投げる。

### Consequences

**Positive**
- 実行できるもの（`PreparedSql`）とできないもの（`Param`）が型で区別され、断片を誤って実行に渡す誤りがコンパイル時に防がれる。
- `?` の個数と値の個数の不一致が生成時に検出される。FR-1 で最も起きやすい実装ミスが、テスト以前に落ちる。
- 不変型であるため、`QueryImpl` の単回使用制約（CON-7）や `ThreadLocal` を跨ぐ受け渡しで新たな並行性の論点が生じない。
- BLOB（`DataType.OBJECT`）が他の値と同じ `BindValue` で表されるため、FR-1.5 と FR-3.2（`getBlobIndex()`）が同一のパラメータ番号体系に乗る。

**Negative**
- 公開型が 3 つ増える。一度公開した型は FR-3.1 の互換維持対象になり、以後シグネチャを変えられない。
- 型が 1 つで済む設計（`PreparedSql` のみ）に比べ、`Param` → `PreparedSql` の変換が 1 段増える。

**Neutral**
- 型名は設計上の呼称であり、実装時に最終決定する。`org.tamacat.dao` に置く判断は、これらが公開 API の一部（`Dao.prepare()` の戻り値、`Query.where(Param)` の引数）であることによる。

### Alternatives Considered

**代替 1: 1 つの型に統一する（`PreparedSql` のみ）**
- 内容: 述語断片も完成文も同じ型で表す
- Pros: 公開型が 1 つ減る。変換が不要
- Cons: 断片を実行に渡す誤りを型で防げない。`DBAccessManager.executeQuery(PreparedSql)` に `col=?` だけを渡してもコンパイルが通る
- 却下理由: 本取り組みはセキュリティ改修であり、誤用が実行時にしか現れない設計は避ける

**代替 2: 型を作らず `Query` / `Search` が値を内部保持し、内部だけで受け渡す**
- 内容: 公開表面を一切増やさない（当初 Q1 の選択肢 C）
- Pros: FR-3.1 に対して最も安全。互換維持対象が増えない
- Cons: **F2 により INSERT / UPDATE / DELETE で原理的に成立しない。** `Dao.getInsertSQL(T)` は利用側サブクラスが override する protected 拡張点であり、サブクラスは `Query` をメソッド内でローカルに生成して捨てる。`Dao.create(T)` は `Query` に到達できないため、値を取り出す手段がない
- 却下理由: コード追跡（F2）により実現不能と判明した

### References
- `application-design-questions.md` の Q1（回答 = A）、F2
- `requirements.md` OQ-2、FR-1.5、FR-3.2、CON-1、CON-6、CON-7

---

## ADR-002: `param()` + `where(String)` 経路をバインド化の対象に含める

### Status
Accepted

### Date
2026-08-05

### Context

`requirements.md` FR-7.1 は `Query.where(String)` を含む raw-SQL 経路を SM-1 の判定対象外とした。その根拠（2.3 の Q5 = A）は「呼び出し側が渡すのは開発者が書いた SQL であり、実行時のユーザー入力ではない」である。

しかしコード追跡（F3）により、この根拠がこの経路に当てはまらないことが判明した。`Dao.param(Column, Conditions, String...)`（`Dao.java:137-139`）は public で、`parser.value(...)` の戻り値——**値がリテラルとして埋め込まれた述語文字列**——を返す。サンプル DAO とテストはこれを `Query.where(String)` に渡している。

| 箇所 | コード |
|---|---|
| `UserDao.java:22` | `.where(param(User.USER_ID, Condition.EQUAL, data.getValue(User.USER_ID)))` |
| `UserDao.java:43` | `.where(param(User.USER_ID, Condition.EQUAL, data.val(User.USER_ID)))` |
| `FileDataDao.java:12, :30` | 同形 |
| `MySQLDaoTest.java:83` | `query.where(dao.param(...))` |

`param()` が運ぶのは `data.getValue(...)` すなわち**実行時の値**である。かつこれはライブラリの正典的な使い方であり、この経路がリテラルのまま残れば SM-1 は実質的に空洞になる。

これは 2.3 の Q11 でサブクエリ経路について認めたのと同じ性質の食い違いである。

### Decision

`param()` + `where(String)` 経路をバインド化の対象に含める。具体的には次を追加する。

- `Dao` / `DaoAdapter` に `public Param prepare(Column, Conditions, String...)` — `param()` のバインド版
- `Query` に `default Query<T> where(Param)` / `and(Param)` / `or(Param)`

**旧 `param()` と `where(String)` は無変更で残す。** サンプル DAO とテストを `param` → `prepare` に移行する。

**`prepare` という別名にする理由**: Java は戻り値の型だけが異なるオーバーロードを許さない。`param(Column, Conditions, String...)` と同じ引数リストで `Param` を返すメソッドは、別名でなければ定義できない。

承認済み `requirements.md` との差分は ADR として記録し、Units Generation（2.7）の承認ゲートまでに `requirements.md` を更新する（Q0-b = C）。更新対象は次の 2 点である。

1. **FR-7.1 / FR-7.2** — 「`Query.where(String)` は SM-1 の判定対象外」を「旧 `where(String)` は対象外だが、`prepare()` + `where(Param)` という代替経路を提供する」に改める
2. **NFR-1 の判定の読み** — ADR-003 によりリテラル生成コードが非推奨 API 経由で残るため、「対象範囲内で値を SQL 文字列に連結する経路が 0 件」を「**実行経路**に値の文字列連結が 0 件」と読み替える

### Consequences

**Positive**
- ライブラリの正典的な使い方がバインド経路に乗り、SM-1 が実効性を持つ。
- 追加は 2 メソッドのみ。既存シグネチャの削除・変更がないため FR-3.1 / NFR-3 を満たす。
- 移行しない利用者は現行どおり動作する。

**Negative**
- 公開メソッドが 2 つ増え、以後の互換維持対象になる。
- `param()` と `prepare()` という似た名前の 2 メソッドが並ぶ。利用者がどちらを使うべきか迷いうる。`@Deprecated` を旧 `param()` に付けない判断（ADR-003 の範囲外）のため、この曖昧さは残る。
- 承認済み要件との差分が生じ、2.7 までに `requirements.md` の更新が必要になる。

**Neutral**
- FR-7.1 が対象外とする残りの経路（`Sort.sort(Object,Object)` の非 `Column` キー、`Column.getFunctionName()`、`DataType.FUNCTION`、旧 `where(String)` の直接利用）は対象外のままである。これらが運ぶのは開発者が書いた SQL であり、Q5 = A の根拠が成立する。

### Alternatives Considered

**代替 1: 現行 FR-7.1 のまま進む（②はリテラルのまま）**
- Pros: 承認済み要件との差分が生じない。追加メソッドが不要
- Cons: サンプル DAO が示す主要な使い方がリテラルのまま残り、SM-1 が主要経路を素通りする
- 却下理由: 本取り組みの目的（`intent-statement.md` の Problem Statement）に照らして成立しない

**代替 2: 対象に含めたうえで旧 `param()` / `where(String)` を非推奨にする**
- Pros: 利用側に移行の合図を明示できる
- Cons: `where(String)` は FR-7.1 が正当に対象外とする用途（開発者が書いた SQL 断片）にも使われる。非推奨にすると、その正当な用途まで否定することになる
- 却下理由: `where(String)` の正当な用途を残すため

**代替 3: requirements-analysis（2.3）に戻って FR-7.1 を改訂してから 2.6 に戻る**
- Pros: 成果物の整合性が常に保たれる
- Cons: ステージ間の往復が増える。2.3 の再実行は questions / reviewer / learnings のすべてを再度通す
- 却下理由: 差分が明確に特定でき（FR-7.1 / FR-7.2 / NFR-1 の 3 箇所）、2.7 のゲートまでに更新すれば下流に影響しないため

### References
- `application-design-questions.md` の F3、Q0-a（回答 = A）、Q0-b（回答 = C）
- `requirements.md` FR-7.1、FR-7.2、NFR-1、`requirements-analysis-questions.md` Q5、Q11

---

## ADR-003: 旧 API の戻り値を変えず、非推奨にしたうえで新メソッドを追加する

### Status
Accepted

### Date
2026-08-05

### Context

OQ-1（`requirements.md` FR-3.3）が本ステージに委ねた最大の決定である。FR-3.1（破壊的変更をしない）と NFR-1（SM-1: 値の文字列連結がゼロ）が正面から衝突する。

対象は `Query.getSelectSQL()` / `getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)` / `getDeleteAllSQL(Table)` と、`Search.getSearchString()` である。いずれも public で `String` を返し、値がリテラルとして埋め込まれている。

コード追跡（F1）により、`getSelectSQL()` のリポジトリ内の呼び出し元は 8 箇所すべてライブラリ内部であることが判明した。リポジトリ内で戻り値を直接実行している外部利用例はない。ただしリポジトリ外の tamacat プロジェクトの依存は未確認である（OQ-5）。

### Decision

**旧メソッドの戻り値の中身を変更しない。** リテラル埋め込みの SQL を返し続ける。これらに `@Deprecated` を付ける。

バインド版を新しいメソッドとして追加する。

- `Query` に `default PreparedSql getSelectPreparedSql()` ほか 4 メソッド
- `Search` に public の `Param getSearchParam()`（`getSearchString()` のバインド版）

`Dao` の内部実行（`search` / `searchList` / `create` / `update` / `delete`）を新メソッドに切り替える。`QueryImpl.addSearch` と `Search.and(Search)` の内部受け渡しも新アクセサに切り替える。

### Consequences

**Positive**
- FR-3.1 / NFR-3 を厳密に満たす。バイナリ互換も挙動互換も保たれ、再コンパイルが不要である。
- OQ-5（リポジトリ外利用者の依存）に対して最も安全である。戻り値を直接実行している外部利用者がいても壊れない。
- 既存テストの大半が変更不要になる（`SQLParserTest` / `SearchTest` / `QueryImplTest` / `MySQLDaoTest`）。変更が必要なのは実行経路を通る e2e テストに限られ、FR-8.2 の対応表が小さくなる。
- `@Deprecated` により利用側に移行の合図が出る。

**Negative**
- **リテラル生成コード（`SQLParser` のリテラル系）が残り、非推奨の public メソッド経由で到達可能なままになる。** NFR-1 の「値を SQL 文字列に連結する経路が 0 件」を「実行経路に 0 件」と読み替える必要がある（ADR-002 の要件更新項目 2）。
- `Search` はリテラル系とバインド系の 2 本のテキストを保持することになる。述語テキストぶんのメモリ重複が生じる。
- 静的解析（SM-3 / CodeQL）が残存するリテラル連結経路を指摘する可能性がある。NFR-2 の達成には、この経路が実行に到達しないことを示すか、指摘を抑制する必要がある。**この点は Build and Test（3.6）で確認を要する。**

**Neutral**
- 将来、旧メソッドを削除するか戻り値を変更する判断は、本 ADR の Status を Superseded にすることで行える。可逆性は保たれている。

### Alternatives Considered

**代替 1: 戻り値の中身を `?` 入りに変える（シグネチャは維持）**
- Pros: リテラル生成コードが残らず SM-1 が最も清潔に達成される。`Search` の 2 本保持も不要
- Cons: バイナリ互換は保たれるが**挙動が破壊される**。戻り値を直接実行していた外部利用者は動かなくなる。FR-3.1 の「破壊的変更をしない」に反する解釈が成り立つ。既存テストのアサーションがすべて書き換えになる
- 却下理由: FR-3.1 が Must であり、OQ-5 が未解決（外部依存が確認できていない）であるため

**代替 2: リテラル維持 + 新メソッド追加、ただし `@Deprecated` は付けない**
- Pros: 旧 API を正規の選択肢として残せる
- Cons: 利用者にどちらを使うべきかの合図が出ない。安全でない経路が推奨と区別されない
- 却下理由: セキュリティ改修である以上、安全でない経路には合図が要る

### References
- `application-design-questions.md` の F1、Q2（回答 = C）、Q3（回答 = A）
- `requirements.md` FR-3.1、FR-3.3、NFR-1、NFR-2、NFR-3、OQ-1、OQ-5

---

## ADR-004: INSERT / UPDATE / DELETE に新しい protected 拡張点を追加する

### Status
Accepted

### Date
2026-08-05

### Context

コード追跡（F2）により判明した、本設計で最も強い制約である。

`Dao.getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)`（`Dao.java:212-222`）は protected で既定は `throw new RuntimeException(new NoSuchMethodException())`、すなわち**利用側 DAO サブクラスが override する拡張点**である（`business-overview.md` の「subclasses must override them」）。

実際のサブクラス実装（`UserDao.java:32-53` ほか、すべて同形）は次の形をとる。

```java
@Override
protected String getInsertSQL(User data) {
    Query<User> query = createQuery().addUpdateColumns(User.TABLE.columns());
    return query.getInsertSQL(data);   // Query はローカルに生成され、String だけが返る
}
```

`Dao.create(T)` は `executeUpdate(getInsertSQL(data))` を呼ぶだけである。したがって **`Dao` は `Query` オブジェクトに到達できない**。この契約（`String` を返す override）を変えずに値を運ぶ方法は存在しない。

### Decision

`Dao` に新しい protected 拡張点を追加する。

```java
protected PreparedSql getInsertPreparedSql(T data) {
    return PreparedSql.ofLiteral(getInsertSQL(data));   // 既定実装は旧 override に委譲
}
protected PreparedSql getUpdatePreparedSql(T data) { ... }
protected PreparedSql getDeletePreparedSql(T data) { ... }
```

`create(T)` / `update(T)` / `delete(T)` は新しい拡張点を呼ぶ。`DaoAdapter` も同名メソッドを持ち `delegate` に転送する。

**既定実装は旧メソッドに委譲し、その結果を値ゼロの `PreparedSql` で包む。** これにより、旧 `getInsertSQL(T)` だけを override している既存サブクラスは、リテラル SQL のまま従来どおり動作する（再コンパイル不要、NFR-3）。

新しいサブクラス、および移行するサブクラスは `getInsertPreparedSql(T)` を override し、`Query.getInsertPreparedSql(T)` の結果を返す。

### Consequences

**Positive**
- FR-1.4（INSERT / UPDATE の値のパラメータ化）が実現できる。この経路は他に方法がない。
- 既存サブクラスが無変更で動く。NFR-3（再コンパイル不要）を満たす。
- 移行が段階的に行える。サブクラスごとに override を差し替えればよい。

**Negative**
- **protected 拡張点は一度公開すると外せない。** 本 ADR の可逆性は 3 段階で最も低い。
- 拡張点が旧新 2 系統になり、利用側から見て「どちらを override すべきか」が分かりにくくなる。Javadoc での明示が必要である。
- 既定実装の委譲経路（旧 override → `ofLiteral`）を通った SQL は**リテラル埋め込みのままであり、SM-1 の達成対象ではない**。移行していないサブクラスは安全にならない。この事実は利用側に明示する必要がある。

**Neutral**
- `PreparedSql.ofLiteral(...)` は値ゼロの `PreparedSql` を作る。`DBAccessManager` はこれを `prepareStatement` して即実行する。`Statement` ではなく `PreparedStatement` を通るため、実行の形は統一される。

### Alternatives Considered

**代替 1: 既存の `getInsertSQL(T)` の戻り値型を変える**
- Cons: シグネチャの破壊的変更。すべてのサブクラスが再コンパイル・改修を要する。FR-3.1 に反する
- 却下理由: FR-3.1 が Must

**代替 2: `Dao.create(T)` が `Query` を受け取るオーバーロードを追加する**
- 内容: `create(Query<T>, T)` のような形で、利用側が `Query` を渡す
- Pros: 拡張点を増やさない
- Cons: 利用側の呼び出し形が変わる。既存の `create(T)` を呼ぶコードは移行できない。拡張点の override という現行の設計思想からも外れる
- 却下理由: 移行経路が既存サブクラスに開かれない

### References
- `application-design-questions.md` の F2
- `requirements.md` FR-1.4、FR-3.1、NFR-3、`business-overview.md`「Who Uses It and How」

---

## ADR-005: 型検証と LIKE エスケープを共通コンポーネントに切り出す

### Status
Accepted

### Date
2026-08-05

### Context

FR-2.1（NUMERIC / FLOAT の型検証）、FR-2.3（LIKE の `%` / `_` エスケープと `escape 'X'` 句）、FR-2.4（DATE / TIME の空値・`NULL`・`current_timestamp` の扱い）は、いずれも「現行の挙動を維持する」ことを Must とする。

これらの規則は現行 `SQLParser`（`:85-139`）の中にある。ADR-003 によりリテラル系（`SQLParser`）は残るため、バインド系にも同じ規則が必要になる。2 か所に実装すると挙動が乖離しうる。

### Decision

型検証・空値判定・`current_timestamp` 判定・LIKE エスケープを `org.tamacat.sql.ValueRules` に切り出す。`SQLParser`（リテラル系）と `BindSqlBuilder`（バインド系）の両方がこれを呼ぶ 3 構成にする。

`SQLParser` の public メソッドの**戻り値は 1 文字も変えない**。委譲するだけである。

### Consequences

**Positive**
- FR-2.1 / FR-2.3 / FR-2.4 の挙動が 1 か所で決まる。リテラル系とバインド系の乖離が構造的に起きない。
- `SQLParserTest`（既存の最も密なテスト）が `SQLParser` の挙動を固定し続けるため、`ValueRules` への切り出しが挙動を変えていないことを既存テストが検証する。
- LIKE 候補枯渇の挙動（FR-8.5 でテストを追加する対象）も 1 か所で決まる。

**Negative**
- クラスが 1 つ増える。`org.tamacat.sql` の public 表面が広がる。
- `ValueRules` を public にするか package-private にするかで、テストのしやすさと互換維持対象の広さがトレードオフになる。**public とする**（`SQLParser` と `BindSqlBuilder` が異なるパッケージから使う可能性を残すため）。

**Neutral**
- `ValueConvertFilter`（クォートエスケープ）は `ValueRules` に含めない。FR-2.2 によりバインド経路では適用しないため、リテラル系（`SQLParser`）に残す。

### Alternatives Considered

**代替 1: `SQLParser` に 2 系統のメソッドを持たせる**
- Pros: クラスが増えない
- Cons: 1 クラスが「リテラル描画」と「プレースホルダ生成」の 2 責務を持つ。`SQLParser` は非推奨にする対象であり、そこに新機能を足すのは方向が逆
- 却下理由: 非推奨クラスに新責務を追加しない

**代替 2: バインド用の新クラスを追加し `SQLParser` はそのまま残す（共通化しない）**
- Pros: `SQLParser` に一切触れない
- Cons: 型検証と LIKE エスケープが 2 か所に重複実装される。FR-2 の「現行の挙動を維持する」が 2 か所で保証されねばならず、乖離のリスクが残る
- 却下理由: 挙動の二重管理を避けるため

### References
- `application-design-questions.md` の Q4（回答 = C）
- `requirements.md` FR-2.1、FR-2.2、FR-2.3、FR-2.4、FR-8.5

---

## ADR-006: `Query` の新 `default` メソッドは既定でリテラルにフォールバックする

### Status
Accepted

### Date
2026-08-05

### Context

`Query` は public interface である（`Query.java:23`）。リポジトリ内の実装は `QueryImpl` のみだが、リポジトリ外に実装クラスが存在する可能性は否定できない（OQ-5）。

Java 8 の `default` メソッドを使えば、インターフェースへのメソッド追加は binary / source ともに非破壊である。しかし**既定実装が何をするか**は別の判断を要する。外部実装は新メソッドを override していないため、既定実装がその実装の挙動を決める。

### Decision

新 `default` メソッドの既定実装は、対応する旧メソッドの結果を `PreparedSql.ofLiteral(...)` で包んで返す。例外を投げない。

```java
default PreparedSql getSelectPreparedSql() {
    return PreparedSql.ofLiteral(getSelectSQL());
}
default Query<T> where(Param param) {
    return where(param.getSql());   // 値は失われる
}
```

### Consequences

**Positive**
- 外部の `Query` 実装が新しい `Dao` の実行経路で失敗しない。現行と同じ挙動（リテラル）で動き続ける。
- FR-3.1 / NFR-3 を外部実装に対しても満たす。

**Negative**
- **既定実装は安全でない側に倒れる。** `where(Param)` の既定実装は値を捨ててリテラル断片を使うため、外部実装を使うと SM-1 が達成されない。
- この挙動は静かである。利用者は自分が安全でない経路を通っていることに気づかない。Javadoc での明示が必要である。

**Neutral**
- 互換側に倒すか安全側に倒すかの判断であり、本 ADR は**互換側**を選んだ。安全側（`UnsupportedOperationException`）に倒す変更は、既定実装を差し替えるだけなので後から行える（可逆性は高い）。

### Alternatives Considered

**代替 1: 既定実装は `UnsupportedOperationException` を投げる**
- Pros: 安全でない経路が静かに通ることがない。実装漏れが即座に分かる
- Cons: 外部の `Query` 実装が新しい `Dao` の実行経路で必ず失敗する。FR-3.1 / NFR-3 に反する
- 却下理由: OQ-5 が未解決であり、外部実装の存在を否定できないため

**代替 2: 既定実装を持たず、`Query` を抽象クラス化する**
- Cons: インターフェースからクラスへの変更は破壊的
- 却下理由: FR-3.1 に反する

### References
- `application-design-questions.md` の Q2（回答 = C）
- `requirements.md` FR-3.1、NFR-3、OQ-5

---

## ADR-007: バインド値の JDBC 適用は `DataType` から setter を選び、数値も `setString` を使う

### Status
Accepted

### Date
2026-08-05

### Context

`PreparedStatementBinder` は `BindValue` を `java.sql.PreparedStatement` の 1 始まりの位置に適用する。`DataType` ごとにどの `setXxx` を使うかを決める必要がある。

現行のリテラル経路は、NUMERIC / FLOAT の値を正規表現で検証したうえで**引用符なしのまま**テキストに埋め込んでいる（`SQLParser.java:93-103`）。実際の型変換は DB エンジンが行っている。

### Decision

| `DataType` | 適用する setter |
|---|---|
| STRING, BOOLEAN | `setString` |
| NUMERIC, FLOAT | `setString` |
| DATE, TIME | `setString` |
| OBJECT | `setBinaryStream` |
| `isNull()` が真 | `setNull` |

`current_timestamp`（FR-2.4）は値ではなく SQL 関数としてテキストに出力し、バインドしない。

### Consequences

**Positive**
- 現行の型変換の挙動が変わらない。DB エンジンが行っていた変換をそのまま維持する。
- FR-2.1 の型検証は `ValueRules.validate` が担うため、setter 側で型を絞る必要がない。
- 実装が単純で、`DataType` の追加に対しても壊れにくい。

**Negative**
- `setBigDecimal` / `setInt` 等を使う場合に比べ、ドライバ側の型推論に依存する。厳格な型チェックを行うドライバでは、`setString` で渡した数値がエラーになる可能性がある。
- **この点は実 DB エンジンに対する検証が必要である。** `requirements.md` の `code-quality-assessment.md` 引用のとおり、現行の実行テストはすべて `MockDriver` に対して行われており、実 DB での検証がない。FR-8.3 で `UserDaoTest2`（Derby）を実行対象に加えることが、この検証の最初の機会になる。

**Neutral**
- `setNull` を使う場合、JDBC は `setNull(int, int sqlType)` の第 2 引数に SQL 型が必要である。`DataType` からの対応付けが要る。この詳細は Functional Design（3.1）の担当とする。

### Alternatives Considered

**代替 1: `DataType` ごとに厳密な setter を使う（`setBigDecimal` / `setDate` 等）**
- Pros: 型安全であり、ドライバの型推論に依存しない
- Cons: 現行と異なる型変換が起きうる。DATE / TIME は現行が文字列としてそのまま渡しており、`java.sql.Date` への変換規則を新たに決める必要がある。FR-2.4 の「現行挙動の維持」に反するリスクがある
- 却下理由: 現行挙動の維持が Must であるため

**代替 2: `setObject` を使う**
- Pros: 実装が最も単純
- Cons: 型情報がドライバに伝わらず、挙動がドライバ依存になる度合いが `setString` より大きい
- 却下理由: `setString` の方が挙動が予測しやすい

### References
- `application-design-questions.md` の Q7（回答 = A、FR-2.3 から一意に決定）
- `requirements.md` FR-1.5、FR-2.1、FR-2.4、FR-8.3、OQ-6

---

## ADR-008: 実行記録は `getExecutedQuery()` を変えず、別メソッドで値を提供する

### Status
Accepted

### Date
2026-08-05

### Context

FR-4.1 は「実行された SQL テキストに加えてバインド値も記録する」ことを求める。FR-4.2 は、その記録に機微データ上の条件（既定オフ、マスク手段の提供等）を付けないと決めた。

現行の `DBAccessManager.getExecutedQuery()`（`:181-188`）は `List<String>` を返す public メソッドであり、既存の e2e テスト（`UserDaoTest` 等）が `get(0)` に対して文字列アサートしている。型を変えると破壊的変更になる。

### Decision

`getExecutedQuery()` は `List<String>` を返し続け、**SQL テキストのみ**を保持する。バインド値を含む記録は新しい public メソッド `getExecutedStatements()`（`List<ExecutedStatement>` を返す）で提供する。

新しい実行メソッド（`executeQuery(PreparedSql)` / `executeUpdate(PreparedSql)`）は、両方のリストに記録する。

### Consequences

**Positive**
- `getExecutedQuery()` の型もアサーションの形も変わらない。FR-3.1 を満たす。
- FR-4.1 が満たされる。テストからバインド値を検証できる（FR-8.1 の補助経路にもなる）。

**Negative**
- 記録が 2 系統になり、メモリ上で SQL テキストが重複する。`ThreadLocal` のリストであるため、長時間走るスレッドでは両方が蓄積する。現行も同じ性質を持つため新しい問題ではないが、量は増える。
- FR-4.2 により、バインド値は平文で `ThreadLocal` に残る。機微な値がプロセスメモリ内に保持されることは、`requirements.md` FR-4.2 で明示的に許容された判断である。

**Neutral**
- `getExecutedQuery()` に記録される SQL は、新経路では `?` を含む形になる。**既存 e2e テストのアサーション文字列は変わる**（`component-dependency.md` の「テストへの影響」参照）。これは FR-8.2 の対応表の対象である。

### Alternatives Considered

**代替 1: `List<String>` のまま値を含む 1 行の文字列にする**
- Pros: メソッドが増えない
- Cons: 値と SQL が文字列として混在し、テストからの構造的なアサートができない。FR-8.1 の助けにならない
- 却下理由: 検証可能性が下がる

**代替 2: 新型のリストを返す新メソッドを主とし、`getExecutedQuery()` はそこから SQL を射影する互換シムにする**
- Pros: 記録が 1 系統で済む
- Cons: `getExecutedQuery()` の呼び出しごとに射影のコストがかかる。現行はリストをそのまま返している
- 却下理由: 大差はないが、実装の単純さで代替 2 より本決定を選んだ。将来この形に変えることは可能（可逆性は高い）

### References
- `application-design-questions.md` の Q6（回答 = B）
- `requirements.md` FR-4.1、FR-4.2、FR-3.1、FR-8.2

---

## ADR-009: 検証基盤は既存 mock スタックの拡張で実現する

### Status
Accepted

### Date
2026-08-05

### Context

FR-8.1 は「バインド位置と値をアサートできること」を Must とする（OQ-3）。現行 `MockPreparedStatement` の `setXxx` はすべて空実装で、`MockConnection.prepareStatement(String)` は SQL 引数を破棄する（`code-quality-assessment.md` #7）。この状態では FR-8.1 が満たせない。

選択肢は 4 つあった。既存 mock スタックの拡張、宣言済みで未使用の EasyMock 5.1.0 の活用、新規テストライブラリの追加、mock を `src/test` へ移動したうえでの拡張。

`org.tamacat.mock.sql` は `src/main` にあり production jar に同梱される（`code-quality-assessment.md` #13）。その移動は `requirements.md` OOS-6 によりスコープ外である。

### Decision

既存の `org.tamacat.mock.sql` スタックを拡張する。`MockConnection.prepareStatement(String)` が SQL を保持し、`MockPreparedStatement` の `setXxx` が位置と値を記録し、テストから取り出せる getter を追加する。

新規依存は追加しない（CON-6）。

### Consequences

**Positive**
- 新規依存が増えない。CON-6 と整合する。
- 既存の e2e テストが使っている mock スタックがそのまま強化されるため、テストの書き方が統一される。
- `MockPreparedStatement.executeUpdate()` が `0` を返す現行挙動は変更しない。既存テストへの影響を避ける。

**Negative**
- **記録機能も production jar に同梱される。** `org.tamacat.mock.sql` が `src/main` にあるためである。jar のサイズが増え、`MockDriver` が `DriverManager` に自己登録する現行の問題（`code-quality-assessment.md` #13）も変わらない。
- テスト専用のコードが production 成果物に含まれる状態を追認することになる。

**Neutral**
- この帰結は `requirements.md` OOS-6（mock の移動はスコープ外）に従った結果である。将来 mock を `src/test` へ移す判断は別途行える。

### Alternatives Considered

**代替 1: 宣言済みの EasyMock を使う**
- Pros: 新規依存なし。`org.tamacat.mock.sql` に触れない
- Cons: 既存 e2e テストは `org.tamacat.mock.sql` に依存しており、テストの書き方が 2 系統になる。EasyMock で `Connection` / `PreparedStatement` の全メソッドをモックするのは、既存スタックの拡張より手数が多い
- 却下理由: テストの一貫性

**代替 2: Mockito 等を新規追加する**
- Cons: 新規依存の追加。CON-6 の趣旨（依存を増やさない）に反する
- 却下理由: 既存手段で足りるため

**代替 3: mock を `src/test` へ移してから拡張する**
- Pros: production jar からモックが外れる。`code-quality-assessment.md` #13 の是正を兼ねる
- Cons: `requirements.md` OOS-6 が明示的にスコープ外としている。公開パッケージの移動は利用側に影響しうる（`org.tamacat.mock.sql` を production で使っている利用者がいれば壊れる）
- 却下理由: スコープ外であり、かつ FR-3.1 に触れるリスクがある

### References
- `application-design-questions.md` の Q8（回答 = A）
- `requirements.md` FR-8.1、OQ-3、OOS-6、CON-6

---

## ADR-010: FR-1.7（識別子位置の注入不能化）の機構は本ステージで決めない

### Status
Accepted

### Date
2026-08-05

### Context

`requirements.md` FR-1.7 は「バインドできない位置（テーブル名・カラム名・ORDER BY 句）に呼び出し側から渡された文字列が、SQL の構文として解釈されうる形でそのまま連結されないこと」を **Should Have** として定める。判定は「(a) 例外で拒否される、(b) 宣言済みメタデータに照合されて拒否される」のいずれかとした。

機構の選択は OQ-9 として本ステージに委ねられていた。対象は `Sort.sort(Object, Object)` の非 `Column` キー経路（`Sort.java:47-60`）と `Column.getFunctionName()`、`DataType.FUNCTION` である。

### Decision

**本ステージでは機構を決めない。** OQ-9 を未解決のまま Units Generation（2.7）に持ち越す。

本ステージの `components.md` / `component-methods.md` は FR-1.7 の機構を前提とした要素を含めない。`Sort` は変更しないコンポーネントとして扱う。

### Consequences

**Positive**
- FR-1.7 は Should Have であり Must ではない。Must の実現に集中できる。
- 2.7 で Unit に分解する際、FR-1.7 を独立した Unit として切り出すか、今回のスコープから外すかを判断できる。判断材料（他の Unit の規模）がその時点では揃っている。

**Negative**
- 設計が 1 点未完のまま次ステージに渡る。2.7 が FR-1.7 の扱いを決める必要がある。
- `Sort` を変更対象から外したため、FR-1.7 を後から実施する場合は `Sort` の設計を追加で行う必要がある。

**Neutral**
- FR-1.7 の受け入れ条件（`requirements.md` AC-10b）は (a) / (b) のどちらの機構でも判定できる形になっている。機構が後から決まっても受け入れ条件は変わらない。

### Alternatives Considered

**代替 1: メタデータ照合で決める**
- 内容: 宣言済みの `Table` / `Column` メタデータに照合し、一致しない識別子を例外で拒否する
- Pros: メタデータは既に存在するため追加設定が不要。ライブラリの設計思想（メタデータ宣言から SQL を組む）と一貫する
- Cons: `Sort.sort(Object k, Object o)` の非 `Column` キー経路は、意図的にメタデータ外の式を許すために存在する。照合を強制すると既存利用者が壊れる可能性がある（FR-3.1）
- 却下理由: 却下ではなく先送り。この選択肢は 2.7 以降も有効である

**代替 2: 許容文字集合で決める**
- 内容: 識別子に使える文字を正規表現で制限する
- Pros: メタデータにない式も通せる
- Cons: 何を許すかの線引きが恣意的になる
- 却下理由: 同上（先送り）

**代替 3: 今回は実施しないと決める**
- Pros: 判断が確定する
- Cons: Should Have を Must でないという理由だけで落とすのは早い。2.7 で規模を見てから判断する方が情報が多い
- 却下理由: 判断材料が揃う時点まで待つ方がよい

### References
- `application-design-questions.md` の Q9（回答 = D）
- `requirements.md` FR-1.7、AC-10b、OQ-9、A-6

---

## ADR-011: バインド値は節ごとのアキュムレータに分けて保持し、テキストの連結順に結合する

### Status
Accepted

### Date
2026-08-05

### Context

FR-1 の正しさは「SQL テキスト中の `?` の出現順と `BindValue` の並び順が一致すること」に尽きる（OQ-2）。当初の設計は「値リストをテキストアキュムレータと同一のライフサイクルで append する」とだけ述べ、リストを 1 本にするか複数にするかを規定していなかった。

レビューで、素朴な単一リスト実装が破綻することが判明した。`QueryImpl.getUpdateSQL`（`:236-274`）は SET 句と WHERE 句を**単一のループ**で組み立てるが、最終テキストは `query + where.toString()`（SET → WHERE）で連結する。ループの反復順は `updateColumns` の登録順であり、ライブラリ自身のフィクスチャでは主キーが 1 番目である。

| 根拠 | 内容 |
|---|---|
| `User.java:17` | `USER_ID` は `primaryKey(true)` |
| `User.java:23-24` | `registerColumn(USER_ID, PASSWORD, DEPT_ID, UPDATE_DATE, AGE)` — 主キーが登録順 1 番目 |
| `DefaultTable.java:16` | `LinkedHashSet<Column>` — 反復順は登録順 |
| `UserDao.java:41-45` | `addUpdateColumns(User.TABLE.columns())` — この順序が `updateColumns` になる |
| `QueryImpl.java:244-247` | 主キー列でループ 1 回目に `addWhere` が発火する |

したがって WHERE の値が SET の値より先に生成される。単一リストでは:

```
最終テキスト:  UPDATE users SET password=?,dept_id=?,update_date=?,age=? WHERE users.user_id=?
? の順序:      [password, dept_id, update_date, age, user_id]
単一リストの順: [user_id, password, dept_id, update_date, age]
```

**この不一致は例外を起こさない。** `?` の個数（5）と値の個数（5）が一致するため `PreparedSql.of(...)` の個数検査を通り、JDBC のパラメータ数検査も通る。誤った値が誤ったカラムに書き込まれた UPDATE が正常終了する。

### Decision

**値リストは、対になるテキストアキュムレータと 1 : 1 で持つ。複数のテキストアキュムレータを連結して最終 SQL を組む場合、値リストも同じ連結順で結合する。**

`QueryImpl` は `whereValues`（`where` と対）、`setValues`（SET 句と対）、`insertValues`（VALUES 句と対）を持つ。`getUpdatePreparedSql` は `setValues ++ whereValues` の順で結合する。

`Search` も同じ規則に従い、`search`（リテラル系テキスト）/ `bindSearch`（バインド系テキスト）/ `bindValues` の 3 つを、単一の private な append メソッドを経由してのみ変更する。

### Consequences

**Positive**
- ループの実行順に関係なく、値の並びが常にテキストの `?` の並びと一致する。
- 規則が構造として表現されるため、`QueryImpl` に新しい節（例: HAVING）が加わっても同じ規則を適用すればよい。
- `Search` の 3 状態を 1 メソッドに閉じ込めることで、片方だけ更新する経路が作れなくなる。

**Negative**
- `QueryImpl` の状態が増える（値リスト 3 本）。`setValues` / `insertValues` はメソッドローカルに近い生存期間だが、`whereValues` はインスタンス生存であり CON-7 の単回使用制約に従う。
- 実装者がこの規則を知らずに 1 本のリストで実装すると、**テストで検出しない限り本番まで到達する**。この失敗様式は FR-8.1（バインド位置と値のアサート）が検出対象とすべきものであり、Build and Test（3.6）で「UPDATE の SET + WHERE 混在ケース」を必ずテストする必要がある。

**Neutral**
- 個数検査（`PreparedSql.of`）はこの誤りを検出できない。検出は位置と値のアサート（FR-8.1）に依存する。両者は補完関係にある。

### Alternatives Considered

**代替 1: 値リストを 1 本にし、テキストも 1 本のアキュムレータに統一する**
- 内容: `getUpdateSQL` のループを SET 用と WHERE 用の 2 パスに分け、テキストも生成順に append する
- Pros: 値リストが 1 本で済む
- Cons: `QueryImpl.getUpdateSQL` の構造を大きく変える。`addWhere` の副作用（`useAutoPrimaryKeyUpdate = false`）のタイミングが変わり、既存の挙動に影響しうる。旧 `getUpdateSQL` の出力が変わるリスクがある（ADR-003 が禁じている）
- 却下理由: 旧メソッドの戻り値を変えないという ADR-003 の制約に抵触するため

**代替 2: 値に位置情報を持たせ、最後にソートする**
- 内容: `BindValue` に「テキスト上の位置」を持たせ、結合時にソートする
- Pros: 生成順に依存しない
- Cons: 位置をテキスト組み立て時に正確に知る必要があり、結局同じ問題を別の形で解くことになる。`BindValue` が可変になるか、位置を別途管理する必要が生じる
- 却下理由: 複雑さに見合わない

### References
- レビュー指摘 1（`aidlc-architecture-reviewer-agent`, iteration 1）
- `component-dependency.md`「順序の不変条件」、`component-methods.md` M-2「値アキュムレータ」
- `requirements.md` OQ-2、FR-1、FR-8.1

---

## ADR-012: 未束縛のプレースホルダを持つ `PreparedSql` は実行前に拒否する

### Status
Accepted

### Date
2026-08-05

### Context

ADR-004 の互換シム `PreparedSql.ofLiteral(String)` は、旧 `getInsertSQL(T)` 等が返すリテラル SQL を値ゼロの `PreparedSql` に包む。

しかし旧経路が生成する SQL は、BLOB カラム（`DataType.OBJECT`）を含む場合に**未バインドの `?` を含む**。`SQLParser.parseValue` が OBJECT に対して `"?"` を返し（`:112-113`）、`QueryImpl.getUpdateSQL` がそれを SET 句に置く（`:268`）ためである。現行はこの `?` を `Query.getBlobIndex()` で位置指定し `Dao.executeUpdate(String, int, InputStream)` 経由で束縛する設計である。

したがって `ofLiteral` に「`?` の個数 = 値の個数」を強制すると、BLOB を含む旧経路の SQL がすべて生成時に落ちる。一方、検査せずに `prepareStatement` へ渡すと、未束縛の `?` を持つ文が実行され、JDBC ドライバがパラメータ未設定を報告する。

### Decision

`ofLiteral` は生成時の個数検査を**行わない**。代わりに `PreparedSql` に `hasUnboundPlaceholders()` を持たせ、`DBAccessManager` の `PreparedSql` 版実行メソッドが実行前にこれを検査して、真なら `DaoException` を投げる。

エラーメッセージには、BLOB を含む更新は旧 BLOB 経路（`Dao.executeUpdate(String, int, InputStream)`）か新しい `getUpdatePreparedSql` の override を使うべき旨を示す。

`ofLiteral` で作られた `PreparedSql`（値ゼロかつ検査未実施）に対する `?` の数え方は、**引用符の外にある `?` のみを数える**。旧経路の SQL は値がリテラルとして埋め込まれており、値の中に `?` を含みうるためである。走査規則の詳細は Functional Design（3.1）で確定する。

### Consequences

**Positive**
- BLOB を含む旧経路の SQL が生成時に落ちない。ADR-004 の互換シムが機能する。
- 未束縛の `?` を持つ文が実行に至らず、明確なエラーメッセージで早期に失敗する。現行の「実 DB まで到達してから失敗する」より診断しやすい。

**Negative**
- `hasUnboundPlaceholders()` は引用符を考慮した走査を要する。単純な文字数え上げでは誤判定する。この走査は完全な SQL パーサではないため、極端な入力（エスケープされた引用符の連続等）で誤判定する余地が残る。
- 現行では `Statement` で実行されて DB がエラーを返していたケースが、`DaoException` に変わる。**例外の型と発生位置が変わる挙動変更である。**

  この「現行も失敗する」という主張には限定が要る。**実 JDBC ドライバに対しては成り立つ**が、リポジトリのテスト用 `MockStatement` は SQL を検証せず `executeUpdate` が無条件に `1` を返すため（`MockStatement.java:71-74`）、モック上では現行が「成功」する。ただし調査の結果、この経路を実際に通す実行テストは存在しない（`FileData` を参照するのは `QueryImplTest02.java` / `FileDataDao.java` のみで、いずれも `Dao.create` / `update` を経由しない）。したがって既存テストが壊れることはない。

**Neutral**
- 新経路（`of(...)` で作られた `PreparedSql`）は生成時に個数検査を通っているため、`hasUnboundPlaceholders()` は常に偽である。検査のコストは旧経路にのみかかる。

### Alternatives Considered

**代替 1: `ofLiteral` でも個数検査を行う**
- Cons: BLOB を含む旧経路の SQL がすべて生成時に落ちる。ADR-004 の互換シムが機能しなくなり、既存サブクラスが壊れる（NFR-3 違反）
- 却下理由: 互換性を壊すため

**代替 2: 検査せず、そのまま実行してドライバに任せる**
- Pros: 実装が最も単純
- Cons: エラーがドライバ固有のメッセージになり、原因が「BLOB を含む旧経路を新実行経路に流した」ことだと利用者に分からない
- 却下理由: 診断性が低い

### References
- レビュー指摘 5（`aidlc-architecture-reviewer-agent`, iteration 1）
- `component-methods.md` C-2「`ofLiteral` が `?` 個数検査を行わない理由と、それが生む危険」
- ADR-004、`requirements.md` FR-1.5、NFR-3

---

## 承認済み要件との差分（2.7 までに `requirements.md` を更新する対象）

ADR-002 の決定（Q0-b = C）により、次の 2 点を Units Generation（2.7）の承認ゲートまでに `requirements.md` に反映する。

| # | 対象 | 現在の記述 | 更新後の趣旨 | 由来 |
|---|---|---|---|---|
| 1 | FR-7.1 / FR-7.2 | `Query.where(String)` を含む raw 経路は SM-1 の判定対象外 | 旧 `where(String)` の直接利用は対象外だが、`param()` 経由の値は `prepare()` + `where(Param)` という代替経路で SM-1 の対象に含まれる | ADR-002 |
| 2 | NFR-1 | 「対象範囲内で、値を SQL 文字列に連結する経路が 0 件」 | 「**実行経路**に値の文字列連結が 0 件」— 非推奨の public メソッド経由で到達できるリテラル生成経路は判定対象外とする | ADR-003 |

この 2 点以外に `requirements.md` との差分はない。FR-1 / FR-2 / FR-3 / FR-4 / FR-5 / FR-6 / FR-8 と NFR-2〜NFR-7 は、本設計がそのまま満たす。

---

## 本ステージで解いた Open Questions

| OQ | 状態 | 解 |
|---|---|---|
| OQ-1（戻り値契約） | **解決** | ADR-003 — 旧 API はリテラル維持 + 非推奨、バインド版を新メソッドで追加 |
| OQ-2（値の保持と順序） | **解決** | ADR-001 + ADR-011 — 値リストを節ごとのテキストアキュムレータと 1 : 1 で持ち、テキストの連結順に結合する。加えて生成時に `?` 個数を検査する |
| OQ-3（検証基盤と新規依存） | **解決** | ADR-009 — 既存 mock スタックを拡張。新規依存は追加しない |
| OQ-5（リポジトリ外利用者） | **解決** | ADR-003 + ADR-006 — 旧 API を無変更で残し、`default` 実装をリテラルにフォールバックさせることで、外部実装・外部利用者を壊さない |
| OQ-9（FR-1.7 の機構） | **未解決** | ADR-010 — 2.7 に持ち越す |
| OQ-4（カバレッジ 80%） | 対象外 | NFR Requirements（3.2）の担当 |
| OQ-6（Derby 依存） | 対象外 | Build and Test（3.6）の担当。ADR-007 の Negative がこれに関連する |
| OQ-7（CodeQL の測定） | 対象外 | Build and Test（3.6）の担当。ADR-003 の Negative がこれに関連する |
| OQ-8（利用アプリ側の監査） | 未解決 | 上流から継承。本ステージでは解けない |

---

---

## レビュー後の修正記録（iteration 上限到達後）

`aidlc-architecture-reviewer-agent` によるレビューは stage 定義の上限（`reviewer_max_iterations: 2`）に達した。iteration 2 の判定は **NOT-READY** で、指摘は 2 件であった。いずれも修正内容が一意に定まるため、上限到達後に適用した。**この修正はレビュアーによる再検証を受けていない。**

| # | 指摘 | 適用した修正 | 検証 |
|---|---|---|---|
| F-1（blocking） | `Search.getSearchParam()` を package-private とする指定は、唯一の呼び出し元 `QueryImpl` から呼べない。`Search` は `org.tamacat.dao`（`Search.java:5`）、`QueryImpl` は `org.tamacat.dao.impl`（`QueryImpl.java:5`）で別パッケージであり、Java の package-private はサブパッケージに及ばない | `getSearchParam()` を **public** に変更（`getSearchString()` と同じ扱い）。`component-methods.md` M-3、`decisions.md` ADR-003、`services.md` の該当箇所を訂正 | 両ファイルの `package` 宣言を実ソースで確認した |
| F-2（non-blocking） | ADR-012 の「現行もこのケースは失敗する」という主張は、実 JDBC ドライバには成り立つがリポジトリの `MockStatement` には成り立たない（`MockStatement.java:71-74` が `executeUpdate` で無条件に `1` を返す） | ADR-012 の Consequences に限定を追記。あわせて、この経路を通す実行テストが存在しないため既存テストは壊れないことを記録 | レビュアーの調査結果（`FileData` の参照箇所は `QueryImplTest02.java` / `FileDataDao.java` のみで `Dao.create` / `update` を経由しない）に基づく |

**F-1 の帰結**: `getSearchParam()` が public になったことで、`Search` の公開メソッドが 1 つ増える。これは FR-3.1 の互換維持対象が 1 つ増えることを意味するが、追加のみであり既存 API の削除・変更はない。`getSearchString()`（非推奨）のバインド版という位置づけであり、公開されること自体は設計上不自然ではない。

iteration 1 の指摘 5 件（UPDATE のバインド順序、`escapeLike` のシグネチャ、`Search` の 3 状態同期、`getBlobIndex()` の生成タイミング、`ofLiteral` の未束縛 `?`）は、iteration 2 のレビューですべて解消が確認されている。

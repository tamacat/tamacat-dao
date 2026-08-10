# Application Design — Questions

## Sources

- `requirements.md`（requirements-analysis, 2.3）— FR-1〜FR-8、NFR-1〜NFR-7、制約 CON-1〜CON-8、前提 A-1〜A-6、Open Questions OQ-1〜OQ-9。本ステージは **OQ-1（`getSelectSQL()` 等の戻り値契約）**、**OQ-2（バインド値の保持場所と順序）**、**OQ-3（検証基盤の実現手段と新規依存の可否）**、**OQ-5（リポジトリ外利用者への影響の扱い）**、**OQ-9（FR-1.7 の機構）** を解く責務を負う。
- `architecture.md`（codekb）— 現行のレイヤ構成、`SQLParser` の 2 プリミティブ、実行面（Execution Surface）、方言アーキテクチャ、raw-SQL エスケープハッチ。
- `component-inventory.md`（codekb）— 各コンポーネントの責務と依存、SQL-bearing の別。
- `stories.md`（user-stories, 2.4）— 本ワークフローのスコープで **SKIP** のため存在しない。要件は `requirements.md` から直接引き継ぐ。
- `team-practices`（practices-discovery, 2.2）— 同じく **SKIP** のため存在しない。`aidlc/spaces/default/memory/org.md` の既定に従う。

### 本ステージが前提とする実コードの事実

設問の選択肢は次の事実に基づく（すべて `src/main` の実ソースで確認済み）。

- `Query` は **public interface**（`Query.java:23`）で、実装は `QueryImpl` のみ（リポジトリ内）。Java 8 の `default` メソッドを使えば、メソッド追加は binary/source ともに非破壊である。
- `Search` は **public class**（`Search.java:14`）。`getSearchString()`（`:88-90`）が `StringBuilder` の中身を返す。`Search` は自前の `SQLParser` フィールドを持つ（`:30, :33, :37`）。
- `SQLParser.value(...)`（`:39-73`）と `parseValue(...)`（`:85-117`）はいずれも **public** で `String` を返す。`parseValue` は `DataType.OBJECT` に対してのみ `"?"` を返す（`:112-113`）。
- `DBAccessManager` の実行面は `executeQuery(String)`（`:97-104`）、`executeUpdate(String)`（`:106-113`）、`preparedStatement(String)`（`:88-95`）。いずれも先頭で `getExecutedQuery().add(sql)` を呼ぶ。
- `getExecutedQuery()` は **`List<String>` を返す public メソッド**（`:181-188`）。既存 e2e テストはこの戻り値に対してアサートしている。
- `Dao` の実行メソッド `executeQuery(String)` / `executeUpdate(String)` / `executeUpdate(String,int,InputStream)` はいずれも **protected**（`Dao.java:249, :257, :266`）。したがってサブクラス（利用側 DAO）からは見える。

---

## Call-path trace (2026-08-05T04:40:00Z)

ユーザーの指摘「一連の処理全体を見て判断が必要だが、確認しているのか？」を受けて、SQL テキストの生成から実行までの呼び出し関係を `src/main` と `src/test` の全体に対して追跡した。結果、当初の設問の前提が 2 か所で成立しないことが判明した。以下はすべて実ソースで確認した事実である。

### F1. `getSelectSQL()` の戻り値は、リポジトリ内では実行以外に使われていない

`src/main` の呼び出し元は 8 箇所、すべてライブラリ内部である。

| 呼び出し元 | 用途 |
|---|---|
| `Dao.java:157` | `search(Query)` — 実行に直結 |
| `Dao.java:180` | `searchList(Query,int,int)` — 実行に直結 |
| `MySQLDao.java:41` | LIMIT を付けて実行 |
| `OracleDao.java:32` | `searchListForOracle` — 死んだコード（FR-6.1 の対象） |
| `QueryImpl.java:390, :395, :400, :405` | `andIn` / `andNotIn` / `andExists` / `andNotExists` のサブクエリ合成（FR-1.6 の対象） |

利用側に戻り値を渡す経路は `Query` インターフェース経由のみで、リポジトリ内に外部利用例はない。**SELECT については、戻り値の中身を変えてもリポジトリ内で壊れるのはテストだけである。**

### F2. INSERT / UPDATE / DELETE は事情がまったく異なる — これが最大の設計制約

`Dao.getInsertSQL(T)` / `getUpdateSQL(T)` / `getDeleteSQL(T)`（`Dao.java:212-222`）は **protected で既定は `throw new RuntimeException(new NoSuchMethodException())`**、すなわち **利用側 DAO サブクラスが override する拡張点**である。実際のサブクラス実装（`UserDao.java:32-53`、`FileDataDao.java`、`UserStatDao.java` すべて同形）は次の形をとる。

```java
@Override
protected String getInsertSQL(User data) {
    Query<User> query = createQuery().addUpdateColumns(User.TABLE.columns());
    return query.getInsertSQL(data);   // Query はここでローカルに作られ、String だけが返る
}
```

そして `Dao.create(T)` は `executeUpdate(getInsertSQL(data))` を呼ぶだけである（`Dao.java:224-234`）。

**帰結: `Dao` は `Query` オブジェクトに到達できない。** サブクラスがメソッド内でローカルに生成して捨てるためである。したがって「`Query` が値を内部に保持し、`Dao` が実行時に取り出す」という設計（当初 Q1 の選択肢 C）は、**INSERT / UPDATE / DELETE 経路では原理的に成立しない**。この拡張点の契約（`String` を返す override）を変えずに値を運ぶ方法は存在しない。

### F3. ライブラリの正典的な使い方が、FR-7.1 が対象外とした raw 経路を通る

`Dao.param(Column, Conditions, String...)`（`Dao.java:137-139`）は **public** で、`parser.value(...)` の戻り値——すなわち**値がリテラルとして埋め込まれた述語文字列**——をそのまま返す。`DaoAdapter.param`（`:120-122`）はこれに委譲する。

サンプル DAO とテストはこれを `Query.where(String)` に渡す。

| 箇所 | コード |
|---|---|
| `UserDao.java:22` | `.where(param(User.USER_ID, Condition.EQUAL, data.getValue(User.USER_ID)))` |
| `UserDao.java:43` | `.where(param(User.USER_ID, Condition.EQUAL, data.val(User.USER_ID)))` |
| `FileDataDao.java:12, :30` | 同形 |
| `MySQLDaoTest.java:83` | `query.where(dao.param(User.USER_ID, Condition.EQUAL, user.val(User.USER_ID)))` |

`Query.where(String)` は `requirements.md` **FR-7.1 が SM-1 の判定対象外とした raw 経路**である。しかし `param()` が運ぶのは**実行時の値**（`data.getValue(...)`）であって、開発者が書いた SQL ではない。FR-7.1 の根拠（Q5 = A の理由「呼び出し側が渡すのは開発者が書いた SQL であり、実行時のユーザー入力ではない」）が、この経路には当てはまらない。

これは Q11 でサブクエリ経路について認めたのと同じ性質の食い違いである。ただし**こちらはライブラリの主要な使い方**であり、この経路がリテラルのまま残ると SM-1 は実質的に空洞になる。

### F4. `Search` → `Query` の受け渡しも文字列である

`QueryImpl.addSearch`（`:455-461`）が `search.getSearchString()` を `addWhere` に渡す（`:459`）。`Search.and(Search)` / `or(Search)`（`Search.java:54-66`）も他の `Search` の文字列を取り込む。`QueryImpl.andOuterJoin`（`:353`）も同様。したがって `Search` が値を保持しても、現行の境界を通ると文字列しか渡らない。

### F5. UPDATE / DELETE の主キー条件も文字列経路を通る

`QueryImpl.getUpdateSQL`（`:246`）と `getDeleteSQL`（`:285`）は、主キー値を `parser.value(col, Condition.EQUAL, data.getValue(col))` でリテラル化し、`addWhere("and", ...)` に渡す。すなわち SET 句の値と WHERE の主キー値は別経路で組み立てられ、テキスト上の順序は「SET 句 → WHERE」である。バインド順序を作る場合はこの順序に一致させる必要がある。

### F6. `getBlobIndex()` はリポジトリのどこからも呼ばれていない

`src/main` / `src/test` を全走査した結果、宣言（`Query.java:158`）と実装（`QueryImpl.java:469`）以外に呼び出し元は存在しない。BLOB 用の `FileDataDao` すら呼んでいない。`code-quality-assessment.md` の記載を実測で確認した。

### この追跡が設問に与える影響

- 当初 Q1 の選択肢 C（public 表面を増やさない）は **F2 により INSERT / UPDATE / DELETE で不成立**。SELECT だけなら成立しうる。
- 当初 Q2 の判断材料が変わる。SELECT と INSERT/UPDATE/DELETE は制約がまったく異なるため、同じ答えで括れない。
- **F3 は承認済み要件 FR-7.1 に対する反証である。** これをどう扱うかを先に決めないと、SM-1 の実効範囲が定まらず Q2 も決められない。→ Q0 として先に問う。

---

## Q0（追跡により発生）. `Dao.param()` + `Query.where(String)` 経路（F3）を、どう扱いますか？

`requirements.md` FR-7.1 はこの経路を SM-1 の判定対象外とした。しかし追跡の結果、これはライブラリの正典的な使い方であり、運ばれるのは実行時の値である。承認済みの要件に対する反証であるため、影響範囲を確認したうえで決める。

**影響を受ける成果物**: `requirements.md` の FR-7.1 / FR-7.2 / NFR-1、および `requirements-analysis-questions.md` の Q5。

- A. requirements-analysis（2.3）に戻って FR-7.1 を改訂する。`param()` + `where(String)` を SM-1 の対象に含め、値を運べる代替経路を要件とする
- B. Application Design 内で扱う。FR-7.1 は文言上そのままにし、`param()` 経路のバインド化を本ステージの設計判断（ADR）として決め、要件との差分を `decisions.md` に明記する
- C. 現行 FR-7.1 のまま進む。`param()` + `where(String)` はリテラルのまま残し、バインド版の別 API を用意して利用側に移行を促す（SM-1 の判定は新 API 経路のみ）
- D. まだ決められない
- X. Other (please specify)

[Answer]: （Q0-a / Q0-b に分割。下記参照）

**議論の経緯（2026-08-05T04:45:00Z〜）**

初回提示時のユーザー回答は「D. まだ決められない」。理由は、設問が**中身**（②をバインド化するか）と**手続き**（承認済み要件との差分をどこで正すか）を混ぜていたため。分割して再提示した。

再提示後のユーザー回答は「具体的にどのように対応しようと考えているのか伝わらないため、判断できない」。抽象的な「値を保持する型」「public メソッド 2 つ」という説明では判断材料にならないという指摘。以下の具体形を提示して再々提示した。

**Java の制約（判断に効く事実）**: 戻り値の型だけが異なるオーバーロードは作れない。したがって `param(Column, Conditions, String...)` と同じ引数リストで別の型を返すメソッドは**別名**にする必要がある。

**① `Search` 経由 — 公開 API 変更ゼロ**

`Search.and(Column, Conditions, String... values)` は値を型付き引数で受け取るため、`Search` が内部に値リストを持てば足りる。`QueryImpl.addSearch`（`:455-461`）がテキストと値の両方を引き取るよう内部変更する。

```java
// 呼び出し側は変更なし
Search s = createSearch().and(User.NAME, Condition.LIKE_PART, "Tama");
Query<User> q = createQuery().select(User.TABLE.columns()).and(s, sort);
// 変更後: SQL "... where users.name like ? escape '$'"   bind[1] = "%Tama%"
```

**② `param()` 経由 — 追加 2 メソッド**

```java
// 追加（別名。param() のオーバーロードにはできない）
public Param prepare(Column column, Conditions condition, String... values)   // Dao / DaoAdapter
default Query<T> where(Param param)                                           // Query（default メソッド）

// 呼び出し側の移行
- .where(param(User.USER_ID, Condition.EQUAL, data.getValue(User.USER_ID)))
+ .where(prepare(User.USER_ID, Condition.EQUAL, data.getValue(User.USER_ID)))
// 変更後: SQL "... where users.user_id=?"   bind[1] = "admin"
```

旧 `param()` と `where(String)` は無変更で残る。移行しない利用者は現行どおり動作する。

**③ INSERT / UPDATE / DELETE — 新しい protected 拡張点**

```java
// Dao に追加。既定実装が旧 override に委譲するため既存サブクラスは無変更で動く
protected PreparedSql getInsertPreparedSql(T data) {
    return PreparedSql.ofLiteral(getInsertSQL(data));   // 既存サブクラスはここを通る
}
public int create(T data) { return executeUpdate(getInsertPreparedSql(data)); }

// 新しいサブクラスはこちらを override する
@Override protected PreparedSql getInsertPreparedSql(User data) {
    Query<User> query = createQuery().addUpdateColumns(User.TABLE.columns());
    return query.getInsertPreparedSql(data);
}
```

---

## Q0-a（中身）. `param()` + `where(String)` 経路②を、バインド化の対象に含めますか？

- A. 含める — `prepare()` と `Query.where(Param)` を追加し、サンプル DAO とテストを `param` → `prepare` に移行する。旧 `param()` / `where(String)` は無変更で残す
- B. 含めない — ②はリテラルのまま残し、①と③だけをバインド化する
- C. 含めたうえで旧 API を非推奨にする — A に加えて旧 `param()` と `where(String)` に `@Deprecated` を付ける
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. 含める（prepare + where(Param)）
**Mode:** guided | **Timestamp:** 2026-08-05T04:50:00Z

---

## Q0-b（手続き）. Q0-a = A により生じる、承認済み `requirements.md` との差分をどこで正しますか？

`requirements.md` FR-7.1 は `Query.where(String)` を SM-1 の判定対象外と明記している。Q0-a = A はこの記述と矛盾する（`param()` 経由の値はバインドされることになる）。影響を受けるのは FR-7.1 / FR-7.2 / NFR-1 と、2.3 の Q5 の回答である。

- A. 本ステージの `decisions.md` に ADR として差分を明記して進む（最も早い）
- B. requirements-analysis（2.3）に戻って FR-7.1 / FR-7.2 / NFR-1 を書き換えてから 2.6 に戻る（整合性は最も高いが往復が増える）
- C. `decisions.md` に記録して進め、Units Generation（2.7）の承認ゲートまでに `requirements.md` を更新する
- D. まだ決められない
- X. Other (please specify)

[Answer]: C. 今は ADR、2.7 までに更新
**Mode:** guided | **Timestamp:** 2026-08-05T04:55:00Z

---

## Q1. SQL テキストとバインド値を一緒に運ぶ「器」を、どの形にしますか？

現行は SQL テキスト（`String`）だけが `Query` → `Dao` → `DBAccessManager` を流れる。値をバインドで渡すには、テキストと値の並びを対にして運ぶ必要がある。

- A. 新しい公開型（例: `PreparedSql` — SQL テキスト + 値の並び）を `org.tamacat.dao` に導入し、`Query` / `Search` にそれを返す `default` メソッドを追加する
- B. 新しい型は作らず、`Query` / `Search` 自身が値を蓄積し、`getBindValues()` 相当の `default` アクセサを追加する
- C. 新しい公開型も公開アクセサも作らない。値の受け渡しは `QueryImpl` → `Dao` の内部（package-private / protected）だけで完結させ、public API の表面を一切増やさない
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. 2 つに分ける（Param / PreparedSql）
**Mode:** guided | **Timestamp:** 2026-08-05T04:55:00Z

---

## Q2. `Query.getSelectSQL()` / `getInsertSQL()` / `getUpdateSQL()` / `getDeleteSQL()` の戻り値を、どうしますか？（OQ-1 の中核）

`requirements.md` FR-3.3 が本ステージに委ねた決定。FR-3.1（破壊的変更をしない）と NFR-1（SM-1: 値の文字列連結がゼロ）が正面から衝突する点である。

- A. リテラル埋め込みの SQL を返し続ける（戻り値の中身も維持）。バインドで実行する経路を別に用意し、既存メソッドは互換のためだけに残す。**帰結**: リテラル生成コードが残るため、NFR-1 の判定を「実行経路にリテラル連結がゼロ」と読み替える必要がある
- B. 戻り値の中身を `?` 入りのパラメータ化 SQL に変える（シグネチャは維持）。**帰結**: バイナリ互換は保たれるが、戻り値を直接実行していた外部利用者は壊れる。既存テストのアサーションはすべて書き換えになる（FR-8.2 の対応表が必要）
- C. A と同じくリテラル埋め込みを返すが、`@Deprecated` を付けて将来の削除意思を示す。**帰結**: A の帰結に加え、利用側に移行の合図を出せる
- D. まだ決められない
- X. Other (please specify)

[Answer]: C. リテラル維持 + 非推奨 + 新メソッド
**Mode:** guided | **Timestamp:** 2026-08-05T04:55:00Z

---

## Q3. `Search.getSearchString()`（public、WHERE 断片を `String` で返す）は、どうしますか？

`Query.andOuterJoin` は内部で `search.getSearchString()` を親 SQL に連結しており（`QueryImpl.java:351-356`）、`Search.and(Search)` / `or(Search)` も同様に他の `Search` の文字列を取り込む（`Search.java:54-66`）。つまりこのメソッドは公開 API であると同時に内部の合成経路でもある。

- A. Q2 と同じ扱いにする（Q2 が A ならリテラル、B なら `?` 入り）
- B. Q2 とは独立に扱う。`getSearchString()` はリテラル埋め込みを返し続け、内部の合成は別経路で行う
- C. Q2 とは独立に扱う。`getSearchString()` は `?` 入りを返し、`Query` 側の戻り値だけ Q2 の判断に従う
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. Q2 と揃える（リテラル維持 + @Deprecated、内部の受け渡しは値を含む新アクセサへ）
**Mode:** guided | **Timestamp:** 2026-08-05T05:00:00Z

---

## Q4. `SQLParser`（public class、`value` / `parseValue` はいずれも public で `String` を返す）を、どう作り替えますか？

現行の `SQLParser` は「値を SQL リテラルに描画する」ことが責務そのものである。バインド化するとこの責務は「プレースホルダを置き、値を別に返す」に変わる。ただし FR-2.1（型検証）と FR-2.3（LIKE エスケープ）は維持する必要があり、これらは現行 `SQLParser` の中にある。

- A. 既存の public メソッドは互換のため残し、バインド用の新メソッド群を同じクラスに追加する（1 クラス 2 系統）
- B. バインド用の新しいクラス（例: `PreparedSQLParser`）を追加し、`SQLParser` は互換のためそのまま残す（責務ごとに別クラス）
- C. 型検証（FR-2.1）と LIKE エスケープ（FR-2.3）を共通のコンポーネントに切り出し、リテラル系とバインド系の両方がそれを使う（3 コンポーネント構成）
- D. まだ決められない
- X. Other (please specify)

[Answer]: C. 共通部を切り出す（型検証と LIKE エスケープを共通コンポーネント化した 3 構成）
**Mode:** guided | **Timestamp:** 2026-08-05T05:00:00Z

---

## Q5. 実行経路（`Dao` と `DBAccessManager`）を、どう拡張しますか？

`DBAccessManager.executeQuery(String)` / `executeUpdate(String)` は public、`Dao.executeQuery(String)` / `executeUpdate(String)` は protected（利用側 DAO サブクラスから見える）。どちらもシグネチャ削除はできない。

- A. 既存メソッドは残し、値を受け取るオーバーロード（例: `executeQuery(String sql, List<?> values)`）を両クラスに追加する
- B. `Dao` 側は `Query` オブジェクトを受け取る新メソッド（例: `executeQuery(Query<T>)`）を追加し、`DBAccessManager` 側だけ値付きオーバーロードを持つ
- C. `DBAccessManager` に `PreparedStatement` を組み立てて実行する単一の新メソッドを置き、`Dao` は既存 protected メソッドの中身をそこへ委譲する（`Dao` の public/protected 表面は増やさない）
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. Dao（protected）と DBAccessManager（public）の両方に PreparedSql 版を追加
**Mode:** guided | **Timestamp:** 2026-08-05T05:00:00Z

---

## Q6. `DBAccessManager.getExecutedQuery()`（`List<String>` を返す public メソッド）に、FR-4.1 のバインド値をどう載せますか？

FR-4.1 は「SQL テキストに加えてバインド値も記録する」ことを求める。しかし戻り値の型は `List<String>` であり、既存 e2e テスト（`UserDaoTest` 等）が `get(0)` に対して文字列アサートしている。型を変えると破壊的変更になる。

- A. `List<String>` のまま、値を含む 1 行の文字列として記録する（例: SQL の後ろに値の並びを付す）。**帰結**: 既存テストのアサーション文字列は変わるが型は不変
- B. `List<String>` はそのまま SQL テキストのみを保持し、バインド値を返す別の public メソッド（例: `getExecutedBindValues()`）を追加する。**帰結**: 既存テストのアサーションは変わらない可能性がある
- C. 値を保持する新しい型のリストを返す新メソッドを追加し、`getExecutedQuery()` はそこから SQL テキストだけを射影して返す互換シムにする
- D. まだ決められない
- X. Other (please specify)

[Answer]: B. getExecutedQuery() は SQL テキストのみ維持し、バインド値を返す別の public メソッドを追加
**Mode:** guided | **Timestamp:** 2026-08-05T05:00:00Z

---

## Q7. LIKE の `escape 'X'` 句（FR-2.3）を、バインド下でどう成立させますか？

`escape 'X'` は SQL テキスト側の構文であり、エスケープ文字 `X` は値の中身から動的に選ばれる（`SQLParser.java:124-137`）。値をバインドすると、テキストを組む時点では値を見ていない、という順序の問題が生じる。

- A. 値を先に検査してエスケープ文字を決め、テキストに `escape 'X'` を埋め込んだうえで、エスケープ済みの値をバインドする（テキスト生成時に値を参照する）
- B. エスケープ文字を固定（例: 常に `$`）にし、値の中の当該文字自体もエスケープする。テキストは値に依存しなくなる
- C. `escape` 句を付けず、`%` / `_` をエスケープした値をそのままバインドする（方言依存の挙動になる）
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. 値を先に検査してエスケープ文字を決め、テキストに escape 'X' を埋め、エスケープ済みの値をバインドする — FR-2.3（現行挙動の維持）から一意に決まるため設問化せず決定
**Mode:** guided | **Timestamp:** 2026-08-05T04:55:00Z

---

## Q8. FR-8.1（バインド位置と値をアサートできること）を、どの手段で実現しますか？（OQ-3）

現行 `MockPreparedStatement` の `setXxx` はすべて空実装で、`MockConnection.prepareStatement(String)` は SQL 引数を破棄する。`org.tamacat.mock.sql` は `src/main` にあり、production jar に同梱されている（`code-quality-assessment.md` #13）。EasyMock 5.1.0 が test スコープに宣言済みだが参照されていない。

- A. 既存の `org.tamacat.mock.sql` スタックを拡張する（`MockPreparedStatement` に記録機能、`MockConnection` に SQL 保持を追加）。新規依存なし。ただし記録機能も production jar に同梱される
- B. 宣言済みの EasyMock を使ってテスト側でモックする。新規依存なし。`org.tamacat.mock.sql` には触れない
- C. 新しいテストライブラリ（Mockito 等）を test スコープで追加する
- D. `org.tamacat.mock.sql` を `src/test` へ移し、そのうえで拡張する（production jar からモックを外すことを兼ねる）。**注**: これは公開パッケージの移動であり、`code-quality-assessment.md` #13 の是正でもあるが、`requirements.md` のスコープ外（OOS-6）である
- X. Other (please specify)

[Answer]: A. 既存の org.tamacat.mock.sql スタックを拡張（新規依存なし）
**Mode:** guided | **Timestamp:** 2026-08-05T05:05:00Z

---

---

## Q9（OQ-9）. FR-1.7（識別子位置の注入不能化、Should Have）の機構を本ステージで決めますか？

- A. 宣言済みの `Table` / `Column` メタデータに照合し、一致しない識別子を例外で拒否する
- B. 識別子に使える文字を正規表現で制限し、違反を例外で拒否する
- C. 今回は実施せず、OQ-9 を将来の課題として残す
- D. まだ決められない
- X. Other (please specify)

[Answer]: D. まだ決められない
**Mode:** guided | **Timestamp:** 2026-08-05T05:05:00Z
**帰結:** OQ-9 は未解決のまま残る。FR-1.7 は Should Have であるため、実施可否の判断は Units Generation（2.7）まで持ち越せる。本ステージの `components.md` / `component-methods.md` は FR-1.7 の機構を前提とした要素を含めない。

---

## Answer analysis

Q0-a / Q0-b / Q1〜Q9 の回答を横断して点検した。

**整合が確認できたもの**

- Q0-a = A（`prepare()` + `where(Param)` を追加）と Q2 = C（`getSelectSQL()` はリテラル維持 + 非推奨 + 新メソッド）と Q3 = A（`getSearchString()` も同様）は、いずれも「旧 API は無変更で残し、バインド版を加算的に追加する」という一貫した規則になっている。FR-3.1（破壊的変更をしない）を厳密に満たす。
- Q1 = A（`Param` と `PreparedSql` の 2 型）と Q5 = A（両クラスに `PreparedSql` 版を追加）は整合する。`Param` は WHERE に渡す断片、`PreparedSql` は実行できる完成文であり、実行 API が受け取るのは後者のみになる。
- Q4 = C（型検証と LIKE エスケープを共通コンポーネント化）は、Q2 = C / Q3 = A がリテラル系を残すことを前提に必要となる。リテラル系とバインド系が同じ検証・エスケープ規則を共有できる。
- Q6 = B（別メソッド追加）は Q5 = A と整合する。実行経路が `PreparedSql` を受け取るなら、値の記録もその時点で行える。
- Q8 = A（既存 mock スタック拡張）は `requirements.md` OOS-6（mock の `src/test` 移動はスコープ外）と整合する。

**要件との差分（Q0-b = C により 2.7 までに `requirements.md` を更新する対象）**

1. **FR-7.1 / FR-7.2 の対象範囲。** Q0-a = A により `param()` + `where(String)` 経路は SM-1 の対象に含まれる（新 API 経由）。FR-7.1 の「`Query.where(String)` は SM-1 の判定対象外」という記述は、「旧 `where(String)` は対象外だが、`prepare()` + `where(Param)` という代替経路を提供する」に改める必要がある。
2. **NFR-1 の判定の読み。** Q2 = C / Q3 = A により、リテラル生成コード（`SQLParser` のリテラル系）は非推奨の public メソッド経由で到達可能なまま残る。NFR-1 の「対象範囲内で、値を SQL 文字列に連結する経路が 0 件」は、「**実行経路**に値の文字列連結が 0 件」と読み替える必要がある。

**未解決のまま残るもの**

- OQ-9（FR-1.7 の機構）— Q9 = D。Should Have のため 2.7 まで持ち越せる。
- OQ-5（リポジトリ外利用者の依存）— 本ステージでは解けない。Q2 = C / Q3 = A により旧 API を無変更で残す設計にしたため、影響は最小化されている。

**Q8 = A の帰結（記録）**

`org.tamacat.mock.sql` は `src/main` にあるため、記録機能を追加すると production jar に同梱される。これは `code-quality-assessment.md` #13 が指摘する既存の状態を変えないという判断であり、OOS-6 と整合する。

---

## Consolidated Summary Confirmation

本ステージの設計判断を以下に確定する。この確認が `Looks correct` になるまで設計成果物（`components.md` 他 5 点）は生成しない。

### 追跡により確定した設計制約（F1〜F6）

| # | 事実 | 設計への影響 |
|---|---|---|
| F1 | `getSelectSQL()` の呼び出し元はすべてライブラリ内部（8 箇所） | SELECT 経路は内部だけで完結させられる |
| F2 | `Dao.getInsertSQL(T)` は利用側が override する protected 拡張点で、`Dao` は `Query` に到達できない | INSERT/UPDATE/DELETE には新しい protected 拡張点が必須 |
| F3 | `param()` + `where(String)` が正典的な使い方で、運ぶのは実行時の値 | FR-7.1 の前提が成立しない → Q0-a で対象に含めた |
| F4 | `Search` → `Query` の受け渡しは文字列 | 内部アクセサで値も渡す必要がある |
| F5 | UPDATE/DELETE の主キー値も文字列経路（SET 句 → WHERE の順） | バインド順序をこの順に合わせる |
| F6 | `getBlobIndex()` はどこからも呼ばれていない | FR-3.2 の契約定義はリポジトリ内で何も壊さない |

### 確定した設計判断

| # | 決定 |
|---|------|
| Q0-a | `param()` + `where(String)` 経路をバインド化の対象に含める。`prepare()`（`Dao` / `DaoAdapter`）と `Query.where(Param)`（`default` メソッド）を追加。旧 `param()` / `where(String)` は無変更で残す |
| Q0-b | 承認済み要件との差分は `decisions.md` に ADR として記録し、Units Generation（2.7）の承認ゲートまでに `requirements.md` を更新する |
| Q1 | 値を運ぶ型は 2 つに分ける — `Param`（WHERE に渡す述語断片 + 値）と `PreparedSql`（実行できる完成文 + 値） |
| Q2 | `getSelectSQL()` 等はリテラルを返し続け `@Deprecated` を付ける。`getSelectPreparedSql()` 等を `default` メソッドで追加し、`Dao` の内部実行は新メソッドに切り替える |
| Q3 | `Search.getSearchString()` も Q2 と同じ扱い（リテラル維持 + `@Deprecated`）。内部の受け渡しは値を含む新アクセサに切り替える |
| Q4 | 型検証（FR-2.1）と LIKE エスケープ（FR-2.3）を共通コンポーネントに切り出し、リテラル系（`SQLParser`）とバインド系の両方がそれを使う 3 構成にする |
| Q5 | `Dao`（protected）と `DBAccessManager`（public）の両方に `PreparedSql` 版の実行メソッドを追加する |
| Q6 | `getExecutedQuery()` は SQL テキストのみ維持し、バインド値を返す別の public メソッドを追加する |
| Q7 | LIKE の `escape 'X'` は、値を先に検査してエスケープ文字を決め、テキストに `escape 'X'` を埋め、エスケープ済みの値をバインドする（FR-2.3 から一意に決定） |
| Q8 | FR-8.1 は既存の `org.tamacat.mock.sql` スタックを拡張して実現する（新規依存なし） |
| Q9 | FR-1.7 の機構は決めない。OQ-9 として 2.7 に持ち越す |

**この内容で設計成果物 5 点を生成してよろしいですか？**

- Looks correct — この判断内容で `components.md` / `component-methods.md` / `services.md` / `component-dependency.md` / `decisions.md` を生成する
- Request changes — 生成前に 1 つ以上の判断を修正する

[Answer]: Looks correct
**Mode:** guided | **Timestamp:** 2026-08-05T05:10:00Z

---

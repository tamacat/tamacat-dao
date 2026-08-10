# Business Rules — U4 `dialects`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U4 の責務・境界・所有コンポーネント、実装上の制約（`SQL_CALC_FOUND_ROWS` の `replaceFirst` は不変、LIMIT / rownum の値は末尾、`OracleDao` の `createSearch` / `createQuery` の非対称）、「挙動が変わる点」。BR-2 / BR-3 / BR-6 / BR-11 はここに直接対応する。
- **`unit-of-work-story-map.md`**（同上）— U4 が担う FR-5.1 / 5.2 / 5.3 / 6.1 / 6.3 / 6.4、判定する AC-9 / AC-10、Unit 内の実装順序 1〜4、および cross-cutting 要件（NFR-1〜NFR-7、CON-3 / CON-6、AC-11）。
- **`requirements.md`**（requirements-analysis, 2.3）— FR-5.1〜5.3 / FR-6.1 / 6.3 / 6.4、AC-9 / AC-10、NFR-1（ADR-003 による読み替え後）／ NFR-3 / NFR-5 / NFR-6 / NFR-7、CON-1。各規則の「由来」列が参照する。
- **`components.md`**（application-design, 2.6）— M-7 の変更範囲、および `MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition` 不変・PostgreSQL 専用クラス不追加。BR-17 / BR-19 の根拠。
- **`component-methods.md`**（同上）— M-7 の変更内容表。BR-6 / BR-12 はその 2 点を修正する（差分の全量は `business-logic-model.md` § 8）。
- **`services.md`**（同上）— R-2 JDBC セッション管理 / R-3 接続プーリング（BR-9 の根拠）、R-5 実行の観測（BR-10 の根拠）。

`domain-entities.md` が状態と不変条件（PW-n / DS-n / VF-n）、`business-logic-model.md` がアルゴリズムを扱う。**本文書は「どの経路でも守られなければならない規則」を列挙する。**

---

## 規則一覧

| # | 規則 | 由来 |
|---|---|---|
| BR-1 | ページング窓の**導出式**（`offset = start - 1`、上限 `offset + max`）は MySQL と Oracle で同一である。`start` は 1 始まりであり、汎用経路（`Dao.java:181-184`）と MySQL 現行（`MySQLDao.java:46`）の意味に揃える。**ただし式の適用は方言ごとに異なる**——**W-2 に限り** MySQL は `offset` を適用しない。W-3 は `offset` が両方言とも 0 であり、コールバックの打ち切り（`MySQLDao.java:60-61`）が独立に効くため実効窓は両方言とも `[1, max]` で一致する（`domain-entities.md` § 1「4 つの状態」） | Q3 = A、`Dao.searchList` |
| BR-2 | **MySQL が LIMIT を付ける条件を広げない。** `start > 0 && max > 0`（`MySQLDao.java:42`）のときだけ付ける。それ以外はコールバック内の `add >= max` だけで打ち切る現行の挙動を維持する | 現行維持、NFR-5 |
| BR-3 | `SQL_CALC_FOUND_ROWS` の付与は `replaceFirst("SELECT ", ...)`（`MySQLDao.java:44`）のまま変えない。**大文字・末尾スペースにしか一致しない脆さも含めて現行のまま**である | `unit-of-work.md` U4 |
| BR-4 | 方言が `PreparedSql` を合成するヘルパ `compose(PreparedSql, String)` は、**`MySQLDao` と `OracleDao` に同一の実装を private static で置く**。`Dao` に共通メソッドを追加しない | `unit-of-work.md` U4 の境界、AC-11 |
| BR-5 | `compose` は `base.hasUnboundPlaceholders()` を分岐条件にする——真なら `ofLiteral`、偽なら `of` を使う。**この条件はほぼ入力の由来（checked / unchecked）を保つが、unchecked かつプレースホルダ 0 個の入力では checked 側の枝に切り替わる**（値・個数とも 0 のため実害はない） | U1 ADR-012、`domain-entities.md` DS-3 |
| BR-6 | **ページング境界値（MySQL の LIMIT、Oracle の rownum）はバインドしない。** `int` をテキストに直接書く。窓の値は `?` を 1 つも作らない | Q2 = C |
| BR-7 | コールバック内の `if (max > 0 && add >= max) break;` を**両方言とも残す**。SQL 側の上限との二重の防御である | 現行維持 |
| BR-8 | `SELECT FOUND_ROWS()` は**値ゼロの `PreparedSql`** として実行する。`PreparedSql.of("SELECT FOUND_ROWS()", emptyList())` は `?` 0 個・値 0 個で U1 の個数検査（PS-2）を通る | Q1 = A |
| BR-9 | `FOUND_ROWS()` は**同一セッションで実行されなければならない**。同一 `Statement` である必要はない。`DBAccessManager` が `ThreadLocal` の同一 `Connection` を返し、`PreparedStatement` がそこから都度生成されることがこれを保証する | `services.md` R-2 / R-3 |
| BR-10 | `getExecutedQuery()` の記録件数が増えることを**許容する**。W-1 かつ `useHitCount` のとき 0 件 → 2 件になる。記録を失う方向の変化は起こさない | Q1 = A、FR-4.1 |
| BR-11 | Oracle の rownum ラップは `start >= 1` を**すべて 2 段ラップに統一する**。現行の `start > 1` / `start == 1` の分岐は廃す | Q3 = A |
| BR-12 | `OracleDao.searchList(Query,int,int)` を override し `searchListForOracle` に委譲する。**旧 `searchListForOracle` は public のまま、シグネチャを変えずに残す** | AC-9、NFR-3 |
| BR-13 | Oracle 経路では**コールバック内で行を読み飛ばさない**。rownum が DB 側で読み飛ばしているため、重ねると `2 × (start-1)` 行が飛ぶ | Q3 = A、`Dao.java:181-184` |
| BR-14 | `for update` の**判定**（`sql.toLowerCase().endsWith("for update")`）と**付与位置**（最外側の末尾）は現行の意図を踏襲する。ラップしない W-4 では元の位置に残る | 現行維持 |
| BR-15 | **Oracle 経路は `hitCount` を設定しない。** `FOUND_ROWS()` に相当する機構がなく、`count(*)` の追加は FR-6.1 の範囲外である | 現行維持、NFR-7 |
| BR-16 | `OracleValueConvertFilter.convertValue(null)` は **`null` を返す**。`Search.DefaultValueConvertFilter` / `MySQLSearch.MySQLValueConvertFilter` と同一の形にする | FR-6.4、AC-10 |
| BR-17 | `Condition` / `MySQLCondition` / `PostgreSQLCondition` を**変更しない**。`BindSqlBuilder` と `SQLParser` が同じ `Conditions` メソッドを使うため、述語の形は自動的に一致する | FR-5.3、`components.md` M-7 |
| BR-18 | 本 Unit が壊したテストは本 Unit が直す。FR-8.2 の対応表は U4 の該当行だけを起こし、集約は Build and Test（3.6）が行う | Q7 = B、NFR-5 |
| BR-19 | **PostgreSQL 専用の `Dao` / `Search` を追加しない。** 汎用フォールバック経路として扱う | FR-5.2、OOS-4 |
| BR-20 | `MySQLSearch` / `MySQLSearch.MySQLValueConvertFilter` を変更しない。`MySQLDao.createSearch()` / `createQuery()` によるフィルタ注入も旧経路のために不変で残す | `components.md` M-7 |
| BR-21 | `wrapRownum` は `base` の末尾に `for update` があれば**ラップの前に剥がし**（`core`）、最外側に**ちょうど 1 回だけ**付け直す。`base` をそのまま内側へ入れて外側にも付けると、インラインビューの内側と外側の両方に `for update` が現れ実行不能な SQL になる——現行 `OracleDao.java:37-48` は剥がし処理を欠いており、死んだコードだったため未発覚だった欠陥である | `OracleDao.java:36-48`、`business-logic-model.md` § 4.3 |

---

## 失敗様式

**この Unit で「静かに壊れる」形を列挙する。** 個数検査や型検査で捕まらないものだけを挙げる。

### 失敗様式 1 — ページング窓の off-by-one

`offset = start - 1` を `offset = start` と書いても、`?` の個数も値の個数も変わらず、SQL としても正当である。**症状は「1 行目が返らない」または「1 行余分に返る」**であり、テストが件数だけを見ていると通り抜ける。

**検出**: `start = 1` / `start = 2` の両方で**先頭行の内容**をアサートする。件数だけでは足りない。W-1〜W-4 の 4 状態それぞれで確認する（`business-logic-model.md` § 9-2）。

### 失敗様式 2 — `compose` の 2 実装が乖離する

BR-4 により `MySQLDao` と `OracleDao` に同じヘルパを重複させる。**片方だけを直すと、由来（checked / unchecked）の扱いが方言によって変わる。**

症状: 外部 `Query` 実装 ＋ `where(Param)` の組み合わせで、MySQL では `DaoException`、Oracle では `InvalidParameterException` が飛ぶ（あるいはその逆）。**どちらも例外で落ちるため「動く / 動かない」の差にはならず、例外型を捕まえている利用側でだけ差が出る。**

**検出**: 両方言に**同一のテスト**を当てる（unchecked な `PreparedSql` を返す `Query` スタブを 1 つ作り、両方の `searchList` に通して同じ例外型を確認する）。

### 失敗様式 3 — Oracle で行を二重に読み飛ばす

BR-13 に反してコールバック内の読み飛ばしを残すと、rownum の読み飛ばしと合わせて `2 × (start-1)` 行が飛ぶ。**例外は出ず、件数も `max` 以下のまま**であり、返る行が「ずれている」ことにしか現れない。

**検出**: 失敗様式 1 と同じく先頭行の内容をアサートする。`start = 3` 以上で顕在化する（`start = 1` では `offset = 0` のため差が出ない）。

### 失敗様式 4 — `FOUND_ROWS()` が別のクエリの値を返す

BR-9（同一セッション）が守られていても、1 本目と 2 本目の間に別の文が実行されると `FOUND_ROWS()` の対象が変わる。本設計では同一メソッド内で連続実行するため構造的に起きないが、**`DaoExecuteHandler` の実装が `handleAfterExecuteQuery` の中で SQL を発行すると割り込む**。

**検出**: 既定の `NoneDaoExecuteHandler` では起きない。利用側が独自ハンドラを差す場合の制約として `business-logic-model.md` § 9 に記録し、Javadoc に明示する。

### 失敗様式 5 — `SQL_CALC_FOUND_ROWS` が付かないまま `FOUND_ROWS()` を呼ぶ

BR-3 の `replaceFirst("SELECT ", ...)` が一致しない SQL（小文字の `select` で始まる外部 `Query` 実装の出力など）では置換が起きない。それでも `useHitCount && paged` の条件で 2 本目は実行され、**前回の `SQL_CALC_FOUND_ROWS` 付きクエリの値、または 0 が `hitCount` に入る。**

**現行と同じ挙動である**（現行も同じ `replaceFirst` を使い、同じ条件で `FOUND_ROWS()` を呼ぶ）。本取り組みの回帰ではないため是正しないが、記録する。

---

## 残存リスク

**この Unit の完了後も残る、SM-1 / セキュリティ上の穴を明示する。**

| # | 残存箇所 | 内容 | 扱い |
|---|---|---|---|
| R-1 | `MySQLDao.java` の LIMIT | `" limit " + (start-1) + "," + max` の `int` 連結が実行経路に残る | **意図的**（Q2 = C）。`int` 型を経由するため SQL 構文を注入できない。NFR-1 の「値」に当たらないという読みを本ステージの決定とする |
| R-2 | `OracleDao` の rownum 境界値 | 同上 | 同上 |
| R-3 | 旧リテラル経路の `ValueConvertFilter` | `SQLParser` 経由の `getSearchString()` / `getSelectSQL()` は `'` の二重化によるエスケープに依存し続ける | FR-7.1 が対象外とした範囲。`OracleValueConvertFilter` の null ガード（BR-16）はこの経路の**欠陥是正**であって、経路そのものを閉じるものではない |
| R-4 | 外部 `Query` 実装の `default` 経路 | `getSelectPreparedSql()` を override しない外部実装は `ofLiteral(getSelectSQL())` を返し、値がリテラルのまま方言経路に載る | ADR-004 / ADR-006 が定めた互換シムの帰結。方言固有の穴ではない。`compose`（BR-5）はこの由来を保つだけで、閉じはしない |
| R-5 | `PostgreSQL` の識別 | PostgreSQL 専用の `Dao` / `Search` がないため、PostgreSQL 固有のエスケープ規則（`E'...'` や `standard_conforming_strings`）は旧リテラル経路で考慮されない | **現行から不変**（FR-5.2 / OOS-4）。バインド経路ではドライバが担うため、新経路を使う限り問題にならない |
| **R-6** | **MySQL と Oracle のページング窓が W-2 に限り揃わない**（`domain-entities.md` § 1「4 つの状態」） | 同じ `(start, max)` に対し、W-2（`start>0 && max<=0`）では MySQL は `offset` を適用せず実効窓が `[1, ∞)` になる一方、Oracle は本設計で `offset` を適用する。**利用側アプリが方言をまたいで同じページング呼び出しを使い回すと、W-2 に限り返る行が方言によって変わる。W-3 は両方言とも `[1, max]` で一致するため対象外** | **意図的に是正しない**（BR-1）。MySQL 側を対称化すると FR-6.3 の範囲（連結方法の是正）を超えて行スキップ機構を新設することになり、NFR-5 に対するリスクを負う。Build and Test（3.6）は W-2 に限りこの非対称を前提に両方言を個別にテストする必要がある |

**R-1 / R-2 は本ステージが新たに作った穴ではない**——現行と同じ形を維持したものである。ただし「FR-6.3 が求めた是正を、要件が想定した手段（バインド）とは別の手段（型による安全化）で満たした」という判断を含むため、**Build and Test（3.6）が SM-1 を測定するときにこの読みを前提にする必要がある**（`business-logic-model.md` § 9-7・§ 9-8）。この読みの正しい典拠は `requirements.md:108`（FR-6.3 の位置づけ）であり、ADR-003 ではない。

**R-6 は R-1 / R-2 と性質が異なる**——R-1 / R-2 は現行の形を維持した結果の残存リスクである。R-6（MySQL/Oracle の W-2 非対称）は**この非対称自体は本ステージが新たに作ったものではない**——U4 以前は `OracleDao` が `searchList(Query,int,int)` を override しておらず、呼び出し側は `Dao.searchList` の継承実装（`Dao.java:181-184` の行読み飛ばしループ）を経由していた。この継承経路は W-2 でも `offset` を反映するため、返る行の集合は本ステージの前後で変わらない——変わるのは絞り込みの実施場所（アプリケーション側 → DB 側）だけである。MySQL 側の非適用も現行から不変である。**本ステージが新しく行ったのは、この既存の非対称を初めて文書として明示したことである**（`nfr-requirements/security-requirements.md` R-16、`tech-stack-decisions.md` TSD-15）。

---

## 現行から引き継ぐ既知の欠陥

**是正しないと決めたもの**を明示する。いずれも本取り組みの回帰ではない。

| # | 欠陥 | 所在 | 是正しない理由 |
|---|---|---|---|
| 1 | `replaceFirst("SELECT ", ...)` が大文字・末尾スペースにしか一致しない | `MySQLDao.java:44` | 現行と同じ。`QueryImpl.getSelectSQL()` は必ず `"SELECT "` で始まる（`QueryImpl.java:141`。宣言は `:138`）ため、リポジトリ内では顕在化しない。是正は FR の範囲外 |
| 2 | Oracle 経路が `hitCount` を設定しない | `OracleDao` 全体 | BR-15。`count(*)` の追加は FR-6.1 の範囲外であり NFR-7 とも食い違う |
| 3 | `OracleDao` が `createQuery()` を override せず、INSERT / UPDATE のリテラルに既定フィルタが使われる | `OracleDao.java:20-25` | `requirements.md` FR-5.1 の補足。バインド経路では `ValueConvertFilter` が適用されない（FR-2.2）ため当該経路では解消される。旧経路に残る非対称は FR-7.1 の対象外範囲 |
| 4 | `MySQLDao.searchList` が W-2（`start>0 && max<=0`）で LIMIT を付けず、かつ行の読み飛ばしも行わない——`offset` がどこにも効かず実効窓が `[1, ∞)` になる（W-3 はコールバックの打ち切りが独立して効くため両方言とも `[1, max]` で一致する。§ 1「4 つの状態」参照） | `MySQLDao.java:39-78` に `Dao.java:181-184` 相当の行スキップが存在しない | BR-2。条件を広げて `offset` 適用・行スキップを足すと現行と異なる SQL が生成され NFR-5 に対するリスクになる。性能目標は置かない（NFR-7）。**Oracle 側は W-2 で `offset` を適用するため、W-2 に限り方言間の非対称が生じる（R-6）** |
| 5 | `for update` の検出が `endsWith("for update")` の単純比較であり、`for update nowait` / `for update of ...` / 末尾の余分な空白には一致しない | `OracleDao.java:36`、`business-logic-model.md` § 4.3 の `wrapRownum` も同じ判定を踏襲 | **現行から不変。新設ではない。** 現行 `OracleDao.java:36` も同じ単純比較であり、`searchListForOracle` が死んだコードだったため未発覚だった。FR-6.1 で経路が生きるとこの脆さも露出しうるが、検出ロジックの強化は本 Unit の責務（FR-6.1: `?` の未バインド解消、`max` の利用）を超える。`for update nowait` 等が渡された場合、`forUpdate` が偽と判定され `for update nowait` の全体が `core` の内側に取り込まれたまま出力される——構文としては（Oracle の言語仕様上）内側のインラインビューに `for update` 節が現れる形になり、シナリオによっては構文エラーになりうる。Build and Test（3.6）に引き継ぐ |

---

## 判定できる受け入れ条件

| AC | 内容 | 本 Unit のどこが満たすか | 判定できる時点 |
|---|---|---|---|
| **AC-9** | Oracle の `searchList` — rownum ページング、未バインド `?` なし、`start` と `max` の両方を使用 | `business-logic-model.md` § 4（BR-6 / BR-11 / BR-12 / BR-13）。**「未バインド `?` なし」は `?` を作らないことで満たす** | U4 完了時 |
| **AC-10** | Oracle の `ValueConvertFilter` に null — NPE なし、他 2 実装と同じ結果 | `business-logic-model.md` § 5（BR-16、VF-1 / VF-2） | U4 完了時 |
| AC-11（横断） | 変更前にコンパイルされたサブクラスが再コンパイルなしでロードできる | **U4 は新規 public / protected メンバを 1 つも追加しない**（`business-logic-model.md` § 8）。判定は U2 ＋ U3 が揃った時点 | U3 完了時 |

**AC-9 の「未バインドの `?` が残っていない」の読み**: 受け入れ条件は `?` の**バインド**を要求しておらず、「未バインドの `?` が残っていないこと」だけを要求している。Q2 = C は `?` を生成しないことでこれを満たす。**この読みを明示的に記録する**——`component-methods.md` M-7 が「rownum の `?` をバインドする」と書いていたため、AC-9 をバインドの要求と読み違える余地がある。

---

## Unit をまたぐ要件の遵守

`unit-of-work-story-map.md`「Unit をまたぐ要件」に対する U4 の状態。

| ID | 内容 | U4 の状態 |
|---|---|---|
| NFR-1 | 実行経路に値の文字列連結が 0 件 | ⚠️ **読みを伴って充足。** R-1 / R-2（`int` のページング指定）は「値」に当たらないとする（Q2 = C の帰結 3） |
| NFR-2 | CodeQL で SQL インジェクションの指摘 0 件 | 未判定。`int` の連結が指摘される可能性がある。測定方法は OQ-7 として Build and Test（3.6） |
| NFR-3 | public API に破壊的変更なし | ✅ **新規 public / protected メンバなし、削除・シグネチャ変更なし** |
| NFR-4 | Java 8 を維持 | ✅ ラムダ（`ResultSetHandler`）のみ。post-8 の API を使わない |
| NFR-5 | 変更後の全テストがグリーン | `MySQLDaoTest` は無変更で緑になる見込み（LIMIT のテキストが変わらず、`searchList` を呼ぶテストがない）。`OracleSearchTest` に null ケースを追加する |
| NFR-6 | テスト件数が変更前を下回らない | ✅ 純増（`OracleDaoTest` の新設、`OracleSearchTest` への追加） |
| NFR-7 | 性能目標は設定しない | ⚠️ FR-6.1 により Oracle の性能特性が変わる（全行スキャン → rownum）。最適化目的ではなく欠陥是正の副次的結果である |
| CON-1 | Java 8 | ✅ |
| CON-3 | v2.0 ブランチ上でローカルのみ。`git push` しない | ✅ 本ステージは設計のみ |
| CON-6 | 新規の第三者依存を追加しない | ✅ 追加なし |

**NFR-1 の読みの典拠は `requirements.md:108`（FR-6.3 の位置づけ）である。** ADR-003（`requirements.md:384`）は非推奨 API 経由でのみ到達できるリテラル生成経路を対象外にしたものであり、LIMIT の連結は生きた実行経路にあるため ADR-003 の免除は及ばない。

**AC-9 / AC-10 の追加**（`business-rules.md` の「判定できる受け入れ条件」節と同じ内容をここに再掲しない。参照のこと）。

**R-6（残存リスク）: MySQL / Oracle のページング窓の非対称は NFR-1〜NFR-7 のいずれの対象でもない**が、Unit をまたぐ意味での「挙動の一貫性」に関わるため、Build and Test（3.6）に引き継ぐ（`business-logic-model.md` § 9）。

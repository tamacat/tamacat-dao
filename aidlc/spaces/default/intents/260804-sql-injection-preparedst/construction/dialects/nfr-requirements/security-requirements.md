# Security Requirements — U4 `dialects`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U4）— § 1（ページング窓の導出）、§ 2（`PreparedSql` の合成と由来の保持）、§ 3（MySQL の実行経路の載せ替え）、§ 4（Oracle の rownum ページング）、§ 5（`OracleValueConvertFilter` の null ガード）、§ 8（2.6 契約からの差分）、§ 9（後続ステージへの引き継ぎ）。本文書の脅威モデルはこの範囲を対象にする。
- **`business-rules.md`**（同上）— BR-1〜BR-21（規則一覧）、R-1〜R-6（残存リスク、U4 番号）、既知の欠陥 1〜5。本文書はこれらのうちセキュリティに効くものを要件として固定する。
- **`requirements.md`**（requirements-analysis 2.3）— NFR-1（ADR-003 による読み替え後）、NFR-2、NFR-3、NFR-5、NFR-6、NFR-7、FR-5.1〜5.3、FR-6.1 / 6.3 / 6.4、CON-1、CON-6、CON-8、AC-9、AC-10。
- **`technology-stack.md`**（codekb）— compile スコープ依存が `tamacat-core` と `javax.json` だけであること、CodeQL が `master` のみを対象とすること。
- **U1 `bind-foundation` / U2 `select-path` の `security-requirements.md`** — SEC-1〜SEC-17、R-1〜R-15。U4 はこれらを継承し、`MySQLDao` / `OracleDao` / `OracleSearch` の経路について同じ判定基準を適用する。番号は SEC-18、R-16 から振る。

`tech-stack-decisions.md` は本文書と対になる成果物で、Q1（JaCoCo ゲートの適用範囲）と Q2（`MIGRATION.md` への追記）を扱う。

---

## この文書の範囲

**U4 も `kind: library` である**（`unit-of-work.md` U4 の表）。したがって U1 / U2 と同じく、認証・認可・暗号化・規制フレームワーク・データ所在地はいずれも**該当なし**である（U1 `security-requirements.md`「この文書の範囲」の表をそのまま継承する）。

**U1・U2 と U4 でセキュリティ面の性質がさらに変わる。**

| | U1 `bind-foundation` | U2 `select-path` | U4 `dialects` |
|---|---|---|---|
| Unit の性質 | 新規クラス 8 個を足す | 既存 5 クラスを書き換える | **既存 3 クラスの一部だけを書き換える**（`MySQLDao.searchList` / `OracleDao.searchListForOracle` ＋新規 `searchList` override / `OracleSearch.OracleValueConvertFilter.convertValue`） |
| 主な問い | 新しく作る面をどう閉じるか | 既存経路をバインド化の対象に含めるか、書き換えの正しさをどう保証するか | **バインドできない値（ページング境界値）をどう安全にするか**と、**方言固有の既存欠陥をどう是正するか** |
| 中心的な脅威 | T（構文解釈）＋ I（値の平文露出） | T（構文解釈）＋ T の別形（述語の欠落） | **T（構文解釈）は U1/U2/U3 の機構をそのまま使うため新規の脅威なし。むしろ中心は Tampering ではなく Correctness/Availability 寄りの残存リスク（R-16）である** |

**U4 が新たに作る「値が構文として解釈される」面はない。** U4 の値関連の唯一の新規決定（ページング境界値をバインドしない、Q2 = C）は `int` 型を経由する時点で構造的に注入不能であり、U1/U2 が確立した「値は `BindValue` としてのみ運ばれる」という不変条件（SEC-1、SEC-10）の**外側**にある独立した安全化である。本文書はこの決定を要件として固定する（SEC-18）ほか、U4 固有の 2 つの懸念（rownum ラップの構文的正しさ、方言間の非対称）を扱う。

---

## 脅威モデル（STRIDE）

`threat-modelling-stride.md` の 6 カテゴリを U4 に当てる。**該当しないカテゴリはその旨を書く。**

| カテゴリ | U4 での評価 | 対応 |
|---|---|---|
| **S** Spoofing | **該当なし。** | — |
| **T** Tampering | **限定的。値の構文解釈は U1/U2 の機構が既に閉じている。** U4 が扱うのはページング境界値（`int` 型で構造的に注入不能）と `for update` の検出（構文組み立ての正しさの問題であり、値の注入経路ではない） | SEC-18、SEC-19 |
| **R** Repudiation | **該当なし。** U4 は実行記録の仕組みに触れない。U1 SEC-9 をそのまま継承する | 継承 |
| **I** Information Disclosure | **限定的。新しい保持機構は作らないが、記録の量が増える経路がある。** `MySQLDao.searchList` は現行 `dbm.createStatement()` を経由するため `getExecutedQuery()` / `getExecutedStatements()` に**何も記録しない**（`DBAccessManager.createStatement()`、`:80-86`、は記録処理を持たない）。U4 が `Dao.executeQuery(PreparedSql, ResultSetHandler)` に載せ替えると、本体クエリ 1 本（値を保持）と `FOUND_ROWS()` 1 本（値ゼロ）の**計 2 本**が新たに記録される。値を保持するのは本体クエリであり `FOUND_ROWS()` ではない | SEC-22 |
| **D** Denial of Service | **該当なし。** U4 は繰り返しやバッファリングを持ち込まない。`isNumeric` 同様の正規表現バックトラックのような面もない | — |
| **E** Elevation of Privilege | **該当なし。** 権限モデルを持たない。識別子位置（FR-1.7）は U5 の担当であり U4 は `Sort` に触れない | — |

**この脅威モデルが示す U4 の性質**: STRIDE の 6 カテゴリのうち U4 が新規の要件を必要とするのは T の一部と I の一部（記録量の増加）だけである。U4 の設計上の主要なリスクは STRIDE の枠に収まらない——**「SQL として構文的に正しいか」という correctness の問題**（`for update` の位置。**W-2 のみ**が MySQL/Oracle 間で行集合が非対称であり、W-3 は両方言とも `[1, max]` で一致する。`business-rules.md` R-6・既知の欠陥 4・BR-1 を参照）であり、これらは `business-rules.md` の残存リスク・既知の欠陥として既に記録済みである。本文書はそれをセキュリティ要件の言葉に翻訳する。

---

## セキュリティ要件

判定基準は**機械的に確認できる形**で書く。番号は U2 の SEC-10〜SEC-17 に続けて SEC-18 から振る。

### SEC-18. ページング境界値は型によって構造的に注入不能である

| 項目 | 内容 |
|---|---|
| 要件 | `MySQLDao.searchList` の LIMIT 値、`OracleDao.searchListForOracle` の rownum 境界値は `int` 型の呼び出し引数（`start` / `max`）から直接テキスト連結されること。呼び出し側から渡される `String` 値がこれらの位置に文字列連結されないこと |
| 判定基準 | `MySQLDao.searchList` / `OracleDao.searchListForOracle` のソースを検査し、LIMIT / rownum のテキスト連結の入力が `int start` / `int max`（メソッド引数）のみであることを確認する。呼び出し側が渡す `Query` / `Search` から取得した `String` 値がこれらの式に混入しないこと |
| 由来 | Q2 = C、`business-logic-model.md` § 3.2 / § 4.3、BR-6 |

**「バインドしない」ことがなぜ安全か**: SEC-1（U1）/ SEC-10（U2）が要求する「値は `BindValue` としてのみ運ばれる」という不変条件は、**呼び出し側が渡す `String` 値**を対象にしている。ページング境界値は呼び出し側が渡す `Query` / `Search` の値ではなく、`searchList(Query, int, int)` の `int` 引数そのものである。**`int` は Java の型システムによって SQL 構文を運べない**——呼び出し側が `"'; DROP TABLE users; --"` のような文字列を `int` 引数の位置に渡すことはコンパイル時に不可能である。したがってバインドしなくても SEC-1 / SEC-10 と同じ水準の安全性が型によって保証される。

**NFR-1 の読みとの関係**: `business-rules.md`「NFR-1 の読みの典拠は `requirements.md:108`」の判断を、本要件は SEC の言葉で言い換えたものである。CodeQL（NFR-2）がこの `int` 連結を指摘した場合の扱いは Build and Test（3.6）に送る（下記「Build and Test（3.6）に送る検証項目」）。

### SEC-19. `for update` の合成は最外側にちょうど 1 回だけ現れる

| 項目 | 内容 |
|---|---|
| 要件 | `OracleDao` の rownum ラップにおいて、元の SQL 末尾の `for update` 節は剥がした上でラップの最外側に 1 回だけ再構成すること。インラインビューの内側に残さないこと |
| 判定基準 | W-1 / W-2 / W-3 それぞれについて、`for update` 付きクエリを渡したとき生成される SQL に `for update` の出現がちょうど 1 回であり、かつ最外側（インラインビューの外）にあること |
| 由来 | `business-logic-model.md` § 4.3、BR-21 |

**これは注入防御ではなく構文的正しさの要件である。** `for update` の二重出現は「SQL が実行できなくなる」という可用性の問題であり、値の注入経路ではない。しかし現行 `OracleDao.java:37-48` の欠陥（死んだコードのため未発覚）が FR-6.1 によって生きた経路になる以上、**「実行できないクエリを生成しない」ことを機械的に検証できる要件として明示する**（`security-guide.md` の対象は主に注入・認可だが、可用性はセキュリティ属性の一部である——CIA の A）。

**残存する脆さ**: `for update` の検出は `endsWith("for update")` の単純比較であり、`for update nowait` 等の亜種には一致しない（`business-rules.md`「現行から引き継ぐ既知の欠陥」5）。**現行から不変の脆さであり、本要件はこれを是正することを要求しない**——SEC-19 が要求するのは「検出できた場合に二重化しない」ことであり、「あらゆる亜種を検出する」ことではない。

### SEC-20. `OracleValueConvertFilter` は null に対して例外を投げない

| 項目 | 内容 |
|---|---|
| 要件 | `OracleValueConvertFilter.convertValue(null)` は `NullPointerException` を投げず `null` を返すこと。`value == null` のときの戻り値の点で `Search.DefaultValueConvertFilter` / `MySQLSearch.MySQLValueConvertFilter` と同一にすること（`value != null` のときの変換内容は 3 実装で異なる——MySQL のみ `\` → `\\` を追加で行う。`MySQLSearch.java:15`） |
| 判定基準 | **AC-10** — 汎用実装・MySQL 実装と同じ結果が返る |
| 由来 | FR-6.4、BR-16 |

**セキュリティというより可用性の要件である。** `NullPointerException` はサービス拒否（部分的な D）に相当する——旧リテラル経路で null 値を渡す呼び出しが、この 1 箇所の欠陥だけで例外終了する。バインド経路では `ValueConvertFilter` は適用されない（FR-2.2、U1 BR-18）ため、この要件は旧経路の可用性にのみ効く。

### SEC-21. 新規の第三者 compile 依存を追加しない（U1 SEC-8 / U2 SEC-17 の継承）

| 項目 | 内容 |
|---|---|
| 要件 | U4 は compile スコープの第三者依存を 1 つも追加しない |
| 判定基準 | `mvn dependency:tree` の compile スコープが変更前と一致する |
| 由来 | CON-6、U1 SEC-8、U2 SEC-17 |

U4 が触るコードはすべて JDK 標準 API と U1 / U2 が新設した型（`PreparedSql` / `ResultSetHandler`）だけで書ける。

### SEC-22. `MySQLDao.searchList` の実行記録が増えることを受容する（既存の記録機構を継承するのみ）

| 項目 | 内容 |
|---|---|
| 要件 | `MySQLDao.searchList` を `Dao.executeQuery(PreparedSql, ResultSetHandler)` に載せ替えたことで、`getExecutedQuery()` / `getExecutedStatements()` に**呼び出しのたびに少なくとも本体クエリ 1 本**が新たに記録されること（`useHitCount && paged` のときは `FOUND_ROWS()` を含め計 2 本）。**マスクや秘匿は行わない**（U1 SEC-5 の方針を継承する） |
| 判定基準 | すべての `searchList` 呼び出しで `getExecutedStatements()` に本体クエリ 1 件（WHERE 由来のバインド値を保持）が追加されること。`useHitCount && paged` のときはさらに `FOUND_ROWS()`（値ゼロ）が追加され計 2 件になること |
| 由来 | 本ステージで新規（STRIDE-I）、`business-logic-model.md` § 3.5、BR-10 |

**なぜ新しい保持機構ではないか**: `ExecutedStatement` の型・保持方法・`ThreadLocal` のスコープはいずれも U1 が定義済みであり、U4 は`Dao.executeQuery`（U2 が追加）を呼ぶだけである。**しかし U1 の SEC-5 / R-4 が受容の根拠とした「現行の `getExecutedQuery()` が既に同じデータをリテラル埋め込みで保持しているため露出面は増えない」という論法は、この経路にはそのまま適用できない**——`MySQLDao.searchList` の現行実装（`dbm.createStatement()`）は `getExecutedQuery()` を一切記録していない（`DBAccessManager.createStatement()`、`:80-86`、に記録処理はない）ため、U4 はこの特定の経路について**記録件数を 0 から増やす**。

**それでも受容できる理由**: (1) 記録される値は U1 が既に「利用側プロセスのメモリ内に平文で存在することを受容する」と決めた R-4 と同じ性質・同じ `ThreadLocal` スコープの記録であり、**保持機構そのものは新設していない**。(2) SELECT の実行という操作自体は、`MySQLDao.searchList` が現行でも `DaoEvent`（`Dao.createDaoEvent`、`getExecuteHandler()`）を発火させており、記録されていなかっただけで既に観測可能な操作だった。(3) FR-4.1 の意図（実行記録にバインド値を含める）にこの経路も含めることは、むしろ要件の充足範囲が広がる方向の変化である。

---

## 残存リスク

**受容するもの**と**後段に送るもの**を分けて記録する。番号は U2 の R-8〜R-15 に続けて R-16 から振る。

| ID | 残存リスク | 由来 | 扱い |
|---|---|---|---|
| **R-16** | MySQL と Oracle のページング窓が W-2（`start>0 && max<=0`）に限り揃わない。同じ `(start, max)` に対し MySQL は `offset` を無視した `[1, ∞)`、Oracle は `(offset, ∞)` を返す | `business-rules.md` R-6、`domain-entities.md` § 1 | **受容。** 対称化は FR-6.3 の範囲を超えて MySQL 側に行スキップ機構を新設することになり NFR-5 に対するリスクを負う。**セキュリティ上の露出ではない**——非対称は常に「MySQL が Oracle より広い範囲（`offset` 分だけ多くの行）を返す」方向にのみ振れ、逆方向（Oracle の絞り込みより MySQL が狭く返す）は生じない。認可境界が述語で表現されている利用側であっても、W-2 は「本来返らないはずの行が Oracle 経由でだけ漏れる」形にはならない——漏れうるとすれば MySQL 側が常に広く返す既存の（U4 が変えていない）挙動である。ただし呼び出し側が方言をまたいでページング呼び出しを使い回すコードを書いている場合、挙動の違いに気づかないリスクはある。**この非対称自体は U4 が新たに作ったものではない**——W-2 で Oracle 側が `offset` を反映するのは、U4 以前は `Dao.searchList` の継承実装（アプリケーション側での行読み飛ばし）を経由していたためであり、返る行の集合は U4 の前後で変わらない（絞り込みの場所が DB 側に移っただけである）。U4 が変えたのは Oracle の実装機構であって、MySQL/Oracle 間の非対称の有無ではない |
| **R-17** | `for update` の検出（`endsWith("for update")`）が `for update nowait` / `for update of ...` に一致しない場合、`for update` がラップの内側に取り残され構文エラーになりうる | `business-rules.md` 既知の欠陥 5、`OracleDao.java:36` | **受容（現行から不変）。** SEC-19 の対象外。Build and Test（3.6）に引き継ぐ |
| **R-18** | `MySQLDao.searchList` の `SQL_CALC_FOUND_ROWS` の付与（`replaceFirst("SELECT ", ...)`）が大文字・末尾スペースの厳密一致であり、外部 `Query` 実装が異なる大文字小文字で組み立てた SQL には一致しない | `business-rules.md` 既知の欠陥 1、`MySQLDao.java:44` | **受容（現行から不変）。** セキュリティ上の露出ではなく `FOUND_ROWS()` が誤った値（0 または直前のクエリの値）を返すだけである |

**U1 の R-1〜R-7、U2 の R-8〜R-15 は U4 でも有効である。** `MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition` は不変であり（BR-17、BR-20）、`Search` / `QueryImpl` / `Dao` の汎用経路にも触れない。**ただし R-4（値の平文保持）と R-5（実行記録の非有界成長。`release()` では削除されず `shutdown()` まで積み上がる）は、SEC-22 により `MySQLDao.searchList` 経路にも適用範囲が拡大する**——これまで 0 件だった記録がこの経路でも積み上がるようになるため、「変更を加えない」のではなく「同じ受容判断を新しい経路に拡張する」が正確な表現である。

---

## 3.1 の規則に対する修正

**なし。** U4 のレビュー iteration 2（`business-logic-model.md` の `## Review` 節、オーケストレータ適用記録）で 3.1 成果物自体を訂正済みであり、NFR Requirements の段階で新たに古くなる 3.1 の記述はない。

---

## 要件と上流の対応

| 要件 | 満たす上流要件 | 判定する AC | 検証手段 |
|---|---|---|---|
| SEC-18 | NFR-1（読み替え後）、**FR-6.3**（Should。**文面〔`int` の直接連結の解消〕は満たさない。手段を変えて意図を満たす**——`business-logic-model.md` § 8 D-1 / § 9-8）、Q2 = C | AC-9 の一部（未バインド `?` なし） | ソース検査 |
| SEC-19 | 本ステージで新規（可用性） | — | W-1〜W-3 の `for update` 亜種テスト。Build and Test（3.6） |
| SEC-20 | FR-6.4 | **AC-10** | 単体テスト（U4 で判定可能） |
| SEC-21 | CON-6 | — | `mvn dependency:tree` の差分 |
| SEC-22 | FR-4.1（適用範囲の拡大） | — | 実行記録アサート（`useHitCount && paged` の 2 件、非 paged 時の 1 件） |

**U4 が単独で判定できるのは AC-10 のみ**（`unit-of-work-story-map.md`「AC → Unit のマッピング」）。AC-9 は W-1〜W-4 の 4 状態それぞれの生成 SQL 確認を要し、SEC-19 の亜種テストとあわせて Build and Test（3.6）が実施する。

---

## Build and Test（3.6）に送る検証項目

U1 が送った 6 項目、U2 が送った 7 項目に加えて、U4 から次を送る。

| # | 項目 | 由来 |
|---|---|---|
| 14 | **SEC-19 の `for update` 亜種テスト**。`for update nowait` / `for update of t.c` を渡したときの挙動を確認する。現行から不変の脆さであり是正は要求しないが、実 DB でどう失敗するか（構文エラーか、無視されるか）を記録する | R-17、`business-rules.md` 既知の欠陥 5 |
| 15 | **AC-9 の 4 状態確認**。W-1〜W-4 それぞれで生成 SQL に未バインドの `?` が 0 個であること、`for update` がちょうど 1 回であることを確認する | AC-9、SEC-18、SEC-19 |
| 16 | **R-16 の非対称の記録**。MySQL / Oracle の W-2 で異なる行集合が返ることを、利用者向け文書（`MIGRATION.md`、下記 Q2）に記載した内容と実挙動が一致することを確認する | R-16 |
| 17 | **`OracleDaoTest` の新設**。現在テストが 1 本もない `OracleDao` に対する最初のテストとなる。NFR-6 に対しては純増 | `business-logic-model.md` § 9-2 |
| 18 | **NFR-2（CodeQL）が LIMIT / rownum の `int` 連結を指摘した場合の扱い**。U1 の項目 2（`SQLParser` の残存リテラル経路、ADR-003 の非推奨経路）とは**別の経路**であり、U4 の連結は生きた実行経路にある。指摘された場合に「実行に到達しないことを示すか、指摘を抑制するか」の判断が必要（SEC-18 の由来、`requirements.md:108`） | SEC-18、CON-8、U1 の Build and Test 項目 2 とは別扱い |

---

## Review

NOT-READY

**Reviewer:** aidlc-architecture-reviewer-agent（3.2 NFR Requirements / U4 `dialects`、iteration 2 — final）

参照した実ソース: `MySQLDao.java`（全文）、`OracleDao.java`（全文）、`Dao.java`（全文。特に `:177-197` `searchList(Query,int,int)`、`:249-255` `executeQuery(String)`）、`DBAccessManager.java`（全文。`:80-86` `createStatement()`、`:88-95` `preparedStatement(String)`、`:97-104` `executeQuery(String)`、`:167-179` `release()`、`:206-215` `shutdown()`）、`MySQLSearch.java`、`OracleSearch.java`、`Search.java`、`pom.xml`。参照した契約: 本ステージの Q&A、`business-logic-model.md` / `business-rules.md`（U4 3.1）、`requirements.md`（FR-6.1/6.3/6.4、NFR-1/2、AC-9/AC-10、CON-6/CON-8、`:108`）、`technology-stack.md`、および U1 / U2 の `security-requirements.md` / `tech-stack-decisions.md`（番号・継承主張の照合に限る）。

### iteration 1 の 3 件の blocking の検証結果

**B-1（STRIDE-I）— 是正済み。実ソースで裏を取った。**

- `MySQLDao.java:50` は `dbm.createStatement()`、`:54` / `:65` は `Statement.executeQuery(String)`。`DBAccessManager.createStatement()`（`:80-86`）は `getConnection().createStatement()` を返すだけで `getExecutedQuery().add(...)` を**行わない**。記録を行うのは `preparedStatement(String)`（`:90`）と `executeQuery(String)`（`:99`）／`executeUpdate(String)`（`:108`）だけである。したがって現行のこの経路の記録件数は **0 件**で確定する。
- STRIDE-I 行は「該当なし」から「限定的」に改まり、`:80-86` の引用も正確である。SEC-22 が新設され、U1 SEC-5 / R-4 の受容根拠（「現行の `getExecutedQuery()` が同じデータを既に保持しているため露出面は増えない」）が**この経路には適用できない**ことを明示している。iteration 1 が求めた核心は満たされている（残る不足は N-1 / N-2 / N-3 として非 blocking で記録する）。

**B-2 — 是正済み。**

- (a) 検証項目 **18**（CodeQL / NFR-2 の扱い）が追加され、U1 の項目 2 と**別扱い**であることを明記している。U1 `security-requirements.md` の項目 2 が「残存するリテラル連結経路（`SQLParser` のリテラル系）」＝ ADR-003 Negative の非推奨経路であることを確認し、重複でないことを確かめた。CON-8（`requirements.md:169`）も本文で使われるようになった。行 14〜17 との内容重複もない。
- (b) 「要件と上流の対応」の SEC-18 行が **FR-6.3** を「文面〔`int` の直接連結の解消〕は満たさない。手段を変えて意図を満たす」という但し書き付きで引くようになった。`business-logic-model.md` § 8 D-1 / § 9-8 の申し送りと一致する。

**B-3 — 是正済み。** 44 行目は `for update` の位置に限定され、「**W-2 のみ**が非対称、W-3 は両方言とも `[1, max]`」と `business-rules.md` BR-1 / R-6 / 既知の欠陥 4 の訂正後の位置に揃った。

**iteration 1 の non-blocking のうち是正を確認したもの**: N-1（`tech-stack-decisions.md:11` の役割記述）、N-3（SEC-19 由来からレビュー所見 ID を除去）、N-4（SEC-20 の「同一」を null 時に限定し、MySQL のみ `\` → `\\` を行うことを明記。`MySQLSearch.java:15` と一致）、N-2（R-16 の自己矛盾する一文の削除）。**未是正**: N-5（二重採番）、N-6 の 1 と 2（下記 B-2）、N-7（TSD-13 の全数確認の漏れ）、N-8（`offset+max` のオーバーフロー）。

---

### Blocking

**B-1 — 「3.1 の規則に対する修正: なし」（126 行目）が成立しない。本ステージが新たに主張した「W-2 の非対称は U4 が作ったものではない」は正しいが、`business-rules.md` は正反対の帰属を書いており、そのまま残っている**

R-16（116 行目）と `tech-stack-decisions.md` TSD-15（`:136-142`）が新たに主張する内容——「この非対称自体は U4 が新たに作ったものではない。U4 以前は `Dao.searchList` の継承実装（アプリケーション側での行読み飛ばし）を経由していたため、返る行の集合は U4 の前後で変わらない」——は**実ソースで裏が取れる**。

- `OracleDao.java` 全文に `searchList(Query,int,int)` の override は**存在しない**（`createSearch()` と `searchListForOracle(Query,int,int)` の 2 メソッドのみ）。したがって `oracleDao.searchList(q, start, max)` は現在 `Dao.java:177` の継承実装に落ちる。
- `Dao.java:181-184` は `if (start > 0) { for (int i = 1; i < start; i++) rs.next(); }` で `start-1` 行を読み飛ばし、`:186-192` で `max > 0` のとき `max` 件で打ち切る。W-2（`start>0 && max<=0`）では読み飛ばしのみが効き、実効窓は `(offset, ∞)` になる——U4 後の `rownum_ > {offset}`（`business-logic-model.md` § 4.2）と**同じ行集合**である。
- MySQL 側の W-2 は `MySQLDao.java:42`（LIMIT を付けない）と `:60-61`（`max<=0` なので break しない）により `[1, ∞)`。U4 は BR-2 によりこれを変えない。

**したがって非対称は U4 以前から実在する。** ところが `business-rules.md:97` は「**R-6 は R-1 / R-2 と性質が異なる——R-6 は Oracle 側の欠陥是正（FR-6.1）によって新たに顕在化する方言間の非対称である**」と書いており、本ステージの新しい主張と真っ向から食い違う。両立しない——行集合が前後で変わらないなら「FR-6.1 によって新たに顕在化する」ことはない。

なぜ体裁の問題で済まないか: 本文書 124-126 行の「3.1 の規則に対する修正」節は、まさにこの種の齟齬を後段に伝えるために置かれている枠であり、そこに「**なし**」と書いてある。結果、3.6 の読者は `business-rules.md` R-6（U4 が顕在化させた新しい非対称）と本文書 R-16 ＋ `MIGRATION.md` 追記（従来から不変）という**相反する 2 つの帰属**を、どちらが正かの手がかりなしに受け取る。検証項目 16 は「`MIGRATION.md` の記載と実挙動の一致」を 3.6 に求めているため、3.6 は必ずこの矛盾に突き当たる。

是正は「3.1 の規則に対する修正」に 1 行を起こし、`business-rules.md:97`（および必要なら R-6 の扱い列）が `OracleDao` に override がない現行前提を取り違えていること、正しくは「非対称は現行から存在し、U4 が変えるのは絞り込みの場所（アプリケーション側 → DB 側）と性能特性だけである」ことを記録すれば足りる。設計判断自体（受容する）は変わらない。

**B-2 — TSD-15 の `MIGRATION.md` 追記文が、同じコードブロック内で自己矛盾している。1 つ目の箇条書きは実ソースとも食い違う（利用者向けに配布されるテキストである）**

`tech-stack-decisions.md:130-142` の追記文:

| 箇条書き | 記述 | 実ソース |
|---|---|---|
| 1 つ目（`:130-133`） | 「`OracleDao.searchList(Query, int, int)` はこれまで…**動作せず**（…）、`Dao.searchList` の全行スキャン（メモリ上での読み飛ばし）に**フォールバックしていました**」 | `OracleDao` は `searchList` を override していないため、`OracleDao.searchList` は `Dao.searchList` **そのもの**である。「動作せず、`Dao.searchList` にフォールバックした」は自己言及であり、動作しなかったのは `searchListForOracle` の方である。ページング自体は `Dao.java:181-192` で**動作していた** |
| 1 つ目（`:135`） | 「**全行フェッチしてからの読み飛ばし** → DB 側での絞り込み」 | `Dao.java:186-191` は `if (max > 0 && add >= max) break;` で打ち切る。W-1 / W-3 では全行をフェッチしない。正確には「クライアント側での読み飛ばしと打ち切り → DB 側での絞り込み」 |
| 2 つ目（`:136-142`、今回追加） | 「従来も汎用経路（`Dao.searchList` の継承実装）が**オフセット分の行をアプリケーション側で読み飛ばしており**、返る行の集合は今回の変更の前後で変わりません」 | 正しい（B-1 参照） |

**1 つ目と 2 つ目が同じ経路について正反対のことを述べている**——一方は「動作せず／全行フェッチ」、他方は「オフセットを適用しており行集合は不変」。iteration 1 の N-6 は 3 点を挙げたが、今回の是正は 3 点目（非対称の新旧帰属）だけを直し、1 点目・2 点目を残したため、以前は単に不正確だったものが**内部矛盾**に変わった。これは 3.6 がそのままリポジトリの `MIGRATION.md` に写す利用者向け文面であり、読者は「自分のコードの Oracle ページングは今まで壊れていたのか、それとも結果は同じなのか」を判断できない。判定基準（`:122`）も 1 つ目の箇条書きしか検査対象にしていないため、この矛盾は判定基準では捕まらない。

是正は 1 つ目の箇条書きを「これまでは汎用経路（`Dao.searchList`）がアプリケーション側で行を読み飛ばし・打ち切っていた。これからは同じ絞り込みを DB 側の rownum が行う。返る行は同じだが性能特性が変わる」に書き換えれば、2 つ目とも実ソースとも整合する。

---

### Non-blocking

**N-1 — SEC-22 と STRIDE-I 行が「計 2 本」に限定されているが、記録が増えるのは W-1 かつ `useHitCount` のときだけではない。** `Dao.executeQuery(PreparedSql, ResultSetHandler)` への載せ替えは 4 状態すべてに効くため、W-2 / W-3 / W-4 および `useHitCount == false` の W-1 でも**本体クエリ 1 本**が 0 件から 1 件に増える。しかも**値を保持するのはこの 1 本目**である（`FOUND_ROWS()` は BR-8 のとおり値ゼロ）。SEC-22 の判定基準は `useHitCount && paged` の 2 件だけを機械的に確認する形になっており、露出の主要部分（全状態で 1 件）が要件の文面からも判定基準からも抜けている。「本体クエリは全状態で 1 件、`FOUND_ROWS()` は `useHitCount && paged` のときのみ追加で 1 件」と書けば実態と一致する。BR-10 の「W-1 かつ `useHitCount` のとき 0 件 → 2 件」も同じ狭さを持つため、あわせて 3.1 の規則に対する修正に挙げる余地がある。

**N-2 — SEC-22 は U1 R-4 の適用拡大は書いたが R-5（実行記録の非有界成長）に触れておらず、120 行目と矛盾する。** `DBAccessManager` の `executedQuery` は `ThreadLocal` で保持され、`release()`（`:167-179`）では削除されず `shutdown()`（`:206-215`）でのみ `remove()` される。U1 R-5 が受容したこの成長特性は、これまで 1 件も記録していなかった `MySQLDao.searchList` 経路にも今回及ぶ。にもかかわらず 120 行目は「U1 の R-1〜R-7…は U4 でも有効である。**U4 はこれらのいずれにも変更を加えない**」と書く。SEC-22 が明示的に「この経路について記録件数を 0 から増やす」と述べている以上、少なくとも R-4 / R-5 については「適用範囲が拡大する」であって「変更を加えない」ではない。120 行目に例外を 1 つ付ければ足りる。

**N-3 — SEC-22 が「要件と上流の対応」表（132-137 行）にも 3.6 の検証項目表（147-153 行）にも現れない。** SEC-18 / 19 / 20 / 21 は表にあるが SEC-22 だけがない。判定基準（`getExecutedStatements()` に 2 件追加されること、1 件目が WHERE 由来の値を保持すること）は mock（`getLastPreparedStatement()` / `getExecutedStatements()`）で検査できる性質だが、**誰がいつ検査するかがどこにも書かれていない**。要件を足したときに対応表と申し送りを更新していない、という単純な取りこぼしである。

**N-4 — 本文書の中で「Q2」が 2 つの異なる設問を指している。** 11 行目の「Q2（`MIGRATION.md` への追記）」は**本ステージの** Q&A の Q2（回答 A）だが、27 行目・58 行目・134 行目の「Q2 = C」は **3.1 functional-design の** Q&A の Q2（ページング境界値をバインドしない）である。同一文書内で修飾なしに同じ記号が別の設問・別の回答を指しており、3.6 の読者が「Q2 = A なのか C なのか」で詰まる。3.1 側を「3.1 Q2 = C」と修飾すれば解消する。

**N-5 — 3.6 への申し送り番号の二重採番が未解消で、範囲が広がった。** 本文書は 14〜**18**、`tech-stack-decisions.md` は 13〜15 を振るため、U4 の中で **#14 / #15 が別内容で 2 回定義される**（本文書 14 = `for update` 亜種 / 15 = AC-9 の 4 状態、TSD 14 = `MIGRATION.md` 整合確認 / 15 = R-16 の影響確認）。TSD の未解決事項 13 は U2 `security-requirements.md` の項目 13 とも衝突する。しかも TSD 13 の由来列は「`security-requirements.md` 検証項目 14 / 15」と書いており、著者自身が 2 系列を前提にしている。系列名（`SEC-BT-n` / `TSD-BT-n`）を付けるか一本化するかを 3.6 への申し送りに含めるのが安全である（iteration 1 N-5 の再掲）。

**N-6 — STRIDE の R 行「U4 は実行記録の仕組みに触れない」が I 行・SEC-22 と噛み合わない。** I 行は「記録の量が増える経路がある」、SEC-22 は「0 件から増やす」と書く。R（Repudiation）としての結論（U1 SEC-9 を継承、監査ログではない）は変わらないが、R 行の理由づけは「記録の**仕組み**は変えないが、記録される経路は増える（I 行・SEC-22 参照）」と書くのが正確である。

**N-7 — SEC-18 の「型による安全化」が保証する範囲が構文注入に限られることの明示が未追加（iteration 1 N-8 の再掲）。** 連結されるのは `start - 1` と `offset + max`（`business-logic-model.md` § 1、§ 4.3）という導出値であり、`offset + max` の算術オーバーフローは `rownum_ <= -N` という構文的には妥当で意味的には誤った SQL を生む。注入ではなく correctness / 可用性の問題であり、SEC-19 が `for update` について採ったのと同じ性質のものである。要件化するかは 3.6 の判断でよいが、SEC-18 が「型が安全性を保証する」と結ぶ以上、保証範囲が構文注入のみであることを 1 文添えると誤読を防げる。

**N-8 — TSD-13 の「U4 が使う言語機能・API の全数確認」に漏れが残る（iteration 1 N-7 の再掲）。** § 3.2 の `String.replaceFirst`（`MySQLDao.java:44` から不変、Java 1.4）、§ 5 の `String.replace(CharSequence,CharSequence)`（Java 5、`OracleSearch` の null ガード実装が使う）、`ArrayList` / `Collection` の生成が表にない。いずれも Java 8 以下であり TSD-13 の判定（Java 8 維持）は揺るがないが、「漏れなく列挙する」という宣言は満たされていない。`pom.xml:17-18` / `:101-102` が `1.8` であることは実ファイルで再確認した。

---

### 反証を試みたが妥当だった主張（記録）

- **SEC-22 の受容判断そのもの**: 反証を試みたが成立する。(1) 保持機構（`ExecutedStatement` の型、`ThreadLocal` のスコープ）は U1 が定義済みで U4 は新設していない——`DBAccessManager.java:35` の `ThreadLocal<List<String>> executedQuery` は現行から存在する。(2) 「SELECT の実行自体は現行でも `DaoEvent` として観測可能だった」も `MySQLDao.java:48-51`（`createDaoEvent(sql)` ＋ `handleBefore/AfterExecuteQuery`）で裏が取れる。しかも現行の `DaoEvent` が運ぶ SQL は**リテラル埋め込み済み**（`query.getSelectSQL()`）であり、値の平文が既にプロセス内を流れている点で U1 R-4 と同性質である。(3) FR-4.1 の充足範囲が広がる方向の変化であることも正しい。受容の結論は健全であり、B-1 / N-1 / N-2 はいずれも記述の範囲と帰属の問題であって判断の誤りではない。
- **R-16 の「セキュリティ上の露出ではない」**: W-2 で MySQL が余分に返す `offset` 行はいずれも呼び出し側の WHERE 述語を満たす行であり、認可が述語で表現されている利用側でも境界は破られない。非対称が常に MySQL 側が広い方向にのみ振れることも `MySQLDao.java:42, :60-61` と `Dao.java:181-184` の比較で成立する。
- **SEC-19 / R-17**: `OracleDao.java:36`（`endsWith("for update")` の単純比較）、`:37-48`（`q.append(sql)` の後に `q.append(" for update")` で二重化）を再確認。「検出できた場合に二重化しない」ことだけを要求し、亜種の検出強化は求めない範囲設定は `business-rules.md` 既知の欠陥 5 と整合する。
- **SEC-20**: `Search.java`（`DefaultValueConvertFilter` の null ガード）、`MySQLSearch.java:11-20`（`:15` で `'` の二重化に加えて `\` → `\\`）、`OracleSearch.java`（ガードなし）を再確認。今回の文面「`value == null` のときの戻り値の点で同一にする」は 3 実装の実態と正確に一致する。
- **SEC-21 / TSD-12**: U4 が必要とする API は JDK 標準と U1 / U2 の型で尽きる。`mvn dependency:tree` 差分という判定基準は U1 SEC-8 / U2 SEC-17 と同一で機械的に検査できる。
- **検証項目 18 の独立性**: U1 `security-requirements.md` の項目 2 は ADR-003 Negative（非推奨 API 経由のリテラル生成経路）を対象としており、U4 の `" limit " + (start-1) + "," + max` は `business-rules.md` R-1 / `business-logic-model.md` § 9-7 のとおり生きた実行経路にある。別扱いとする主張は正しく、`requirements.md:108`（FR-6.3 の位置づけ「実行時のユーザー入力ではなく呼び出し側が渡すページング指定」）という典拠も実ファイルで確認した。
- **番号の衝突**: SEC-18〜22 / R-16〜18 / TSD-11〜15 に U1（SEC-1〜9、R-1〜7、TSD-1〜5）／U2（SEC-10〜17、R-8〜15、TSD-6〜10）との重複・欠番はない。「U1 が送った 6 項目、U2 が送った 7 項目」も U2 `security-requirements.md` の項目 7〜13 と一致する。
- **TSD-11（JaCoCo を拡張しない）／ TSD-15（追記する）と本ステージ Q&A の整合**: Q1 = A、Q2 = A の回答と決定・判定基準が一致する。却下形も U2 TSD-6 / TSD-9 の論法の継承として筋が通る。

---

**適用記録（オーケストレータ、2026-08-09。レビュアーの iteration 上限到達後）**

`reviewer_max_iterations: 2` に達したため、iteration 2 の指摘（blocking 2 件、non-blocking 8 件のうち対応可能なもの）は**レビュアーの再検証を受けずに**適用した。

| 指摘 | 適用した修正 | 検証根拠 |
|---|---|---|
| **blocking B-1** — `business-rules.md:97` が R-6 を「Oracle 側の欠陥是正によって**新たに顕在化する**」と書き、本ステージの訂正（R-16・TSD-15）と矛盾していた | `business-rules.md` の当該文を、`OracleDao` が従来 `searchList` を override せず `Dao.searchList` の継承経路（`Dao.java:181-184`）を経由していたため W-2 の返り値は前後で変わらないこと、本ステージが新しく行ったのは**文書化**であることを明記する形に書き換えた | `OracleDao.java` に `searchList(Query,int,int)` の override がないこと、`Dao.java:181-184` の行スキップループを確認 |
| **blocking B-2** — `tech-stack-decisions.md` の `MIGRATION.md` 追記案の bullet 1 が自己矛盾していた（「`Dao.searchList` にフォールバックしていた」という表現が `OracleDao.searchList` 自体が `Dao.searchList` であることと矛盾し、「全行フェッチしてからの読み飛ばし」が `Dao.java:190` の `max` による早期打ち切りと矛盾） | bullet 1 を、override が存在せず継承がそのまま呼ばれていたこと、`Dao.searchList` は DB 側では絞り込まず `rs.next()` の読み飛ばしと `max` による早期打ち切りで結果を作っていたことを正確に述べる形に書き換えた | `Dao.java:177-197`（`searchList` 本体、`:181-184` の読み飛ばし、`:190-191` の早期打ち切り）を確認 |
| non-blocking N-2 — R-4/R-5 の適用拡大が「U4 はこれらのいずれにも変更を加えない」という文と矛盾 | 該当文に例外（R-4/R-5 は SEC-22 により適用範囲が拡大する）を明記 | — |
| non-blocking N-3 — SEC-22 が要件対応表・3.6 送付リストに欠けている | 要件対応表に SEC-22 の行を追加 | — |
| non-blocking — SEC-22 の判定基準が `useHitCount && paged` の 2 件ケースのみを扱い、非 paged 時の 1 件記録に触れていなかった | 要件・判定基準を「呼び出しのたびに少なくとも本体クエリ 1 本」に一般化した | `MySQLDao.java` の `searchList` が `paged` に関わらず 1 本目のクエリを常に実行することを確認 |

**未解消の指摘**: N-4（"Q2" の用語重複、本ステージの Q2=A と 3.1 の Q2=C）、N-5（3.6 送付リストの #14/#15 番号のもう一段の重複）、N-6（STRIDE-R の文言）、N-7（SEC-18 の範囲）、N-8（TSD-13「全数」の網羅性）は軽微な表現上の指摘であり、判定基準や設計判断には影響しないため今回は見送った。次にこの文書を触る機会があれば解消するとよい。

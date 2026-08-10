# Unit of Work — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

- **`components.md`**（application-design, 2.6）— 新規 C-1〜C-8 と変更 M-1〜M-8。本文書の各 Unit は、この一覧の項目を過不足なく分配したものである。
- **`component-methods.md`**（同上）— 各コンポーネントの公開メソッドシグネチャ。Unit 間の統合点はここで確定済みのシグネチャそのものであり、本ステージが新たに契約を作ることはない。
- **`services.md`**（同上）— 「tamacat-dao にサービスは存在しない」という記録と、「段階的な移行の可能性」表。本分解の切り方（実行経路別）はこの表の切り替え単位に対応する。
- **`component-dependency.md`**（同上）— 依存マトリクスと順序の不変条件、ブラストレーダス。Unit 境界がこの依存方向に逆行しないことを確認した。
- **`decisions.md`**（同上）— ADR-001〜ADR-012。とくに ADR-010 が本ステージに持ち越した OQ-9 を U5 として解決し、ADR-002 / ADR-003 が本ステージのゲートまでに求める `requirements.md` 更新を実施した。
- **`requirements.md`**（requirements-analysis, 2.3）— FR-1〜FR-8、NFR-1〜NFR-7、CON-1〜CON-8、AC-1〜AC-11。Unit ごとの担当 FR は「FR と Unit の対応」節に示す。
- **`stories.md`**（user-stories, 2.4）— 本スコープで **SKIP** のため存在しない。ユーザーストーリーの代わりに `requirements.md` の FR と AC を割り当ての単位として用いる（詳細は `unit-of-work-story-map.md`）。

設問と回答の出典は `units-generation-questions.md` の Q1〜Q7 および FQ-1 を指す。

---

## 分解の方針

Q1 = A により、**実行経路**を軸に切る。共通の土台を 1 Unit にまとめ、その上に経路ごとの Unit を積む。

この軸を選んだ理由は `services.md`「段階的な移行の可能性」表にある。本設計は新経路を旧経路と**並置する**構造であるため、経路単位で独立に切り替えられる。レイヤ別（Q1 = B）に切ると、単独の Unit では組み立てから実行まで通らず、動く形が出るのが遅くなる。FR 群別（Q1 = C）に切ると FR-1 が巨大化し、FR-2 と同じクラス（`BindSqlBuilder`）を 2 つの Unit が同時に触ることになる。

粒度は粗く（Q2 = A、FQ-1 により 3〜5 と読み替え）、Unit は 5 個とする。内訳は Must の 4 個と Should の 1 個である。

---

## Unit 定義

### U1. `bind-foundation` — バインドできる土台

| 項目 | 内容 |
|---|---|
| kind | `library` |
| 複雑度 | **L** |
| 責務 | 「SQL テキスト + 値の並び」を対で運ぶ型と、それを検証・生成・JDBC に適用する機構の一式を新設する。あわせて、その正しさをテストから確認できる状態を作る |
| 所有するコンポーネント | C-1 `Param`、C-2 `PreparedSql`、C-3 `BindValue`、C-4 `ValueRules`、C-5 `BindSqlBuilder`、C-6 `PreparedStatementBinder`、C-7 `ExecutedStatement`、C-8 mock スタック拡張、M-5 `DBAccessManager`（`PreparedSql` 版の実行メソッドと `getExecutedStatements()`）、M-6 `SQLParser`（`ValueRules` への委譲化） |
| 境界 | `Query` / `Search` / `Dao` の公開 API には触れない。SQL 文の組み立て（節の連結）を行わない。断片（`Param`）と完成文（`PreparedSql`）の**型と規則**を提供するところまで |
| 依存 | なし |
| 担当 FR | FR-2.1〜FR-2.4、FR-4.1、FR-4.2、FR-8.1 |

**mock 拡張を含める理由（Q3 = B）**: 現行 `MockPreparedStatement` の `setXxx` はすべて空実装で、`MockConnection.prepareStatement(String)` は SQL 引数を破棄する。この状態では、どの Unit もバインド位置と値をテストで確認できない（`decisions.md` ADR-009）。`PreparedStatementBinder`（C-6）は「値を 1 始まりの位置に適用する」ことだけを責務とするコンポーネントであり、その正しさを確認する唯一の手段が mock の記録機能である。同じ Unit に置くことで、C-6 が「テストできないまま完了した」状態を作れなくする。

**`SQLParser` の委譲化を含める理由**: ADR-005 により `ValueRules` は `SQLParser`（旧・リテラル系）と `BindSqlBuilder`（新・バインド系）が共有する。委譲化を U1 に含めると、既存の `SQLParserTest`（リポジトリで最も密なテスト）が `ValueRules` 切り出し後も挙動が 1 文字も変わっていないことを U1 の完了時点で検証する。これを後回しにすると、`ValueRules` が「バインド系からしか呼ばれていない」状態で確定してしまい、リテラル系との乖離が後から生じうる。

**実装上の制約**:
- `PreparedSql.ofLiteral(...)` は `?` 個数検査を行わない。代わりに `hasUnboundPlaceholders()` を持ち、`DBAccessManager` の `PreparedSql` 版実行メソッドが実行前に検査して真なら `DaoException` を投げる（ADR-012）。引用符の外にある `?` のみを数える走査規則の詳細は Functional Design（3.1）で確定する
- `getExecutedQuery()` は `List<String>` を返し続ける（ADR-008）。新旧 2 系統に記録する
- NUMERIC / FLOAT にも `setString` を使う（ADR-007）。この選択は実 DB エンジンに対する検証を要し、Build and Test（3.6）の FR-8.3 が最初の機会になる
- CON-1（Java 8）、CON-6（新規第三者依存を追加しない）

---

### U2. `select-path` — 読み取り経路のバインド化

| 項目 | 内容 |
|---|---|
| kind | `library` |
| 複雑度 | **L** |
| 責務 | WHERE 述語の組み立てから SELECT の実行までを、U1 の土台の上でバインド経路に切り替える。旧 API の戻り値は変えない |
| 所有するコンポーネント | M-3 `Search`（3 状態の同期、`getSearchParam()` の追加）、M-2 `QueryImpl` のうち `whereValues` / `addWhere(String, Param)` / `addSearch` / `getSelectPreparedSql()` / `andIn` / `andNotIn` / `andExists` / `andNotExists`、M-1 `Query` の新 `default` メソッド一式と `@Deprecated` 付与、M-4 `Dao` / `DaoAdapter` のうち `prepare(...)` / `executeQuery(PreparedSql)` / `search(Query)` / `searchList(Query,int,int)` |
| 境界 | INSERT / UPDATE / DELETE の経路には触れない。方言クラスには触れない。`Sort` には触れない |
| 依存 | U1 |
| 担当 FR | FR-1.1、FR-1.2、FR-1.3、FR-1.6、FR-3.1、FR-3.3、FR-7.1、FR-7.2 |

**この Unit が抱える最大の設計リスク**: `Search` は `search`（リテラル系テキスト）/ `bindSearch`（バインド系テキスト）/ `bindValues`（値）の 3 状態を保持し、そのすべてを同じ呼び出しの中で更新しなければならない（`component-methods.md` M-3「同期規則」）。3 つのうち 1 つだけを変更する経路が 1 つでもできると、`Param.of(...)` の個数検査を通り抜けたまま述語が欠落する。設計はこれを構造で守ることを求めている——`search` / `bindSearch` / `bindValues` を直接触るのは単一の private メソッド `append(String literal, Param bind, String connector)` だけとし、`and` / `or` / `and(Search)` / `or(Search)` はすべてそれを経由する。

**`Query` インターフェースの変更をここに置く理由**: M-1 の追加は `default` メソッド 8 個と `@Deprecated` 5 個であり、そのうち `getSelectPreparedSql()` / `where(Param)` / `and(Param)` / `or(Param)` は U2 が実際に override する。残り（`getInsertPreparedSql` 等）は U3 が override するが、インターフェース側の宣言を 2 つの Unit に分けると、U2 完了時点で `Query` が「半分だけ新メソッドを持つ」状態になり、`QueryImpl` のコンパイルが片方の Unit に引きずられる。インターフェース宣言は U2 で**一括して**追加し、U3 は override を足すだけにする。

**移行作業（Q7 = B）**: `UserDao.java:22, :43` / `FileDataDao.java:12, :30` / `MySQLDaoTest.java:83` の `param()` → `prepare()` 切り替えと、`UserDaoTest` の実行 SQL アサーション書き換えのうち SELECT 経路に該当するぶんは、この Unit の内部作業とする。FR-8.2 の対応表もこの Unit で該当行を起こす。

**実装上の制約**:
- CON-7（`QueryImpl` は単回使用）。`whereValues` は `where` の `StringBuilder` と同一ライフサイクルに載る
- サブクエリ（FR-1.6）は子の値を親の値リストの**差し込み位置**に挿入する。並び替えは発生しない（`component-dependency.md`「サブクエリの差し込み」）
- 旧 `getSelectSQL()` / `getSearchString()` の戻り値の中身を変更しない（ADR-003）

---

### U3. `write-path` — 書き込み経路のバインド化

| 項目 | 内容 |
|---|---|
| kind | `library` |
| 複雑度 | **M** |
| 責務 | INSERT / UPDATE / DELETE をバインド経路に切り替える。既存サブクラスが無変更で動き続ける互換シムを設ける |
| 所有するコンポーネント | M-4 `Dao` / `DaoAdapter` の新 protected 拡張点（`getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)`）と `executeUpdate(PreparedSql)`、`create(T)` / `update(T)` / `delete(T)` の切り替え、M-2 `QueryImpl` のうち `setValues` / `insertValues` / `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql` / `getBlobIndex()` のバインド版契約、M-8 `BlobUtils` の位置づけ変更 |
| 境界 | SELECT 経路には触れない。方言クラスには触れない |
| 依存 | U1、U2 |
| 担当 FR | FR-1.4、FR-1.5、FR-3.2、FR-6.2 |

**U2 に依存する理由**: `getUpdatePreparedSql` は `setValues ++ whereValues` の順で値を結合する（ADR-011）。`whereValues` と `addWhere(String, Param)` は U2 が導入する。また `getDeletePreparedSql` / `getDeleteAllPreparedSql` は WHERE のみを使うため、U2 の値アキュムレータがなければ成立しない。

**FR-6.2 をここに同居させる理由（Q4 = B）**: `QueryImpl.java:268` の OBJECT ブランチにテーブル修飾が残る不整合は、`getUpdatePreparedSql` が触る SET 句の組み立てそのものである。バインド版とリテラル版の両方で修正するため、SET 句を書き換える Unit の中で直すのが自然であり、別 Unit にすると同じ数行を 2 回触ることになる。

**この Unit が抱える最大の設計リスク**: `QueryImpl.getUpdateSQL`（`:236-274`）は SET 句と WHERE 句を単一のループで組み立てるが、テキストは SET → WHERE の順で連結する。ライブラリ自身のフィクスチャでは主キーが `updateColumns` の 1 番目であるため（`User.java:23-24`、`DefaultTable.java:16` の `LinkedHashSet`）、**WHERE の値が SET の値より先に生成される**。値リストを 1 本にすると全ての値が 1 つずつずれ、しかも `?` の個数は一致するため生成時検査も JDBC のパラメータ数検査も通り抜ける（`component-dependency.md`「単一の値リストが破綻する具体例」）。この失敗様式を検出できるのは U1 が用意した mock の位置・値アサートだけであり、Build and Test（3.6）で「UPDATE の SET + WHERE 混在ケース」を必ずテストする必要がある。

**実装上の制約**:
- 既定実装は旧 `getInsertSQL(T)` 等に委譲し `PreparedSql.ofLiteral(...)` で包む（ADR-004）。この経路を通った SQL はリテラル埋め込みのままであり、**SM-1 の達成対象ではない**
- `getBlobIndex()` は新旧で戻り値の意味が異なる（旧＝OBJECT カラムの個数、新＝バインドパラメータ位置）。Javadoc で明示する（`component-methods.md` M-2）
- BLOB を含む旧経路の SQL は未束縛の `?` を持つため、`hasUnboundPlaceholders()` により実行前に `DaoException` で拒否される（ADR-012）

---

### U4. `dialects` — 方言経路のバインド化と既存欠陥の是正

| 項目 | 内容 |
|---|---|
| kind | `library` |
| 複雑度 | **M** |
| 責務 | MySQL / Oracle の方言経路を新経路に載せ、その経路上に存在する既存欠陥を是正する |
| 所有するコンポーネント | M-7 `MySQLDao`（`searchList` の `PreparedSql` 化、LIMIT のバインド）、`OracleDao`（`searchListForOracle` の修正と有効化）、`OracleSearch.OracleValueConvertFilter`（null ガード） |
| 境界 | 汎用経路（`Dao` / `Search` / `QueryImpl`）には触れない。`MySQLSearch` / `MySQLCondition` / `PostgreSQLCondition` は変更なし。PostgreSQL 専用クラスは追加しない |
| 依存 | U2 |
| 担当 FR | FR-5.1、FR-5.2、FR-5.3、FR-6.1、FR-6.3、FR-6.4 |

**FR-6.1 / FR-6.3 / FR-6.4 をここに同居させる理由（Q4 = B）**: FR-6.1（`searchListForOracle` の未バインド `?`）と FR-6.3（LIMIT の `int` 直接連結）は、この Unit がまさに書き換える実行メソッドの中にある。FR-6.4（`OracleValueConvertFilter` の null ガード）はバインド化とは無関係な旧経路の修正だが、対象ファイルが `OracleSearch.java` の 5 行であり、同じ方言まわりに触る Unit で一緒に直すほうが分散させるより追跡しやすい。

**挙動が変わる点**: FR-6.1 の有効化により、Oracle のページングが汎用経路の全行スキャン（`Dao.java:181-184`）から rownum 方式に変わる。public API の破壊ではないが**観測可能な挙動の変更**であり、性能特性も変わる（`requirements.md` NFR-7 の補足）。

**実装上の制約**:
- `SQL_CALC_FOUND_ROWS` の `replaceFirst("SELECT ", ...)`（`MySQLDao.java:44`）はテキスト操作であり `?` を増やさない。順序に影響しないため不変
- LIMIT / rownum の値は WHERE より後ろのテキストに現れるため、値リストの末尾に追加すれば順序が一致する（`component-dependency.md`「後続の連結」）
- `OracleDao` は `createSearch` を override するが `createQuery` を override しないという現行の非対称は、バインド経路では `ValueConvertFilter` が適用されないため（FR-2.2）当該経路では解消される

---

### U5. `identifier-safety` — 識別子位置の注入不能化（Should）

| 項目 | 内容 |
|---|---|
| kind | `library` |
| 複雑度 | **S** |
| 責務 | バインドできない位置（テーブル名・カラム名・ORDER BY 句）に呼び出し側から渡された文字列が、SQL の構文として解釈されうる形でそのまま連結されない状態にする |
| 所有するコンポーネント | `Sort`（`Sort.java:47-60` の非 `Column` キー経路）、および `Column.getFunctionName()` / `DataType.FUNCTION` の扱い |
| 境界 | 値のバインドには触れない。U1〜U4 が触るクラスを変更しない |
| 依存 | U2 |
| 担当 FR | FR-1.7（**Should Have**） |

**この Unit を独立させた理由（Q5 = A、FQ-1 = A）**: 機構が他の 4 Unit とまったく異なる。U1〜U4 は「値をバインドする」ことで安全性を得るが、この Unit は「識別子を検証して拒否する」ことで安全性を得る。共有するコードもない。Should Have であるため、この Unit だけを落としても U1〜U4 は成立し、SM-1 の判定にも影響しない（NFR-1 の対象範囲は FR-1.7 を含まない）。

**未確定事項**: 機構が (a) 例外による拒否か (b) 宣言済み `Table` / `Column` メタデータへの照合かは、**Functional Design（3.1）で決定する**。受け入れ条件 AC-10b はどちらの機構でも判定できる形になっている。

**この Unit が抱えるリスク**: `Sort.sort(Object k, Object o)` の非 `Column` キー経路は、**意図的にメタデータ外の式を許すために存在する**。検証を強制すると既存利用者が壊れうる（FR-3.1 / NFR-3 との衝突）。3.1 はこの衝突を解く必要がある。`application-design` は `Sort` を「変更しないコンポーネント」として扱っており、この Unit の設計は 3.1 が新規に行う。

**U2 に依存する理由**: `Sort` は `Query.where(Search, Sort)` / `and(Search, Sort)` / `or(Search, Sort)` / `orderBy` を通じて SELECT 文に適用される。U2 がこれらの経路をバインド版に切り替えた後でなければ、検証をどの層に置くかを決められない。

---

## デプロイモデル

**全 Unit が単一の成果物に同梱される。** tamacat-dao は Maven の `jar` パッケージングで配布されるスタンドアロンの Java ライブラリであり、デプロイ可能なプロセスを 1 つも持たない（`services.md`「本ステージにおける『サービス』の適用範囲」）。したがって Unit ごとに配置単位を選ぶ余地がなく、5 Unit すべてが `kind: library` である。

| Unit | 配置 | 成果物 |
|---|---|---|
| U1〜U5 | `org.tamacat:tamacat-dao` の単一 jar に同梱 | `src/main/java` 配下のクラス（U1 のみ `org.tamacat.mock.sql` を含む） |

**記録すべき帰結**: U1 が拡張する `org.tamacat.mock.sql` は `src/main` にあるため、**mock の記録機能も production jar に同梱される**。これは `requirements.md` OOS-6 が mock の `src/test` 移動をスコープ外としたことに従った結果であり、`decisions.md` ADR-009 が Negative として記録している。

---

## FR と Unit の対応

| 要件 | 担当 Unit |
|---|---|
| FR-1.1〜FR-1.3（単一値・LIKE・IN/BETWEEN のバインド） | U2（生成規則は U1 の `BindSqlBuilder`） |
| FR-1.4（INSERT VALUES / UPDATE SET のバインド） | U3 |
| FR-1.5（BLOB） | U3（`BindValue` と `PreparedStatementBinder` は U1） |
| FR-1.6（サブクエリ） | U2 |
| FR-1.7（識別子位置、**Should**） | **U5** |
| FR-2.1〜FR-2.4（検証・エスケープ） | U1 |
| FR-3.1（API 互換） | U2（`Query` の `default` 追加と非推奨）、U3（`Dao` の互換シム） |
| FR-3.2（`getBlobIndex()`） | U3 |
| FR-3.3（旧メソッドの戻り値契約） | U2 |
| FR-4.1 / FR-4.2（実行記録） | U1 |
| FR-5.1〜FR-5.3（方言） | U4 |
| FR-6.1 / FR-6.3 / FR-6.4（Oracle・MySQL の欠陥） | U4 |
| FR-6.2（`QueryImpl:268` のテーブル修飾） | U3 |
| FR-7.1 / FR-7.2（raw 経路の維持と文書化） | U2 |
| FR-7.3（`prepare()` + `where(Param)` の代替経路。2.7 で追加） | U2 |
| FR-8.1（バインド位置と値のアサート） | U1 |
| FR-8.2（旧⇔新アサーション対応表） | U2 / U3 / U4 に分散（Q7 = B）。集約は Build and Test（3.6） |
| FR-8.3〜FR-8.5（未実行テストの取り込み、インジェクションプローブ、LIKE 候補枯渇） | Build and Test（3.6）。本ステージの Unit には含めない |

**未割当がないことの確認**: FR-1〜FR-8 のすべての小項目が上表のいずれかの行に現れる。`application-design` の `components.md` で「**未割当**」だった FR-1.7 は、本ステージで U5 に割り当てられた。

---

## 前提と持ち越し事項

### 本ステージで解決した Open Question

| OQ | 状態 | 解 |
|---|---|---|
| OQ-9（FR-1.7 の機構） | **部分解決** | 「実施するか」は解決 — U5 として実施する（Q5 = A、FQ-1 = A）。「どの機構か」は Functional Design（3.1）に委ねる |

### 本ステージで実施した `requirements.md` の更新

`decisions.md`「承認済み要件との差分」表が本ステージの承認ゲートまでに求めていた 2 点を反映した。

| # | 対象 | 更新内容 |
|---|---|---|
| 1 | FR-7.1 / FR-7.2 | 旧 `where(String)` の**直接利用**は SM-1 の判定対象外のままとしつつ、`param()` 経由で渡される値は代替経路により SM-1 の対象に含まれることを明記。FR-7.2 に代替経路と移行方法の文書化を追加（ADR-002） |
| 2 | **FR-7.3（新規追加）** | `Dao.prepare(...)` と `Query.where(Param)` / `and(Param)` / `or(Param)` の提供を要件化。旧 `param()` / `where(String)` は無変更で残す。差分を FR-7.1 の但し書きだけで表すと「何を追加するのか」が要件として残らないため、独立した FR として起こした（ADR-002） |
| 3 | NFR-1 | 判定基準を「対象範囲内で値を SQL 文字列に連結する経路が 0 件」から「**実行経路**に値の文字列連結が 0 件」に改め、非推奨の public メソッド経由でのみ到達できるリテラル生成経路を判定対象外とした（ADR-003） |
| 4 | OQ-9 | 実施可否の解決（U5 として実施）を反映。機構の選択のみ 3.1 に残ることを明記 |

`requirements.md` には「本ステージ以降に適用された更新」節を追加し、2.3 時点の `## Review` 節が更新前の内容に対するものであることを明示した。

**`decisions.md` の差分表との食い違いを明示する。** `decisions.md`「承認済み要件との差分」節は更新対象を **2 点**（FR-7.1 / FR-7.2 の文言、NFR-1 の判定の読み替え）と数え、「この 2 点以外に `requirements.md` との差分はない」と明言している。しかし本ステージが実際に適用したのは上表の **4 点**である。差分の内訳と、それぞれが承認済みの決定の範囲内である根拠は次のとおり。

| 差分表に載っていない更新 | 根拠 | 逸脱か |
|---|---|---|
| FR-7.3 の新規追加 | ADR-002 の **Decision 本文**が `Dao.prepare(...)` と `Query.where(Param)` / `and(Param)` / `or(Param)` の追加を明示的に決定している。ADR-002 はこの追加に要件 ID を与えなかったため、差分表の 2 点（既存記述の**修正**）に数えられていない | **逸脱ではない。** 決定の内容は承認済みであり、本ステージは要件 ID を与えて追跡可能にしたにすぎない。ID がなければ FR-7.1 の但し書きに「対象に含める」という否定形が残るだけで、「では何を追加するのか」が要件から引けない |
| OQ-9 のステータス更新 | ADR-010 が「2.7 で FR-1.7 を独立した Unit として切り出すか、今回のスコープから外すかを判断できる」として実施可否の判断を本ステージに委ねている | **逸脱ではない。** ADR-010 が本ステージに委ねた判断そのものであり、差分表は ADR-002 由来の 2 点のみを列挙したものであって ADR-010 由来の更新を含んでいない |

**したがって `decisions.md` の「この 2 点以外に差分はない」という記述は、ADR-002 由来の文言修正に限れば正しいが、`requirements.md` 全体に対する差分の全量としては不完全である。** この不完全さは `decisions.md` の記載範囲の問題であり、本ステージの逸脱ではない。`decisions.md` は 2.6 の承認済み成果物であるため本ステージでは編集せず、ここに記録して解消する。

FR-1.7 は U5 として実施するため、**Won't Have には落としていない**。優先度は Should Have のまま維持する。

### 後続ステージに残る未解決事項

| OQ | 解消先 |
|---|---|
| OQ-4（カバレッジ 80% の適用可否と計測手段） | NFR Requirements（3.2） |
| OQ-6（`UserDaoTest2` 有効化に伴う Derby 依存の追加可否） | Build and Test（3.6） |
| OQ-7（SM-3 / NFR-2 の測定方法。CodeQL は `master` のみ対象、CON-8） | Build and Test（3.6）。CI Pipeline（3.7）は本スコープで SKIP |
| OQ-8（利用アプリ側の監査指摘と SM-3 の測定対象の同一性） | 未定 — 利用側アプリケーションの状況確認が必要 |
| FR-1.7 の機構選択（OQ-9 の残り） | Functional Design（3.1）、U5 の中で |

### 本ステージが決めなかったこと

**実装順序とクリティカルパスは決めていない。** 本文書と `unit-of-work-dependency.md` が示すのは依存のトポロジー——何が何に依存しうるか——だけである。どの Unit を先に出荷するか、どの Bolt が何を証明するかという経済的な判断は Delivery Planning（2.8）が行う。

---

## Review

READY

iteration 2。iteration 1 で指摘した 4 件の non-blocking 指摘それぞれについて、修正後の `unit-of-work.md` / `unit-of-work-story-map.md` / `unit-of-work-dependency.md` を再読し、実ソース（`DaoAdapter.java:26` 他）と `decisions.md` の該当 ADR 本文を再度突き合わせて検証した。加えて、修正が新たな欠陥を持ち込んでいないかを別途確認した。4 件とも実質的に解消されている。ブロッキングな欠陥は今回も見つからなかったため READY を維持するが、指摘 1 の修正自体に小さな新規の算術誤りが 1 件見つかったため、non-blocking として記録する。

**指摘 1 — 解消（ただし修正文中に新たな軽微な算術誤りあり、non-blocking）。**
`unit-of-work-story-map.md`「網羅性の検証」節（66行）は FR 総数を「31 件」とし、内訳（7/4/3/2/3/4/3/5）を明示している。実際に `FR → Unit のマッピング` 表を数え直すと合計は 7+4+3+2+3+4+3+5 = **31** で一致する。「割り当ての検証まとめ」表（186行）も「31 件すべて」で揃っており、以前あった「32 件」という誤りは解消された。
Unit ごとの件数についても、「表への出現行数で数える」という定義のもとで手動突合した結果、U1=7（FR-2.1〜2.4／4.1／4.2／8.1）、U2=10（FR-1.1〜1.3／1.6／3.1／3.3／7.1〜7.3／8.2）、U3=6（FR-1.4／1.5／3.1／3.2／6.2／8.2）、U4=7（FR-5.1〜5.3／6.1／6.3／6.4／8.2）、U5=1（FR-1.7）で、「網羅性の検証」節（67行）と「割り当ての検証まとめ」表（188行）の両方が同じ数字（U1=7/U2=10/U3=6/U4=7/U5=1）を示しており、以前の「U1=8」「U2=10 対 U2=9」という自己矛盾は解消されている。
ただし、67行の説明文「合計が 31 を超えるのは、FR-3.1（U2＋U3）と FR-8.2（U2/U3/U4＋3.6）が複数 Unit にまたがるためである」は不正確である。実際に Unit 側の出現件数を合計すると 7+10+6+7+1 = **31** であり、FR 総数の 31 と**一致するのであって超えない**。これは、FR-3.1（+1 個分の超過）と FR-8.2（+2 個分の超過）による超過ぶん（合計 +3）が、どの Unit にも割り当てられない FR-8.3／FR-8.4／FR-8.5（Build and Test 3.6 のみが担当、-3 個分の不足）と数値的にちょうど相殺しているためである。Unit 割り当て自体・個々の件数（7/10/6/7/1）はいずれも正しく、これは「合計が超える」という 1 文の説明が誤っているだけの軽微な指摘である。67行の「合計が31を超える」を「合計は31に一致する（FR-3.1／FR-8.2による超過分は、Unitに割り当てのないFR-8.3〜FR-8.5の不足分と相殺するため）」のように訂正することを推奨する。

**指摘 2 — 解消。**
`unit-of-work.md`「本ステージで実施した `requirements.md` の更新」節（208〜215行）に、`decisions.md` の「差分は 2 点のみ」という宣言と実際の 4 点更新との食い違いを明示する段落と表が追加された。引用の正確性を再検証した——`decisions.md` の ADR-002 Decision 本文が実際に `Dao.prepare(...)` と `Query.where(Param)`/`and(Param)`/`or(Param)` の追加を決定していること（該当箇所と一致）、ADR-010 の Consequences・Positive が実際に「2.7 で Unit に分解する際、FR-1.7 を独立した Unit として切り出すか、今回のスコープから外すかを判断できる」と書いていること（該当箇所と一致）をいずれも確認した。結論（「逸脱ではない」「`decisions.md` の記載範囲の問題」）は誠実で、ADR の実際の決定範囲を超えて要件を追加したり、逆に必要な更新を過小に見せたりする方向の脚色は見られない。`decisions.md` 自体は 2.6 の承認済み成果物として編集せず、`unit-of-work.md` 側に記録する、という処理も方法論的に妥当である。

**指摘 3 — 解消。**
`unit-of-work-story-map.md` の AC-11 行（90行）、「AC の網羅性」節（96行）、「Unit をまたぐ要件」節の AC-11 再掲（116行）のいずれもが、根拠を「`Query` の `default` 追加」から「`DaoAdapter` は `AutoCloseable` のみを実装し `Query` を実装しない（`DaoAdapter.java:26`）ため、実際の理由は U2 と U3 がそれぞれ `DaoAdapter.java` に直接メソッドを追加すること」に訂正している。`DaoAdapter.java:26` を再確認したところ `public class DaoAdapter<T extends ORMappingSupport<T>> implements AutoCloseable` で `Query` を実装していないことを確認しており、修正後の記述は実ソースと一致する。結論（U2 と U3 の両方が揃うまで判定できない）は変わらず、根拠だけが正しくなった。

**指摘 4 — 解消。**
`unit-of-work-dependency.md`「並行開発の機会」節（137行）の該当文は「U4 は `QueryImpl` を変更せず、`getSelectPreparedSql()` という公開 API の呼び出し側として利用するだけである。U3 は書き込み経路の担当であり `getSelectPreparedSql()` を呼ばない。したがって段 3 で `QueryImpl.java` に書き込むのは U3 のみであり、書き込みの競合はない」に書き換えられている。これは同文書の「ブラストレーダスと Unit の対応」表（183行、`QueryImpl.java` を触るのは U2 と U3 のみで U4 は含まれない）と整合しており、実ソース（`MySQLDao.java` / `OracleDao.java` が `searchList` 系のみを override し `QueryImpl` 自体を変更しないこと）とも整合する。

以上、4 件とも解消を確認した。新規に見つかったのは指摘 1 の修正に伴う軽微な算術誤り（「超える」ではなく「一致する」が正しい）のみであり、ブロッキングではない。

---

**適用記録（オーケストレータ、2026-08-05T17:31:00Z）**: 上記 iteration 2 で唯一残った指摘（`unit-of-work-story-map.md`「網羅性の検証」節の「合計が 31 を超える」）を、レビュアーが本文中で指定した訂正文言のとおりに適用した。「合計は 31 に一致する。FR-3.1 と FR-8.2 による重複計上の +3 が、どの Unit にも割り当てのない FR-8.3 / FR-8.4 / FR-8.5 の −3 と相殺するため」に書き換えている。**この 1 文の訂正はレビュアーの再検証を受けていない**が、変更はレビュアー自身が示した文言の適用であり、Unit 割り当て・件数・DAG のいずれにも影響しない。

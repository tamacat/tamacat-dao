# Bolt Plan — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— Unit U1〜U5 の定義・責務・境界・複雑度・kind・実装制約。本計画はこの 5 Unit を 4 つの Bolt に束ねる。
- **`unit-of-work-dependency.md`**（同上）— 依存 DAG（`U1 → U2 → {U3, U4, U5}`）と並行可能な組。本計画の Bolt 順序がこの DAG に違反しないことを「DAG 適合の検証」節で確認する。
- **`unit-of-work-story-map.md`**（同上）— FR / AC の Unit への割り当て。各 Bolt の Definition of Done はこの割り当てから引いた AC を判定条件とする。
- **`components.md`**（application-design, 2.6）— C-1〜C-8 / M-1〜M-8。Bolt に含まれるコンポーネントの粒度はこの一覧に対応する。
- **`requirements.md`**（requirements-analysis, 2.3）— FR / NFR / CON / AC。とくに CON-3（v2.0 上でローカルのみ、push しない）が Way of Working を規定する。
- **`stories.md`**（user-stories, 2.4）— 本スコープで **SKIP** のため存在しない。Bolt の Definition of Done はユーザーストーリーではなく `requirements.md` の AC を判定条件とする。
- **`mockups`**（refined-mockups, 2.5）— 同じく **SKIP**。UI を持たないライブラリであり、Bolt に UI 検収の観点はない。
- **`team-practices`**（practices-discovery, 2.2）— 同じく **SKIP**。したがって `aidlc/spaces/default/memory/team.md` と `project.md` の該当セクションは空であり、`org.md` の既定が効く（下記「適用した practices」参照）。

設問と回答の出典は `delivery-planning-questions.md` の Q1〜Q7 および FQ-1〜FQ-3 を指す。

---

## 適用した practices

practices-discovery（2.2）が SKIP のため、`aidlc/spaces/default/memory/` の解決順（project → team → org）で **org.md の既定**まで下りる。ただし 2 点は本プロジェクトの制約により読み替える。

| 項目 | org.md の既定 | 本プロジェクトでの適用 | 根拠 |
|---|---|---|---|
| Way of Working | trunk-based。base / target とも `main`。squash-merge | **base / target とも `v2.0`。squash-merge。`git push` しない** | `requirements.md` CON-3（v2.0 上でローカルのみ、リモートは v1.6.1 のまま）。このリポジトリの既定ブランチは `master` であり `main` は存在しない。Q5 = A |
| Walking Skeleton | scope 依存。active scope が `skeleton: on` を宣言していれば最初に走らせる | **実施する。** Bolt 1 が walking skeleton であり、単独でゲートを持つ | `.claude/scopes/aidlc-sql-parameterization.md` の `skeleton: on`、`scope-document.md`「Sequencing Approach」 |
| Deployment | マージで staging にデプロイ、production は手動承認 | **該当なし。** デプロイ可能なプロセスを持たないライブラリであり、Operation フェーズは全ステージ SKIP | `services.md`、`aidlc-state.md` の Stages to Skip |
| Testing Posture | scope 別。`sql-parameterization` は org.md の列挙にない | ワークフロー状態の **Test Strategy: Standard**（コンポーネントあたり 5-8 テスト、単体 ＋ 主要境界の統合） | `requirements.md`「テスト量の方針」 |

**org.md からの逸脱を明示する**: Way of Working の base / target を `main` から `v2.0` に読み替えた。これは org.md への矛盾ではなく、`main` を持たないリポジトリでのトランクの同定である——v2.0 が本取り組みにおけるトランクであり、そこへ短命の Bolt ブランチを squash-merge するという形は org.md の趣旨をそのまま保っている。異なるのは `git push` しない点であり、これは CON-3 が明示的に定めた作業制約である。

---

## Bolt 一覧

**4 Bolt、逐次実行**（Q1 = D、Q4 = B、FQ-1 = A）。全 Bolt を `aidlc-developer-agent`（AI）が実行する（team-formation 2.5 が SKIP のため。詳細は `team-allocation.md`）。

### Bolt 1 — `walking-skeleton` ⭐ walking skeleton

| 項目 | 内容 |
|---|---|
| 含む Unit | U1 `bind-foundation` と U2 `select-path` を**縦に薄く貫く一部** |
| walking skeleton | **はい。** 単独でゲートを持ち、承認後に ladder prompt が出る |
| 範囲 | `Param` / `PreparedSql` / `BindValue` / `ValueRules`（EQUAL 経路に必要な最小） / `BindSqlBuilder.value`（EQUAL のみ） / `PreparedStatementBinder`（STRING の `setString` のみ） ＋ `Search.and` の値蓄積 ＋ `QueryImpl.whereValues` と `getSelectPreparedSql()` ＋ `Dao.search` ＋ `DBAccessManager.executeQuery(PreparedSql)` ＋ mock スタック拡張（`MockConnection.prepareStatement` の SQL 保持、`MockPreparedStatement` の位置・値記録） |
| 範囲外 | LIKE / IN / BETWEEN、NULL 判定、型検証、`current_timestamp`、サブクエリ、INSERT / UPDATE / DELETE、方言、`Sort` |
| 証明する層 | SQL 組み立て層（`BindSqlBuilder`）→ 値の運搬層（`Param` / `PreparedSql`）→ 実行層（`DBAccessManager` / `PreparedStatementBinder`）→ JDBC（`java.sql.PreparedStatement`）→ 検証層（mock 記録）。**層を 1 つも飛ばさない** |
| Definition of Done | STRING カラムに対する EQUAL 条件を持つ `Search` から `Dao.search` を実行したとき、(1) 実行 SQL に値のリテラルが含まれず該当位置に `?` が現れる、(2) その `?` に対応するバインド値が呼び出し側の渡した値と一致することを、拡張した `MockPreparedStatement` からアサートできる。既存テストが緑のまま（`SQLParserTest` / `SearchTest` / `QueryImplTest` / `MySQLDaoTest` は無変更で通る） |
| 判定する AC | **AC-1**（FR-1.1 / NFR-1）、FR-8.1 の受け入れ条件 |
| 確信仮説 | 「値と SQL テキストを対で運ぶ 2 型（`Param` / `PreparedSql`）＋ mock 記録という構成で、値が SQL 文字列から外れ、かつその正しさをテストから機械的に確認できる」——これが成り立たなければ、残り 3 Bolt の前提が崩れる |
| 想定デモ | 1 本の SELECT について、実行 SQL 文字列（`?` 入り）と、位置ごとのバインド値の一覧を並べて示す |

**なぜ最小なのか（Q2 = A）**: walking skeleton の目的はアーキテクチャが通ることの証明であって、機能の網羅ではない。条件種別を 1 つに絞ることで、失敗したときに「どの層が悪いか」が一意に切り分けられる。LIKE を含めると、失敗の原因が層の接続なのかエスケープ規則なのか判別しにくくなる。

---

### Bolt 2 — `read-path-complete`

| 項目 | 内容 |
|---|---|
| 含む Unit | U1 の残り ＋ U2 の残り |
| walking skeleton | いいえ |
| 範囲（U1 の残り） | `ValueRules` の全規則（FR-2.1 型検証 / FR-2.3 LIKE エスケープ / FR-2.4 空値・`NULL`・`current_timestamp`）、`SQLParser` の `ValueRules` 委譲化と `@Deprecated`、`ExecutedStatement` と `getExecutedStatements()`（FR-4.1 / 4.2）、`PreparedStatementBinder` の全 `DataType` 対応、`PreparedSql.ofLiteral` と `hasUnboundPlaceholders()`（ADR-012） |
| 範囲（U2 の残り） | `BindSqlBuilder` の LIKE / IN / BETWEEN / IS NULL 対応、`Search` の 3 状態同期（private `append(...)`）と `getSearchParam()`、`Query` の新 `default` メソッド 8 個の宣言と旧 5 メソッドへの `@Deprecated`、`Dao.prepare(...)`、サブクエリ（`andIn` / `andNotIn` / `andExists` / `andNotExists`）、`UserDao` / `FileDataDao` / `MySQLDaoTest` の `param()` → `prepare()` 移行、`UserDaoTest` の SELECT 経路アサーション書き換え |
| Definition of Done | 読み取り経路が全条件種別で成立する。`SQLParser` の公開メソッドの戻り値が 1 文字も変わらない（`SQLParserTest` が無変更で緑）。`getSearchString()` / `getSelectSQL()` の戻り値の中身が変わらない。FR-8.2 の対応表に本 Bolt ぶんの行が起きている |
| 判定する AC | **AC-2**（LIKE のエスケープ規則）、**AC-3**（IN の `(?,?,?)` と順序）、**AC-4**（サブクエリの差し込み位置）、**AC-5**（NUMERIC への非数値で `InvalidParameterException`）、**AC-6**（クォートの二重化が起きない）、**AC-8**（実行記録にバインド値） |
| 確信仮説 | 「旧 API の戻り値を 1 文字も変えずに、読み取り経路のすべての条件種別をバインド化できる」——ADR-003 が FR-3.1 と NFR-1 の衝突に与えた解が、実装レベルで成立することの証明 |
| 想定デモ | `SQLParserTest` / `SearchTest` / `QueryImplTest` が無変更で緑であることと、同じ条件に対するバインド版の実行 SQL ＋ 値を並べて示す |

**この Bolt が抱える最大のリスク**: `Search` の 3 状態（`search` / `bindSearch` / `bindValues`）の同期。3 つのうち 1 つだけを変更する経路が 1 つでもできると、`Param.of(...)` の個数検査を通り抜けたまま述語が欠落する。設計はこれを単一の private `append(...)` に閉じ込めることで**構造として**守ることを求めている（`component-methods.md` M-3、ADR-011）。

---

### Bolt 3 — `write-path-and-dialects`

| 項目 | 内容 |
|---|---|
| 含む Unit | U3 `write-path`（先）→ U4 `dialects`（後） |
| walking skeleton | いいえ |
| 範囲（U3） | `QueryImpl` の `setValues` / `insertValues`、`getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql`、`getBlobIndex()` のバインド版契約、`Dao` / `DaoAdapter` の新 protected 拡張点と互換シム、`executeUpdate(PreparedSql)`、`create` / `update` / `delete` の切り替え、**FR-6.2**（`QueryImpl:268` の SET 句テーブル修飾）、`QueryImplTest02/03` の BLOB プレースホルダ位置修正、`UserDaoTest` の書き込み経路アサーション書き換え |
| 範囲（U4） | `MySQLDao.searchList` の `PreparedSql` 化と **FR-6.3**（LIMIT のバインド）、`OracleDao.searchListForOracle` の修正と有効化（**FR-6.1**）、`OracleSearch.OracleValueConvertFilter` の null ガード（**FR-6.4**）、`MySQLDaoTest` の LIMIT アサート |
| Bolt 内の作業順 | **U3 → U4**（Q3 = A のリスク順。FQ-1 = A により Bolt 境界は設けず、Bolt 内の順序として効かせる） |
| Definition of Done | 書き込み経路と方言 3 経路が成立する。既存の DAO サブクラスが再コンパイルなしで動作する。`mvn test` が Failures = 0 / Errors = 0 |
| 判定する AC | **AC-3b**（INSERT VALUES / UPDATE SET のカラム並び順どおりのバインド値）、**AC-7**（`getBlobIndex()` がバインド位置を返し `setBinaryStream` した UPDATE が通る）、**AC-9**（Oracle の rownum ページング、未バインド `?` なし、`start` と `max` の両方使用）、**AC-10**（`ValueConvertFilter` の null で NPE なし）、**AC-11**（既存サブクラスが再コンパイルなしでロードできる） |
| 確信仮説 | 「節ごとに分けた値アキュムレータを、テキストの連結順に結合するという規則（ADR-011）が、SET 句と WHERE 句を単一ループで組み立てる現行構造の上で正しく動く」 |
| 想定デモ | SET ＋ WHERE が混在する UPDATE について、実行 SQL の `?` の並びとバインド値の並びを 1 : 1 で対照して示す |

**この Bolt が抱える最大のリスク（本計画で最も重い）**: `QueryImpl.getUpdateSQL`（`:236-274`）は SET 句と WHERE 句を単一ループで組み立てるが、テキストは SET → WHERE の順で連結する。ライブラリ自身のフィクスチャでは主キーが `updateColumns` の 1 番目であるため（`User.java:23-24`、`DefaultTable.java:16` の `LinkedHashSet`）、WHERE の値が SET の値より先に生成される。値リストを 1 本にすると**全ての値が 1 つずつずれる**。しかも `?` の個数と値の個数は一致するため、生成時の個数検査も JDBC のパラメータ数検査も通り抜け、**誤った値が誤ったカラムに書き込まれた UPDATE が正常終了する**。

この失敗様式を検出できるのは Bolt 1 で用意した mock の位置・値アサートだけである。AC-3b がその判定条件であり、Bolt 3 の Definition of Done に必ず含める。

**FQ-1 = A の帰結を明示する**: U3 が抱えるこのリスクは、U4 とまとめて 1 つのゲートで承認される。U3 単独のゲートは存在しない。Bolt 3 のゲートでは、AC-3b の判定結果を U4 の結果と分けて提示する。

---

### Bolt 4 — `identifier-safety`（Should）

| 項目 | 内容 |
|---|---|
| 含む Unit | U5 `identifier-safety` |
| walking skeleton | いいえ |
| 範囲 | `Sort.sort(Object, Object)` の非 `Column` キー経路（`Sort.java:47-60`）、`Column.getFunctionName()`、`DataType.FUNCTION` に対する識別子検証。機構（(a) 例外による拒否 / (b) 宣言済み `Table` / `Column` メタデータへの照合）は **Functional Design（3.1）で決定する** |
| Definition of Done | ORDER BY 句のキー、またはテーブル名・カラム名の位置に SQL の構文文字を含む文字列が渡されたとき、例外で拒否されるかメタデータ照合で拒否されるかのいずれかが起こり、渡された文字列が構文として生きたまま SQL テキストに現れない |
| 判定する AC | **AC-10b**（FR-1.7） |
| 確信仮説 | 「識別子位置の検証を導入しても、`Sort.sort(Object, Object)` の既存利用（意図的にメタデータ外の式を許す用途）を壊さない」——これが成り立たなければ FR-1.7 は Should Have として落とす判断がありうる |
| 想定デモ | 悪意ある識別子文字列を ORDER BY に渡したときの拒否と、既存の正当な `Sort` 利用が通り続けることを並べて示す |

**この Bolt だけが設計未完のまま Construction に入る。** `application-design` は `Sort` を「変更しないコンポーネント」として扱っており、参照できる既存設計がない（ADR-010）。3.1 が `Sort` の設計を新規に行い、そこで FR-3.1（既存利用を壊さない）との衝突を解く必要がある。**解けない場合、この Bolt を落として FR-1.7 を Won't Have にする判断がありうる。** Should Have であり、NFR-1 の対象範囲は FR-1.7 を含まないため、落としても SM-1 の達成には影響しない。

---

## DAG 適合の検証

`unit-of-work-dependency.md` の DAG は `U1 → U2 → {U3, U4, U5}`。本 Bolt 順序がこれに違反しないことを確認する。

| Bolt | 含む Unit | 先行して満たされている必要のある依存 | 満たされているか |
|---|---|---|---|
| 1 | U1 / U2 の縦切り | なし（U1 の一部が U2 の一部に先行するが、同一 Bolt 内で順序が保たれる） | ✅ |
| 2 | U1 の残り ＋ U2 の残り | U1 → U2 は Bolt 内で U1 の残りを先に完成させることで満たす | ✅ |
| 3 | U3 ＋ U4 | U1（Bolt 1/2 で完了）、U2（Bolt 1/2 で完了） | ✅ |
| 4 | U5 | U2（Bolt 1/2 で完了） | ✅ |

**トポロジカル順序からの逸脱はない。** Bolt 1 が U1 と U2 を縦に貫くのは walking skeleton の定義そのものであり、DAG 違反ではない——U1 の必要部分が U2 の必要部分に先行するという Unit 内の順序が Bolt 内で保たれている。詳細な議論は `risk-and-sequencing-rationale.md` を参照。

**並行可能性を使わなかった点**: DAG は `{U3, U4, U5}` の並行実行を許すが、Q4 = B により逐次とした。理由は `risk-and-sequencing-rationale.md` に記す。

---

## Construction の実行設定

| 設定 | 値 | 根拠 |
|---|---|---|
| Bolt 数 | 4 | Q1 = D、FQ-1 = A |
| 実行形態 | 逐次（並行バッチなし） | Q4 = B |
| walking skeleton | Bolt 1。単独ゲート、承認後に ladder prompt | scope の `skeleton: on` |
| Bolt 2 以降のゲート | ladder prompt の回答による（`Construction Autonomy Mode`） | `stage-protocol.md` §1 |
| worktree の base / target | `v2.0` / `v2.0` | Q5 = A、CON-3 |
| マージ戦略 | squash-merge | Q5 = A、org.md `## Way of Working` |
| `git push` | **しない** | CON-3 |
| 設計ステージの反復 | **unit-major** | Q7 = B |

**unit-major を選んだ帰結**: 3.1 Functional Design と 3.2 NFR Requirements が、Unit ごとに続けて書かれる（1 つの Unit の 3.1 と 3.2 を書いてから次の Unit へ）。ゲートの回数は stage-major と同じ（ステージあたり 1 回）だが、設計ブロックの最後にまとめて来る。walking-skeleton-first と Bolt ごとの縦切りに合わせた選択である。この設定は `aidlc-state.ts set-construction-iteration unit-major` で記録する。

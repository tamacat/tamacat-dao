# Delivery Planning — 設問

**ステージ**: 2.8 delivery-planning（Inception）
**Depth**: Standard（目安 5〜8 問）
**作成**: 2026-08-05T22:45:48Z

本ステージが決めるのは **Bolt の束ね方と出荷順**である。2.7 が作った依存 DAG は
「何が何に依存しうるか」を示すだけで、そのどの経路を通るかは経済的な判断であり、
DAG からは導けない。ここで初めてその判断を行う。

---

## Sources

- `inception/units-generation/unit-of-work.md` — Unit U1〜U5 の定義、責務、複雑度、kind、実装制約
- `inception/units-generation/unit-of-work-dependency.md` — 依存 DAG（`U1 → U2 → {U3, U4, U5}`）、並行可能な組、Unit をまたぐ不変条件
- `inception/units-generation/unit-of-work-story-map.md` — FR / AC の Unit への割り当て、横断要件
- `inception/application-design/components.md` — C-1〜C-8 / M-1〜M-8
- `inception/application-design/services.md` — 「walking skeleton の最小単位」の記述
- `inception/requirements-analysis/requirements.md` — FR / NFR / CON / AC / OQ
- `ideation/scope-definition/scope-document.md` — Sequencing Approach（walking-skeleton-first）
- `aidlc/spaces/default/memory/org.md` — practices-discovery が SKIP のため、`## Way of Working` / `## Walking Skeleton` / `## Deployment` はこの既定が効く
- `stories.md`（user-stories 2.4）/ `mockups`（refined-mockups 2.5）/ `team-practices`（practices-discovery 2.2）— いずれも本スコープで **SKIP** のため存在しない

### 本ステージで前提として扱う事実（設問にしない）

| 項目 | 事実 | 出典 |
|---|---|---|
| walking skeleton の実施 | **実施する。** 最初の Bolt は walking skeleton とし、単独でゲートを持つ | `.claude/scopes/aidlc-sql-parameterization.md` の `skeleton: on`、`scope-document.md`「Sequencing Approach」、`org.md` `## Walking Skeleton` |
| 担当 mob | team-formation（1.5）が SKIP のため、**全 Bolt を `aidlc-developer-agent`（AI）が実行する**。人間のチーム編成は行わない | stage 定義 Step 5、`aidlc-state.md` の Stages to Skip |
| Unit の依存 | `U1 → U2 → {U3, U4, U5}`。並行可能な組は `{U3, U4, U5}` | `unit-of-work-dependency.md` |

---

## Q1. Bolt の粒度 — 5 Unit をどう束ねるか

Unit と Bolt は 1 : 1 とは限らない。1 Bolt は Construction ステージ（3.1 / 3.2 / 3.5 / 3.6）を 1 周する単位である。

- **A. 1 Unit = 1 Bolt（5 Bolt）** — ただし walking skeleton は U1 と U2 を薄く縦に貫く別枠とするため、実際は「skeleton Bolt ＋ 5 Bolt」ではなく「skeleton を U1/U2 Bolt の中に含める」形になる
  - 帰結: Bolt 境界が Unit 境界と一致し追跡が単純。Bolt 数は 5
- **B. skeleton を独立 Bolt にし、残りを Unit 単位にする（6 Bolt）** — Bolt 1 = 単純な WHERE 比較 1 経路の縦切り（`services.md`「walking skeleton の最小単位」）、Bolt 2 = U1 の残り、Bolt 3 = U2 の残り、Bolt 4 = U3、Bolt 5 = U4、Bolt 6 = U5
  - 帰結: skeleton が最小になり、最初のゲートが早く来る。ただし U1 / U2 が 2 つの Bolt に割れ、同じファイルを 2 度触る
- **C. 依存の段で束ねる（3 Bolt）** — Bolt 1 = U1 ＋ U2（skeleton を含む）、Bolt 2 = U3 ＋ U4、Bolt 3 = U5
  - 帰結: Bolt 数が最小。ただし Bolt 1 が非常に大きく、最初のゲートまでが長い
- **D. skeleton ＋ 依存の段（4 Bolt）** — Bolt 1 = skeleton（縦切り）、Bolt 2 = U1 ＋ U2 の残り、Bolt 3 = U3 ＋ U4、Bolt 4 = U5
  - 帰結: skeleton が早く、以降は並行可能な組を 1 Bolt にまとめる
- **X. Other（自由記述）**

[Answer]: D（2026-08-08T17:48:23Z, **Mode:** guided）

---

## Q2. walking skeleton（Bolt 1）にどこまで含めるか

`services.md`「walking skeleton の最小単位」は次の範囲を挙げている——`ValueRules` ＋ `BindSqlBuilder` ＋ `Param` ＋ `PreparedSql` ＋ `BindValue` ＋ `PreparedStatementBinder` ＋ `Search.and` の値蓄積 ＋ `QueryImpl.getSelectPreparedSql` ＋ `Dao.search` ＋ `DBAccessManager.executeQuery(PreparedSql)` ＋ mock 拡張。

- **A. 上記のとおり。ただし条件は 1 種類（STRING の EQUAL）に絞る** — AC-1 が判定条件になる
  - 帰結: 最小。LIKE / IN / BETWEEN・NULL 判定・型検証は Bolt 2 以降
- **B. 上記に加えて値の検証・エスケープ規則（FR-2 全体）を含める** — `ValueRules` を完成させてから縦に通す
  - 帰結: skeleton が「土台としても完成している」状態になるが、範囲が広がりゲートが遅れる
- **C. 上記に加えて INSERT を 1 本通す** — 読み取りと書き込みの両方を端から端まで証明する
  - 帰結: `Dao` の新 protected 拡張点（ADR-004、可逆性が最も低い決定）を最初に検証できる。ただし U3 の一部を前倒しすることになる
- **X. Other（自由記述）**

[Answer]: A（2026-08-08T17:48:23Z, **Mode:** guided）

---

## Q3. skeleton 以降の順序方針

DAG は `{U3, U4, U5}` を任意の順に並べられる。何を基準に選ぶか。

- **A. リスクの大きい順** — 「値の並び順ずれ」（`component-dependency.md` が「最も危険な失敗様式」と呼ぶ、例外を出さずに誤った値が書き込まれる UPDATE）を抱える **U3 write-path を先**にする。方言（U4）と識別子検証（U5）は後
- **B. 影響範囲の広い順** — 利用者が最も多く通る経路から。汎用経路（U3）→ 方言（U4）→ Should（U5）。結果として A と同じ並びになるが理由が異なる
- **C. 小さい順（早く数を減らす）** — U4（M）→ U3（M）→ U5（S）のうち独立して閉じるものから
- **D. Should を先に片付ける** — U5 を早めに出し、落とすかどうかの判断を早く確定させる
- **X. Other（自由記述）**

[Answer]: A（2026-08-08T17:48:23Z, **Mode:** guided）

---

## Q4. Bolt の並行実行

`unit-of-work-dependency.md` は `{U3, U4, U5}` が相互に独立であり並行可能だと記録している。Construction をそのとおり並行で走らせるか。

- **A. 並行させる** — U3 / U4 / U5 を 1 バッチとして同時に走らせ、バッチ単位でゲートする
  - 帰結: 所要が短くなる。ただし失敗時の切り分けが増え、3 つの worktree を同時に扱う
- **B. 逐次にする** — Q3 で決めた順に 1 Bolt ずつ
  - 帰結: 各 Bolt の完了時点でテストが緑であることを確認してから次へ進める。ローカル単独作業（CON-3）とは相性がよい
- **X. Other（自由記述）**

[Answer]: B（2026-08-08T17:48:23Z, **Mode:** guided）

---

## Q5. Construction worktree の base / target ブランチ

`org.md` の `## Way of Working` は trunk-based で base / target とも **`main`** を既定とする。しかしこのリポジトリの既定ブランチは **`master`**、かつ `requirements.md` CON-3 は「作業は **v2.0** ブランチ上でローカルのみ、`git push` しない。リモートは v1.6.1 のまま」と定める。practices-discovery が SKIP のため team.md / project.md は空で、org.md の既定がそのまま効いてしまう。

- **A. base / target とも `v2.0`。squash-merge。push しない** — CON-3 に従い、`org.md` の `main` は当該リポジトリでの読み替えとして扱う
- **B. base / target とも `v2.0`。merge commit を残す（squash しない）** — Bolt ごとの中間コミットを v2.0 上に残す
- **C. worktree を使わず、v2.0 上で直接作業する** — Bolt ごとのブランチ分離を行わない
- **X. Other（自由記述）**

[Answer]: A（2026-08-08T17:48:23Z, **Mode:** guided）

---

## Q6. 外部依存・ゲート項目

未解決事項のうち、Bolt の進行を止めうるものが 2 件ある。

| 項目 | 内容 | 影響する Bolt |
|---|---|---|
| OQ-6 / A-4 | FR-8.3 で `UserDaoTest2`（Derby / `javadb`）を実行対象に加えると、ビルドに **Derby 依存の追加**が必要になる。「実 DB エンジンに対する検証がない」現状は解消されるが、ビルドの前提条件が増える。許容可否は未確認 | Build and Test（3.6）。ADR-007（NUMERIC に `setString`）の検証機会でもある |
| OQ-7 / CON-8 | CodeQL ワークフローは `master` への push / PR と週次 cron でのみ動作する。**v2.0 では CI が走らない**ため、SM-3 / NFR-2 の測定にはブランチ設定の変更か手動実行が必要 | Build and Test（3.6） |

これらをどう扱うか（複数選択可）。

- **A. Derby 依存の追加を許容する** — FR-8.3 / OQ-6 を解決し、実 DB 検証を得る
- **B. Derby は保留し、`UserDaoTest2` は実行対象に加えない** — FR-8.3 を部分達成とし、OQ-6 を Build and Test に残す
- **C. CodeQL を手動実行して NFR-2 を測る** — ワークフロー設定は変えず、必要時に手動でトリガする
- **D. CodeQL のブランチ設定を v2.0 にも広げる** — ただし CON-3（push しない）と衝突するため、実行はローカル CLI に限る
- **E. NFR-2 の測定自体を Build and Test まで判断保留にする** — 本ステージでは外部依存として記録するだけ
- **X. Other（自由記述）**

[Answer]: X（Other）— 「Derby はリタイアしているため認められない。H2DB を使うのはどうか？」（2026-08-08T17:48:23Z, **Mode:** guided）。選択肢 A（Derby 依存の許容）は却下。代替として H2 を検討する。CodeQL 側（C / D / E）は未決のため **FQ-2** で確定する

---

## Q7. Construction の設計ステージの反復順序

Construction の設計ステージ（3.1 Functional Design、3.2 NFR Requirements）を Unit 横断で回すか、Unit ごとに回すか。ゲートの回数は同じで、**タイミングだけが変わる**。

- **A. stage-major（既定）** — 3.1 を全 Unit ぶん書き、そのゲートを通してから 3.2 を全 Unit ぶん書く
  - 帰結: 各ステージの成果物を横並びで比較しやすい。ゲートはステージ単位で早めに来る
- **B. unit-major** — 1 つの Unit の 3.1 と 3.2 を続けて書き、次の Unit に移る
  - 帰結: 1 Unit の設計が一貫した形で揃う。walking-skeleton-first や Bolt ごとの縦切りと相性がよい。ゲートは設計ブロックの最後にまとめて来る（1 ステージ 1 承認は変わらない）
- **X. Other（自由記述）**

[Answer]: B（unit-major）（2026-08-08T17:48:23Z, **Mode:** guided）

---

## 追加設問

### FQ-1. Q3（リスクの大きい順）と Q1（U3 ＋ U4 を同一 Bolt）の関係

Q3 = A は「リスクの大きい U3 write-path を先にする」を選んだ。しかし Q1 = D は
**U3 と U4 を同じ Bolt 3 に束ねる**ため、両者の間に Bolt 境界がなく、
リスク順の判断は Bolt 3 の**内部**でしか働かない。U3 が抱える最大のリスク
（値の並び順ずれ——例外を出さずに誤った値が書き込まれる UPDATE）は、
Bolt 3 のゲートで U4 と一緒に承認されることになる。

- **A. Bolt 境界は Q1 = D のまま（4 Bolt）。Bolt 3 の内部を U3 → U4 の順で進める**
  - 帰結: Bolt 数は 4。リスク順は Bolt 内の作業順として効く。U3 単独のゲートはない
- **B. Bolt 3 を割って 5 Bolt にする** — Bolt1=skeleton / Bolt2=U1＋U2 の残り / Bolt3=U3 / Bolt4=U4 / Bolt5=U5
  - 帰結: 最も危険な失敗様式を抱える U3 が独立したゲートを持つ。Bolt 数は 5 に増える
- **X. Other（自由記述）**

[Answer]: A（2026-08-08T17:48:23Z, **Mode:** guided）

---

### FQ-2. 実 DB 検証基盤（Q6 = X の具体化）と CodeQL の測定方法

Q6 で Derby は却下された（リタイア済みのため）。代替として H2 が提案された。
判断に必要な事実を実測したので、それを踏まえて確定する。

**実測した事実**

| 項目 | 事実 |
|---|---|
| ビルド / テスト JVM | **JDK 25**（Corretto 25.0.1）、Maven 3.9.11。`source/target 1.8` のまま `mvn compile` は BUILD SUCCESS |
| pom.xml の Derby | **依存が 1 つも宣言されていない。** `UserDaoTest2` は Derby の有無以前に現状動かない |
| `db.xml` の `javadb` bean | ドライバは `org.apache.derby.jdbc.ClientDriver`（ネットワーク版）だが URL は `jdbc:derby:db/sample;create=true`（組み込み版の形式）。依存を足しても接続できない設定 |
| test スコープの前例 | `mysql-connector-j` 9.6.0 / `easymock` 5.1.0 / `logback` がすでに test スコープで宣言済み |

**CON-1 / NFR-4（Java 8）との関係**: これらが縛るのは**生成物**（`src/main` のバイトコードと使用 API）であり、test スコープの依存には及ばない。ビルド JVM が 25 であるため、Java 11+ を要求する H2 2.x も test スコープなら使える。CON-6 が禁じているのも「**compile スコープ**の第三者依存」であり、test スコープはその対象外である。

**H2 を選ぶ場合の副次的な効果**: 本ライブラリは MySQL / Oracle の方言クラスを持つが、H2 は**汎用フォールバック経路**（`Dao` / `Search`）を通る。これは FR-5.1 が求める 3 経路のうち汎用経路を実 DB で検証することになる。さらに、H2 は型に厳格であるため **ADR-007（NUMERIC / FLOAT にも `setString` を使う）の妥当性が実際に試される**。ADR-007 は「実 DB エンジンに対する検証が必要である」と自ら Negative に記録しており、H2 はその検証の場として Derby より適している。ただし H2 が `setString` を NUMERIC 列に対して拒否した場合、**ADR-007 の見直しが必要になる**——これはリスクであると同時に、この検証を入れる目的そのものでもある。

- **A. H2 を test スコープで追加し、`javadb` bean を H2 の組み込み設定に置き換える** — `org.h2.Driver` ＋ `jdbc:h2:mem:...`。`UserDaoTest2` を FR-8.3 の実行対象に含める
- **B. H2 を追加するが、`javadb` bean は残したまま H2 用の bean を別 id で足す** — 既存設定を壊さない
- **C. 実 DB 検証は今回入れない** — `UserDaoTest2` は実行対象に加えず、FR-8.3 を部分達成（`QueryImplTest02` / `QueryImplTest03` のみ）とし、ADR-007 の実 DB 検証を未解決のまま残す
- **X. Other（自由記述）**

[Answer]: A（2026-08-08T18:04:16Z, **Mode:** guided）

### FQ-3. CodeQL（NFR-2 / SM-3）の測定方法

CON-8 のとおり CodeQL ワークフローは `master` への push / PR と週次 cron でのみ動作し、v2.0 では走らない。CON-3 は `git push` を禁じている。

- **A. CodeQL CLI をローカルで手動実行する** — ワークフロー設定もブランチ設定も変えない。CON-3 と衝突しない
- **B. 測定方法の確定を Build and Test（3.6）まで保留する** — 本ステージでは外部依存として記録するだけ
- **C. ワークフローのブランチ設定を v2.0 にも広げる** — ただし push しない限り発火しないため、実効性は A と変わらない
- **X. Other（自由記述）**

[Answer]: A（2026-08-08T18:04:16Z, **Mode:** guided）

---

## Consolidated Summary Confirmation

| 設問 | 回答 | 意味 |
|---|---|---|
| Q1 | D | skeleton ＋ 依存の段（**4 Bolt**） |
| Q2 | A | skeleton は最小 — STRING の EQUAL 1 種を端から端まで |
| Q3 | A | skeleton 以降はリスクの大きい順（U3 write-path を先に） |
| Q4 | B | Bolt は逐次実行 |
| Q5 | A | worktree の base / target とも `v2.0`、squash-merge、push しない |
| Q6 | X | Derby は却下（リタイア済み）。代替を FQ-2 で確定 |
| Q7 | B | Construction の設計ステージは **unit-major** |
| FQ-1 | A | Bolt 境界は 4 のまま。リスク順は Bolt 3 内の作業順として効かせる |
| FQ-2 | A | **H2 を test スコープで追加**し、`javadb` bean を H2 の組み込み設定に置き換える。`UserDaoTest2` を FR-8.3 の実行対象に含める |
| FQ-3 | A | CodeQL は **CLI をローカルで手動実行**。ワークフロー設定もブランチ設定も変えない |

**プロンプト**: この内容で成果物を生成してよいか。
**選択肢**: Looks correct / Request changes

[Answer]: Looks correct（2026-08-08T18:05:21Z, **Mode:** guided）

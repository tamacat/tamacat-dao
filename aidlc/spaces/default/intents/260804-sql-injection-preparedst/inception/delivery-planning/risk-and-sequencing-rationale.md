# Risk and Sequencing Rationale — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

- **`unit-of-work-dependency.md`**（units-generation, 2.7）— 依存 DAG と「Unit をまたぐ不変条件」。本文書は、その DAG が許す複数の経路のうちどれを選んだか、そしてなぜかを記録する。
- **`unit-of-work.md`**（同上）— 各 Unit が抱えるリスクの記述（「この Unit が抱える最大の設計リスク」）。本文書のリスク登録簿はこれを Bolt 単位に集約したものである。
- **`unit-of-work-story-map.md`**（同上）— AC の Unit への割り当て。各リスクの検出手段が AC のどれに対応するかを引く。
- **`components.md`**（application-design, 2.6）— 変更対象コンポーネントとその境界。
- **`requirements.md`**（requirements-analysis, 2.3）— FR / NFR / AC / OQ。とくに未解決 OQ がリスクの源になる。
- **`stories.md`**（user-stories, 2.4）/ **`mockups`**（refined-mockups, 2.5）/ **`team-practices`**（practices-discovery, 2.2）— いずれも本スコープで **SKIP** のため存在しない。順序判断にユーザー価値の優先度づけ（ストーリー単位の WSJF）を使えないため、後述のとおり別の基準を採った。

---

## 採用した順序ヒューリスティック

**walking-skeleton-first（Cockburn, *Crystal Clear*）＋ risk-first（Boehm, Spiral Model）のハイブリッド。**

- **Bolt 1 は walking skeleton**（scope の `skeleton: on`、`scope-document.md`「Sequencing Approach」）
- **Bolt 2 以降は risk-first**（Q3 = A）

**WSJF / CD3（Reinertsen）は採らなかった。** WSJF は「user-business value ＋ time criticality ＋ risk-reduction value ÷ job size」で並べるが、本取り組みには **value と time criticality を分ける材料がない**——5 つの Unit はすべて同一の成功指標（SM-1: 値の文字列連結ゼロ）に寄与し、そのうち 4 つは Must で、どれか 1 つでも欠ければ SM-1 は達成されない。value 側が定数になる以上、WSJF は実質的に「risk-reduction ÷ job size」に退化し、risk-first と同じ順序を出す。それを WSJF と呼ぶのは根拠を装飾するだけなので、素直に risk-first と記録する。

**value-first も採らなかった。** 同じ理由——利用者にとっての価値は「SQL インジェクションのリスクが解消されること」の 1 点であり、部分的な達成に部分的な価値がない。むしろ半分だけ移行した状態は、**誤った安心を生む**という意味で無移行より悪い（scope 定義がこの点を明示している）。

---

## Bolt 順序と、その根拠

| 順 | Bolt | 選んだ理由 |
|---|---|---|
| 1 | `walking-skeleton` | アーキテクチャの成立を最小コストで証明する。ここが通らなければ残り 3 Bolt の前提が崩れる |
| 2 | `read-path-complete` | 土台（U1）を完成させないと U3 / U4 が着手できない（DAG の制約）。加えて、読み取り経路は `Search` の 3 状態同期という**構造的な**リスクを持ち、これを先に片付けないと Bolt 3 のデバッグに混入する |
| 3 | `write-path-and-dialects` | **本取り組みで最も危険な失敗様式**（値の並び順ずれ）を抱える U3 を、Should の U5 より先に置く |
| 4 | `identifier-safety` | Should Have。かつ設計が未完（機構が 3.1 で決まる）。落とす判断がありうる唯一の Bolt であり、最後に置くことで他の Bolt がその判断に影響されない |

### Bolt 3 の内部順序 — U3 を U4 より先に

Q3 = A（リスクの大きい順）を Bolt 内の作業順として適用する。

| Unit | リスクの重さ | 理由 |
|---|---|---|
| U3 `write-path` | **最重** | 値の並び順ずれが例外を出さずに通過する（後述の R-1） |
| U4 `dialects` | 中 | `OracleDao.searchListForOracle` の有効化は観測可能な挙動変更だが、失敗は可視である |

### 逸脱の記録

**トポロジカル順序からの逸脱はない。** Bolt 順序は DAG が許す経路の 1 つに完全に収まっている（`bolt-plan.md`「DAG 適合の検証」）。

**ただし DAG が許す並行性を使っていない。** `unit-of-work-dependency.md` は `{U3, U4, U5}` を同時に着手できると記録しているが、Q4 = B により逐次とした。理由：

- CON-3 により作業はローカル単独である。並行実行の主な利得は複数の実行者が同時に進むことだが、実行者は 1 系統しかいない（`team-allocation.md`）
- 並行バッチは失敗時に「どの Bolt が壊したか」の切り分けを増やす。R-1（値の並び順ずれ）は**症状が出ない**種類の失敗であり、切り分けの難度が上がる状況を作りたくない
- 3 つの worktree を同時に扱うと、squash-merge の順序と衝突解決が計画外の判断を呼ぶ

**この逸脱は可逆である。** 並行に切り替えたくなった時点で、DAG がそれを許すことは `unit-of-work-dependency.md` に記録済みである。

---

## リスク登録簿

### R-1. UPDATE のバインド値が 1 つずつずれる（症状が出ない）

| 項目 | 内容 |
|---|---|
| Bolt | **3**（U3） |
| 可能性 | **高** — 素朴な実装（値リストを 1 本にして生成順に append）が自然に導く誤り |
| 影響 | **Critical** — 誤った値が誤ったカラムに書き込まれた UPDATE が**正常終了する**。データ破壊が静かに起こる |
| 機序 | `QueryImpl.getUpdateSQL`（`:236-274`）は SET 句と WHERE 句を単一ループで組み立てるが、テキストは SET → WHERE の順で連結する。ライブラリ自身のフィクスチャでは主キーが `updateColumns` の 1 番目（`User.java:23-24`、`DefaultTable.java:16` の `LinkedHashSet`）であるため、WHERE の値が SET の値より先に生成される |
| なぜ検査を通り抜けるか | `?` の個数（5）と値の個数（5）が一致するため、`PreparedSql.of(...)` の生成時検査も JDBC のパラメータ数検査も通る |
| 緩和策 | (1) ADR-011 の規則——値リストはテキストアキュムレータと 1 : 1 で持ち、テキストの連結順に結合する——を実装の構造として守る。(2) **Bolt 1 で mock の位置・値アサート基盤を先に作る**（これが唯一の検出手段）。(3) **AC-3b を Bolt 3 の Definition of Done に必ず含める**。(4) Build and Test（3.6）で「SET ＋ WHERE 混在 UPDATE」を必ずテストする |
| 検出手段 | **AC-3b のみ。** 個数検査では捕まらない |

### R-2. `Search` の 3 状態が同期を失い、述語が欠落する

| 項目 | 内容 |
|---|---|
| Bolt | **2**（U2） |
| 可能性 | 中 |
| 影響 | **High** — 述語が欠落した SELECT は、意図より多くの行を返す。WHERE が丸ごと消えれば全件返る |
| 機序 | `Search` は `search`（リテラル系）/ `bindSearch`（バインド系）/ `bindValues` の 3 状態を保持する。3 つのうち 1 つだけを変更する経路が 1 つでもできると崩れる |
| なぜ検査を通り抜けうるか | `Param.of(...)` の個数検査は「個数のずれ」を検出するが、「バインド系のテキストと値を**両方とも** append し忘れた」場合は個数が一致したまま述語が消える |
| 緩和策 | `search` / `bindSearch` / `bindValues` を直接触るのは単一の private メソッド `append(String literal, Param bind, String connector)` だけとし、`and` / `or` / `and(Search)` / `or(Search)` はすべてそれを経由する（`component-methods.md` M-3）。**検査ではなく構造で守る** |
| 検出手段 | AC-1 / AC-2 / AC-3（各条件種別の実行 SQL 検証）、既存 `SearchTest` の 12 件のアサート |

### R-3. H2 が NUMERIC 列への `setString` を拒否し、ADR-007 が崩れる

| 項目 | 内容 |
|---|---|
| Bolt | **3**（Build and Test 3.6 で顕在化） |
| 可能性 | 中 |
| 影響 | **High** — ADR-007（NUMERIC / FLOAT にも `setString` を使う）の見直しが必要になる。`PreparedStatementBinder` の setter 選択を作り直すことになり、Bolt 1 で作った土台に手が入る |
| 機序 | ADR-007 は「現行のリテラル経路は数値を引用符なしで埋め込み、型変換は DB エンジンが行っている。`setBigDecimal` に変えると現行と異なる型変換が起きうる」として `setString` を選んだ。しかし ADR-007 自身が Negative に「厳格な型チェックを行うドライバでは `setString` で渡した数値がエラーになる可能性がある」「**実 DB エンジンに対する検証が必要である**」と記録している |
| なぜ H2 で顕在化するか | H2 は型に厳格であり、現行の唯一の実行テスト基盤（`MockDriver`）は型を一切検証しない。FQ-2 = A により H2 を導入すること自体が、この検証を初めて実行することを意味する |
| 緩和策 | (1) H2 の導入を Bolt 3 より前倒しできないため、代わりに **Bolt 1 の時点で `PreparedStatementBinder` の setter 選択を 1 か所に閉じ込める**（`DataType` → setter の対応表を単一メソッドに集約）。これにより見直しが必要になっても変更範囲が最小で済む。(2) 顕在化した場合は ADR-007 を Superseded とし、`DataType` ごとに厳密な setter を使う代替 1 に切り替える |
| 検出手段 | `UserDaoTest2`（H2）の実行。FR-8.3 |

### R-4. `Sort` の識別子検証が既存利用を壊す（FR-1.7 が実施できない）

| 項目 | 内容 |
|---|---|
| Bolt | **4**（U5）。判明は Functional Design（3.1） |
| 可能性 | 中 |
| 影響 | 低 — FR-1.7 は **Should Have** であり、NFR-1 の対象範囲に含まれない。落としても SM-1 の達成に影響しない |
| 機序 | `Sort.sort(Object k, Object o)` の非 `Column` キー経路は、**意図的にメタデータ外の式を許すために存在する**。メタデータ照合を強制すると、その正当な用途が壊れる（FR-3.1 違反） |
| 緩和策 | Bolt 4 を最後に置き、他の Bolt がこの判断に影響されない構造にした。3.1 で両立できないと判明した場合、Bolt 4 を落として FR-1.7 を Won't Have にする |
| 検出手段 | 3.1 の設計判断。AC-10b と FR-3.1 の同時成立可否 |

### R-5. 静的解析（CodeQL）が残存するリテラル連結経路を指摘し、NFR-2 が達成できない

| 項目 | 内容 |
|---|---|
| Bolt | Build and Test（3.6） |
| 可能性 | 中 |
| 影響 | 中 — SM-3 / NFR-2 の判定に直結する |
| 機序 | ADR-003 により、旧 API の戻り値を変えない代わりに **`SQLParser` のリテラル生成コードが残り、非推奨の public メソッド経由で到達可能なままになる**。CodeQL はこの経路を指摘しうる |
| 緩和策 | (1) 本ステージで `requirements.md` の NFR-1 の判定を「**実行経路**に値の文字列連結が 0 件」に更新済み（units-generation 2.7 で反映）。(2) 指摘が出た場合、この経路が実行に到達しないことを示すか、指摘を抑制する。判断は 3.6 |
| 検出手段 | CodeQL CLI のローカル手動実行（FQ-3 = A） |

### R-6. 未解決の OQ が Construction 中に判断を要求する

| 項目 | 内容 |
|---|---|
| Bolt | 全般 |
| 可能性 | 高（設計上、意図的に残された未解決事項があるため） |
| 影響 | 低〜中 |
| 内訳 | **OQ-9 の残り**（FR-1.7 の機構）→ 3.1 で決定、Bolt 4。**OQ-4**（カバレッジ 80% の適用可否と計測手段）→ 3.2 NFR Requirements。**OQ-8**（利用アプリ側の監査指摘と SM-3 の測定対象の同一性）→ 未定、本取り組みでは解けない |
| 緩和策 | いずれも解消先ステージが特定済みであり、Bolt の進行を止めない。OQ-8 のみ本取り組みの外にあるため、`intent-statement.md` の A-2 として未解消のまま残ることを受け入れる |

---

## リスクと緩和策の対応表

`project.md` の学びに従い、列挙したリスクすべてに緩和策が割り当たっているかを照合する。

| リスク | 重大度 | 緩和策 | 割り当て済みか |
|---|---|---|---|
| R-1 UPDATE の値ずれ | **Critical** | ADR-011 の構造的遵守 ＋ Bolt 1 の mock 基盤 ＋ AC-3b を DoD に ＋ 3.6 で混在ケース必須 | ✅ 4 件 |
| R-2 `Search` の 3 状態 | High | 単一 private `append(...)` に閉じ込める（構造で守る） | ✅ |
| R-3 H2 の型厳格性 | High | setter 選択を Bolt 1 で 1 か所に集約 ＋ 顕在化時は ADR-007 を Superseded にして代替 1 へ | ✅ 2 件 |
| R-4 `Sort` の既存利用破壊 | 低 | Bolt 4 を最後に置く ＋ 両立不能なら Won't Have に落とす | ✅ |
| R-5 CodeQL の指摘 | 中 | NFR-1 の判定更新（実施済み） ＋ 3.6 で到達不能性の提示か抑制 | ✅ |
| R-6 未解決 OQ | 低〜中 | 解消先ステージが全件特定済み（OQ-8 を除く） | ✅ |

**未割当のリスクはない。** R-1 のみ緩和策を 4 重にしているのは、これが唯一「症状が出ない」種類の失敗であり、1 つの防御が抜けたときに気づく手段がないためである。

---

## 順序判断が変わりうる条件

本計画の順序は、次のいずれかが起きた場合に見直す。

| 条件 | 見直しの内容 |
|---|---|
| Bolt 1 で mock 拡張が想定より重いと判明した | walking skeleton の範囲を Q2 = A よりさらに狭める（例: `DBAccessManager` の記録機能を Bolt 2 へ送る） |
| Bolt 2 で `Search` の 3 状態同期が構造的に守れないと判明した | ADR-003（旧 API の戻り値を変えない）の再検討。リテラル系を落とせば 3 状態が 2 状態になる |
| 3.1 で `Sort` の検証が FR-3.1 と両立しないと判明した | Bolt 4 を削除し、FR-1.7 を Won't Have に落とす |
| H2 が ADR-007 を崩した | Bolt 3 の完了条件に「setter 選択の作り直し」が加わる。Bolt 1 の成果物に手が入るため、Bolt 1 の再検証が必要になる |

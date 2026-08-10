# Team Allocation — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— Unit U1〜U5 と複雑度。担当の割り当てはこの単位に対応する。
- **`unit-of-work-dependency.md`**（同上）— 並行可能な組。本計画では並行を使わないため、同時に稼働する担当は常に 1 つである。
- **`unit-of-work-story-map.md`**（同上）— 各 Unit が持つ FR / AC。担当が何を完了条件とするかはここから引く。
- **`components.md`**（application-design, 2.6）— 変更対象コンポーネント。必要な技能の判断材料。
- **`requirements.md`**（requirements-analysis, 2.3）— CON-3（ローカル単独作業）。人的な体制を組まない根拠。
- **`stories.md`**（user-stories, 2.4）/ **`mockups`**（refined-mockups, 2.5）— 本スコープで **SKIP** のため存在しない。担当の割り当てに UI / ストーリー観点は含まれない。
- **`team-practices`**（practices-discovery, 2.2）— 同じく **SKIP**。チーム編成に関する affirmed practice は存在せず、`org.md` の既定に従う。
- **team-formation（1.5）** — 本スコープで **SKIP**。人間のチームは編成されていない。

---

## 割り当ての前提

team-formation（1.5）が SKIP であるため、参照できるチーム定義が存在しない。`delivery-planning` ステージ定義の規定に従い、**全 Bolt を `aidlc-developer-agent`（AI）が実行する**。

これは省略ではなく、本取り組みの性質に対応した結果である。

| 事実 | 出典 |
|---|---|
| 作業は v2.0 ブランチ上でローカルのみ、`git push` しない。リモートは v1.6.1 のまま | `requirements.md` CON-3 |
| 対象は tamacat-dao ライブラリ内の SQL 生成／実行経路のみ。利用側アプリケーションは対象外 | CON-4 / OOS-1 |
| デプロイ可能なプロセスを持たず、Operation フェーズは全ステージ SKIP | `services.md`、`aidlc-state.md` |

チーム数が 1（実体としては AI 実行者 1 系統）であるため、**Program Board 相当の調整表は作らない**。複数チーム間のハンドオフも、チーム間の依存待ちも発生しない。

---

## Bolt と担当

| Bolt | 内容 | 担当 | 同時稼働 |
|---|---|---|---|
| 1 `walking-skeleton` | U1 / U2 の縦切り（STRING の EQUAL 1 経路） | `aidlc-developer-agent` | 1 |
| 2 `read-path-complete` | U1 の残り ＋ U2 の残り | `aidlc-developer-agent` | 1 |
| 3 `write-path-and-dialects` | U3（先）→ U4（後） | `aidlc-developer-agent` | 1 |
| 4 `identifier-safety` | U5（Should） | `aidlc-developer-agent` | 1 |

**逐次実行（Q4 = B）のため、同時に稼働する担当は常に 1 つである。** `unit-of-work-dependency.md` は `{U3, U4, U5}` の並行を許すが、本計画はそれを使わない。したがって worktree も同時に 1 つしか存在しない。

---

## 各 Bolt に必要な技能

人的な割り当てはないが、各 Bolt が要求する知識の性質は Bolt ごとに異なる。レビューの観点を定めるために記録する。

| Bolt | 要求される知識 | レビューで重点的に見るべき点 |
|---|---|---|
| 1 | JDBC の `PreparedStatement` 契約（1 始まりのパラメータ位置）、不変オブジェクトの設計、JUnit での mock 拡張 | 層を飛ばしていないか。`?` の個数と値の個数の整合検査が生成時に効いているか |
| 2 | 現行 `SQLParser` の LIKE エスケープ規則（`:119-139`）の完全な理解、Java 8 の `default` メソッドの互換性 | `SQLParser` の戻り値が 1 文字も変わっていないか。`Search` の 3 状態が単一の private メソッドを経由しているか |
| 3 | `QueryImpl.getUpdateSQL`（`:236-274`）のループ構造と `updateColumns` の反復順、JDBC のバイナリストリーム、Oracle の rownum ページング | **SET ＋ WHERE 混在 UPDATE の値の並び順**（AC-3b）。ここが本取り組み全体で最も危険な失敗様式 |
| 4 | 識別子の検証設計、`Sort` の既存利用（意図的にメタデータ外の式を許す用途） | 既存の正当な `Sort` 利用を壊していないか（FR-3.1） |

---

## 人間の関与点

AI が実行するが、判断を人間に返す点が計画上 4 か所ある。

| # | タイミング | 内容 |
|---|---|---|
| 1 | Bolt 1 のゲート | walking skeleton の承認。`skeleton: on` により autonomy mode に関わらず必ず提示される |
| 2 | Bolt 1 承認の直後 | ladder prompt —「残りの Bolt をどう走らせるか」（自律実行 / 毎 Bolt ゲート）。回答は `Construction Autonomy Mode` として状態に記録される |
| 3 | Bolt 2 以降の各ゲート | `Construction Autonomy Mode: gated` を選んだ場合のみ |
| 4 | Bolt が失敗したとき | **autonomy mode に関わらず必ず停止して確認する**（retry / skip / abort）。自律モードで人間に戻る唯一のケース |

加えて、計画外だが起こりうる判断が 2 件ある。

| 事象 | 判断 |
|---|---|
| Functional Design（3.1）で `Sort` の識別子検証が FR-3.1 と両立しないと判明した場合 | Bolt 4 を落として FR-1.7 を Won't Have にするか、既存利用を壊す変更を許容するか |
| H2 が NUMERIC 列への `setString` を拒否した場合 | ADR-007（NUMERIC / FLOAT にも `setString`）の見直し。詳細は `external-dependency-map.md` |

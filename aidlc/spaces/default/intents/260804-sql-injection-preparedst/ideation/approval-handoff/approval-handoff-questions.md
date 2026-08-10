# Initiative Approval & Handoff — Questions

## 上流成果物

本ステージは IDEATION フェーズの全成果物を集約する。

| 成果物 | 状態 |
|--------|------|
| `intent-statement.md`（intent-capture） | 承認済み。問題定義、成功指標 SM-1〜3、プロダクト境界、各制約 |
| `stakeholder-map.md`（intent-capture） | 承認済み。ステークホルダー2者、意思決定者、コミュニケーション要件 CR-1/CR-2 |
| `scope-document.md`（scope-definition） | 承認済み。能力 C-1〜C-9、Out of Scope O-1〜O-3、Value Stream Map |
| `intent-backlog.md`（scope-definition） | 承認済み。proto-Unit PU-1〜PU-9、Walking Skeleton 候補 |
| `competitive-analysis.md`（market-research） | 不在 — 本スコープで 1.2 が SKIP |
| `feasibility-assessment.md` / `constraint-register.md`（feasibility） | 不在 — 本スコープで 1.3 が SKIP |
| `team-assessment.md`（team-formation） | 不在 — 本スコープで 1.5 が SKIP |
| `wireframes.md`（rough-mockups） | 不在 — 本スコープで 1.6 が SKIP |

ステージ定義が挙げる質問のうち、市場調査の妥当性・モックアップの適合・モブの人員配置に関するものは、対応する成果物が存在しないため本ステージでは問わない。

---

## Q1. 意図とスコープに合意していますか？

IDEATION で確定した内容: 「tamacat-dao の SQL 生成／実行経路のみを対象に、値が入る位置すべてをバインド変数化する。public API の後方互換性は維持する。Java 8 のまま、ローカルのみで作業する」

- A. 合意している — このまま INCEPTION へ進む
- B. 意図に修正が必要（内容を記述してください）
- C. スコープに修正が必要（内容を記述してください）
- X. Other (please specify)

[Answer]: A. 合意している — このまま INCEPTION へ進む
**Mode:** guided | **Timestamp:** 2026-08-05T00:10:00Z

---

## Q2. 以下のうち、重大リスクとして認識しているものはどれですか？（複数選択可）

- A. **設計が行き詰まるリスク** — public API の後方互換性を維持したまま、SQL 組み立ての全経路をバインド変数化できない可能性。両立可否は未検証で、Application Design（2.6）で判明する
- B. **回帰ネットが弱いリスク** — 既存テストが「生成された SQL 文字列」をアサートしている場合、実装変更と同時にテストも書き換わるため、回帰の検出力が期待より低くなる
- C. **特殊な位置の取りこぼしリスク** — LIKE のワイルドカードエスケープや、方言固有の既存プレースホルダなど、単純な値位置とは異なる扱いが必要な箇所を見落とす
- D. **部分移行のリスク** — 一部の経路だけがバインド変数化され、残りが文字列連結のまま残ると、実際には脆弱なのに安全だと誤認する
- E. 上記はいずれも重大とは考えない
- X. Other (please specify)

[Answer]: A, B, C, D（4 件すべてを重大リスクと認識）
**Mode:** guided | **Timestamp:** 2026-08-05T00:10:00Z

---

## Q3. リスクへの対処方針として、どれを採りますか？（複数選択可）

- A. 最初の 1 経路（Walking Skeleton）を通した時点で、承認ゲートで結果を確認してから残りに進む — 既にスコープで決まっている方針を、リスク緩和として明示的に位置づける
- B. Application Design（2.6）で、API 互換性と全経路バインド変数化の両立可否を最優先で検証し、両立しない場合は API 方針の見直しをその場で判断する
- C. 既存テストの回帰検出力を確認し、必要なら「バインド値をアサートするテスト」を先に追加してから実装を変更する
- D. 完了判定に静的解析（CodeQL）の指摘ゼロを含め、取りこぼしを機械的に検出する
- E. 特段の緩和策は設けない
- X. Other (please specify)

[Answer]: 初回 A, D → Q5 により B, C を追加。最終: A, B, C, D（4 件すべて）
**Mode:** guided | **Timestamp:** 2026-08-05T00:10:00Z / 追加分 2026-08-05T00:14:00Z

---

## Q4. 時間・リソースのコミットメントはありますか？

- A. 特に制約はない — 期限なし、単独作業、着手可能
- B. 使える時間に上限がある（内容を記述してください）
- C. 他の作業と並行するため、中断が入る前提で進めたい
- X. Other (please specify)

[Answer]: A. 特に制約はない — 期限なし、単独作業、着手可能
**Mode:** guided | **Timestamp:** 2026-08-05T00:10:00Z

---

## 回答分析（Answer analysis）

Q2 で 4 件すべてが重大リスクと認識されたのに対し、Q3 の初回回答は A・D の 2 件のみだった。リスクと緩和策の対応を照合したところ:

| リスク | 初回の緩和策 | 状態 |
|--------|--------------|------|
| A. 設計が行き詰まる | Q3-A が部分的にカバー（1 経路通した時点で両立可否の一端が見える）。明示的な検証策 Q3-B は未選択 | 部分カバー |
| B. 回帰ネットが弱い | 該当なし。Q3-C（テストを先に強化）は未選択 | **未カバー** |
| C. 特殊な位置の取りこぼし | Q3-D（静的解析を完了判定に） | カバー済み |
| D. 部分移行のリスク | Q3-A（1 経路通してから確認）＋ Q3-D | カバー済み |

未選択の選択肢を「不要と決定された」とは扱わないため、Q5 で 1 回だけ確認した。

---

## Q5（フォローアップ）. リスク A・B に緩和策が割り当たっていません。どうしますか？

- A. Q3-B を追加 — Application Design（2.6）で API 互換性と全経路バインド変数化の両立可否を最優先で検証する（リスク A 向け）
- B. Q3-C を追加 — 既存テストの回帰検出力を確認し、必要ならバインド値をアサートするテストを先に追加する（リスク B 向け）
- C. 追加しない — Q3-A・D で実質的にカバーされると考える
- D. 後続ステージに委ねる — 緩和策の確定は 2.6 / 3.2 で行い、ここでは未割り当てとして記録する
- X. Other (please specify)

[Answer]: A および B（Q3-B と Q3-C の両方を追加）
**Mode:** guided | **Timestamp:** 2026-08-05T00:14:00Z
**結果:** リスク A〜D の 4 件すべてに緩和策が割り当たった。

---

## Consolidated Summary Confirmation

**Prompt:** これで内容は正しいですか？（成果物を生成する前の最終確認）

- A. Looks correct — この回答内容で成果物（initiative-brief.md / decision-log.md / フェーズ境界検証）を生成する
- B. Request changes — 生成前にひとつ以上の回答を修正する

[Answer]: A. Looks correct
**Mode:** guided | **Timestamp:** 2026-08-05T00:18:00Z

---

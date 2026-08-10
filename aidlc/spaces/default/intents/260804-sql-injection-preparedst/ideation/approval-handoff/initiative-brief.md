# Initiative Brief — SQL パラメータ化（PreparedStatement 化）

**対象:** tamacat-dao v2.0 / **スコープ:** `sql-parameterization` / **フェーズ:** IDEATION 完了、INCEPTION 引き渡し

## Intent and Problem Statement

`intent-statement.md` より。tamacat-dao の SQL 組み立てが文字列連結によって行われており、そこに起因する SQL インジェクション脆弱性を除去する。併せて、本ライブラリを利用しているアプリケーション側で挙がるセキュリティ監査／診断の指摘に対応できる状態にする。解決手段は PreparedStatement を用いた実装への変更として、依頼時点で指定されている。

`stakeholder-map.md` より。ステークホルダーは開発者本人のみで、実装品質と後方互換性の両方に責任を持つ。人間のセキュリティ監査担当者は存在せず、「指摘元」は tamacat-dao 本体に対する自動静的解析（CodeQL ワークフロー）である。意思決定者は開発者本人、影響者はいない。

## Market Validation Summary

該当なし。市場調査ステージ（1.2）は本スコープで SKIP されており、`competitive-analysis.md` は存在しない。利用者が開発者自身のプロジェクトに限られ、外部利用者がいないため、市場の観点からの投資判断は本イニシアチブの成否に関与しない。

## Feasibility and Risk Highlights

feasibility ステージ（1.3）は本スコープで SKIP されており、`feasibility-assessment.md` と `constraint-register.md` は存在しない。制約は `intent-statement.md` の `## Initial Scope Signal` から引き継いだ。

**制約:**

| # | 制約 | 出典 |
|---|------|------|
| 1 | public API の後方互換性を維持する（破壊的変更はしない） | `intent-statement.md` |
| 2 | Java 8 のまま維持する | `intent-statement.md` |
| 3 | v2.0 ブランチで、当面ローカルのみで作業し git push しない | `intent-statement.md` |

**リスクと緩和策:** 4 件のリスクすべてが重大と認識され、すべてに緩和策が割り当たっている。 [Q2] [Q3] [Q5]

| # | リスク | 影響 | 緩和策 |
|---|--------|------|--------|
| R-1 | public API の後方互換性を維持したまま全経路をバインド変数化できない可能性。両立可否は未検証 | Critical — 成立しなければ制約 1 か SM-1 のいずれかを見直すことになる | Application Design（2.6）で両立可否を最優先で検証し、両立しない場合は API 方針の見直しをその場で判断する |
| R-2 | 既存テストが「生成された SQL 文字列」をアサートしている場合、実装変更と同時にテストも書き換わり、回帰の検出力が期待より低い | High — SM-2 の達成が見かけ倒しになる | 既存テストの回帰検出力を確認し、必要ならバインド値をアサートするテストを先に追加してから実装を変更する |
| R-3 | LIKE のワイルドカードエスケープや方言固有の既存プレースホルダなど、単純な値位置と異なる扱いが必要な箇所の見落とし | High — 見落とした経路が脆弱なまま残る | 完了判定に静的解析（CodeQL）の指摘ゼロを含め、取りこぼしを機械的に検出する |
| R-4 | 一部の経路だけがバインド変数化され、残りが文字列連結のまま残ると、実際には脆弱なのに安全だと誤認する | Critical — セキュリティ修正の目的そのものを損なう | 最初の 1 経路（Walking Skeleton）を通した時点で承認ゲートを置き、結果を確認してから残りに進む。加えて静的解析での指摘ゼロを完了判定に含める |

## Scope Boundary

`scope-document.md` および `intent-backlog.md` より。

**最小限の価値あるスコープ:** SQL の値が入る位置すべて — WHERE の比較値、LIKE、IN、INSERT / UPDATE の値 — がバインド変数になった時点。

**In Scope（9 能力）:** 単一値の比較条件 / LIKE 条件（ワイルドカードのエスケープを含む）/ IN 句・複数値条件 / INSERT・UPDATE の値 / BLOB・バイナリ値 / DB 方言ごとの差異（MySQL・Oracle・PostgreSQL）/ 既存テストの移行 / 静的解析での指摘ゼロ — 以上 8 件が Must Have。バインドできない位置（テーブル名・カラム名・ORDER BY 句）の注入不能化が Should Have（暫定）。

**Out of Scope:** 利用側アプリケーションの修正 / パフォーマンス最適化 / 新規 DB 方言のサポート追加。

**成功指標:** SM-1 全経路がバインド変数を使用 / SM-2 既存テストがグリーン維持 / SM-3 静的解析での SQL インジェクション指摘ゼロ（いずれも測定対象は tamacat-dao 本体）。

**バックログ:** `intent-backlog.md` が proto-Unit PU-1〜PU-9 を MoSCoW 付きで列挙している。Walking Skeleton 候補は PU-1（単一値の比較条件）。能力間の依存関係は未確定で、Application Design（2.6）が判断する。

## Concept Visuals

該当なし。rough-mockups ステージ（1.6）は本スコープで SKIP されており、`wireframes.md` は存在しない。UI を持たないライブラリの内部変更であるため、視覚的な概念図は本イニシアチブの判断材料にならない。`scope-document.md` の Value Stream Map が、意図から成功指標への流れを示す唯一の図である。

## Team Plan

team-formation ステージ（1.5）は本スコープで SKIP されており、`team-assessment.md` は存在しない。開発者本人の単独作業であり、モブ編成・スキルギャップ分析・エスカレーション経路の設計はいずれも該当しない。 [Q4]

時間・リソースの制約はない。期限は設定されておらず、着手可能な状態にある。 [Q4]

## Go / No-Go Recommendation

**推奨: Go（INCEPTION へ進む）**

根拠:

1. **意図とスコープが確定し、合意されている。** プロダクト境界、成功指標、制約、優先度がすべて明示され、ユーザーが合意している。 [Q1]
2. **重大リスク 4 件すべてに緩和策が割り当たっている。** 未カバーのリスクは残っていない。 [Q2] [Q3] [Q5]
3. **最大のリスク R-1 は、次フェーズの早い段階で判明する。** Application Design（2.6）が両立可否を最優先で検証する設計になっており、行き詰まりが判明した場合の損失は設計工数に限定される。実装に入る前に判断できる。
4. **着手を妨げる資源制約がない。** 期限なし、単独作業、ローカルのみ。 [Q4]

**留保事項:** R-1 が「両立不可」と判明した場合、`intent-statement.md` の API 互換性方針（public API の後方互換性を維持する）か SM-1（全経路バインド変数）のいずれかを見直す必要がある。これは v2.0 という破壊的変更が許容されうるバージョンであることを踏まえた再判断になる。2.6 の承認ゲートがその判断点となる。

## Assumptions & Open Questions

上流成果物から引き継いだ未解決事項。本ステージでは解決していない。

- [assumption] API 互換性の方針が SM-1 と両立するかは未検証。Application Design（2.6）で判断する。（`intent-statement.md` より — R-1 として本ブリーフのリスク表に反映済み）
- [assumption] Problem Statement が指す「利用アプリケーション側の監査／診断指摘」と、SM-3 が測定する「tamacat-dao 本体に対する静的解析」が同一かは未確認。SM-3 は Problem Statement 第2項の充足を直接には検証しない。（`intent-statement.md` / `stakeholder-map.md` より — Requirements Analysis 2.3 への申し送り）
- [assumption] PU-9（識別子位置の注入不能化）の MoSCoW 区分は Must Have でないことのみが確定しており、Should / Could / Won't のいずれかは未選択。暫定的に Should Have としている。（`scope-document.md` / `intent-backlog.md` より）
- [assumption] 能力間の依存関係は未確定。proto-Unit の並びは依存順ではなく分類順である。Application Design（2.6）が依存グラフを確定し、Delivery Planning（2.8）がそれを入力に Bolt 順序を決める。（`scope-document.md` / `intent-backlog.md` より）
- [assumption] SQL インジェクション以外のセキュリティ課題の扱いは範囲未確定。明示的な Won't Have にはせず、発見時に都度判断する。プロダクト境界は変更しない。（`scope-document.md` より）

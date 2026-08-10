<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-05T05:30:00Z — ブラウンフィールドの設計設問を作る前に、対象 public API の呼び出し元を src 全体で走査すべきだった; 初回の設問はユーザーに「一連の処理全体を見て判断が必要だが、確認しているのか？」と差し戻された。走査した結果、選択肢の前提が 2 か所で崩れていた（Dao は Query に到達できない / 正典的な使い方が対象外とした raw 経路を通る）。上流成果物（architecture.md, component-inventory.md）は構造を記述するが、誰が誰を呼ぶかまでは記述していない。
- 2026-08-05T05:30:00Z — 設計の選択肢は、実コードのシグネチャと before/after のコード片で示した; 「値を保持する型を導入」「public メソッド 2 つの追加」という抽象的な説明では「具体的にどのように対応しようと考えているのか伝わらないため、判断できない」と再度差し戻された。Java の制約（戻り値型だけのオーバーロードは不可）のような、実装レベルの事実が判断を左右する。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-05T05:30:00Z — レビュー反復上限（2 回）到達後に、NOT-READY の指摘 2 件を修正した; §12a は「上限到達時は未解決の指摘を明記してゲートへ」と定めるが、指摘（package-private では別パッケージから呼べない）は修正内容が一意に定まる欠陥だった。既知の壊れた設計をそのまま提示するより修正する方が有用と判断し、再検証を受けていない旨を成果物とゲートに明記した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-05T05:30:00Z — 承認済み要件（FR-7.1）への反証を見つけたとき、2.3 に戻さず ADR に記録して 2.7 までに更新する形を選んだ; 差分が 3 箇所に特定でき、下流に影響しないため。2.3 の再実行は questions / reviewer / learnings をすべて通し直すことになる。ユーザーが手続きを選べるよう、中身（バインド化するか）と手続き（どこで差分を正すか）を別の設問に分けた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-05T05:30:00Z — F-1 の修正（getSearchParam を public に）はレビュアーの再検証を受けていない; 反復上限に達した後の修正であるため。次に 2.6 を触るとき、または 2.7 のレビューで、この点を確認する必要がある。
- 2026-08-05T05:30:00Z — hasUnboundPlaceholders() の `?` 走査規則（引用符の外だけ数える）を 3.1 に持ち越した; 旧経路のリテラル SQL は値の中に `?` を含みうるため単純な数え上げでは誤判定する。完全な SQL パーサではないため、エスケープされた引用符の連続などで誤判定する余地が残る。

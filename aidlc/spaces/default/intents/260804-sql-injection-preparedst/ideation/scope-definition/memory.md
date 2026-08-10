<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-04T12:40:00Z — 能力を成果レベル（何が安全になるか）で記述し、クラス名・パッケージ構成などの実装詳細を成果物から排除した; intent-capture のレビュー（NOT-READY ×3）で「ideation 成果物に実装詳細を書くな」という指摘を繰り返し受けたため、本ステージでは最初から成果レベルに統一した。
- 2026-08-04T12:40:00Z — PU-9（識別子位置の注入不能化）の MoSCoW 区分を暫定 Should Have とした; Q3 は「Must ではない」ことのみを確定させ、Should / Could / Won't のどれかは選ばせていない。Q7 の Won't Have 一覧になく Q1 が最小スコープから除外していることから Should Have と推定したが、推定であることを `[assumption]` として明示した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-04T12:28:00Z — Q5（順序づけ方針）を、方法論の用語（risk-first / value-first / dependency-first / walking-skeleton-first）のまま提示して伝わらなかったため、案件固有の具体例に置き換えて再提示した; ユーザーの回答は「意味がわからない」。方法論用語をそのまま選択肢ラベルにするのは ideation ステージでは不親切。次回は最初から具体例で提示する。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-04T12:40:00Z — 優先度フレームワークに MoSCoW を選び、RICE / WSJF を採らなかった; 単独開発者・期限なし・利用者が自分自身という条件では Reach や Cost of Delay の入力値が意味のある差を生まないため。代替案は RICE で proto-Unit を序列化することだったが、入力値がすべて推測になり見かけの精度を生むだけと判断した。
- 2026-08-04T12:40:00Z — Must Have 7/8 という配分への押し返しを 1 回で止めた; product-guide.md は「すべてが Must Have なら優先順位づけができていない — 押し返せ」と指示するが、ユーザーは「部分的にパラメータ化された SQL 層は誤った安心を生む」という具体的な理由を添えて A を選んだ。理由が成立している以上、繰り返し押し返すのは指示への不服従にあたると判断した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

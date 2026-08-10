<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-05T00:22:00Z — ステージ定義が挙げる質問のうち、市場調査の妥当性・モックアップの適合・モブの人員配置に関するものを問わなかった; 対応する上流成果物（competitive-analysis / wireframes / team-assessment）が SKIP により存在しないため。存在しない成果物について質問すると、回答が推測になり成果物に根拠のない記述が混入する。
- 2026-08-05T00:22:00Z — Go/No-Go を質問にせず、initiative-brief の推奨として書き、承認ゲートを決定点とした; ステージ定義は brief に「Go/no-go recommendation」を含めることを求めており、ゲートが Approve / Request Changes / Reject Initiative の 3 択を持つため、質問にすると同じ決定を 2 回聞くことになる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-05T00:22:00Z — リスクと緩和策の対応表を作り、未カバーのリスクを機械的に検出して 1 回だけ確認した; Q2 で 4 件すべてが重大とされたのに Q3 の緩和策が 2 件しかなく、照合しなければ気づかない齟齬だった。代替案は「ユーザーが選ばなかったのだから不要」と解釈することだったが、未選択を「不要と決定された」と扱わない原則に反する。押し返しは 1 回に留めた。
- 2026-08-05T00:22:00Z — feasibility 裏付け検査を「不合格」ではなく「検査不能（スコープによる意図的な省略）」と判定した; verification.md は Ideation→Inception で feasibility backing を求めるが、1.3 はコンポーザーが理由付きで SKIP した。存在しない成果物の不在を失敗として数えると、スコープ合成そのものを否定することになる。代わりに、その最大論点が R-1 として緩和策付きで管理されている旨を明記した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

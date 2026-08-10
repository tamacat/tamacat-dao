<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-04T10:58:00Z — 質問文と選択肢を日本語で記述した; ユーザーの入力が日本語であり、構造マーカー（`## Sources`, `## Q<n>.`, `[Answer]:`）は英語形式のまま維持してセンサー互換性を保った。
- 2026-08-04T10:58:00Z — Sources レジスタには `[desc]` と `[scope]` のみを登録した; org.md の実効ルール（trunk-based, Testing Posture 等）は intent-capture の成果物（問題定義・ステークホルダー）に直接寄与しないため、許可ソース宇宙を最小に保った。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-04T11:02:00Z — 構造化質問の描画で全 A-E+X 選択肢のうち最大4件のみを提示した; Claude Code の AskUserQuestion は1問あたり4選択肢が上限のため、残りはハーネス組み込みの Other エスケープに委ねた。質問ファイルは全選択肢を保持しており、そちらが正典。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-04T11:20:00Z — Q12=C（public インターフェース維持）と SM-1（全経路バインド変数）の緊張関係を、矛盾ではなく Application Design への申し送り事項として扱った; 実行経路を内部化すれば public API を保ったままバインド変数化できる余地があり、ここで矛盾と断定するのは早計と判断した。代替案は Q12 を矛盾として差し戻すことだったが、C は明示的なユーザー制約であり尊重すべきと考えた。
- 2026-08-04T11:20:00Z — Q4=C（規制・コンプライアンス）を、規格名を推測で補わず Q11 で直接確認した; 成果物に実在しない規格名が混入するのを避けるため、想定のまま繰り越すのではなくフォローアップで確定させた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-04T12:05:00Z — 【Application Design（2.6）への申し送り】Q12 でユーザーが選んだ選択肢 C の原文は「内部（package-private / 実装クラス）の変更のみ許容し、public インターフェースは維持する」だった; 成果レベルのコミット（public API の後方互換性を維持する）のみを intent-statement.md に記載し、実装レベルの部分（どのコード層を変更してよいか）はここに退避した。2.6 はこの利用者選好を参考にしてよいが、確定した設計判断として扱ってはならない — SM-1 との両立可否の分析は 2.6 の仕事。
- 2026-08-04T11:55:00Z — 【解決済み】確定回答が実装アプローチに関わる場合の記録先は `## Initial Scope Signal` の行（`[Q<n>]` 出典の事実）であり、`[assumption]` ではない; レビュアー iteration 2 の指摘により確定。`[assumption]` は「確認できない」内容に限られ、確定回答を移すと第一級ソースを第二級扱いすることになる。未確認なのは方針そのものではなく「その方針で目標が達成可能か」の部分だけ。
- 2026-08-04T11:35:00Z — ユーザーの確定回答（Q12）を ideation 成果物にどう記録すべきかの一般則が不明確; 回答は確定しているが内容が実装アプローチであるため、制約として書くと ideation ガードレールに違反し、`[assumption]` として書くと確定回答を想定扱いすることになる。今回は後者（選好として `## Assumptions & Open Questions` に記録）を選んだが、この扱いが正しいかは要確認。
- 2026-08-04T11:35:00Z — 質問の選択肢に実装詳細を背景として書くと、ideation ステージであっても実装判断を引き出してしまう; Q12 の設問文に `Query#getSelectSQL()` 等のクラス名を書いたことがレビュアーに「実装判断へ誘導した」と指摘された。ideation フェーズの設問設計として要注意。

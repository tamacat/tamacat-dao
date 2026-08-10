<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-05T03:42:00Z — 選択肢が 5 個ある設問は、チップ 4 個 + 残り 1 個を設問本文に明記して Other 経由で選べる形にした; stage-protocol §3 は「A-D を先に、E 以降をフォローアップで」と指示するが、残り 1 個だけのフォローアップは 1 択となり AskUserQuestion が拒否する（question-rendering.md「Never send a one-option call」）。本文に全選択肢を書けば「ユーザーが全選択肢を見る」という §3 の要求は満たせる。
- 2026-08-05T03:40:00Z — consumes に列挙されていない `code-quality-assessment.md` も読んだ; SM-1 の検証可能性（MockPreparedStatement が何も記録しない）と、経路上の既存欠陥の一覧は、要件の抜けを判定するのに必要な事実であり、architecture.md だけでは得られない。codekb は space レベルの共有ストアなので読むこと自体に制約はない。
- 2026-08-05T04:12:00Z — 要件の出典欄に設問番号を書くときは、その設問の本文が当該要件を実際に裏付けているか照合する; FR-2.4（DATE/TIME の現行挙動維持）に `Q4` と書いたが、Q4 の本文は型検証・クォートエスケープ・LIKE エスケープの 3 点しか扱っておらず DATE/TIME に触れていなかった。出典が書かれていても裏付けていなければ、traceability 上は偽の系譜になる。
- 2026-08-05T04:12:00Z — 上流の能力記述が機構レベルで未確定なとき、機構を発明せず「観測可能な結果」だけを合否基準に固定し、機構の選択は Open Question として次ステージに逃がした; C-9（識別子位置の注入不能化）は scope-document でも暫定配置のままだった。ここで許可リスト等を選ぶとユーザーが確認していない設計判断を要件に混ぜることになる一方、そのまま写すと testability 要件に反する。判定を「拒否される、またはメタデータに照合される」という択一の観測結果に落とすと、どちらの機構でも判定できる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-08-05T03:40:00Z — Standard depth の目安（5-8 問）を超えて 10 問を作成した; ブラウンフィールドのライブラリ改修で、public API の観測可能な挙動（戻り値の中身、getBlobIndex、getExecutedQuery、型検証・エスケープ）が個別に決まらないと要件が書けない。加えて C-6 と現行コードの PostgreSQL 不一致という矛盾検出（MANDATORY）が 1 問を占める。水増しではなく、いずれも要件生成をブロックする。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-08-05T04:12:00Z — コードの事実から導出した新規要件（FR-2.4 の DATE/TIME 挙動、FR-8.5 の LIKE 候補枯渇テスト）を追加の設問にせず、出自を明記したうえで承認ゲートで確認する形にした; 設問を増やせば確認は取れるが、既に 13 問を経ておりゲートの直前でさらに往復を増やす。出自と「設問として提示していない」旨を成果物に書けば、ゲートで人間が同じ判断をできる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

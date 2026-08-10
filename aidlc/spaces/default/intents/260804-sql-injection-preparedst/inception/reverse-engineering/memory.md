<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-08-05T03:16:00Z — link 1 のスキャン結果をファイルに書き出して link 2 にパスで渡した; pipeline は「各リンクが上流のすべてを見る」契約だが、10k トークン規模のスキャン結果をプロンプトに直書きすると context budget のルール（内容ではなくパスを渡す）に反する。ファイル経由なら link 2 が必要な節を再読できる。
- 2026-08-05T03:16:00Z — スキャン対象を SQL spine に絞らず、ライブラリ全体を走査させた上で SQL spine のみ深掘りさせた; codekb は space レベルの永続ストアで、この space の将来のすべての intent が再利用する。現 intent 専用に絞ると次の intent で再スキャンが必要になる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-08-05T03:16:00Z — codekb が未コミットの作業ツリー状態を記録している; `pom.xml` のバージョン 1.6.1→2.0 が未コミットのまま。timestamp 成果物に明記され staleness trigger にもなっているが、下流ステージが「version 2.0」を引用する場合、それは未コミットの事実を引用していることになる。
- 2026-08-05T03:16:00Z — カバレッジがツールで測定されておらず、org.md の「minimum 80% line coverage」を評価できない; 138 テストが通るという事実はあるが、それは件数であって被覆率ではない。本 intent の Testing Posture がこの制約とどう折り合うかは NFR Requirements（3.2）で扱う必要がある。

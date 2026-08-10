> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations

- 2026-08-05T13:23:03Z — stage 定義 Step 3 が求める「Deployment model（monolithic / independent / hybrid）」は設問にしない; `services.md` が「tamacat-dao にサービスは存在しない」と記録済みで、成果物は Maven の単一 jar 1 個に固定されている。Unit ごとのデプロイ単位を選ぶ余地がないため、設問にすると意味のない選択を強いる。unit-of-work.md には「全 Unit が同一 jar に同梱される」と事実として記す。
- 2026-08-05T17:05:00Z — `stories.md` が不在（user-stories 2.4 が SKIP）のため、story map の割り当て単位を `requirements.md` の FR と AC に置き換えた; ストーリーを本ステージで新規に捏造するとトレーサビリティの起点が二重になる。FR を「何を作るか」、AC を「完了をどう判定するか」の 2 層に分け、網羅性の検証項目（全 FR が割り当て済み／全 Unit が FR と AC を持つ）はストーリー版と同じ形を保った。
- 2026-08-05T17:06:00Z — `Query` インターフェースの新 `default` メソッド 8 個の宣言を U2 に一括して置き、U3 は override を足すだけとした; 宣言を U2 と U3 に分けると U2 完了時点で `Query` が半分だけ新メソッドを持つ状態になり、`QueryImpl` のコンパイルが片方の Unit に引きずられる。

## Deviations

- 2026-08-05T17:10:00Z — 承認済みの 2.3 成果物 `requirements.md` を本ステージで編集した; `decisions.md` の「承認済み要件との差分」表が 2.7 の承認ゲートまでの更新を求めており、その根拠 ADR-002 / ADR-003 は 2.6 のゲートで承認済みであるため。ステージの `produces` 外への書き込みだが、遡及であることを隠さないよう `requirements.md` に「本ステージ以降に適用された更新」節を追加し、2.3 時点の `## Review` 節が更新前の内容に対するものであることを明記した。
- 2026-08-05T17:10:30Z — ADR-002 の差分を FR-7.1 の但し書きだけで表さず、`FR-7.3` として独立の要件を起こした; 「`param()` 経由も対象に含める」という否定形の但し書きだけでは「では何を追加するのか」（`prepare()` + `where(Param)`）が要件として残らず、下流が実装対象を要件から引けない。

- 2026-08-05T17:30:00Z — 「網羅性を検証した」という自己申告の集計値（FR 総数、Unit ごとの件数）を実際に数え直さずに書き、レビュアーの 2 イテレーション連続で算術誤りを指摘された; 1 回目は総数 32（正しくは 31）と U1=8（正しくは 7）、および同一文書内で U2 が 10 と 9 に食い違う自己矛盾。2 回目は訂正文の「合計が 31 を超える」（実際は一致する——重複計上 +3 と未割当 −3 が相殺）。割り当て表そのものは 2 回とも正しく、誤っていたのは検証を主張する数字のほうだった。網羅性の主張は、表を書いた後に必ず数え直してから書く。

## Tradeoffs

- 2026-08-05T16:55:00Z — Unit の軸を実行経路別（Q1 = A）とし、レイヤ別・FR 群別を採らなかった; レイヤ別は単独 Unit で端から端まで通らず動く形が出るのが遅い、FR 群別は FR-1 が巨大化し FR-2 と同じクラス（`BindSqlBuilder`）を 2 Unit が同時に触る。実行経路別は `services.md`「段階的な移行の可能性」表の切り替え単位とそのまま一致する。
- 2026-08-05T16:56:00Z — Q2「粗い（3〜4 Unit）」と Q5「FR-1.7 を Unit に含める（＝5 Unit）」の矛盾を、粒度の側を 3〜5 に広げる形（FQ-1 = A）で解いた; FR-1.7 を SELECT 経路 Unit に同居させる案は Unit 数を 4 に保てるが、walking skeleton を含む Must の Unit の完了が Should の作業を待つことになる。Must と Should を Unit 境界で分離しておけば U5 を落としても U1〜U4 は成立する。
- 2026-08-05T17:07:00Z — mock スタック拡張（C-8）を独立 Unit にせず共通基盤 U1 に含めた（Q3 = B）; `PreparedStatementBinder`（C-6）の正しさを確認する手段は mock の記録機能しかなく、同じ Unit に置くことで C-6 が「テストできないまま完了した」状態を作れなくする。独立 Unit にすると Unit 数が 1 増えるうえ、境界が「production コード／テストコード」という実行経路とは別の軸になる。

## Open questions

- 2026-08-05T17:12:00Z — 承認済みの上流ステージ成果物を編集すると、**現在アクティブなステージの sensor がその成果物に対して発火する**。`requirements.md` への 6 回の編集すべてで `upstream-coverage` が SENSOR_FAILED を出したが、これは 2.3 の成果物を 2.7 の consumes リストで判定した誤検出であり、本ステージの 3 成果物に対する発火はすべて pass している。sensor は advisory なのでゲートを塞がないが、下流ステージが同様の遡及編集を行うときも同じノイズが出る。sensor 側で「編集対象が現ステージの produces に含まれるか」を見る余地があるかは framework 側の論点。
- 2026-08-05T17:13:00Z — U5 `identifier-safety` は機構（例外拒否／メタデータ照合）が未確定のまま Construction に入る唯一の Unit である。`application-design` は `Sort` を「変更しないコンポーネント」としており参照できる既存設計がない。Functional Design（3.1）が `Sort.sort(Object, Object)` の非 `Column` キー経路（意図的にメタデータ外の式を許す）と FR-3.1 の衝突を解く必要がある。

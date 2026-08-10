# Intent Capture & Framing — Questions

## Sources

- [desc] Initial description: "SQLインジェクション対策として、PrepeardStatementを使った実装に変更したい。\n・バージョン/ブランチ v2.0 作成済み。リモートはv1.6.1。まずは、ローカルでのみ作業する。git pushしない。\n・Java8のまま。"
- [scope] Workflow-selected scope: `sql-parameterization`.

---

## Q1. この取り組みで解決したいビジネス上の問題は何ですか？

- A. 現行の SQL 文字列連結による SQL インジェクション脆弱性そのものを除去したい（セキュリティ債の解消）
- B. 本ライブラリを利用しているアプリケーション側のセキュリティ監査／診断指摘に対応したい
- C. 実際にインシデント・脆弱性報告があり、その修正が必要
- D. v2.0 のメジャーバージョンに向けた設計上の刷新の一環（セキュリティはその主要動機のひとつ）
- E. 上記のうち複数（該当するものを複数選択可 — select all that apply）
- X. Other (please specify)

[Answer]: E. 上記のうち複数 → A, B
**Mode:** guided | **Timestamp:** 2026-08-04T11:02:00Z

---

## Q2. このライブラリの利用者（顧客）は誰で、どのような不便を抱えていますか？

- A. 社内／自分自身のプロジェクトのみで利用しており、外部利用者はいない
- B. OSS として公開しており、不特定の外部開発者が利用している
- C. 特定の取引先・受託案件で利用されている
- D. 上記のうち複数（select all that apply）
- E. Not yet defined（利用者像を今の時点で特定しない）
- X. Other (please specify)

[Answer]: A. 社内／自分自身のプロジェクトのみで利用しており、外部利用者はいない
**Mode:** guided | **Timestamp:** 2026-08-04T11:02:00Z

---

## Q3. この取り組みの成功をどのように測りますか？（測定可能な指標）

- A. SQL を組み立てる全経路が PreparedStatement のバインド変数を使用していること（文字列連結による値埋め込みがゼロ）
- B. 既存テストスイートが全てグリーンのまま維持されること
- C. 静的解析（例: 既存の CodeQL ワークフロー）で SQL インジェクション指摘がゼロになること
- D. 上記のうち複数（select all that apply）
- E. Not yet defined（成功指標はまだ決めていない）
- X. Other (please specify)

[Answer]: D. 上記のうち複数 → A, B, C
**Mode:** guided | **Timestamp:** 2026-08-04T11:02:00Z

---

## Q4. 「なぜ今なのか」— この取り組みの引き金は何ですか？

- A. セキュリティ上の技術的負債であり、以前から認識していた（自発的な着手）
- B. 外部からの脆弱性指摘・監査・診断結果
- C. 規制・コンプライアンス要件
- D. v2.0 という破壊的変更が許容されるタイミングが来たため
- E. Not yet defined
- X. Other (please specify)

[Answer]: A, B, C（複数選択）
**Mode:** guided | **Timestamp:** 2026-08-04T11:02:00Z

---

## Q5. 主要なステークホルダーは誰で、それぞれ何を気にしていますか？

- A. 開発者本人（tamacat）のみ — 実装品質と後方互換性の両方に責任を持つ
- B. 開発者本人に加えて、ライブラリ利用者（API 互換性を気にする）
- C. 開発者本人に加えて、セキュリティ監査担当／指摘元
- D. 上記のうち複数（select all that apply）
- E. Not identified（現時点で特定しない）
- X. Other (please specify)

[Answer]: A. 開発者本人（tamacat）のみ — 実装品質と後方互換性の両方に責任を持つ
**Mode:** guided | **Timestamp:** 2026-08-04T11:05:00Z

---

## Q6. スコープや優先度を決めるのは誰ですか？また、決定に影響を与えるのは誰ですか？

- A. 開発者本人が単独で決定する（影響者なし）
- B. 開発者本人が決定するが、既存ライブラリ利用者の互換性要求が実質的な制約になる
- C. 別途、決裁者（プロジェクトオーナー等）がいる
- D. Not yet defined
- X. Other (please specify)

[Answer]: A. 開発者本人が単独で決定する（影響者なし）
**Mode:** guided | **Timestamp:** 2026-08-04T11:05:00Z

---

## Q7. 報告・コミュニケーション上の要件はありますか？（レポート頻度、変更告知など）

- A. なし — ローカル作業のみで、外部への報告は不要（"git pushしない" の方針どおり）
- B. 破壊的 API 変更がある場合、リリースノート／CHANGELOG での告知が必要
- C. マイグレーションガイド（既存利用者向けの移行手順）が必要
- D. 上記のうち複数（select all that apply）
- E. None（該当なし）
- X. Other (please specify)

[Answer]: B. 破壊的 API 変更がある場合、リリースノート／CHANGELOG での告知が必要
**Mode:** guided | **Timestamp:** 2026-08-04T11:05:00Z

---

## Q8. ワークフローは `sql-parameterization` スコープで開始されました。このスコープは意図しているプロダクト境界と一致していますか？

（このスコープは 32 ステージ中 15 ステージを実行し、Operation フェーズ全体を SKIP します — ローカル作業のみ・git push しない、という前提に基づきます。）

- A. 一致している — 現在のスコープを確認する（tamacat-dao ライブラリ内の SQL 生成／実行経路のみを対象とする）
- B. 一致しているが、対象を SQLParser 周辺の最小変更に絞りたい（プロダクト境界をより狭く定義したい）
- C. 一致していない — 対象を tamacat-dao 以外（利用側アプリケーション等）にも広げたい
- D. 一致していない — 別のプロダクト境界を定義したい（内容を記述してください）
- E. Not yet defined
- X. Other (please specify)

[Answer]: A. 一致している — 現在のスコープを確認する（tamacat-dao ライブラリ内の SQL 生成／実行経路のみを対象とする）
**Mode:** guided | **Timestamp:** 2026-08-04T11:05:00Z

---

## 矛盾検出（Contradiction detection）

以下 2 件の矛盾を検出したため、フォローアップ質問 Q9 / Q10 で解消する。

**矛盾 1 — 監査・規制の主体が特定されていない**
Q1=B（利用アプリ側のセキュリティ監査／診断指摘に対応）、Q4=B（外部からの脆弱性指摘・監査・診断結果）、Q4=C（規制・コンプライアンス要件）は、指摘元・監査担当・規制要件を課す主体の存在を示す。しかし Q5=A（ステークホルダーは開発者本人のみ）および Q6=A（単独で決定、影響者なし）はその主体を否定している。

**矛盾 2 — 告知の宛先が存在しない**
Q2=A（社内／自分自身のみで利用、外部利用者はいない）および Q5=A（ステークホルダーは開発者本人のみ）に対し、Q7=B（破壊的 API 変更時に CHANGELOG／リリースノートでの告知が必要）は告知の受け手を前提とする。

---

## Q9. 監査・規制の主体について、どれが実態に最も近いですか？

- A. 実際の監査担当者や規制当局はいない — Q4 の B/C は「一般的なセキュリティ上の望ましさ」を表しただけであり、引き金は実質的に A（自発的に認識していた技術的負債）のみ。Q4 の回答を A のみに訂正する
- B. 自動化された仕組み（GitHub CodeQL 等の静的解析）が「指摘元」である — 人間の監査担当者はいない。ステークホルダーは本人のみのままで正しい
- C. 実在の監査担当者／指摘元がいる — Q5 に「セキュリティ監査担当／指摘元」を追加し、Q6 の「影響者なし」も訂正する
- D. 規制・コンプライアンス要件が実在する（特定の規格・基準がある） — 内容を記述してください
- X. Other (please specify)

[Answer]: B. 自動化された仕組み（GitHub CodeQL 等の静的解析）が「指摘元」である — 人間の監査担当者はいない。ステークホルダーは本人のみのままで正しい
**Mode:** guided | **Timestamp:** 2026-08-04T11:10:00Z
**矛盾 1 の解消:** Q1=B / Q4=B の「監査・指摘」は自動静的解析による指摘を指す。人間の監査主体は存在しないため、Q5=A（本人のみ）と Q6=A（影響者なし）はそのまま有効。Q4=C（規制・コンプライアンス要件）については具体的な規格名が特定されていないため、成果物では `[assumption]` として扱う。

---

## Q10. CHANGELOG／リリースノートの告知先について、どれが実態に最も近いですか？

- A. 将来の自分自身と、v1.6.1 からの移行時の記録のため — 現時点で外部の受け手はいない。Q2=A / Q5=A のままで正しい
- B. GitHub 上で公開されているため、不特定の潜在的利用者が受け手になりうる — Q2 を B（OSS として公開）に訂正する
- C. 社内の別プロジェクト／別チームが受け手である — Q5 に「ライブラリ利用者」を追加する
- D. 告知は実際には不要だった — Q7 を A（なし、ローカル作業のみ）に訂正する
- X. Other (please specify)

[Answer]: A. 将来の自分自身と、v1.6.1 からの移行時の記録のため — 現時点で外部の受け手はいない。Q2=A / Q5=A のままで正しい
**Mode:** guided | **Timestamp:** 2026-08-04T11:10:00Z
**矛盾 2 の解消:** CHANGELOG／リリースノートは将来の自分自身に向けた記録であり、外部の受け手を前提としない。Q2=A（外部利用者なし）と Q5=A（本人のみ）はそのまま有効。

---

## Consolidated Summary Confirmation

**Prompt:** これで内容は正しいですか？（成果物を生成する前の最終確認）

- A. Looks correct — この回答内容で成果物（intent-statement.md / stakeholder-map.md）を生成する
- B. Request changes — 生成前にひとつ以上の回答を修正する

[Answer]: A. Looks correct
**Mode:** guided | **Timestamp:** 2026-08-04T11:14:00Z

---

## Assumption Confirmation

生成された 2 つの成果物には、以下の `[assumption]` が残っている。

**intent-statement.md**

- A1. 引き金として挙げられた規制・コンプライアンス要件について、具体的な規格名・基準名は特定されていない。どの規格に対応する必要があるかは未確認である。 [Q4] [Q9]
- A2. v2.0 において public API の破壊的変更を許容してよいかどうかは、依頼内容からは確定していない。v2.0 ブランチが作成済みである事実はメジャーバージョン更新を示唆するが、API 契約を壊してよいとは明言されていない。 [desc]
- A3. CHANGELOG／リリースノートでの告知が必要になるのは「破壊的 API 変更がある場合」とされているが、この取り組みで実際に破壊的変更が発生するかどうかは現時点で未確定である。 [Q7]

**stakeholder-map.md**

- A4. CR-1 の発生条件である「破壊的 API 変更」が実際に生じるかどうかは、現時点で未確定である。生じない場合、コミュニケーション要件は実質的にゼロになる。 [Q7]
- A5. マイグレーションガイド（既存利用者向けの移行手順）は要件として選択されなかった。ただしこれは「不要と決定された」ことの確認ではなく、単に選択されなかったことを意味する。 [Q7]

**選択肢**

- A. Accept assumptions — 上記を `[assumption]` ラベル付きのまま保持して先に進む（受諾は想定を事実に変えるものではない）
- B. Convert to follow-up questions — 上記を追加のフォローアップ質問（Q11 以降）に変換し、回答を得てから成果物を改訂する

[Answer]: B. Convert to follow-up questions
**Mode:** guided | **Timestamp:** 2026-08-04T11:18:00Z
**変換マップ:** A1 → Q11 / A2 → Q12 / A3・A4 → Q13 / A5 → Q14

---

## Q11. 引き金として挙げられた「規制・コンプライアンス要件」について、具体的な規格・基準はありますか？（A1 の解消）

- A. 特定の規格はない — 一般的なセキュリティ上のベストプラクティス（OWASP Top 10 の SQL インジェクション対策など）を念頭に置いていた
- B. 特定の規格・基準がある（規格名を記述してください。例: PCI-DSS、社内セキュリティ基準、業界ガイドライン）
- C. 利用側アプリケーションが受けている規制が間接的に波及している（規制名と波及元を記述してください）
- D. Not yet defined — 現時点では特定できない。想定のまま繰り越す
- X. Other (please specify)

[Answer]: A. 特定の規格はない — 一般的なセキュリティ上のベストプラクティス（OWASP Top 10 の SQL インジェクション対策など）を念頭に置いていた
**Mode:** guided | **Timestamp:** 2026-08-04T11:22:00Z

---

## Q12. v2.0 において、public API の破壊的変更（後方互換性を壊す変更）を許容しますか？（A2 の解消）

背景: 現在 `Query#getSelectSQL()` などが素の `String` を返し、`Dao` / `DaoAdapter` / `DBAccessManager` および各方言実装がそれを消費している。バインド値を運ぶには API 契約の変更が必要になる可能性が高い。

- A. 許容する — v2.0 はメジャーバージョンであり、必要なら public API の署名・戻り値型を変更してよい
- B. 原則許容しない — 既存 API の署名は維持し、新 API を追加する形（既存メソッドは非推奨化）で対応したい
- C. 内部（package-private / 実装クラス）の変更のみ許容し、public インターフェースは維持する
- D. Not yet defined — Application Design（2.6）で判断したい。想定のまま繰り越す
- X. Other (please specify)

[Answer]: C. 内部（package-private / 実装クラス）の変更のみ許容し、public インターフェースは維持する
**Mode:** guided | **Timestamp:** 2026-08-04T11:22:00Z

---

## Q13. CHANGELOG／リリースノートでの告知は、破壊的 API 変更の有無にかかわらず必要ですか？（A3・A4 の解消）

- A. 破壊的変更の有無にかかわらず必要 — セキュリティ修正である以上、変更内容は必ず記録する
- B. 破壊的 API 変更がある場合のみ必要 — Q7 の回答どおりの条件付き
- C. 不要 — ローカル作業のみのため、この取り組みの範囲では告知しない
- D. Not yet defined
- X. Other (please specify)

[Answer]: A. 破壊的変更の有無にかかわらず必要 — セキュリティ修正である以上、変更内容は必ず記録する
**Mode:** guided | **Timestamp:** 2026-08-04T11:22:00Z

---

## Q14. 既存利用者向けのマイグレーションガイド（移行手順ドキュメント）は必要ですか？（A5 の解消）

- A. 不要 — 外部利用者がおらず、CHANGELOG で十分
- B. 必要 — 破壊的 API 変更がある場合、移行手順を文書化する
- C. 必要 — 破壊的変更の有無にかかわらず、v1.6.1 → v2.0 の移行手順を文書化する
- D. Not yet defined
- X. Other (please specify)

[Answer]: B. 必要 — 破壊的 API 変更がある場合、移行手順を文書化する
**Mode:** guided | **Timestamp:** 2026-08-04T11:22:00Z
**注記:** Q12=C により public インターフェースは維持される方針であるため、この条件は現行方針下では発動しない見込みである。

---

## Assumption Confirmation (round 2)

Q11〜Q14 により A1〜A5 はすべて解消され、成果物を改訂した。改訂後に残る `[assumption]` は以下 1 件のみ。

**intent-statement.md**

- A6. C-1（public インターフェース維持）の制約下で SM-1（SQL 組み立ての全経路がバインド変数を使用）を達成できるかどうかは、本ステージでは検証されていない。両者の両立可否は Application Design（2.6）で判断する必要がある。 [Q3] [Q12]

**stakeholder-map.md**

- なし（`## Assumptions & Open Questions` は `None.`）

**選択肢**

- A. Accept assumptions — A6 を `[assumption]` ラベル付きのまま保持して先に進む（受諾は想定を事実に変えるものではない）
- B. Convert to follow-up questions — A6 を追加のフォローアップ質問に変換し、回答を得てから成果物を改訂する

[Answer]: A. Accept assumptions
**Mode:** guided | **Timestamp:** 2026-08-04T11:26:00Z
**注記:** 受諾は A6 を事実に変えるものではない。`[assumption]` ラベルは保持され、Application Design（2.6）への申し送り事項として繰り越される。

---

## Reviewer Findings (iteration 1 — NOT-READY)

`aidlc-product-lead-agent` が 2 件の指摘を返し、成果物を改訂した。

**指摘 1 — `## Constraints` C-1 が ideation フェーズのガードレールに違反**
`intent-statement.md` の自作セクション `## Constraints` の C-1（「内部の変更のみ許容し、public インターフェースは維持する」）は実装アプローチに関する判断であり、`memory/phases/ideation.md` §Scope Discipline「No implementation details (architecture, tech stack, code) in ideation artifacts」に直接違反する。またステージ定義 Step 5 が列挙する節構成（Problem Statement / Target Customer / Success Metrics / Initiative Trigger / Initial Scope Signal）にない。さらに A6 自身が C-1 と SM-1 の両立可否は未検証と認めており、実現可能性の検証前に設計空間を凍結していた。
**改訂:** `## Constraints` を削除。Java 8 と「ローカルのみ・git push しない」は `## Initial Scope Signal` の行として戻した（いずれも `[desc]` 由来のスコープ信号）。Q12 の API 互換性選好は `## Assumptions & Open Questions` に移し、確定した制約ではなくユーザーの選好として記録した上で、最終判断を Application Design（2.6）に委ねた。

**指摘 2 — Q9 の矛盾解消が 2 つの異なる監査概念を同一視**
Q1=B は「利用アプリケーション側」の監査／診断指摘を述べるが、Q9 の解消はこれを「本リポジトリ自身の自動静的解析」に読み替えた。CodeQL ワークフローは tamacat-dao 本体をスキャンするものであり、利用側アプリケーションを対象とする保証は回答のどこにもない。この未確認の同一視が SM-3 とステークホルダー表に確定事実として伝播していた。
**改訂:** SM-3 に「測定対象: tamacat-dao 本体」列を追加して測定範囲を明示。同一性が未確認である旨を両成果物の `## Assumptions & Open Questions` に `[assumption]` として記録した。

---

## Assumption Confirmation (round 3)

レビュアー指摘への対応により想定の集合が変化したため、再確認する。

**intent-statement.md**

- A7. API 互換性の方針（Q12 の「public インターフェースは維持する」という選好）は本ステージでは制約として確定させない。SM-1 との両立可否と最終的な方針は Application Design（2.6）で判断する。 [Q12] [Q3]
- A8. Problem Statement 第2項の「利用アプリケーション側のセキュリティ監査／診断指摘」と、Q9 で特定された「自動静的解析による指摘」が同一であるかは未確認。SM-3 は tamacat-dao 本体のみを測定対象としており、Problem Statement 第2項の充足を直接には検証しない。 [Q1] [Q9]

**stakeholder-map.md**

- A9. 「自動静的解析」を唯一の指摘元ステークホルダーとして扱っているが、利用側アプリケーションの監査主体が別に存在する可能性は排除されていない。 [Q1] [Q9]
- A10. CR-2 の発生条件（破壊的 API 変更の有無）は、API 互換性方針が Application Design（2.6）で確定するまで判定できない。 [Q14] [Q12]

**選択肢**

- A. Accept assumptions — 上記を `[assumption]` ラベル付きのまま保持して先に進む（受諾は想定を事実に変えるものではない）
- B. Convert to follow-up questions — 上記を追加のフォローアップ質問に変換し、回答を得てから成果物を改訂する

[Answer]: A. Accept assumptions
**Mode:** guided | **Timestamp:** 2026-08-04T11:38:00Z
**注記:** 受諾は A7〜A10 を事実に変えるものではない。`[assumption]` ラベルは保持される。A7・A10 は Application Design（2.6）、A8・A9 は Requirements Analysis（2.3）への申し送り事項として繰り越される。

---

## Reviewer Findings (iteration 2 — NOT-READY) と人間の差し戻し

`aidlc-product-lead-agent` は指摘 2 を「クローズ」と判定した一方、指摘 1 への対応が別の問題に置き換わったと判定した。reviewer の iteration 上限（2）に達したため、未解決の指摘を明示した上で承認ゲートを提示し、人間は **Request Changes** を選択した。

**未解決だった指摘:** 確定回答である Q12 を `## Assumptions & Open Questions` に移して `[assumption]` タグを付けたことは、ステージの grounding contract 規則 5（`[assumption]` は「確認できない」内容に限る）に反する。規則 1 は確定 `[Q<n>]` 回答を `[desc]` / `[scope]` と同格の第一級ソースとして扱う。さらにこの結果、`stakeholder-map.md` 内で同じ Q12 について「制約である」（22行目）と「確定していない」（32行目）という相反する主張が併存する自己矛盾が生じていた。

**改訂（人間の差し戻し後）:**
- `intent-statement.md` の `## Initial Scope Signal` に「API 互換性の方針」行を追加し、Q12 の回答を `[Q12]` 出典の事実として記載した（Java 8 / no-push と同じ扱い）。
- `## Assumptions & Open Questions` には、真に未確認である「その方針の下で SM-1 が達成可能か」のみを残した。方針そのものが確定している旨を本文で明示した。
- `stakeholder-map.md` 22行目の「制約である」という表現を「開発者本人が自ら定めた API 互換性の方針」に改め、32行目および A10 の表現と整合させた。

---

## Reviewer Findings (差し戻し後 iteration 1 — NOT-READY)

`aidlc-product-lead-agent` は、自身の前回推奨に対しても批判的に検証した上で、指摘 1 の実体が未解消であると判定した。

**指摘:** 「内部（package-private / 実装クラス）の変更のみ許容し、public インターフェースは維持する」という記述は、どのコード層を変更してよいかを規定する実装レベルの指示である。`## Constraints` から `## Initial Scope Signal` へ移しても、`memory/phases/ideation.md` §Scope Discipline「No implementation details (architecture, tech stack, code) in ideation artifacts」への違反は治らない（コンテナ名を変えても内容の違反は治らない）。また `## Initial Scope Signal` はステージ定義 Step 5 で「workflow-selected scope と user-confirmed product boundary を分けて示す」節と定義されており、この行はスコープ／境界の記述ではない。成果レベルのコミット（public API の後方互換性を維持する）と実装レベルの指示（だから package-private のみ変更可）は別物であり、後者は Application Design（2.6）が行うべきトレードオフ分析を先取りする。

**改訂:**
- `## Initial Scope Signal` の「API 互換性の方針」行を成果レベルの記述に限定した: 「public API の後方互換性を維持する（破壊的変更はしない）」。
- 実装レベルの記述（どのコード層を変更してよいか）は成果物から削除し、Application Design（2.6）向けの文脈として `memory.md` の Open questions に退避した。2.6 は利用者選好として参考にしてよいが、確定した設計判断として扱わない。
- `## Assumptions & Open Questions` に「どのコード層をどう変更してこの方針を実現するかは、本ステージでは定めない」と明記した。
- `stakeholder-map.md` の該当箇所（22行目・32行目・A10）を同じ成果レベルの表現に揃えた。

---

---

---

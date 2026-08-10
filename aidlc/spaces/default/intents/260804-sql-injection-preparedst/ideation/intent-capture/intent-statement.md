# Intent Statement — SQL パラメータ化（PreparedStatement 化）

## Problem Statement

- tamacat-dao の SQL 組み立てが文字列連結によって行われており、そこに起因する SQL インジェクション脆弱性そのものを除去する必要がある。これはセキュリティ上の技術的負債の解消として位置づけられる。 [desc] [Q1]
- 併せて、本ライブラリを利用しているアプリケーション側で挙がるセキュリティ監査／診断の指摘に対応できる状態にする。 [Q1]
- 解決手段は PreparedStatement を用いた実装への変更として、依頼時点で既に指定されている。 [desc]

## Target Customer

- 利用者は社内／開発者自身のプロジェクトに限られ、外部利用者はいない。したがって本取り組みの直接の受益者は、tamacat-dao を組み込んでいる自身のアプリケーション群である。 [Q2]
- 受益内容は、アプリケーション側が SQL インジェクションのリスクをライブラリ層で解消できること、およびそれによって監査・診断の指摘に応えられることである。 [Q1] [Q2]

## Success Metrics

| # | 指標 | 判定基準 | 測定対象 | Source |
|---|------|----------|----------|--------|
| SM-1 | SQL を組み立てる全経路が PreparedStatement のバインド変数を使用している | 文字列連結による値の埋め込みがゼロであること | tamacat-dao 本体 | [Q3] |
| SM-2 | 既存テストスイートがグリーンのまま維持される | 変更前後で新規の失敗テストがゼロであること | tamacat-dao 本体 | [Q3] |
| SM-3 | tamacat-dao 本体に対する静的解析で SQL インジェクション指摘がゼロになる | 静的解析（例: 既存の CodeQL ワークフロー）の該当指摘件数がゼロであること | tamacat-dao 本体 | [Q3] [Q9] |

- SM-3 が測定するのは tamacat-dao 本体に対する自動静的解析の結果であり、その「指摘元」は人間の監査担当者ではない。 [Q9] [Q3]
- Problem Statement 第2項が述べる「利用アプリケーション側の監査／診断指摘」と、SM-3 が測定する対象が同一であるかは確認されていない（`## Assumptions & Open Questions` を参照）。 [Q1] [Q9]

## Initiative Trigger

- **自発的な技術的負債の解消** — セキュリティ上の技術的負債として以前から認識されていた。 [Q4]
- **指摘・監査** — 引き金として指摘・監査が挙げられているが、その主体は自動化された静的解析の仕組み（例: GitHub CodeQL）であり、人間の監査担当者は存在しない。 [Q4] [Q9]
- **セキュリティ上のベストプラクティス** — 引き金のひとつとして「規制・コンプライアンス要件」が挙げられたが、これは特定の規格・基準を指すものではなく、一般的なセキュリティ上のベストプラクティス（OWASP Top 10 の SQL インジェクション対策など）を念頭に置いたものである。準拠すべき特定の規格は存在しない。 [Q4] [Q11]

## Initial Scope Signal

| 区分 | 値 | Source |
|------|-----|--------|
| Workflow-selected scope | `sql-parameterization`（workflow-selected） | [scope] |
| User-confirmed product boundary | tamacat-dao ライブラリ内の SQL 生成／実行経路のみを対象とする | [Q8] |
| 作業上の制約 | v2.0 ブランチは作成済み、リモートは v1.6.1。当面ローカルでのみ作業し、git push しない | [desc] |
| 技術上の制約 | Java 8 のまま維持する | [desc] |
| API 互換性の方針 | public API の後方互換性を維持する（破壊的変更はしない） | [Q12] |

- ユーザーは workflow-selected scope が意図するプロダクト境界と一致していることを確認済みである。 [Q8]

## Assumptions & Open Questions

- [assumption] API 互換性の方針（public API の後方互換性を維持する）が SM-1（SQL 組み立ての全経路がバインド変数を使用）と両立するかは、本ステージでは検証されていない。両立可否は Application Design（2.6）で判断する。方針そのものは確定しているが、その方針の下で SM-1 が達成可能かどうかが未確認である。どのコード層をどう変更してこの方針を実現するかは、本ステージでは定めない。 [Q12] [Q3]
- [assumption] Problem Statement 第2項が指す「利用アプリケーション側のセキュリティ監査／診断指摘」と、Q9 で特定された「自動静的解析による指摘」が同一のものであるかは確認されていない。利用側アプリケーションが静的解析の対象になっているかも未確認である。SM-3 は tamacat-dao 本体のみを測定対象としており、Problem Statement 第2項の充足を直接には検証しない。 [Q1] [Q9]

## Review

READY

**The finding is closed. The revision fixed the substance, not just the label this time.**

**Outcome vs. solution shape — genuinely outcome-level now.** `intent-statement.md` line 39: "API 互換性の方針 | public API の後方互換性を維持する（破壊的変更はしない） | `[Q12]`". This states a result (no breaking changes to the public surface) with zero reference to which code layer, visibility modifier, or class structure achieves it. That is a legitimate problem/opportunity-level commitment, not architecture guidance — it is the same category of statement as "we will not raise the minimum Java version" (already accepted as a scope signal). The previously-flagged clause ("内部（package-private / 実装クラス）の変更のみ許容") is gone from both artifacts; per the questions-file log (line 339) it was relocated to `memory.md` as non-binding context for Application Design (2.6) — I did not read `memory.md` per instructions, but its removal from the two grounding-contract-governed artifacts is what my finding required, and that removal is verified directly in both files.

**No overstatement of what `[Q12]` supports.** The confirmed Q12 answer (`intent-capture-questions.md` line 224) selected option C, which bundles an outcome ("public インターフェースは維持する") with one specific mechanism among several ("内部…のみ許容"). Option B would have reached the same public-compatibility outcome via a different mechanism (deprecate-and-add). Stating only the outcome common to what was actually confirmed, and dropping the mechanism-specific detail, is a faithful narrowing — not a claim the source doesn't support. Good practice: the artifact resists the temptation to smuggle the mechanism back in under a softer verb.

**Consistent across both artifacts, no residual contradiction.** `intent-statement.md` line 45 (assumption) explicitly separates the two questions cleanly: "方針そのものは確定しているが、その方針の下で SM-1 が達成可能かどうかが未確認である。どのコード層をどう変更してこの方針を実現するかは、本ステージでは定めない。" `stakeholder-map.md` line 22 ("…public API の後方互換性を維持するという方針は…開発者本人が自ら定めたものである"), line 32, and A10 (line 40) all use the same "public API の後方互換性を維持する" phrasing and the same confirmed-policy / unconfirmed-compatibility split. I found no wording anywhere that reintroduces "制約である," "選好," or any other framing that would re-open the epistemic-status question closed in the prior iteration.

**On `## Initial Scope Signal`'s scope (raised explicitly by the coordinator) — a real but non-blocking observation, not upgraded to a finding.** The stage's Step 5 description of this section is narrow ("Show the workflow-selected scope separately from the user-confirmed product boundary") and the table now carries three rows beyond that — 作業上の制約, 技術上の制約, and this API-compatibility row — that are boundary/constraint statements rather than scope-vs-boundary statements in the strict sense. I considered elevating this to NOT-READY on a stricter reading of Step 5 as an exhaustive content list. I did not, for two reasons: (1) all three rows are accurately `[desc]`/`[Q12]`-sourced, clearly self-labeled by row name, and none misrepresent a decision's status the way the removed clause did; (2) Step 5's per-section descriptions read as guidance on core content, not an exhaustive whitelist, and the required-sections sensor only checks heading count, not row taxonomy. This is worth a light editorial pass in a later stage (or renaming the section) but does not block a developer or QA from knowing what to build, which is the bar for readiness. Flagging it here so it isn't silently dropped.

**Nothing else regressed.** Problem Statement, Target Customer, Success Metrics (with the 測定対象 column from the earlier fix), Initiative Trigger, the Q1/Q9 conflation assumption pair, and the Communication Requirements table are all unchanged from the last clean state and remain internally consistent. All `[Answer]:` tags remain filled through round 3, and `intent-capture-questions.md`'s reviewer-findings log accurately reflects the full history of findings and revisions across all iterations.

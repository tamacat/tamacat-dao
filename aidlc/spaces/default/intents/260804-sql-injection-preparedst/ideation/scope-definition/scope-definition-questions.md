# Scope Definition & Prioritization — Questions

## 上流成果物

- `intent-statement.md`（intent-capture）— 問題定義、成功指標 SM-1〜3、API 互換性の方針、Java 8 / ローカルのみ / git push なしの制約、およびプロダクト境界（tamacat-dao ライブラリ内の SQL 生成／実行経路のみ）。本ステージの質問と成果物はこれを起点とする。
- `feasibility-assessment.md` / `constraint-register.md` — 本スコープでは feasibility ステージが SKIP のため存在しない。制約は `intent-statement.md` の `## Initial Scope Signal` から引き継ぐ。

---

## Q1. 「価値を届ける最小限のスコープ」をどう定義しますか？

- A. SQL の値が入る位置すべて（WHERE の比較値、LIKE、IN、INSERT/UPDATE の値）がバインド変数になれば最小限を満たす
- B. 上記に加えて、バインドできない位置（テーブル名・カラム名・ORDER BY）も注入不能であることを確認できて初めて最小限を満たす
- C. まず 1 つの代表的な経路（例: 単純な SELECT の WHERE 条件）が端から端まで動くことが最小限。残りはその後
- D. 対応する DB 方言すべて（MySQL / Oracle / PostgreSQL 等）で成立して初めて最小限を満たす
- E. Not yet defined
- X. Other (please specify)

[Answer]: A. SQL の値が入る位置すべて（WHERE の比較値、LIKE、IN、INSERT/UPDATE の値）がバインド変数になれば最小限を満たす
**Mode:** guided | **Timestamp:** 2026-08-04T12:20:00Z

---

## Q2. 以下の能力のうち、Must Have（これがないと成立しない）はどれですか？（複数選択可）

- A. 単一値の比較条件（`=`, `<`, `>` など）のパラメータ化
- B. LIKE 条件のパラメータ化（前方一致・部分一致、およびワイルドカードのエスケープを含む）
- C. IN 句・複数値条件のパラメータ化
- D. INSERT / UPDATE の値のパラメータ化
- E. BLOB / バイナリ値の取り扱い
- X. Other (please specify)

[Answer]: A, B, C, D（すべて Must Have）
**Mode:** guided | **Timestamp:** 2026-08-04T12:20:00Z
**注記:** E（BLOB / バイナリ値）は Q2 では選択されなかったが、Q3 でも「Must ではない」として選択されなかったため、Q3 の回答により Must Have として扱う。

---

## Q3. 以下の能力のうち、Should Have / Could Have / Won't Have に振り分けたいものはどれですか？

- A. バインドできない位置（テーブル名・カラム名・ORDER BY 句）の注入不能化（許可リスト等による検証）
- B. DB 方言ごとの差異への対応（MySQL / Oracle / PostgreSQL）
- C. 既存テストの移行（生成 SQL 文字列のアサーション → バインド値のアサーション）
- D. 静的解析（CodeQL）での指摘ゼロの達成 — SM-3 の直接的な達成手段
- E. 上記はいずれも Must Have であり、振り分ける対象がない
- X. Other (please specify)

[Answer]: A のみ（識別子位置の注入不能化は Must ではない）。B（DB 方言対応）、C（既存テスト移行）、D（静的解析での指摘ゼロ）は Must Have のまま。
**Mode:** guided | **Timestamp:** 2026-08-04T12:20:00Z

---

## Q4. 能力間の依存関係について、どの認識が正しいですか？

- A. 値をバインドで運ぶ共通の仕組みが先に必要で、個別の条件種別（LIKE、IN 等）はその上に乗る
- B. 各条件種別は独立しており、任意の順序で対応できる
- C. DB 方言対応は他のすべてに依存する（共通部分が固まってから）
- D. A と C の両方
- E. Not yet defined — Application Design（2.6）で明らかにする
- X. Other (please specify)

[Answer]: E. Not yet defined — Application Design（2.6）で明らかにする
**Mode:** guided | **Timestamp:** 2026-08-04T12:20:00Z

---

## Q5. 実装の順序づけについて、どの方針を採りますか？

- A. Risk-first — 最も不確実な部分（例: LIKE のエスケープ機構、方言差）を先に潰す
- B. Value-first — 最も利用頻度の高い経路（単純な WHERE 条件）から先に安全にする
- C. Dependency-first — 共通基盤から順に、依存関係の順序どおりに進める
- D. Walking-skeleton-first — 1 経路を端から端まで通してから横に広げる
- E. Not yet defined — Delivery Planning（2.8）で決める
- X. Other (please specify)

[Answer]: D. Walking-skeleton-first — 1 経路を端から端まで通してから横に広げる
**Mode:** guided | **Timestamp:** 2026-08-04T12:28:00Z
**注記:** 初回提示時にユーザーから「意味がわからない」と回答があったため、各選択肢を本案件の具体例（単純な WHERE 条件 1 経路 / LIKE エスケープ・Oracle rownum の `?` 衝突 / 共通のバインド基盤）に置き換えて再説明した上で再提示した。

---

## Q6. 特定の能力に紐づく期限（ハードデッドライン）はありますか？

- A. なし — 期限は設定されていない
- B. あり（対象の能力と期限を記述してください）
- C. 期限はないが、v2.0 のリリース前にすべて完了している必要がある
- X. Other (please specify)

[Answer]: A. なし — 期限は設定されていない
**Mode:** guided | **Timestamp:** 2026-08-04T12:20:00Z

---

## Q7. 明示的にスコープ外（Won't Have this time）とするものはありますか？（複数選択可）

- A. 利用側アプリケーションの修正 — tamacat-dao の外は対象外
- B. SQL インジェクション以外のセキュリティ課題（認証・認可、ログ、暗号化など）
- C. パフォーマンス最適化（PreparedStatement キャッシュなど）— 安全性が先、最適化は後
- D. 新規 DB 方言のサポート追加
- E. 対象外として明示したいものはない
- X. Other (please specify)

[Answer]: A, C, D（B は選択されず → Q8 参照）
**Mode:** guided | **Timestamp:** 2026-08-04T12:20:00Z

---

## Q8（フォローアップ）. Q7 で B「SQL インジェクション以外のセキュリティ課題」がスコープ外として選択されなかったのは意図的ですか？

- A. 選び漏れ — SQLi 以外のセキュリティ課題も明示的に Won't Have とする
- B. 意図的に残した — 作業中に目についたセキュリティ問題は、SQLi 以外でも対応したい（境界を閉じない）
- C. Not yet defined — 現時点では決めない
- X. Other (please specify)

[Answer]: B. 意図的に残した — 作業中に目についたセキュリティ問題は、SQLi 以外でも対応したい（境界を閉じない）
**Mode:** guided | **Timestamp:** 2026-08-04T12:20:00Z

---

## 回答分析（Answer analysis）

**優先順位づけの検証:** Q2 と Q3 の回答により、識別された 8 能力のうち 7 件が Must Have となった（識別子位置の注入不能化のみが非 Must）。`.claude/knowledge/aidlc-product-agent/product-guide.md` の MoSCoW ガイダンスは「Must Haves should not exceed 60% of planned capacity」「If everything is a Must Have, you have not prioritized — push back」としており、Q9 で確認する。

**境界の検証:** `intent-statement.md` の `## Initial Scope Signal` はプロダクト境界を「tamacat-dao ライブラリ内の SQL 生成／実行経路のみ」と確定している。Q8=B（SQLi 以外のセキュリティ課題について境界を閉じない）はこれと解釈次第で両立するが、適用範囲が変わるため Q10 で確認する。

---

## Q9. Must Have が 8 能力中 7 件になりました。この配分でよいですか？

MoSCoW の一般則では Must Have を絞ることが推奨されますが、セキュリティ修正では「部分的な適用は誤った安心を生む」という反論も成立します。

- A. このままでよい — 部分的にパラメータ化された SQL 層は誤った安心を生むため、全部が Must Have で正しい
- B. 絞りたい — Must Have は「値が入る位置のパラメータ化」（Q2 の A〜D）に限り、DB 方言対応・既存テスト移行・静的解析での指摘ゼロは Should Have に落とす
- C. 別の絞り方をしたい（内容を記述してください）
- X. Other (please specify)

[Answer]: A. このままでよい — 部分的にパラメータ化された SQL 層は誤った安心を生むため、全部が Must Have で正しい
**Mode:** guided | **Timestamp:** 2026-08-04T12:35:00Z
**注記:** MoSCoW の一般則（Must Have は容量の 60% 以下）からの意図的な逸脱。理由はセキュリティ修正における部分適用の危険性であり、ユーザーが明示的に選択した。

---

## Q10. Q8-B（SQLi 以外のセキュリティ課題にも対応しうる）は、確定済みのプロダクト境界とどう両立させますか？

`intent-statement.md` はプロダクト境界を「tamacat-dao ライブラリ内の SQL 生成／実行経路のみ」と確定しています。

- A. 境界内に限る — SQL 生成／実行経路の中で見つかった問題（リソースリーク、例外の握りつぶし等）なら SQLi 以外でも対応する。経路の外（認証・認可・暗号化など）は対象外
- B. 発見時に都度判断する — 明示的な Won't Have にはせず、見つかった時点で対応するかを判断する（本ステージでは範囲を確定しない）
- C. 境界を広げる — tamacat-dao 全体のセキュリティ課題を対象に含める（プロダクト境界の変更を伴う）
- X. Other (please specify)

[Answer]: B. 発見時に都度判断する — 明示的な Won't Have にはせず、見つかった時点で対応するかを判断する（本ステージでは範囲を確定しない）
**Mode:** guided | **Timestamp:** 2026-08-04T12:35:00Z
**注記:** 本ステージでは範囲を確定しないという回答のため、成果物では `## Assumptions & Open Questions` に未確定事項として記録する。プロダクト境界（SQL 生成／実行経路のみ）は変更しない。

---

## Consolidated Summary Confirmation

**Prompt:** これで内容は正しいですか？（成果物を生成する前の最終確認）

- A. Looks correct — この回答内容で成果物（scope-document.md / intent-backlog.md）を生成する
- B. Request changes — 生成前にひとつ以上の回答を修正する

[Answer]: A. Looks correct
**Mode:** guided | **Timestamp:** 2026-08-04T12:38:00Z

---

---

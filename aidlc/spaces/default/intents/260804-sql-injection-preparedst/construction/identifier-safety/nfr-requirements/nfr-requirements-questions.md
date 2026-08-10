# NFR Requirements Questions — U5 `identifier-safety`

Unit: **U5 `identifier-safety`**（kind: `library`、Should Have）
Stage: NFR Requirements（3.2）/ Construction

## Sources

- `business-logic-model.md` / `business-rules.md` / `domain-entities.md`（functional-design 3.1, U5）— `IdentifierRules`（新規 public 型）、BR-1〜BR-14、R-1〜R-6、既存の柔軟性を狭める副作用（R-6：文字列リテラルを含む ORDER BY 式が拒否されるようになる）。
- `requirements.md` — CON-1、CON-6、NFR-1（対象外）、NFR-3、FR-1.7、AC-10b。
- **U1〜U4 の `security-requirements.md` / `tech-stack-decisions.md`** — SEC-1〜22、R-1〜18、TSD-1〜15。U5 はこれらを継承し、番号を SEC-23、R-19、TSD-16 から振る。

**U1 との類似点**: U5 は `IdentifierRules` という**新規クラス**を追加する——U2〜U4 が既存クラスの書き換えだったのに対し、U1 と同じ「新規クラスを追加する」性質を持つ。JaCoCo ゲートの適用可否（Q1）はこの類似性ゆえに U1 の先例（拡張する）が U2〜U4 の先例（拡張しない）より近い。

設問は 2 件。

---

## Q1. JaCoCo のカバレッジゲートに `IdentifierRules` を含めるか

U2・U4 は「既存クラスの一部だけを書き換える」性質のため拡張を見送った（TSD-6、TSD-11）。U5 の `IdentifierRules` は**U1 と同じ新規クラス**であり、その性質は当てはまらない。

- **A. 含める。** `IdentifierRules` を `jacoco-maven-plugin` の `<includes>` に追加し、U1 の 8 クラスと同じ 80% ラインでゲートする（**推奨**）
- **B. 含めない。** Should Have の小さなヘルパクラスであり、テスト件数（NFR-6）で十分
- **X.** Other (please specify)

論点: `IdentifierRules` は U1 の 8 クラスと同じ「新規・状態を持たない・単体テストで完結する」性質を持ち、U1 の TSD-3 が確立した基準（新規クラスに限ってゲートする）にそのまま合致する。B を選ぶ理由は乏しい——U2/U4 が挙げた「既存の未被覆コードごと測ることになる」という却下理由が U5 には当てはまらないためである。

[Answer]: A（含める）— 2026-08-09、**Mode:** guided

---

## Q2. `MIGRATION.md` に識別子検証による挙動変更を追記するか

U5 の変更は**観測可能な挙動の破壊的変更**を含む——現行は危険な字面を含む識別子もそのまま SQL に連結されていたが（拒否されず実行されるか DB 側で構文エラーになる）、変更後は `InvalidParameterException` が生成時に投げられる。これは U2/U4 が追記した「性能特性の変化」より直接的な**互換性への影響**である。

- **A. 追記する。** `MIGRATION.md`「4. 注意点」に、`Sort.sort` の非 `Column` キーと `Column.functionName(...)` に渡す文字列がクォート等の字面を含むと例外になることを明記する（**推奨**）
- **B. 追記しない**
- **X.** Other (please specify)

論点: U2 の Q4 = B が確立した `MIGRATION.md` の目的（利用者への実務上の周知）に照らすと、U5 の変更は U2/U4 のどの追記よりも**直接的に既存呼び出しを壊しうる**（文字列リテラルを含む ORDER BY 式、`getFunctionName()` にクォートを含む値を渡す既存コード）。追記しない理由が見当たらない。

[Answer]: A（追記する）— 2026-08-09、**Mode:** guided

---

## Ambiguity and Contradiction Analysis

**曖昧な回答**: なし。

**矛盾**: なし。

---

## Consolidated Summary Confirmation

**Prompt**: この内容で `security-requirements.md` / `tech-stack-decisions.md` を生成してよいか。

**Options**:
- Looks correct — この回答から成果物を生成する
- Request changes — 生成前に回答を修正する

**回答の要約**

| # | 決定 | 帰結 |
|---|---|---|
| Q1 | **A** — JaCoCo ゲートに `IdentifierRules` を含める | U1 の先例（新規クラスはゲート対象）に従う |
| Q2 | **A** — `MIGRATION.md` に挙動変更を追記する | 既存呼び出しを壊しうる変更であり、利用者への周知が必要 |

[Answer]: Looks correct — 2026-08-09

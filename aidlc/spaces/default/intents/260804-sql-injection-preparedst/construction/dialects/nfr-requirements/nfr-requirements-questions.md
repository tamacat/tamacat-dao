# NFR Requirements Questions — U4 `dialects`

Unit: **U4 `dialects`**（kind: `library`）
Stage: NFR Requirements（3.2）/ Construction
Depth: Standard、Test Strategy: Standard（`aidlc-state.md`）

## Sources

- `business-logic-model.md` / `business-rules.md`（functional-design 3.1, U4）— § 1〜§ 9、BR-1〜BR-21、R-1〜R-6（U4 番号）、既知の欠陥 1〜5
- `requirements.md` — CON-1（Java 8）、CON-6（compile 依存）、NFR-1〜NFR-7、FR-5.1〜5.3、FR-6.1 / 6.3 / 6.4
- `technology-stack.md`（codekb）
- **U1 `bind-foundation` / U2 `select-path` の `security-requirements.md` / `tech-stack-decisions.md`** — SEC-1〜17、R-1〜15、TSD-1〜10。U4 はこれらを継承し、番号を SEC-18 / R-16 / TSD-11 から振る

**produces が 2 件である理由**: U4 の kind は `library`（`unit-of-work.md`）であり、`produces_kinds` により performance / scalability / reliability は `service` / `ui` kind 限定である。エンジンのディレクティブも `security-requirements` と `tech-stack-decisions` の 2 件だけを返している。

**U4 が新しいセキュリティ面を作らないこと**: U4 は新規クラスを 0 個作り、既存 3 クラス（`MySQLDao` / `OracleDao` / `OracleSearch`）を修正する。値のバインド化は U1 / U2 / U3 が確立した機構をそのまま使い、U4 が新たに作る唯一の値関連の決定（ページング境界値をバインドしない、Q2 = C）は「型による安全化」であり文字列連結ではない。したがって STRIDE の T（Tampering）に対して新しい脅威は生じない。設問は 2 件に絞った——**U1 の JaCoCo ゲート適用範囲**と**移行ガイドへの追記**である。

---

## Q1. JaCoCo のカバレッジゲート（U1 TSD-3）を U4 の変更 3 クラスに拡張するか

U2 は同じ問いに **A（拡張しない）** と答えた（U2 `tech-stack-decisions.md` TSD-6）。U4 も同じ性質を持つ——`MySQLDao` / `OracleDao` / `OracleSearch` はいずれも既存レガシークラスであり、U4 はその一部（`searchList` / `OracleValueConvertFilter.convertValue`）だけを書き換える。

**U4 固有の事情**: `OracleDao` は現在テストが 1 本もない（`OracleSearchTest` は `OracleSearch` のみを対象とする）。`OracleDaoTest` を新設する予定だが（`business-logic-model.md` § 9-2）、ゲート化するかどうかは別の問いである。

- **A.** 拡張しない。ゲート対象は U1 の 8 クラスのまま（**推奨**。U2 と同じ理由——既存クラスを `<includes>` に加えると U4 と無関係な未被覆コードごと測ることになり、数字がゲートの意味を持たなくなる）
- **B.** `OracleDao` だけを新規に追加する（U4 で初めてテストが付くクラスであるため）
- **X.** Other (please specify)

論点: B は「初めてテストが付くのだから記録を残す」という理由付けだが、`OracleDao` には `searchListForOracle` 以外にも `createSearch()` 等の既存メソッドがあり、それらは U4 で触らない。クラス単位のゲートである以上、B も U2 が却下した「既存の未被覆コードごと測る」問題を抱える。U4 の主要な失敗様式（W-3 の逆転、`for update` の二重化）はいずれも被覆率では検出できない——分岐が実行されても誤った SQL が生成されるだけで、行としては通る。

[Answer]: A（拡張しない）— 2026-08-09、**Mode:** guided

---

## Q2. U2 が新設した `MIGRATION.md` に、U4 が引き起こす観測可能な挙動変更を追記するか

U2 `tech-stack-decisions.md` TSD-9 が `MIGRATION.md` を新設した。U4 は Oracle のページングを全行スキャンから rownum 方式に切り替える（`unit-of-work.md` U4「挙動が変わる点」）——これは public API の破壊ではないが観測可能な挙動変更であり、FR-7.2 が求める文書化の対象になりうるかを確認する。

- **A.** 追記する。`MIGRATION.md` に「4. 注意点」の追加項目として、Oracle のページング挙動変更（全行スキャン → rownum）を記す（**推奨**）
- **B.** 追記しない。FR-7.2 は raw-SQL 経路の文書化（FR-7.1 に対応する経路）を対象にしており、Oracle のページング挙動変更はそれとは別の性質（欠陥是正の副作用）である
- **X.** Other (please specify)

論点: `MIGRATION.md`「3. SM-1 の対象外に残る経路」の性質とは異なり、Oracle のページング変更は SM-1 に関わらない挙動変更である。FR-7.2 の文言（「raw 経路の各経路と代替経路の文書化」）を厳密に読むと B が妥当に見えるが、`MIGRATION.md`「1. 何が変わったか」節は既に「SELECT 経路が PreparedStatement によるバインドに切り替わったこと」を書いており、Oracle のページング変更もこの節の自然な延長にある。**FR-7.2 の要求を超えるが、`unit-of-work.md` が明示的に「観測可能な挙動の変更」と記録した事実を利用者向け文書のどこにも書かないのは片手落ちである。**

[Answer]: A（追記する）— 2026-08-09、**Mode:** guided

---

## Ambiguity and Contradiction Analysis

**曖昧な回答**: なし。

**矛盾**: なし。U1 / U2 の決定を継承するのみで、3.1 の成果物に対する修正は生じない。

**追加の設問**: 不要。

---

## Consolidated Summary Confirmation

**Prompt**: この内容で `security-requirements.md` / `tech-stack-decisions.md` を生成してよいか。

**Options**:
- Looks correct — この回答から成果物を生成する
- Request changes — 生成前に回答を修正する

**回答の要約**

| # | 決定 | 帰結 |
|---|---|---|
| Q1 | **A** — JaCoCo ゲートを U4 に拡張しない | U1 TSD-3 の `<includes>` は不変。U4 の主要な失敗様式は被覆率で検出できない |
| Q2 | **A** — `MIGRATION.md` に Oracle のページング挙動変更を追記する | FR-7.2 の対象を超える追記だが、利用者への実務上の周知として行う |

[Answer]: Looks correct — 2026-08-09

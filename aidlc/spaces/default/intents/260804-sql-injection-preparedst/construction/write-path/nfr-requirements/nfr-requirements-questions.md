# NFR Requirements Questions — U3 `write-path`

Unit: **U3 `write-path`**（kind: `library`）
Stage: NFR Requirements（3.2）/ Construction
Depth: Standard、Test Strategy: Standard（`aidlc-state.md`）

## Sources

- `business-logic-model.md`（functional-design 3.1, U3、iteration 2 適用記録込み）— § 2 / § 3（INSERT / UPDATE のバインド版、BR-1〜BR-7）、§ 4（DELETE、`buildBindWhere` 共有ヘルパ）、§ 5 / § 5.1 / § 5.2（BLOB 実行の `DBAccessManager` / `Dao` / `DaoAdapter` 側の追加）、§ 6（`Dao` の既定実装、BLOB は自動判定しない）、§ 7（`getBlobIndex()` の二重の意味）、§ 9（新規 public / protected メンバ、Pr-1〜Pr-10）、`## Review` の適用記録（B-1〜B-3、N-1、N-2 の是正と N-3/N-5〜N-10 の未対応リスト）
- `business-rules.md`（同上）— BR-1〜BR-17、失敗様式 1〜3、残存リスク R-1〜R-4、Unit をまたぐ要件の遵守表
- `requirements.md`（requirements-analysis 2.3）— FR-1.4（INSERT VALUES / UPDATE SET のバインド化、Must）、FR-1.5（BLOB は現行同様バインド、位置は変わりうる、Must）、FR-3.2（`getBlobIndex()` はバインドパラメータ位置を返す、Must）、FR-6.2（`:268` OBJECT 分岐のテーブル修飾不整合の解消、Must）、NFR-1（実行経路に値の文字列連結が 0 件）、NFR-3（public API 破壊的変更なし）、NFR-5（全テストグリーン）、NFR-6（テスト件数 >= 138）、CON-1（Java 8）、AC-3b、AC-7
- `technology-stack.md`（codekb）— compile スコープ依存 3 件、ビルド JVM が JDK 25 Corretto、カバレッジ計測ツールの不在
- U1 `bind-foundation` の `security-requirements.md` / `tech-stack-decisions.md` — SEC-1〜SEC-9、R-1〜R-7、TSD-1〜TSD-5（とくに TSD-3、JaCoCo ゲートを U1 の新規 8 クラスに限定）
- U2 `select-path` の `security-requirements.md` / `tech-stack-decisions.md` — SEC-10〜SEC-17、R-8〜R-15、TSD-6〜TSD-10（とくに Q4=B の Javadoc ＋ `MIGRATION.md` の方針、Q2=A のカバレッジゲート非拡張の先例）

**produces が 2 件である理由**: U1・U2 と同じく U3 の kind は `library`（`unit-of-work.md` U3 の表）であり、`produces_kinds` により performance / scalability / reliability は `service` / `ui` 限定である。エンジンのディレクティブも `security-requirements` と `tech-stack-decisions` の 2 件だけを返している。

**U1・U2 と U3 で問いの性質が変わる。** U1 は新規クラスを足す Unit、U2 は既存 5 クラスの書き換えだが**新経路への切り替えが自動**（`Dao.search` を呼べば必ずバインド版を経由する）だった。U3 は既存クラスの書き換えである点は U2 と同じだが、**BLOB を扱う書き込みだけは自動的に新経路へ乗らない**——`Dao` / `DaoAdapter` の既定 `create`/`update` は常に非 BLOB 経路（`executeUpdate(PreparedSql)`）を使い、BLOB を含む書き込みはサブクラスが自分で override して `executeUpdate(PreparedSql,int,InputStream)` を呼ぶ必要がある（BR-11、iteration 1 で確定した責任分担）。**この事実を利用者にどう伝えるか**が U3 固有の論点になる。

設問は 2 件。

---

## Q1. カバレッジゲート（U1 の TSD-3）を U3 に拡張するか

U2・U4・U5 で繰り返し検討され、いずれも拡張しない判断が採られている（U2 Q2=A、以降の Unit も同じ判断を踏襲）。U3 でも同じ論点が成立するかを確認する。

### 事実

| 項目 | 状態 |
|---|---|
| U3 が変更するクラス | `QueryImpl`（INSERT / UPDATE / DELETE のバインド版 3 メソッド ＋ `buildBindWhere` ヘルパ）、`Dao`、`DaoAdapter`、`DBAccessManager` |
| U3 が新設するクラス | **なし** |
| これらの既存クラスの現在の被覆率 | 不明（U1 が JaCoCo を導入したのは新規 8 クラスのみ） |
| `org.md` の 80% floor | 本 scope `sql-parameterization` には課されていない（U1 Q1 で確認済み、以降の Unit でも踏襲） |
| U3 で最も危険な失敗様式 | 失敗様式 1（SET と WHERE の値が 1 つずつずれる）。`?` の個数検査を通り抜けたまま値が誤った位置にバインドされるため、**line coverage では検出できない**（`business-rules.md` 失敗様式 1） |

### 選択肢

- **A.** 拡張しない。TSD-3 のゲート対象は U1 の新規 8 クラスのままとし、U3 は AC-3b / AC-7 の mock 位置・値アサートを Build and Test（3.6）の必須検証項目として引き渡す（**推奨**）
- **B.** 変更した 4 クラスをゲート対象に加える（しきい値は 80%）
- **C.** 4 クラスをゲート対象に加えるが、既存の未被覆コードを考慮して低いしきい値にする
- **X.** Other (please specify)

論点: A の根拠は U2 Q2 と同型である。(1) 4 クラスは既存レガシーであり、U3 の変更部分だけを分離して測る手段が JaCoCo にはない。(2) line coverage は U3 の主要な失敗様式（値の位置ずれ）を検出しない——行は実行されるが誤った値がバインドされるだけである。検出できるのは mock の位置・値アサート（AC-3b、AC-7）であり、これは被覆率とは別の規律である。

[Answer]: A（拡張しない。TSD-3 のゲート対象は U1 の 8 クラスのまま）— 2026-08-10、**Mode:** guided

---

## Q2. BLOB を扱うサブクラスに要求される override 責務を、どの形で利用者に伝えるか

**これは U3 固有の論点である。** U1・U2 の新経路は呼び出し側が何もしなくても自動的に安全になる（`Dao.search` を呼べば必ずバインド版を経由する）。しかし U3 の BLOB 経路は違う——`Dao` / `DaoAdapter` の既定 `create(T)` / `update(T)` は常に `executeUpdate(PreparedSql)`（非 BLOB 版）を呼び、BLOB カラムを持つエンティティであってもバインド化された `PreparedSql` の**テキストと非 BLOB の値**しか運ばない。BLOB の実データを書き込むには、サブクラスが `create`/`update` を override し、`getInsertPreparedSql(data)` 等で `PreparedSql` を得て `getBindIndexOf(DataType.OBJECT, 1)`（または `getBlobIndex()`）で位置を取得し、`InputStream` とともに新設の `executeUpdate(PreparedSql, int, InputStream)` を明示的に呼ぶ必要がある（`business-logic-model.md` § 6、BR-11）。

### 事実

| 項目 | 状態 |
|---|---|
| 現行の責任分担 | `Dao.create`/`update` の**既定実装も現状 BLOB を自動判定していない**（`Dao.java:224-234` は `String` 版 `executeUpdate` のみを呼ぶ）。今回の変更で新たに生じる制約ではなく、現行の責任分担がバインド版に引き継がれるだけである |
| 実際に BLOB を override しているサブクラス | リポジトリ全体を検索したが `Dao.executeUpdate(String,int,InputStream)` の呼び出し元も `getBlobIndex()` の呼び出し元も**製品コード・テストコードのいずれにも存在しない**（U3 functional-design、iteration 2 で確認済み） |
| FR-7.2（Must、U2 が既に文書化した対象） | 「SM-1 対象外の raw 経路」の文書化であり、BLOB override の責務とは別の要件——BLOB 経路自体は SM-1 の対象内（バインドされる）であり、対象外にはならない |
| U2 Q4=B が確立した文書化の形 | Javadoc ＋ リポジトリルートの `MIGRATION.md`（旧経路 → 新経路の対応表を 1 か所にまとめる） |

### 選択肢

- **A.** Javadoc のみ。`Dao.getInsertPreparedSql(T)` 等 protected 拡張点と `executeUpdate(PreparedSql,int,InputStream)` の Javadoc に、BLOB を扱うサブクラスは override が必要である旨と実装例を書く
- **B.** A に加えて、U2 Q4=B が新設した `MIGRATION.md` に BLOB override のセクションを追加し、`business-logic-model.md` § 6 のコード片相当の実装例を載せる（**推奨**）
- **C.** 本ステージでは形を決めず、Build and Test（3.6）に委ねる
- **X.** Other (please specify)

論点: A は最小コストだが、BLOB override は「非推奨 API の呼び出しを避ける」という U2 の文書化対象とは性質が異なる——**既存の呼び出し元が 0 件のため、この情報の唯一の到達経路は文書である**（非推奨警告のような IDE 上のヒントが機構として存在しない）。B は既に `MIGRATION.md` という置き場が確立しているため追加コストが小さく、他の移行情報と同じ場所にまとまる。C は FR-1.5 / FR-3.2 が要求する「BLOB は現行同様動作する」ことの実装可能性を後段に丸投げすることになり、Must 要件の文書化を欠いたまま Unit を完了させることになる。

**呼び出し元が 0 件であることの意味**: A / B いずれを選んでもリポジトリ内のテストへの影響はない。判断は「ライブラリが BLOB を扱う外部利用者に対してこの変更をどう伝えるか」だけで決まる。

[Answer]: B（Javadoc ＋ `MIGRATION.md` に BLOB override のセクションを追加し、実装例を載せる）— 2026-08-10、**Mode:** guided

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。2 件とも単一の選択肢が確定している。

**定量目標の欠落**: Q1=A により U3 には被覆率の数値目標が存在しない。これは U2・U4・U5 と同じ意図的な不設定であり、代わりに AC-3b / AC-7 の mock 位置・値アサートが**数えられる**判定基準を置く。NFR-6（テスト件数 >= 138）も引き続き掛かる。

**矛盾**: なし。Q1 は U2 Q2 の先例をそのまま踏襲し、Q2 は U2 Q4 が確立した文書化の置き場（`MIGRATION.md`）を再利用する形であり、いずれも既存の決定と整合する。

| # | 組み合わせ | 確認内容 | 判定 |
|---|---|---|---|
| 1 | Q1=A ＋ `org.md` `## Testing Posture` | 80% floor は本 scope に課されていない | **矛盾なし** |
| 2 | Q1=A ＋ NFR-6（件数 >= 138） | AC-3b / AC-7 のテスト追加で件数は増える方向 | **矛盾なし** |
| 3 | Q2=B ＋ CON-1（Java 8） | `MIGRATION.md` は Markdown ファイルであり言語機能に触れない | **矛盾なし** |
| 4 | Q2=B ＋ FR-7.2（U2 が既に確定した文書化義務） | `MIGRATION.md` への追記であり、既存の対応表の形式（旧経路 → 新経路）を破らない | **矛盾なし。** 同一ファイル内にセクションを追加するのみ |
| 5 | Q2=B ＋ BR-11（BLOB は自動判定しない責任分担） | 文書化する内容が BR-11 の記述と一致しているか | **矛盾なし。** Javadoc と `MIGRATION.md` はいずれも BR-11 の記述をそのまま利用者向けに翻訳したものである |

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
| Q1 | **A** — カバレッジゲートを U3 に拡張しない | TSD-3 のゲート対象は U1 の 8 クラスのまま。規律は AC-3b / AC-7 の mock アサートに委ねる |
| Q2 | **B** — Javadoc ＋ `MIGRATION.md` に BLOB override のセクションを追加 | FR-1.5 / FR-3.2 の実装可能性を利用者に伝える手段が確定。`tech-stack-decisions.md` に既存ファイルへの追記として記録 |

[Answer]: Looks correct — 2026-08-10、**Mode:** guided

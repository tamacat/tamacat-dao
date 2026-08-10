# Domain Entities — U5 `identifier-safety`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U5 の責務「バインドできない位置（テーブル名・カラム名・ORDER BY 句）に呼び出し側から渡された文字列が、SQL の構文として解釈されうる形でそのまま連結されない状態にする」、境界（値のバインドには触れない、U1〜U4 が触るクラスを変更しない）、依存 U2。
- **`unit-of-work-story-map.md`**（同上）— U5 が担う FR-1.7（Should）、判定する AC-10b、Unit 内の実装順序 1〜4。
- **`requirements.md`**（requirements-analysis 2.3）— FR-1.7、AC-10b、NFR-1（対象範囲は FR-1.7 を含まない）、NFR-3、CON-1。
- **U2 `select-path` の 3.1 成果物** — § 11 引き継ぎ 6（`orderBy(Sort)` と `col.getFunctionName()` は識別子位置であり U2 は `Sort` に触れていない）、`business-rules.md` R-4。

`functional-design-questions.md` の Q1 = A（例外による拒否）、Q2 = A（`Sort` の非 `Column` 枝と `QueryImpl` SELECT 句の両方に適用）が本文書の前提である。

---

## この文書の範囲

**U5 は既存クラス（`Sort` / `Column` / `QueryImpl`）にフィールドを 1 つも追加しない。** 検証は新設の public ヘルパ `IdentifierRules`（状態を持たない）として追加する——`Sort` / `QueryImpl` とは別パッケージ（`org.tamacat.sql`）にあるため package-private では届かず、public にせざるを得ない（§ 1「`public` である理由」、A-1 / A-2）。**U1 の `ResultSetHandler<R>`（A-7）、U2 の `Search.getSearchParam()` の public 化と同種の、新規 public 型・メンバの追加である。**

| 対象 | 新規フィールド | 新規 public 型・メンバ |
|---|---|---|
| `Sort` | なし | なし（呼び出し元として `IdentifierRules.validate` を使うのみ） |
| `QueryImpl` | なし | なし（同上） |
| `org.tamacat.sql`（新設） | なし（状態なし） | **`IdentifierRules`（public 型）、`validate(String)`（public static メンバ）** |

---

## `IdentifierRules` — 状態を持たない検証ヘルパ

U1 の `ValueRules`（値の分類・検証）と対になる、識別子専用のヘルパである。**U1 の `ValueRules` を再利用しない**——`ValueRules` は `Column` の `DataType` を前提にした値の分類規則であり、識別子は型を持たない生の文字列であるため対象が異なる（`unit-of-work.md` U5 の境界「値のバインドには触れない」とも整合する）。

### 属性

状態を持たない。すべて `static` メソッドである。

### 検証する字面（ID-1〜ID-5）

`validate` が拒否する**字面**は次の 5 種類に限る。

| # | 拒否する字面 | 理由 |
|---|---|---|
| ID-1 | `'`（シングルクォート） | 文字列リテラルの境界を偽装できる |
| ID-2 | `"`（ダブルクォート） | 一部 DB で識別子の引用に使われ、境界を偽装できる |
| ID-3 | `;`（セミコロン） | 文の終端・複文注入に使われる |
| ID-4 | `--`（2 文字連続） | 行コメント開始。後続のテキストを無効化できる |
| ID-5 | `/*` および `*/` | ブロックコメントの開始・終了。テキストの一部を無効化できる |

**`null` 入力は字面の検証（ID-1〜ID-5）とは別の、先行するガード条件である。** `null` に対して `indexOf` を呼ぶと `NullPointerException` になるため、`validate` の先頭で明示的に `null` チェックする。**`null` は例外を投げず素通しする**（BR-14。`QueryImpl` の `col.getFunctionName()` が `null` を返しうる既存の正当な状態と衝突しないための訂正——`business-logic-model.md` § 1 参照）。

### 不変条件

| # | 不変条件 | 破れたときの挙動 |
|---|---|---|
| IR-1 | `validate(String raw)` は ID-1〜ID-5 のいずれかを含む入力に対して必ず `InvalidParameterException` を投げる | 呼び出し元の実装ミス（テストで検出） |
| IR-2 | `validate(String raw)` は ID-1〜ID-5 のいずれも含まない入力に対して**入力をそのまま返す**（変換・エスケープ・正規化を行わない） | — |
| IR-3 | `validate(null)` は `null` を返す（例外を投げない）。`NullPointerException` にもならない | `raw == null` の先頭ガードが `indexOf` 呼び出しより先に評価される（BR-14） |
| IR-4 | 検証は**冪等**——同じ入力に対して常に同じ結果（例外の有無）を返す | 状態を持たないため構造的に成立 |

**IR-2 が「エスケープしない」理由**: U1 が確立した方針（バインド経路ではクォートエスケープを適用しない、SEC-3）と対称である。識別子はバインドできない位置にあるため、エスケープによる安全化ではなく**拒否**による安全化を採る——エスケープされた識別子は多くの DB で構文的に無効になるか、意図と異なる識別子を参照してしまう。

### ライフサイクル

状態遷移を持たない。呼び出しのたびに独立して判定する。

```
Sort.sort(Object k, Object o)  ──> k が Column でない ──> IdentifierRules.validate(k.toString()) ──> 例外 or 通過
QueryImpl の SELECT 句組み立て ──> col.isFunction() ──> IdentifierRules.validate(col.getFunctionName()) ──> 例外 or 通過
```

---

## `Sort` の状態（変更なし）

`Sort.sort` の内部状態（`StringBuilder sort`）は変更しない。**`Sort` クラスへの変更はメソッド本体 1 行（検証呼び出しの追加）のみであり、フィールドもシグネチャも変わらない。**

| 状態 | 型 | 変更 |
|---|---|---|
| `sort` | `StringBuilder` | 不変（`Sort.java:33`） |

---

## `QueryImpl` の状態（変更なし）

SELECT 句の組み立て（`select` の `StringBuilder`）に状態の変更はない。検証は既存のループ内に 1 行追加されるだけである。

---

## 2.6 / 2.7 契約からの差分

**この節が差分の全量である。** iteration 1 は「新規 public メンバなし」と誤って記録していた——`IdentifierRules` は呼び出し元（`org.tamacat.dao.Sort`、`org.tamacat.dao.impl.QueryImpl`）と別パッケージ（`org.tamacat.sql`）にあるため、package-private では届かない（U1 `PreparedSql.getPlaceholderCount()`、U2 `Search.getSearchParam()` と同じ制約）。訂正後の差分は次のとおり。

**新規 public 型・メンバ**:

| # | 新規 public メンバ | パッケージ | 根拠 |
|---|---|---|---|
| A-1 | `IdentifierRules`（**新規 public 型**） | `org.tamacat.sql` | `Sort` / `QueryImpl` から呼ぶため。U1 A-7（`ResultSetHandler<R>`）と同じ「新設 public 型」の扱い |
| A-2 | `IdentifierRules.validate(String)`（`public static String`） | `org.tamacat.sql` | 同上 |

**新規の例外送出経路（挙動の変更）**: `Sort.sort(Object, Object)` の非 `Column` 枝と `QueryImpl` の SELECT 句組み立て（`getFunctionName()` を使う枝）に、`InvalidParameterException` を投げる新しい失敗モードが加わる。これは**観測可能な挙動の変更**である——現行は危険な字面を含む識別子もそのまま SQL に連結されていた（拒否されず、DB 側で構文エラーになるか、最悪 SQL として解釈されていた）。新経路はより早く、より明確に失敗する（U2 の BR-19 が採った「より早く、より明確に失敗する」変更という判断と同じ性質）。

**U2 BR-10（旧メソッドの戻り値不変）との関係を明示する。** U2 BR-10 は「`getSelectSQL()` は 1 文字も変わらない」ことを保証の対象にしているが、これは**正常系**（危険な字面を含まない `functionName` を渡した場合）についての保証である。U5 が追加する例外送出は**異常系**（危険な字面を含む `functionName` を渡した場合）にのみ発火し、正常系の戻り値には一切影響しない。したがって U2 BR-10 とは矛盾しない——BR-10 が保証する「戻り値の中身」ではなく「戻り値が返るかどうか（例外で落ちるか）」という直交する軸の変更である。U2 が同じ論法を FR-3.1 の互換性判定（新規メソッドの追加は互換性を破らない）に対して用いた先例（`business-logic-model.md` § 10「削除・シグネチャ変更: なし」）と整合する。**NFR-3（既存サブクラスが再コンパイルなしで動く）にも抵触しない**——`getSelectSQL()` のシグネチャ・戻り値の型は変わらず、新しい例外は `RuntimeException` の派生（`InvalidParameterException`）であり `throws` 節の変更を要さない。

**削除・シグネチャ変更**: なし。

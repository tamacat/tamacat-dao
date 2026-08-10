# Security Requirements — U5 `identifier-safety`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U5）— § 1（`IdentifierRules.validate`）、§ 2（`Sort` への適用）、§ 3（`QueryImpl` への適用）、§ 4（`DataType.FUNCTION` の対象外確認）。本文書の脅威モデルはこの範囲を対象にする。
- **`business-rules.md`**（同上）— BR-1〜BR-14、R-1〜R-6。本文書はこれらのうちセキュリティに効くものを要件として固定する。
- **`requirements.md`**（requirements-analysis 2.3）— CON-1、CON-6、NFR-1（対象外）、NFR-3、FR-1.7、AC-10b。
- **`technology-stack.md`**（codekb）— compile スコープ依存 3 件のみ。
- **U1〜U4 の `security-requirements.md`** — SEC-1〜22、R-1〜18。U5 はこれらを継承し、番号を SEC-23、R-19 から振る。

`tech-stack-decisions.md` は本文書と対になる成果物で、Q1（JaCoCo ゲートへの追加）と Q2（`MIGRATION.md` への追記）を扱う。

---

## この文書の範囲

**U5 も `kind: library` である。** 認証・認可・暗号化・規制フレームワーク・データ所在地はいずれも該当なし（U1「この文書の範囲」の表を継承）。

**U1〜U4 との違い**: U1〜U4 は**値**の位置を安全にした。U5 は**識別子**の位置を安全にする——バインドできない位置であるため、機構そのものが異なる（拒否であってエスケープではない、`IdentifierRules` § 1）。U5 は U1 と同じく**新規クラス**（`IdentifierRules`）を追加する点で U2〜U4（既存クラスの書き換え）と性質が異なる。

---

## 脅威モデル（STRIDE）

| カテゴリ | U5 での評価 | 対応 |
|---|---|---|
| **S** Spoofing | 該当なし | — |
| **T** Tampering | **中心的関心。** 識別子位置への文字列注入を拒否する。ただし対象は ORDER BY の非 `Column` キーと SELECT の `getFunctionName()` の 2 経路のみ（R-5、部分的な被覆）。`Column` 経由の ORDER BY、`Sort.Order` 引数（R-23）は未検証のまま残る | SEC-23 |
| **R** Repudiation | 該当なし。U5 は実行記録に触れない | 継承 |
| **I** Information Disclosure | 該当なし。U5 は値の保持・記録面を持たない。例外メッセージに識別子文字列を含めるが、これは「値」ではなく呼び出し側が渡した SQL 断片相当の文字列である（BR-2） | SEC-25 |
| **D** Denial of Service | **限定的。** `IdentifierRules.validate` は `indexOf` の連鎖のみでバックトラックのリスクを持たない（BR-1） | — |
| **E** Elevation of Privilege | 該当なし。権限モデルを持たない | — |

---

## セキュリティ要件

番号は U4 の SEC-18〜SEC-22 に続けて SEC-23 から振る。

### SEC-23. 識別子位置に構文的に危険な字面を通さない

| 項目 | 内容 |
|---|---|
| 要件 | `Sort.sort(Object, Object)` の非 `Column` キーと `QueryImpl` の SELECT 句組み立て（`col.getFunctionName()`）に渡された文字列が、`'`、`"`、`;`、`--`、`/*`、`*/` のいずれかを含む場合、`InvalidParameterException` を投げて SQL テキストへの混入を防ぐこと |
| 判定基準 | **AC-10b（部分）** — 危険な字面を含む文字列を渡すと例外が投げられ、SQL テキストに現れない。ORDER BY の非 `Column` キーと SELECT の関数名の 2 経路について判定できる |
| 由来 | FR-1.7、BR-3、BR-7、BR-10 |

**「部分」であることを明示する。** AC-10b の Given が挙げる 3 つの識別子位置（ORDER BY キー、テーブル名、カラム名）のうち、本要件が満たすのは実質 1.5 個である——テーブル名・カラム名そのもの（`Column.columnName(String)` / `Table` のテーブル名設定を経由する経路）は対象外（R-5）。これはスコープを絞る決定であり、`Column` / `DefaultColumn` / `Table` を変更しない技術的制約から来るものではない（`business-rules.md` BR-5 の訂正参照）。

### SEC-24. `null` の識別子は素通しし、既存の正当な状態を壊さない

| 項目 | 内容 |
|---|---|
| 要件 | `IdentifierRules.validate(null)` は例外を投げず `null` を返すこと。`Column.isFunction() == true` かつ `getFunctionName() == null` という既存の正当な状態（`Column.FUNCTION` 定義のみで `functionName` を設定しない場合）を壊さないこと |
| 判定基準 | **`isFunction() == true` かつ** `getFunctionName()` が `null` を返す `Column` を SELECT 句組み立てに渡した場合、例外が投げられず現行どおり `"null"` が出力される（`isFunction() == false` の `Column` は `validate` を経由しないため対象外） |
| 由来 | BR-14（レビュー iteration 2 で発見・是正）、`DefaultColumn.java`、`ColumnFunctionTest.java` |

**セキュリティ要件というより回帰防止の要件である。** `null` はいかなる注入字面も持ちえないため、素通しは安全性を損なわない。この要件が守るのは「Should Have の追加によって既存の正当な挙動（Must 相当）を壊さない」という NFR-3 の趣旨である。

### SEC-25. 例外メッセージに識別子文字列を含めてよい（U1 SEC-4 とは異なる扱い）

| 項目 | 内容 |
|---|---|
| 要件 | `IdentifierRules.validate` が投げる例外のメッセージに、検証対象の識別子文字列を含めてよい |
| 判定基準 | ソース検査で確認できる |
| 由来 | BR-2、U2 SEC-15 の先例 |

**U1 SEC-4（例外メッセージにバインド値を含めない）とは対象が異なる。** 識別子はバインドされる値ではなく、呼び出し側が渡した SQL 断片相当の文字列であり、U2 BR-19 の早期失敗（`where(String)` 等のメッセージに SQL テキストを含めてよい）と同じ扱いにできる。

### SEC-26. 新規の第三者 compile 依存を追加しない（継承）

| 項目 | 内容 |
|---|---|
| 要件 | U5 は compile スコープの第三者依存を 1 つも追加しない |
| 判定基準 | `mvn dependency:tree` の compile スコープが変更前と一致する |
| 由来 | CON-6、U1 SEC-8 |

`IdentifierRules` は JDK 標準 API（`String.indexOf`）のみで書ける。

---

## 残存リスク

番号は U4 の R-16〜R-18 に続けて R-19 から振る。

| ID | 残存リスク | 由来 | 扱い |
|---|---|---|---|
| **R-19** | ブラックリスト（ID-1〜ID-5）は代表的な注入パターンに限られ、DB 固有の識別子引用符や Unicode の同型文字を使った迂回は対象外である | `business-rules.md` R-1 | **受容。** FR-1.7 は Should Have であり、あらゆる方言の特殊文字の完全な列挙までは要求されない |
| **R-20** | AC-10b の Given 3 位置（ORDER BY キー、テーブル名、カラム名）のうち、テーブル名・カラム名そのものは対象外 | `business-rules.md` R-5 | **受容（スコープの明示的な narrow）。** `MappingUtils.getColumnName` の呼び出し箇所を包む拡張で将来対応可能（技術的な不可能性ではない） |
| **R-21** | 文字列リテラルを含む正当な ORDER BY 式（`TO_CHAR(d,'YYYY')` 等）が拒否されるようになる——既存の柔軟性を狭める副作用 | `business-rules.md` R-6 | **受容。** 安全性と表現力の両立はブラックリスト方式では原理的に困難。`Column` 経由の代替経路がある |
| **R-22** | ブラックリストが拒否するのは述語注入ではなく識別子注入のみ。`"col1 = 1 OR 1=1"` のような危険な字面を含まない述語注入は通る | `business-rules.md` R-4 | **受容。** `Sort.sort` の非 `Column` 経路は「呼び出し側が SQL を書く」ことを前提にした raw 経路であり、U2 FR-7.1 と同じ性質の残存リスク |
| **R-23** | `Sort.sort(Object k, Object o)` の第 2 引数 `o`（ソート方向）は検証されない。`SortTest.java:41` は `o` に生の `String`（`"asc"`）を渡す用法を記述している（ただし `:39` の `@Test` はコメントアウトされており無効化されたテストである）。理論上は `o` に危険な字面を含む文字列を渡す誤用も可能である | `business-rules.md` BR-6、R-2（U5 番号） | **受容（3.1 で確定済みの判断を継承）。** `unit-of-work-story-map.md` が定めた U5 の担当範囲（識別子位置：テーブル名・カラム名・ORDER BY 句のキー）に `Order` パラメータは含まれない。`o` は通常 `Sort.Order` enum（`asc`/`desc` の 2 値）で使われる |

---

## 3.1 の規則に対する修正

**なし。** U5 のレビュー iteration 2 で 3.1 成果物自体を訂正済みであり、NFR Requirements の段階で新たに古くなる記述はない。

---

## 要件と上流の対応

| 要件 | 満たす上流要件 | 判定する AC | 検証手段 |
|---|---|---|---|
| SEC-23 | FR-1.7 | **AC-10b（部分）** | 単体テスト（U5 で判定可能。Build and Test 3.6 で網羅） |
| SEC-24 | NFR-3（回帰防止） | — | 単体テスト |
| SEC-25 | 本ステージで新規 | — | ソース検査 |
| SEC-26 | CON-6 | — | `mvn dependency:tree` の差分 |

---

## Build and Test（3.6）に送る検証項目

U1〜U4 が送った項目に加えて、U5 から次を送る。

| # | 項目 | 由来 |
|---|---|---|
| 19 | AC-10b の判定テスト（危険な字面を渡した場合の拒否と非混入） | `business-logic-model.md` § 6-1 |
| 20 | 正当な非 `Column` キー（`RAND()` 等、危険な字面を含まない複合式）の回帰テスト | § 6-2 |
| 21 | `getSelectSQL()` / `getSelectPreparedSql()` 両方が同じ検証を受けることの確認 | § 6-3 |
| 22 | `isFunction() == true` かつ `getFunctionName() == null` のケースで例外が出ないことの回帰テスト（SEC-24） | § 6-4、BR-14 |
| 23 | **AC-10b の部分被覆を AC カバレッジ集計に明記する。** U5 が最後の Unit であり、この記録の最後の機会になる | § 6-5、R-20 |

---

## Review

NOT-READY

### 検証の方法と範囲（iteration 2 / 最終）

iteration 1 の記憶を持たない状態で、`security-requirements.md` / `tech-stack-decisions.md` を `nfr-requirements-questions.md`、resolved consumes（`business-logic-model.md`、`business-rules.md`、`requirements.md`、`technology-stack.md`）、dispatch で許可された sibling の NFR 成果物、および実ソース（`Sort.java` 全文、`QueryImpl.java:135-157` / `:473-475`、`Query.java`、`DefaultColumn.java` 全文、`MappingUtils.java:128-137`、`InvalidParameterException.java`、`SortTest.java` 全文）と突き合わせ、主張を反証する方向で検算した。

### iteration 1 の 3 件の blocking について（是正の検証結果）

- **iteration 1 指摘 2（`Sort` 経路の発火点と `asc`/`desc` の欠落）— 是正を確認した。** `Sort.java:47-58` は `sort.append(...)` を呼び出しの場で実行するため、`k.toString()` を包む検証（`business-logic-model.md` § 2.2）は `sort.sort(...)` のその行で throw する。追記が 2 つの箇条書きに分かれ、`Sort` 側に「**その呼び出しの時点で**例外が投げられます（SQL 組み立て時ではありません）」、`Column.functionName` 側に「**SELECT 句を組み立てる時点で**」と書き分けられているのは実挙動どおりである（SELECT 側は `QueryImpl.getSelectSQL()`（`:136`）が組み立て時に `col.getFunctionName()` を読む）。`Sort.java:35-41` の `asc(Object)` / `desc(Object)` はいずれも `sort(k, Order.ASC/DESC)` へ委譲しており、追記本文と TSD-19 の決定行の双方が両者を名指しするようになった点も正しい。
- **iteration 1 指摘 3（上流 R-2 の後継欠落）— 是正を確認した。R-23 は根拠を持つ。** `business-rules.md` R-2 は実在し（`business-rules.md:55`）、内容も「`Sort.Order`（`o.toString()`）は検証しない／理論上 `sort.sort(col, "危険な文字列")` の誤用が可能／`unit-of-work-story-map.md` の担当範囲外として受容」であり、R-23 の要約と一致する。BR-6（`:24`）も同旨。`SortTest.java:41` は確かに `sort.sort(User.USER_ID, "asc");` で第 2 引数に生の `String` を渡している。`Sort.Order` が `asc`/`desc` の 2 値 enum であること（`Sort.java:16-31`）も確認した。R-19〜R-23 の採番に重複・欠番はなく、SEC-23〜26 / TSD-16〜19 とも衝突しない。
- **iteration 1 指摘 1（`MIGRATION.md` 追記の代替経路案内）— 是正されていない。過剰修正により誤りの向きが反転した。** 下記の指摘 1・指摘 2 を参照。ORDER BY 側を SELECT 側から書き分けた点までは正しい（`Sort.java:52` の `isFunction()` 枝は確かに `col.getColumnName()` を連結しており、`business-logic-model.md` § 2.2 の変更後コードも `Column` の 2 枝を 1 文字も変えない。BR-3 / BR-5 / BR-7 とも整合するので、`Column` 経由の ORDER BY が検証対象外であること自体は真である）。しかしそこから導かれた**具体的な回避手順**と、SELECT 側の**「回避手段がない」という断定**が、いずれも実ソースで反証される。

### その他に反証できなかった主張

- `Sort.java:52`（`isFunction()` 枝 = `col.getColumnName()` を無検証で連結）と `Sort.java:54` / `QueryImpl.java:154`（`MappingUtils.getColumnName(col)`、無検証）は実測どおりで、`business-logic-model.md` § 2.2 / § 3.2 の変更後コードはこの 3 箇所を変更しない。「ORDER BY の `Column` 経路は検証対象外」は真である。
- SEC-23〜26 の由来（FR-1.7、BR-3、BR-7、BR-10、BR-14、CON-6）は 3.1 / 2.3 の原文と一致する。SEC-24 の `validate(null) → null` は `business-logic-model.md` § 1 のコード（`if (raw == null) return null;`）および BR-14 と一致する。
- `InvalidParameterException` は `IllegalArgumentException` を継承する非検査例外であり（`InvalidParameterException.java:7`）、「既存の呼び出しがコンパイルできなくなる」種類の破壊ではない——追記が「破壊的変更」を実行時の挙動変更として説明しているのは正確である。
- TSD-17 / TSD-18 の「変えないことの確認」は実 `pom.xml` と整合する（compile スコープ 3 件、`1.8`）。`IdentifierRules` が使う API は `String.indexOf` のみで Java 8 制約に抵触しない。

### 指摘 1（blocking）— 追記の「SELECT 句には同等の回避手段がありません」は偽であり、代わりに案内された `where(String)` は SELECT 句の投影を表現できない

追記本文（TSD-19「追記する内容」）はこう断定する。

> **ただし SELECT 句の `Column.functionName(String)` には同等の回避手段がありません**——検証対象の `getFunctionName()` を経由しない代替経路は用意されていないため、文字列リテラルを含む関数式が必要な場合は、開発者が書いた SQL 断片として扱われる raw 経路（`where(String)` 等、FR-7.1 の対象）を使う代替を検討してください。

2 点とも誤っている。

**(a) 「`getFunctionName()` を経由しない代替経路は用意されていない」は偽。** SELECT 句組み立ての `else` 枝がまさにそれである。

```java
// QueryImpl.java:151-155（U5 の変更後も :154 は 1 文字も変わらない — business-logic-model.md § 3.2）
if (col.isFunction()) {
    select.append(IdentifierRules.validate(col.getFunctionName()) + " " + col.getColumnName());
} else {
    select.append(getColumnName(col));      // :154 → QueryImpl.getColumnName(:473-475) → MappingUtils.getColumnName
}
```

`MappingUtils.getColumnName(col)`（`MappingUtils.java:128-137`）は `table == null` のとき `col.getColumnName()` を**そのまま**返す。したがって `new DefaultColumn("TO_CHAR(created,'YYYY')")`（`DefaultColumn.java:39-42` の public コンストラクタ。`isFunction` は false、`table` は null）を `select(...)` に渡すと、式は無検証で SELECT 句に出力される（`tables.add(null)` は `QueryImpl.java:160-161` の `if (tab == null) continue;` で無害）。これは ORDER BY 側で追記が勧めるのと**同じ枝・同じメソッド**（`Sort.java:54` と `QueryImpl.java:154` はどちらも `MappingUtils.getColumnName`）であり、「ORDER BY にはあるが SELECT にはない」という非対称は成立しない。

**(b) 案内先の `where(String)` は代替になりえない。** `Query` の投影 API は `Query.java:25` / `:27` の `select(Collection<Column>)` / `select(Column...)` のみで、`Column` 以外を受ける SELECT 経路は存在しない。`where(String)` / `and(String)` / `or(String)`（`Query.java:130-134`）は WHERE 句にしか効かない。関数式を**選択列として**出したい利用者に `where(String)` を勧めても要求は満たされず、利用者は「回避手段がない」と読んで移行を諦めるか、無意味な書き換えを試みる。

利用者向け確定テキストであり、3.6 はこれを逐語で `MIGRATION.md` に書き出す。(a) の事実（`isFunction` を立てない `Column` の `columnName` に式を置く経路は無検証のまま）を正しく書くか、書かないと決めるなら (b) の誤った案内を削除する必要がある。なお (a) を明記する場合は、それが R-5（テーブル名・カラム名は対象外）と同じ穴であること——すなわち「回避策」であると同時に「未塞ぎの経路」であること——も併せて示すのが誠実である。

### 指摘 2（blocking）— ORDER BY 側の回避手順は前提条件が欠けており、そのとおりに書き換えると例外の代わりに**壊れた SQL** が静かに出力される

同じ段落の ORDER BY 側はこう書く。

> 文字列リテラルを含む式が必要な場合、生の文字列の代わりに `columnName` にその式を設定した `Column` を渡す形に書き換えることで回避できます。

`Sort.sort` の `Column` 枝は 2 つに分かれる（`Sort.java:49-55`）。

```java
if (col.isFunction()) {
    sort.append(col.getColumnName() + " " + o.toString());              // :52 式がそのまま出る
} else {
    sort.append(MappingUtils.getColumnName(col) + " " + o.toString());  // :54 table != null なら前置される
}
```

追記の指示（「`columnName` にその式を設定した `Column` を渡す」）を素直に読むと、利用者は既存のテーブル登録済み `Column`（`DefaultColumn(Table, ...)` は `:50` で `table.registerColumn(this)` するため `getTable() != null`）の `columnName` に式を設定する。すると `:54` を通り、`MappingUtils.java:135` が `table.getTableNameWithSchema() + "." + col.getColumnName()` を返すため、出力は

```
users.TO_CHAR(created,'YYYY') asc
```

となる。元の `sort.sort("TO_CHAR(created,'YYYY')", ASC)` が出していた `TO_CHAR(created,'YYYY') asc` とは別物であり、**例外にならず DB 実行時まで気づかれない**。元の出力を再現するには (i) `table` を持たない `Column`（`new DefaultColumn("TO_CHAR(...)")`、`MappingUtils.java:130-131` の分岐）、または (ii) `isFunction() == true` の `Column`（`Sort.java:52`）のいずれかである必要がある——追記はこの条件を 1 つも書いていない。

破壊的変更の回避手順としてそのまま実行できない指示であり、指摘 1 と同じ段落・同じ確定テキストに属する。条件（table を持たない `Column`、または `isFunction()` が真の `Column`）を明記するか、`Sort.java:52` / `:54` のどちらの枝を意図しているかを特定する必要がある。

### 非ブロッキングな所見

1. **SEC-24 の判定基準が前提条件から `isFunction() == true` を落としたままである（iteration 1 から未修正）。** 判定基準は「`getFunctionName()` が `null` を返す `Column` を SELECT 句組み立てに渡した場合」とだけ書くが、`isFunction()` が偽なら `QueryImpl.java:153-154` の `else` 枝に入り `validate` は呼ばれず `"null"` も出ない。同じ表の要件行と `business-logic-model.md` § 6-4 は「`isFunction() == true` かつ `getFunctionName() == null`」と正しく書いているが、Build and Test 検証項目 22 も同様に条件を落としている。3.6 が判定基準どおりにテストを書くと、実装の有無にかかわらず緑になる空振りテストになる（construction 規則「実装に関係なく常に通るテストを作らない」に抵触）。`isFunction()` を真にする経路は `DefaultColumn.java:61-62`（`Column.FUNCTION` 定義）と `:193-197`（`setFunctionName`）の 2 つで、前者だけを使えば当該状態を作れる。
2. **`Sort.sort` は検証で throw しても `sort` バッファに区切りの `,` を残す。** `Sort.java:48` の `if (sort.length() > 0) sort.append(",");` は検証（`business-logic-model.md` § 2.2 が `:57` 相当に挿入）より前に実行される。`InvalidParameterException` は非検査例外（`IllegalArgumentException` 継承）なので通常は伝播して `Sort` インスタンスごと捨てられるが、例外を捕捉して同じ `Sort` を使い続けると `getSortString()` が余分な `,` を持つ文字列を返す。3.6 が「例外後も既存の並びが壊れない」ことを確かめるテストを書くとここで落ちる。SEC-23 か検証項目 19 に一言添えると 3.6 の判断を誤らせない。
3. **SEC-23 の要件行が `Sort.asc(Object)` / `Sort.desc(Object)` を名指ししない。** TSD-19 の決定行は今回 `asc` / `desc` を追加したが、SEC-23 と BR-3 は `sort(Object, Object)` のみを挙げる。委譲（`Sort.java:35-41`）により実質は同じで事実誤りではないが、対になる 2 文書の粒度が揃っていない。
4. **STRIDE 表 Tampering 行の「対応」列が SEC-24 を挙げている（iteration 1 から未修正）。** SEC-24 は本文自身が「セキュリティ要件というより回帰防止」と認めており、改竄への対応ではない。SEC-23 のみが正確。あわせて R-23（`o` の未検証）は Tampering 行の「2 経路のみ」という限定の外側にある事実であり、同行から参照しておくと残存リスクの所在が 1 か所で読める。
5. **R-23 が引く `SortTest.java:41` は無効化されたテストメソッド内にある。** `SortTest.java:39` は `//	@Test` とコメントアウトされており、`testSort()` は実行されない。行そのものは実在するので「`o` に生の `String` を渡す書き方が実コードにある」ことの根拠にはなるが、「既存テストが示すとおり定型的」という書き方は実行中のテストを想起させる。「無効化されたテストに残る用例」と書けば正確になる。
6. **TSD-16 が、U2 TSD-6 / U4 TSD-11 の判定基準の字面と衝突することに触れていない（iteration 1 から未修正）。** U2 `tech-stack-decisions.md` TSD-6 の判定基準は「U2 の変更後も `pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` が 8 件のままである」であり、U5 適用後は 9 件になる。3.6 は単一の `pom.xml` に対して全 Unit の判定基準を検算するため、TSD-16 に「U5 完了後は 9 件。8 件は各 Unit 単体の差分に関する基準である」と 1 行添えると誤判定を避けられる。
7. **TSD-16 / TSD-19 の判定基準は U1（JaCoCo 実行部）・U2（`MIGRATION.md`）の適用後にしか検算できない。** 実 `pom.xml` に `jacoco` の記述はなく、`MIGRATION.md` も未作成である。本文にはその旨があるが、未解決事項 16〜18 にはビルド順序の前提として現れない。
8. **U2 TSD-9 が定めた `MIGRATION.md`「3. SM-1 の対象外に残る経路」は「識別子位置（R-12、U5 で扱う予定）」と書いている。** U5 出荷後はこの「予定」が古くなり、2 経路は検証済み・残りは対象外という状態を反映しない。TSD-19 は「4. 注意点」への追記だけを決めており 3 節に触れていない。3.6 への申し送りに加えると文書全体の整合が保てる。
9. **「実質 1.5 個」は AC-10b の Given に対する被覆を過大に見せる（iteration 1 から未修正）。** 検証する 2 経路のうち SELECT の `getFunctionName()` は Given の 3 位置（ORDER BY キー・テーブル名・カラム名）のいずれでもなく、ORDER BY キーも `Column` 経由の枝は無検証である。検証項目 23 が 3.6 に記録させるのはこの数字なので、分数ではなく「検証する経路 2 件／未検証の経路（`Column` 経由 ORDER BY、`MappingUtils.getColumnName` 経由のテーブル名・カラム名、`Sort` の `Order` 引数）」と列挙する形が後任に正確に伝わる。

### 判定

iteration 1 の指摘 2（発火点と `asc`/`desc`）と指摘 3（R-2 の後継 = R-23）は実ソースで是正を確認した。指摘 1 の是正は不完全で、同じ段落が今度は**逆向きに**誤っている——SELECT 側は実在する無検証経路（`QueryImpl.java:154` / `MappingUtils.java:130-131`）を「存在しない」と断定し、代わりに投影を表現できない `where(String)` を案内する（指摘 1）。ORDER BY 側は前提条件を欠いたまま、そのとおりに書き換えると例外ではなく壊れた SQL を静かに出す手順を示す（指摘 2）。いずれも実ソースで機械的に確認でき、3.6 が逐語で利用者向け文書に書き出す確定テキストである。後続に拾い直す Unit がないため、**NOT-READY** と判定する。

---

**適用記録（オーケストレータ、2026-08-10。レビュアーの iteration 上限到達後）**

`reviewer_max_iterations: 2` に達したため、iteration 2 の指摘（blocking 2 件、non-blocking のうち対応可能なもの）は**レビュアーの再検証を受けずに**適用した。

| 指摘 | 適用した修正 | 検証根拠 |
|---|---|---|
| **blocking 1** — `tech-stack-decisions.md` TSD-19 が「SELECT 句の `Column.functionName(String)` には同等の回避手段がない」と誤って断定していた | `functionName(...)` / `isFunction()` を一切使わず `columnName(...)` に式を設定した `Column` を使う代替手順に書き換えた。ORDER BY・SELECT の両方に共通する単一の手順にした | `QueryImpl.java:154` → `QueryImpl.getColumnName`（`:473-475`）→ `MappingUtils.getColumnName`（`:128-137`）が `table==null` のとき `col.getColumnName()` を無検証で返すことを確認 |
| **blocking 2** — ORDER BY の代替手順（`columnName` に式を設定した `Column`）が前提条件（`Table` に未登録であること）を欠いており、そのまま実行すると例外ではなく `"テーブル名.式"` という壊れた SQL を静かに生成する | 「`Table` に登録しない `Column`」という前提条件を明記し、外した場合の帰結（例外にならず気づきにくい形で壊れる）を警告した | `DefaultColumn` のコンストラクタでの `Table` 登録、`MappingUtils.getColumnName` の `table != null` 分岐を確認 |
| non-blocking — SEC-24 の判定基準・検証項目 22 が `isFunction() == true` という前提を落としており、実装の有無にかかわらず緑になる空振りテストになりうる | 判定基準・検証項目に `isFunction() == true` を明記した | — |
| non-blocking — STRIDE 表の Tampering 行が「セキュリティ要件というより回帰防止」と自認する SEC-24 を対応欄に挙げていた | SEC-24 を外し、`Column` 経由 ORDER BY と `Sort.Order`（R-23）が未検証のまま残ることを明記した | — |
| non-blocking — R-23 が引く `SortTest.java:41` は無効化されたテストメソッド内にある | 「`:39` の `@Test` はコメントアウトされており無効化されたテストである」と明記し、「既存テストが示すとおり定型的」という言い回しを外した | `SortTest.java:38-40` を確認 |

**未解消の指摘**: TSD-16 の判定基準（U2/U4 の「8 件」と U5 適用後の「9 件」の字面上の不整合）、TSD-16/TSD-19 が U1/U2 の適用後にしか検算できないというビルド順序の前提、U2 の `MIGRATION.md`「3. SM-1 の対象外に残る経路」が U5 出荷後に古くなること、「実質 1.5 個」という表現の精度は、いずれも軽微な表現・引き継ぎの精度に関する指摘であり判定基準や設計判断には影響しないため、今回は見送った。Build and Test（3.6）が `MIGRATION.md` を実際に編集する際に拾うことを期待する。

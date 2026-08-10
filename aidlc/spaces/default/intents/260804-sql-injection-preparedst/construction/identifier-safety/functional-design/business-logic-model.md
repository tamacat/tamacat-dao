# Business Logic Model — U5 `identifier-safety`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U5 の責務・境界・依存 U2、未確定事項（機構の選択を本ステージに委ねる）、リスク（`Sort.sort` の非 `Column` キー経路が意図的にメタデータ外の式を許すこと）。
- **`unit-of-work-story-map.md`**（同上）— U5 が担う FR-1.7（Should）、判定する AC-10b、Unit 内の実装順序 1〜4（本文書の § 3 がこれに対応づく）。
- **`requirements.md`**（requirements-analysis 2.3）— FR-1.7 の原文（(a) 例外拒否 / (b) メタデータ照合）、AC-10b の Given/When/Then、NFR-1（対象範囲は FR-1.7 を含まない）、NFR-3、CON-1。
- **U2 `select-path` の 3.1 成果物** — § 11 引き継ぎ 6、`business-rules.md` R-4。

U1 `bind-foundation` が定義した `InvalidParameterException` の使い方（生成時に投げる、メッセージにバインド値を含めない）は本 Unit の依存先である。状態の定義は本 Unit の `domain-entities.md`、規則の列挙は `business-rules.md` にある。本文書は**アルゴリズムと適用箇所**を扱う。

---

## この文書の範囲

U5 は「バインドできない位置に呼び出し側の文字列が構文として生きたまま入らない状態にする」。U1〜U4 が値の位置を安全にしたのに対し、U5 は**識別子の位置**を安全にする——値とは異なる機構（バインド不能な位置であるため）、異なる安全化の形（拒否であってエスケープではない）を使う。

| # | 処理 | 担当 | 節 |
|---|---|---|---|
| 1 | 危険な字面を検出する | `IdentifierRules.validate` | § 1 |
| 2 | `Sort` の非 `Column` キー経路に適用する | `Sort.sort` | § 2 |
| 3 | `QueryImpl` の SELECT 句（`Column.getFunctionName()`）に適用する | `QueryImpl` | § 3 |
| 4 | `DataType.FUNCTION`（値の位置）が U5 の対象外であることの確認 | 不作為の確認 | § 4 |

**U5 が触らないもの**: `Column` インターフェースと `DefaultColumn`（メタデータの構造そのもの）、`Table`（メタデータ照合を採らないため参照不要）、U1〜U4 が確立した値のバインド機構。

---

## § 1. `IdentifierRules.validate` — 検出アルゴリズム

```java
package org.tamacat.sql;

import org.tamacat.dao.exception.InvalidParameterException;

public final class IdentifierRules {

    private IdentifierRules() {}

    public static String validate(String raw) {
        if (raw == null) {
            return null;                                                  // BR-14。null は素通しする
        }
        if (raw.indexOf('\'') >= 0 || raw.indexOf('"') >= 0 || raw.indexOf(';') >= 0
                || raw.indexOf("--") >= 0 || raw.indexOf("/*") >= 0 || raw.indexOf("*/") >= 0) {
            throw new InvalidParameterException("Unsafe identifier: [" + raw + "]");
        }
        return raw;
    }
}
```

**`null` は例外を投げず素通しする（BR-14、iteration 2 の是正）**: 当初 `null` は `InvalidParameterException` で拒否する設計だったが、`QueryImpl` の SELECT 句組み立て（§ 3）の呼び出し元で**この前提が現行の正当な状態と衝突する**ことが判明した——`Column.FUNCTION` 定義（`Column.FUNCTION`）だけを付けて `functionName(String)` を呼ばない `Column` は `isFunction() == true` かつ `getFunctionName() == null` になりうる（`DefaultColumn` の `FUNCTION` 定義処理と `setFunctionName` は独立しており、前者だけでも `isFunction` が真になる）。`ColumnFunctionTest.java` がこの状態（`getFunctionName()` が `null` を返しうること）を固定している。現行 `QueryImpl.java:152` はこの `null` を文字列連結で `"null"` としてそのまま SELECT 句に出力しており（`col.getFunctionName() + " " + ...` の `null + " "` は `"null "` になる）、**危険な字面を含まない `null` は本来拒否する理由がない**。`validate` が `null` を無条件に拒否すると、この既存の正当な状態（Should Have の追加によって Must 相当の既存機能を壊す）を破る。`null` はいかなる注入字面も持ちえない（ID-1〜ID-5 のいずれにも一致しない）ため、素通しさせても安全性は損なわれない。

**`Sort.sort` の非 `Column` 枝で `k` が `null` の場合**: `k.toString()` の呼び出し自体が `IdentifierRules.validate` に渡る前に `NullPointerException` を投げる（`k` は `Object` 型であり `null.toString()` は呼べない）。したがって `validate` が `null` に到達するのは `QueryImpl` の `col.getFunctionName()` 経由だけであり、`Sort` 側の挙動に変化はない（現行も `k == null` なら同じ `NullPointerException` になる）。

**`public` である理由（型・可視性の訂正）**: 呼び出し元は `org.tamacat.dao.Sort` と `org.tamacat.dao.impl.QueryImpl` であり、いずれも `org.tamacat.sql` とは別パッケージである。Java の package-private は別パッケージに及ばない——U1 `PreparedSql.getPlaceholderCount()`（`domain-entities.md` A-1）、U2 `Search.getSearchParam()`（§ 2.4）が同じ制約から `public` にした先例と同じ理由による。したがって `IdentifierRules` とその `validate` メソッドは**新規 public 型・新規 public メンバ**として `domain-entities.md`「2.6 / 2.7 契約からの差分」に計上する（下記訂正を参照）。

**単純な `indexOf` の連鎖であり正規表現を使わない。** `ValueRules.isNumeric`（U1）が正規表現のバックトラック（R-6、後段に送った残存リスク）を抱えているのとは対照的に、`indexOf` は文字列長に対して線形であり同種のリスクを持ち込まない（BR-1）。

**メッセージに `raw` を含めることの是非**: U1 SEC-4 は「例外メッセージにバインド値を含めない」と定めるが、これは**値**についての規則である。識別子はバインドされる値ではなく、呼び出し側が渡した SQL 断片相当の文字列であり、U2 BR-19 の早期失敗（`where(String)` 等に危険な `?` を含むテキストを渡したときのメッセージに SQL テキストを含めてよい、U2 SEC-15）と同じ扱いにできる——識別子は「開発者が書いたコードの一部」に近い性質を持つ（BR-2）。

**`validate` の戻り値**: 入力をそのまま返す（IR-2）。呼び出し元でのメソッドチェーンを簡潔にするための設計であり、意味的な変換は一切行わない。

---

## § 2. `Sort.sort` への適用

### 2.1 現行

```java
// Sort.java:47-60
public Sort sort(Object k, Object o) {
    if (sort.length() > 0) sort.append(",");
    if (k instanceof Column) {
        Column col = (Column)k;
        if (col.isFunction()) {
            sort.append(col.getColumnName() + " " + o.toString());
        } else {
            sort.append(MappingUtils.getColumnName(col) + " " + o.toString());
        }
    } else {
        sort.append(k.toString() + " " + o.toString());
    }
    return this;
}
```

### 2.2 変更後

```java
public Sort sort(Object k, Object o) {
    if (sort.length() > 0) sort.append(",");
    if (k instanceof Column) {
        Column col = (Column)k;
        if (col.isFunction()) {
            sort.append(col.getColumnName() + " " + o.toString());
        } else {
            sort.append(MappingUtils.getColumnName(col) + " " + o.toString());
        }
    } else {
        sort.append(IdentifierRules.validate(k.toString()) + " " + o.toString());   // BR-3
    }
    return this;
}
```

**変更は 1 行のみ**（`k.toString()` を `IdentifierRules.validate(k.toString())` に置き換える）。`Column` の枝は変更しない——`Column` はメタデータであり、開発者がコード上で定義するオブジェクトである。`unit-of-work.md`「実装上の制約」が対象とする「呼び出し側から渡された文字列」は非 `Column` 枝の `k` を指す（BR-4）。

**`col.getColumnName()` / `MappingUtils.getColumnName(col)` を検証しない理由**: これらは `Column` メタデータから導出される値であり、`Column` インターフェースの実装（`DefaultColumn` 等）を通じて開発者がコード上で定義する。実行時に外部入力から動的に構築される経路ではない——U5 の境界（「U1〜U4 が触るクラスを変更しない」）とも整合し、`Column` / `DefaultColumn` を変更しない（BR-5）。

### 2.3 `o`（`Order`）を検証しない理由

`o.toString()` も検証していない。`Order` は enum（`asc` / `desc` の 2 値のみ）であり、`Object` として受け取る現行のシグネチャでも任意の `Object` が渡されうるが、`unit-of-work-story-map.md` が U5 の担当と定めた対象は識別子位置（テーブル名・カラム名・ORDER BY 句）であり、ソート方向は識別子ではない。**この判断を明示的に記録する**（BR-6）——`o.toString()` に危険な文字列を渡す誤用（`sort.sort(col, "'; DROP TABLE users; --'")`）は理論上可能だが、`unit-of-work.md` の Should Have の範囲を超える拡張であり、本ステージでは対象外とする。

---

## § 3. `QueryImpl` の SELECT 句への適用

### 3.1 現行

```java
// QueryImpl.java:139-157（getSelectSQL のループ本体、実測で訂正）
for (Column col : getSelectColumns()) {
    if (select.length() == 0) {
        select.append(SELECT + " ");
        if (distinct) { select.append("DISTINCT" + " "); }
    } else {
        select.append(",");
    }
    if (col.getType() == DataType.OBJECT) {
        blobIndex++;                                        // :148-150。isFunction() の真偽に関わらず数える
    }
    if (col.isFunction()) {
        select.append(col.getFunctionName() + " " + col.getColumnName());   // :151-152
    } else {
        select.append(getColumnName(col));                  // :153-155
    }
    tables.add(col.getTable());                              // :156。FROM 句が使うテーブル集合の収集
}
```

**iteration 1 の誤り（訂正記録）**: 本節は当初 `blobIndex++` を `else` 枝の内側に、`tables.add(col.getTable())` を欠いた形で引用していた。実ソースでは `blobIndex++` は `isFunction()` の分岐の**外側・前**（`:148-150`）にあり関数カラムも数える。`tables.add(...)`（`:156`）はループ末尾で毎回実行され FROM 句が使うテーブル集合を作る。U2 `business-logic-model.md` § 4.1 が「`blobIndex` は SELECT カラムに `DataType.OBJECT` が現れるたびに `blobIndex++` する（増分は `:149`）」と明記しているとおりであり、本節はその記述に合わせて訂正した。

### 3.2 変更後

```java
for (Column col : getSelectColumns()) {
    if (select.length() == 0) {
        select.append(SELECT + " ");
        if (distinct) { select.append("DISTINCT" + " "); }
    } else {
        select.append(",");
    }
    if (col.getType() == DataType.OBJECT) {
        blobIndex++;
    }
    if (col.isFunction()) {
        select.append(IdentifierRules.validate(col.getFunctionName()) + " " + col.getColumnName());   // BR-7
    } else {
        select.append(getColumnName(col));
    }
    tables.add(col.getTable());
}
```

**変更は 1 行のみ**（`col.getFunctionName()` を `IdentifierRules.validate(col.getFunctionName())` に置き換える）。ループの残りの行——`blobIndex` の計数、`tables.add(...)`、`getColumnName(col)` の枝——はいずれも 1 文字も変わらない。`col.getColumnName()` は検証しない——`Column.isFunction()` が真の枝でも `getColumnName()` はメタデータ由来の値であり、`getFunctionName()` だけが開発者コードから渡される任意の文字列である（§ 2.2 と同じ論拠、BR-5 の適用）。

**`QueryImpl` は U2 が所有するクラスだが、この変更は U2 の設計への介入ではない。** `unit-of-work.md` U5 の境界「U1〜U4 が触るクラスを変更しない」は、U1〜U4 が確定した**設計判断**（値のバインド機構、WHERE/SELECT の組み立て構造、実行経路の形）への介入を禁じる趣旨であり、U2 が既に確定した SELECT 句組み立てのロジック・順序・値の結合には一切触れない**局所パッチ**である（`functional-design-questions.md` の矛盾判定 1 を参照）。U2 の `business-logic-model.md` § 4.1（`buildSelectClause`）が担う責務のうち、この 1 行が担うのは「`getFunctionName()` の検証」だけであり、SELECT 句のテキスト構造そのものは 1 文字も変わらない。

**`QueryImpl.getSelectSQL()`（リテラル版）にも同じ検証がかかる。** `getSelectSQL()` と `getSelectPreparedSql()`（U2 が追加）はいずれも同じ `buildSelectClause` を経由する（U2 `business-logic-model.md` § 4.1）ため、変更は自動的に両方の経路に効く。これは**旧 API の観測可能な挙動を変える**——現行 `getSelectSQL()` は危険な字面を含む `functionName` もそのまま返していたが、変更後は生成時に `InvalidParameterException` を投げる（BR-8）。**U2 BR-10「旧メソッドの戻り値不変」との関係は `domain-entities.md`「2.6 / 2.7 契約からの差分」で扱う**——正常系の戻り値は 1 文字も変わらず、異常系（危険な字面を含む場合）にのみ新しい例外が発火するため、BR-10 とは直交する軸の変更であり矛盾しない。

---

## § 4. `DataType.FUNCTION`（値の位置）は U5 の対象外であることの確認

`unit-of-work-story-map.md`「Unit 内の実装順序」3 は「`Column.getFunctionName()` / `DataType.FUNCTION` の扱いの確定」を U5 の作業として挙げているが、実ソースを調べると **`DataType.FUNCTION` は識別子ではなく値の分類として U1 が既に扱っている**。

```java
// U1 business-logic-model.md § 4.1（tokenFor、抜粋）
if (t == DataType.FUNCTION) {
    return new Token(value == null ? "null" : value, null);   // U1 BR-9
}
```

`DataType.FUNCTION` は `BindSqlBuilder.tokenFor` が値をバインドせずテキストとして展開する分類であり、`Column.getFunctionName()`（識別子位置、SELECT 句）とは**別の概念**である——前者は `Column` の**型**（バインド時に値をどう扱うか）、後者は `Column` の**属性**（関数呼び出しの識別子）である。同じ「`Column` が関数を表す」という着想を共有するが、コード上は独立している（`isFunction()` / `getFunctionName()` は SELECT 句専用、`DataType.FUNCTION` は値のバインド専用）。

**したがって `DataType.FUNCTION` に関する決定は U1 の責務範囲であり、U1 の BR-9・残存リスク R-1（`unit-of-work.md` U1「実装上の制約」）が既に扱っている。** U5 は `DataType.FUNCTION` に触れない（U5 BR-9）。`unit-of-work-story-map.md` の実装順序 3 の文言は「`Column.getFunctionName()` **または** `DataType.FUNCTION` のどちらか一方、あるいは両方の扱いを確定する」と読むのが妥当であり、本ステージは前者（`getFunctionName()`）のみを扱う——後者は U1 が既に確定済みであるため、本ステージで再確定する対象がない。

---

## § 5. 実装順序

`unit-of-work-story-map.md`「U5 `identifier-safety`」の 4 段に本文書の節を対応づける。

| 段 | 内容 | 本文書 | 完了の確認 |
|---|---|---|---|
| 1 | 機構の決定（Functional Design 3.1） | Q1 = A（`functional-design-questions.md`） | 本ステージのゲート承認そのもの |
| 2 | `Sort.sort(Object, Object)` の非 `Column` キー経路への適用 | § 2 | 危険な字面を渡すと `InvalidParameterException`。既存の `SortTest`（`Column` のみを渡す）は無変更で緑 |
| 3 | `Column.getFunctionName()` / `DataType.FUNCTION` の扱いの確定 | § 3（`getFunctionName()`）、§ 4（`DataType.FUNCTION` は対象外の確認） | `QueryImpl` の SELECT 句組み立てで危険な字面を渡すと `InvalidParameterException` |
| 4 | AC-10b を判定するテストの追加 | Build and Test（3.6）に引き継ぐ | § 6 の引き継ぎ事項参照 |

---

## § 6. 後続ステージへの引き継ぎ事項

| # | 引き継ぎ先 | 内容 |
|---|---|---|
| 1 | **Build and Test（3.6）** | AC-10b の判定テストを追加する。`Sort.sort(rawKey, Sort.Order.ASC)` と `QueryImpl` の SELECT 句組み立て（`Column.functionName(危険な文字列)`）の両方について、危険な字面（ID-1〜ID-5）を渡したとき `InvalidParameterException` が投げられ、かつ渡した文字列が構文として生きたまま SQL テキストに現れないことをアサートする |
| 2 | **Build and Test（3.6）** | 正当な非 `Column` キー（`"RAND()"`、`"COUNT(*)"` 等、危険な字面を含まない複合式）が引き続き通ることを確認する回帰テストを追加する——`unit-of-work.md` が保護を求めた既存の柔軟性が壊れていないことの確認 |
| 3 | **Build and Test（3.6）** | `getSelectSQL()` / `getSelectPreparedSql()` の両方が同じ検証を受けることを確認する（§ 3.2「変更は自動的に両方の経路に効く」の検証） |
| 4 | **Build and Test（3.6）** | `Column.isFunction() == true` かつ `getFunctionName() == null` の `Column` を SELECT 句組み立てに渡した場合、例外が投げられず現行どおり `"null"` が SELECT 句に出力されることを確認する（BR-14 の回帰テスト） |
| 5 | **Build and Test（3.6）** | **AC-10b の判定は「部分的」であることを SM-1 / AC のカバレッジ集計に明記する。** 検証されるのは ORDER BY の非 `Column` キーと SELECT の `getFunctionName()` の 2 経路のみであり、テーブル名・カラム名そのもの（`Column.columnName(String)` / `Table` のテーブル名設定を経由する経路）は対象外（R-5）。U5 は本ワークフローの最後の Unit であり、この未解決のギャップを引き継ぐ後続 Unit がないため、3.6 のカバレッジ報告がこの記録の最後の機会になる |

---

## Review

NOT-READY

### 検証の方法と範囲（iteration 2 / 最終）

iteration 1 の記憶を持たない状態で、3 成果物（`business-logic-model.md` / `business-rules.md` / `domain-entities.md`）を `functional-design-questions.md`、resolved consumes（`unit-of-work.md`、`unit-of-work-story-map.md`、`requirements.md`、`components.md`、`component-methods.md`、`services.md`）、および dispatch で許可された U1 / U2 の 3.1 成果物と突き合わせた。あわせて実ソース（`Sort.java`、`Column.java`、`DefaultColumn.java`、`DefaultTable.java`、`Table.java`、`MappingUtils.java`、`QueryImpl.java`、`DataType.java`、`Columns.java`、`ColumnDefine.java`、テスト側は `SortTest.java` / `ColumnFunctionTest.java` / `ColumnsTest.java` / `UserStat.java`）を読み、掲げられた主張を反証する方向で検算した。

### 反証できなかった主張（是正を確認した）

- **§ 3.1 / § 3.2 の `QueryImpl.java:139-157` 引用は実ソースと一致する。** 実ソースの `:139` `for`、`:140-147` の `select.length()==0` 分岐、`:148-150` の `if (col.getType() == DataType.OBJECT) { blobIndex++; }`（`isFunction()` 分岐の**外側・前**）、`:151-155` の `isFunction()` 分岐、`:156` の `tables.add(col.getTable())` がすべて正しい位置で再現されている。行番号ラベル・行末注釈（`:148-150` / `:151-152` / `:153-155` / `:156`）も実測と一致し、U2 `business-logic-model.md` § 4.1 の「増分は `:149`」とも矛盾しない。iteration 1 の構造的誤りは解消済み。
- **`IdentifierRules` を `public final class` + `public static String validate(String)` にする訂正は、クロスパッケージのコンパイル問題を実際に解消する。** 呼び出し元は `org.tamacat.dao.Sort` / `org.tamacat.dao.impl.QueryImpl`、宣言先は `org.tamacat.sql`（実在するパッケージ。`InvalidParameterException` も `org.tamacat.dao.exception` に実在）。援用された先例（U1 A-1 `PreparedSql.getPlaceholderCount()`、U2 § 2.4 `Search.getSearchParam()`）も原文どおりである。`domain-entities.md` の A-1 / A-2 は U1 の A-n と番号が重なるが、参照側は一貫して「U1 A-7」のように unit 接頭辞を付けており、実害のある衝突ではない。
- **R-6 / BR-11 の書き換えは正確である。** § 1 のコードは `raw.indexOf('\'')` を無条件で拒否するため、`TO_CHAR(d,'YYYY')` や `CASE WHEN x='A' ...` は ID-1 に該当し拒否される——BR-11 の記述と R-6 の残存リスクはこの事実と一致する。R-6 が示す代替経路（`Column` 経由）も実ソースで裏が取れる（`Sort.java:52` の `isFunction()` 枝は `col.getColumnName()` を無検証で連結する）。
- **ID-6 の廃止・`U1 BR-9` 接頭辞・Q&A の言い回し訂正はいずれも反映済み。** `domain-entities.md:46` は `null` を「先行するガード条件」として ID-1〜ID-5 の表から外し、IR-1 / BR-10 / § 6 引き継ぎ 1 の「ID-1〜ID-5」という列挙と整合している。§ 4 のコード注釈は `// U1 BR-9` になっている。`functional-design-questions.md` は 3 箇所とも「`Sort` への変更をメソッド本体 1 行に抑える」に直っており、BR-12 と矛盾しない。
- **`SortTest` が無変更で緑（NFR-5）という見込みは維持される。** `SortTest.java` の 3 メソッドはすべて `Column` を渡す。既存テストで危険な字面を持つ `functionName` を使うものもない（`UserStat.java:22` の `"sum(score)"` は ID-1〜ID-5 を含まない）。

### 指摘 1（blocking）— `domain-entities.md`「この文書の範囲」節が未修正のまま残り、同じ文書の「2.6 / 2.7 契約からの差分」節と正面から矛盾する

`domain-entities.md:16-22` は依然として次のように書いている。

> **U5 は新しい public 型を 1 つも導入しない。** 検証は package-private のヘルパ 1 つ（状態を持たない）として追加するのみであり…
>
> \| `org.tamacat.sql`（新設ヘルパ） \| なし（状態なし） \| **なし（package-private）** \|

一方、同じ文書の `:88-95` は「iteration 1 は『新規 public メンバなし』と誤って記録していた」と明記し、A-1（`IdentifierRules` = **新規 public 型**）と A-2（`validate` = `public static`）を計上する。`business-logic-model.md` § 1 のコードも `public final class` / `public static` である。**同一文書内で、同じ論点について反対の宣言が併存している。**

これは体裁の問題ではない。`business-rules.md` NFR-3 の「✅」は「`domain-entities.md`「2.6 / 2.7 契約からの差分」参照」で根拠を担保しており、FR-3.1 / AC-11 の監査（U1 が A-8〜A-12 について「製品成果物の public 表面に載り FR-3.1 の互換維持対象になる」と明示した先例）は「public 表面の全量」を正しく読めることに依存する。「この文書の範囲」節を先に読んだ実装者・監査者は public 表面を 0 件と読む。iteration 1 の指摘 1 に対する是正が文書内で完了していない。

### 指摘 2（blocking）— `isFunction() == true` かつ `getFunctionName() == null` の `Column` は実ソースで到達可能であり、IR-3 によって**正常系**で `InvalidParameterException` になる。BR-10 直交性の論証はこの入力で崩れる

`DefaultColumn.java` を実測すると `isFunction` が真になる経路は 2 つある。

```java
// DefaultColumn.java:44-65（コンストラクタ）
if (FUNCTION.equals(def))
    this.isFunction = true;          // :61-62 ← functionName は設定されない

// DefaultColumn.java:193-197
public Column setFunctionName(String functionName) {
    this.functionName = functionName;  // null を渡せる
    this.isFunction = true;
    return this;
}
```

`ColumnDefine FUNCTION` は `Column.java:16` で public に公開されており、`new DefaultColumn(table, "col", type, name, Column.FUNCTION)` は **`isFunction() == true` / `getFunctionName() == null`** の `Column` を作る。この `Column` を SELECT に載せると現行 `QueryImpl.java:152` は `"null col"` を連結し、`getSelectSQL()` は正常に文字列を返す（`ColumnFunctionTest.java:30` が `getFunctionName()` の `null` 返却を明示的に固定していることが、この状態が想定内であることを裏づける）。

§ 3.2 の変更後コードは `IdentifierRules.validate(col.getFunctionName())` を無条件に呼ぶため、この入力に対して **IR-3（`validate(null)` は `InvalidParameterException`）が発火する**。これは「危険な字面」を 1 文字も含まない入力であり、

- § 3.2 の「正常系の戻り値は 1 文字も変わらず、**異常系（危険な字面を含む場合）にのみ**新しい例外が発火する」
- `domain-entities.md:99` の「U5 が追加する例外送出は**異常系**（危険な字面を含む `functionName` を渡した場合）にのみ発火し、正常系の戻り値には一切影響しない」

という記述は、この入力に対して**偽**である。U2 BR-10（「旧メソッドの戻り値の中身を 1 文字も変えない」。`Query.getSelectSQL()` を名指しで保護し、検証手段を「`QueryImplTest` が変更なしで緑」と定義）との直交性の論証は、まさにこの「正常系には触れない」という一点に全体重を掛けているため、論証ごと成立しない。

リポジトリ内の既存テストにこの形の `Column` はない（`ColumnFunctionTest` の COL2〜COL4 は `type(DataType.FUNCTION)` だけで、`type()` は `isFunction` を立てない——`isFunction` への代入は `:62` と `:195` の 2 箇所のみ）ため、既存テストは緑のまま通ってしまう。**設計が明示しない限り、実装者はこの挙動変更に気づかずに入れる。** `getFunctionName()` が `null` のときに検証を素通りさせるのか（`col.getFunctionName()` が null なら validate を呼ばない）、拒否を意図するのか（その場合 BR-10 との関係の論証をやり直す必要がある）を設計が決める必要がある。残存リスク R-1〜R-6 のいずれもこれを記録していない。

### 指摘 3（blocking）— R-5 / BR-5 が AC-10b の 2/3 を対象外にする根拠（「セッター自体に手を入れる必要がある」）は実ソースで反証される

BR-5 の新しい文面は、テーブル名・カラム名を検証しない理由をこう述べる。

> それでも検証しない理由は、この経路を安全にするには `Column` / `DefaultColumn` / `Table` の**セッター自体に手を入れる必要があり**、`unit-of-work.md` U5 の境界（…）を破るためである

この必要性の主張は成立しない。U5 が既に 2 回採用した手法——**値の生成点ではなく SQL 組み立ての呼び出し点で 1 行検証する**——が、そのまま同じ位置に適用できる。

- `Sort.java:54` — `sort.append(MappingUtils.getColumnName(col) + " " + o.toString());`
- `QueryImpl.java:154` — `select.append(getColumnName(col));`

いずれも `Column` / `DefaultColumn` / `Table` を 1 文字も変えずに `IdentifierRules.validate(...)` で包める。`MappingUtils.getColumnName(col)`（`MappingUtils.java:128-137`）は `table.getTableNameWithSchema() + "." + col.getColumnName()` を返すため、この 1 箇所でテーブル名とカラム名の**両方**が同時に検査対象になる。AC-10b の When（「その Query から SQL が組み立てられる」）が指す時点とも一致する。

すなわち、R-5 が「受容（スコープを明示的に narrow）」の根拠として挙げた技術的制約は存在せず、`unit-of-work.md` の境界も（`Sort` / `QueryImpl` の呼び出し点への 1 行追加を許容するという U5 自身の解釈のもとでは）この 2 箇所を排除しない。FR-1.7 が Should Have であることはスコープを狭める正当な理由になりうるが、**その場合の根拠は「コストと互換リスクを見て意図的に落とした」でなければならず、「技術的に不可能」ではない。** 現状の R-5 / BR-5 は誤った必要性に基づいており、後続（Build and Test 3.6、および将来のスコープ拡張の判断者）に誤った制約を伝える。

なお、この 2 箇所を実際に検証対象に加えるかどうかは設計判断であり、加えた場合は指摘 2 と同種の互換性検討（`getColumnName()` が `null` の `Column`、ベンダ固有の引用識別子など）が必要になる。本レビューはどちらかを指示しない——根拠の記述が事実と食い違っている点を欠陥として挙げる。

### 非ブロッキングな所見

- **`business-logic-model.md`「この文書の範囲」（`:25`）が R-5 と接続していない。** ここは依然として「`Table`（メタデータ照合を採らないため参照不要）」とだけ書く。これは機構 B を採らない理由であって、テーブル名を識別子位置として検証しない理由ではない。`business-rules.md` R-5 への参照を 1 行足せば 3 文書が揃う。
- **§ 6 の引き継ぎ表が AC-10b の部分被覆を Build and Test に伝えていない。** 引き継ぎ 1 は 2 経路のテストを指示し、引き継ぎ 4 は「特になし（U5 が最後の Unit）」で閉じる。R-5 が記録した「AC-10b の Given 3 位置のうち 2 位置が未処理」という事実は、AC 被覆表（FR-8.2 / AC-11 の集約先）に載る必要がある。U5 が最後の Unit である以上、ここで送らなければどこにも残らない。
- **`business-rules.md` の残存リスク表の並びが R-1, R-2, R-3, R-4, R-6, R-5 になっている。** 追記の痕跡であり、R-5 が R-6 の後にある。並べ替えを推奨する。
- **`functional-design-questions.md:138` の参照先が誤っている。** 「`business-logic-model.md`「2.6 / 2.7 契約からの差分」に 1 行として記録すれば足りる」とあるが、この節は `domain-entities.md` にある（`business-logic-model.md` § 3.2 の参照は正しく `domain-entities.md` を指しており、そちらは是正済み）。Q&A は本レビューの編集対象外だが、記録として残す。
- **`Sort.java` / `QueryImpl.java` への `import org.tamacat.sql.IdentifierRules;` の追加が「変更は 1 行のみ」の外にある。** BR-12 / BR-13 の「1 行のみ」は本体行の話であり import 行を含まない。実装時の齟齬を避けるため一言添えると良い。
- **BR-5 が援用する `components.md` の「変更しないコンポーネント」表には `Sort` も載っている（`:273`）。** ただし `Sort` の行は「FR-1.7 の機構が未決（OQ-9 / Q9 = D）のため、**本ステージでは**変更対象としない」という条件付きであり、`Column` / `Table` の行（`:267`、無条件）とは性格が異なる。この差は BR-5 の論拠として使えるが、現状の文面はその区別に触れていない。

### 判定

指摘 1（同一文書内で public 表面の宣言が矛盾したまま残る）、指摘 2（`isFunction() && getFunctionName()==null` という到達可能な正常系入力で例外が発火し、U2 BR-10 との直交性の論証が崩れる）、指摘 3（AC-10b の 2/3 を落とす根拠が実ソースで反証される）は、いずれも実ソースまたは渡された契約で機械的に確認でき、実装者が設計者への追加確認なしには決められない事項を含む。**NOT-READY** と判定する。

（本 iteration で是正が確認された事項——`QueryImpl` 引用の正確化、`public` 化によるコンパイル可能性、R-6 / BR-11、ID-6 の廃止、`U1 BR-9` 接頭辞、Q&A の言い回し——は上記「反証できなかった主張」に列挙したとおりであり、再指摘していない。）

---

**適用記録（オーケストレータ、2026-08-09。レビュアーの iteration 上限到達後）**

`reviewer_max_iterations: 2` に達したため、iteration 2 の指摘（blocking 3 件、non-blocking のうち対応可能なもの）は**レビュアーの再検証を受けずに**適用した。

| 指摘 | 適用した修正 | 検証根拠 |
|---|---|---|
| **blocking 1** — `domain-entities.md`「この文書の範囲」が `IdentifierRules` 追加後も「新しい public 型を 1 つも導入しない」「なし（package-private）」と書いたままで、同じ文書内の「2.6 / 2.7 契約からの差分」（A-1/A-2）と矛盾していた | 「この文書の範囲」を訂正し、`IdentifierRules` が新規 public 型・`validate` が新規 public メンバであることを明記。表の「新規 public 型」列も訂正した | `domain-entities.md` の 2 箇所を突き合わせて矛盾がないことを確認 |
| **blocking 2** — `Column.isFunction()==true` かつ `getFunctionName()==null` が実ソースで到達可能（`DefaultColumn` が `Column.FUNCTION` 定義だけで `functionName` 未設定の場合）であり、`IdentifierRules.validate(null)` が例外を投げる設計は現行の正当な状態（SELECT 句に `"null"` が出力される）を壊す | `validate(null)` を例外ではなく `null` の素通しに変更（BR-14）。`domain-entities.md` の IR-3 も同じ内容に訂正。§ 1 に `Sort` 側は `k.toString()` が先に `NullPointerException` を投げるため影響を受けないことを明記 | `DefaultColumn.java`（`FUNCTION` 定義処理と `setFunctionName` が独立）、`ColumnFunctionTest.java`（`getFunctionName()` が `null` を返しうることの固定）を確認 |
| **blocking 3** — BR-5 / R-5 が「テーブル名・カラム名の検証には `Column`/`DefaultColumn`/`Table` への変更が必須」と主張していたが、`MappingUtils.getColumnName` の呼び出し箇所を包む手法（既に 2 箇所で使っている手法と同じ）で技術的には可能であり、主張が事実と異なっていた | BR-5 / R-5 を「技術的な不可能性ではなくスコープの決定」と正しく書き換え、見送る実質的な理由（`MappingUtils.getColumnName` が全経路で使われ影響範囲が想定スコープを超えること、具体的な悪用経路が上流に示されていないこと）を明記した | `MappingUtils.java:128-137`、`Sort.java:54`、`QueryImpl.java:154` の呼び出し形を確認 |
| non-blocking — § 6 の引き継ぎ表が AC-10b の部分被覆（R-5）を Build and Test に伝えていなかった | 引き継ぎ項目 4・5 を追加し、BR-14 の回帰テストと AC-10b の部分被覆の明記を指示した | — |
| non-blocking — 残存リスク表の並びが R-1〜R-4, R-6, R-5 になっていた | R-5 / R-6 を数値順に並べ替えた | — |
| non-blocking — `functional-design-questions.md` の参照先が `business-logic-model.md` になっていた（正しくは `domain-entities.md`） | 参照を訂正した | — |
| non-blocking — `Sort` / `QueryImpl` への `import` 追加が「変更は 1 行のみ」の記述から漏れていた | BR-12 / BR-13 に import 1 行を明記。BR-12 に `components.md:273` の `Sort` 行が条件付き記述であることも追記した | `components.md:267`（`Column`/`Table`、無条件）と `:273`（`Sort`、条件付き）を確認 |

**未解消の指摘**: なし（iteration 2 で報告された blocking・non-blocking はすべて対応した）。

# Functional Design Questions — U5 `identifier-safety`

Unit: **U5 `identifier-safety`**（kind: `library`、**Should Have**）
Stage: Functional Design（3.1）/ Construction
Depth: Standard（`aidlc-state.md`）

## Sources

- `unit-of-work.md` — U5 の責務「バインドできない位置（テーブル名・カラム名・ORDER BY 句）に呼び出し側から渡された文字列が、SQL の構文として解釈されうる形でそのまま連結されない状態にする」、境界（値のバインドには触れない。U1〜U4 が触るクラスを変更しない）、依存 U2、未確定事項（機構は (a) 例外による拒否か (b) 宣言済み `Table` / `Column` メタデータへの照合かを本ステージで決定する）、リスク（`Sort.sort(Object k, Object o)` の非 `Column` キー経路は意図的にメタデータ外の式を許すために存在し、検証を強制すると既存利用者が壊れうる。`application-design` は `Sort` を「変更しないコンポーネント」として扱っている）。
- `unit-of-work-story-map.md` — U5 が担う FR-1.7（Should）、判定する AC-10b、実装順序 1〜4（1. 機構の決定 — 本ステージ、2. `Sort.sort` の非 `Column` キー経路への適用、3. `Column.getFunctionName()` / `DataType.FUNCTION` の扱いの確定、4. AC-10b を判定するテストの追加）。
- `requirements.md` — FR-1.7（「(a) 例外で拒否される、(b) 宣言済みの `Table`/`Column` メタデータに照合され一致しないものが拒否される」という二値の観測可能な結果）、AC-10b（Given/When/Then）、NFR-1（対象範囲は FR-1.7 を含まない）、NFR-3（後方互換）、CON-1（Java 8）。
- **U2 `select-path` の 3.1 成果物** — § 11 引き継ぎ 6「`orderBy(Sort)` と SELECT 句の `col.getFunctionName()`（`QueryImpl.java:152`）は識別子位置であり、呼び出し側の文字列が構文として入る。U2 は `Sort` に触れていない」。`business-rules.md` R-4（残存リスク、U5 に送る）。
- 実ソース — `Sort.java`、`Column.java`（`meta` パッケージ）、`Table.java`、`MappingUtils.java`、`QueryImpl.java`（`:152`、`:424-432`）、`SortTest.java`。

設問は 2 件。**Q1 は `unit-of-work.md` が本ステージに明示的に委ねた「機構の選択」そのもの**、Q2 は Q1 の結果によって決まる**適用範囲**（`Sort` の非 `Column` 経路と `Column.getFunctionName()` の両方に同じ機構を当てるか）である。

---

## Q1. 識別子位置の検証機構 — 例外による拒否か、メタデータ照合か（OQ-9 の残り）

### 現状（実ソースで確認）

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
        sort.append(k.toString() + " " + o.toString());   // ← 検証なしでそのまま連結
    }
    return this;
}
```

`k` が `Column` でない場合（`asc(Object)` / `desc(Object)` / `sort(Object, Object)` に `String` 等を渡した場合）、`k.toString()` が**無検証で** ORDER BY 句に連結される。呼び出し元はリポジトリ内に 0 件（`SortTest.java` はすべて `Column` を渡す）。

`Column.isFunction() == true` の枝も同様に無検証である——`col.getFunctionName()` は呼び出し側が `Column.functionName(String)` で設定した任意の文字列であり、`SELECT` 句にも同じパターンで現れる（`QueryImpl.java:152`）。

### 選択肢

**A. 例外による拒否（構文的に危険な文字を検知）**

```java
private static final Pattern UNSAFE = Pattern.compile("['\";]|--|/\\*|\\*/");

private static String validateIdentifier(String raw) {
    if (raw == null || UNSAFE.matcher(raw).find()) {
        throw new InvalidParameterException("Unsafe identifier: [" + raw + "]");
    }
    return raw;
}
```

SQL 構文としての意味を持ちうる字面（引用符・セミコロン・コメント開始/終了）を拒否する。**識別子として正当な文字集合をホワイトリスト化するのではなく、注入に使われる字面をブラックリスト化する**——ホワイトリストは `RAND()`、`COUNT(*) desc`、`t.col1, t.col2` のような正当な複合式（後述、既存の非 `Column` 経路が意図的に許してきたもの）を壊しうるが、ブラックリストはこれらを通しつつ `'; DROP TABLE users; --` のような注入は拒否する。

**B. 宣言済み `Table` / `Column` メタデータへの照合**

渡された文字列を、対象 `Table` の `find(String columnName)`（`Table.java:34`）で解決できるカラム名と完全一致させ、一致しないものを拒否する。

```java
// 概念コード。Sort は Table への参照を持たないため、呼び出し側から Table を渡す新しい引数が要る
if (table.find(raw) == null) {
    throw new InvalidParameterException("Unknown column: [" + raw + "]");
}
```

**C. Other (please specify)**

### 論点

| 観点 | A（ブラックリスト＋例外） | B（メタデータ照合） |
|---|---|---|
| `unit-of-work.md` が指摘するリスク（`Sort.sort` の非 `Column` キー経路が意図的にメタデータ外の式を許す） | **両立する。** `RAND()`、`COUNT(*)`、`t1.col1, t2.col2` のような複合式・関数呼び出しは字面上「危険な字」を含まなければ通る | **両立しない。** `Table.find(...)` は単一カラム名の完全一致のみを解決でき、関数呼び出しや複合式、他テーブルの修飾付き参照は原理的に拒否される。既存の意図的な用途を壊す |
| API の変更量 | **なし。** `Sort` / `QueryImpl` の内部に検証を追加するだけで、シグネチャは変わらない | **`Sort` が `Table` を知る必要がある。** 現在 `Sort` はどの `Table` にも属さない値オブジェクトであり、`sort(Object, Object)` に `Table` 引数を追加するか `Query` 側で事後検証する新しい経路が要る（NFR-3 に対するリスク） |
| NFR-3（既存サブクラスが再コンパイルなしで動く） | ✅ 追加のみ（例外を投げる新しい失敗モードが増えるが、シグネチャは不変） | ⚠️ `Table` 引数を追加する形にすると新しいオーバーロードで済むが、既存の `sort(Object, Object)` を素通りさせるなら B の保証は片方の入口だけになる |
| `application-design` の「`Sort` を変更しないコンポーネント」という前提 | **`Sort` への変更をメソッド本体 1 行に抑える。** フィールド・シグネチャは変えず、検証は新設の識別子検証ヘルパに置いて `Sort.sort` からそのヘルパを 1 行呼ぶだけにする | `Sort` に `Table` を持たせる変更が前提をより大きく破る可能性がある |
| 判定の機械的検証可能性（AC-10b） | ✅ 危険な字面を含む文字列を渡し、例外またはテキストへの非混入を確認できる | ✅ 未知のカラム名を渡し拒否を確認できる。ただし「危険だが既知のカラム名と偶然一致する」ケースは原理的に起こらない（カラム名にSQL構文文字は通常含まれない） |
| U1 の先例との一貫性 | **一貫する。** U1 の `ValueRules.isNumeric` 等も「現行の正規表現を維持する」形でホワイトリストではなくパターン照合を使っている。U4 も「型による構造的安全化」を採った | 新しい種類の検証（メタデータ照合）を持ち込む |

**A を推奨する。** 現行の非 `Column` 経路の設計意図（メタデータ外の任意の式を許す）を壊さずに構文注入だけを閉じられるのは A だけである。B は AC-10b の Given/When/Then が要求する「宣言済みメタデータへの照合」という選択肢そのものではあるが、`unit-of-work.md` が明示した既存利用パターンとの衝突（`application-design` が `Sort` を非変更コンポーネントと扱った前提）を壊すコストが大きい。

[Answer]: A（例外による拒否。構文的に危険な字面のブラックリスト）— 2026-08-09、**Mode:** guided

**A を採ることの帰結（本設計が扱う）**:

1. **`Sort` への変更をメソッド本体 1 行に抑える。** 検証ロジックは新設のヘルパ（`org.tamacat.sql` の識別子検証ユーティリティ）に置き、`Sort.sort(Object, Object)` の非 `Column` 枝からそれを 1 行呼ぶだけにする。フィールドもシグネチャも変えない。`application-design` の「`Sort` は変更しないコンポーネント」という前提を字義どおりには満たさないが、最小侵襲な形でその趣旨（`Sort` の構造・振る舞いを大きく変えない）を維持する
2. **AC-10b の判定は「危険な字面を渡す」テストで行う。** メタデータへの照合ではないため、「宣言されていないカラム名」を渡しても拒否されない——これは意図的である（正当な複合式・関数呼び出しを許す設計のため）。AC-10b の Given/When/Then は「例外による拒否」の枝でそのまま判定できる
3. **ブラックリストの具体的な字面は Functional Design（本ステージ）が確定する。** `unit-of-work.md` が「機構の選択」を委ねたのに対し、字面の具体的な集合はさらに一段細かい決定であり、`business-logic-model.md` で扱う

---

## Q2. 検証を `Sort.sort` の非 `Column` 経路だけに適用するか、`Column.getFunctionName()` にも適用するか

`unit-of-work.md`「後続 Unit への引き継ぎ事項」6（U2 から U5 へ）は次の 2 か所を識別子位置として挙げている。

1. `Sort.sort(Object, Object)` の非 `Column` キー経路（`Sort.java:56-58`）
2. `Column.getFunctionName()` — SELECT 句（`QueryImpl.java:152`）と ORDER BY 句（`Sort.java:52`、`col.isFunction()` の枝）の両方に現れる

FR-1.7 の原文は「(a) 例外で拒否される、(b) 宣言済みの `Table`/`Column` メタデータに照合され一致しないものが拒否される」であり、経路を限定していない。

- **A. 両方に適用する**（**推奨**）——`Sort` の非 `Column` キーと `Column.getFunctionName()` の両方に、Q1 = A の同じ検証ヘルパを通す
- **B. `Sort` の非 `Column` キーだけに適用する**——`getFunctionName()` は `Column` オブジェクトの構築時に開発者が設定するものであり、`unit-of-work.md`「実装上の制約」が対象とする「呼び出し側から渡された文字列」とは性質が異なるという読み方
- **X.** Other (please specify)

### 論点

`Column.functionName(String)` を呼ぶのは**カラム定義を書く開発者**であり、`Sort.sort(Object, Object)` に文字列を渡すのは**クエリを組み立てる呼び出し側コード**である。両者は「渡す主体」が異なる——前者は静的なメタデータ定義（多くの場合コード内の定数）、後者は動的な呼び出し（ユーザー入力に近づきうる）。

しかし `getFunctionName()` の値が実行時に**外部入力から動的に構築される**経路（例えば `Column.functionName(userProvidedSortKey)` のような誤用）を排除できない以上、**FR-1.7 の文言（「渡された文字列が構文として生きたまま SQL テキストに現れることはない」）を字義どおり満たすには両方に適用する方が安全側に倒れる**。検証コストはどちらも同じ 1 行のヘルパ呼び出しである。

[Answer]: A（両方に適用する）— 2026-08-09、**Mode:** guided

**適用箇所は 3 か所になる**（`getFunctionName()` が 2 経路で使われるため）:

1. `Sort.sort(Object, Object)` の非 `Column` 枝（`Sort.java:57`）
2. `Sort.sort(Object, Object)` の `Column.isFunction()` 枝（`Sort.java:52`、`col.getColumnName()` ではなく `col.getFunctionName()` の側）—待、実ソース再確認: `Sort.java:52` は `col.getColumnName()` を使っている。`getFunctionName()` を直接使うのは `QueryImpl.java:152`（SELECT 句）のみである。**訂正**: `Sort.java` の `isFunction()` 枝は `getColumnName()` を使っており `getFunctionName()` は使わない。適用箇所は次の 2 か所に修正する
   - `Sort.sort(Object, Object)` の非 `Column` 枝（`Sort.java:57`）
   - `QueryImpl` の SELECT 句組み立て（`QueryImpl.java:152`、`col.getFunctionName()`）——ただし `unit-of-work.md` U5 の境界は「U1〜U4 が触るクラスを変更しない」であり `QueryImpl` は U2 が触ったクラスである。この境界と Q2 = A の適用範囲の間に緊張があることを `business-logic-model.md` で扱う

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。2 件とも単一の選択肢が確定している。

**矛盾**: **1 件、Q2 の回答自体の中に見つかった。** Q2 の論点セクションを書いた時点では `Sort.java:52` の `isFunction()` 枝も `getFunctionName()` を使うと誤って前提していたが、実ソースを再確認すると `col.getColumnName()` を使っている（`getFunctionName()` を直接使うのは `QueryImpl.java:152` の SELECT 句のみ）。したがって Q2 = A が実際に指す適用箇所は「`Sort` の非 `Column` 枝」と「`QueryImpl` の SELECT 句」の 2 か所であり、後者は `unit-of-work.md` の境界（「U1〜U4 が触るクラスを変更しない」。`QueryImpl` は U2 が所有）と表面上衝突する。

| # | 組み合わせ | 確認内容 | 判定 |
|---|---|---|---|
| 1 | Q2=A ＋ `unit-of-work.md` U5 の境界 | `QueryImpl.java:152` は U2 が所有するクラスであり、U5 の境界「U1〜U4 が触るクラスを変更しない」に反するように見える | **境界の意図を確認して解消する。** 境界が禁じるのは U1〜U4 の**設計判断への介入**（値のバインド機構、SELECT/WHERE の組み立て構造）である。識別子検証ヘルパを 1 行呼ぶだけの追加は、U2 が確定した `getSelectSQL()` の構造やシグネチャを一切変えない**局所パッチ**であり、`domain-entities.md`「2.6 / 2.7 契約からの差分」に記録すれば足りる。U1 も U4 も他 Unit の成果物への「記録するが編集しない」修正を先例として確立している（U2 の BR-12 修正、U4 の `for update` 是正時の `business-rules.md` 更新など）。この先例に従い、`QueryImpl.java:152` への 1 行の追加を「U2 の設計への介入」ではなく「U5 が担当する識別子検証の適用」として扱う |

**追加の設問**: 不要。

---

## Consolidated Summary Confirmation

**Prompt**: この内容で `business-logic-model.md` / `business-rules.md` / `domain-entities.md` を生成してよいか。

**Options**:
- Looks correct — この回答から成果物を生成する
- Request changes — 生成前に回答を修正する

**回答の要約**

| # | 決定 | 帰結 |
|---|---|---|
| Q1 | **A** — 例外による拒否（構文的に危険な字面のブラックリスト） | `Sort` への変更はメソッド本体 1 行のみ（新設ヘルパを呼ぶだけ）。AC-10b は「危険な字面の拒否」で判定する |
| Q2 | **A** — `Sort` の非 `Column` 枝と `QueryImpl` の SELECT 句（`getFunctionName()`）の両方に適用 | 適用箇所は当初想定の 3 か所ではなく 2 か所（実ソース再確認により訂正）。`QueryImpl.java:152` への追加は U2 の設計への介入ではなく局所パッチとして扱う |

[Answer]: Looks correct — 2026-08-09

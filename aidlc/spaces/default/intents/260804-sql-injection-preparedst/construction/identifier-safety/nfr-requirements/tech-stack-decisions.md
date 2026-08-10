# Tech Stack Decisions — U5 `identifier-safety`

## 上流成果物との関係

- **`business-logic-model.md`** / **`business-rules.md`**（functional-design 3.1, U5）— `IdentifierRules`（新規 public 型）、BR-1〜BR-14。
- **`requirements.md`** — CON-1、CON-6、NFR-5、NFR-6、NFR-7。
- **`technology-stack.md`**（codekb）— Maven、compiler 3.8.1、surefire 2.22.2、JaCoCo（U1 が追加）。
- **U1〜U4 の `tech-stack-decisions.md`** — TSD-1〜15。U5 はこれらを継承し、番号を TSD-16 から振る。

`security-requirements.md` は本文書と対になる成果物である。Q1（JaCoCo ゲートへの追加）と Q2（`MIGRATION.md` への追記）はいずれも本文書が TSD-16 / TSD-19 として扱う。

---

## この文書の範囲

**U5 は `IdentifierRules` という新規クラスを 1 つ追加する。** U1 の「新規クラスを追加する」性質に近く、U2〜U4 の「既存クラスの一部を書き換える」性質とは異なる。本文書の中身は 3 つである。

1. **JaCoCo ゲートへの追加**（TSD-16）——U1 の先例を継承する判断
2. **変えないことの確認**（TSD-17、TSD-18）
3. **`MIGRATION.md` への追記**（TSD-19）

---

## TSD-16. `IdentifierRules` を JaCoCo ゲートに追加する

| 項目 | 内容 |
|---|---|
| 決定 | `jacoco-maven-plugin` の `check` ルールの `<includes>` に `org.tamacat.sql.IdentifierRules` を追加し、U1 の 8 クラスと同じ 80% line coverage でゲートする |
| 判定基準 | `pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` に `IdentifierRules` が含まれる |
| 由来 | 本ステージ Q1 = A |

### U1 の先例がそのまま当てはまる理由

| 項目 | U1（拡張した） | U2/U4（拡張しなかった） | U5 |
|---|---|---|---|
| 新設するクラス | 8 個 | 0 個 | **1 個**（`IdentifierRules`） |
| 変更するクラス | なし | 5 個 / 3 個（既存レガシー） | **なし**（`Sort` / `QueryImpl` は各 1 行の呼び出し追加のみで、クラス自体は「変更するクラス」に数えない——ゲート対象にはしない） |
| ゲートの表現 | 意味を持つ（新規クラスのみ） | 既存の未被覆コードごと測ることになる | **意味を持つ**（`IdentifierRules` は U5 が新設した完結したクラスであり、既存の未被覆コードを巻き込まない） |

`IdentifierRules` は状態を持たない `static` メソッド 1 つのクラスであり、U1 の 8 クラスと同じ「新規・単体テストで完結する」性質を持つ。U2（`tech-stack-decisions.md` TSD-6）が却下した理由（「既存の未被覆コードごと測ることになる」）は `IdentifierRules` には当てはまらない。

### `Sort` / `QueryImpl` をゲート対象に加えない

`Sort` と `QueryImpl` はメソッド本体 1 行が変わるだけであり（BR-12、BR-13）、U2 / U4 と同じ理由（既存の未被覆コードごと測ることになる）でゲート対象に加えない。

---

## TSD-17. compile スコープの第三者依存を追加しない

| 項目 | 内容 |
|---|---|
| 決定 | U5 は compile スコープの第三者依存を 1 つも追加しない |
| 判定基準 | `mvn dependency:tree` の出力が変更前と一致する |
| 由来 | CON-6、U1 TSD-1、`security-requirements.md` SEC-26 |

`IdentifierRules` は JDK 標準 API（`String.indexOf`）のみで書ける。

---

## TSD-18. Java 8 を維持する

| 項目 | 内容 |
|---|---|
| 決定 | `maven.compiler.source` / `target` を `1.8` のまま維持する |
| 判定基準 | `pom.xml:17-18` / `:101-102` が `1.8` のまま |
| 由来 | CON-1、NFR-4 |

### U5 が使う言語機能・API の全数確認

| 構成要素 | 使用箇所 | 最低要求 | 判定 |
|---|---|---|---|
| `String.indexOf(char)` / `indexOf(String)` | `IdentifierRules.validate` | Java 1.0 | ✅ |
| `private` コンストラクタ（ユーティリティクラスのインスタンス化防止） | `IdentifierRules` | Java 1.0 | ✅ |
| `public static` メソッド | `IdentifierRules.validate` | Java 1.0 | ✅ |

**`Pattern` / `Matcher` は使わない。** `IdentifierRules` は正規表現ではなく `indexOf` の連鎖で判定する（`business-rules.md` BR-1）。U1 の `ValueRules.isNumeric`（正規表現）とは対照的である。

### テストの構成

| 項目 | 決定 |
|---|---|
| テストフレームワーク | JUnit 4 のまま |
| 新設するテスト | `IdentifierRulesTest`（新規クラスに対する単体テスト。純増、NFR-6） |
| `SortTest` | アサートを追加する（既存アサートは変更しない） |
| `QueryImplTest` | SELECT 句の `functionName` 検証についてアサートを追加する |

---

## TSD-19. `MIGRATION.md`（U2 が新設）に識別子検証による破壊的変更を追記する

| 項目 | 内容 |
|---|---|
| 決定 | `MIGRATION.md`「4. 注意点」に、`Sort.sort` / `Sort.asc` / `Sort.desc` の非 `Column` キーと `Column.functionName(...)` に渡す文字列の制約を追記する |
| 判定基準 | `MIGRATION.md`「4. 注意点」に、危険な字面（`'`、`"`、`;`、`--`、`/*`、`*/`）を含む識別子文字列が例外になる旨の記述がある |
| 由来 | 本ステージ Q2 = A |

### 追記する内容

```markdown
## 4. 注意点（追記）

- **識別子位置に渡す文字列に制約が加わりました。** 次の 2 箇所に渡す文字列に、`'`（シングルクォート）、
  `"`（ダブルクォート）、`;`（セミコロン）、`--`（行コメント）、`/*` `*/`（ブロックコメント）のいずれかが
  含まれていると `InvalidParameterException` が投げられるようになりました。**これは破壊的変更です**。
  - `Sort.sort(Object, Object)`（および `Sort.asc(Object)` / `Sort.desc(Object)`）に `Column` 以外のキー
    （生の文字列等）を渡す場合——**その呼び出しの時点で**例外が投げられます（SQL 組み立て時ではありません）。
    文字列リテラルを含む ORDER BY 式（例: `sort.sort("TO_CHAR(created,'YYYY')", Sort.Order.ASC)`）を
    使っている呼び出し側コードは、この変更以降エラーになります
  - `Column.functionName(String)` に設定する関数名の文字列——**SELECT 句を組み立てる時点で**例外が
    投げられます。`TO_CHAR(created,'YYYY')` のような文字列リテラルを含む関数式を `functionName(...)` に
    設定しているコードは、この変更以降エラーになります

  **代替経路（`columnName` に式を設定した `Column` を使う）は ORDER BY・SELECT のいずれでも使えますが、
  前提条件があります。** `functionName(...)` / `isFunction()` を一切使わず、`columnName(...)` に文字列
  リテラルを含む式を設定した `Column` を渡してください——`Sort.sort` の `Column` 経路（`isFunction()` の
  真偽いずれの枝も）と `QueryImpl` の SELECT 句の非関数枝はいずれも `col.getColumnName()` /
  `MappingUtils.getColumnName(col)` を使い、これらは検証対象外のままです。**ただし、その `Column` が
  `Table` に登録されている（`getTable() != null`）場合、`MappingUtils.getColumnName` はテーブル名を
  前置して `"テーブル名.式"` という壊れた SQL を、例外を投げずに生成します。** 代替経路として使う
  `Column` は**必ず `Table` に登録しない**（コンストラクタでテーブルを渡さない）形にしてください。この
  前提を外すと、例外にもならず気づきにくい形で SQL が壊れます。
- **この制約はテーブル名・カラム名そのものには適用されません。** `Column.columnName(String)` や
  テーブル名の設定は本リリースでは検証対象外です（既知の制約。将来のリリースで拡張される可能性が
  あります）。
```

**U2/U4 の追記との違いを明示する。** U2 の追記（`andOuterJoin` の非推奨化）と U4 の追記（性能特性の変化）はいずれも「新しい代替経路がある」「性能が変わる」という**非破壊的**な性質だった。U5 の追記は**既存の正当な呼び出しがエラーになりうる**という点で性質が異なり、より目立つ形（太字の「破壊的変更」表記）で記載する。

### 却下した形

| 選択肢 | 却下理由 |
|---|---|
| 追記しない | 既存呼び出しを壊しうる変更を利用者向け文書のどこにも書かないのは無責任である |
| 独立した BREAKING_CHANGES.md を新設する | U2 が `MIGRATION.md` に一本化する判断を既に下しており（TSD-9「腐敗リスクへの対処」）、U5 単独でこれを覆す理由がない |

---

## 未解決事項（Build and Test 3.6 へ）

U1〜U4 が送った項目に加えて、U5 から次を送る。

| # | 事項 | 由来 |
|---|---|---|
| 16 | `IdentifierRulesTest` の内容——ID-1〜ID-5 それぞれの拒否、`null` の素通し（BR-14）、危険な字面を含まない正当な複合式の通過 | `security-requirements.md` 検証項目 19〜22 |
| 17 | `MIGRATION.md` 追記内容とソースの整合確認 | TSD-19 |
| 18 | AC-10b の部分被覆をカバレッジ報告に反映する | `security-requirements.md` 検証項目 23 |

---

## 現行スタックの確認（変更なし）

| 項目 | 状態 |
|---|---|
| ビルドツール | Apache Maven。変更なし |
| `maven-compiler-plugin` 3.8.1 | 変更なし（TSD-18） |
| `maven-surefire-plugin` 2.22.2 | 変更なし |
| `jacoco-maven-plugin` | **`<includes>` に `IdentifierRules` を追加**（TSD-16） |
| compile スコープ依存 3 件 | 変更なし（TSD-17） |
| リポジトリルートの構成 | `MIGRATION.md` に 1 節を追記する（TSD-19）。新規ファイルは作らない |

---

## Review

<!-- reviewer が記入する -->

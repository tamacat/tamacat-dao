# Business Rules — U5 `identifier-safety`

## 上流成果物との関係

- **`unit-of-work.md`**（units-generation, 2.7）— U5 の責務・境界・依存 U2、既存の意図的な柔軟性（`Sort.sort` の非 `Column` キー経路）。BR-4 / BR-6 はここに直接対応する。
- **`unit-of-work-story-map.md`**（同上）— U5 が担う FR-1.7（Should）、判定する AC-10b、実装順序 1〜4。
- **`requirements.md`**（requirements-analysis 2.3）— FR-1.7 の原文、AC-10b、NFR-1（対象範囲は FR-1.7 を含まない）、NFR-3、CON-1。
- **U1 `bind-foundation` の `security-requirements.md`** — SEC-4（例外メッセージにバインド値を含めない）。BR-2 が参照する。
- **U2 `select-path` の 3.1 成果物** — § 11 引き継ぎ 6、BR-19（早期失敗の先例）。

`domain-entities.md` が状態と不変条件（IR-n）、`business-logic-model.md` がアルゴリズムを扱う。**本文書は「どの経路でも守られなければならない規則」を列挙する。**

---

## 規則一覧

| # | 規則 | 由来 |
|---|---|---|
| BR-1 | `IdentifierRules.validate` は正規表現を使わず `String.indexOf` の連鎖で判定する。文字列長に対して線形であり、バックトラックのリスクを持ち込まない | U1 R-6 との対比 |
| BR-2 | 例外メッセージに検証対象の識別子文字列を含めてよい。識別子は U1 SEC-4 が対象とする「バインド値」ではなく、呼び出し側が渡した SQL 断片相当の文字列である | U2 SEC-15 の先例 |
| BR-3 | `Sort.sort(Object, Object)` の非 `Column` 枝でのみ `IdentifierRules.validate` を呼ぶ。`Column` の枝（メタデータ由来）は検証しない | Q1 = A、Q2 = A |
| BR-4 | 「呼び出し側から渡された文字列」とは `Sort.sort` の非 `Column` 枝の `k` を指す。`Column` オブジェクトはメタデータであり対象外である | `unit-of-work.md` U5 の実装上の制約 |
| BR-5 | `Column.getColumnName()` / `MappingUtils.getColumnName(col)`（テーブル名・カラム名そのもの）は検証しない。**これは `functionName()` に検証を適用した Q2 = A の判断と表面上は非対称である**——`Column.columnName(String)` と `Table` のテーブル名設定はいずれも `functionName(String)` と同じ形の public セッターであり、理屈のうえでは同じ経路で動的に構築されうる。**技術的に検証を挟めないわけではない**——`MappingUtils.getColumnName(col)`（`table.getTableNameWithSchema() + "." + col.getColumnName()`）の呼び出し箇所（`Sort.java:54`、`QueryImpl.java:154`）を `IdentifierRules.validate(...)` で包めば、`Column` / `DefaultColumn` / `Table` 自体には触れずに同じ手法を適用できる。**それでも本ステージでは見送る**——これはスコープを絞る決定であり、技術的な不可能性ではない。理由は 2 点: (1) `MappingUtils.getColumnName` は U1〜U4 の全経路（WHERE / FROM / SELECT / ORDER BY）で広く使われており、そこに検証を追加すると影響範囲が「非 `Column` の ORDER BY キーと SELECT の関数名」という当初のスコープ（`unit-of-work.md` が示した既存の意図的な柔軟性）を大きく超え、U1〜U4 が確定した経路すべてに新しい失敗モードを持ち込む。(2) `Column.getColumnName()` はメタデータとして開発者がコード上で定義するのが通常の用法であり、実行時に外部入力から動的に構築される具体的な悪用経路が `unit-of-work.md` にも `requirements.md` にも示されていない。**したがってテーブル名・カラム名そのものの安全化は本ステージのスコープから明示的に外れ、残存リスクとして記録する（R-5）——将来必要になれば `MappingUtils.getColumnName` の呼び出し箇所を包む同じ手法で拡張できる** | `functional-design-questions.md` Q2 の論点、iteration 2 の是正 |
| BR-6 | `Sort.Order`（`o.toString()`）は検証しない。ソート方向は識別子位置ではなく、`unit-of-work-story-map.md` が定めた U5 の担当範囲外である | 本ステージの決定 |
| BR-7 | `QueryImpl` の SELECT 句組み立てで `col.isFunction()` が真のとき、`col.getFunctionName()` にのみ `IdentifierRules.validate` を呼ぶ。同じ枝の `col.getColumnName()` は検証しない | BR-5 と同じ論拠 |
| BR-8 | `QueryImpl.getSelectSQL()`（リテラル版）にも検証がかかる。`getSelectSQL()` と `getSelectPreparedSql()` は同じ `buildSelectClause` を経由するため、検証は自動的に両経路に効く | `business-logic-model.md` § 3.2、U2 § 4.1 |
| BR-9 | `DataType.FUNCTION`（値の分類）は U5 の対象外である。U1 の `BindSqlBuilder.tokenFor`（BR-9、U1 番号）が既に扱っており、U5 は再確定しない | `business-logic-model.md` § 4 |
| BR-10 | 検証で拒否する字面は ID-1〜ID-5（`'`、`"`、`;`、`--`、`/*`、`*/`）に限る。識別子として正当な文字（英数字・アンダースコア・ドット・括弧・アスタリスク・カンマ・空白）はホワイトリスト化せず通す | Q1 = A の帰結、`domain-entities.md` |
| BR-11 | 正当な複合式・関数呼び出しのうち、**ID-1〜ID-5 の字面を含まないもの**（`"RAND()"`、`"COUNT(*)"`、`"t1.col1, t2.col2"` 等）は引き続き通す。**文字列リテラルを含む式（`"TO_CHAR(d,'YYYY')"`、`"CASE WHEN x='A' THEN 1 ELSE 0 END"` 等）は `'` を含むため ID-1 に該当し拒否される**——これは既存の柔軟性を狭める副作用であり、R-6 に記録する | `unit-of-work.md` U5 のリスク記述、R-6 |
| BR-12 | `Sort` クラスへの変更は `sort(Object, Object)` メソッド本体 1 行のみ（＋ファイル先頭の `import org.tamacat.sql.IdentifierRules;` 1 行）。フィールド・シグネチャは変更しない | `application-design` の「`Sort` は変更しないコンポーネント」という前提。同表の `Sort` 行は「本ステージでは変更対象としない」という OQ-9 待ちの条件付き記述であり（`components.md:273`）、`Column` / `Table` 行の無条件の記述（`:267`）とは性格が異なる |
| BR-13 | `QueryImpl` への変更は SELECT 句組み立てループ内の 1 行のみ（＋ `import` 1 行）。U2 が確定した組み立て構造・順序・値の結合には触れない | `unit-of-work.md` U5 の境界、U2 の設計への非介入 |
| BR-14 | `IdentifierRules.validate(null)` は例外を投げず `null` を返す（素通し）。`Column.isFunction() == true` かつ `getFunctionName() == null` という既存の正当な状態（`DefaultColumn` が `Column.FUNCTION` 定義だけで `functionName` を設定しない場合に生じる）を壊さないための訂正。`null` はいかなる注入字面も持ちえないため安全性は損なわれない | iteration 2 の是正、`DefaultColumn.java`、`ColumnFunctionTest.java` |

---

## 失敗様式

### 失敗様式 1 — ブラックリストの網羅漏れ

BR-10 のブラックリストは「よく知られた注入パターン」を対象にしており、**すべての SQL 方言のすべての構文的特殊文字を網羅する保証はない**。例えば、一部の DB 固有の識別子引用符（バッククォート `` ` `` 等）はブラックリストに含まれていない。

**検出**: 是正しないが、`business-rules.md`「現行から引き継ぐ既知の欠陥」に類する扱いとして残存リスクに記録する（R-1）。FR-1.7 は Should Have であり、AC-10b が要求する 2 機構（例外 / メタデータ照合）のいずれも「あらゆる方言の特殊文字を完全に列挙する」ことまでは要求していない。

### 失敗様式 2 — 呼び出し順序による迂回

`IdentifierRules.validate` は `Sort.sort` と `QueryImpl` の SELECT 句組み立ての**呼び出し時点**でのみ発火する。`Column` オブジェクトを直接操作して `getFunctionName()` の戻り値を後から書き換えるような迂回（`DefaultColumn` の setter を検証後に再度呼ぶ等）は、検証済みの値をキャッシュしない設計であるため**発生し得ない**——`col.getFunctionName()` は呼び出しのたびに現在の値を返し、`QueryImpl` は SELECT 句組み立てのたびに再評価する。したがって迂回の余地はない（IR-4 の冪等性が支える）。

---

## 残存リスク

| ID | 残存リスク | 由来 | 扱い |
|---|---|---|---|
| **R-1** | ブラックリスト（ID-1〜ID-5）が網羅する字面は代表的な注入パターンに限られる。DB 固有の識別子引用符やエンコーディングを使った迂回（例: URL エンコード、Unicode の同型文字）は対象外である | BR-10、失敗様式 1 | **受容。** FR-1.7 は Should Have であり、AC-10b の判定はブラックリストによる拒否で成立する。網羅性の拡張は本ステージの範囲外 |
| **R-2** | `Sort.Order`（`o.toString()`）は検証しない。理論上 `sort.sort(col, "危険な文字列")` のような誤用が可能である | BR-6 | **受容。** `unit-of-work-story-map.md` が定めた U5 の担当範囲（識別子位置）に `Order` は含まれない |
| **R-3** | `DataType.FUNCTION` の値（U1 が扱う）は本ステージの対象外のまま残る。呼び出し側が `DataType.FUNCTION` のカラムに危険な文字列を値として渡した場合の扱いは U1 BR-9 / R-1 が定義済みであり、U5 では変更しない | BR-9、U1 R-1 | **U1 の受容判断をそのまま引き継ぐ。** U5 が新たに評価する対象ではない |
| **R-4** | 正当な複合式（BR-11）を通す設計上、`"col1 = 1 OR 1=1"` のような**識別子として不正だが構文的に「危険な字面」を含まない**文字列は依然として通る——これは識別子ではなく述語の注入であり、ブラックリストでは検出できない | Q1 = A の設計選択（ホワイトリスト化しない） | **受容。** この経路（`Sort.sort` に任意の文字列を渡す）はそもそも「呼び出し側が SQL を書く」ことを前提にした raw 経路の一種であり、U2 の FR-7.1（raw-SQL 経路は SM-1 の対象外）と同じ性質の残存リスクである。`business-rules.md`（U2）R-12 が既に「識別子位置は FR-1.7 の対象、U2 は `Sort` に触れない」と記録しており、本ステージの決定はこの性質を変えない——**FR-1.7 が求めるのは「構文として生きたまま現れない」ことであり、「呼び出し側が渡した式が常に安全である」ことではない** |
| **R-5** | **AC-10b が Given で挙げる 3 つの識別子位置（ORDER BY 句のキー、テーブル名、カラム名）のうち、本ステージが検証するのは実質 1.5 個である**——`Sort` の非 `Column` キー（ORDER BY）と `QueryImpl` の `getFunctionName()`（関数名）だけを検証し、`Column.columnName(String)` / `Table` のテーブル名設定を経由するテーブル名・カラム名そのものは検証しない（BR-5） | BR-5、AC-10b の Given、`unit-of-work.md` U5 の境界 | **受容（スコープを明示的に narrow）。技術的な不可能性ではなくスコープの決定である**——`MappingUtils.getColumnName(col)`（`Sort.java:54`、`QueryImpl.java:154`）の呼び出し箇所を `IdentifierRules.validate(...)` で包めば、`Column` / `DefaultColumn` / `Table` 自体には触れずに同じ手法を適用できる。それでも見送るのは、(1) `MappingUtils.getColumnName` が U1〜U4 の WHERE / FROM / SELECT / ORDER BY のほぼ全経路で使われており、そこへの追加は本ステージが意図したスコープ（`unit-of-work.md` が明示した既存の柔軟性の保護）を大きく超えるため、(2) `Column.getColumnName()` は通常メタデータとして開発者がコード上で定義する値であり、実行時に外部入力から動的に構築される具体的な悪用経路が上流のどこにも示されていないためである。FR-1.7 は Should Have であり、AC-10b の「例外 or メタデータ照合」はどちらの機構を選んでも**適用箇所の網羅性**までは保証しない。将来必要になれば `MappingUtils.getColumnName` の呼び出し箇所を包む同じ手法で拡張できる |
| **R-6** | ブラックリストが `'` を拒否するため、文字列リテラルを含む正当な ORDER BY 式（`TO_CHAR(d,'YYYY')` 等）は非 `Column` 経路を通せなくなる——既存の柔軟性を狭める副作用である | BR-11、Q1 = A の帰結 | **受容。** 安全性（構文注入の拒否）と表現力（任意の文字列リテラルを含む式）はブラックリスト方式では両立しない。文字列リテラルを含む式が必要な場合、呼び出し側は `Column` 経由（`col.isFunction()` の枝、検証対象外）で同等の式を組み立てる代替経路がある |

---

## 判定できる受け入れ条件

| AC | 内容 | 本 Unit のどこが満たすか | 判定できる時点 |
|---|---|---|---|
| **AC-10b** | 識別子位置に SQL の構文文字を含む文字列が渡されたとき、例外で拒否されるか、メタデータ照合で拒否されるかのいずれかが起こり、渡された文字列が構文として生きたまま SQL テキストに現れない。**本ステージが満たすのは ORDER BY の非 `Column` キーと SELECT の関数名の 2 経路のみ。テーブル名・カラム名そのものは対象外（R-5）** | `business-logic-model.md` § 2（`Sort`）、§ 3（`QueryImpl`）。BR-3、BR-7、BR-10 | U5 完了時（テストの追加は Build and Test 3.6） |

---

## Unit をまたぐ要件の遵守

| ID | 内容 | U5 の状態 |
|---|---|---|
| NFR-1 | 実行経路に値の文字列連結が 0 件 | **対象外。** `requirements.md` が明示するとおり、NFR-1（SM-1）の対象範囲は FR-1.7 を含まない |
| NFR-3 | public API に破壊的変更なし | ✅ **削除・シグネチャ変更なし**（`IdentifierRules` は新規 public 型として追加されるが、既存クラスのメンバは 1 つも削除・変更しない。`domain-entities.md`「2.6 / 2.7 契約からの差分」参照）。`InvalidParameterException` という新しい失敗モードが加わるが、これは既存 API の**シグネチャ**を変えるものではない（U2 の BR-19 と同じ扱い） |
| NFR-5 | 変更後の全テストがグリーン | `SortTest` は無変更で緑になる見込み（すべて `Column` を渡しており非 `Column` 枝を経由しないため、BR-3 の変更の影響を受けない） |
| NFR-6 | テスト件数が変更前を下回らない | ✅ 純増（AC-10b のテストを Build and Test 3.6 で追加） |
| NFR-7 | 性能目標は設定しない | ✅ `indexOf` の連鎖は既存の文字列処理と同水準のコストであり、最適化を意図した変更ではない |
| CON-1 | Java 8 | ✅ 使用する API は `String.indexOf` のみ（Java 1.0） |

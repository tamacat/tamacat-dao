# Security Requirements — U3 `write-path`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U3、iteration 2 適用記録込み）— § 2（`getInsertPreparedSql`）、§ 3 / § 3.1（`getUpdatePreparedSql`、`buildBindWhere` 共有ヘルパ）、§ 4（`getDeletePreparedSql` / `getDeleteAllPreparedSql`）、§ 5 / § 5.1 / § 5.2（BLOB 実行、`DBAccessManager` / `Dao` / `DaoAdapter` 側）、§ 6（`Dao` の既定 `create`/`update`/`delete`）、§ 7（`getBlobIndex()` の二重の意味）、§ 9（新規メンバ、Pr-1〜Pr-10）。本文書の脅威モデルはこの経路を対象にする。
- **`business-rules.md`**（同上）— BR-1〜BR-10（値アキュムレータの分離、トークン取得、WHERE 描画の共有）、BR-11（BLOB は自動判定しない責任分担）、BR-12（FR-6.2 のリテラル版修正）、BR-15（`BindSqlBuilder` のローカル変数化）、BR-16 / BR-17（`Dao.executeUpdate(PreparedSql)` の本体、`DaoAdapter` の独立追加）、失敗様式 1〜3、残存リスク R-1〜R-4。
- **`requirements.md`**（requirements-analysis 2.3）— FR-1.4（Must、INSERT VALUES / UPDATE SET のバインド化）、FR-1.5（Must、BLOB は現行同様バインド）、FR-3.2（Must、`getBlobIndex()` はバインドパラメータ位置）、FR-6.2（Must、`:268` OBJECT 分岐のテーブル修飾不整合の解消）、NFR-1（判定基準は「実行経路に値の文字列連結が 0 件」）、NFR-3、NFR-5、NFR-6、CON-1、AC-3b、AC-7。
- **`technology-stack.md`**（codekb）— compile スコープ依存 3 件、ビルド JVM が JDK 25 Corretto、カバレッジ計測ツールの不在。
- **U1 `bind-foundation` の `security-requirements.md` / `tech-stack-decisions.md`** — SEC-1〜SEC-9、R-1〜R-7、TSD-1〜TSD-5。
- **U2 `select-path` の `security-requirements.md` / `tech-stack-decisions.md`** — SEC-10〜SEC-17、R-8〜R-15、TSD-6〜TSD-10、とくに TSD-9 の `MIGRATION.md` 新設と 4 節構成。
- **`domain-entities.md`**（functional-design 3.1, U3）— WV-1〜WV-5（値アキュムレータの不変条件、SEC-28 が引用する WV-3 相当）、BI-1〜BI-3（`blobIndex` の不変条件、SEC-29 が引用する BI-2）。

`tech-stack-decisions.md` は本文書と対になる成果物で、Q1（カバレッジゲート）と Q2（BLOB override の文書化）を扱う。

---

## この文書の範囲

**U3 も `kind: library` である**（`unit-of-work.md` U3 の表）。認証・認可・暗号化・規制フレームワーク・データ所在地は U1・U2 と同じく該当なしである（U1「この文書の範囲」の表を継承する）。

**U3 は U2 とも性質が異なる。**

| | U2 `select-path` | U3 `write-path` |
|---|---|---|
| Unit の性質 | 既存 5 クラスを書き換え、**新経路への切り替えは自動**（`Dao.search` を呼べば必ずバインド版を経由する） | 既存クラスを書き換えるが、**BLOB を含む書き込みだけは自動的に新経路へ乗らない**——既定 `create`/`update` は非 BLOB 経路のみを使い、BLOB はサブクラスの override 責務のまま（現行と同じ責任分担、BR-11） |
| 中心的な脅威 | 値が SQL 構文として解釈される（T）＋ 述語が黙って欠落する（T の別形） | 値が SQL 構文として解釈される（T）＋ **値の位置がずれてバインドされる（T の別形、失敗様式 1）** |
| 検査をすり抜ける失敗様式 | 同期規則の破れ（個数検査を通過） | SET と WHERE の値が 1 つずつずれる（`?` の個数は一致したまま） |

U3 が新たに作る「情報の露出面」はない——INSERT / UPDATE / DELETE のいずれも U1 の `BindSqlBuilder` / `PreparedStatementBinder` を経由し、値の運搬経路は SEC-1〜SEC-9 が既に固定している。本文書の中心は、**値の個数ではなく位置**が正しいことをどう担保するかにある。

---

## 脅威モデル（STRIDE）

`threat-modelling-stride.md` の 6 カテゴリを U3 に当てる。

| カテゴリ | U3 での評価 | 対応 |
|---|---|---|
| **S** Spoofing | **該当なし。** U1・U2 と同じくライブラリは同一性を主張する主体を持たない | — |
| **T** Tampering | **中心的関心。3 つの形を持つ。** (1) 値が SQL 構文として解釈される——INSERT / UPDATE / DELETE の全経路をバインド化することで構造的に解消する。(2) **値の位置ずれ**——`insertValues` / `setValues` を `bindFragments` 由来の WHERE 値と誤った順序で結合すると、個数は一致したまま全ての値が 1 つずつずれてバインドされる（失敗様式 1、U3 で最も危険）。(3) **BLOB の位置ずれ**——`blobIndex` の算出を誤ると `setBinaryStream` が誤った列を上書きする（失敗様式 2） | SEC-27、SEC-28、SEC-29 |
| **R** Repudiation | **該当なし。** U3 は実行記録の仕組みに触れない。`DBAccessManager.executeUpdate(PreparedSql, int, InputStream)` は U1 の `record` / `checkBindable` をそのまま踏襲し、U1 SEC-9（実行記録を監査ログとして位置づけない）を継承する | 継承 |
| **I** Information Disclosure | **U3 は新しい露出面を作らない。** INSERT / UPDATE の値は現行も `getInsertSQL` / `getUpdateSQL` の SQL テキストに埋め込まれていた値と同じであり、`BindValue` として運ばれる形に変わるだけである。例外メッセージには U1 SEC-4 の方針を適用する | SEC-30 |
| **D** Denial of Service | **該当なし。** U3 は繰り返しやバッファリングを持ち込まない。`insertValues` / `setValues` はメソッドローカルであり CON-7 の単回使用の外側にも影響しない | — |
| **E** Elevation of Privilege | **該当なし。** U2 SEC-12 と同じく権限モデルを持たない。識別子位置は U5 の担当であり U3 の範囲外 | 継承 |

---

## セキュリティ要件

判定基準は機械的に確認できる形で書く。番号は U5 の SEC-26 に続けて SEC-27 から振る。

### SEC-27. INSERT / UPDATE / DELETE の実行経路に値の文字列連結を残さない

| 項目 | 内容 |
|---|---|
| 要件 | `Dao.create(T)` / `update(T)` / `delete(T)` から `java.sql.PreparedStatement` に至る経路（`Dao` 側・`DaoAdapter` 側の両方）で、呼び出し側から渡された値が SQL テキストに文字列連結されないこと。値は `BindValue` としてのみ運ばれる |
| 対象経路 | `QueryImpl.getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` / `getDeleteAllPreparedSql` → `Dao.executeUpdate(PreparedSql)` または `DaoAdapter.executeUpdate(PreparedSql)`（`delegate` 転送）→ `DBAccessManager.executeUpdate(PreparedSql)` |
| 例外 | (1) `DataType.FUNCTION` と SQL 関数（U1 SEC-1 の例外を継承。自動タイムスタンプ列の `current_timestamp` 等）。(2) `getXxxPreparedSql` を override せず既定の `PreparedSql.ofLiteral(getXxxSQL(...))` フォールバック（BR-14、U2 R-13 の形 1 と同型）に留まるサブクラス。**これは外部実装だけの話ではない**——本リポジトリの `UserDao`（`:32-53`）/ `FileDataDao`（`:17-41`）/ `UserStatDao`（`:24-39`）は旧 `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` のみを override し、新設の `getInsertPreparedSql` 等は override しない。`TestDao` はこれらの旧メソッドも override せず既定の `RuntimeException`（BR-14）のままである。**この経路は 3.6 での移行完了までの暫定的な例外であり、恒久的な適用除外ではない**（R-29 が扱う） |
| 判定基準 | `getInsertPreparedSql` / `getUpdatePreparedSql` / `getDeletePreparedSql` が返す `PreparedSql` のテキストに、呼び出し側が渡した値が現れない。**AC-3b**（INSERT VALUES / UPDATE SET のカラム並び順どおりのバインド値）が mock のバインド値アサートで判定する |
| 由来 | NFR-1、FR-1.4、`business-logic-model.md` § 2、§ 3、§ 4 |

### SEC-28. 値アキュムレータの分離により位置ずれを構造的に防ぐ

| 項目 | 内容 |
|---|---|
| 要件 | `insertValues` / `setValues`（INSERT / UPDATE のカラム値）と `bindFragments` 由来の WHERE 値は、`PreparedSql` 組み立て直前の 1 箇所（`combined = new ArrayList<>(setValues); combined.addAll(bindWhereValues);`）でのみ結合すること。中間状態でのマージ・並び替えを行わない |
| 判定基準 | ソース検査で確認できる。`insertValues` / `setValues` への書き込みは各ビルドメソッド内でのみ発生し、`bindFragments` 由来の値は `buildBindWhere` 経由でのみ収集され、結合は `PreparedSql.of` の呼び出し直前 1 箇所に限られる |
| 由来 | BR-1、BR-7、`business-rules.md` 失敗様式 1、`unit-of-work.md` U3「最大の設計リスク」 |

**なぜ検査ではなく構造か。** `Param.of` / `PreparedSql.of` の個数検査（U1 PS-2）は「`?` の個数 ≠ 値の個数」を捕まえるが、**値の結合順が入れ替わっても個数は変わらない**ため、この検査をすり抜ける。U2 SEC-12 が同期規則を「単一の private メソッド」で構造的に守ったのと同じ発想を、U3 は「結合を 1 箇所に限定する」形で適用する。

### SEC-29. `getBlobIndex()` は独立の走査結果として算出し、手計算のカウンタに頼らない

| 項目 | 内容 |
|---|---|
| 要件 | `blobIndex` は `PreparedSql` を組み立てた**後**に `prepared.getBindIndexOf(DataType.OBJECT, 1)`（U1 の走査ロジック、BI-2）を呼んで得ること。列を数える独立したカウンタで値の位置を計算しない |
| 判定基準 | ソース検査で確認できる。`getInsertPreparedSql` / `getUpdatePreparedSql` のいずれも `blobIndex` への代入が `getBindIndexOf` の戻り値からのみ行われる。**AC-7**（OBJECT ＋ STRING 2 列の UPDATE で `getBlobIndex()` がバインド位置を返す）が判定する |
| 由来 | FR-3.2、FR-1.5、`business-logic-model.md` § 7、BI-2 |

**独立走査を要求する理由。** 列を数える手計算のカウンタは、`p.getValues()` が 0 個になる列（BR-3、SQL 関数値）があると位置がずれる——U3 の functional-design iteration 1 で実際に検出された欠陥である（`business-logic-model.md` の適用記録）。U1 自身のスキャナに委ねることで、この失敗様式を構造的に再発させない。

### SEC-30. BLOB を扱う override 経路の責務を利用者に伝え、値をメッセージに含めない

| 項目 | 内容 |
|---|---|
| 要件 | (a) BLOB を扱うサブクラスに要求される override 責務（`create`/`update` を override し `getBindIndexOf(DataType.OBJECT, 1)` で位置を得て `executeUpdate(PreparedSql, int, InputStream)` を呼ぶこと）を Javadoc と `MIGRATION.md` で文書化する（本ステージ Q2 = B）。(b) U3 が新たに例外を投げうる経路（`Param.of` / `PreparedSql.of` の個数検査、`checkBindable`）のメッセージに `BindValue` の内容を含めない（U1 SEC-4 の継承） |
| 判定基準 | (a) `Dao.getInsertPreparedSql(T)` 等 protected 拡張点と `executeUpdate(PreparedSql,int,InputStream)` の Javadoc、および `MIGRATION.md` の BLOB override セクションが存在する。(b) ソース検査で U3 が組み立てる例外メッセージに `getValues()` 由来の文字列が現れない |
| 由来 | 本ステージ Q2 = B、FR-1.5、FR-3.2、U1 SEC-4 の継承、`security-guide.md` OWASP #9 |

**この要件が閉じるもの。** BLOB override の責務は U1・U2 の新経路と異なり**呼び出し側の能動的な対応を要求する**——既定実装への切り替えだけでは達成されない。現行も呼び出し元が 0 件であるため（`business-logic-model.md` iteration 2 の確認）、Javadoc という到達手段だけでは新規に BLOB を扱おうとする実装者に情報が届かない可能性があり、`MIGRATION.md`（U2 が新設）に実装例を残すことで到達率を補う。

**`BlobUtils` の位置づけ**（`unit-of-work.md` U3 の所有コンポーネント M-8）。現行の BLOB 経路は `Dao.java:270` の `BlobUtils.executeUpdate(PreparedStatement, int, InputStream)` を通るが、新経路（§ 5）は `ps.setBinaryStream` を直接呼び `BlobUtils` を経由しない。`BlobUtils` の既存 public メソッドは互換のためそのまま残し、新規の第三者依存は追加しない（SEC-32）。

### SEC-31. FR-6.2 のリテラル版修正はバインド版の設計に影響しない

| 項目 | 内容 |
|---|---|
| 要件 | リテラル版 `getUpdateSQL` の `:268`（OBJECT 分岐）に `.replaceFirst(tableName + ".", "")` を追加してテーブル修飾の不整合を解消すること。バインド版の SET 句は BR-5 により最初からテーブル修飾を作らない構造であるため、この修正はバインド版の実行経路に影響しない |
| 判定基準 | `getUpdateSQL` が生成する SQL のテーブル修飾がカラム間で一貫する（回帰テスト）。バインド版 `getUpdatePreparedSql` の SET 句にテーブル修飾が現れないことも合わせて確認する。**スキーマ付きテーブル（`MappingUtils.getColumnName` がスキーマ修飾名を返す設定）では `.replaceFirst(tableName + ".", "")` がスキーマ部分を落としきれず、リテラル版とバインド版で出力が食い違う**（`business-logic-model.md` `## Review` 未解消 N-10 の申し送り）。この差分自体は新経路の実行には影響しないが、3.6 の回帰テスト観点に含めること |
| 由来 | FR-6.2、BR-5、BR-12 |

**U3 が触れる唯一のリテラル版コード変更である。** この修正自体は STRIDE の T（構文解釈）カテゴリには当たらない——値の連結ではなく既存の文字列操作の不整合の是正であるため、Must 要件として本文書に記録するが、脅威モデルの新規項目としては扱わない。

### SEC-32. 新規の第三者 compile 依存を追加しない（U1 SEC-8、U2 SEC-17 の継承）

| 項目 | 内容 |
|---|---|
| 要件 | U3 は compile スコープの第三者依存を 1 つも追加しない。`tamacat-core` と `javax.json` のみという現状を維持する |
| 判定基準 | `mvn dependency:tree` の compile スコープが**U3 の変更に起因しては**変更前と一致する（`tech-stack-decisions.md` TSD-21 と同じ粒度——他 Unit 由来の test スコープ追加はこの判定基準の対象外） |
| 由来 | CON-6、U1 SEC-8、U2 SEC-17 |

U3 が新設するメンバはすべて JDK 標準 API（`java.util` / `java.io` / `java.sql`）と U1・U2 が新設した型（`Param` / `PreparedSql` / `BindValue` / `BindSqlBuilder`）だけで書ける。`MIGRATION.md` への追記は Markdown ファイルであり依存ではない。

---

## 残存リスク

番号は U5 の R-23 に続けて R-24 から振る（U5 のレビュー iteration 2 の是正で `identifier-safety` の残存リスク表が R-19〜**R-23** まで伸びているため、U3 は R-24 起点になる）。

| ID | 残存リスク | 由来 | 扱い |
|---|---|---|---|
| **R-24** | BLOB の `InputStream` 取得方法・override の実装自体は呼び出し側（BLOB を扱うサブクラス）の責務のまま残る。U3 が正しく `executeUpdate(PreparedSql, int, InputStream)` を用意し文書化しても、実装者が誤った override を書けば意味をなさない | BR-11、SEC-30 | **受容。** 現行と同じ責任分担であり、U3 が新たに作るリスクではない。SEC-30 が文書化で到達率を補う |
| **R-25** | `getDeletePreparedSql` の主キー述語ロジック（BR-4）が `getUpdatePreparedSql` と重複している（コードの重複） | `business-rules.md` R-2 | **受容。** U2 の `compose` 重複、U4 の同種判断と同じトレードオフ |
| **R-26** | `buildBindWhere` ヘルパは U2 `getSelectPreparedSql()` の WHERE 描画ループと同じロジックを別の場所に複製したものである | `business-rules.md` R-4、BR-9 | **受容。** U2 の成果物（レビュー済み）を変更せずに再利用する術がない。Build and Test（3.6）で両方に同じテスト観点を当てることで乖離を検出する |
| **R-27** | `Dao.getInsertPreparedSql(T)` **および `DaoAdapter.getInsertPreparedSql(T)`**（BR-17 により独立、`business-logic-model.md` § 5.2）の既定実装はいずれも `PreparedSql.ofLiteral(...)` のままであり、`values` が空のため `getBindIndexOf` は常に `-1` を返す。既定のまま BLOB override を書くと `setBinaryStream(-1, in)` になる（`checkBindable` が先に例外を投げるため診断は可能）。**このリポジトリの実利用サブクラスはすべて `DaoAdapter` を継承する**（`Dao` の直接継承者は方言専用の `MySQLDao` / `OracleDao` のみ）ため、実害が起きるのは主に `DaoAdapter` 側である | U3 functional-design レビュー N-7（非ブロッキング、未対応のまま申し送り） | **後段に送る。** 実装フェーズ（Code Generation 3.5）で BLOB override 実装時に、`Dao` 側だけでなく `DaoAdapter.getInsertPreparedSql(T)` 等もバインド版を返すよう override する必要があることを明記する |
| **R-28** | 失敗様式 2（BLOB の位置ずれ）の検出方法として `business-rules.md` が挙げる「`getBlobIndex()` と `getBindIndexOf` の突き合わせ」は、是正後は `blobIndex` が `getBindIndexOf` の戻り値そのものであるため恒真であり何も検出しない | U3 functional-design レビュー N-3（非ブロッキング、未対応のまま申し送り） | **後段に送る。** Build and Test（3.6）で「列の並びから手で導いた絶対位置」との突き合わせに書き換える必要がある |
| **R-29** | 既定フォールバック（BR-14）により、`getXxxPreparedSql` を override しないサブクラスの `create`/`update`/`delete` は**リテラル SQL を実行し続ける**。本リポジトリの実利用サブクラスのうち `UserDao`（`:32-53`）と `FileDataDao`（`:17-41`）、および `UserStatDao`（`:24-39`。いずれも `DaoAdapter` を継承）は旧 `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` のみを override し、新設の `getInsertPreparedSql` 等は override しない。`TestDao` は旧メソッドも override せず既定の `RuntimeException`（BR-14）のままである。したがって**これらのサブクラスを新設メソッドの override へ移行しない限り、`Dao.create` → `PreparedStatement` の実経路では FR-1.4 / NFR-1 は満たされない**——`QueryImpl.getXxxPreparedSql` 自体がバインド化されていることと、呼び出し元の DAO がそれを使うことは別の話である。**この例外は 3.6 での移行完了までの暫定的なものであり、恒久的な適用除外ではない**（SEC-27 例外 (2)） | SEC-27 例外 (2)、`requirements.md:148`（NFR-1 の判定基準は実行経路） | **Build and Test（3.6）に送る。** `UserDao` / `FileDataDao` を `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` の override（`query.getInsertPreparedSql(data)` 等へ委譲、現行の `getInsertSQL` 委譲パターンと同形）へ移行することを 3.6 の必須項目とする。`UserStatDao` は書き込み経路のテストが薄いため移行の要否を 3.6 が判断する（対象外とする場合は理由を明記する）。移行するまでは `UserDaoTest` の INSERT / UPDATE / DELETE アサートはリテラルのまま変化しない |

---

## 要件と上流の対応

| 要件 | 満たす上流要件 | 判定する AC | 検証手段 |
|---|---|---|---|
| SEC-27 | NFR-1、FR-1.4 | **AC-3b** | mock のバインド値アサート |
| SEC-28 | NFR-1（構造的な担保） | **AC-3b**（不変条件 1 を検出する唯一の AC） | ソース検査 ＋ mock の位置アサート |
| SEC-29 | FR-3.2、FR-1.5 | **AC-7** | ソース検査 ＋ mock の位置アサート |
| SEC-30 | FR-1.5、FR-3.2 | — | Javadoc / `MIGRATION.md` の存在確認、ソース検査（例外メッセージ） |
| SEC-31 | FR-6.2 | — | 回帰テスト（テーブル修飾の一貫性） |
| SEC-32 | CON-6 | — | `mvn dependency:tree` の差分 |

**U3 が単独で判定できるのは AC-3b / AC-7 の 2 件**（`unit-of-work-story-map.md`「AC → Unit のマッピング」）。

---

## Build and Test（3.6）に送る検証項目

U1・U2 が送った項目に加えて、U3 から次を送る。

| # | 項目 | 由来 |
|---|---|---|
| 14 | **AC-3b の判定**。INSERT VALUES / UPDATE SET のバインド値がカラム並び順どおりであることを、mock の位置・値アサートで確認する。**不変条件 1（値リスト分離）を検出する唯一の AC** | SEC-28 |
| 15 | **AC-7 の判定**。OBJECT ＋ STRING 2 列の UPDATE で `getBlobIndex()` が期待する絶対位置（列の並びから手で導いた整数）を返すことを確認する。**R-28 により「`getBindIndexOf` との突き合わせ」では検出できない**——列の並びから独立に導いた期待値との比較が必要 | SEC-29、R-28 |
| 16 | **FR-6.2 の回帰テスト**。リテラル版・バインド版の両方でテーブル修飾の不整合が解消されていることを確認する | SEC-31 |
| 17 | **BLOB override の統合テスト**。`DaoAdapter` を継承し `create`/`update` を override した BLOB サブクラスが `getBindIndexOf(DataType.OBJECT, 1)` で正しい位置を得て `executeUpdate(PreparedSql, int, InputStream)` を呼べることを確認する。**R-27 の実害（`DaoAdapter.getInsertPreparedSql(T)` の既定実装が `PreparedSql.ofLiteral(...)` のままだと、それに対する `getBindIndexOf` が `-1` を返す）を避けるため、override 例では `QueryImpl` 由来のバインド版を返す実装にすること** | SEC-30、R-24、R-27 |
| 18 | **`MIGRATION.md` と Javadoc の整合確認**（U2 未解決事項 8 の延長）。BLOB override セクションに対応する Javadoc が存在すること | SEC-30、Q2 = B |
| 19 | **`UserDao` / `FileDataDao` の `getXxxPreparedSql` 移行（前提条件付き）**。既定フォールバック（BR-14）のもとでは、これらのサブクラスが `getInsertPreparedSql(T)` 等を override しない限り `create`/`update`/`delete` はリテラル SQL のまま実行される（R-29）。3.6 でこの override 移行（`query.getInsertPreparedSql(data)` 等への委譲、現行の `getInsertSQL` 委譲と同形）を行って初めて、`UserDaoTest` の INSERT / UPDATE / DELETE 経路のアサート文字列が `?` 入りに変わる（FR-8.2 の対応表の U3 該当行）。`UserStatDao` を移行対象に含めるかは 3.6 が判断する。**移行しない場合、これらのアサートは 1 文字も変わらずグリーンのままである** | R-29、`business-rules.md` |

---

## Review

NOT-READY

**Reviewer:** aidlc-architecture-reviewer-agent（NFR Requirements 3.2 / U3 `write-path`、iteration 1、2026-08-10）

### 検証の方法と範囲

`security-requirements.md` と `tech-stack-decisions.md` の主張を、機械的に確認できる根拠に突き合わせた。突き合わせた対象: 実ソース `QueryImpl.java`（`:184-219`、`:235-274`、`:268`、`:275-280`、`:469`）、`Dao.java`（`:220-234`、`:257-274`）、`DaoAdapter.java`（`:26`、`:152-186`）、`DataType.java:9`、`Query.java:158`、`BlobUtils.java:18`、`pom.xml`（`:17-18`、`:98-102`、`:127-136`、依存宣言）、`src/test/.../UserDao.java`、`FileDataDao.java`、`UserDaoTest.java`、リポジトリ全体の `getBlobIndex` / `setBinaryStream` / `executeUpdate(...InputStream)` 検索。上流契約は `business-logic-model.md`（`## Review` の適用記録と未解消リストを含む）、`business-rules.md`、`requirements.md`、`technology-stack.md`、本ステージの `nfr-requirements-questions.md`。

**確認できた主張（指摘なし）**

- 要件 ID と本文: FR-1.4（`requirements.md:44`）、FR-1.5（`:45`）、FR-3.2（`:71`）、FR-6.2（`:102`）、NFR-1 と「実行経路に値の文字列連結が 0 件」という判定基準の改訂（`:140`、`:148`）、NFR-3〜NFR-6（`:142-145`）、CON-1 / CON-3 / CON-5 / CON-6 / CON-7（`:162-168`）、AC-3b（`:237-244`）、AC-7（`:269-275`）——いずれも引用どおり。
- 実コード引用: `:268` が `.replaceFirst` を欠く OBJECT 分岐であること、`Dao.java:224-234` が `create`/`update`/`delete`、`DaoAdapter.java:26` が `implements AutoCloseable`（`Dao` 非継承）であること、`DataType.OBJECT` の存在——すべて一致。
- 「`getBlobIndex()` / `executeUpdate(String,int,InputStream)` の呼び出し元が 0 件」——正しい。`getBlobIndex` は宣言（`Query.java:158`）と実装（`QueryImpl.java:469`）のみ、`executeUpdate(String,int,InputStream)` は宣言（`Dao.java:266`）と `DaoAdapter.java:184-185` の素通し転送のみで、使用側は製品コード・テストコードのいずれにも無い。
- `pom.xml` 由来の数値: `:17-18` と `:101-102` がいずれも `1.8`、`maven-compiler-plugin` 3.8.1、surefire 2.22.2、compile スコープ依存 3 件（`tamacat-core` / `javax.json-api` / `javax.json`）——すべて一致。TSD-22 の Java 8 機能全数表（try-with-resources、ダイヤモンド、`setBinaryStream` 等）も `business-logic-model.md` § 2〜§ 7 のコード片と照合して漏れ・誤りなし。post-8 機能は 1 つも現れない。
- SEC-28 が引用する結合コード（`combined = new ArrayList<>(setValues); combined.addAll(bindWhereValues);`）は `business-logic-model.md` § 3.1（190-191 行）と逐語一致。SEC-29 の `getBindIndexOf(DataType.OBJECT, 1)` の要求も § 2（134 行）/ § 3.1（193 行）と一致。
- **R-26 / R-27 の申し送り内容は正確。** `business-logic-model.md` `## Review`「未解消の指摘」の N-7（既定 `ofLiteral` のため `getBindIndexOf` が常に `-1`、`checkBindable` が先に例外を投げるため診断可能）と N-3（`getBlobIndex()` と `getBindIndexOf` の突き合わせが是正後は恒真）を、内容・非ブロッキング・未対応という属性ごと正しく引き継いでいる。R-24 / R-25 も `business-rules.md` R-2 / R-4 と一致。
- Q1 = A の論拠（line coverage は失敗様式 1 を検出しない）は `business-rules.md`「失敗様式 1」と整合し、比較表の「U3 の新設クラス 0 個 / 変更 4 クラス」も `business-logic-model.md` § 3〜§ 6 の変更対象と一致する。

---

### ブロッキング

**B-1. 既定フォールバック（BR-14）により、このリポジトリのどの DAO も新経路に載らない。にもかかわらず本文書は「`UserDaoTest` のアサート文字列が `?` 入りに変わる」と書き、この落差を残存リスクに計上していない**

SEC-27 の「例外 (2)」は、既定の `getXxxPreparedSql` フォールバック（`PreparedSql.ofLiteral(getXxxSQL(...))`）を使う場合を **「外部の `Dao` 実装」の話として**除外している。しかし実ソースでは、それが**本リポジトリの全 DAO の既定の姿**である。

- `src/test/java/org/tamacat/dao/test/UserDao.java:32-52` — `UserDao extends DaoAdapter<User>` は `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` のみを override し、いずれも `query.getInsertSQL(data)`（リテラル版）を返す。`getInsertPreparedSql` は override しない。
- `FileDataDao.java:18-40` も同形。`business-logic-model.md` § 5.2（321 行）が確認するとおり、実利用サブクラスはすべて `DaoAdapter` 継承である。
- したがって `DaoAdapter.create(data)` → `delegate.executeUpdate(getInsertPreparedSql(data))` → 既定実装 `PreparedSql.ofLiteral(getInsertSQL(data))`（§ 5.2、334-342 行）となり、**値がリテラル連結された SQL がそのまま実行される**。

帰結が 2 つある。

1. **検証計画が事実と矛盾する。** 本文書「Build and Test に送る検証項目」の **# 19**（「`UserDaoTest` の INSERT / UPDATE / DELETE 経路のアサート文字列が `?` 入りに変わる分」）と `tech-stack-decisions.md` TSD-23 の同趣旨の記述は、この設計のもとでは成立しない。実際のアサートは `UserDaoTest.java:41-44` が `"INSERT INTO users (...) VALUES ('admin','password',null,null,null)"`、`:85-88` が `"UPDATE users SET password='password' WHERE users.user_id='admin'"`、`:97-100` が `"DELETE FROM users WHERE users.user_id='admin'"` であり、`UserDao` が `getInsertPreparedSql` を override しない限り**1 文字も変わらずグリーンのまま**である。3.6 は存在しない差分を探すことになる。
2. **残存リスクの欠落。** R-23〜R-27 のどれも「既存サブクラスは `getXxxPreparedSql` を override するまで旧リテラル経路のままである」ことを記録していない。SEC-27 の判定基準は `QueryImpl.getXxxPreparedSql` の戻り値だけを見る形になっているため、この文書のどの要件も NFR-1 / FR-1.4 が **`Dao.create` から `PreparedStatement` までの実経路**で満たされることを判定しない——NFR-1 の判定基準（`requirements.md:148`）が「`Dao.search` / `create` / `update` / `delete` から `java.sql.PreparedStatement` に至る経路」と明示しているにもかかわらずである。

必要な是正は (i) SEC-27 の例外 (2) を「外部実装」ではなく「`getXxxPreparedSql` を override していないすべてのサブクラス（本リポジトリの `UserDao` / `FileDataDao` / `UserStatDao` / `TestDao` を含む）」と正しく記述すること、(ii) 対応する残存リスク（R-28 相当）を新設し、扱い（受容／3.6 で該当 DAO を移行／後続 Unit）を明示すること、(iii) # 19 と TSD-23 の記述を (ii) の扱いと整合させること、の 3 点。

**B-2. Q2 = B が指定する文書（`MIGRATION.md` / Javadoc）の内容仕様が、override 先のクラスを取り違えている**

R-26 と `tech-stack-decisions.md` TSD-24「注意点」行は、`-1` の危険を **`Dao.getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)`** の既定実装の問題として書く。しかし `business-logic-model.md` § 5.2（358 行）は「**BLOB を扱うサブクラスの override 先も `DaoAdapter` 側になる**」と明記し、BR-17（`business-rules.md:38`）も `DaoAdapter` が `Dao` を継承しないため 5 メンバを独立に追加すると定める。`DaoAdapter.getInsertPreparedSql(T)` の既定も `PreparedSql.ofLiteral(getInsertSQL(data))`（§ 5.2、334-342 行）であり、同じ `-1` の危険を持つ。

本文書内でも食い違っている——「Build and Test に送る検証項目」# 17 は正しく「**`DaoAdapter` を継承し** `create`/`update` を override した BLOB サブクラス」と書くのに対し、R-26 と TSD-24 は `Dao` しか名指ししない。Q2 = B の成果物（`MIGRATION.md` の BLOB override セクション）は**呼び出し元 0 件の機能に対する唯一の到達手段**（SEC-30 の「この要件が閉じるもの」が自ら述べている）であるから、そこで名指しするクラスが実際の override 先と違えば、読者は自分のクラスに存在しないメソッドを override しようとして詰まる。FR-1.5 / FR-3.2 の利用者側での実現可能性が閉じない。

必要な是正: R-26、TSD-24「注意点」行、および TSD-24「BLOB を扱うサブクラスが必要な変更」行を、`Dao` 側と `DaoAdapter` 側の**両方**（およびこのリポジトリの実利用サブクラスは後者であること）を明示する形に改める。

---

### 非ブロッキング

- **N-1（`business-logic-model.md` 未解消 N-10 が引き継がれていない）**: FD レビュー N-10（スキーマ付きテーブルでは `.replaceFirst(tableName + ".", "")` が `MappingUtils.getColumnName` のスキーマ付き名から修飾を落としきれず、リテラル版とバインド版で出力が食い違う）は「3.6 の回帰テスト観点に申し送り済み（未対応の追記作業のみ残る）」とされている。その追記先は FR-6.2 を担う SEC-31 と検証項目 # 16 のはずだが、どちらも「テーブル修飾がカラム間で一貫する」までしか書いておらずスキーマ付きの差分に触れていない。SEC-31 の判定基準に 1 行加えるだけで閉じる。
- **N-2（M-8 `BlobUtils` が両文書とも未言及）**: FD レビュー N-8 のとおり `unit-of-work.md` U3 の所有コンポーネントに「M-8 `BlobUtils` の位置づけ変更」が含まれ、これも**未対応**のまま残っている。実コードでは旧 BLOB 経路が `Dao.java:270` の `BlobUtils.executeUpdate(stmt, index, in)` を通り、`business-logic-model.md` § 5 の新経路はこれを迂回して `ps.setBinaryStream` を直に呼ぶ。SEC-32 が「依存を増やさない」ことは書く一方、U3 が所有する既存ユーティリティの去就がどの成果物にも無い。SEC-30 か残存リスクに 1 行（「`BlobUtils` は互換のため残し、新経路は経由しない」）が要る。
- **N-3（TSD-21 の判定基準がリポジトリ全体を見る形になっている）**: 「`mvn dependency:tree` の出力が変更前と一致する」は U3 の変更に閉じた判定ではない。`business-logic-model.md` § 10（443 行）が「Derby の導入可否は OQ-6」として test スコープ依存の追加余地を残しているため、他 Unit が test 依存を 1 件加えるだけで U3 の判定基準が偽になる。「U3 の変更に起因する差分が 0」と書くのが正しい。あわせて、対になる SEC-32 の判定基準は compile スコープのみを見る形であり、TSD-21（test を含む全体）と粒度が揃っていない——U2 のペアと同じ整合性を主張する以上、どちらかに揃えるべき。
- **N-4（TSD-20 の判定基準が現時点では実在しない設定を参照する）**: 「`pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` が 8 件のまま」は U1 の Code Generation が JaCoCo を追加した後にのみ検査できる。現 `pom.xml` に `jacoco` の記述は 1 件も無い（surefire は `:127-136`）。前提として正しいが、判定の前提条件（U1 の実装完了）を明記しないと 3.6 が実行できない検査を持つことになる。
- **N-5（sibling 番号の連続性は本レビューでは検証できなかった）**: 「SEC-27 は U5 の SEC-26 に続ける」「R-23 は U5 の R-22 に続ける」「TSD-20 は U5 の TSD-19 に続ける」「U4 の最後は TSD-15」「`MIGRATION.md` は U2 TSD-9 の新設」「U5 TSD-16 は `IdentifierRules` の例外」——これらはいずれも sibling unit の成果物を読まないと確認できないが、`aidlc-reviewer-scope` フックが `construction/identifier-safety/`・`construction/dialects/`・`construction/select-path/` への Read / Grep をすべて拒否したため**未検証**である（ディスパッチはこれらを閲覧可としていたが、フックの許可リストには載っていない）。算術上の矛盾は無い（U1: SEC-1〜9 / R-1〜7、U2: SEC-10〜17 / R-8〜15 → U4・U5 が SEC-18〜26 / R-16〜22 を占める形は成立する）。次イテレーションで確認するならフックの exempt list への登録が必要。
- **N-6（U2 の TSD 範囲が Q&A と成果物で食い違う）**: `nfr-requirements-questions.md:14` は U2 を「TSD-6〜TSD-9」と書くが、本文書 10 行目・`tech-stack-decisions.md` 10 行目は「TSD-6〜TSD-10」と書き、TSD-23 は「U2 TSD-10」を surefire の由来として引く。どちらかが誤り。N-5 と同じ理由で判定できない。
- **N-7（引用の忠実性、軽微）**: `tech-stack-decisions.md` TSD-23 の surefire 引用 `<includes>**/*Test.java</include>` は開始・終了タグが対応していない。実際は `pom.xml:132-134` の `<includes><include>**/*Test.java</include></includes>`。
- **N-8（出典の列挙漏れ、軽微）**: SEC-29 は `BI-2`、SEC-28 は `WV-3` 相当の不変条件を由来に挙げるが、これらは `domain-entities.md` の記号であり、両文書の「上流成果物との関係」に `domain-entities.md` が列挙されていない。

---

### 再提出時に判定する点

B-1・B-2 はいずれも「既定フォールバック（BR-14）が誰に適用されるか」という同じ事実の帰結である。必要なのは (i) SEC-27 例外 (2) の対象をリポジトリ内 DAO を含む形に正し、対応する残存リスクを 1 件新設して検証項目 # 19 / TSD-23 をそれと整合させること、(ii) R-26 / TSD-24 の override 先に `DaoAdapter` を加えること、の 2 点。確認済みの主張（要件 ID、実コード行番号、Java 8 全数表、Q1 = A の論拠、R-23〜R-25 / R-27 の申し送り）には手を入れる必要がない。非ブロッキング N-1〜N-4 は同じ改訂の機会に閉じられる。

---

NOT-READY

**Reviewer:** aidlc-architecture-reviewer-agent（NFR Requirements 3.2 / U3 `write-path`、iteration 2、2026-08-10）

### 検証の方法と範囲（iteration 2）

iteration 1 の 2 件の blocking と 8 件の non-blocking に対する是正を、実ソースと sibling 成果物に突き合わせて検算した。今回は dispatch の exempt list が実ファイルパス単位で有効であり、iteration 1 の N-5 で未検証だった sibling の採番を**すべて確認できた**（`select-path` / `dialects` / `identifier-safety` の `nfr-requirements/` 配下の 5 ファイルを直接 Read。ディレクトリ走査は引き続きフックが拒否するため行っていない）。実ソースは `UserDao.java` / `FileDataDao.java` / `UserStatDao.java` / `TestDao.java` を全文、上流契約は `business-logic-model.md` § 5.2、`business-rules.md` BR-14 / BR-17 を再読した。

### iteration 1 の指摘に対する是正の検証

- **B-1 — 是正を確認した。** `UserDao.java:32-53` / `FileDataDao.java:17-41` / `UserStatDao.java:24-39` はいずれも旧 `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` のみを override し、`getXxxPreparedSql` は 1 件も override しない（`src/test/java/org/tamacat/dao/` 全体の grep でも新設メソッド名の出現は 0 件）。SEC-27 例外 (2) の書き換え、R-28 の新設、検証項目 # 19 と TSD-23 の条件付き化はいずれもこの事実と整合する。「移行しない限り `UserDaoTest` のアサートは 1 文字も変わらない」という帰結が両文書で一致した。
- **B-2 — 是正を確認した。** R-26 が `Dao.getInsertPreparedSql(T)` と `DaoAdapter.getInsertPreparedSql(T)` の両方を名指しし、`DaoAdapter` 側が実害の主戦場であると書く形は、`business-logic-model.md` § 5.2（「BLOB を扱うサブクラスの override 先も `DaoAdapter` 側になる」、`DaoAdapter.getInsertPreparedSql` の既定が `PreparedSql.ofLiteral(getInsertSQL(data))`）および BR-17（`DaoAdapter` は `Dao` を継承せず 5 メンバを独立に追加）と一致する。TSD-24 の「BLOB を扱うサブクラスが必要な変更」行・「注意点」行も同じ形に揃い、検証項目 # 17 との食い違いが解消した。
- **N-1 / N-2 / N-4 / N-6 / N-7 / N-8 — いずれも閉じている。** SEC-31 の判定基準にスキーマ修飾の申し送りが入った。SEC-30 に `BlobUtils` の去就（互換のため残す・新経路は経由しない）が入った。TSD-20 に JaCoCo 設定の存在という前提条件が入った。`nfr-requirements-questions.md:14` の U2 範囲は `TSD-6〜TSD-10` に修正され、実物（U2 TSD-6/7/8/9/10）と一致する。TSD-23 の surefire 引用は `pom.xml:132-134` の実物どおりに直った。`domain-entities.md` が本文書の「上流成果物との関係」に加わった（`tech-stack-decisions.md` 側は BI-2 / WV-3 を引用していないため追加不要。実際に追加されていないが問題ない）。
- **N-3 — 部分的に閉じた。** TSD-21 の判定基準は「U3 の変更に起因する差分が 0 件」に narrow され、他 Unit の test 依存追加に左右されなくなった。単独で読んでも意味が通る。ただし SEC-32（compile スコープのみ）と TSD-21（決定は test も含む）の粒度は依然として一致しておらず、TSD-21 の由来欄の「（compile スコープの粒度で揃える）」という但し書きが実態を上書きしていない（下記 非ブロッキング 3）。

### 新たに確認できた事実（sibling 採番、N-5 の決着）

| 主張 | 実物 | 判定 |
|---|---|---|
| U2 = SEC-10〜17、R-8〜15、TSD-6〜10 | SEC-10〜SEC-17、R-8〜R-15（`R-15` が最後）、TSD-6〜TSD-10 | ✅ |
| U4 の最後は TSD-15 | `dialects` TSD-11〜TSD-15、SEC-18〜22 / R-16〜18 | ✅ |
| U5 の最後は TSD-19 → U3 は TSD-20 から | `identifier-safety` TSD-16〜TSD-19 | ✅ |
| U5 の最後は SEC-26 → U3 は SEC-27 から | `identifier-safety` SEC-23〜SEC-26 | ✅ |
| U2 TSD-9 の `MIGRATION.md` 新設と 4 節構成 | 「1. 何が変わったか / 2. 対応表 / 3. SM-1 の対象外に残る経路 / 4. 注意点」 | ✅ |
| **U5 の最後は R-22 → U3 は R-23 から** | **`identifier-safety` の残存リスク表は R-19〜R-23。`R-23`（`Sort.sort` 第 2 引数 `o` は未検証）が実在する** | ❌ 下記 B-3 |

---

### ブロッキング

**B-3. `R-23` が U5 と U3 で衝突している（採番の前提が事実に反する）**

本文書 118 行「番号は U5 の R-22 に続けて R-23 から振る」は偽である。`construction/identifier-safety/nfr-requirements/security-requirements.md:92` に **`R-23`**（`Sort.sort(Object k, Object o)` の第 2 引数が検証されない残存リスク）が実在する——U5 のレビュー iteration 2 の是正で追加されたものであり、同ファイルの本文（`:138`）も「R-19〜R-23 の採番に重複・欠番はなく」と明示している。したがって現状の成果物群には **`R-23` が 2 つ**存在し、U3 の R-23〜R-28 は U5 の R-23 と 1 件重なる。

Build and Test（3.6）は全 Unit の残存リスクを 1 つの集合として受け取り、`R-23` を ID で参照する（本文書の検証項目 # 17 が `R-26` を、# 15 が `R-27` を ID で引くのと同じ形）。衝突した ID は解決先が一意に定まらない。

**必要な是正（exact）**: `security-requirements.md` の残存リスク番号を 1 つずつ繰り下げる。
- 118 行 → 「番号は U5 の **R-23** に続けて **R-24** から振る」
- 残存リスク表（120-127 行）の `R-23`→`R-24`、`R-24`→`R-25`、`R-25`→`R-26`、`R-26`→`R-27`、`R-27`→`R-28`、`R-28`→`R-29`
- 同ファイル内の参照も併せて更新: 40 行（STRIDE 表は SEC のみなので変更不要）、58 行（`R-28`→`R-29`）、92 行（SEC-32 参照のみ、変更不要）、153 行 # 15（`R-27`→`R-28`）、155 行 # 17（`R-26`→`R-27`、`R-23`→`R-24`）、157 行 # 19（`R-28`→`R-29`）、要件と上流の対応表（該当なし）
- `tech-stack-decisions.md` の参照も更新: 123 行 TSD-23（`R-28`→`R-29` を 2 箇所）、141 行 TSD-24（`R-26`→`R-27`）、172 行 未解決事項 14（`R-23 / R-26`→`R-24 / R-27`）、174 行 未解決事項 16（`R-27`→`R-28` を 2 箇所）

（SEC / TSD の採番には衝突がない。繰り下げが必要なのは R 系列のみである。）

**B-4. TSD-20 の判定基準「`<includes>` が 8 件のまま」は、同じ TSD-20 が引用する U5 TSD-16 と矛盾しており、3.6 で必ず偽になる**

`tech-stack-decisions.md:33` の判定基準は「U3 の変更後も `pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` が **8 件のまま**である」と書く。ところが同じ TSD-20 の 45 行は「U5 の TSD-16 は新規クラス `IdentifierRules` を追加する場合のみ拡張した例外」と、U5 がゲート対象を増やすことを正しく認識している。実物（`construction/identifier-safety/nfr-requirements/tech-stack-decisions.md` TSD-16）は `<includes>` に `org.tamacat.sql.IdentifierRules` を**追加する**決定であり、判定基準も「`<includes>` に `IdentifierRules` が含まれる」である。U1 の 8 クラス ＋ `IdentifierRules` で **9 件**になる。

3.6 は単一の `pom.xml` に対して全 Unit の判定基準を検算する。U3 の判定基準を字面どおり実行すると、U3 が何も変更していないにもかかわらず false になる。U2 TSD-6 / U4 TSD-11 も同じ「8 件」を書いているが、あれらは U5 が存在する前に書かれた（U5 のレビューが non-blocking #6 として申し送り、未解消のまま残した）。U3 は U5 の後に書かれ、U5 TSD-16 を明示的に引用しているため、同一セクション内の自己矛盾になっている点が異なる。

**必要な是正（exact）**: `tech-stack-decisions.md:33` の判定基準を、件数ではなく U3 由来の差分で書く。例:

> 判定基準 | **U3 の変更によって `pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` が 1 件も増減しない**（U1 の 8 クラス ＋ U5 TSD-16 が追加する `IdentifierRules` で、U5 適用後の件数は 9 件になる。「8 件」は U1〜U4 単体の判定基準の字面であり、全 Unit 適用後の状態ではない）。**前提**: この判定は U1 の Code Generation（3.5）が JaCoCo 設定を実際に追加した後にのみ検査できる——現時点の `pom.xml` に `jacoco` の記述は 1 件も無い（surefire 設定は `:127-136`）

あわせて 186 行「現行スタックの確認」表の `jacoco-maven-plugin` 行の「`<includes>` は U1 の 8 クラスのまま」も「U3 は増減させない（U5 が `IdentifierRules` を加えるため全体では 9 件）」に揃える。

---

### 非ブロッキング

1. **SEC-27 例外 (2) と R-28 が挙げる 4 DAO のうち `TestDao` は説明と実態が違う。** 両者は 4 つを「いずれも旧 `getInsertSQL` / `getUpdateSQL` / `getDeleteSQL` **のみを** override し」と一括りにするが、`TestDao.java`（`class TestDao extends DaoAdapter<User> implements Collection<Object>`）は SQL 系メソッドを**1 つも** override していない（override するのは `Collection` の実装メソッドのみ）。結論（`getXxxPreparedSql` を override しない＝ ofLiteral フォールバックに留まる）は変わらないが、`TestDao` は既定 `getInsertSQL` が現行どおり `RuntimeException` を投げる別の状態にある（BR-14 の「旧 override 未実装なら現行と同じ RuntimeException」）。列挙から外すか、「`TestDao` は旧メソッドも override していない」と書き分けると正確になる。
2. **R-28 が特定する 4 DAO と、3.6 に課す移行対象 2 DAO が食い違う。** R-28 の「扱い」欄は `UserDao` / `FileDataDao` の移行のみを 3.6 の必須項目とし、`UserStatDao`（`UserStatDao.java:24-39` で 3 メソッドとも override、実在するテストフィクスチャ）の扱いを書いていない。検証項目 # 19 も同じ 2 件しか挙げない。`UserStatDao` を移行対象に含めるのか、含めない理由（例: `UserStatDaoTest` が書き込み経路を通らない）を明示するのか、どちらかを 1 行で書くと 3.6 が判断に迷わない。
3. **SEC-32 と TSD-21 の粒度は、N-3 の是正後も揃っていない。** TSD-21 の決定は「compile も test も追加しない」、判定基準は「U3 由来の差分が 0 件」（全スコープ）である一方、SEC-32 の判定基準は「compile スコープが変更前と一致する」に留まる。TSD-21 の由来欄が「`security-requirements.md` SEC-32（compile スコープの粒度で揃える）」と書くのは、実際には揃っていない状態を「揃えた」と述べている。SEC-32 側の判定基準を「compile / test いずれのスコープにも U3 由来の追加がない」に広げるか、TSD-21 の但し書きを「TSD-21 は SEC-32 より広く test スコープも対象にする」と正直に書くかのいずれか。
4. **引用行番号の軽微なずれ。** SEC-27 例外 (2) と R-28 の `UserDao.java:32-52` は実際には `:32-53`（`getDeleteSQL` の閉じ括弧が `:53`）、`FileDataDao.java:18-40` は実際には `:17-41`（`@Override` が `:17`、閉じ括弧が `:41`）。引用の趣旨は変わらないが、3.6 が行番号で当たりを付ける際に 1 行ずれる。
5. **SEC-27 の「例外」と R-28 の「必須」が同じ事実に逆向きの性格付けをしている。** SEC-27 は「本リポジトリの DAO は要件の**例外**」と書き（つまり満たさなくてよい）、R-28 は「3.6 で移行することを**必須項目**とする」と書く（つまり満たさなければならない）。「この経路は R-28 が扱う」という繋ぎがあるので読み解けるが、SEC-27 例外 (2) の末尾に「3.6 の移行完了までの暫定的な例外である」と 1 語添えると、3.6 が SEC-27 を「恒久的な適用除外」と読む余地が消える。
6. **検証項目 # 17 の「`DaoAdapter.getInsertPreparedSql(T)` の既定実装が `-1` を返す」は言い回しが不正確。** 既定実装が返すのは `PreparedSql.ofLiteral(...)` であり、`-1` を返すのはそれに対する `getBindIndexOf(DataType.OBJECT, 1)` である。R-26 本文は正しく書けているので、# 17 を「既定実装（`ofLiteral`）に対して `getBindIndexOf` が `-1` を返す」に揃えるだけでよい。
7. **U5 の申し送り「`MIGRATION.md`「3. SM-1 の対象外に残る経路」が古くなる」は U3 でも拾われていない。** U2 TSD-9 の 3 節は「識別子位置（R-12、U5 で扱う予定）」と書き、U5 のレビュー非ブロッキング 8 が「U5 出荷後にこの『予定』が古くなる」と指摘して未解消のまま残った。U3 は `MIGRATION.md` に追記する最後の Unit であり、TSD-24 の未解決事項（3.6 へ）にこの申し送りを 1 行加えられる位置にある。U3 の責務ではないため非ブロッキングだが、拾える最後の機会である。

---

### 判定

iteration 1 の blocking 2 件はいずれも実ソースと上流契約に照らして正しく是正されており、非ブロッキング 8 件のうち 7 件も閉じている。今回 sibling を実際に読めたことで、iteration 1 では検証できなかった採番の連続性を確認し、**`R-23` の衝突（B-3）**という新しい事実誤認を検出した。あわせて、N-4 の是正で触れた TSD-20 の判定基準が、同じセクションが引用する U5 TSD-16 と矛盾したまま残っていること（**B-4**）を確認した。いずれも sibling 成果物という機械的に確認できる根拠を持ち、是正は採番の繰り下げと判定基準 1 行の書き換えという純粋に機械的な作業である。設計判断・脅威モデル・要件本文には手を入れる必要がない。

以上より **NOT-READY**。ただし残る 2 件はいずれも文書内の ID と判定基準の整合の問題であり、上記「必要な是正（exact）」に列挙した箇所をそのまま適用すれば閉じる。

---

### 適用記録（オーケストレータ、iteration 2 上限到達後の直接適用）

`reviewer_max_iterations: 2` に到達したため、レビュアーによる再検証を経ずにオーケストレータが以下を適用した。

| # | 対応した指摘 | 適用内容 | 検証根拠 |
|---|---|---|---|
| 1 | B-3（`R-23` の衝突） | `security-requirements.md` の残存リスク番号を全て 1 つ繰り下げ（R-23〜R-28 → R-24〜R-29）。「番号は U5 の R-22 に続けて」を「番号は U5 の R-23 に続けて R-24 から」に訂正。本文内の相互参照（SEC-27 例外 (2)、検証項目 #15/#17/#19）、および `tech-stack-decisions.md` 側の参照（TSD-23、TSD-24、未解決事項 #14/#16）をすべて更新した | `construction/identifier-safety/nfr-requirements/security-requirements.md:92,138`（`R-23` が実在し「R-19〜R-23 に重複・欠番なし」と自己宣言） |
| 2 | B-4（TSD-20 の自己矛盾） | 判定基準を「8 件のまま」から「U3 の変更によって 1 件も増減しない」に書き換え、U5 TSD-16 が `IdentifierRules` を追加するため全 Unit 適用後は 9 件になる旨を明記。「現行スタックの確認」表の該当行も同じ表現に揃えた | `construction/identifier-safety/nfr-requirements/tech-stack-decisions.md` TSD-16（`IdentifierRules` を `<includes>` に追加する決定） |
| 3 | 非ブロッキング 1（`TestDao` の性格づけ誤り） | SEC-27 例外 (2) と R-29（旧 R-28）の説明を、`TestDao` は旧メソッドも override しない（既定の `RuntimeException` のまま）と正確に書き分けた | `TestDao.java` 全文確認（SQL 系メソッドの override なし） |
| 4 | 非ブロッキング 2（`UserStatDao` の移行要否の欠落） | R-29 の「扱い」欄と検証項目 #19 に「`UserStatDao` の移行要否は 3.6 が判断する」旨を追記 | レビュアーの指摘どおり |
| 5 | 非ブロッキング 4（引用行番号のずれ） | `UserDao.java:32-52` → `:32-53`、`FileDataDao.java:18-40` → `:17-41` に訂正（両ファイルの該当箇所すべて） | レビュアーの実測 |
| 6 | 非ブロッキング 5（例外／必須の性格づけの曖昧さ） | SEC-27 例外 (2) と R-29 の両方に「3.6 での移行完了までの暫定的な例外であり、恒久的な適用除外ではない」旨を明記 | レビュアーの指摘どおり |
| 7 | 非ブロッキング 6（検証項目 #17 の言い回し） | 「既定実装が `-1` を返す」→「既定実装（`ofLiteral`）に対して `getBindIndexOf` が `-1` を返す」に訂正 | レビュアーの指摘どおり |
| 8 | 非ブロッキング 3（SEC-32 / TSD-21 の粒度不一致） | SEC-32 の判定基準を「U3 の変更に起因しては」という限定付きに広げ、TSD-21 と同じ「U3 由来の差分」の粒度に揃えた | レビュアーの指摘どおり |

**未解消の指摘（3.6 への申し送り。iteration 2 上限のため、これ以上のレビュアー再検証は行わない）**

- **非ブロッキング 7（`MIGRATION.md`「3. SM-1 の対象外に残る経路」の"予定"表現が古い）** — U2 TSD-9 の 3 節が「識別子位置（R-12、U5 で扱う予定）」と書いたままであることの是正は、U3 の責務範囲外（U2 の成果物）だが拾える最後の機会だった。**未対応** — Build and Test（3.6）に申し送る。

これらはいずれも新経路の正しさ（AC-3b、AC-7、FR-1.4/1.5/3.2/6.2）を損なわないと判断し、Unit の完了をブロックしない。

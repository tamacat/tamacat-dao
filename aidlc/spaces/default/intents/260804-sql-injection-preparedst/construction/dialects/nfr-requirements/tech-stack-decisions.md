# Tech Stack Decisions — U4 `dialects`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U4）— § 1〜§ 9 が使う言語機能と JDBC API。§ 8 の 2.6 契約からの差分（新規メンバなし）。
- **`business-rules.md`**（同上）— BR-1〜BR-21、既知の欠陥 1〜5。
- **`requirements.md`**（requirements-analysis 2.3）— CON-1 / NFR-4（Java 8 の維持）、CON-3（ローカルのみ）、CON-6（compile スコープの第三者依存）、NFR-5 / NFR-6 / NFR-7、FR-6.1 / 6.3。
- **`technology-stack.md`**（codekb）— Maven、compiler 3.8.1 の source/target 1.8、surefire 2.22.2、compile スコープ依存 3 件のみ。
- **U1 `bind-foundation` / U2 `select-path` の `tech-stack-decisions.md`** — TSD-1〜TSD-10。U4 はこれらを継承し、番号を TSD-11 から振る。

`security-requirements.md` は本文書と対になる成果物である。**Q1（JaCoCo ゲートの適用範囲）と Q2（`MIGRATION.md` への追記）はいずれも本文書（`tech-stack-decisions.md`）が TSD-11 / TSD-15 として扱う**——`security-requirements.md` は SEC-18〜SEC-22 / R-16〜R-18 を扱う。

---

## この文書の範囲

**U4 は技術選定を 1 件も行わない。** U1（変えないことの確認 4 件＋唯一の追加 JaCoCo）、U2（変えないことの確認 3 件＋唯一の追加 `MIGRATION.md`）に続き、U4 は**新規の追加を 1 件も持たない**——`MIGRATION.md` への 1 節の追記のみである。

本文書の中身は 2 つである。

1. **U1 / U2 の決定の適用範囲を確定する**（TSD-11）——JaCoCo ゲートを U4 に拡張するかの判断
2. **変えないことの確認**（TSD-12、TSD-13、TSD-14）＋**`MIGRATION.md` への追記**（TSD-15）

---

## TSD-11. カバレッジゲート（U1 TSD-3、U2 TSD-6）を U4 に拡張しない

| 項目 | 内容 |
|---|---|
| 決定 | `jacoco-maven-plugin` の `check` ルールの `<includes>` を**変更しない**。ゲート対象は U1 が新設した 8 クラスのままとする |
| 判定基準 | U4 の変更後も `pom.xml` の JaCoCo `check-new-classes` 実行部の `<includes>` が 8 件のままである |
| 由来 | 本ステージ Q1 = A |

### U2 と同じ理由が U4 にも当てはまる

| 項目 | U1 | U2 | U4 |
|---|---|---|---|
| 新設するクラス | 8 個 | 0 個 | **0 個** |
| 変更するクラス | なし | 5 個（すべて既存レガシー） | **3 個**（`MySQLDao` / `OracleDao` / `OracleSearch`、すべて既存レガシー） |
| ゲートの表現を適用した場合 | 意味を持つ（新規クラスのみ） | 既存の未被覆コードごと測ることになる | **同じ問題。加えて `OracleDao` は現在テストが 1 本もなく、被覆率が 0% から始まる** |

**U4 固有の追加論点**: `OracleDao` に `OracleDaoTest` を新設する（`business-logic-model.md` § 9-2）ため、U4 で初めて被覆率が計測可能になる。しかし `OracleDao` には U4 が触れない既存メソッド（`createSearch()` 等）もあり、クラス単位のゲートである以上、U2 が却下した「既存の未被覆コードごと測る」問題を U4 でも抱える。

**被覆率が U4 の主要な失敗様式を検出しないこと**: U2 の TSD-6 と同じ論法が成立する。U4 の主要な失敗様式（W-3 の窓の取り違え、`for update` の二重化）は**分岐が実行されても誤った SQL が生成されるだけ**であり、行としては通る。規律は被覆率ではなく `security-requirements.md` SEC-19 の亜種テストと Build and Test（3.6）の 4 状態確認（AC-9）に投じる。

### 却下した形

| 選択肢 | 却下理由 |
|---|---|
| `OracleDao` を新規に `<includes>` へ追加（U2 の却下理由と同型） | クラス単位のゲートである以上、U4 が触れない既存メソッドごと測ることになる |
| `MySQLDao` / `OracleSearch` も含めて 3 クラスすべてを追加 | 同上。かつ `MySQLDao` / `OracleSearch` は U4 が触れる範囲がさらに小さい（1〜数メソッド） |

### `org.md` との関係

U1 Q1 で確認済みのとおり、本 scope `sql-parameterization` は `org.md` の 80% floor の対象外である。U4 が拡張しないことも U1 / U2 と同じ判断の継続である。

---

## TSD-12. compile スコープの第三者依存を追加しない

| 項目 | 内容 |
|---|---|
| 決定 | U4 は compile スコープの第三者依存を 1 つも追加しない。test スコープも追加しない |
| 判定基準 | `mvn dependency:tree` の出力が変更前と一致する |
| 由来 | CON-6、U1 TSD-1、U2 TSD-7、`security-requirements.md` SEC-21 |

U4 が変更するコードはすべて JDK 標準 API と U1 / U2 が新設した型（`PreparedSql` / `ResultSetHandler`）だけで書ける。

---

## TSD-13. Java 8 を維持する

| 項目 | 内容 |
|---|---|
| 決定 | `maven.compiler.source` / `target` を `1.8` のまま維持し、U4 のコードは post-8 の言語機能・API を使用しない |
| 判定基準 | `pom.xml:17-18` と `pom.xml:101-102` が `1.8` のまま。`mvn -q compile` が成功する |
| 由来 | CON-1、NFR-4、U1 TSD-2、U2 TSD-8 |

### U4 が使う言語機能・API の全数確認

`business-logic-model.md` § 1〜§ 5 に現れる構成要素を漏れなく列挙する。

| 構成要素 | 使用箇所 | 最低要求 | 判定 |
|---|---|---|---|
| 三項演算子 | § 1 の `offset` 導出 | Java 1.0 | ✅ |
| `String.substring` / `trim` | § 4.3 `wrapRownum` の `core` 剥がし | Java 1.0 | ✅ |
| `String.toLowerCase` / `endsWith` | § 4.3 `forUpdate` 判定（現行から不変） | Java 1.0 | ✅ |
| `StringBuilder` | § 4.3 `wrapRownum`、§ 3.2 の LIMIT 連結 | Java 5 | ✅ |
| `private static` メソッド（`compose` / `wrapRownum`） | § 2.2、§ 4.3 | Java 1.0 | ✅ |
| ラムダ式（`ResultSetHandler<R>` の実装） | § 3.2、§ 4.3 の `executeQuery(sql, rs -> {...})` | **Java 8**（U1 / U2 が既に確立） | ✅ |
| `Collections.emptyList()` | § 3.2 の `PreparedSql.of("SELECT FOUND_ROWS()", ...)` | Java 1.2 | ✅ |
| `switch` を使わない条件分岐（`if` チェーン） | § 1 の 4 状態判定 | — | ✅ |

### 使用しないことを明示する post-8 の機能

U1 TSD-2 / U2 TSD-8 と同じ一覧（`var`、`record`、`List.of` 等）。U4 のアルゴリズムにも現れない。

**`Stream` API は U4 でも使わない。** `wrapRownum` の分岐は状態（W-1〜W-4）ごとに異なる文字列組み立てであり、コレクション変換を伴わない。

---

## TSD-14. テストの構成を変えない

| 項目 | 決定 | 由来 |
|---|---|---|
| テストフレームワーク | JUnit 4 のまま | OOS-6、U1 TSD-5、U2 TSD-10 |
| surefire | 2.22.2 のまま。`<includes>**/*Test.java</include>` も変更しない | U1 TSD-5 |
| `MySQLDaoTest` | **無変更で緑**（BR-2、§ 3.5）。`searchList` を呼ぶテストが現在存在しないため自明に成立する | `business-logic-model.md` § 7 段 2 |
| `OracleDaoTest`（新設） | U4 で新規に作成する。現在 `OracleDao` にはテストが 1 本もない | `business-logic-model.md` § 9-2、NFR-6（純増） |
| `OracleSearchTest` | null ケースを追加する（既存アサートは変更しない） | `business-logic-model.md` § 7 段 1 |
| 検証チャネル | U1 が用意した mock の位置・値アサート（`getLastPreparedStatement().getPreparedSql()`）を使う。U4 は新しい検証チャネルを追加しない | `component-methods.md` C-8（`:387`） |

**U4 が書き換えるテストはない。** `MySQLDaoTest` / `OracleSearchTest` はアサートを**追加**するのみで、既存アサートの変更はない（U2 の `SearchTest` / `QueryImplTest` と同じ「無変更で緑」の扱い）。

---

## TSD-15. `MIGRATION.md`（U2 が新設）に Oracle のページング挙動変更を追記する

| 項目 | 内容 |
|---|---|
| 決定 | U2 `tech-stack-decisions.md` TSD-9 が新設した `MIGRATION.md` の「4. 注意点」節に、Oracle のページング挙動変更（全行スキャン → rownum 方式）を追記する |
| 判定基準 | `MIGRATION.md`「4. 注意点」に、`OracleDao.searchList(Query,int,int)` を使う呼び出し側に向けて、ページング方式が変わり性能特性も変わる旨の記述がある |
| 由来 | 本ステージ Q2 = A |

### 追記する内容

```markdown
## 4. 注意点（追記）

- **Oracle のページングが rownum 方式に切り替わりました。** `OracleDao` はこれまで `searchList(Query, int, int)`
  を独自に override しておらず、呼び出しは親クラス `Dao` の実装（`Dao.searchList`）にそのまま渡っていました。
  `Dao.searchList` は SQL をそのまま DB に投げて実行し、返ってきた `ResultSet` に対して `start` 件目まで
  `rs.next()` で読み飛ばし、`max` 件集めた時点でアプリケーション側の処理を打ち切ります——**DB 側では絞り込まれず、
  クエリ自体は方言固有の `WHERE` / `ORDER BY` の範囲でしか制限されていませんでした。** `OracleDao` 専用に
  用意されていた `searchListForOracle`（rownum によるページングを試みるメソッド）はリポジトリのどこからも
  呼ばれておらず、かつ未バインドの `?` を含む SQL を実行する欠陥を持っていたため、実質的に使えませんでした。
  これからは `searchList` の呼び出しだけで rownum による DB 側の絞り込みが有効になります。public API の
  シグネチャは変わりませんが、大きな結果セットに対する性能特性が変わります（DB 側での絞り込みにより、
  不要な行を読み飛ばすための通信・処理コストが減ります）。
- **MySQL と Oracle でページングの端の挙動が完全には一致しません（この差自体は今回の変更で生じたものではありません）。**
  `start > 0` かつ `max <= 0`（件数を指定しない後方ページング）を渡した場合、MySQL は従来どおりオフセットを
  無視して全件を返します。Oracle は `start` に基づく絞り込みを行いますが、**これは今回新しく Oracle だけに
  加わった制限ではありません**——この状態では従来も汎用経路（`Dao.searchList` の継承実装）がオフセット分の
  行をアプリケーション側で読み飛ばしており、返る行の集合は今回の変更の前後で変わりません（絞り込みの場所が
  アプリケーション側から DB 側に移っただけです）。変わらず存在するのは MySQL と Oracle の違いそのものであり、
  方言をまたいで同じページング呼び出しを使い回すコードは、この違いに注意してください。
```

**FR-7.2 の対象を超える追記であることを明示する。** `MIGRATION.md`「3. SM-1 の対象外に残る経路」はバインド化の対象外経路（FR-7.1 系）を扱うが、本追記は Oracle のページング欠陥是正という**別の性質**の変更である。それでも `unit-of-work.md` が「観測可能な挙動の変更」と明示的に記録した事実を利用者向け文書のどこにも書かないのは片手落ちであるという判断（Q2 の論点）による。**「性能特性が変わる」（第 1 項）と「MySQL/Oracle の非対称」（第 2 項）は性質が異なる**——前者は今回の変更で新たに生じる観測可能な差異、後者は元から存在した差異を今回初めて文書化するものである。

### 却下した形

| 選択肢 | 却下理由 |
|---|---|
| 追記しない（Q2 = B） | `unit-of-work.md` が明示した観測可能な挙動変更が利用者向け文書のどこにも現れなくなる |
| 独立した新規ファイル（例: `CHANGELOG.md`）を作る | リポジトリに文書ファイルを増やす判断は U2 が既に検討し `MIGRATION.md` に一本化した（TSD-9「腐敗リスクへの対処」）。U4 単独でこの判断を覆す理由がない |

---

## 却下した選択肢

| 選択肢 | 却下理由 |
|---|---|
| JaCoCo のゲート対象に `OracleDao` を加える | TSD-11。既存の未被覆コードごと測ることになり、U4 の主要な失敗様式は被覆率で検出できない |
| `wrapRownum` / `compose` を `Dao` の共通 protected メソッドに昇格する | `business-logic-model.md` § 2.3 が既に却下している（AC-11 の判定対象を増やす） |
| MySQL 側にも `offset` 適用の行スキップを追加し W-2 の非対称を解消する | `business-rules.md` BR-2。現行と異なる SQL が生成され NFR-5 に対するリスクを負う |

---

## 未解決事項（Build and Test 3.6 へ）

U1 が送った 6 項目、U2 が送った 6 項目に加えて、U4 から次を送る。

| # | 事項 | 由来 |
|---|---|---|
| 13 | **`OracleDaoTest` の新設内容**。W-1〜W-4 の 4 状態、`for update` 亜種、AC-9 のアサートをどう組織するか | `security-requirements.md` 検証項目 14 / 15 |
| 14 | **`MIGRATION.md` 追記内容とソースの整合確認**。U2 の未解決事項 8（Javadoc との整合）と同じ性質のチェックを、U4 の追記についても行う | TSD-15 |
| 15 | **R-16（MySQL/Oracle の W-2 非対称）が利用側の既存呼び出しパターンに影響するかの確認**。リポジトリ内に方言をまたいでページング呼び出しを共有するコードがないかの確認（現状 `MySQLDao.searchList` を呼ぶテストは存在しないため、実害の兆候はない） | `security-requirements.md` R-16 |

---

## 現行スタックの確認（変更なし）

| 項目 | 状態 |
|---|---|
| ビルドツール | Apache Maven、`jar` パッケージング。変更なし |
| `maven-compiler-plugin` 3.8.1（source / target 1.8） | 変更なし（TSD-13） |
| `maven-surefire-plugin` 2.22.2 | 変更なし（TSD-14） |
| `jacoco-maven-plugin`（U1 が追加） | **設定を変更しない**（TSD-11）。`<includes>` は U1 の 8 クラスのまま |
| compile スコープ依存 3 件 | 変更なし（TSD-12） |
| test スコープ依存 | 変更なし（TSD-12、TSD-14） |
| CodeQL ワークフローのブランチ設定 | **U4 では変更しない。** OQ-7 として 3.6 が判断する |
| リポジトリルートの構成 | **`MIGRATION.md` に 1 節を追記する**（TSD-15）。新規ファイルは作らない |

---

## Review

<!-- reviewer が記入する -->

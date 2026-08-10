# Security Requirements — U1 `bind-foundation`

## 上流成果物との関係

- **`business-logic-model.md`**（functional-design 3.1, U1）— § 4 `BindSqlBuilder` の値分類、§ 5 プレースホルダ計数、§ 6 `PreparedStatementBinder`、§ 7 `DBAccessManager` の実行・検査・記録、§ 8 mock 拡張、§ 10 の 2.6 契約からの差分（A-8〜A-12 が production jar の public 表面に載る）。本文書の脅威モデルはこの 5 面を対象にする。
- **`business-rules.md`**（同上）— BR-2 / BR-3（型検証）、BR-9（`DataType.FUNCTION` の raw 経路）、BR-18（`ValueConvertFilter` の非適用）、BR-20（例外の funnel）、BR-27〜BR-30（実行記録）、BR-33〜BR-37（mock）。本文書はこれらのうちセキュリティに効くものを要件として固定する。
- **`requirements.md`**（requirements-analysis 2.3）— NFR-1（実行経路に値の文字列連結が 0 件）、NFR-2（CodeQL の指摘 0 件）、NFR-3（後方互換）、NFR-7（性能目標なし）、FR-2.1 / FR-2.2、FR-4.1 / FR-4.2、FR-7.1（SM-1 対象外の raw 経路）、CON-6、CON-8、OQ-4（本ステージで解決）、OQ-7（3.6 へ）。
- **`technology-stack.md`**（codekb）— カバレッジ計測ツールの不在、CodeQL が `master` のみを対象とすること、compile スコープ依存が `tamacat-core` と `javax.json` だけであること、mock スタックが `src/main` にあること。

`tech-stack-decisions.md` は本文書と対になる成果物で、Q1（JaCoCo の追加）を扱う。

---

## この文書の範囲

**U1 は `kind: library` であり、デプロイ可能なプロセスを持たない**（`unit-of-work.md`「デプロイモデル」、`services.md`）。したがって次の領域は**該当なし**である。

| 一般的な項目 | U1 での状態 |
|---|---|
| 認証（AuthN） | 該当なし。ライブラリは認証面を持たない。JDBC 接続の資格情報は利用側の `JdbcConfig` が保持し、U1 は触れない |
| 認可（AuthZ） | 該当なし。権限モデルを持たない |
| 保存時・転送時の暗号化 | 該当なし。TLS も鍵管理も JDBC ドライバと利用側の設定に属する |
| 規制フレームワーク（PCI-DSS / HIPAA / SOC 2 / GDPR） | **直接には該当なし。** tamacat-dao は汎用データアクセスライブラリであり、データ主体も保持データも持たない。ただしバインド値は利用側アプリケーションのデータであり、それが規制対象になりうる。この帰結は SEC-5 と R-4 で扱う |
| データ所在地・越境移転 | 該当なし |

**U1 が実際に持つセキュリティ面は 2 つだけである。**

1. **値が SQL 構文として解釈される経路**（本取り組みの目的そのもの）
2. **バインド値がプロセス内に平文で保持・露出される経路**（U1 が新たに作る面）

---

## 脅威モデル（STRIDE）

`threat-modelling-stride.md` の 6 カテゴリを U1 の 2 面に当てる。**該当しないカテゴリはその旨を書く**——空欄にすると「見落とし」と「非該当」の区別が付かないためである。

| カテゴリ | U1 での評価 | 対応 |
|---|---|---|
| **S** Spoofing | **該当なし。** ライブラリは同一性を主張する主体を持たない | — |
| **T** Tampering | **中心的関心。** SQL インジェクションは「呼び出し側の値が SQL の構文として解釈される」タンパリングである。U1 の `BindSqlBuilder`（`?` 生成）と `PreparedStatementBinder`（JDBC への適用）がこれを構造的に解消する | SEC-1、SEC-2、SEC-3 |
| **R** Repudiation | **該当なし（ただし誤解を招く面がある）。** `getExecutedQuery()` / `getExecutedStatements()` は監査ログ**ではない**——`ThreadLocal` に保持されるプロセス内の一時記録であり、永続化も改竄防止もない | SEC-9 |
| **I** Information Disclosure | **U1 が新たに作る主要な面。** バインド値の平文保持（`ExecutedStatement`）、mock の記録、例外メッセージ | SEC-4、SEC-5、SEC-6、SEC-7 |
| **D** Denial of Service | **限定的。** 実行記録の非有界成長、`InputStream` の保持、`isNumeric` の正規表現バックトラック | SEC-7、R-5、R-6 |
| **E** Elevation of Privilege | **該当なし。** 権限モデルを持たない。識別子位置（FR-1.7）は「値から構文への昇格」に相当するが、U5 `identifier-safety` の担当であり U1 の範囲外 | R-3 |

---

## セキュリティ要件

判定基準は**機械的に確認できる形**で書く。`nfr-requirements-guide.md`「Security Anti-Requirements」に従い、「安全であること」のような測れない表現は使わない。

### SEC-1. 新経路に値の文字列連結を残さない

| 項目 | 内容 |
|---|---|
| 要件 | `BindSqlBuilder` が生成するテキストに、呼び出し側から渡された値の文字列が現れないこと。値は `BindValue` としてのみ運ばれる |
| 例外 | `DataType.FUNCTION`（BR-9）と SQL 関数（BR-7、`current_timestamp`）は値ではなくテキストとして出る。いずれも FR-7.1 / FR-2.4 が現行維持を定めたもの |
| 判定基準 | `BindSqlBuilder` の `tokenFor` が返すテキストトークンが `"?"` 以外になるのは、上記 2 例外のときだけである。ソース検査で確認できる |
| 由来 | NFR-1、FR-1、`business-logic-model.md` § 4.1 |

### SEC-2. NUMERIC / FLOAT の型検証を維持する

| 項目 | 内容 |
|---|---|
| 要件 | NUMERIC / FLOAT カラムに空でない非数値が渡された場合、`InvalidParameterException("value is not numeric.")` で拒否すること |
| 判定基準 | **AC-5** — NUMERIC カラムに `"';select * from dual --'"` を渡すと `InvalidParameterException` が投げられ、SQL は実行されない |
| 由来 | FR-2.1、BR-2、BR-3 |

この検証は**バインド化しても不要にならない**。バインドすれば構文注入は防げるが、型不整合による実行時エラーは残る。かつ `User_test.java:14` の手動プローブ（FR-8.4）が期待する挙動がこれである。

### SEC-3. バインド経路でクォートエスケープを適用しない

| 項目 | 内容 |
|---|---|
| 要件 | `BindSqlBuilder` は `ValueConvertFilter` を保持せず、呼ばないこと。クォートのエスケープは JDBC ドライバの責務に移る |
| 判定基準 | **AC-6** — シングルクォートを含む値をバインド経路で渡したとき、バインド値が呼び出し側の文字列と一致する（`''` への二重化が起きない） |
| 由来 | FR-2.2、BR-18 |

二重エスケープは値の破壊であり、正しさの問題であると同時に「エスケープに頼る安全性」から「バインドによる安全性」への責務移動が完了したことの確認でもある。

### SEC-4. 例外メッセージにバインド値を含めない

| 項目 | 内容 |
|---|---|
| 要件 | `DaoException` / `InvalidParameterException` のメッセージに `BindValue` の内容を含めないこと。**SQL テキストは含めてよい** |
| 判定基準 | `business-logic-model.md` § 7.2 の `checkBindable` が組み立てるメッセージに `sql.getValues()` 由来の文字列が現れない。ソース検査で確認できる |
| 由来 | 本ステージ（`security-guide.md` OWASP #9「Do NOT log sensitive data」、STRIDE-I） |

**SQL テキストを含めてよい理由**: `checkBindable` が発火するのは `ofLiteral` 由来の `PreparedSql`（旧 override 経路）に限られ、そのテキストは**旧 API がすでに返しているもの**である。同じ文字列は `getExecutedQuery()`（`DBAccessManager.java:181-188`）にも `DaoEvent`（`Dao.java:241-243`）にも流れており、`LoggingDaoExecuterHandler` がログに出しうる。したがって例外メッセージに含めても**新しい露出面を作らない**。一方でバインド値は旧 API のどこにも現れないため、メッセージに載せると新規の露出になる。

### SEC-5. 実行記録のバインド値はマスクしない（受容された露出）

| 項目 | 内容 |
|---|---|
| 要件 | `ExecutedStatement` は渡された値を**マスクせず**保持する。既定オフのスイッチもマスク手段も設けない。**`DataType.OBJECT` は例外**——SEC-7 により標識のみを記録する（値そのものは保持しない） |
| 判定基準 | **AC-8** — 実行直後の `getExecutedQuery()` に SQL テキストが、`getExecutedStatements()` にバインド値が記録されている |
| 由来 | FR-4.2、BR-29（**本ステージで `OBJECT` について限定**。下記「FR-4.1 の範囲限定」および「3.1 の規則に対する修正」参照） |

**SEC-5 と SEC-7 の関係**: SEC-5 は「記録する値を秘匿しない」という方針、SEC-7 は「`OBJECT` については値そのものを記録できない」という技術的制約である。両者は矛盾しない——`OBJECT` を標識化するのは機微性を理由としたマスクではなく、ストリームを読めば実行が壊れるという不可避の制約による。STRING / BOOLEAN / NUMERIC / FLOAT / DATE / TIME の値は一切秘匿しない。

**これは受容された露出である。** `requirements.md` FR-4.2 の判断根拠は「現行もリテラルが埋め込まれた SQL を記録しており、値は既にこのリストに平文で含まれているため露出面は増えない」ことである。`services.md` R-5 が記録するとおり、この記録は `ThreadLocal` のリストとして利用側プロセスのメモリ内に平文で存在する。

**利用側への義務**: バインド値が規制対象データ（PII / PHI / カード番号等）を含みうるアプリケーションは、(1) ヒープダンプの取り扱い、(2) `getExecutedStatements()` を読む自前コードの露出、の 2 点を自らの脅威モデルで扱う必要がある。ライブラリ側はこれを制御しない。

### SEC-6. mock の記録はスレッドに閉じる

| 項目 | 内容 |
|---|---|
| 要件 | `MockConnection` が保持する `MockPreparedStatement` の記録リストを `ThreadLocal` にすること |
| 判定基準 | 別スレッドから `MockConnection.getPreparedStatements()` を呼んだとき、そのスレッドが実行した文だけが見える |
| 由来 | 本ステージ Q2 = D |

**この要件が閉じるもの**: 変更前の `MockPreparedStatement` は `setXxx` がすべて空実装で**何も記録していなかった**（`MockPreparedStatement.java:47-317`）。U1 は記録機能と `MockDriver.getInstance()` を追加するため、`MockConnection` がインスタンス単位で全スレッド共有である以上、**スレッドを跨いで全記録を読める**という U1 固有の広がりが生じる。`ThreadLocal` 化はこれを消し、mock の露出を `DBAccessManager` の記録（既に `ThreadLocal`）と同じ水準に揃える。

**閉じないもの**: `org.tamacat.mock.sql` が production jar に同梱される事実と、`MockDriver.acceptsURL` が URL を問わず `true` を返す事実（R-7）。いずれも OOS-6 / 現行の性質であり U1 の範囲外である。

### SEC-7. 実行記録は `InputStream` を保持しない

| 項目 | 内容 |
|---|---|
| 要件 | `ExecutedStatement` は `DataType.OBJECT` の値について `InputStream` を保持しないこと。「この位置にバイナリ値がバインドされた」ことを示す標識だけを記録する |
| 判定基準 | `ExecutedStatement` の保持するどのフィールドからも `InputStream` に到達できない。ソース検査で確認できる |
| 由来 | 本ステージ Q3 = A |

**技術的必然であることを明示する。** `InputStream` の**内容**を記録するには読む必要があり、読めばストリームは消費されて後続の `setBinaryStream` が空を書く——**記録がバインドを壊す**。したがって選択肢は「参照を保持し続ける」か「標識だけを記録する」の 2 つしかない。前者は `ThreadLocal` のリストが `shutdown()` まで削除されない（BR-30）ため、ストリームとそれが包むファイルハンドルをスレッド寿命まで握り続ける資源保持の欠陥になる。

### SEC-8. 新規の第三者 compile 依存を追加しない

| 項目 | 内容 |
|---|---|
| 要件 | U1 は compile スコープの第三者依存を 1 つも追加しない。`tamacat-core` と `javax.json` のみという現状を維持する |
| 判定基準 | `mvn dependency:tree` の compile スコープが変更前と一致する |
| 由来 | CON-6、`technology-stack.md` |

供給網リスク（`security-guide.md` OWASP #6）の観点から、セキュリティ改修そのものが新たな依存面を増やさないことを要件として固定する。Q1 = C が追加する JaCoCo は **Maven ビルドプラグイン**であり compile / test いずれのスコープにも入らず jar にも入らないため、この要件に抵触しない（根拠は `tech-stack-decisions.md`）。

### SEC-9. 実行記録を監査ログとして位置づけない

| 項目 | 内容 |
|---|---|
| 要件 | `getExecutedQuery()` / `getExecutedStatements()` が監査要件（非否認性）を満たすものではないことを Javadoc に明示すること |
| 判定基準 | 両メソッドの Javadoc に、`ThreadLocal` のプロセス内一時記録であり永続化・改竄防止・時刻付与を行わない旨の記述がある |
| 由来 | 本ステージ（STRIDE-R） |

`regulatory-frameworks.md`「Audit Trail Requirements」が求める性質（不変ストレージ、保持期間、アクセス制御）を 1 つも満たさない。バインド値を記録するようになったことで「監査ログが手に入った」と誤解される余地が生じるため、明示的に否定する。

---

## 残存リスク

**受容するもの**と**後段に送るもの**を分けて記録する。いずれも隠さない。

| ID | 残存リスク | 由来 | 扱い |
|---|---|---|---|
| **R-1** | `DataType.FUNCTION` の値はバインドされずテキストにそのまま出る。呼び出し側の文字列が SQL 構文として生きたまま入る | BR-9、FR-7.1 が SM-1 対象外と明示 | **受容。** 渡されるのは開発者が書いた SQL であるという 2.3 Q5 = A の判断。FR-7.2 が文書化を求める |
| **R-2** | `ofLiteral` 互換シム経路（旧 `getInsertSQL(T)` 等を override したままのサブクラス）はリテラル埋め込みのまま実行される | ADR-004 の Negative | **受容。** NFR-3（再コンパイル不要）との引き換え。移行していないサブクラスは安全にならないことを利用側に明示する |
| **R-3** | 識別子位置（テーブル名・カラム名・ORDER BY）の注入 | FR-1.7、ADR-010 | **U5 `identifier-safety` に送る。** 機構は 3.1 が U5 の中で決める。NFR-1 の対象範囲は FR-1.7 を含まない |
| **R-4** | バインド値が `ThreadLocal` に平文で保持される | FR-4.2、SEC-5 | **受容。** 現行の `getExecutedQuery()` が同じデータをリテラル埋め込みで保持しているため露出面は増えない |
| **R-5** | 実行記録が非有界に成長する（`release()` では削除されず `shutdown()` まで積み上がる） | BR-30、Q3 = A で上限を設けない選択 | **受容。** 現行 `getExecutedQuery()` と成長特性を揃える。上限の導入は NFR-7 / OOS-2 が排した最適化に当たる |
| **R-6** | `isNumeric` の正規表現 `^\-?[0-9]*\.?[0-9]+$` は、数字が続いた末尾に非数字が来る入力に対して **O(n²) のバックトラック**を起こす。入力は利用側アプリケーションが渡すカラム値であり、攻撃者の影響下にありうる | 現行 `SQLParser.java:145-150`。BR-3 が逐語的な維持を定めている | **後段に送る（Build and Test 3.6）。** U1 は FR-2.1 の「現行の挙動を維持する」に従い正規表現を変更しない。長さガードを入れると非常に長い正当な数値の判定結果が変わるため、挙動変更として別途判断を要する。**U1 が持ち込んだものではなく、U1 が温存したものである** |
| **R-7** | `org.tamacat.mock.sql` が production jar に同梱され、`MockDriver.acceptsURL` が URL を問わず `true` を返す。本番設定に `MockDriver` が残ると `DriverManager` が mock を返しうる | `MockDriver.java:37-39`、ADR-009 の Negative、OOS-6 | **受容（U1 で軽減）。** mock の `src/test` 移動は OOS-6 がスコープ外とした。SEC-6（`ThreadLocal` 化）により、この事故が起きた場合の mock 側の記録の露出は **`getExecutedStatements()` と同水準**に留まる（下記の比較表を参照）。`META-INF/services/java.sql.Driver` が存在しないため `ServiceLoader` による自動登録は起こらない（本ステージで確認） |

**R-7 の比較対象を正確にする。** バインド経路では `getExecutedQuery()` は `?` 入りの SQL テキストのみを保持し、**バインド値を一切含まない**（BR-28、`business-logic-model.md` § 7.3 の `record(...)`）。したがって mock の記録を `getExecutedQuery()` と比べるのは誤りである。値の平文を保持する同格の記録は `getExecutedStatements()` であり、これも `ThreadLocal` である。

| 記録 | 保持する内容 | スコープ | R-7 の事故時に値の平文を持つか |
|---|---|---|---|
| `getExecutedQuery()`（バインド経路） | `?` 入りの SQL テキストのみ | `ThreadLocal` | **持たない** |
| `getExecutedQuery()`（`ofLiteral` 経路） | リテラル埋め込みの SQL テキスト | `ThreadLocal` | 持つ（現行と同じ） |
| `getExecutedStatements()` | SQL テキスト ＋ バインド値 | `ThreadLocal` | **持つ** |
| `MockConnection` の記録（SEC-6 適用後） | SQL テキスト ＋ 適用された位置と値 | `ThreadLocal` | **持つ** |

**訂正後も結論は変わらない**——SEC-6 により mock の記録は、同じ Unit が導入する `getExecutedStatements()` と同じスレッドスコープに収まる。U1 が唯一新しく広げていた「スレッドを跨いで読める」という性質だけが消える。消えないのは「値の平文がプロセス内に存在する」ことであり、それは R-4 として別途受容している。

---

## FR-4.1 の範囲限定

**FR-4.1 は `DataType.OBJECT` について字義どおりには満たせない。** 本ステージがこれを明示的に限定する。

| 項目 | 内容 |
|---|---|
| FR-4.1 の原文 | 「`DBAccessManager.getExecutedQuery()` は、実行された SQL テキストに加えて、その実行に渡されたバインド値も記録すること」 |
| 限定 | `DataType.OBJECT` の値については、**バイナリの内容を記録しない**。「この位置に `OBJECT` 型の値がバインドされた」ことを示す標識のみを記録する |
| 根拠 | 下記 3 案の検討結果。いずれも `OBJECT` の内容を安全に記録できない |
| AC への影響 | **なし。** AC-8 のシナリオは「値を持つ条件で SELECT が実行された直後」であり、SELECT の WHERE 述語に `OBJECT` は現れない |
| 他の型への影響 | なし。STRING / BOOLEAN / NUMERIC / FLOAT / DATE / TIME の値は従来どおり完全に記録される |

### 検討した 3 案

| 案 | 内容 | 却下理由 |
|---|---|---|
| 1. ストリームを読んで内容を記録する | 記録時に `InputStream` を読み切る | **実行が壊れる。** ストリームは消費され、後続の `setBinaryStream` が空を書く。`mark` / `reset` は `InputStream` の一般契約では保証されない（`markSupported()` が `false` を返す実装が多く、`FileInputStream` もその 1 つ） |
| 2. バッファリングする | 全読み込み→ `byte[]` 化→新しい `ByteArrayInputStream` をバインドに渡し、`byte[]` を記録に保持する | **却下。** (a) BLOB 全体をヒープに載せる——大きなバイナリでは `OutOfMemoryError` の直接原因になる。(b) その `byte[]` を `ThreadLocal` の記録に保持するため、参照保持案より**メモリ影響が大きい**（元のストリームは lazy かもしれないが `byte[]` は確実に常駐する）。(c) `BindValue.ofStream(in)` が保持するストリームと、実際にバインドされるストリームが**別インスタンスになる**。`domain-entities.md` C-3 の注記が定める「`BindValue` 自身は不変（フィールドは final、生成後に差し替わらない）」という性質と、BR-19（ストリーム値は 1 回しかバインドできない）の前提が変わる。(d) NFR-7 / OOS-2 が排した性質の変更でもある |
| 3. 標識のみを記録する（**採用**） | 「この位置に `OBJECT` 型の値がバインドされた」ことだけを記録する | 実行を壊さず、資源を保持せず、ヒープを消費しない。失われるのは BLOB の中身のみで、位置と型は保たれる |

案 1 と案 2 を退けた結果、残る選択肢は「参照だけを保持する」（SEC-7 が排した資源保持の欠陥）か案 3 のみである。

---

## 3.1 の規則に対する修正

本ステージの決定により、Functional Design（3.1）の記述 2 箇所が古くなる。**3.1 の成果物は編集しない**——functional-design / bind-foundation はレビュアーの READY 受領が有効であり、`produces[]` 成果物への書き込みはその受領を無効化するためである。修正内容をここに記録し、本文書を後段の参照先とする。

| # | 3.1 の記述 | 修正後 | 由来 |
|---|---|---|---|
| 1 | `business-rules.md` BR-36「`MockConnection` は全テストで共有されるため、記録はテスト間で蓄積する」 | 記録リストは `ThreadLocal` であり、蓄積は**スレッド単位**になる。`clearPreparedStatements()` の必要性は変わらない——surefire は fork ごとに単一スレッドでテストを走らせるため、同一スレッド上のテスト間では依然蓄積する。`getPreparedStatements()` / `getLastPreparedStatement()` / `clearPreparedStatements()` はいずれも呼び出しスレッドの記録に作用する | Q2 = D、SEC-6 |
| 2 | `business-logic-model.md` § 7.3 の `record(...)` が `new ExecutedStatement(sql.getSql(), sql.getValues())` として `BindValue` をそのまま保持する。`domain-entities.md` C-7 の属性 `values: List<BindValue>` | `ExecutedStatement` は `DataType.OBJECT` の `BindValue` について `InputStream` を保持しない形に置き換えて保持する（標識化）。他の型の `BindValue` はそのまま保持してよい | Q3 = A、SEC-7 |
| 3 | `business-rules.md` BR-29「`ExecutedStatement` は渡された値を**そのまま**保持する。既定オフのスイッチもマスク手段も設けない」 | 「マスク手段は設けない」は維持する。ただし `DataType.OBJECT` については値そのものを保持せず標識化する。これは機微性を理由としたマスクではなく技術的制約による（本文書「FR-4.1 の範囲限定」の 3 案検討） | Q3 = A、SEC-5 / SEC-7 |
| 4 | `domain-entities.md` C-7 の不変条件 **ES-2**「値のマスク・秘匿・**省略**を行わない（FR-4.2）」 | 「マスク・秘匿を行わない」は維持する。`DataType.OBJECT` の内容についてのみ**省略する**。ES-2 の無条件の文言をこの範囲で限定する | Q3 = A、SEC-7 |

**影響を受けない 3.1 の規則**: BR-34（同一位置への 2 回目の set は後勝ち）は `MockPreparedStatement` 単位の規則であり、`MockConnection` の記録リストの生存範囲とは独立である。BR-30（`getExecutedStatements()` のライフサイクルを `getExecutedQuery()` に揃える）も、上限を設けない選択（Q3 = A）により維持される。BR-28（`getExecutedQuery()` は SQL テキストのみを保持する）も不変であり、むしろ R-7 の比較表がこの規則に依拠している。

---

## 要件と上流の対応

| 要件 | 満たす上流要件 | 判定する AC | 検証手段 |
|---|---|---|---|
| SEC-1 | NFR-1、FR-1 | AC-1 / AC-3 / AC-4（判定は U2 完了時） | ソース検査 ＋ mock のバインド値アサート |
| SEC-2 | FR-2.1 | **AC-5** | 単体テスト（U1 で判定可能） |
| SEC-3 | FR-2.2 | **AC-6** | 単体テスト（U1 で判定可能） |
| SEC-4 | 本ステージで新規 | — | ソース検査 |
| SEC-5 | FR-4.1、FR-4.2 | **AC-8** | 単体テスト（U1 で判定可能） |
| SEC-6 | 本ステージで新規（Q2 = D） | — | 別スレッドからの `getPreparedStatements()` |
| SEC-7 | 本ステージで新規（Q3 = A） | — | ソース検査 |
| SEC-8 | CON-6 | — | `mvn dependency:tree` の差分 |
| SEC-9 | 本ステージで新規 | — | Javadoc の存在確認 |

**U1 が単独で判定できるのは AC-5 / AC-6 / AC-8 の 3 件**（`unit-of-work-story-map.md`「AC → Unit のマッピング」）。他は後続 Unit の完了時に判定する。

---

## Build and Test（3.6）に送る検証項目

| # | 項目 | 由来 |
|---|---|---|
| 1 | NFR-2 / SM-3 の測定方法。CodeQL は `master` への push / PR と週次 cron でのみ動作し、`v2.0` ブランチでは走らない | **OQ-7**（`requirements.md`）、CON-8 |
| 2 | 残存するリテラル連結経路（`SQLParser` のリテラル系）を CodeQL が指摘した場合の扱い。実行に到達しないことを示すか、指摘を抑制するか | ADR-003 の Negative |
| 3 | JaCoCo が JDK 25 Corretto 上で動作すること。版が古いとエージェントが落ちうる | Q1 = C、`tech-stack-decisions.md` |
| 4 | `sqlTypeOf`（`setNull` の SQL 型）は `MockPreparedStatement.setNull` が空実装のためモックで検証できない。実 DB での確認 | BR-17、ADR-007 の Negative、**OQ-6** |
| 5 | R-6（`isNumeric` の O(n²) バックトラック）の扱い。長さガードは挙動変更を伴うため判断を要する | 本ステージ |
| 6 | SEC-6 の検証——別スレッドから `MockConnection.getPreparedStatements()` を呼んで空であること | Q2 = D |

---

## Review

READY

### iteration 2 — 検証の方法と範囲

iteration 1 の blocking 指摘 F-1（R-7 が `getExecutedQuery()` を誤って比較対象にしていた）と non-blocking 3 件（TSD-2 の `withInitial` 誤帰属、「3.1 の規則に対する修正」表の抜け、バッファリング案の未検討）への修正を検証した。`src/main/java` の実ソース（`DBAccessManager.java`、`MockDriver.java`、`MockConnection.java`）、同一 Unit の 3.1 成果物（`business-logic-model.md`、`business-rules.md`、`domain-entities.md`）、上流の `requirements.md`、対になる `tech-stack-decisions.md`、`nfr-requirements-questions.md` を突き合わせた。修正が触れていない箇所（`isNumeric` の O(n²) 分析、JaCoCo 設定、OQ-4 の解決、sensors）は軽く再確認するに留めた。

### F-1 の解消状況

**実体として解消されている。体裁だけの修正ではない。**

- R-7 の該当文は「`getExecutedStatements()` と同水準」に訂正され、直後に比較表（`getExecutedQuery()` バインド経路 / `ofLiteral` 経路 / `getExecutedStatements()` / `MockConnection` の記録）が追加された。4 行を実コード・3.1 成果物と照合した——`DBAccessManager.java`（現行）の `executedQuery` は `List<String>` で SQL テキストのみを保持し、設計上追加される `executedStatements`（`business-logic-model.md` § 7.3）は `getExecutedQuery().add(sql.getSql())` と `getExecutedStatements().add(new ExecutedStatement(sql.getSql(), sql.getValues()))` を分けて呼ぶため、バインド経路の `getExecutedQuery()` に値が混入する余地はない。表の「持たない／持つ」の判定はいずれも `business-rules.md` BR-28（`getExecutedQuery()` は SQL テキストのみ、型 `List<String>`）と一致する。`ofLiteral` 経路の行（リテラル埋め込みの SQL テキストを保持＝値の平文を持つ）も、`ofLiteral` が旧 `SQLParser` 経路（値をテキストに埋め込む）から生成される SQL であるという `domain-entities.md` C-2「由来」節の記述と整合する。
- 訂正後の結論（「D により mock の記録が `getExecutedStatements()` と同じスレッドスコープに収まる」「値の平文がプロセス内に存在することは消えず R-4 として受容」）は、`getExecutedStatements()` 自体が本 Unit の新規 `ThreadLocal`（BR-30、§ 7.3）であり、`MockConnection`（SEC-6 適用後）も同じく `ThreadLocal` 化されるため、両者のスレッドスコープが実際に一致する。論理的に成立する。
- `nfr-requirements-questions.md` Q2 補足の訂正段落は、本ファイル R-7 の主張と同一の結論・同一の根拠（BR-28）を述べており、食い違いはない。

### Non-blocking 所見 1〜3 の適用状況

1. **TSD-2 の `withInitial` 訂正——正しい。** `tech-stack-decisions.md` は使用箇所を「`MockConnection` の記録リスト（SEC-6 のコード片）のみ」に限定し、`executedStatements`（§ 7.3）を別行で「`new ThreadLocal<>()` ＋ 手動の遅延初期化」とした。実ソース `DBAccessManager.java:35` の `executedQuery` フィールドは `new ThreadLocal<>()`（`withInitial` ではない）であり、設計が意図的にこれと同形を選んでいるという記述（「既存 `executedQuery` と同形にするため `withInitial` を使わない」）とも一致する。`nfr-requirements-questions.md` Q2=D のコード片は実際に `ThreadLocal.withInitial(ArrayList::new)` を使っており、2 行への分割は事実と一致する。

2. **「3.1 の規則に対する修正」表への行 3（BR-29）・行 4（ES-2）追加——正しい引用。** `business-rules.md` BR-29 の原文「`ExecutedStatement` は渡された値をそのまま保持する。既定オフのスイッチもマスク手段も設けない」と、`domain-entities.md` ES-2 の原文「値のマスク・秘匿・省略を行わない（FR-4.2）」を、追加された行 3・行 4 の引用と逐語で突き合わせたところ、いずれも一致した。あわせて SEC-5 の要件文に `OBJECT` 例外が明記され、SEC-5 と SEC-7 の関係を説明する段落が追加されている。
   **独立再カウント**: 3.1 の 3 成果物（`business-logic-model.md`、`business-rules.md`、`domain-entities.md`）を通読し、Q1/Q2/Q3 の決定で文言上古くなる箇所を数え直したが、修正表の行 1〜4（BR-36、§ 7.3 の `record(...)` コード片 / C-7 属性表、BR-29、ES-2）以外に古くなる記述は見つからなかった。`domain-entities.md` C-7「ライフサイクル」節（`release()` は削除しない、`shutdown()` が `remove()` する）は Q3 = A の影響を受けず、「影響を受けない 3.1 の規則」注記（BR-34、BR-30、BR-28 は不変）とも一致する。

3. **バッファリング案の「検討した 3 案」表への追加——大筋で正しいが、却下理由の 1 点に誤った引用がある。** 案 2（バッファリング）の却下理由 (a)（ヒープ常駐、`OutOfMemoryError`）と (b)（`byte[]` を `ThreadLocal` に保持するため参照保持案よりメモリ影響が大きい）は妥当である。しかし理由 (c)「バインドされる `InputStream` が呼び出し側の渡したものと別インスタンスになり、`BindValue` の不変契約（**BV-4**）と BR-19 の前提が変わる」は、`domain-entities.md` の **BV-4**（「`isNull()` が真なら `value` も `stream` も null」）と照合すると誤りである。BV-4 は NULL 値の構造的制約であり、案 2 が扱う対象（`isNull()` が偽の OBJECT ストリーム値）には適用されない。「同じ `BindValue` インスタンスの `stream` フィールドが生成後に差し替わらない」という一般的な不変性は C-3 の「`InputStream` に関する注記」の地の文（「`BindValue` 自身は不変〔フィールドは final、生成後に差し替わらない〕」）に書かれているが、これは番号付きの不変条件として存在せず、`BV-4` という番号を当てるのは F-1 と同種の「誤った参照で主張を裏付ける」誤りである。

   ただし影響は限定的である。案 2 の却下は (a) と (b) だけで十分に成立しており（ヒープ常駐と大きなメモリ影響は独立に有効な理由）、(c) を除いても結論（標識化を採用する）は変わらない。BR-19（ストリーム値は 1 回しかバインドできない）の引用は、bind 対象のストリームが元のものと入れ替わるという記述自体は妥当な懸念であり誤りとは言えない。したがって blocking にはしない——**次回修正時に (c) の `BV-4` を削除するか、正しい参照（C-3「`InputStream` に関する注記」の地の文、または一般的な不変性の記述）に差し替えることを推奨する。**

### 新たな欠陥の有無

追加された比較表・3 案表・修正表の行 3/4、SEC-5/SEC-7 の関係段落を本文の他の主張（SEC-6、SEC-7、R-4、R-5、FR-4.1 の範囲限定、AC-8）と突き合わせたが、矛盾は見つからなかった。上記の BV-4 誤引用（non-blocking）以外に新たな欠陥は見つからなかった。

### 軽く再確認した既存の主張（iteration 1 で反証できなかったもの）

- `DBAccessManager.java` の `executedQuery` フィールド（`:35`）、`getExecutedQuery()`（`:181-188`）、`release()`（`:167-179`、削除しない）、`shutdown()`（`:206-215`、`remove()` する）を再読し、iteration 1 の確認結果と一致することを確認した。
- `MockDriver.java` の static 初期化子（`:21-27`）、`acceptsURL`（`:37-39`）、`connect`（`:41-43`）を再読し、引用行番号・内容とも iteration 1 の確認結果と一致することを確認した。
- `isNumeric` の O(n²) バックトラック分析（R-6）、JaCoCo 設定（`tech-stack-decisions.md` TSD-3）、OQ-4 の解決（Q1=C）は本イテレーションで変更されておらず、iteration 1 の検証結果を維持する。

以上により、blocking な欠陥は見つからなかった。BV-4 の誤引用（non-blocking）を除き、F-1 と non-blocking 3 件はいずれも実体として解消されている。READY と判定する。

---

**適用記録（オーケストレータ、2026-08-09）**: iteration 2 の non-blocking 指摘（「検討した 3 案」表の案 2 の却下理由 (c) が `BV-4` を誤って引用している）を、レビュアーが示した選択肢のとおりに訂正した。`BV-4`（「`isNull()` が真なら `value` も `stream` も null」）は NULL 値に関する構造的制約であり、非 null のストリームを扱うバッファリング案の論点ではない。引用を `domain-entities.md` C-3 の注記（「`BindValue` 自身は不変——フィールドは final、生成後に差し替わらない」）に差し替えた。**この訂正はレビュアーの再検証を受けていない**が、変更は誤った ID を正しい根拠に置き換えたのみで、却下理由 (a) / (b) が独立して案 2 を退けるという判断にも、他のどの要件・規則にも影響しない。

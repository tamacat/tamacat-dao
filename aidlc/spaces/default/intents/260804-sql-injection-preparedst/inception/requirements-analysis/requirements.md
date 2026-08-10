# Requirements — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

本要件書は次の成果物を起点とし、いずれも変更せずに引き継ぐ。

- **`intent-statement.md`**（intent-capture, 1.1）— 問題定義、成功指標 SM-1〜SM-3、プロダクト境界（tamacat-dao ライブラリ内の SQL 生成／実行経路のみ）、public API の後方互換性維持の方針、Java 8 維持・ローカルのみ／git push なしの作業制約、および 2 件の未検証前提。
- **`scope-document.md`**（scope-definition, 1.4）— 最小価値スコープ、能力 C-1〜C-9 とその優先度、スコープ外 O-1〜O-3、walking-skeleton-first の順序方針。
- **`business-overview.md`**（reverse-engineering, 2.1 / space-level codekb）— 本ライブラリが「アプリケーション開発者が SQL を手書きせずにメタデータからクエリを組み立てる」ためのものであること、利用形態が `DaoAdapter<T>` のサブクラス化であること、および観測された制約（Java 8、JDBC ドライバは利用側が供給、ORM フレームワーク非依存）。
- **`architecture.md`**（同上）— 中心的設計事実「SQL は文字列連結で組み立てられ `java.sql.Statement` で実行される」、`SQLParser` の 2 プリミティブ、唯一のバインド経路（BLOB / `DataType.OBJECT`）、raw-SQL エスケープハッチの一覧、方言アーキテクチャと実行面（Execution Surface）。
- **`code-structure.md`**（同上）— SQL を組み立てるファイル群と実行するファイル群の分類、テストの 3 形態、および反復パターン（テンプレート置換、`StringBuilder` 累積、regex による文字列手術）。
- **`code-quality-assessment.md`**（同上、参考）— 既存テストの密度と配置、`MockPreparedStatement` が何も記録しないという検証上の制約、技術的負債レジスタ。
- **`team-practices`**（practices-discovery, 2.2）— 本ワークフローのスコープで **SKIP** のため存在しない。したがって way of working / testing posture は `aidlc/spaces/default/memory/org.md` の既定に従う。

本ステージが確定するのは「何が満たされるべきか」である。どのクラスをどう変更するかという設計判断は Application Design（2.6）以降に委ねる。回答の出典は `requirements-analysis-questions.md` の Q1〜Q13 を指す。

---

## Intent Analysis

達成したいのは、**tamacat-dao を組み込むアプリケーションが、ライブラリ層で SQL インジェクションのリスクを解消できる状態**である（`intent-statement.md` の Target Customer / Problem Statement）。「PreparedStatement を使う」ことは手段であって目的ではない。目的は、呼び出し側が渡す値が SQL の構文として解釈される余地をなくすことにある。

この目的に照らすと、本取り組みの本質は次の 3 点に整理される。

1. **値と構文の分離。** 現行では `SQLParser.parseValue` が値を SQL リテラルとして描画し、`QueryImpl` がそれを文字列テンプレートに埋め込み、`DBAccessManager` が完成した文字列を `Statement` に渡す（`architecture.md` の Execution Surface）。この一本道の各段で「値はもう文字列である」という前提が成立しているため、変更は 1 箇所の置換では済まない。値を最後まで値として運ぶ経路を通す必要がある。
2. **エスケープからバインドへの責務移動。** 現行の安全性は `ValueConvertFilter` によるクォートエスケープに依存しており、方言ごとに 3 実装が存在して null の扱いすら一致していない（`architecture.md`）。バインド変数はこの責務を JDBC ドライバに移す。移した後もエスケープを残すと二重エスケープになるため、どの経路でどちらが働くかを明示的に決める必要がある。
3. **検証手段の再構築。** 現行テストは生成 SQL 文字列を密にアサートしており、それが唯一の検証チャネルである。値が SQL 文字列から外れる以上、この検証チャネルは機能しなくなる。かつ `MockPreparedStatement` は何も記録しないため、代替チャネルは存在しない（`code-quality-assessment.md`）。検証手段の再構築は副作用ではなく、本取り組みの構成要素である。

制約側では、**public API の後方互換性維持**（`intent-statement.md`）が最も強く効く。現行の `Query` インターフェースは SQL を `String` で返す設計であり、`String` は値を運べない。この一点で互換性と SM-1 が正面から衝突する。本ステージではこの衝突の存在を要件として記録し、解き方の選択は Application Design（2.6）に委ねる（Q1 = 未決）。

---

## Functional Requirements

優先度は MoSCoW（`scope-document.md` の配分を継承）。「出典」列は `requirements-analysis-questions.md` の設問番号、`scope-document.md` の能力番号、`intent-statement.md` の成功指標番号を指す。

### FR-1. 値のパラメータ化

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-1.1 | 単一値の比較条件（`=`, `<`, `>`, `<=`, `>=`, `<>` 等）に渡された値は、SQL テキストに埋め込まれずバインド変数として渡されること | Must | C-1, SM-1 |
| FR-1.2 | LIKE 条件（前方一致・部分一致・後方一致）に渡された値は、バインド変数として渡されること | Must | C-2, SM-1 |
| FR-1.3 | IN 句および複数値条件（`BETWEEN` を含む）に渡された各値は、それぞれ独立したバインド変数として渡されること | Must | C-3, SM-1 |
| FR-1.4 | INSERT の VALUES 句および UPDATE の SET 句に渡された値は、バインド変数として渡されること | Must | C-4, SM-1 |
| FR-1.5 | BLOB / バイナリ値（`DataType.OBJECT`）は、現行同様バインド変数として渡されること。他の値もバインドされることにより、そのパラメータ位置は現行と変わりうる | Must | C-5, Q2 |
| FR-1.6 | サブクエリ経路（`andIn` / `andNotIn` / `andExists` / `andNotExists`）で親クエリに合成される子クエリの値は、リテラル化されずバインド変数として親の実行時に渡されること | Must | Q11 |
| FR-1.7 | バインドできない位置（テーブル名・カラム名・ORDER BY 句）に呼び出し側から渡された文字列が、SQL の構文として解釈されうる形でそのまま連結されないこと。判定は次のいずれかが成立することとする: (a) 当該引数が例外で拒否される、(b) 宣言済みのメタデータ（`Table` / `Column`）に照合され、一致しないものが拒否される | Should | C-9 |

**FR-1.7 の判定基準と未確定範囲。** 上記 (a) / (b) のどちらを採るか、あるいは別の機構（識別子の許可文字集合による検証等）を採るかは本ステージでは決めていない。`scope-document.md` は C-9 を Should Have として暫定配置したのみで、機構はどの上流成果物でも確定していない。要件として固定したのは「呼び出し側の文字列が識別子位置で構文として生きたまま SQL に入る経路が残らないこと」という観測可能な結果のみである。機構の選択は OQ-9 として Application Design（2.6）に委ねる。Should Have であるため、2.6 の判断次第では本取り組みで実施しないこともありうる。

**FR-1.6 の補足。** `QueryImpl.andIn` 等（`QueryImpl.java:389-406`）は子 `Query` の `getSelectSQL()` が返す完成済み SQL テキストを親の WHERE に連結する。子の WHERE 条件は子の `Search` 経由で組み立てられるため、そこには実行時の値が含まれる。したがってこの経路は FR-7 が対象外とする raw 経路とは性質が異なり、SM-1 の判定対象に含める（Q11 = B により Q5 = A をこの範囲で上書き）。

### FR-2. 値の検証とエスケープの挙動

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-2.1 | NUMERIC / FLOAT カラムに非数値が渡された場合、現行同様 `InvalidParameterException` で拒否されること（型検証を維持する） | Must | Q4 |
| FR-2.2 | `ValueConvertFilter` によるクォートエスケープは、バインド変数で値を渡す経路では適用されないこと（二重エスケープを生じさせない）。FR-7 が対象外とする raw 経路が残る場合、そこでのみ適用されること | Must | Q4 |
| FR-2.3 | LIKE 値に含まれる `%` / `_` のエスケープと `escape 'X'` 句の付与は、現行の挙動を維持すること（LIKE のワイルドカード意味論はバインドでは解決しないため） | Must | Q4 |
| FR-2.4 | DATE / TIME カラムの現行挙動——空文字および `"NULL"` を `null` として扱い、リテラル `current_timestamp` をバインドせず SQL 関数として発行する——が維持されること | Must | `architecture.md`（本ステージでの新規導出） |

**FR-2.4 の出自。** この要件は Q1〜Q13 のいずれの設問でもユーザーに提示していない。根拠は `architecture.md` の `parseValue` の DataType 表（DATE / TIME の行）のみであり、本ステージがコードの事実から導出した新規要件である。`current_timestamp` は値ではなく SQL 関数として発行されるため、バインド変数化の対象にすると意味が変わる。承認ゲートではこの点を明示的に確認すること。

**FR-2.3 の補足。** `SQLParser.parseLikeStringValue`（`SQLParser.java:119-139`）には、値が `$ # ~ ! ^` の 5 候補すべてを含む場合に `%` / `_` がエスケープされずアクティブなワイルドカードとして出力される経路がある。この経路にテストは存在しない（`code-quality-assessment.md`）。「現行の挙動を維持する」がこの欠陥の維持を意味しないことを明示するため、FR-8.5 を置く。

### FR-3. public API の互換性

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-3.1 | public API の後方互換性が維持されること（破壊的変更を行わない） | Must | `intent-statement.md` |
| FR-3.2 | `Query.getBlobIndex()` は public のまま残り、BLOB カラムの**バインドパラメータ位置**を返すこと | Must | Q2 |
| FR-3.3 | `Query.getSelectSQL()` / `getInsertSQL()` / `getUpdateSQL()` / `getDeleteSQL()` の戻り値の中身をどうするかは、本ステージでは確定しない。Application Design（2.6）で FR-3.1 と SM-1 を両立させる案を比較して決定すること | Must | Q1 |

**FR-3.2 の補足。** `getBlobIndex()` の契約——1 始まりの JDBC パラメータ位置なのか、OBJECT カラムの個数なのか——はリポジトリ内では確定していない。呼び出し元もテストも存在しない（`architecture.md` / `code-quality-assessment.md` の Stated Unknowns）。Q2 = A の選択により、本取り組み以降は「BLOB カラムのバインドパラメータ位置」と定義する。リポジトリ内に依存箇所がないため、この定義がリポジトリ内の何かを壊すことはない。リポジトリ外の tamacat プロジェクトについては未確認である（OQ-5 参照）。

### FR-4. 実行済み SQL の記録

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-4.1 | `DBAccessManager.getExecutedQuery()` は、実行された SQL テキストに加えて、その実行に渡されたバインド値も記録すること | Must | Q3 |
| FR-4.2 | FR-4.1 の記録に対し、機微データの扱いに関する追加条件（既定オフ、マスク手段の提供等）は設けないこと | Must | Q13 |

**FR-4.2 の根拠。** 現行もリテラルが埋め込まれた SQL を記録しており、値は既にこのリストに平文で含まれている。したがってバインド値を記録しても露出面は増えない、という判断による（Q13 = A）。

### FR-5. DB 方言

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-5.1 | 既存の 3 経路——MySQL（`MySQLDao` / `MySQLSearch`）、Oracle（`OracleDao` / `OracleSearch`）、汎用フォールバック（`Dao` / `Search`）——のすべてで FR-1 が成立すること | Must | C-6, Q9 |
| FR-5.2 | PostgreSQL は汎用フォールバック経路として扱うこと。PostgreSQL 専用の `Dao` / `Search` を追加しないこと | Must | Q9 |
| FR-5.3 | 方言ごとの `Conditions` 実装（`Condition`、`MySQLCondition`、`PostgreSQLCondition`）が定義する演算子と値テンプレートが、パラメータ化後も現行と同じ述語を生成すること | Must | C-6 |

**FR-5.1 の補足。** `OracleDao` は `createSearch` を override するが `createQuery` を override しないため、WHERE 述語には Oracle 用フィルタが、INSERT / UPDATE のリテラルには既定フィルタが使われるという非対称が現行に存在する（`architecture.md` の Dialect Architecture）。FR-2.2 によりバインド経路ではクォートエスケープが適用されなくなるため、この非対称は当該経路では解消される。raw 経路が残る範囲での扱いは Application Design（2.6）で確認すること。

### FR-6. 変更経路上の既存欠陥の是正

`scope-document.md` の O-1〜O-3 はいずれもこれらを扱っていない。Q10 および Q12 により本取り組みに追加された要件である。

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-6.1 | `OracleDao.searchListForOracle`（`OracleDao.java:30-73`）を修正して有効化すること。未バインドの `?` を含む SQL を `Statement` で実行する現行の状態を解消し、rownum によるページングが動作すること | Must | Q10-A, Q12 |
| FR-6.2 | `QueryImpl.java:268` の OBJECT ブランチでテーブル修飾が残る不整合（BLOB カラムだけ `file.data=?` のように修飾され、SET 句の他カラムと形が揃わない）を解消すること | Must | Q10-B |
| FR-6.3 | `MySQLDao.java:46` の LIMIT 句における `int` の直接連結を解消すること | Should | Q10-C |
| FR-6.4 | `OracleSearch.OracleValueConvertFilter`（`OracleSearch.java:11-15`）に null ガードを入れ、他 2 実装（`Search.DefaultValueConvertFilter`、`MySQLSearch.MySQLValueConvertFilter`）と null 時の挙動を揃えること | Must | Q10-D |

**FR-6.1 の補足。** 当該メソッドは `Dao.searchList` の override ではなく（名前が異なる）、リポジトリ内のどこからも呼ばれておらず、テストもない。有効化すると Oracle のページング挙動が汎用経路の全行スキャン（`Dao.java:181-184`）から rownum 方式に変わる。public API の破壊ではないが、観測可能な挙動の変更である。また現行実装では `max` が rownum の構成に使われていない（`start` のみがラッピングを制御する）ため、修正はこの点も含む。

**FR-6.3 の位置づけ。** LIMIT の値は値位置ではあるが、実行時のユーザー入力ではなく呼び出し側が渡すページング指定である。したがって SM-1 の達成には必須ではなく、品質上の改善として Should Have とする。

### FR-7. raw-SQL 経路の扱い

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-7.1 | 次の経路は現行の挙動を維持し、SM-1 の判定対象外とすること: `Query.where(String)` / `and(String)` / `or(String)` の**直接利用**、`Sort.sort(Object, Object)` の非 `Column` キー経路、`Column.getFunctionName()`、`DataType.FUNCTION` の値。ただし `Dao.param(...)` が返す述語を `where(String)` に渡す経路は例外であり、FR-7.3 の代替経路により SM-1 の対象に含める | Must | Q5、ADR-002 |
| FR-7.2 | FR-7.1 の各経路が呼び出し側の SQL テキストをそのまま連結すること、およびそれらが SM-1 の判定対象外であることを、利用者が参照できる形で文書化すること。あわせて FR-7.3 の代替経路の存在と、旧経路から新経路への移行方法を文書化すること | Must | Q5、ADR-002 |
| FR-7.3 | `Dao.param(Column, Conditions, String...)` が運ぶ実行時の値に対して、バインド版の代替経路（`Dao.prepare(...)` と `Query.where(Param)` / `and(Param)` / `or(Param)`）を提供すること。旧 `param()` と `where(String)` はシグネチャ・挙動とも無変更で残すこと | Must | ADR-002 |

**FR-7.1 の根拠と範囲。** これらの経路に渡されるのは開発者が書いた SQL であり、実行時のユーザー入力ではない、という判断による（Q5 = A）。ただしサブクエリ経路（`andIn` 等）はこの理由が当てはまらないため FR-1.6 として対象に含めた（Q11）。すなわち FR-7.1 の一覧は、`architecture.md` の Raw-SQL escape hatches からサブクエリ経路を除いたものである。

**FR-7.1 / FR-7.2 の更新と FR-7.3 の追加（Units Generation 2.7 で反映、由来: `decisions.md` ADR-002）。** Application Design（2.6）のコード追跡 F3 により、`Dao.param(Column, Conditions, String...)`（`Dao.java:137-139`）が **`data.getValue(...)` すなわち実行時の値**をリテラルとして埋め込んだ述語文字列を返し、サンプル DAO とテストがそれを `Query.where(String)` に渡していることが判明した（`UserDao.java:22, :43`、`FileDataDao.java:12, :30`、`MySQLDaoTest.java:83`）。これはライブラリの正典的な使い方であり、この経路がリテラルのまま残ると SM-1 が主要経路を素通りする。したがって Q5 = A の根拠（「渡されるのは開発者が書いた SQL である」）は、`where(String)` の**直接利用**には成立するが `param()` 経由には成立しない。FR-7.1 をその範囲に限定し、代替経路を FR-7.3 として追加する。`prepare` という別名を用いるのは、Java が戻り値の型だけが異なるオーバーロードを許さないためである。

### FR-8. 検証手段

| ID | 要件 | 優先度 | 出典 |
|---|---|---|---|
| FR-8.1 | テストから、各バインドパラメータの**位置と値**をアサートできること。現行 `MockPreparedStatement` の `setXxx` はすべて空実装で `MockConnection.prepareStatement(String)` は SQL 引数を破棄するため、この制約の解消を本要件に含む | Must | Q8 |
| FR-8.2 | 既存テストの書き換えに際し、旧アサーション（生成 SQL 文字列）と新アサーション（バインド値）の対応表を成果物として作成すること。対応表は、書き換え前に検証していた振る舞いが書き換え後も検証されていることを示すこと | Must | Q6 |
| FR-8.3 | 現在 surefire に拾われていない `QueryImplTest02`、`QueryImplTest03`、`UserDaoTest2` を実行対象に含めること | Must | Q7 |
| FR-8.4 | `User_test.java:14` の SQL インジェクション手動プローブ（NUMERIC カラムに `';select * from dual --'` を渡す）を、ビルドで実行される回帰テストとして取り込むこと。FR-2.1 により、期待される振る舞いは `InvalidParameterException` による拒否である | Must | Q7, Q4 |
| FR-8.5 | LIKE 値が `$ # ~ ! ^` の 5 候補すべてを含む場合の挙動（現行は `%` / `_` が未エスケープのまま出力される）にテストを追加し、期待される振る舞いを明示的に固定すること | Should | FR-2.3, `code-quality-assessment.md` |

**FR-8.2 の判定基準。** 「テスト件数が減っていないこと」と「対応表の各行に新アサーションが存在すること」の 2 点をもって満たされたとみなす。

---

## Non-Functional Requirements

| ID | 分類 | 要件 | 判定基準 | 出典 |
|---|---|---|---|---|
| NFR-1 | セキュリティ | SM-1 の対象範囲において、値が SQL テキストに文字列連結で埋め込まれる箇所がゼロであること。対象範囲は FR-1（FR-1.6 を含む）と FR-7.3 の代替経路であり、FR-7.1 の raw 経路の直接利用と FR-1.7（識別子位置）は含まない | **実行経路**に値の文字列連結が 0 件。非推奨の public メソッド経由でのみ到達できるリテラル生成経路は判定対象外とする | SM-1, Q5, Q11, ADR-002, ADR-003 |
| NFR-2 | セキュリティ | tamacat-dao 本体に対する静的解析（既存の CodeQL ワークフロー）で SQL インジェクションの指摘が 0 件であること | 該当指摘件数 = 0 | SM-3 |
| NFR-3 | 互換性 | public API に破壊的変更がないこと。既存の利用側コード（`DaoAdapter<T>` のサブクラス）が再コンパイルなしで動作すること | public な型・メソッドシグネチャの削除および互換性のない変更が 0 件 | `intent-statement.md`, Q1 |
| NFR-4 | 移植性 | Java 8 を source / target として維持すること | `pom.xml` の source/target が 1.8 のまま、post-8 の言語機能を使用しない | `intent-statement.md` |
| NFR-5 | 保守性 | 変更後の全テストがグリーンであること（移行後のテストを対象とする） | `mvn test` が Failures = 0, Errors = 0 で成功 | SM-2, Q6 |
| NFR-6 | 保守性 | FR-8.3 で実行対象に加えたテストを含めて、テスト件数が変更前（138 件）を下回らないこと | 実行テスト件数 >= 138 | Q6, Q7 |
| NFR-7 | 性能 | 性能目標は設定しない。PreparedStatement キャッシュ等の最適化は行わない | 該当なし（O-2 によりスコープ外） | O-2 |

**NFR-1 の判定基準の更新（Units Generation 2.7 で反映、由来: `decisions.md` ADR-003）。** Application Design（2.6）は、FR-3.1（破壊的変更をしない）と NFR-1 の衝突を「旧 API の戻り値を変えず、非推奨にしたうえでバインド版を新メソッドとして追加する」形で解いた。その帰結として、**リテラル生成コード（`SQLParser` のリテラル系）が残り、非推奨の public メソッド経由で到達可能なままになる**。したがって「対象範囲内で値を SQL 文字列に連結する経路が 0 件」という当初の判定基準はこの設計の下では達成できない。判定を「**実行経路**に値の文字列連結が 0 件」に改める——`Dao.search` / `searchList` / `create` / `update` / `delete` から `java.sql.PreparedStatement` に至る経路に、値の文字列連結が現れないことをもって満たされたとみなす。到達可能だが実行経路に乗らないリテラル生成（非推奨 API の直接呼び出し）は判定対象外とする。

なお静的解析（NFR-2 / SM-3）は残存するリテラル連結経路を指摘しうる。この経路が実行に到達しないことを示すか、指摘を抑制するかは Build and Test（3.6）で確認を要する（`decisions.md` ADR-003 の Negative）。

**NFR-7 の補足。** 性能目標は置かないが、FR-6.1（Oracle の rownum ページング有効化）は全行スキャンからの変更であるため、結果として性能特性が変わる。これは最適化を目的とした変更ではなく、欠陥是正の副次的な結果である。

**テスト量の方針。** `team-practices` が存在しない（practices-discovery は SKIP）ため、`aidlc/spaces/default/memory/org.md` の既定に従う。ただし `org.md` の `## Testing Posture` が列挙する scope（`mvp` / `enterprise` / `feature` / `infra` / `bugfix` / `security-patch` / `poc` / `refactor` / `workshop`）に、本ワークフローの scope である `sql-parameterization` は含まれていない。したがってテスト量はワークフロー状態（`aidlc-state.md` の `**Test Strategy**`）が記録する **Standard** に従う。Standard の定義——コンポーネントあたり 5-8 テスト、単体テスト + 主要境界の統合テスト——は `.claude/aidlc-common/protocols/stage-protocol.md` §8「Test Strategy」による。`org.md` が `mvp` / `enterprise` / `feature` / `infra` に課す「line coverage 80% 以上」を本取り組みに適用するかは OQ-4 を参照。

---

## Constraints

| ID | 制約 | 出典 |
|---|---|---|
| CON-1 | Java 8 を維持する。post-8 の言語機能・API を使用しない | `intent-statement.md` |
| CON-2 | public API の後方互換性を維持する（破壊的変更をしない） | `intent-statement.md` |
| CON-3 | 作業は v2.0 ブランチ上でローカルのみ。`git push` しない。リモートは v1.6.1 のまま | `intent-statement.md` |
| CON-4 | 対象は tamacat-dao ライブラリ内の SQL 生成／実行経路のみ。利用側アプリケーションは対象外 | `intent-statement.md`, O-1 |
| CON-5 | JDBC ドライバは利用側アプリケーションが供給する。ライブラリは `java.sql` / `javax.sql` に対してのみコードを書く | `business-overview.md` |
| CON-6 | ORM フレームワーク（Spring / Hibernate / MyBatis 等）に依存しない。compile スコープの第三者依存は `tamacat-core` と `javax.json` のみという現状を前提とする | `business-overview.md` |
| CON-7 | `QueryImpl` は単回使用である（`where` の `StringBuilder` がリセットされない）。バインド値を保持する仕組みも同じライフサイクルに従う | `architecture.md` |
| CON-8 | CodeQL ワークフローは `master` への push / PR と週次 cron でのみ動作する。v2.0 ブランチでは CI が走らないため、SM-3 / NFR-2 の測定にはブランチ設定の変更か手動実行が必要である | `code-quality-assessment.md` |

---

## Assumptions

`intent-statement.md` から継承するもの（本ステージでは解消していない）。

| ID | 前提 | 継承元 |
|---|---|---|
| A-1 | public API の後方互換性維持（CON-2）と SM-1 が両立するかは未検証である。両立可否は Application Design（2.6）で判断する。方針そのものは確定しているが、その方針の下で SM-1 が達成可能かは未確認 | `intent-statement.md` |
| A-2 | `intent-statement.md` の Problem Statement 第 2 項が指す「利用アプリケーション側のセキュリティ監査／診断指摘」と、SM-3 が測定する「tamacat-dao 本体に対する自動静的解析の指摘」が同一であるかは確認されていない | `intent-statement.md` |

本ステージで新たに生じたもの。

| ID | 前提 | 根拠 |
|---|---|---|
| A-3 | FR-1.6（サブクエリ経路のバインド化）の実装コストは Q1 の結論に依存する。Q1 が B / C（値を運ぶ API が正）ならほぼ追加コストなしで従い、Q1 が A（既存メソッドはリテラル SQL を返し続ける）なら、子クエリの合成を新しい値保持経路側で行う必要があり手数が増える | Q11 の議論 |
| A-4 | FR-8.3 で `UserDaoTest2`（Derby / `javadb` 統合経路）を実行対象に加えると、ビルドに Derby が必要になる。これにより「実 DB エンジンに対して生成 SQL を検証する実行テストが存在しない」という現状（`code-quality-assessment.md`）は解消されるが、ビルドの前提条件が増える。この前提条件の追加が許容されるかは確認していない | Q7 |
| A-5 | FR-8.1（バインド位置と値のアサート）の実現手段が、既存 `org.tamacat.mock.sql` スタックの拡張であるか、テストスコープに宣言済みで未使用の EasyMock の活用であるか、新規のテストライブラリ導入であるかは確定していない。新規依存の追加可否も確定していない | Q8, OQ-3 |
| A-6 | `scope-document.md` の C-9（識別子位置の注入不能化）は Should Have として暫定配置されたものであり、その配置は本ステージでも変更していない（FR-1.7）。実現機構は未確定である（OQ-9） | `scope-document.md` |

---

## Out of Scope

| ID | 対象外 | 理由 | 出典 |
|---|---|---|---|
| OOS-1 | 利用側アプリケーションの修正 | tamacat-dao の外はプロダクト境界の外 | O-1, CON-4 |
| OOS-2 | 性能最適化（PreparedStatement キャッシュ等） | 安全性が先、最適化は後 | O-2 |
| OOS-3 | 新規 DB 方言のサポート追加 | 既存方言の対応が対象であり、対応範囲の拡大はしない | O-3 |
| OOS-4 | PostgreSQL 専用の `Dao` / `Search` クラスの追加 | PostgreSQL は汎用フォールバック経路として扱う | Q9, FR-5.2 |
| OOS-5 | FR-7.1 が列挙する raw-SQL 経路のシグネチャ・挙動の変更 | 呼び出し側が渡すのは開発者が書いた SQL であり、実行時のユーザー入力ではない | Q5 |
| OOS-6 | `code-quality-assessment.md` の技術的負債レジスタのうち、SQL 生成／実行経路に乗っていない項目（open version range、MANIFEST バージョン乖離、mock クラスの production jar 同梱、ドライバの過剰な deregister、静的状態のロック不整合、空 catch ブロック、`e.printStackTrace()`、非推奨 API の残置 等） | 変更経路上にないため。Q10 は経路上の 4 件のみを対象とした | Q10 |

SQL インジェクション以外のセキュリティ課題については、`scope-document.md` の判断（明示的な Won't Have とはせず、発見時に都度判断する。プロダクト境界は変更しない）をそのまま引き継ぐ。

---

## Acceptance Criteria

主要要件の受け入れ条件を Given / When / Then で示す。網羅的な一覧ではなく、判定が曖昧になりやすい要件を対象とする。

**AC-1（FR-1.1 / NFR-1）**
```
Given STRING カラムに対する EQUAL 条件を持つ Search が組み立てられている
When その Search を含む Query から SELECT が実行される
Then 実行された SQL テキストに値のリテラルが含まれず、該当位置に ? が現れる
And その ? に対応するバインド値として、呼び出し側が渡した値がそのまま渡されている
```

**AC-2（FR-1.2 / FR-2.3）**
```
Given STRING カラムに対する LIKE_PART 条件に、値 "a%b_c" が渡されている
When その条件を含む SELECT が実行される
Then 実行された SQL テキストに値のリテラルが含まれず、該当位置に ? が現れる
And escape 句が現行と同じ規則で付与されている
And バインド値において % と _ が現行と同じ規則でエスケープされている
```

**AC-3（FR-1.3）**
```
Given NUMERIC カラムに対する IN 条件に 3 つの値が渡されている
When その条件を含む SELECT が実行される
Then SQL テキストの IN 句が (?,?,?) の形になっている
And 3 つのバインド値が渡された順序どおりに渡されている
```

**AC-3b（FR-1.4）**
```
Given STRING カラムと NUMERIC カラムを持つ Bean に対する INSERT が組み立てられている
When その INSERT が実行される
Then 実行された SQL テキストの VALUES 句に値のリテラルが含まれず、カラム数と同じ数の ? が現れる
And 各 ? に対応するバインド値が、カラムの並び順どおりに渡されている
And 同じことが UPDATE の SET 句についても成立する
```

**AC-4（FR-1.6）**
```
Given 子 Query の WHERE に値を持つ条件が設定され、その子 Query が親の andIn に渡されている
When 親 Query から SELECT が実行される
Then 実行された SQL テキストに子クエリの値のリテラルが含まれていない
And 子クエリの値が、親のバインドパラメータ列のうち子クエリの差し込み位置に対応する位置に渡されている
```

**AC-5（FR-2.1 / FR-8.4）**
```
Given NUMERIC カラムに対する EQUAL 条件に、値 "';select * from dual --'" が渡されている
When その条件で Search を組み立てようとする
Then InvalidParameterException がスローされる
And SQL は実行されない
```

**AC-6（FR-2.2）**
```
Given STRING カラムに対する EQUAL 条件に、シングルクォートを含む値が渡されている
When バインド経路でその条件を含む SELECT が実行される
Then バインド値は呼び出し側が渡した文字列と一致する（'' への二重化が行われていない）
```

**AC-7（FR-1.5 / FR-3.2）**
```
Given OBJECT カラムと 2 つの STRING カラムを持つテーブルに対する UPDATE が組み立てられている
When getBlobIndex() が呼ばれる
Then 返される値は、その UPDATE の SQL における BLOB カラムのバインドパラメータ位置である
And その位置に setBinaryStream を行った UPDATE が正常に実行できる
```

**AC-8（FR-4.1）**
```
Given 値を持つ条件で SELECT が実行された直後である
When getExecutedQuery() が参照される
Then 実行された SQL テキストが記録されている
And その実行に渡されたバインド値も記録されている
```

**AC-9（FR-6.1）**
```
Given Oracle 方言が選択されている
When searchList(query, start, max) が呼ばれる
Then rownum によるページングが行われる
And 実行される SQL に未バインドの ? が残っていない
And start と max の両方がページング範囲の決定に使われている
```

**AC-10（FR-6.4）**
```
Given Oracle 方言が選択されている
When ValueConvertFilter に null が渡される
Then NullPointerException がスローされない
And 汎用実装および MySQL 実装と同じ結果が返る
```

**AC-10b（FR-1.7、Should）**
```
Given ORDER BY 句のキー、またはテーブル名・カラム名の位置に、SQL の構文文字を含む文字列が渡されている
When その Query から SQL が組み立てられる
Then 例外で拒否されるか、宣言済みの Table / Column メタデータに照合されて拒否されるかのいずれかが起こる
And 渡された文字列が構文として生きたまま SQL テキストに現れることはない
```
（(a) 例外による拒否と (b) メタデータ照合のどちらを採るかは OQ-9。この AC はどちらの機構でも判定できる形にしてある。）

**AC-11（NFR-3）**
```
Given 変更前の tamacat-dao に対してコンパイルされた利用側の DaoAdapter サブクラス
When 変更後の tamacat-dao jar に差し替える
Then 再コンパイルなしでロードでき、NoSuchMethodError / NoSuchFieldError が発生しない
```

---

## Open Questions

後続ステージで解消すべき事項。いずれも本ステージでは推測で埋めていない。

| ID | 未解決事項 | 解消先 |
|---|---|---|
| OQ-1 | `Query.getSelectSQL()` 等の戻り値をどうするか（FR-3.3）。CON-2 と SM-1 の両立をどの構造で実現するか。この決定が A-1 と A-3 の両方を規定する | Application Design（2.6） |
| OQ-2 | バインド値をどこで保持し、どの順序で JDBC に渡すか。`QueryImpl` の単回使用制約（CON-7）および `Search` の `StringBuilder` 累積とどう整合させるか | Application Design（2.6） |
| OQ-3 | FR-8.1 の実現手段。既存 `org.tamacat.mock.sql` スタックの拡張か、宣言済み未使用の EasyMock の活用か、新規テストライブラリの導入か。新規依存を追加してよいかを含む（A-5） | Application Design（2.6）／ Build and Test（3.6） |
| OQ-4 | `org.md` が課す「line coverage 80% 以上」を本取り組みに適用するか。現行リポジトリにはカバレッジ計測ツールが一切なく（`code-quality-assessment.md`）、138 件という数字は件数であって被覆率ではない。計測手段の導入可否を含む | NFR Requirements（3.2） |
| OQ-5 | リポジトリ外の tamacat プロジェクトが `Query.getBlobIndex()` や `getSelectSQL()` の戻り値に依存しているかは未確認である。NFR-3 の検証範囲がリポジトリ内に限られることの妥当性 | Application Design（2.6） |
| OQ-6 | FR-8.3 で `UserDaoTest2` を有効化する場合の Derby 依存追加（A-4）が許容されるか | Build and Test（3.6） |
| OQ-7 | SM-3 / NFR-2 の測定方法。CodeQL は `master` のみを対象としており v2.0 ブランチでは走らない（CON-8）。ブランチ設定を変えるか、手動実行するか | Build and Test（3.6）／ CI Pipeline（3.7、本スコープでは SKIP） |
| OQ-8 | A-2（利用アプリ側の監査指摘と SM-3 の測定対象の同一性）は `intent-statement.md` から未解消のまま継承している | 未定 — 利用側アプリケーションの状況確認が必要 |
| OQ-9 | FR-1.7 の機構——(a) 例外による拒否か、(b) 宣言済み `Table` / `Column` メタデータへの照合か、あるいは別の検証方式か。**実施可否は Units Generation（2.7）で解決した**——Unit `identifier-safety`（U5）として実施する（`unit-of-work.md`）。優先度は Should Have のまま。**機構の選択のみが未解決** | Functional Design（3.1）、U5 の中で |

---

## Traceability

要件から上流成果物への対応。

| 要件群 | 上流の能力 / 指標 | 上流成果物 |
|---|---|---|
| FR-1.1〜FR-1.5 | C-1, C-2, C-3, C-4, C-5 / SM-1 | `scope-document.md`, `intent-statement.md` |
| FR-1.6 | SM-1（Q11 による対象範囲の確定） | `intent-statement.md`, `architecture.md` |
| FR-1.7 | C-9 | `scope-document.md` |
| FR-2.1〜FR-2.3 | 現行挙動の維持（Q4） | `architecture.md`, `code-quality-assessment.md` |
| FR-2.4 | 上流にない追加要件（本ステージでコードから導出） | `architecture.md` |
| FR-3.1〜FR-3.3 | API 互換性の方針 | `intent-statement.md` |
| FR-4.1〜FR-4.2 | 検証チャネルの再定義（Q3, Q13） | `architecture.md` |
| FR-5.1〜FR-5.3 | C-6（Q9 により PostgreSQL の意味を確定） | `scope-document.md`, `architecture.md` |
| FR-6.1〜FR-6.4 | 上流にない追加要件（Q10, Q12） | `code-quality-assessment.md` |
| FR-7.1〜FR-7.3 | SM-1 の対象範囲の明示（Q5）、および `param()` 経路の代替提供（ADR-002） | `architecture.md`, `decisions.md` |
| FR-8.1〜FR-8.5 | C-7 / SM-2 | `scope-document.md`, `intent-statement.md`, `code-quality-assessment.md` |
| NFR-1 | SM-1 | `intent-statement.md` |
| NFR-2 | C-8 / SM-3 | `scope-document.md`, `intent-statement.md` |
| NFR-3〜NFR-4 | API 互換性、Java 8 | `intent-statement.md` |
| NFR-5〜NFR-6 | SM-2 | `intent-statement.md` |
| NFR-7 | O-2 | `scope-document.md` |

C-8（静的解析での指摘ゼロ）と SM-3 の達成判定は **NFR-2 が単独で担う**。FR-8 群はテストと検証手段に関する要件であり、静的解析の指摘件数を直接動かすものではないため、C-8 / SM-3 とは対応づけない。O-1〜O-3 は OOS-1〜OOS-3 に対応する。

上流にない新規要件は次のとおりで、いずれも出自を明記した。

| 新規要件 | 出自 |
|---|---|
| FR-2.4 | 本ステージが `architecture.md` の `parseValue` DataType 表から導出。設問としてユーザーに提示していない |
| FR-6.1〜FR-6.4 | Q10 / Q12（変更経路上の既存欠陥の是正） |
| FR-8.2 | Q6（旧→新アサーション対応表） |
| FR-8.3〜FR-8.4 | Q7（未実行テストの取り込み、インジェクションプローブの回帰テスト化） |
| FR-8.5 | 本ステージが `code-quality-assessment.md` の未テスト挙動一覧から導出（LIKE 候補枯渇経路）。設問としてユーザーに提示していない |
| FR-7.3 | **Units Generation（2.7）で追加。** `decisions.md` ADR-002 が Application Design（2.6）のコード追跡 F3 に基づいて決定した `prepare()` + `where(Param)` の代替経路を要件化したもの |

---

## 本ステージ以降に適用された更新

本文書は Requirements Analysis（2.3）の成果物として承認された。その後、下流ステージの決定により次の更新が適用されている。更新はいずれも承認済み ADR に基づき、適用ステージの承認ゲートで明示された。

| 適用ステージ | 更新箇所 | 由来 | 内容 |
|---|---|---|---|
| Units Generation（2.7） | FR-7.1 / FR-7.2 | `decisions.md` ADR-002 | FR-7.1 の対象外範囲を `where(String)` の**直接利用**に限定。`param()` 経由の値は代替経路（FR-7.3）により SM-1 の対象に含める。FR-7.2 に代替経路と移行方法の文書化を追加 |
| Units Generation（2.7） | FR-7.3（新規） | `decisions.md` ADR-002 | `Dao.prepare(...)` と `Query.where(Param)` / `and(Param)` / `or(Param)` の提供を要件化。旧 `param()` / `where(String)` は無変更で残す |
| Units Generation（2.7） | NFR-1 | `decisions.md` ADR-003 | 判定基準を「対象範囲内で値の文字列連結が 0 件」から「**実行経路**に値の文字列連結が 0 件」に改め、非推奨 API 経由でのみ到達できるリテラル生成経路を判定対象外とした |
| Units Generation（2.7） | OQ-9 | `unit-of-work.md` | 実施可否を解決（U5 `identifier-safety` として実施）。機構の選択のみ Functional Design（3.1）に残る |

下記の `## Review` 節は 2.3 時点のレビュー結果であり、上記の更新より前の内容に対するものである。節中の行番号は更新前のものを指す。

---

## Review

READY

前回の 3 件のブロッキング指摘を個別に検証した。いずれも実体が是正されており、体裁だけの修正ではない。加えてコード上の主張（行番号・挙動）を実ソースと突き合わせ、トレーサビリティ表・AC 番号体系・OQ 参照の整合性を独立に再点検したが、READY を覆す新規の欠陥は見つからなかった。

**指摘 1（FR-1.7 の可検証性）— 解消を確認。** `requirements.md:47` の FR-1.7 は「(a) 例外で拒否される、(b) 宣言済みの `Table`/`Column` メタデータに照合され一致しないものが拒否される」という二値の観測可能な結果に書き換わっており、AC-10b（313行付近）はこれをそのまま Given/When/Then 化して「渡された文字列が構文として生きたまま SQL テキストに現れることはない」という否定条件まで含めている。`phases/inception.md` の「各要件は明確な pass/fail 基準を持つこと」を満たす — QA は悪意ある識別子入力を与え、(a) 例外 or (b) 却下、かつ非混入、の 2 点を機械的に確認できる。
機構の先取り（サイレントな solution shape の混入）についても再確認した。`Table` / `Column` は架空のクラスではなく実在するメタデータクラスである（`src/main/java/org/tamacat/dao/meta/Table.java`, `Column.java`、`code-structure.md:39`）。したがって (b) は実装の先取りではなく、リポジトリに実在する語彙を使って「観測可能な結果」を記述しただけであり、しかも本文が「(a)/(b) のどちらを採るか、別の機構を採るかは本ステージでは決めていない」と明記し、機構選択を OQ-9（317行、Application Design 2.6 へ）に切り出している。A-6（182行）も同じ立場を重ねて記録している。過剰決定ではない。

**指摘 2（FR-2.4 の出典誤帰属）— 解消を確認。** `requirements.md:60` の出典列は `architecture.md`（本ステージでの新規導出）に修正され、直後の「FR-2.4 の出自」段落（62行）が「Q1〜Q13 のいずれの設問でもユーザーに提示していない」ことを明記している。実際に `requirements-analysis-questions.md` の Q1〜Q13 本文・フォローアップ Q11〜Q13 のいずれにも DATE/TIME の `current_timestamp` 挙動は登場しない — 出典なしという新しい記載は正確である。コード上の裏付けも確認した：`architecture.md:47` に「TIME, DATE | empty or "NULL" → null; the exact literal `current_timestamp` emitted bare」とあり、`requirements.md:60` の記述と一致する。トレーサビリティ表（340〜341行）も FR-2.1〜FR-2.3（Q4 由来）と FR-2.4（新規導出）を別行に分離しており、新規要件サマリ表（360行）にも FR-2.4 が漏れなく載っている。出自・分離・開示のすべてが揃っている。

**指摘 3（トレーサビリティの自己矛盾）— 解消を確認。** `requirements.md:347`〜`349` は FR-8.1〜FR-8.5 行を `C-7 / SM-2` のみに絞り、NFR-1 行（348行）を `SM-1`、NFR-2 行（349行）を `C-8 / SM-3` として分離した。354行の説明文「C-8（静的解析での指摘ゼロ）と SM-3 の達成判定は NFR-2 が単独で担う。FR-8 群は…C-8 / SM-3 とは対応づけない」は表と完全に一致しており、以前のような二重対応（FR-8 行と NFR 行の両方が C-8/SM-3 を主張する）は残っていない。scope-document.md の C-1〜C-9 を全数照合したが、9 件すべてがちょうど 1 箇所（C-1〜C-5→FR-1.1〜1.5、C-6→FR-5、C-7→FR-8、C-8→NFR-2、C-9→FR-1.7）に対応しており、孤立も二重登録もない。SM-1〜SM-3 も同様に一意に対応している。

**非ブロッキング 2 件も是正を確認した。**
- テスト量の方針（147行）: 「Standard の定義…は `.claude/aidlc-common/protocols/stage-protocol.md` §8「Test Strategy」による」という出典が追加された。実ファイルを確認したところ、`stage-protocol.md` の `## 8. Depth Guidance` 配下に `### Test Strategy` 節があり（734行）、「Standard — per-component model: 5-8 tests per component / Unit tests + integration tests (key boundaries)」（747〜749行）が requirements.md の記述と一致する。出典は正確。
- FR-1.4 に AC-3b（230行）が追加され、INSERT の VALUES 句と UPDATE の SET 句の両方についてバインド化と順序保持を Given/When/Then で固定している。未検証だった要件の可検証性が確保された。

**独立の再点検で確認した項目（新規の欠陥なし）。**
- コード上の主張の正確性: `QueryImpl.java:389-406`（andIn/andNotIn/andExists/andNotExists が子 `getSelectSQL()` を連結）、`QueryImpl.java:268`（OBJECT 分岐だけ `.replaceFirst(tableName+".", "")` を欠く）、`SQLParser.java:119-139`（`$#~!^` 5 候補枯渇時に `%`/`_` が未エスケープで通る経路）、`OracleDao.java:30-73`（`searchListForOracle` が未バインドの `?` を `Statement` 相当の `executeQuery(String)` に渡し、`max` は rownum 構成に不使用）、`MySQLDao.java:46`（LIMIT の `int` 直接連結）、`OracleSearch.java:11-15`（null ガードなし、`Search.DefaultValueConvertFilter` と `MySQLSearch.MySQLValueConvertFilter` は null ガードあり）—— いずれも実ソースと一致することを確認した。`code-quality-assessment.md` の「138 件」「MockPreparedStatement が記録しない」等の主張も原典と一致する。
- Q&A への忠実性: Q1（未決のまま FR-3.3/OQ-1 に反映）、Q5/Q11 の部分上書き（FR-7.1 がサブクエリ経路を除外し、FR-1.6 の補足で上書き関係を明記）、Q13（追加条件なし、FR-4.2 に反映）はいずれも原文と食い違いなし。
- AC の ID 参照: AC-1, AC-2, AC-3, AC-3b, AC-4〜AC-11, AC-10b が参照する FR/NFR ID（FR-1.1, FR-1.2, FR-1.3, FR-1.4, FR-1.5, FR-1.6, FR-1.7, FR-2.1〜2.3, FR-3.2, FR-4.1, FR-6.1, FR-6.4, FR-8.4, NFR-1, NFR-3）はすべて FR/NFR セクションに実在し、ダングリング参照はない。
- OQ 参照: 本文中で言及される OQ-1〜OQ-9 はすべて Open Questions 表に実在し、逆に表の各 OQ も本文（FR-1.7 補足、A-1〜A-6、NFR 補足）のどこかに根拠を持つ。
- A-1/A-2 の認識論的地位: `intent-statement.md` の Assumptions & Open Questions をそのまま「継承するもの」として区別しており、本ステージで新たに事実であるかのように昇格させた箇所はない。
- ステージ定義（`requirements-analysis.md` Step 11）が要求する 7 区分（Intent analysis, Functional/Non-Functional Requirements, Constraints, Assumptions, Out of Scope, Open Questions）はすべて揃っており、`upstream-coverage` センサーが要求する `intent-statement` / `scope-document` / `team-practices` への参照も冒頭の「上流成果物との関係」節に明記されている。

以上により、再審査でも新規のブロッキング事項は見つからなかった。READY と判定する。

# Requirements Analysis — Questions

## Sources

- `intent-statement.md`（intent-capture, 1.1）— 問題定義、成功指標 SM-1〜SM-3、プロダクト境界（tamacat-dao ライブラリ内の SQL 生成／実行経路のみ）、public API の後方互換性維持の方針、Java 8 維持、ローカルのみ・git push なしの制約。
- `scope-document.md`（scope-definition, 1.4）— 最小価値スコープ、能力 C-1〜C-9、スコープ外 O-1〜O-3、walking-skeleton-first の順序方針。
- `business-overview.md` / `architecture.md` / `code-structure.md`（reverse-engineering, 2.1 / space-level codekb）— 現行実装の事実。とくに「SQL は文字列連結で組み立てられ `java.sql.Statement` で実行される」という中心的設計事実、唯一のバインド経路（BLOB / `DataType.OBJECT`）、`SQLParser` の 2 プリミティブ、raw-SQL エスケープハッチ、方言差。
- `code-quality-assessment.md`（同上、参考）— 既存テストの配置と、`MockPreparedStatement` が何も記録しないという検証上の制約。

本ステージは要件（何が満たされるべきか）を確定する。どのクラスをどう変更するかという設計判断は Application Design（2.6）以降に委ねる。

---

## Q1. `Query.getSelectSQL()` など「SQL 文字列を返す public メソッド」の戻り値は、変更後どうあるべきですか？

現行では `Query.getSelectSQL()` / `getInsertSQL()` / `getUpdateSQL()` / `getDeleteSQL()` は値がリテラルとして埋め込まれた完全な SQL 文字列を返し、`Dao.executeQuery(String)` / `executeUpdate(String)` がそれを受け取る（`architecture.md` の Execution Surface）。値をバインド変数で運ぶ以上、この「SQL 文字列だけを返す」形は値を運べない。public API の後方互換性維持（`intent-statement.md`）と SM-1（全経路がバインド変数）の両立は、この点で最初に決まる。

- A. シグネチャも戻り値の中身も維持する（リテラル埋め込みの SQL 文字列を返し続ける）。バインドで実行する経路は別に追加し、既存メソッドは互換のために残す
- B. シグネチャは維持するが、戻り値の中身を `?` を含むパラメータ化 SQL に変える（値は別経路で保持・供給される）
- C. 既存メソッドは維持しつつ非推奨（`@Deprecated`）とし、値と SQL を一緒に運ぶ新 API を正とする
- D. まだ決められない — Application Design（2.6）で代替案を比較して決める
- X. Other (please specify)

[Answer]: D. まだ決められない
**Mode:** guided | **Timestamp:** 2026-08-05T03:45:00Z

---

## Q2. `Query.getBlobIndex()`（現行で唯一のバインド位置を返すメソッド）は、変更後どう扱われるべきですか？

現行では BLOB（`DataType.OBJECT`）だけが `?` として出力され、その JDBC パラメータ位置を `getBlobIndex()` が返す。ただしその契約（1 始まりのパラメータ位置なのか、OBJECT カラムの個数なのか）はリポジトリ内では確定していない — 呼び出し元もテストも存在しない（`architecture.md` / `code-quality-assessment.md` の Stated Unknowns）。全ての値がバインドされると、BLOB は「多数あるパラメータのひとつ」になる。

- A. public メソッドとして残し、変更後も「BLOB カラムのパラメータ位置」を返す（他の値もバインドされるため、位置は現行と変わる可能性がある）
- B. public メソッドとして残すが非推奨とし、常に現行と同じ値を返す互換シムとして扱う
- C. 契約が確定していない以上、まず契約を確定させることを要件とする（値と挙動をテストで固定する）
- D. まだ決められない — Application Design（2.6）で決める
- X. Other (please specify)

[Answer]: A. 現役のまま残す
**Mode:** guided | **Timestamp:** 2026-08-05T03:45:00Z

---

## Q3. `DBAccessManager.getExecutedQuery()`（実行済み SQL の記録）は、変更後に何を記録すべきですか？

現行ではスレッドローカルの `List<String>` に SQL テキストのみが記録され、既存の end-to-end テスト（`UserDaoTest` など）はこれを事実上のアサーション経路として使っている。バインド変数化後、この記録は `?` を含む SQL だけになり、実際にどの値が渡されたかは記録されない（`architecture.md` の Cross-Cutting Concerns）。

- A. SQL テキストのみ（`?` を含む形）。バインド値は記録しない
- B. SQL テキストに加えてバインド値も記録する（テストとトラブルシュートの両方で必要）
- C. SQL テキストのみを記録し、バインド値の検証は別の仕組みで行う
- D. 現行の「リテラル埋め込み済み SQL」を再構成して記録し、既存テストのアサーションを壊さない
- E. まだ決められない
- X. Other (please specify)

[Answer]: B. SQL + バインド値
**Mode:** guided | **Timestamp:** 2026-08-05T03:45:00Z

---

## Q4. 現行の値検証・エスケープの挙動は、バインド変数化後にどうあるべきですか？

現行 `SQLParser` は 3 つの挙動を持つ。(1) NUMERIC / FLOAT カラムに非数値が来ると `InvalidParameterException` を投げて拒否する、(2) `ValueConvertFilter` が `'`（MySQL では `\` も）をエスケープする、(3) LIKE 値に `%` / `_` が含まれるときエスケープして `escape 'X'` 句を付ける。バインド変数化すると (2) は不要（二重エスケープの原因）になるが、(1) と (3) は意味が変わる。いずれも既存テスト（`SQLParserTest`）が固定している挙動である。

- A. (1) 型検証は維持、(2) クォートエスケープは廃止、(3) LIKE ワイルドカードのエスケープは維持（LIKE の意味論はバインドでは解決しないため）
- B. (1) も (2) も廃止し、値の解釈は JDBC ドライバに委ねる。(3) のみ維持
- C. (1)(2)(3) すべて維持する（挙動を一切変えない）
- D. (1) 型検証も (3) LIKE エスケープも維持しつつ、(2) はバインド経路では適用しない（raw 経路が残る場合はそこでのみ適用）
- E. まだ決められない
- X. Other (please specify)

[Answer]: D. 経路ごとに使い分け
**Mode:** guided | **Timestamp:** 2026-08-05T03:45:00Z

---

## Q5. 呼び出し側の SQL 断片をそのまま連結する経路（raw-SQL エスケープハッチ）は、どう扱うべきですか？

`architecture.md` の Raw-SQL escape hatches が列挙するとおり、以下は `SQLParser` を通さず呼び出し側のテキストをそのまま SQL に連結する: `Query.where(String)` / `and(String)` / `or(String)`、`QueryImpl.andIn` / `andNotIn` / `andExists` / `andNotExists`（子クエリの SQL テキストを親の WHERE に埋め込む）、`Sort.sort(Object, Object)`（キーが `Column` でない場合）、`Column.getFunctionName()`、`DataType.FUNCTION` の値。これらは SM-1「SQL を組み立てる全経路がバインド変数を使用」の判定対象に含まれるかどうかが未確定である。

- A. 現状維持。呼び出し側が渡すのは開発者が書いた SQL であり、実行時のユーザー入力ではない。SM-1 の判定対象外とし、その旨を文書化する
- B. 現状維持だが、SM-1 の判定対象に含める（＝これらの経路が残る限り SM-1 は未達とみなす）ため、非推奨化または代替 API の提供を要件とする
- C. サブクエリ経路（`andIn` / `andExists` 等）だけは対象とする — 子クエリの値もバインドで運ばれるべきであり、テキスト連結では値が失われるため
- D. `Sort` の非 Column キーと識別子位置（テーブル名・カラム名・ORDER BY）は C-9 として別扱いのまま、それ以外は A と同じ扱いにする
- E. まだ決められない
- X. Other (please specify)

[Answer]: A. 現状維持・SM-1 対象外
**Mode:** guided | **Timestamp:** 2026-08-05T03:48:00Z

---

## Q6. SM-2「既存テストスイートがグリーンのまま維持される」と C-7「既存テストの移行」は、どう両立させますか？

SM-2 は「変更前後で新規の失敗テストがゼロ」を判定基準とする（`intent-statement.md`）。一方 C-7 は生成 SQL 文字列のアサーションをバインド値のアサーションへ移行することを Must Have としている（`scope-document.md`）。SQL 文字列を変える以上、`SQLParserTest` / `SearchTest` / `QueryImplTest` / `MySQLDaoTest` / `UserDaoTest` のアサーションはそのままでは通らない。

- A. 「移行後のテストがグリーン」を SM-2 の判定とする。テストの書き換えは想定内であり、テスト件数が減らないこと・同じ振る舞いを別の形で検証していることを条件とする
- B. 既存テストは 1 行も変更せずにグリーンを維持することを条件とする（＝生成 SQL 文字列が変わらない実装のみ許容する）
- C. 既存テストは変更してよいが、変更前後で検証している振る舞いの対応表（旧アサーション → 新アサーション）を成果物として要求する
- D. まだ決められない
- X. Other (please specify)

[Answer]: C. 旧→新対応表を要求
**Mode:** guided | **Timestamp:** 2026-08-05T03:48:00Z

---

## Q7. 現在ビルドで実行されていないテストは、本取り組みの対象に含めますか？（複数選択可）

`code-quality-assessment.md` によれば、surefire が `**/*Test.java` のみを対象とするため `QueryImplTest02.java`（唯一 BLOB の `?` プレースホルダをアサートしているテスト）、`QueryImplTest03.java`、`UserDaoTest2.java` は実行されない。また 4 つのファイルは JUnit ではなく `main()` ハーネスであり、その 1 つ `User_test.java:14` は SQL インジェクションの手動プローブを含む。

- A. `QueryImplTest02`（BLOB プレースホルダ）を実行対象に含める — BLOB は C-5 で Must Have のため
- B. `QueryImplTest03` / `UserDaoTest2` も実行対象に含める
- C. `User_test.java` のインジェクションプローブを、実行される回帰テストとして取り込む
- D. いずれも対象外。実行されていないテストの整理は本取り組みのスコープ外とする
- E. まだ決められない
- X. Other (please specify)

[Answer]: A. QueryImplTest02 を含める, B. Test03 / UserDaoTest2 も, C. インジェクションプローブ
**Mode:** guided | **Timestamp:** 2026-08-05T03:48:00Z

---

## Q8. バインドされた値を検証できるようにすることは、本取り組みの要件に含めますか？

`code-quality-assessment.md` の記録によれば、`MockPreparedStatement` の `setXxx` はすべて空実装で、`MockConnection.prepareStatement(String sql)` は `sql` 引数を破棄する。したがって現状のテスト基盤では「どの値がどの位置にバインドされたか」を一切アサートできない。SM-1 を自動で検証するには、この制約を解消する必要がある。

- A. 要件に含める。バインド位置と値をアサートできることを、SM-1 の検証手段として必須とする
- B. 要件に含めるが、検証は「SQL テキストに `?` が現れ、リテラルが現れない」ことまでで足りるものとする
- C. 要件に含めない。SM-1 の検証は静的解析（SM-3 / CodeQL）と目視レビューで足りる
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. 必須とする
**Mode:** guided | **Timestamp:** 2026-08-05T03:48:00Z

---

## Q9. C-6「DB 方言ごとの差異への対応（MySQL / Oracle / PostgreSQL）」の PostgreSQL は、何を意味しますか？

`scope-document.md` の C-6 は MySQL / Oracle / PostgreSQL を挙げているが、現行コードには PostgreSQL 向けの `Dao` も `Search` も存在せず、`PostgreSQLCondition` という条件 enum があるだけで、`DaoAdapter.setDatabase` にも `postgres` の分岐がない（`architecture.md` の Dialect Architecture）。この不一致を解消する必要がある。

- A. 既存の 3 経路（MySQL / Oracle / 汎用フォールバック）でパラメータ化が成立すればよい。PostgreSQL は汎用経路として扱い、`PostgreSQLCondition` がその経路で正しく動けば足りる
- B. PostgreSQL 専用の `Dao` / `Search` を追加することを本取り組みに含める
- C. `PostgreSQLCondition` は対象に含めるが、方言クラスの追加は含めない（A とほぼ同義だが、`PostgreSQLCondition` のパラメータ化を明示的な要件とする）
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. 既存 3 経路で足りる
**Mode:** guided | **Timestamp:** 2026-08-05T03:51:00Z

---

## Q10. 変更経路上で見つかっている既存の欠陥は、本取り組みで扱いますか？（複数選択可）

`code-quality-assessment.md` の技術的負債レジスタのうち、SQL 生成／実行経路に直接乗っているものは次のとおり。`scope-document.md` の O-1〜O-3 はいずれもこれらを扱っていない。

- A. `OracleDao.searchListForOracle` — 未バインドの `?` を含む SQL を `Statement` で実行する死んだコード。実 DB では失敗する。削除または修正を含める
- B. `QueryImpl:268` — BLOB カラムだけ `.replaceFirst` が省かれ、SET 句で唯一テーブル修飾されたまま出力される
- C. `MySQLDao:46` — LIMIT 句の `int` 直接連結（値位置ではあるがユーザー入力ではない）
- D. `OracleSearch` の null ガード欠落（`OracleValueConvertFilter` が null で NPE する）
- E. いずれも扱わない。既存の欠陥修正は本取り組みのスコープ外とする
- X. Other (please specify)

[Answer]: A. searchListForOracle, B. QueryImpl:268 BLOB 修飾, C. MySQLDao:46 LIMIT 連結, D. OracleSearch null ガード
**Mode:** guided | **Timestamp:** 2026-08-05T03:51:00Z

---

## Answer analysis

Q1〜Q10 の全回答を横断して、あいまい語・矛盾・不足を点検した（stage-protocol.md §3）。

**整合が確認できたもの**

- Q1=D（API 戻り値は未決）と Q2=A（`getBlobIndex()` は現役のまま、位置は変わりうる）— Q2 は「他の値もバインドされる」ことを前提とするが、Q1 のどの選択肢とも両立する。
- Q4=D（型検証と LIKE エスケープは維持、クォートエスケープはバインド経路では適用しない）と Q7-C（`User_test.java:14` のインジェクションプローブを回帰テスト化）— 当該プローブは NUMERIC カラムに `';select * from dual --'` を渡すため、型検証維持の下では引き続き `InvalidParameterException` で拒否される。期待挙動は「拒否」で確定しており、あいまいさはない。
- Q9=A — `scope-document.md` の C-6 と現行コード（PostgreSQL 向け `Dao`/`Search` 不在）の不一致を解消した。PostgreSQL は汎用フォールバック経路として扱う。
- Q6=C と Q7=A/B/C — 既存テストの書き換えを許容し対応表を要求する方針は、未実行テストの取り込みと矛盾しない。
- Q10=A〜D は `scope-document.md` の O-1〜O-3（スコープ外）のいずれにも抵触しない。ただし 1.4 のスコープ文書に対する追加であるため、requirements.md では出典を Q10 として明記する。

**未解消として follow-up に回したもの**

1. Q5=A の理由（「呼び出し側が渡すのは開発者が書いた SQL であり、実行時のユーザー入力ではない」）が、サブクエリ経路（`andIn` / `andNotIn` / `andExists` / `andNotExists`）には当てはまらない。この経路は子 `Query` の `getSelectSQL()` テキストをそのまま親の WHERE に埋め込むため、子クエリの `Search` が保持する**実行時の値**がリテラルとして親 SQL に流れ込む（`architecture.md` の Raw-SQL escape hatches）。Q5=A の理由が覆う範囲と、この経路の実態が食い違う。→ Q11
2. Q10=A の選択肢本文が「削除**または**修正」という二択を内包しており、要件として一意に定まらない。→ Q12
3. Q3=B（実行済み SQL に加えてバインド値も記録する）は、機微な値がプロセス内の `ThreadLocal<List<...>>` に平文で残ることを意味する。SQL インジェクション対策という本取り組みの目的に対して、新たなデータ露出面を作る可能性がある（リスクの不整合）。→ Q13

---

## Q11（フォローアップ）. サブクエリ経路（`andIn` / `andNotIn` / `andExists` / `andNotExists`）は、Q5=A の「SM-1 判定対象外」に含めたままでよいですか？

Q5 で A（raw 経路は現状維持・SM-1 の判定対象外）を選択した理由は「呼び出し側が渡すのは開発者が書いた SQL であり、実行時のユーザー入力ではない」であった。しかしサブクエリ経路だけは事情が異なる。`QueryImpl.andIn` 等（`QueryImpl.java:389-406`）は子 `Query` の `getSelectSQL()` が返す**完成済み SQL テキスト**を親の WHERE に連結する。子クエリの WHERE 条件は子の `Search` 経由で組み立てられるため、そこには実行時の値が `SQLParser` によってリテラル化されて含まれている。つまりこの経路では、開発者が書いた SQL ではなく実行時の値が文字列として親 SQL に入る。

- A. 対象外のままでよい。子クエリの値も結局は開発者が組み立てた `Search` を通っており、実行時入力が直接ここに入る構成は想定していない
- B. サブクエリ経路のみ SM-1 の判定対象に含める（Q5 の回答を A から C 相当に修正する）。子クエリの値もバインドで運ばれることを要件とする
- C. 対象には含めないが、「サブクエリ経路を使う場合は値がリテラル化される」ことを既知の制限として明文化することを要件とする
- D. まだ決められない — Application Design（2.6）で判断する
- X. Other (please specify)

**議論（2026-08-05T03:56:00Z）** — ユーザーからの確認: 「（B は）対応できるのですか？」

技術的な可否として: **対応可能**。ただし Q1 の決定に依存する。

- JDBC のパラメータ位置は、最終的な SQL 文字列に `?` が現れる順序で決まる。`andIn` 等は子の SQL テキストを親の WHERE の**一点に**まとめて差し込むため、子のバインド値もその位置に連続したブロックとして挿入すれば位置は一致する。並び替えは発生しない。
- 前提は「`Query` が SQL テキストとバインド値を一緒に保持し、外に出せること」。これは SM-1 が主経路に対してすでに要求していることであり、サブクエリ対応はその仕組みの再利用にすぎない。独立した難所ではない。
- 親側は WHERE の `StringBuilder`（`QueryImpl.java:48`）と同じライフサイクルで値リストを持つ必要がある。`QueryImpl` は既に単回使用（`where` がリセットされない）なので、値リストも同じ制約に従えばよく、新たな制約は増えない。
- 後続の連結（`MySQLDao:44` の `SQL_CALC_FOUND_ROWS` 置換、`:46` の LIMIT 追記）は WHERE より後ろに付くため、位置の整合は保たれる。

依存関係と注意点:

- Q1 が B / C（戻り値がパラメータ化 SQL、または値を運ぶ新 API が正）なら、サブクエリ対応はほぼ追加コストなしで従う。
- Q1 が A（既存メソッドはリテラル埋め込み SQL を返し続け、バインドは別経路）の場合、既存の `getSelectSQL()` 経由で子を取ると値はリテラル化されたまま返る。この場合サブクエリの合成は新しい値保持経路側で行う必要があり、ひと手間増える。
- `andExists` / `andNotExists` も同じ形なので、同じ扱いで済む。

[Answer]: B. SM-1 対象に含める
**Mode:** guided | **Timestamp:** 2026-08-05T03:58:00Z
**注記:** この回答により Q5 の回答（A. 現状維持・SM-1 対象外）はサブクエリ経路について部分的に上書きされる。SM-1 の判定対象は「Q5=A が外した raw 経路のうち、サブクエリ経路を除いたもの」となる。requirements.md ではこの上書き関係を明記する。

---

## Q12（フォローアップ）. Q10-A で選択した `OracleDao.searchListForOracle` の扱いは、削除と修正のどちらですか？

Q10 の選択肢 A は「削除または修正を含める」という二択を内包していた。当該メソッドは `Dao.searchList` のオーバーライドではなく（名前が異なる）、リポジトリ内のどこからも呼ばれておらず、`TODO: bugfix` の Javadoc とコメントアウトされたブロックを持ち、未バインドの `?` を含む SQL を `Statement` で実行する。`max` は rownum の構成に使われていない。テストもない（`code-quality-assessment.md` 技術的負債レジスタ #5）。

- A. 削除する。死んだコードであり、Oracle のページングは汎用経路（`Dao.searchList` の行スキップ）で動いている
- B. 修正して有効化する。Oracle の rownum ページングは汎用の全行スキャンより望ましく、本取り組みでバインド化するついでに正す
- C. 現状維持のまま、未バインドの `?` を含む SQL を実行する経路であることを既知の欠陥として記録するにとどめる（Q10-A の選択を撤回する）
- D. まだ決められない
- X. Other (please specify)

[Answer]: B. 修正して有効化
**Mode:** guided | **Timestamp:** 2026-08-05T03:55:00Z

---

## Q13（フォローアップ）. Q3=B で記録するバインド値に、機微データの扱いに関する条件を付けますか？

Q3 で B（SQL テキストに加えてバインド値も記録する）を選択した。`DBAccessManager.getExecutedQuery()` はスレッドローカルの可変リストで、`Dao` 経由で呼び出し側から参照できる。現行はリテラル埋め込み済み SQL を記録しているので機微値は既に含まれうるが、本取り組みは「値を SQL テキストから外す」変更である以上、記録側で改めて平文保持するかどうかは明示的な判断を要する。

- A. 条件を付けない。現行もリテラル埋め込み SQL を記録しており、露出面は増えない
- B. 既定では値を記録せず、明示的に有効化したときのみ記録する（既定オフ）
- C. 常に記録するが、値をマスクする手段（フィルタ等）を差し込めることを要件とする
- D. まだ決められない
- X. Other (please specify)

[Answer]: A. 条件を付けない
**Mode:** guided | **Timestamp:** 2026-08-05T03:55:00Z

---

## Consolidated Summary Confirmation

全 13 問の回答を確定した内容として以下にまとめる。この確認が `Looks correct` になるまで `requirements.md` は生成しない。

### API と観測可能な挙動

| # | 決定 |
|---|------|
| Q1 | `getSelectSQL()` 等の戻り値の扱いは**未決**。Application Design（2.6）で代替案を比較して決める |
| Q2 | `Query.getBlobIndex()` は public のまま現役で残す。他の値もバインドされるため、返す位置は現行と変わりうる |
| Q3 | `DBAccessManager.getExecutedQuery()` は SQL テキストに加えて**バインド値も記録**する |
| Q13 | そのバインド値の記録に機微データ上の条件は**付けない**（現行もリテラル埋め込み SQL を記録しており露出面は増えない、という判断） |

### 値の検証・エスケープ

| # | 決定 |
|---|------|
| Q4 | (1) NUMERIC/FLOAT の型検証は**維持**（非数値は `InvalidParameterException`）、(3) LIKE の `%`/`_` エスケープと `escape` 句も**維持**、(2) クォートエスケープはバインド経路では**適用しない**（raw 経路が残る場合はそこでのみ適用） |

### SM-1 の判定範囲

| # | 決定 |
|---|------|
| Q5 | raw-SQL エスケープハッチ（`where(String)`/`and(String)`/`or(String)`、`Sort.sort(Object,Object)`、`Column.getFunctionName()`、`DataType.FUNCTION`）は現状維持し、SM-1 の判定**対象外**。その旨を文書化する |
| Q11 | ただしサブクエリ経路（`andIn`/`andNotIn`/`andExists`/`andNotExists`）は**例外として SM-1 の対象に含める**。子クエリの値もバインドで運ばれることを要件とする（Q5=A をこの範囲で上書き） |
| Q9 | C-6 の PostgreSQL は**汎用フォールバック経路**として扱う。専用 `Dao`/`Search` の追加は行わない。既存 3 経路（MySQL / Oracle / 汎用）でパラメータ化が成立すればよい |

### テストと検証

| # | 決定 |
|---|------|
| Q6 | SM-2 は「移行後のテストがグリーン」で判定する。既存テストの書き換えは許容し、**旧アサーション → 新アサーションの対応表を成果物として要求**する |
| Q7 | 現在ビルドで実行されていないテストのうち、`QueryImplTest02`（BLOB プレースホルダ）、`QueryImplTest03`、`UserDaoTest2`（Derby 統合経路）を実行対象に含める。さらに `User_test.java:14` のインジェクションプローブを**実行される回帰テスト**として取り込む |
| Q8 | バインド位置と値をアサートできることを、SM-1 の検証手段として**必須**とする（現行 `MockPreparedStatement` は何も記録しないため、この制約の解消が要件に含まれる） |

### 既存欠陥の扱い（`scope-document.md` に対する追加）

| # | 決定 |
|---|------|
| Q10 | 変更経路上の既存欠陥 4 件をすべて本取り組みで扱う |
| Q10-A / Q12 | `OracleDao.searchListForOracle` — **修正して有効化**する（削除ではない）。Oracle の rownum ページングは汎用の全行スキャンより望ましいため |
| Q10-B | `QueryImpl:268` — BLOB カラムだけテーブル修飾が残る不整合を直す |
| Q10-C | `MySQLDao:46` — LIMIT 句の `int` 直接連結を直す |
| Q10-D | `OracleSearch` の `OracleValueConvertFilter` に null ガードを入れる |

### この確認の時点で残る前提・派生事項（requirements.md では明記する）

- Q1 が未決のため、「public API の後方互換性維持」と SM-1 の両立可否は依然として未確認のまま 2.6 に持ち越される（`intent-statement.md` の assumption を継承）。
- Q11=B のコストは Q1 の結論に依存する（Q1=B/C ならほぼ追加コストなし、Q1=A ならサブクエリ合成を新経路側で行う必要がある）。
- Q12=B により Oracle のページング挙動が全行スキャンから rownum 方式に変わる。public API の破壊ではないが、観測可能な挙動の変更である。
- Q7 で `UserDaoTest2`（Derby）を実行対象に含めると、現行で唯一「実 DB エンジンに対する検証がない」という状態が解消される一方、ビルドに Derby が必要になる。
- Q10-C（LIMIT のバインド化）は値位置ではあるがユーザー入力ではないため、SM-1 の達成には必須ではない。品質上の改善として扱う。

**この内容で `requirements.md` を生成してよろしいですか？**

- Looks correct — この回答内容で成果物を生成する
- Request changes — 生成前に 1 つ以上の回答を修正する

[Answer]: Looks correct
**Mode:** guided | **Timestamp:** 2026-08-05T04:00:00Z

---

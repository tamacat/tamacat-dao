<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations

- 2026-08-10T00:00:00Z — （U3）U1 が「どう使うかは U3 が決める」として引き継いだ BLOB 実行の統合問題（DBAccessManager.executeUpdate(PreparedSql) が try-with-resources で即座に close するため、呼び出し側が setBinaryStream で上書きする隙間がない）を、DBAccessManager に専用メソッドを新設する形で解決した; 現行の executeUpdate(String,int,InputStream) が PreparedStatement を呼び出し側に返す形で同じ問題を解いていたことをヒントに、U1の「実行を内部で完結させる」設計方針を維持したまま同じ効果を得る形を選んだ。

- 2026-08-09T00:00:00Z — （U5）`unit-of-work-story-map.md` 実装順序 3「Column.getFunctionName() / DataType.FUNCTION の扱いの確定」を、getFunctionName()（識別子位置）のみ本ステージで扱い、DataType.FUNCTION（値の分類）は U1 が既に確定済みとして再確定しないと解釈した; 実ソースを調べると両者はコード上独立しており、DataType.FUNCTION は U1 の BindSqlBuilder.tokenFor（BR-9）が既に扱っている。

- 2026-08-09T00:00:00Z — 本ステージを Full モード（Steps 1-7）で実行した; エンジンが per-unit の run-stage を直接発行しており、Bolt の QUESTION-ONLY / ARTIFACT-ONLY 分割モードでは呼ばれていないため。
- 2026-08-09T00:00:00Z — `consumes` に列挙されていない `component-dependency.md` / `decisions.md` / `bolt-plan.md` と実ソース（`SQLParser.java` / `Search.java` / `DBAccessManager.java` / `MockDriver.java` / `MockConnection.java` / `MockStatement.java` / `Dao.java` / `QueryImpl.java` / `SQLParserTest.java`）を読んだ; ADR-007 / ADR-012 が本ステージに委ねた 2 点（`setNull` の SQL 型、`?` の走査規則）は、実装レベルの事実がなければ決められないため。
- 2026-08-09T00:00:00Z — `ValueRules` の抽出仕様を `architecture.md` の DataType 表ではなく `SQLParser.java` の実コードと `SQLParserTest.java` のアサーション 30 件から起こした; 「現行の挙動を維持する」（FR-2.1〜FR-2.4）の判定基準は既存テストが固定している出力そのものであるため。

- 2026-08-09T00:00:00Z — （U4）FR-6.4（`OracleValueConvertFilter` の null ガード）と FR-5.2（PostgreSQL 専用クラスを追加しない）を設問にしなかった; 前者は AC-10 が「他 2 実装と同じ結果」を要求しており実装 2 本を読めば形が一意に決まり、後者は不作為の確認で決めることがないため。

- 2026-08-09T00:00:00Z — （U5）Q2 の適用範囲を検討する過程で当初 3 か所（Sort の非 Column 枝、Sort の isFunction 枝、QueryImpl の SELECT 句）に適用するつもりだったが、実ソースを確認すると Sort.java:52 の isFunction 枝は col.getColumnName() を使っており getFunctionName() は使っていなかった。適用箇所を 2 か所に訂正した; 設問を書いた時点の前提を裏取りせずに進めていたら過剰な変更（Sort への不要な追加変更）になっていた。

## Deviations

- 2026-08-10T00:00:00Z — （U3）レビュー iteration 1 で blocking 7 件を検出・是正した; 主な欠陥は (1) U2 に存在しない `whereValues` フィールドを前提にしていた、(2) INSERT/UPDATE のバインドトークンを `"?"` に固定書きし自動タイムスタンプ列（`current_timestamp` のような SQL 関数値）で `?` の個数と値の個数が食い違う欠陥を作っていた、(3) `blobIndex` を独立の `position` カウンタで手計算しており U1 自身の `getBindIndexOf` スキャナ（BI-2 不変条件）を使っていなかった、(4) 存在しない `Dao.query(data)` を捏造していた、(5) WHERE をリテラル `where.toString()` から描画しており値ゼロの述語で `?` と値の個数が食い違っていた、(6) `Dao.executeUpdate(PreparedSql,int,InputStream)` の本体が抜けていた、の 7 件。実ソースの `QueryImpl.java` / `Dao.java` / U1・U2 の確定成果物を読み直して是正した。
- 2026-08-10T00:00:00Z — （U3）レビュー iteration 2 で blocking 3 件を検出・是正した; iteration 1 の是正が `Dao` / `DaoAdapter` 側の設計を縮退させ、隠れていた欠落を露出させた——(1) `Dao.executeUpdate(PreparedSql)`（1 引数版）が宣言も本体も無いまま `create`/`update`/`delete` から呼ばれていた、(2) `DaoAdapter` は `Dao` を継承しない委譲ラッパ（`DaoAdapter.java:26`）であるため `Dao` 側の変更だけではこのリポジトリのどの DAO（すべて `DaoAdapter` を継承）にも新経路が届かない設計だった、(3) `QueryImpl` の `bindSqlBuilder` が 6 箇所で参照されながら宣言が無く、`domain-entities.md` は「新規フィールドなし」と矛盾する記述をしていた。`reviewer_max_iterations: 2` に到達したため、レビュアーの再検証を受けずにオーケストレータが適用した——(1) は § 5.1 に本体を追加、(2) は § 5.2 を新設し `DaoAdapter` に 5 個の独立メンバを追加、(3) は `BindSqlBuilder` を各メソッドのメソッドローカル変数として宣言する形（現行の `SQLParser parser` ローカル変数パターンに倣う）で解消した。非ブロッキング指摘のうち `business-logic-model.md` § 3.1 のヘルパ未共有（N-1）と § 9 の Pr 参照の取りこぼし（N-2）も併せて適用し、N-3/N-5〜N-10 は Build and Test（3.6）または実装フェーズへの申し送りとして未対応のまま記録した。

- 2026-08-09T00:00:00Z — 本ファイルを当初 `construction/bind-foundation/functional-design/memory.md` に作成し、後から正規パス `construction/functional-design/memory.md` へ移した; ディレクティブの `memory_path` と stage file の Learn 節はどちらも `<record>/<phase>/<stage>/memory.md`（unit ディレクトリを挟まない）を指しており、per-unit ステージでも diary はステージ単位で 1 本である。誤った場所のままだと `aidlc-learnings.ts surface --slug functional-design` が候補ゼロを返し、§13 の ritual が diary を拾えなくなる。

- 2026-08-09T00:00:00Z — C-8（mock 拡張）の設計に、`component-methods.md` が列挙していない「テストが `MockPreparedStatement` に到達する経路」を追加した; `MockConnection.prepareStatement` が毎回 `new MockPreparedStatement(this)` を返し呼び出し側に渡らないため、記録機能だけを足しても FR-8.1 のアサートが書けないことが実コードから判明した。
- 2026-08-09T00:00:00Z — `DBAccessManager.executeQuery(PreparedSql)` が生成する `PreparedStatement` のライフサイクルを本ステージで設計した; 2.6 の `component-methods.md` M-5 は生成と実行のみを述べており、現行 `executeQuery(String)` が ThreadLocal にキャッシュした単一 `Statement` を使い回すのに対し新経路は毎回新規生成になるという差分が扱われていない。

- 2026-08-09T00:00:00Z — 2.6 契約からの差分を「追加 N 点」と要約せず、新規 public メンバを 1 個ずつ ID 付きで列挙する形に変えた; レビュアー iteration 1 の唯一の blocking 指摘が「見出しの件数が自分自身の表と食い違い、もう一方の成果物では過少申告になっている」であり、件数を要約する形式そのものが欠陥の温床だった。
- 2026-08-09T00:00:00Z — 同じ差分を 2 つの成果物に書いたことが食い違いの原因だった; `domain-entities.md` 側を「型に関する差分のみ」とスコープし、「全量は `business-logic-model.md` § 10。食い違ったら § 10 が正」という優先順位を明記して解消した。

- 2026-08-09T00:00:00Z — （U2）2.6 の `component-methods.md` M-2 が `whereValues` をリテラルの `where` と対にしていたが、ADR-003 により `where` はリテラルを保持し続けるため `?` の個数が値と一致せず `PreparedSql.of` が必ず落ちる; 上流の表を額面どおりに実装すると動かない設計だった。実ソースの `QueryImpl.java:48` と `:442-453` を読んで初めて判明した。
- 2026-08-09T00:00:00Z — （U2）`component-methods.md` M-2 の `addWhere(String condition, Param param)` は 2 引数では足りない; リテラル面とバインド面が別物である経路（`addSearch`、`andIn` 等）が存在するため、3 引数の内部形が要る。宣言されたシグネチャは薄いラッパとして残せるため、契約の破棄ではなく補完で済んだ。
- 2026-08-09T00:00:00Z — （U2）`andOuterJoin(Table, Search)` が `Search` のリテラルテキストを WHERE ではなく **FROM / JOIN 句**に埋めていた（`QueryImpl.java:350-356`）; `component-dependency.md` の順序表にも `unit-of-work.md` の U2 所有一覧にも現れない経路であり、値が FROM 句に出る以上、結合順は `FROM 句の値 ++ WHERE の値` になる。

- 2026-08-09T00:00:00Z — （U2）「実ソースで確認」と明記した数値を実際には数えていなかった; `Query.java` のメソッド数を 30 と書いたが実測は 35 で、上流 `component-methods.md` M-1 の「25 メソッド」（それ自体が `select` / `addUpdateColumn(s)` のオーバーロード圧縮による計数誤り）を未検証で転記したのが原因。「24 + 6 = 30」という自己検算が内部で辻褄が合っていたため誤りが隠れた。
- 2026-08-09T00:00:00Z — （U2）現行が「していない」と断じる前に該当箇所を読んでいなかった; `DaoAdapter` が `executeQuery(String)` を転送していないと書いたが、実際は `DaoAdapter.java:176-178` で転送していた。その誤った前提を根拠にバインド版の転送を省く決定をしており、`unit-of-work-story-map.md` が AC-11 の判定根拠として `executeQuery(PreparedSql)` を `DaoAdapter` への追加メンバに挙げていることとも矛盾していた。

- 2026-08-09T00:00:00Z — （U4）`component-methods.md` M-7 が指示する「LIMIT を `" limit ?,?"` にしてバインドする」を採らなかった; U1 が ADR-007 で全値を `setString` に固定しているのに対し MySQL の LIMIT パラメータは整数でなければならず、クライアントサイド prepared statement では `limit '0','5'` に展開されて構文エラーになる。上流の指示を額面どおり実装すると動かない設計だった。
- 2026-08-09T00:00:00Z — （U4）`OracleDao.searchList(Query,int,int)` の override を設計に加えた; `unit-of-work.md` / `component-methods.md` は「`searchListForOracle` を修正して有効化する」としか書いていないが、AC-9 の Given が「`searchList(query, start, max)` が呼ばれる」であり、override がなければ AC-9 を判定できない。
- 2026-08-09T00:00:00Z — （U4）`PreparedSql` の合成ヘルパを `Dao` に共通化せず 2 クラスに重複させた; 共通化は `unit-of-work.md` U4 の「汎用経路に触れない」境界を破り、新規 protected メンバとして AC-11 の判定対象を増やすため。3 行の重複を受け入れ、乖離は BR-4 と両方言への同一テストで押さえる。

## Tradeoffs

- 2026-08-09T00:00:00Z — `hasUnboundPlaceholders()` の走査で判定不能（引用符が閉じない）な場合を「未束縛なし」に倒す案を推奨した; 誤検出は既存の動作する呼び出しを `DaoException` で落とす回帰（NFR-3 違反）になるのに対し、検出漏れはドライバのパラメータ未設定報告に落ちるだけで現状と同じであるため、非対称なコストを検出漏れ側に寄せた。
- 2026-08-09T00:00:00Z — `setNull` の SQL 型を DataType ごとの意味に沿って割り当てる案を推奨した; ADR-007 が値に `setString` を選んだ根拠は「現行の DB 側の型変換を維持する」ことであり、NULL には変換すべきテキストがないためこの根拠が及ばない。

- 2026-08-09T00:00:00Z — （U4）合成した `PreparedSql` の由来（checked / unchecked）を保つ分岐を入れた; 保たないと外部 `Query` 実装 ＋ `where(Param)` の組み合わせで方言経路だけ `InvalidParameterException`（`IllegalArgumentException` 派生）になり、汎用経路の `DaoException` と継承関係がないため利用側の catch をすり抜ける。3 行の分岐で例外型と失敗位置を汎用経路に揃えるほうが、診断可能性の一貫性という利得に見合う。
- 2026-08-09T00:00:00Z — （U4）MySQL が LIMIT を付ける条件（`start > 0 && max > 0`）を広げなかった; W-2 / W-3 で MySQL と Oracle の挙動が揃わない不整合が残るが、条件を広げると現行と異なる SQL が生成され NFR-5 に対するリスクを負う。FR-6.3 は連結方法の是正であって付与条件の変更ではない、と読んだ。

- 2026-08-09T00:00:00Z — （U4）レビュー iteration 1 で blocking 2 件を検出・是正した; B-1 は `wrapRownum` が `for update` を内側と外側の両方に残し実行不能な SQL を作る欠陥（現行の死んだコードが既に同じ欠陥を持っていたが FR-6.1 で生きた経路になるため免責されない）で、`core` から剥がして最外側に 1 回だけ付け直す形に直した。B-2 は `domain-entities.md` の窓定義が「MySQL と Oracle で導出式が同一」と書きながら実装は W-2/W-3 で MySQL が offset を適用しない非対称を持っていた食い違いで、状態表を「実効窓」に改め非対称を明示の残存リスク R-6 として記録した。

- 2026-08-09T00:00:00Z — （U4）レビュー iteration 2 で blocking 1 件を検出・是正した; iteration 1 の B-2 是正パスが W-2 の非対称を正しく直す一方で W-3 に新しい誤りを持ち込んでいた（MySQL の `MySQLDao.java:60-61` のコールバック打ち切りが `max>0` だけで独立に発火することを見落とし、「W-3 は打ち切りが起きない」と逆に書いていた）。`reviewer_max_iterations: 2` に達したため、iteration 2 の指摘（blocking 1 件、non-blocking 8 件）はレビュアーの再検証を受けずに適用した。W-3 の実効窓を `[1, max]`（両方言一致）に訂正し、R-6 の適用範囲を W-2 のみに絞った。

- 2026-08-09T00:00:00Z — （U5）レビュー iteration 1 で blocking 4 件、iteration 2 で blocking 3 件を検出・是正した; 主な欠陥は (1) IdentifierRules を package-private と誤設計しコンパイル不能だった、(2) QueryImpl のコード引用が実ソースと構造的に異なっていた、(3) AC-10b の Given 3 位置のうちテーブル名・カラム名を検証対象外にする判断の根拠が不正確だった（技術的に不可能ではなくスコープの決定であるべきだった）、(4) Column.isFunction()==true かつ getFunctionName()==null という実在する状態に対し検証が誤って例外を投げる設計だった。`reviewer_max_iterations: 2` 到達後、iteration 2 の指摘はレビュアーの再検証を受けずに適用した。

## Open questions

- 2026-08-09T00:00:00Z — `MockDriver.acceptsURL` が URL を問わず常に `true` を返す（`MockDriver.java:37-39`）; FR-8.3 / OQ-6 で Derby を classpath に加えると `DriverManager` の登録順によって取得されるドライバが変わりうる。Build and Test（3.6）で確認を要する。
- 2026-08-09T00:00:00Z — `SQLParser.value` に 1 値の `BETWEEN` を渡すと `#{value2}` トークンが置換されずに残る（現行はそれごとクォートして `'a and #{value2}'` を出力する）; 現行時点で壊れた SQL であり本取り組みの回帰ではないが、バインド版でも同じ形で壊れることを固定するテストは置いていない。

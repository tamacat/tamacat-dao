# Unit of Work Story Map — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

- **`stories.md`**（user-stories, 2.4）— 本ワークフローのスコープで **SKIP** のため存在しない。したがって本文書は「ユーザーストーリー → Unit」の対応を作れない。代わりに `requirements.md` の **機能要件（FR）と受け入れ条件（AC）** を割り当ての単位として用いる。これは `requirements.md` 冒頭が「`team-practices` / `stories.md` は SKIP のため存在しない。要件は `requirements.md` から直接引き継ぐ」と記録した扱いを、本ステージでも一貫させたものである。
- **`requirements.md`**（requirements-analysis, 2.3）— FR-1〜FR-8、NFR-1〜NFR-7、AC-1〜AC-11、CON-1〜CON-8。本文書が割り当てる対象。
- **`components.md`**（application-design, 2.6）— 「FR とコンポーネントの対応」表。本文書の割り当てはこの表を Unit 単位に集約したものであり、コンポーネント割り当てと矛盾しない。
- **`component-methods.md`**（同上）— AC が検証する対象メソッドの所在。
- **`component-dependency.md`**（同上）— AC-3b / AC-7 が依存する順序の不変条件（値リストの分離）。
- **`services.md`**（同上）— サービス不在の記録。ユーザー向けの画面や API を持たないため、ストーリーは元々「利用側 Java 開発者の API 体験」としてしか書けない性質のものである。
- **`decisions.md`**（同上）— ADR-002 / ADR-003 が求めた FR-7.1 / FR-7.2 / NFR-1 の更新を本ステージで反映済み。本文書の割り当ては更新後の記述に従う。

---

## ストーリー不在の扱い

ユーザーストーリーがないことは本文書の欠落ではなく、**上流の意図的な SKIP の帰結**である。ストーリーを本ステージで新規に捏造しない。

代わりに次の 2 層で割り当てを行う。

1. **FR → Unit** — 何を作るかの割り当て。網羅性は「FR の全小項目がちょうど 1 つの Unit（または明示された後続ステージ）に対応する」ことで検証する。
2. **AC → Unit** — 完了をどう判定するかの割り当て。AC は Given/When/Then で書かれており、ストーリーの受け入れ条件と同じ役割を果たす。

NFR は Unit 横断（cross-cutting）であるため別節で扱う。

---

## FR → Unit のマッピング

| FR | 要件 | 優先度 | 担当 Unit |
|---|---|---|---|
| FR-1.1 | 単一値の比較条件のバインド | Must | **U2** |
| FR-1.2 | LIKE 条件のバインド | Must | **U2** |
| FR-1.3 | IN / BETWEEN の各値を独立したバインド変数に | Must | **U2** |
| FR-1.4 | INSERT VALUES / UPDATE SET のバインド | Must | **U3** |
| FR-1.5 | BLOB（`DataType.OBJECT`）のバインド | Must | **U3** |
| FR-1.6 | サブクエリ経路の値のバインド | Must | **U2** |
| FR-1.7 | 識別子位置の注入不能化 | **Should** | **U5** |
| FR-2.1 | NUMERIC / FLOAT の型検証を維持 | Must | **U1** |
| FR-2.2 | バインド経路でクォートエスケープを適用しない | Must | **U1** |
| FR-2.3 | LIKE の `%` / `_` エスケープと `escape 'X'` 句の現行挙動維持 | Must | **U1** |
| FR-2.4 | DATE / TIME の空値・`NULL`・`current_timestamp` の現行挙動維持 | Must | **U1** |
| FR-3.1 | public API の後方互換性 | Must | **U2**（`Query` の `default` 追加と非推奨）＋ **U3**（`Dao` の互換シム） |
| FR-3.2 | `getBlobIndex()` がバインドパラメータ位置を返す | Must | **U3** |
| FR-3.3 | 旧 `getSelectSQL()` 等の戻り値契約の確定 | Must | **U2** |
| FR-4.1 | 実行記録にバインド値を含める | Must | **U1** |
| FR-4.2 | 記録に機微データ上の追加条件を設けない | Must | **U1** |
| FR-5.1 | MySQL / Oracle / 汎用の 3 経路すべてで FR-1 が成立 | Must | **U4**（汎用経路は U2 / U3） |
| FR-5.2 | PostgreSQL は汎用フォールバックとして扱う | Must | **U4**（専用クラスを追加しないという不作為の確認） |
| FR-5.3 | 方言ごとの `Conditions` が同じ述語を生成 | Must | **U4**（生成規則は U1 の `BindSqlBuilder`） |
| FR-6.1 | `OracleDao.searchListForOracle` の修正と有効化 | Must | **U4** |
| FR-6.2 | `QueryImpl:268` の SET 句テーブル修飾の不整合 | Must | **U3** |
| FR-6.3 | `MySQLDao:46` の LIMIT の `int` 直接連結 | Should | **U4** |
| FR-6.4 | `OracleValueConvertFilter` の null ガード | Must | **U4** |
| FR-7.1 | raw 経路の現行挙動維持と SM-1 対象外の範囲 | Must | **U2** |
| FR-7.2 | FR-7.1 の各経路と代替経路の文書化 | Must | **U2** |
| FR-7.3 | `prepare()` + `where(Param)` の代替経路の提供（2.7 で追加） | Must | **U2** |
| FR-8.1 | バインド位置と値をテストからアサートできること | Must | **U1** |
| FR-8.2 | 旧⇔新アサーションの対応表 | Must | **U2 / U3 / U4** が該当行を起こし、**Build and Test（3.6）** が集約 |
| FR-8.3 | `QueryImplTest02` / `QueryImplTest03` / `UserDaoTest2` を実行対象に含める | Must | **Build and Test（3.6）**（アサート修正は U3） |
| FR-8.4 | `User_test.java:14` のインジェクションプローブを回帰テスト化 | Must | **Build and Test（3.6）**（期待挙動 `InvalidParameterException` は U1 の FR-2.1 が実現） |
| FR-8.5 | LIKE 候補枯渇時の挙動を固定するテスト | Should | **Build and Test（3.6）**（対象実装は U1 の `ValueRules.escapeLike`） |

### 網羅性の検証

- **すべての FR 小項目（31 件）が割り当て済みである。** 未割当は 0 件。内訳は FR-1 が 7、FR-2 が 4、FR-3 が 3、FR-4 が 2、FR-5 が 3、FR-6 が 4、FR-7 が 3、FR-8 が 5。`components.md` で「**未割当**」と記されていた FR-1.7 は U5 に割り当てられた。FR-7.3 は本ステージで `requirements.md` に追加された新規要件であり、U2 に割り当てた。
- **すべての Unit が 1 つ以上の FR を持つ。** 各 Unit がマッピング表に現れる行数で数えると U1 = 7、U2 = 10、U3 = 6、U4 = 7、U5 = 1。FR を持たない Unit は存在しない。合計は 7+10+6+7+1 = **31** で FR 総数と一致する。FR-3.1（U2 ＋ U3、+1）と FR-8.2（U2 / U3 / U4 ＋ 3.6、+2）による重複計上の +3 が、どの Unit にも割り当てのない FR-8.3 / FR-8.4 / FR-8.5（Build and Test 3.6 のみが担当、−3）とちょうど相殺するためである。
- **後続ステージに送った 4 件（FR-8.2 の集約 / FR-8.3 / FR-8.4 / FR-8.5）は、いずれも送り先を明示している。** すべて Build and Test（3.6）であり、本スコープで EXECUTE である（`aidlc-state.md` の Stages to Execute に 3.6 が含まれる）。取りこぼしにはならない。

---

## AC → Unit のマッピング

`requirements.md` の受け入れ条件を、それを満たす Unit に割り当てる。**AC は Unit の完了判定に使う。**

| AC | 検証する内容 | 担当 Unit | 判定できる時点 |
|---|---|---|---|
| AC-1 | STRING の EQUAL — SQL に `?`、バインド値が渡した値と一致 | **U2** | U2 完了時 |
| AC-2 | LIKE_PART `"a%b_c"` — `escape` 句とエスケープ規則が現行どおり | **U2**（規則は U1） | U2 完了時 |
| AC-3 | NUMERIC の IN 3 値 — `(?,?,?)` と順序 | **U2** | U2 完了時 |
| AC-3b | INSERT VALUES / UPDATE SET — カラム並び順どおりのバインド値 | **U3** | U3 完了時。**不変条件 1（値リスト分離）を検出する唯一の AC** |
| AC-4 | サブクエリ — 子の値が差し込み位置に対応 | **U2** | U2 完了時 |
| AC-5 | NUMERIC に `"';select * from dual --'"` → `InvalidParameterException` | **U1** | U1 完了時（回帰テスト化は FR-8.4 で 3.6） |
| AC-6 | シングルクォートを含む値 — `''` への二重化が起きない | **U1** | U1 完了時（経路としての確認は U2） |
| AC-7 | OBJECT ＋ STRING 2 列の UPDATE — `getBlobIndex()` がバインド位置を返す | **U3** | U3 完了時 |
| AC-8 | 実行直後の `getExecutedQuery()` に SQL、記録にバインド値 | **U1** | U1 完了時 |
| AC-9 | Oracle の `searchList` — rownum ページング、未バインド `?` なし、`start` と `max` の両方を使用 | **U4** | U4 完了時 |
| AC-10 | Oracle の `ValueConvertFilter` に null — NPE なし、他 2 実装と同じ結果 | **U4** | U4 完了時 |
| AC-10b | 識別子位置の構文文字 — 例外拒否またはメタデータ照合 | **U5** | U5 完了時。機構は 3.1 が決める |
| AC-11 | 変更前にコンパイルされたサブクラスが再コンパイルなしでロードできる | **横断（U2 ＋ U3）** | U3 完了時。`DaoAdapter.java` への追加が両 Unit に分かれているため揃うまで判定できない |

### AC の網羅性

- **AC-1〜AC-11（AC-3b / AC-10b を含む 13 件）すべてが割り当て済み。**
- **AC を持たない Unit は U1 を除いて存在しない**——U1 は AC-5 / AC-6 / AC-8 を持つ。
- **AC-11 は単独の Unit では判定できない。** 理由は `Query` インターフェースへの `default` 追加ではない——AC-11 が対象とする利用側サブクラスは `DaoAdapter` を継承するものであり、`DaoAdapter` は `Query` を実装していない（`DaoAdapter.java:26` は `implements AutoCloseable` のみ）。したがって `Query` への `default` 追加は `DaoAdapter` サブクラスの二進互換性に影響しない。実際の理由は、**U2 と U3 がそれぞれ `DaoAdapter.java` 自身に新しい public / protected メソッドを直接追加する**ことにある（U2: `prepare(...)` / `executeQuery(PreparedSql)` / `search` / `searchList`、U3: `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` / `getDeletePreparedSql(T)` / `executeUpdate(PreparedSql)`）。両方が揃って初めて「既存サブクラスが無変更で動く」ことを確認できる。これを横断項目として次節に再掲する。

---

## Unit をまたぐ要件（cross-cutting）

次の要件は特定の Unit に閉じない。**すべての Unit がこれを壊さないことを各自の完了条件に含める必要がある。**

| ID | 内容 | どの Unit も守るべきこと |
|---|---|---|
| NFR-1 | **実行経路**に値の文字列連結が 0 件（ADR-003 により本ステージで判定の読みを更新） | 新しく作る実行経路にリテラル埋め込みを持ち込まない。ただし旧 API 経由で到達できるリテラル生成経路は判定対象外 |
| NFR-2 | CodeQL で SQL インジェクションの指摘 0 件 | 残存するリテラル連結経路（`SQLParser`）が指摘される可能性がある。測定方法は OQ-7 として Build and Test（3.6）に残る |
| NFR-3 | public API に破壊的変更がなく、既存サブクラスが再コンパイルなしで動作 | **既存の public / protected シグネチャを 1 つも削除・変更しない。追加のみ。** AC-11 で判定 |
| NFR-4 | Java 8 を維持（CON-1） | post-8 の言語機能・API を使わない。`Query` の `default` メソッドは Java 8 の機能であり適合する |
| NFR-5 | 変更後の全テストがグリーン | Q7 = B により、各 Unit は自分が壊したテストを自分で直して完了する |
| NFR-6 | テスト件数が変更前（138 件）を下回らない | 各 Unit はアサートを書き換えるとき件数を減らさない |
| NFR-7 | 性能目標は設定しない | 最適化（`PreparedStatement` キャッシュ等）を持ち込まない（OOS-2） |
| CON-3 | v2.0 ブランチ上でローカルのみ。`git push` しない | すべての Unit に適用される作業制約 |
| CON-6 | 新規の第三者依存を追加しない | U1 の mock 拡張が既存スタックの拡張である理由（ADR-009） |

**AC-11（再掲）**: 「変更前にコンパイルされた `DaoAdapter` サブクラスを、変更後の jar に差し替えて再コンパイルなしでロードでき、`NoSuchMethodError` / `NoSuchFieldError` が発生しない」——U2 と U3 がそれぞれ `DaoAdapter.java` にメソッドを追加するため、両方が揃うまで判定できない横断的な受け入れ条件である。

---

## Unit 内の実装順序

Unit **内部**の順序を示す。Unit **間**の順序（どの Unit を先に出荷するか）は Delivery Planning（2.8）の担当であり、ここでは扱わない。

### U1 `bind-foundation`

1. `BindValue`（C-3）— 他の型が保持する最小単位
2. `Param`（C-1）/ `PreparedSql`（C-2）— `?` 個数と値個数の整合検査を含む
3. `ValueRules`（C-4）— `SQLParser` から規則を切り出す
4. `SQLParser`（M-6）を `ValueRules` へ委譲化 — **既存 `SQLParserTest` がここで挙動不変を検証する。以降の作業はこの検証済みの土台の上で行う**
5. `BindSqlBuilder`（C-5）— `ValueRules` を使って `Param` を作る
6. mock スタック拡張（C-8）— `MockConnection.prepareStatement` の SQL 保持と `MockPreparedStatement` の位置・値記録
7. `PreparedStatementBinder`（C-6）— **6 の完了後でなければ正しさを確認できない**
8. `ExecutedStatement`（C-7）と `DBAccessManager`（M-5）の `PreparedSql` 版実行メソッド、`hasUnboundPlaceholders()` の実行前検査

**6 を 7 より先に置く理由**: `PreparedStatementBinder` の責務は「値を 1 始まりの位置に順に適用する」ことだけであり、その正しさを確認する手段は mock の記録機能しかない。逆順にすると 7 が検証されないまま完了する。

### U2 `select-path`

1. `Query`（M-1）に `default` メソッド 8 個を宣言し、旧 5 メソッドに `@Deprecated` を付与 — **U3 が override を足せる土台を先に作る**
2. `Search`（M-3）の 3 状態と private `append(...)` — 同期規則を構造で守る
3. `Search.getSearchParam()`（public）の追加
4. `QueryImpl`（M-2）の `whereValues` と `addWhere(String, Param)` / `addSearch`
5. `QueryImpl.getSelectPreparedSql()`
6. `andIn` / `andNotIn` / `andExists` / `andNotExists`（FR-1.6）— 5 の完成後、子の `getSelectPreparedSql()` を使う
7. `Dao` / `DaoAdapter`（M-4）の `prepare(...)` / `executeQuery(PreparedSql)` / `search` / `searchList`
8. 移行（Q7 = B）: `UserDao` / `FileDataDao` / `MySQLDaoTest` の `param()` → `prepare()`、`SearchTest` / `QueryImplTest` へのバインド版アサート追加、`UserDaoTest` の SELECT 経路アサート書き換え、FR-8.2 対応表の該当行

**2 を 3 より先に置く理由**: `getSearchParam()` が返す `Param` は `bindSearch` と `bindValues` の対である。同期規則が構造として先に立っていなければ、アクセサが不整合な状態を露出しうる。

### U3 `write-path`

1. `QueryImpl` に `setValues` / `insertValues` を追加 — **U2 の `whereValues` と 1 : 1 の規則を踏襲する（ADR-011）**
2. `getInsertPreparedSql(T)` / `getUpdatePreparedSql(T)` — `setValues ++ whereValues` の結合順
3. FR-6.2（`:268` の OBJECT ブランチに `.replaceFirst(tableName + ".", "")`）— バインド版・リテラル版の両方
4. `getDeletePreparedSql(T)` / `getDeleteAllPreparedSql(Table)`
5. `getBlobIndex()` のバインド版契約（`getBindIndexOf(DataType.OBJECT, 1)` のキャッシュ）
6. `Dao` / `DaoAdapter` の新 protected 拡張点と互換シム、`executeUpdate(PreparedSql)`、`create` / `update` / `delete` の切り替え
7. 移行: `QueryImplTest` の書き込み系バインド版アサート、`UserDaoTest` の INSERT / UPDATE / DELETE 経路アサート書き換え、`QueryImplTest02/03` の BLOB プレースホルダ位置修正、FR-8.2 対応表の該当行

**1 を最初に置く理由**: この Unit の最大の失敗様式（全ての値が 1 つずつずれる UPDATE）は値アキュムレータの持ち方そのものに起因する。2 以降を先に書くと、後から値リストの分離に直すのは全面的な書き直しになる。

### U4 `dialects`

1. `OracleSearch.OracleValueConvertFilter` の null ガード（FR-6.4）— 他の作業と独立、最も小さい
2. `MySQLDao.searchList` の `PreparedSql` 化と LIMIT のバインド（FR-6.3）
3. `OracleDao.searchListForOracle` の修正・有効化（FR-6.1）— rownum の `?` をバインドし、`max` をページング範囲の決定に使う
4. 移行: `MySQLDaoTest` の LIMIT バインドアサート、FR-8.2 対応表の該当行

**3 が最後である理由**: `searchListForOracle` は現在リポジトリのどこからも呼ばれておらずテストもない。有効化は観測可能な挙動の変更（全行スキャン → rownum ページング）を伴うため、他の 2 件が済んで方言経路が安定してから行う。

### U5 `identifier-safety`

1. **機構の決定**（Functional Design 3.1）— (a) 例外による拒否か (b) メタデータ照合か
2. `Sort.sort(Object, Object)` の非 `Column` キー経路への適用
3. `Column.getFunctionName()` / `DataType.FUNCTION` の扱いの確定
4. AC-10b を判定するテストの追加

**1 が本ステージで決まっていない。** この Unit は設計が未完のまま Construction に入る唯一の Unit であり、3.1 がその設計を新規に行う。`application-design` は `Sort` を「変更しないコンポーネント」として扱っているため、参照できる既存設計がない。

---

## 割り当ての検証まとめ

| 検証項目 | 結果 |
|---|---|
| すべての FR 小項目が Unit または明示された後続ステージに割り当てられている | ✅ 31 件すべて。未割当 0 件 |
| すべての AC が Unit に割り当てられている | ✅ 13 件すべて |
| すべての Unit が 1 つ以上の FR を持つ | ✅ 表への出現行数で U1=7 / U2=10 / U3=6 / U4=7 / U5=1（FR-3.1 と FR-8.2 が複数 Unit にまたがる） |
| すべての Unit が 1 つ以上の AC を持つ | ✅ U1=3 / U2=4 / U3=2（＋AC-11 横断）/ U4=2 / U5=1 |
| Unit をまたぐ要件が明示されている | ✅ NFR-1〜NFR-7、CON-3、CON-6、AC-11 |
| 後続ステージに送った要件の送り先が明示されている | ✅ FR-8.2 の集約 / FR-8.3 / FR-8.4 / FR-8.5 → Build and Test（3.6、本スコープで EXECUTE） |
| ストーリー不在の扱いが記録されている | ✅ 本文書冒頭。`stories.md` は user-stories（2.4）の SKIP により存在しない |

# NFR Requirements Questions — U1 `bind-foundation`

Unit: **U1 `bind-foundation`**（kind: `library`）
Stage: NFR Requirements（3.2）/ Construction
Depth: Standard、Test Strategy: Standard（`aidlc-state.md`）

## Sources

- `business-logic-model.md`（functional-design 3.1, U1）— § 7.3 の実行記録、§ 8 の mock 拡張、§ 10 の 2.6 契約からの差分（A-8〜A-12 が production jar の public 表面に載ること）
- `business-rules.md`（同上）— BR-27〜BR-30（実行記録）、BR-33〜BR-37（mock）、BR-9（`DataType.FUNCTION` の raw 経路）
- `requirements.md`（requirements-analysis 2.3）— NFR-1〜NFR-7、CON-1（Java 8）、CON-6（**compile スコープ**の第三者依存）、CON-8（CodeQL は `master` のみ）、FR-4.1 / FR-4.2、OQ-4（本ステージが解く）、OQ-7（3.6 が解く）
- `technology-stack.md`（codekb）— カバレッジ計測ツールの不在、surefire 2.22.2 の `**/*Test.java`、ビルド JVM が JDK 25 Corretto、`org.tamacat:tamacat-core` と `javax.json` のみが compile スコープ
- `org.md` `## Testing Posture` — 80% line coverage を課す scope の列挙（`sql-parameterization` は**含まれない**）

**produces が 2 件である理由**: stage file は 5 件を宣言するが、`produces_kinds` により performance / scalability / reliability は `service` / `ui` kind 限定である。U1 の kind は `library`（`unit-of-work.md`「デプロイモデル」— tamacat-dao はデプロイ可能なプロセスを 1 つも持たない）。エンジンのディレクティブも `security-requirements` と `tech-stack-decisions` の 2 件だけを返している。

設問は 3 件。`requirements.md` が本ステージに委ねた OQ-4（Q1）と、U1 の設計が新たに作り出した 2 つの面（Q2 / Q3）に絞った。

---

## Q1. `org.md` の「line coverage 80% 以上」を適用するか、計測手段を入れるか（OQ-4）

**`requirements.md` が本ステージに委ねた点**: 「`org.md` が課す『line coverage 80% 以上』を本取り組みに適用するか。現行リポジトリにはカバレッジ計測ツールが一切なく、138 件という数字は件数であって被覆率ではない。計測手段の導入可否を含む」

**事実**

| 項目 | 状態 | 根拠 |
|---|---|---|
| `org.md` の 80% floor が課される scope | `mvp` / `enterprise` / `feature` / `infra` | `org.md` `## Testing Posture` |
| 本ワークフローの scope | **`sql-parameterization`** — 上の列挙に**含まれない** | `aidlc-state.md` |
| カバレッジ計測ツール | **一切なし**（JaCoCo も Cobertura もプラグインもしきい値もない） | `technology-stack.md`「Testing Stack」 |
| 現行の代替指標 | NFR-6「テスト件数 >= 138」（**件数であって被覆率ではない**） | `requirements.md` NFR-6 |

**CON-6 は JaCoCo を阻まない。** CON-6 の文面は「**compile スコープ**の第三者依存は `tamacat-core` と `javax.json` のみという現状を前提とする」であり、掛かっているのは compile スコープの依存である。JaCoCo は Maven の**ビルドプラグイン**であり compile スコープにも test スコープにも入らず、生成される jar にも入らない。CON-1（Java 8）も生成物のバイトコードと使用 API に掛かる制約であり、ビルドツールには掛からない。

```xml
<!-- 追加する場合の形（surefire 2.22.2 は argLine を尊重する） -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version><!-- ビルド JVM が JDK 25 Corretto のため、JDK 25 を解析できる版を選ぶ --></version>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
  </executions>
</plugin>
```

- **A.** 適用しない。計測ツールも入れない。NFR-6（件数 >= 138）を唯一の量的指標として維持する
- **B.** JaCoCo を追加して**計測と可視化だけ**行う。しきい値によるビルド失敗は設けない
- **C.** JaCoCo を追加し、**U1 が新設するクラスに限って** 80% line coverage をビルドゲートにする（`ValueRules` / `BindSqlBuilder` / `PreparedStatementBinder` / `Param` / `PreparedSql` / `BindValue` / `ExecutedStatement` / `LikeEscape`）。既存クラスは対象外（**推奨**）
- **D.** リポジトリ全体に 80% line coverage のゲートを掛ける
- **X.** Other (please specify)

論点: `org.md` の 80% は本 scope に**課されていない**ため、A は規約違反ではない。しかし本取り組みはセキュリティ改修であり、FR-8.1 が「バインド位置と値をテストからアサートできること」を Must にしている以上、検証の量的な裏付けがまったくないまま進むのは弱い。C は risk のある新規コードにだけゲートを掛け、レガシーには課さない。D は既存コードにカバレッジ規律がないため初回から失敗する見込みが高い。

**JDK 25 のリスク**: ビルド JVM は JDK 25 Corretto である（`technology-stack.md`）。JaCoCo は解析対象のクラスファイル版に追随が必要で、版が古いと JDK 25 でエージェントが落ちうる。B / C / D を選ぶ場合、この検証は Build and Test（3.6）が行う。

[Answer]: C（JaCoCo を追加し、U1 の新規 8 クラスに限って 80% line coverage をビルドゲートにする）— 2026-08-09、**Mode:** guided

---

## Q2. mock の記録機能が production jar に載ることを、どう扱うか

**U1 の設計が新たに作り出した面である。** 2.6 は mock 拡張を決めたが、記録された値が**どこから読めるか**は規定していなかった。

**事実**

```java
// MockDriver.java:21-27 — static 初期化子が自分自身を DriverManager に登録する
static {
    try { DriverManager.registerDriver(new MockDriver()); }
    catch (SQLException e) { e.printStackTrace(); }
}

// MockDriver.java:37-39 — URL を問わず常に true を返す
public boolean acceptsURL(String url) throws SQLException { return true; }

// MockDriver.java:41-43 — 常に同じ MockConnection を返す
public Connection connect(String url, Properties info) { return connection; }
```

`org.tamacat.mock.sql` は `src/main` にあり **production jar に同梱される**（`decisions.md` ADR-009 の Negative、`requirements.md` OOS-6 が移動をスコープ外とした）。

**U1 が加える差分**: 変更前の `MockPreparedStatement` は `setXxx` がすべて空実装で、**何も記録していなかった**。変更後は SQL とバインド値を記録し、Q3 = A の決定により `MockDriver.getInstance()` から共有 `MockConnection` に到達できる（`business-logic-model.md` § 10 の A-8〜A-12）。

**露出の性質**: `DBAccessManager.getExecutedQuery()` / `getExecutedStatements()` は `ThreadLocal` であり、別スレッドからは自分の（空の）リストしか見えない。一方 **`MockConnection` の記録はインスタンス単位で全スレッド共有**であり、`MockDriver.getInstance()` を呼べるコードならスレッドを問わず読める。この点で mock の記録は `DBAccessManager` の記録より露出が広い。

現実的なリスクは標的型攻撃より**事故**である——`acceptsURL` が常に `true` を返すため、mock クラスがクラスパスに載ったまま `MockDriver` がロードされると、`DriverManager` が実ドライバではなく mock を返しうる。その状態で本番相当のコードが走ると、平文のバインド値が共有リストに際限なく積まれる。

- **A.** 現状を受け入れる。OOS-6 に従い mock の移動はスコープ外であり、記録機能も無条件に公開する
- **B.** 記録をシステムプロパティで**既定オフ**にする。`org.tamacat.mock.sql.record=true` のときだけ `setXxx` が記録し、テストは surefire の `systemPropertyVariables` で有効化する（**推奨**）
- **C.** `MockDriver.getInstance()` を追加せず、3.1 の Q3 = A を撤回して `DriverManager` 経由（新規 API ゼロ）に戻す
- **X.** Other (please specify)

論点: B は追加のみで NFR-3 を満たし、FR-8.1 のアサートも surefire 側の 1 行で成立する。一方、既定オフの設定ノブが 1 つ増え、テスト作成者がそれを知らないと「記録が空」で詰まる。A は最も手数が少ないが、U1 が持ち込んだ新しい露出面をそのまま残す。C は 3.1 の決定の巻き戻しであり、`acceptsURL` が常に `true` である以上 Derby 併存時の取得ドライバが登録順に依存するという 3.1 の懸念（BR-37）が戻る。

### Q2 補足（2026-08-09、ユーザーからの「影響やリスクは？」に対する回答）

**記録される中身（影響の大きさ）**: バインド値は利用側アプリケーションのクエリパラメータそのものである。WHERE の値（ユーザー ID、検索語）に加え、U3 以降は INSERT / UPDATE の書き込む列値も通る。本リポジトリ自身のフィクスチャが `User.PASSWORD` を書いている（`UserDaoTest.java:37`）。資格情報や個人データが載りうる面である。

**到達経路（起こりやすさ）— 実コードで 3 経路を確認した**

| 経路 | 判定 | 根拠 |
|---|---|---|
| `ServiceLoader` による自動登録 | **成立しない** | `src/main/resources/META-INF/` に `services/java.sql.Driver` が存在しない（`MANIFEST.MF` のみ）。`DriverManager` の `ServiceLoader` は `MockDriver` を自動ロードしない。static 初期化子（`MockDriver.java:21-27`）は明示的なクラスロード時にしか走らない |
| 設定ミス | **成立する** | `DriverManagerJdbcConfig.loadDriver()` が `ClassUtils.forName(getDriverClass())` を呼ぶ（`:76-82`）。本番の `db.xml` に `org.tamacat.mock.sql.MockDriver` が残っていると登録され、`acceptsURL` が常に `true`（`MockDriver.java:37-39`）のため `DriverManager.getConnection` が mock を返しうる |
| JVM 内で既にコードが動く | **成立する** | `MockDriver.getInstance()` で共有 `MockConnection` に到達し、**スレッドを跨いで**全記録を読める |

**較正した重大度**: 経路 1 は閉じている。経路 2 は「偽 DB に対して動いている」時点で既に破綻した状態であり、U1 はそこにデータ保持を上乗せするだけである。経路 3 は JVM 内でのコード実行という高いハードルだが、**U1 が唯一新しく広げるのはここである**——`DBAccessManager` の記録は `ThreadLocal` で他スレッドからは空に見えるのに対し、`MockConnection` の記録はインスタンス単位で全スレッド共有である。

したがって **U1 が加える限界リスクは実在するが限定的**である。「jar がデータを漏らす」のではなく、「mock がロードされた場合に限り、以前は何も保持していなかったものが平文のパラメータ値を共有リストに保持するようになる」である。

**選択肢 C の再評価**: `getInstance()` を撤回しても `DriverManager.getConnection("jdbc:...")` は同じ共有 `MockConnection` を返すため、読もうとする側は依然到達できる。**C は見かけほど塞がない**一方、3.1 の決定の巻き戻しと BR-37 の懸念復活というコストは確実に払う。

**選択肢 D を追加する。**

- **D.** 記録は無条件に行うが、`MockConnection` の記録リストを **`ThreadLocal`** にする。設定ノブを増やさずに、mock の露出をライブラリが既に持っている水準（`DBAccessManager` の `ThreadLocal` 記録と同じ）まで下げる

  ```java
  // MockConnection
  private final ThreadLocal<List<MockPreparedStatement>> prepared = ThreadLocal.withInitial(ArrayList::new);
  public PreparedStatement prepareStatement(String sql) {
      MockPreparedStatement ps = new MockPreparedStatement(this, sql);
      prepared.get().add(ps);
      return ps;
  }
  public List<MockPreparedStatement> getPreparedStatements() { return prepared.get(); }
  public MockPreparedStatement getLastPreparedStatement() { ... }
  public void clearPreparedStatements() { prepared.get().clear(); }
  ```

  経路 3 の「スレッドを跨いで読める」という U1 固有の広がりが消える。テストは DAO と同じスレッドで走るためアサートは成立し、`clearPreparedStatements()` がスレッド単位になることでテスト分離はむしろ良くなる。`ThreadLocal` はテストスレッド上に残るが、surefire の fork 終了で解放される。

  **比較対象の訂正（レビュー指摘 F-1、2026-08-09）**: 当初この項に「`getExecutedQuery()` が既に同じデータをスレッドごとに保持しているため経路 2 の限界リスクは実質ゼロ」と書いたが、**これは誤りである**。バインド経路では `getExecutedQuery()` は `?` 入りの SQL テキストのみを保持し、バインド値を一切含まない（BR-28）。値の平文を保持する同格の記録は `getExecutedStatements()`（同じく `ThreadLocal`）である。したがって D の効果は「mock の記録が `getExecutedStatements()` と同じスレッドスコープに収まる」ことであり、「値の平文がプロセス内に存在する」こと自体は消えない（それは R-4 として別途受容している）。**結論（D を採る）は変わらない。** 詳細は `security-requirements.md` R-7 の比較表を参照。

[Answer]: D（`MockConnection` の記録リストを `ThreadLocal` 化する）— 2026-08-09、**Mode:** guided

---

## Q3. `getExecutedStatements()` が `InputStream` を無期限に保持してよいか

**これも U1 の設計が新たに作り出した面である。**

**事実**

```java
// 現行 DBAccessManager.java:181-188 — ThreadLocal のリスト。release() は削除せず shutdown() だけが remove する
public List<String> getExecutedQuery() { ... }

// U1 が追加するもの（business-logic-model.md § 7.3、BR-30）
protected ThreadLocal<List<ExecutedStatement>> executedStatements = new ThreadLocal<>();
private void record(PreparedSql sql) {
    getExecutedQuery().add(sql.getSql());
    getExecutedStatements().add(new ExecutedStatement(sql.getSql(), sql.getValues()));
}
```

`ExecutedStatement` は `List<BindValue>` を保持し、`BindValue` は `OBJECT` 型のとき **`InputStream` を保持する**（`domain-entities.md` C-3）。したがって記録に積むと、その `InputStream`（およびそれが包むファイルハンドルやバッファ）が**スレッドが終わるまで解放されない**。

現行の `getExecutedQuery()` は `String` しか保持しないためこの問題を持たない。**U1 が `ExecutedStatement` を追加したことで初めて生じる。** アプリケーションサーバのようにスレッドを使い回す環境では、`shutdown()` が呼ばれるまで積み上がる。

- **A.** 上限は設けない（`getExecutedQuery()` と成長特性を揃える）が、**`ExecutedStatement` は `InputStream` を保持しない**。`OBJECT` の値は「バイナリ値がこの位置にあった」ことを示す標識だけを記録する（**推奨**）
- **B.** A に加えて記録件数に上限を設ける（直近 N 件のみ保持し、古いものから捨てる）
- **C.** 現状踏襲。`BindValue` をそのまま保持し、`InputStream` も保持する
- **X.** Other (please specify)

論点: NFR-7（性能目標を設定しない、最適化を持ち込まない）と OOS-2 があるため、B の上限は「最適化」に見えるが、A の `InputStream` 非保持は最適化ではなく**資源保持の欠陥の回避**である。A を採ってもテストからバインド値をアサートする能力（FR-8.1）は失われない——`OBJECT` の値はもともと文字列表現を持たず（`BindValue.getValue()` は `OBJECT` で null）、位置のアサートは `getBindIndexOf` と mock の記録が担う。C は現行との一貫性は最も高いが、ストリームを握り続ける。

[Answer]: A（上限なし・`InputStream` 非保持。`OBJECT` は標識だけを記録）— 2026-08-09、**Mode:** guided

**FR-4.1 の範囲を狭める決定であることを明示する。** FR-4.1 は「その実行に渡されたバインド値も記録すること」と述べており、字義どおりなら BLOB のバイト列も記録対象になる。しかし `InputStream` の内容を記録するには**ストリームを読む必要があり、読めばそのストリームは消費されてバインドが壊れる**。したがって選択肢は「参照を保持し続ける（資源保持の欠陥）」か「標識だけを記録する」かの 2 つしかなく、FR-4.1 を `OBJECT` に対して字義どおり満たすことは技術的に不可能である。A はこの制約に対する誠実な解であり、`security-requirements.md` に FR-4.1 の範囲限定として記録する。AC-8 の判定には影響しない——AC-8 のシナリオは「値を持つ条件で SELECT が実行された直後」であり、SELECT の WHERE 述語に `OBJECT` は現れない。

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。3 件とも単一の選択肢が確定している。

**矛盾**: なし。ただし **3.1 の成果物に対する修正が 2 件生じる**（下表 3 / 4）。

| # | 組み合わせ | 確認内容 | 判定 |
|---|---|---|---|
| 1 | Q1=C ＋ CON-6 / CON-1 | JaCoCo は Maven ビルドプラグインであり compile / test いずれのスコープにも入らず jar にも入らない。CON-1 は生成物のバイトコードと使用 API に掛かる | **矛盾なし。** どちらの制約も掛からない |
| 2 | Q1=C ＋ `org.md` `## Testing Posture` | `org.md` の 80% は `mvp` / `enterprise` / `feature` / `infra` に課される。本 scope `sql-parameterization` は列挙外 | **矛盾なし。** C は org.md が課していない水準を**自発的に**、しかも新規クラスに限定して課すものであり、広い方針に反しない |
| 3 | Q2=D ＋ 3.1 の BR-34 / BR-36 | BR-36 は「`MockConnection` は全テストで共有されるため記録はテスト間で蓄積する」と述べる。D により蓄積はスレッド単位になる | **3.1 への修正。** `clearPreparedStatements()` の必要性は変わらない——surefire は fork ごとに単一スレッドでテストを走らせるため、同一スレッド上のテスト間では依然蓄積する。BR-34（同位置は後勝ち）は `MockPreparedStatement` 単位の規則であり影響を受けない |
| 4 | Q3=A ＋ 3.1 の § 7.3 / `domain-entities.md` C-7 | 3.1 は `new ExecutedStatement(sql.getSql(), sql.getValues())` と書いており `BindValue` をそのまま保持する（`OBJECT` では `InputStream` を含む） | **3.1 への修正。** `ExecutedStatement` は `InputStream` を保持しない形に改める |
| 5 | Q3=A ＋ FR-4.1 / AC-8 | FR-4.1 を `OBJECT` に対して字義どおり満たすのは技術的に不可能（ストリームを読めば消費されバインドが壊れる） | **範囲限定。** `security-requirements.md` に記録する。AC-8 のシナリオに `OBJECT` は現れないため判定に影響しない |
| 6 | Q3=A ＋ NFR-7 / OOS-2 | 「最適化を持ち込まない」に抵触しないか | **矛盾なし。** 上限を設けない（B を採らない）ことで成長特性は `getExecutedQuery()` と揃う。`InputStream` 非保持は最適化ではなく資源保持の欠陥の回避 |

**3.1 の成果物は編集しない。** functional-design / bind-foundation はレビュアーの READY 受領（`REVIEW_COMPLETED`）が有効であり、`produces[]` 成果物への書き込みはその受領を無効化する。上表 3 / 4 の修正は本ステージの `security-requirements.md` に「3.1 の規則に対する修正」として記録し、後段が参照できる形で残す。3.1 の成果物は修正前の記述を保持する。

**追加の設問**: 不要。

---

## Consolidated Summary Confirmation

**Prompt**: この内容で `security-requirements.md` / `tech-stack-decisions.md` を生成してよいか。

**Options**:
- Looks correct — この回答から成果物を生成する
- Request changes — 生成前に回答を修正する

**回答の要約**

| # | 決定 | 帰結 |
|---|---|---|
| Q1 | **C** — JaCoCo を追加し、U1 の新規 8 クラスに限って 80% line coverage をビルドゲートにする | OQ-4 を解決。`tech-stack-decisions.md` に唯一のツール追加として記録。JDK 25 での動作確認は 3.6 に引き継ぐ |
| Q2 | **D** — `MockConnection` の記録リストを `ThreadLocal` 化する | 3.1 の BR-36 を修正。設定ノブを増やさず、mock の露出を `DBAccessManager` の既存水準まで下げる |
| Q3 | **A** — 上限なし・`InputStream` 非保持 | 3.1 の § 7.3 / C-7 を修正。FR-4.1 の範囲を `OBJECT` について限定し、根拠を記録する |

[Answer]: Looks correct — 2026-08-09、**Mode:** guided

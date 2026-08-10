# NFR Requirements Questions — U2 `select-path`

Unit: **U2 `select-path`**（kind: `library`）
Stage: NFR Requirements（3.2）/ Construction
Depth: Standard、Test Strategy: Standard（`aidlc-state.md`）

## Sources

- `business-logic-model.md`（functional-design 3.1, U2）— § 2（`Search` の 3 状態）、§ 3（`QueryImpl` の 2 状態）、§ 4.2（値の結合順）、§ 7.3（`andOuterJoin` の 3 つの入口）、§ 8（`Dao` の実行経路）、§ 10（2.6 契約からの差分と新規 public / protected メンバ）、§ 11-7（同期テストを 3.6 に引き継ぐ）
- `business-rules.md`（同上）— BR-1 / BR-3 / BR-5（同期規則）、BR-12（非推奨 `andOuterJoin` はリテラルのまま）、BR-13（キー不在時に何もしない欠陥の温存）、BR-19（値ゼロの `Param`）、BR-20（移行対象）、F 節の R-1〜R-6、H 節の失敗様式 1〜6
- `requirements.md`（requirements-analysis 2.3）— NFR-1（判定基準は「**実行経路**に値の文字列連結が 0 件」）、NFR-3 / NFR-4 / NFR-5 / NFR-6、FR-7.1（SM-1 対象外の raw 経路の**閉じた列挙**）、FR-7.2（文書化。Must）、FR-7.3、CON-1 / CON-6、OQ-4（U1 で解決済み）
- `technology-stack.md`（codekb）— Maven / compiler 3.8.1（source・target 1.8）/ surefire 2.22.2、compile スコープ依存 3 件、ビルド JVM が JDK 25 Corretto、カバレッジ計測ツールの不在
- U1 `bind-foundation` の `tech-stack-decisions.md` — TSD-1〜TSD-5、とくに TSD-3（JaCoCo を U1 の新規 8 クラスに限ってゲート化）
- 実ソース — `QueryImpl.java:350-356`（`andOuterJoin`）、`Query.java:101`、`src/test/java/org/tamacat/dao/test/UserDao.java` / `FileDataDao.java`

**produces が 2 件である理由**: U1 と同じく U2 の kind は `library`（`unit-of-work.md` U2 の表）であり、`produces_kinds` により performance / scalability / reliability は `service` / `ui` 限定である。エンジンのディレクティブも `security-requirements` と `tech-stack-decisions` の 2 件だけを返している。

**U1 と U2 で問いの性質が変わる。** U1 は新規クラス 8 個を足す Unit だったため、問いは「新しく作る面をどう閉じるか」だった。U2 は新規クラスを 1 つも足さず（`WhereFragment` は private static の内部型）、既存クラス 5 個（`Query` / `QueryImpl` / `Search` / `Dao` / `DaoAdapter`）を書き換える Unit である。したがって問いは「**既存の経路のうち、どれをバインド化の対象に含めるか**」と「**書き換えの正しさをどう保証するか**」に移る。

設問は 4 件。

---

## Q1. 非推奨 `andOuterJoin(Table, Search)` の値を、実行される SQL にリテラルのまま残してよいか

**これは U2 の設計と `requirements.md` の間の抵触である。** 3.1 は BR-12 でこの経路を「SM-1 対象外」と宣言したが、**その免除を与えている上流要件が存在しない。**

### 事実

現行実装は `Search` のリテラル文字列を FROM 句に連結する。

```java
// QueryImpl.java:350-356（実ソース。行番号は実測）
@Override
public Query<T> andOuterJoin(Table tab1, Search search) {
    if (outerJoinTables.containsKey(tab1)) {
        outerJoinTables.put(tab1, outerJoinTables.get(tab1) + " and " + search.getSearchString());
    }
    return this;
}
```

3.1 § 7.3 が定めるバインド側の扱いは次のとおりである。

```java
putOuterJoin(tab1, literal,
    Param.of(prev.getSql() + " and " + search.getSearchString(), prev.getValues()));
    //                                 ^^^^^^^^^^^^^^^^^^^^^^^^ 値が埋め込まれたリテラルテキスト
```

`getSearchString()` は**値をリテラルとして埋め込んだテキスト**である。これが `bindOuterJoinTables` に入り、`getSelectPreparedSql()` の FROM 句（§ 4.2 の `buildFromClause(true, values)`）を通って `Dao.search` → `DBAccessManager.executeQuery(PreparedSql, ...)` → `PreparedStatement` に到達する。

### 免除の根拠が上流にない

| 確認 | 結果 |
|---|---|
| NFR-1 の判定基準 | 「**実行経路**に値の文字列連結が 0 件」。`Dao.search` から `PreparedStatement` に至る経路が対象 |
| NFR-1 の除外 | 「FR-7.1 の raw 経路の**直接利用**」と「FR-1.7（識別子位置）」の 2 つのみ |
| FR-7.1 の列挙 | `where(String)` / `and(String)` / `or(String)` の直接利用、`Sort.sort(Object,Object)` の非 `Column` キー、`Column.getFunctionName()`、`DataType.FUNCTION`。**`andOuterJoin` は含まれない** |
| FR-1 の列挙 | 1.1 単一値 / 1.2 LIKE / 1.3 IN / 1.4 INSERT / 1.5 BLOB / 1.6 サブクエリ / 1.7 識別子。**`andOuterJoin` は含まれない** |

すなわち `andOuterJoin(Table, Search)` は**どの要件にも明示的に現れない空白**にあり、3.1 が単独で「SM-1 対象外」と決めた。空白であること自体は 2.3 の見落としだが、**その空白をどちらに倒すかは本ステージが決める**。

### Q5 = A の根拠がこの経路には当てはまらない

FR-7.1 の免除理由は「渡されるのは開発者が書いた SQL であり、実行時のユーザー入力ではない」（Q5 = A）である。しかし `andOuterJoin` が受け取るのは `String` ではなく **`Search`** であり、`Search.and(Column, Conditions, String... values)` が運ぶのは**実行時の値**である。

これは 2.6 の追跡 F3 が `Dao.param(...)` について発見したのとまったく同じ構図である——`param()` も「開発者が書いた SQL」ではなく実行時の値を運んでいたため、FR-7.1 の範囲から外して FR-7.3 の代替経路を用意した（ADR-002）。**同じ理由がこの経路にも成立する。**

### 選択肢

- **A.** BR-12 のまま。バインド面にもリテラルテキストを積み、実行される SQL に値が埋め込まれることを受容する。NFR-1 の除外リストに `andOuterJoin(Table, Search)` を追加する要件レベルの修正を本ステージで記録し、FR-7.2 の文書化対象に加える
- **B.** バインド面だけを正す。リテラル面（`outerJoinTables`）は `getSearchString()` のまま維持し、バインド面（`bindOuterJoinTables`）は **`search.getSearchParam()`** を使う（**推奨**）

  ```java
  @Deprecated
  @Override
  public Query<T> andOuterJoin(Table tab1, Search search) {
      if (outerJoinTables.containsKey(tab1)) {
          Param prev = bindOuterJoinTables.get(tab1);
          Param add  = search.getSearchParam();          // ← ? 付きテキスト ＋ 値
          List<BindValue> merged = new ArrayList<>(prev.getValues());
          merged.addAll(add.getValues());
          putOuterJoin(tab1,
              outerJoinTables.get(tab1) + " and " + search.getSearchString(),  // 旧経路は不変
              Param.of(prev.getSql() + " and " + add.getSql(), merged));
      }
      return this;
  }
  ```

  **旧 API の観測可能な挙動は変わらない**——`getSelectSQL()` が返す文字列はリテラル面から作られるため 1 文字も変わらず、`BR-10` を満たす。変わるのは `getSelectPreparedSql()`（U2 が新設する API であり、保存すべき旧挙動を持たない）の出力だけである。追加コストは実質ゼロで、`getSearchParam()` は § 2.4 で public として既に存在し、値の合成は新設の `andOuterJoin(Table, Param)`（§ 7.3）と同一の 4 行である
- **C.** 非推奨版が値を持つ `Search` を受け取ったとき（`getSearchParam().getValues()` が空でないとき）に `InvalidParameterException` で拒否する。代替経路 `andOuterJoin(Table, Param)` への移行を強制する
- **X.** Other (please specify)

論点: A は 3.1 の記述をそのまま通すが、**セキュリティ改修の成果物が「バインドされない値が実行 SQL に残る経路」を自ら新設する**ことになる——現行では `getSelectSQL()` にしか現れなかったものが、新しい `getSelectPreparedSql()` にも現れる。B はそれを実質ゼロコストで閉じ、しかも BR-10（旧戻り値の不変）を破らない。C は最も安全だが、`Query` の非推奨メソッドが実行時例外を投げるようになる点で ADR-006 が排した「外部実装が新経路で必ず失敗する」形に近づく。

**呼び出し元は 0 件である**（`grep -rn "andOuterJoin" src/` の結果は `Query.java:101` の宣言と `QueryImpl.java:351` の実装のみ）。したがって A / B / C いずれを選んでもリポジトリ内のテストは動かない。判断は「ライブラリが外部利用者に対して何を保証するか」だけで決まる。

[Answer]: B（バインド面だけ正す。リテラル面は `getSearchString()` のまま維持し、`bindOuterJoinTables` には `search.getSearchParam()` を使う）— 2026-08-09、**Mode:** guided

---

## Q2. カバレッジゲート（U1 の TSD-3）を U2 に拡張するか

**U1 の決定をそのまま適用できない。** TSD-3 は `<element>CLASS</element>` に**新規 8 クラスを列挙する**形でゲートを組んだ。U2 は**新規クラスを 1 つも足さない**——`WhereFragment` は private static の内部型であり、変更対象は既存の 5 クラスである。

### 事実

| 項目 | 状態 |
|---|---|
| U2 が変更するクラス | `Query`（interface、`default` 9 個追加）、`QueryImpl`、`Search`、`Dao`、`DaoAdapter` |
| U2 が新設するクラス | **なし**（`WhereFragment` は `QueryImpl` の private static 内部型） |
| これらの既存クラスの現在の被覆率 | **不明**（U1 が JaCoCo を入れるまで計測手段が存在しない） |
| `org.md` の 80% floor | 本 scope `sql-parameterization` には課されていない（U1 Q1 で確認済み） |
| U2 で最も危険な失敗様式 | BR-1 の同期破れ＝**述語の欠落**。行は実行されるため **line coverage では検出できない**（`business-rules.md` H 節 1） |

### 選択肢

- **A.** 拡張しない。TSD-3 のゲート対象は U1 の新規 8 クラスのままとし、U2 は Q3 が定める同期検証を負う（**推奨**）
- **B.** 変更した 5 クラスをゲート対象に加える（しきい値は 80%）
- **C.** 5 クラスをゲート対象に加えるが、既存の未被覆コードを考慮して低いしきい値（例: 60%）にする
- **D.** JaCoCo の `<element>METHOD</element>` を使い、U2 が追加・変更したメソッドだけをゲート対象にする
- **X.** Other (please specify)

論点: A の根拠は 2 つある。(1) この 5 クラスは既存レガシーであり、U2 の変更部分だけを分離して測る手段が JaCoCo にはない——B / C はいずれも「U2 と無関係な既存コードの被覆率」をゲートに載せることになり、数字がゲートの意味を持たない。(2) **line coverage は U2 の主要な失敗様式を検出しない**。`append` を呼び忘れた経路は「行が実行されない」のではなく「別の行が実行される」ため、被覆率は 100% でも述語は欠落する。ここに投じる規律は Q3 の同期検証であって被覆率ではない。

D は原理的には可能だが、`<include>` にメソッドシグネチャを列挙する形になり、実装中にシグネチャが変わるたびに POM を直すことになる。しきい値の意味も薄い（新規メソッドの多くは 3〜5 行の委譲である）。

[Answer]: A（拡張しない。TSD-3 のゲート対象は U1 の新規 8 クラスのまま）— 2026-08-09、**Mode:** guided

---

## Q3. BR-1 / BR-3 の同期破れ（U2 最大の失敗様式）を、何で保証するか

**`business-rules.md` H 節 1 が「U2 で最も危険な失敗様式」と名指ししたものである。**

### 事実

3 状態（`search` / `bindSearch` / `bindValues`）または 2 状態（`where` / `bindFragments`）のうち片方だけを更新する経路が 1 つでもできると:

| 検査 | 検出するか |
|---|---|
| `Param.of(...)` の個数検査（U1 BR-24） | **しない**。両方を append し忘れた場合、`?` の個数と値の個数は一致したままである |
| `PreparedSql.of(...)` の個数検査 | **しない**。同上 |
| JDBC のパラメータ数検査 | **しない**。同上 |
| line coverage | **しない**（Q2 の論点） |
| 既存テスト（`SearchTest` / `QueryImplTest`） | **しない**。これらはリテラル面（`getSearchString()` / `getSelectSQL()`）だけをアサートしており、BR-10 によりその戻り値は不変である |

症状は「**WHERE 条件が緩い SQL が正常に実行される**」——例外も警告も出ず、意図より多い行が返る。認可条件が述語で表現されている利用側アプリケーションでは、これは行の漏洩そのものである。

3.1 は「構造で守る」（BR-1 / BR-3 / BR-5 の 3 つの private メソッド）ことを求め、**検証は § 11-7 で Build and Test（3.6）に引き渡した**。本ステージは、その引き渡しを「後で誰かが考える」ではなく**判定可能な要件**に固定する。

### 選択肢

- **A.** 構造（3 つの private メソッド）＋ 3.6 への**必須検証項目**として固定する。検証の形も本ステージで指定する: 同一の `Search` / `QueryImpl` からリテラル面とバインド面の両方を取得し、**トップレベルの述語数と連結子の並びが一致すること**をアサートするテストを、`and` / `or` / `and(Search)` / `or(Search)` / `join` / `where(Search,Sort)` / サブクエリ 4 種のそれぞれについて置く（**推奨**）
- **B.** A に加えて、`getSelectPreparedSql()` の中に実行時の突き合わせを入れる。リテラル面 `where` の述語数とバインド面 `bindFragments.size()` が一致しなければ `DaoException` を投げる
- **C.** A に加えて、テスト専用の診断メソッド（`Search` / `QueryImpl` に package-private の `isSynchronized()`）を置き、テストがそれを呼ぶ
- **D.** 構造のみ。検証の形は 3.6 に完全に委ねる（3.1 の記述のまま）
- **X.** Other (please specify)

論点: A は追加の実行時コストも API 表面もゼロで、失敗様式に正面から対応する唯一のコストが「テストを書くこと」である。Test Strategy が Standard（コンポーネントあたり 5-8 テスト）であることとも整合する。

B は最も強いが 3 つの問題がある。(1) すべてのクエリ実行に文字列走査のコストが乗り、**NFR-7 / OOS-2**（最適化も含め性能特性を触らない）の趣旨に照らして正当化しにくい。(2) リテラル面の「述語数」を数えるには `where` の文字列をパースする必要があり、その走査器自体が新しい欠陥源になる。(3) 本番で発火したときの挙動が「クエリが実行時例外で落ちる」になり、ライブラリのバージョンアップで既存アプリが壊れうる。

C は API 表面（package-private だが `org.tamacat.dao` と `org.tamacat.dao.impl` は別パッケージなので実際には public 相当が必要になる。§ 2.4 の `getSearchParam()` が public になったのと同じ理由）を増やす。A のテストは既存の public アクセサ（`getSearchString()` と `getSearchParam()`、`getSelectSQL()` と `getSelectPreparedSql()`）だけで書けるため、C の追加は不要である。

D は 3.1 の記述のままだが、**本ステージの役目は「後段に送る」ものを判定可能な形にすることである**。D を選ぶと、この失敗様式は誰の判定基準にも現れない。

[Answer]: A（構造 ＋ 3.6 への必須検証項目として、述語数と連結子の並びの一致テストを判定可能な形に固定する）— 2026-08-09、**Mode:** guided

---

## Q4. FR-7.2（Must）の文書化を、どの形の成果物にするか

**FR-7.2 は Must であり、`unit-of-work.md` は U2 の担当 FR に含めている。** しかしその成果物の形は上流のどこにも書かれていない。

### 事実

| 項目 | 状態 |
|---|---|
| FR-7.2 の要求 | (a) FR-7.1 の各経路が呼び出し側の SQL テキストをそのまま連結し SM-1 対象外であること、(b) FR-7.3 の代替経路の存在、(c) 旧経路から新経路への移行方法——の 3 点を「利用者が参照できる形で」文書化する |
| リポジトリの現状 | ルートに `README` も `docs/` も**存在しない**（`ls` の結果は `pom.xml` / `src` / `target` / `aidlc` のみ） |
| 既存の配布物 | `maven-source-plugin` によるソース jar。Javadoc jar は生成していない（`technology-stack.md`） |
| 文書化対象になる経路 | FR-7.1 の 4 経路 ＋ 非推奨 6 メソッド ＋ Q1 の結論次第で `andOuterJoin(Table, Search)` ＋ R-5（外部 `Query` 実装で `default` がリテラルにフォールバックすること） |

### 選択肢

- **A.** Javadoc のみ。該当する各メソッドの `@deprecated` タグと通常の Javadoc に、SM-1 対象外である旨と代替経路を書く（**推奨**）
- **B.** Javadoc ＋ リポジトリルートに移行ガイド（`MIGRATION.md` など）を新設し、旧経路 → 新経路の対応表を 1 か所にまとめる
- **C.** A に加えて `maven-javadoc-plugin` を追加し、Javadoc jar を生成物に含める
- **D.** 本ステージでは形を決めず、Build and Test（3.6）に委ねる
- **X.** Other (please specify)

論点: A は追加のビルド設定も新規ファイルもなしに (a) と (b) を満たし、**非推奨メソッドを呼んだ利用者の IDE にその場で表示される**——「利用者が参照できる形」として最も到達率が高い。弱いのは (c)（移行方法）で、メソッド単位の断片に分かれるため全体像が見えない。

B はその弱点を埋めるが、リポジトリに文書ファイルを置く慣行が現状ないため、置いた文書が更新されずに腐るリスクがある。C は配布物の構成を変える決定であり、CON-6 は掛からない（ビルドプラグイン）が、`technology-stack.md` が記録する現行の配布構成を変えることになる。

なお **FR-8.2 の「旧アサーション → 新アサーション対応表」は別物である**——あれはテストの等価性を示す内部成果物であり、集約先は 3.6 である（`business-rules.md` BR-20）。FR-7.2 は利用者向けの文書であり、混同しない。

[Answer]: B（Javadoc ＋ リポジトリルートに移行ガイドを新設し、旧経路 → 新経路の対応表を 1 か所にまとめる）— 2026-08-09、**Mode:** guided

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。4 件とも単一の選択肢が確定している。

**定量目標の欠落**: Q2 = A により U2 には被覆率の数値目標が存在しない。これは欠落ではなく**意図的な不設定**であり、代わりに Q3 = A が「対象メソッドごとに述語数一致テストを置く」という**数えられる**判定基準を置く。NFR-6（テスト件数 >= 138）も引き続き掛かる。

**矛盾**: 直接の矛盾はない。ただし **Q1 = B により 3.1 の成果物に対する修正が 3 件生じる**（下表 1〜3）。

| # | 組み合わせ | 確認内容 | 判定 |
|---|---|---|---|
| 1 | Q1=B ＋ 3.1 `business-rules.md` BR-12 | BR-12 は「非推奨 `andOuterJoin(Table, Search)` はリテラルのまま残す。この経路は SM-1 の対象外」と述べる | **3.1 への修正。** B はバインド面を `getSearchParam()` に切り替えるため、この経路は SM-1 の対象に**入る**。リテラル面（`outerJoinTables`、および `getSelectSQL()` の出力）は不変であり BR-10 は満たされる |
| 2 | Q1=B ＋ 3.1 `business-logic-model.md` § 7.3 | § 7.3 の非推奨版は `Param.of(prev.getSql() + " and " + search.getSearchString(), prev.getValues())` と書く | **3.1 への修正。** `search.getSearchParam()` を使い、値を `prev.getValues()` に連結する形に改める（Q1 の選択肢 B のコード片） |
| 3 | Q1=B ＋ 3.1 `business-rules.md` F 節 R-1 | R-1「非推奨 `andOuterJoin(Table, Search)` の値は FROM 句にリテラルとして残る。受容」 | **3.1 への修正。** R-1 は解消する。残存リスクではなくなる |
| 4 | Q1=B ＋ BR-10（旧戻り値の不変） | `getSelectSQL()` はリテラル面から作られる（§ 4.1 の `buildFromClause(false, ...)` は `outerJoinTables` を使う） | **矛盾なし。** リテラル面に触れないため戻り値は 1 文字も変わらない |
| 5 | Q1=B ＋ BR-13（キー不在時に何もしない） | `outerJoinTables.containsKey(tab1)` の条件は維持されるか | **矛盾なし。** B のコード片は条件をそのまま保つ。R-2（欠陥の温存）は変わらない |
| 6 | Q1=B ＋ BR-6 / BR-8（値の結合順） | FROM 句の値が増えるため結合順の規則が効く場面が実際に生じる | **矛盾なし。** BR-8 が「テキスト組み立てと同一のループで収集する」ことを既に定めており、`bindOuterJoinTables` の `Param` が値を持つようになるだけである。むしろ BR-6 / BR-8 が**空振りでなくなる** |
| 7 | Q2=A ＋ `org.md` `## Testing Posture` | 80% floor は本 scope `sql-parameterization` に課されていない（U1 Q1 で確認済み） | **矛盾なし。** A は義務を外していない |
| 8 | Q3=A ＋ NFR-7 / OOS-2 | 実行時検査（B）を採らないため性能特性に触れない | **矛盾なし。** A の追加はテストのみ |
| 9 | Q3=A ＋ NFR-6（件数 >= 138） | テストを追加する方向であり件数は増える | **矛盾なし** |
| 10 | Q4=B ＋ CON-3（ローカルのみ、`git push` しない） | 新規ファイルをリポジトリに置く | **矛盾なし。** CON-3 が禁じるのは push であってコミットではない |
| 11 | Q4=B ＋ Q1=B | 移行ガイドの記載対象から「`andOuterJoin(Table, Search)` は SM-1 対象外」の行が消える | **整合させる。** 非推奨であることと代替経路（`andOuterJoin(Table, Param)`）の案内は残り、「対象外」の注記だけが不要になる |

**3.1 の成果物は編集しない。** functional-design / select-path は `reviewer_max_iterations` 到達後の適用記録を持ち、その記述が後段の参照先である。上表 1〜3 の修正は本ステージの `security-requirements.md` に「3.1 の規則に対する修正」として記録し、後段が参照できる形で残す。U1 が同じ扱いをした先例に従う。

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
| Q1 | **B** — 非推奨 `andOuterJoin(Table, Search)` のバインド面を `getSearchParam()` に切り替える | 3.1 の BR-12 / § 7.3 / R-1 を修正。実行 SQL に値がリテラルで残る経路が 1 つ閉じる。旧 API の戻り値は不変 |
| Q2 | **A** — カバレッジゲートを U2 に拡張しない | TSD-3 のゲート対象は U1 の 8 クラスのまま。規律は Q3 に移す |
| Q3 | **A** — 構造 ＋ 3.6 への必須検証項目 | 述語数・連結子の一致テストを、対象メソッドを名指しした判定可能な要件として固定する |
| Q4 | **B** — Javadoc ＋ ルートに移行ガイド | FR-7.2 を成果物の形で確定。`tech-stack-decisions.md` に新規ファイルの追加として記録 |

[Answer]: Looks correct — 2026-08-09、**Mode:** guided

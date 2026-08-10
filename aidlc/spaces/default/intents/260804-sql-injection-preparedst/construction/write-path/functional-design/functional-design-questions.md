# Functional Design Questions — U3 `write-path`

Unit: **U3 `write-path`**（kind: `library`）
Stage: Functional Design（3.1）/ Construction
Depth: Standard（`aidlc-state.md`）

## Sources

- `unit-of-work.md` — U3 の責務「INSERT / UPDATE / DELETE をバインド経路に切り替える。既存サブクラスが無変更で動き続ける互換シムを設ける」、境界（SELECT 経路・方言クラスには触れない）、依存 U1・U2、実装上の制約（既定実装は `PreparedSql.ofLiteral(...)` で包む・ADR-004、`getBlobIndex()` の新旧で意味が異なる、旧経路の BLOB SQL は `hasUnboundPlaceholders()` で拒否・ADR-012）、最大の設計リスク（`getUpdateSQL` が SET → WHERE の順でテキスト連結するが値リストを 1 本にすると全ての値が 1 つずつずれる）。
- `unit-of-work-story-map.md` — U3 が担う FR-1.4 / 1.5 / 3.2 / 6.2、判定する AC-3b / AC-7、Unit 内の実装順序 1〜7。
- `requirements.md` — FR-1.4（INSERT VALUES / UPDATE SET のバインド）、FR-1.5（BLOB のバインド）、FR-3.2（`getBlobIndex()`）、FR-6.2（`QueryImpl:268` のテーブル修飾）、AC-3b、AC-7、NFR-1 / NFR-3、CON-1。
- `component-methods.md` — M-2（`QueryImpl` の値アキュムレータ表、FR-6.2 の修正指示）、M-4（`Dao` / `DaoAdapter` の追加・変更・不変）。
- **U1 `bind-foundation` の 3.1 成果物** — `Param` / `PreparedSql` / `BindValue` の契約、`PreparedStatementBinder.bind`、`DBAccessManager` の実行機構、**「OBJECT カラムの扱い（U3 への引き継ぎ事項）」**（`BindSqlBuilder` が OBJECT に `BindValue.ofNull(OBJECT)` を積み、`PreparedStatementBinder` が `setNull(pos, Types.BLOB)` を適用し、**呼び出し側が `getBindIndexOf(OBJECT,1)` で位置を得て `setBinaryStream(pos, in)` で上書きする**、という機構を U1 が定め「どう使うかは U3 が決める」とした点）。
- **U2 `select-path` の 3.1 成果物** — `Query` の `default` 9 個の一括宣言（U2 が済ませたため U3 は override を足すだけでよい）、§ 11 引き継ぎ 1〜4。
- 実ソース — `QueryImpl.java`（`getInsertSQL`、`getUpdateSQL`、`getDeleteSQL`、`getDeleteAllSQL`、`addWhere`、`getBlobIndex`）、`Dao.java`（`create`/`update`/`delete`、`executeUpdate(String)`、`executeUpdate(String,int,InputStream)`）、`BlobUtils.java`、`DBAccessManager.java`。

設問は 1 件。**U1 が明示的に「どう使うかは U3 が決める」と引き継いだ論点**であり、他の実装詳細（値アキュムレータの分離、FR-6.2 の修正、`getBlobIndex()` の二重の意味）は 2.6 / 2.7 が既に確定しているため設問にしない。

---

## Q1. BLOB を含む INSERT / UPDATE を、新しいバインド経路でどう実行するか

**U1 が定めた機構と、その機構が抱える未解決の実行順序の矛盾**

U1 は BLOB カラムの扱いを次のように定めた（`domain-entities.md` C-3「OBJECT カラムの扱い」）。

1. `BindSqlBuilder` が OBJECT カラムに遭遇したら、テキストに `?` を置き、値として `BindValue.ofNull(DataType.OBJECT)` を積む
2. `PreparedStatementBinder` はこれを `setNull(pos, Types.BLOB)` として適用する
3. 呼び出し側は `PreparedSql.getBindIndexOf(DataType.OBJECT, 1)`（＝ `getBlobIndex()`）で位置を得て `setBinaryStream(pos, in)` で**上書きする**

**しかし U1 が確定した `DBAccessManager.executeUpdate(PreparedSql sql)`（`business-logic-model.md` § 7.1）は、`prepareStatement` → `PreparedStatementBinder.bind` → `executeUpdate` → **try-with-resources による `close`** を 1 メソッド内で完結させる。** `PreparedStatement` は `executeUpdate(PreparedSql)` の呼び出しが終わった時点で既に閉じられており、呼び出し側（`Dao`）が「後から `setBinaryStream` で上書きする」ための隙間が存在しない。

```java
// U1 が確定した DBAccessManager.executeUpdate（再掲）
public int executeUpdate(PreparedSql sql) {
    record(sql);
    checkBindable(sql);
    try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
        PreparedStatementBinder.bind(ps, sql.getValues());
        return ps.executeUpdate();          // ← ここで実行され、try ブロックを抜けると close される
    } catch (SQLException e) {
        throw new DaoException(e);
    }
}
```

したがって「呼び出し側が `setBinaryStream` で上書きする」ためには、**バインドと実行の間に呼び出し側が介入できる新しい経路**が必要になる。これが U3 の決めることである。

### 現行の対応する経路（比較対象）

現行 `Dao.executeUpdate(String sql, int index, InputStream in)`（`Dao.java:266-274`、変更しないメソッドとして 2.6 M-4「不変」に明記済み）は次の形で同じ問題を解いている。

```java
protected int executeUpdate(String sql, int index, InputStream in) throws DaoException {
    DaoEvent event = createDaoEvent(sql);
    getExecuteHandler().handleBeforeExecuteUpdate(event);
    PreparedStatement stmt = dbm.preparedStatement(sql);   // ← close されない生の PreparedStatement を受け取る
    int result = BlobUtils.executeUpdate(stmt, index, in); // ← setBinaryStream + executeUpdate はここで行う
    TransactionStateManager.getInstance().executed();
    event.setResult(result);
    return getExecuteHandler().handleAfterExecuteUpdate(event);
}
```

`DBAccessManager.preparedStatement(String)`（2.6 M-5「不変」）は `PreparedStatement` を**呼び出し側に返し、close の責任を委ねる**。この形なら「バインドと実行の間に割り込む」ことができる。

### 選択肢

**A. `DBAccessManager` に BLOB 専用の実行メソッドを新設する**

```java
// DBAccessManager（新規メソッド）
public int executeUpdate(PreparedSql sql, int blobIndex, InputStream in) {
    record(sql);
    checkBindable(sql);
    try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
        PreparedStatementBinder.bind(ps, sql.getValues());   // OBJECT 列は setNull される
        ps.setBinaryStream(blobIndex, in);                    // ここで上書きする
        return ps.executeUpdate();
    } catch (SQLException e) {
        throw new DaoException(e);
    }
}
```

`try-with-resources` による確実な close は維持しつつ、バインドと実行の間に `setBinaryStream` を挟む形で U1 の設計意図（値は `BindValue.ofNull` として積み、呼び出し側が上書きする）をそのまま実現する。`Dao` 側は対応する `protected int executeUpdate(PreparedSql sql, int blobIndex, InputStream in)` を新設し、`DBAccessManager` に委譲する。

**B. BLOB を含む書き込みは新経路に載せず、旧経路（`getInsertSQL`/`getUpdateSQL` のリテラル版 ＋ `BlobUtils.executeUpdate`）のまま維持する**

`Dao.executeUpdate(String,int,InputStream)` は 2.6 が「不変」と定めており触れる必要がない。BLOB を含む `create`/`update` はこの経路のまま残し、新しい `getInsertPreparedSql`/`getUpdatePreparedSql` は BLOB を含まない書き込みにのみ使われる。

**C. `PreparedStatement` を呼び出し側に返す新しいメソッド（`DBAccessManager.preparedStatement(PreparedSql)`）を追加し、`Dao` 側でバインド・上書き・実行・close をすべて行う**

`DBAccessManager.preparedStatement(String)`（現行）と対称の形。`PreparedStatementBinder.bind` の呼び出しと close の責任が `Dao` 側に移る。

**X.** Other (please specify)

### 論点

| 観点 | A | B | C |
|---|---|---|---|
| FR-1.5（BLOB のバインド）の充足 | ✅ BLOB 以外の値も含め全列がバインドされる | ❌ BLOB を含む行の**他の列がリテラル埋め込みのまま**残り、SM-1 に穴が残る | ✅ A と同じ |
| U1 が確立した `DBAccessManager` の設計（`try-with-resources` による close の一元管理）との整合 | ✅ 維持する。新メソッド内で完結する | 影響なし（新経路を使わないため） | ⚠️ close の責任が `Dao` 側に移り、`DBAccessManager` の設計方針（U1 § 7.1「なぜ `ResultSet` を返す形にしないか」と同種の問題）を破る |
| 実装量 | 中（`DBAccessManager` に 1 メソッド、`Dao`/`DaoAdapter` に 1 メソッド） | 小（追加なし） | 中〜大（`PreparedStatementBinder.bind` を `Dao` 側からも呼べるようにする必要がある。現在は `DBAccessManager` 内部でのみ呼ばれる） |
| `checkBindable` / 実行記録（`record`） | ✅ 新メソッド内で通常どおり行う | 旧経路のまま（`record` されない、U2 が既に指摘した経路） | ✅ 同様に行えるが `Dao` 側の実装が `DBAccessManager` の内部規約に依存する |
| Must 要件（FR-1.5）との適合 | ✅ | ❌（Must を満たさない） | ✅ |

**A を推奨する。** FR-1.5 は Must であり、B はこれを満たさない（BLOB を含む行の非 BLOB 列がバインドされないまま残る）。C は実現可能だが、`PreparedStatementBinder.bind` を `DBAccessManager` の外に公開する必要があり、U1 が確立した「`DBAccessManager` が実行の一切を内部で完結させる」という設計方針（§ 7.1）を破る。A は同じ効果を `DBAccessManager` 内に閉じたまま実現できる。

[Answer]: A（`DBAccessManager` に `executeUpdate(PreparedSql, int blobIndex, InputStream in)` を新設する）— 2026-08-10、**Mode:** guided

**A を採ることの帰結（本設計が扱う）**:

1. **`DBAccessManager` に新規 public メンバが 1 つ追加される。** `executeUpdate(PreparedSql, int, InputStream)`。U1 が確定した M-5 の「追加」表（`executeQuery(PreparedSql, ResultSetHandler)`、`executeUpdate(PreparedSql)`）に**3 つ目の実行メソッド**として並ぶ
2. **`Dao` / `DaoAdapter` にも対応する `protected` メンバが 1 つ追加される。** `executeUpdate(PreparedSql, int, InputStream)`。既存の `executeUpdate(String, int, InputStream)`（2.6 M-4「不変」）と対称の形にする
3. **`blobIndex` の呼び出し順序に依存関係が生まれる。** `getInsertPreparedSql` / `getUpdatePreparedSql` を呼んで `PreparedSql` を得た後、`getBlobIndex()` でその `PreparedSql` に対応する位置を読み、その位置を新メソッドに渡す。呼び出し側（`Dao.create`/`update`）はこの順序（ビルド → `getBlobIndex()` 読み取り → 実行）を守る必要がある——2.6 M-2 が既に定めた `getBlobIndex()` の呼び出し順序依存（「ビルドメソッドを呼んだ後でなければ意味のある値が得られない」）と同じ性質であり、新しい制約ではない
4. **BLOB を持たない `create`/`update` は新メソッドを使わない。** `getBlobIndex()` が `-1`（BLOB なし）を返す場合は通常の `executeUpdate(PreparedSql)` を使う。`Dao.create`/`update` はこの分岐を持つ

---

## Ambiguity and Contradiction Analysis

`stage-protocol.md` §3 が必須とする回答分析。

**曖昧な回答**: なし。単一の選択肢が確定している。

**矛盾**: なし。ただし **2.6 成果物に対する追加が 1 件生じる**。

| # | 組み合わせ | 確認内容 | 判定 |
|---|---|---|---|
| 1 | Q1=A ＋ `component-methods.md` M-5「追加」表 | 2.6 は M-5 の追加を `executeQuery(PreparedSql)` と `executeUpdate(PreparedSql)` の 2 件（後に U1 が `executeQuery(PreparedSql, ResultSetHandler)` に変更）としており、`executeUpdate(PreparedSql, int, InputStream)` を含まない | **2.6 への追加。** BLOB 対応は 2.6 の M-5 表が想定していなかった経路であり、U1 も「どう使うかは U3 が決める」として具体化を委ねていた。追加であり修正ではない |
| 2 | Q1=A ＋ U1 § 7.1「なぜ `ResultSet` を返す形にしないか」 | 新メソッドは `PreparedStatement` を呼び出し側に返さず、`DBAccessManager` 内で完結させる | **矛盾なし。** U1 が §7.1 で排した設計（`Statement` の handle を呼び出し側に渡す）を新メソッドも避けている。`setBinaryStream` は `DBAccessManager` の内部で `PreparedStatementBinder.bind` の直後に呼ぶため、`Statement` の handle は外に出ない |
| 3 | Q1=A ＋ FR-1.5 の Must | 全列（BLOB を含む）がバインドされる | **矛盾なし。** A のみが Must を満たす |

**追加の設問**: 不要。

---

## Consolidated Summary Confirmation

**Prompt**: この内容で `business-logic-model.md` / `business-rules.md` / `domain-entities.md` を生成してよいか。

**Options**:
- Looks correct — この回答から成果物を生成する
- Request changes — 生成前に回答を修正する

**回答の要約**

| # | 決定 | 帰結 |
|---|---|---|
| Q1 | **A** — `DBAccessManager.executeUpdate(PreparedSql, int, InputStream)` を新設 | `Dao`/`DaoAdapter` にも対応メンバを追加。`getBlobIndex()` の呼び出し順序依存を維持。BLOB なしの書き込みは通常経路を使う |

[Answer]: Looks correct — 2026-08-10

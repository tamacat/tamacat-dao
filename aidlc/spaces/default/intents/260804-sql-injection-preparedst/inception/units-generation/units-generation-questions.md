# Units Generation — 設問

**ステージ**: 2.7 units-generation（Inception）
**Depth**: Standard（目安 5〜8 問）
**作成**: 2026-08-05T13:23:03Z

本ステージが決めるのは **Unit の切り方と依存の形（トポロジー）** である。
どの Unit を先に作るか（実装順・クリティカルパス）は Delivery Planning（2.8）の
担当であり、本設問には含めない。

---

## Sources

- `inception/application-design/components.md` — 新規 C-1〜C-8、変更 M-1〜M-8、変更しないコンポーネント
- `inception/application-design/component-methods.md` — 各コンポーネントの公開メソッドシグネチャ
- `inception/application-design/services.md` — サービス不在の記録、実行時の役割 R-1〜R-5、「段階的な移行の可能性」表、walking skeleton の最小単位
- `inception/application-design/component-dependency.md` — 依存マトリクス、順序の不変条件、ブラストレーダス
- `inception/application-design/decisions.md` — ADR-001〜ADR-012、承認済み要件との差分表、未解決の OQ-9
- `inception/requirements-analysis/requirements.md` — FR-1〜FR-8、NFR-1〜NFR-7、CON-1〜CON-8、AC-1〜AC-11

### 本ステージで前提として扱う事実（設問にしない）

| 項目 | 事実 | 出典 |
|---|---|---|
| デプロイモデル | tamacat-dao にデプロイ可能なプロセスは存在しない。全 Unit の成果物は Maven の単一 jar（`org.tamacat:tamacat-dao`）に同梱される。Unit ごとに配置単位を選ぶ余地がない | `services.md`「本ステージにおける『サービス』の適用範囲」 |
| Unit の kind | 上記により全 Unit が `library` である（デプロイされる実行体ではなく、利用側プロセスにリンクされる再利用コード） | 同上 |
| 統合点の形 | Unit 間の統合点はネットワーク契約ではなく **Java の型とメソッドシグネチャ**である。`component-methods.md` が既に確定させている | `component-methods.md` |

---

## Q1. Unit の切り方（境界戦略）

`application-design` は新規 7 クラス（C-1〜C-7）＋ mock 拡張（C-8）と、既存 8 コンポーネントの
変更（M-1〜M-8）を定めた。これをどの軸で Unit に切るか。

- **A. 実行経路別** — 共通基盤（`Param` / `PreparedSql` / `BindValue` / `ValueRules` / `BindSqlBuilder` / `PreparedStatementBinder`）を 1 Unit にまとめ、その上に「SELECT 経路」「INSERT・UPDATE・DELETE 経路」「サブクエリ」「方言」を積む
  - 帰結: `services.md`「段階的な移行の可能性」表の切り替え単位とそのまま一致する。walking skeleton（共通基盤＋SELECT 経路）が 2 Unit で立つ
- **B. レイヤ別** — 値プリミティブ層（C-1/C-2/C-3）、SQL 組み立て層（C-4/C-5）、実行層（C-6/C-7/M-5）を別 Unit にし、その上に呼び出し側（M-2 `QueryImpl` / M-3 `Search` / M-4 `Dao`）を置く
  - 帰結: `component-dependency.md` の依存マトリクスに素直に対応する。ただし単独の Unit では端から端まで通らないため、最初に動く形が出るのが遅い
- **C. FR 群別** — FR-1（パラメータ化）、FR-2（検証・エスケープ）、FR-4（実行記録）、FR-6（既存欠陥）、FR-8（検証手段）をそれぞれ Unit にする
  - 帰結: 要件とのトレーサビリティが 1 : 1 になる。ただし FR-1 が巨大な 1 Unit になり、FR-2 と FR-1 が同じクラス（`BindSqlBuilder`）を同時に触る
- **D. 単一 Unit** — 分割せず 1 Unit として一括で実装する
  - 帰結: Construction が 1 Bolt になる。`component-dependency.md` のブラストレーダス表で「高」が 6 ファイルあり、それらを一度に変更することになる
- **X. Other（自由記述）**

[Answer]: A（2026-08-05T13:29:54Z, **Mode:** guided）

---

## Q2. Unit の粒度

Q1 の軸をどこまで細かく適用するか。

- **A. 粗い（3〜4 Unit）** — 例: 共通基盤 / 読み取り経路 / 書き込み経路 / 方言と欠陥是正
- **B. 中（6〜8 Unit）** — 上記に加え、検証基盤・サブクエリ・移行作業などを独立させる
- **C. 細かい（10 以上）** — クラス単位に近い粒度まで割る
- **X. Other（自由記述）**

[Answer]: A（2026-08-05T13:29:54Z, **Mode:** guided）

---

## Q3. 検証基盤（C-8 mock スタック拡張、FR-8.1）の位置づけ

現行 `MockPreparedStatement` の `setXxx` は空実装、`MockConnection.prepareStatement(String)` は
SQL 引数を破棄する。この状態では **どの Unit もバインド位置と値をテストで確認できない**
（`decisions.md` ADR-009、`requirements.md` FR-8.1）。

- **A. 独立した 1 Unit とし、バインド経路を持つ全 Unit がこれに依存する形にする**
  - 帰結: 最初の Unit が「テストできる状態を作る」ことになる。バインド化そのものは 2 番目以降
- **B. 共通基盤 Unit（Q1-A の 1 個目）に含める**
  - 帰結: Unit 数が 1 減る。共通基盤 Unit が production コードとテスト基盤の 2 責務を持つ
- **C. 独立させず、各 Unit のテスト作業の一部として必要になった時点で都度拡張する**
  - 帰結: 拡張が複数 Unit に分散する。`component-dependency.md` が「最も危険な失敗様式」と呼ぶ値の並び順ずれ（検査 1 で捕まらない）を検出できる状態が、いつ揃うか不定になる
- **X. Other（自由記述）**

[Answer]: B（2026-08-05T13:29:54Z, **Mode:** guided）

---

## Q4. 既存欠陥の是正（FR-6.1〜FR-6.4）の位置づけ

4 件の内訳は次のとおり。

| ID | 対象 | バインド化との関係 |
|---|---|---|
| FR-6.1 | `OracleDao.searchListForOracle`（`:30-73`）を修正して有効化 | 未バインドの `?` をバインドに変えるため、バインド化と同じコードに触る |
| FR-6.2 | `QueryImpl.java:268` の OBJECT ブランチのテーブル修飾不整合 | `getUpdatePreparedSql` と同じメソッド周辺に触る |
| FR-6.3 | `MySQLDao.java:46` の LIMIT の `int` 直接連結（Should） | 同上（方言の実行経路） |
| FR-6.4 | `OracleSearch.OracleValueConvertFilter` の null ガード | **バインド化とは無関係**。旧経路（リテラル系）の修正 |

- **A. 「既存欠陥の是正」1 Unit にまとめる（4 件とも）**
- **B. すべて経路 Unit に同居させる**（FR-6.1 / FR-6.3 / FR-6.4 → 方言 Unit、FR-6.2 → `QueryImpl` を含む Unit）
- **C. バインド化と同じコードに触る 3 件（FR-6.1 / FR-6.2 / FR-6.3）は経路 Unit に同居させ、独立している FR-6.4 だけ別 Unit にする**
- **X. Other（自由記述）**

[Answer]: B（2026-08-05T13:29:54Z, **Mode:** guided）

---

## Q5. FR-1.7 / OQ-9 — 識別子位置（テーブル名・カラム名・ORDER BY 句）の扱い

`decisions.md` ADR-010 が本ステージに持ち越した唯一の未決事項である。
対象は `Sort.sort(Object, Object)` の非 `Column` キー経路（`Sort.java:47-60`）、
`Column.getFunctionName()`、`DataType.FUNCTION`。優先度は **Should Have**。
`application-design` は `Sort` を「変更しないコンポーネント」として扱っており、
この要件を実施する場合は `Sort` の設計を追加で行う必要がある。

- **A. Unit として含める。機構（(a) 例外による拒否／(b) 宣言済み `Table` / `Column` メタデータへの照合）の決定は Functional Design（3.1）に委ねる**
  - 帰結: 未決が 3.1 に繰り越される。受け入れ条件 AC-10b はどちらの機構でも判定できる形になっている
- **B. Unit として含める。機構は「宣言済み `Table` / `Column` メタデータへの照合」に今ここで確定する**
  - 帰結: ライブラリの設計思想（メタデータ宣言から SQL を組む）と一貫する。ただし `Sort.sort(Object k, Object o)` の非 `Column` キー経路は意図的にメタデータ外の式を許すために存在するため、照合を強制すると既存利用者が壊れうる（FR-3.1 との衝突）
- **C. Unit として含める。機構は「例外による拒否」に今ここで確定する**
  - 帰結: 実装は単純。ただし何を拒否対象とするかの線引きが必要で、`Sort` の既存利用を壊すリスクは B と同じ形で残る
- **D. 今回のスコープから外す。FR-1.7 を Won't Have に落とし、`requirements.md` にその旨を記録する**
  - 帰結: Must 要件に集中できる。SM-1 の対象範囲は元々 FR-1.7 を含まない（NFR-1 の定義）ため、成功指標には影響しない
- **X. Other（自由記述）**

[Answer]: A（2026-08-05T13:36:38Z, **Mode:** guided）

---

## Q6. 依存のない Unit の並行実行を明示するか

- **A. 依存グラフ上で同時に着手できる Unit の組を明示する** — Delivery Planning（2.8）が並行バッチを組める
- **B. 常に 1 Unit ずつ進む前提で、単一の topological order のみを示す** — 2.8 の選択肢を狭める代わりに、依存記述が単純になる
- **X. Other（自由記述）**

[Answer]: A（2026-08-05T13:36:38Z, **Mode:** guided）

---

## Q7. 移行作業の位置づけ

バインド化に伴って必ず発生する作業が 3 つある。

1. サンプル DAO（`UserDao.java:22, :43`、`FileDataDao.java:12, :30`）とテスト（`MySQLDaoTest.java:83`）の `param()` → `prepare()` 切り替え（`decisions.md` ADR-002）
2. 実行経路を通る e2e テスト（`UserDaoTest`）のアサーション書き換え — 実行 SQL が `?` 入りに変わるため（`component-dependency.md`「テストへの影響」）
3. FR-8.2 の「旧アサーション ⇔ 新アサーション」対応表の作成

- **A. 「移行と検証」1 Unit にまとめ、経路 Unit すべてに依存させる**
  - 帰結: 対応表が 1 か所に揃う。ただし各経路 Unit の完了時点ではテストが赤いままになりうる
- **B. 各経路 Unit の内部作業として分散させる**
  - 帰結: 各 Unit が「テストが緑」で完了する。対応表（FR-8.2）が Unit をまたいで断片化する
- **C. 実際の書き換えは各経路 Unit に分散させ、対応表（FR-8.2）の集約だけを独立 Unit にする**
- **X. Other（自由記述）**

[Answer]: B（2026-08-05T13:36:38Z, **Mode:** guided）

---

## 追加設問（矛盾解消）

### FQ-1. Q2（粒度 3〜4）と Q5（FR-1.7 を Unit に含める）の不整合

Q1=A / Q3=B / Q4=B / Q7=B から確定する Unit は **4 個**である。

1. 共通基盤（C-1〜C-7 ＋ M-5 実行面 ＋ C-8 mock 拡張）
2. SELECT 経路（M-3 `Search` / M-2 `QueryImpl` SELECT 系 / M-4 `Dao.search` / サブクエリ FR-1.6）
3. 書き込み経路（M-4 の新 protected 拡張点 / M-2 INSERT・UPDATE・DELETE 系 / FR-6.2）
4. 方言（M-7 `MySQLDao` / `OracleDao` / `OracleSearch` ＋ FR-6.1 / FR-6.3 / FR-6.4）

ここに Q5=A の FR-1.7（`Sort` の識別子検証）を独立 Unit として加えると **5 個**になり、
Q2 で選んだ「粗い（3〜4 Unit）」を超える。FR-1.7 は Should Have であり、
バインド化（Must）とは機構がまったく異なる（値のバインドではなく識別子の検証）。

- **A. 5 Unit を許容する** — Q2 の「粗い」を 3〜5 と読み替え、FR-1.7 を独立 Unit として維持する
  - 帰結: Must（1〜4）と Should（5）が Unit として分離され、5 番目を落としても 1〜4 は成立する。Unit 数だけが Q2 の範囲を 1 超える
- **B. FR-1.7 を SELECT 経路 Unit（#2）に同居させる** — ORDER BY 句は SELECT 文の一部であるため
  - 帰結: Unit は 4 個のまま。ただし walking skeleton を含む Must の Unit に Should の作業が入り、**#2 の完了が FR-1.7 の完了を待つ**ことになる
- **C. FR-1.7 をスコープ外にする** — Q5 の回答を D に変更し、Won't Have として `requirements.md` に記録する
  - 帰結: Unit は 4 個。Q2 と完全に整合するが、Q5 で「含める」と選んだ判断を覆すことになる
- **X. Other（自由記述）**

[Answer]: A（2026-08-05T14:11:55Z, **Mode:** guided）

---

## Consolidated Summary Confirmation

| 設問 | 回答 | 意味 |
|---|---|---|
| Q1 | A | 実行経路別に切る（共通基盤の上に SELECT / 書き込み / 方言 を積む） |
| Q2 | A（FQ-1 により 3〜5 に読み替え） | 粗い粒度 |
| Q3 | B | mock 拡張（C-8）は共通基盤 Unit に含める |
| Q4 | B | FR-6.1 / 6.3 / 6.4 → 方言 Unit、FR-6.2 → 書き込み経路 Unit に同居 |
| Q5 | A | FR-1.7 を Unit として含める。機構の決定は Functional Design（3.1）に委ねる |
| Q6 | A | 依存グラフ上で並行可能な組を明示する |
| Q7 | B | 移行作業（param→prepare、e2e テスト書き換え、FR-8.2 対応表）は各経路 Unit に分散 |
| FQ-1 | A | 5 Unit を許容する（Must 4 ＋ Should 1） |

**プロンプト**: この内容で成果物を生成してよいか。
**選択肢**: Looks correct / Request changes

[Answer]: Looks correct（2026-08-05T16:52:53Z, **Mode:** guided）

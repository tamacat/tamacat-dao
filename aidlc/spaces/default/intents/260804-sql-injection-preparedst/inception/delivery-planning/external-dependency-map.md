# External Dependency Map — SQL パラメータ化（PreparedStatement 化）

## 上流成果物との関係

- **`requirements.md`**（requirements-analysis, 2.3）— CON-1 / CON-3 / CON-5 / CON-6 / CON-8、OQ-4 / OQ-6 / OQ-7 / OQ-8、FR-8.3、NFR-2 / NFR-4。本文書のゲート項目はこれらに対応する。
- **`unit-of-work.md`**（units-generation, 2.7）— Unit の実装制約（Java 8、新規依存を追加しない）。
- **`unit-of-work-dependency.md`**（同上）— Unit 間の依存。外部依存はどれも Unit 間の依存を増やさないことを確認する。
- **`unit-of-work-story-map.md`**（同上）— FR-8.3 の割り当て先（Build and Test 3.6）。
- **`components.md`**（application-design, 2.6）— C-8 mock スタック拡張。実 DB 検証との役割分担。
- **`stories.md`**（user-stories, 2.4）/ **`mockups`**（refined-mockups, 2.5）/ **`team-practices`**（practices-discovery, 2.2）— いずれも本スコープで **SKIP** のため存在しない。外部チームとのハンドオフや承認リードタイムは、そもそも本取り組みに存在しない。

---

## 全体像

本取り組みは**ローカル単独作業**であり（CON-3）、外部チームへのハンドオフも承認リードタイムもデータ提供待ちも存在しない。ゲート項目は次の 3 件に限られ、いずれも **Bolt の進行を止めない**——3 件とも Build and Test（3.6）で顕在化する。

| # | 項目 | 種別 | 所有者 | リードタイム | 阻害する Bolt | 状態 |
|---|---|---|---|---|---|---|
| D-1 | H2 Database（test スコープ） | ビルド依存の追加 | 本プロジェクト | なし（Maven Central から取得） | なし（3.6 で使う） | **追加する**（FQ-2 = A） |
| D-2 | CodeQL CLI | ローカルツール | 本プロジェクト | 導入時のみ（CLI の取得） | なし（3.6 で使う） | **ローカル手動実行**（FQ-3 = A） |
| D-3 | 利用側アプリケーションの監査指摘 | 外部情報 | 本取り組みの外 | 不明 | なし | **未解決のまま残す**（OQ-8） |

---

## D-1. H2 Database（test スコープ）

### 決定

**H2 を test スコープで追加し、`src/test/resources/db.xml` の `javadb` bean を H2 の組み込み設定に置き換える**（FQ-2 = A）。`UserDaoTest2` を FR-8.3 の実行対象に含める。

### 経緯

`requirements.md` FR-8.3 は `QueryImplTest02` / `QueryImplTest03` / `UserDaoTest2` を surefire の実行対象に加えることを Must とし、OQ-6 / A-4 が「`UserDaoTest2`（Derby / `javadb`）を有効化するとビルドに Derby が必要になる。この前提条件の追加が許容されるかは確認していない」を残していた。

Q6 で **Derby は却下された**（リタイア済みであるため——ユーザー判断）。代替として H2 が提案され、FQ-2 で確定した。

### 実測した現状

| 項目 | 事実 |
|---|---|
| `pom.xml` の Derby 依存 | **1 つも宣言されていない。** `UserDaoTest2` は Derby の可否以前に現状動かない |
| `db.xml` の `javadb` bean | ドライバは `org.apache.derby.jdbc.ClientDriver`（ネットワーク版）だが URL は `jdbc:derby:db/sample;create=true`（**組み込み版の形式**）。依存を足しても接続できない矛盾した設定 |
| ビルド / テスト JVM | **JDK 25**（Corretto 25.0.1）、Maven 3.9.11 |
| ビルドの現状 | `source/target 1.8` のまま `mvn -DskipTests compile` は BUILD SUCCESS |
| test スコープの前例 | `junit` / `easymock` 5.1.0 / `mysql-connector-j` 9.6.0 / `slf4j` / `logback` がすでに test スコープで宣言済み |

### 制約との関係

| 制約 | 抵触するか | 理由 |
|---|---|---|
| CON-1 / NFR-4（Java 8 を維持） | **しない** | これらが縛るのは**生成物**——`src/main` のバイトコード（`source/target 1.8`）と、そこで使用する API である。test スコープの依存は成果物 jar に含まれず、利用側の実行環境にも要求されない。ビルド JVM が 25 であるため、Java 11+ を要求する H2 2.x も test スコープなら動作する |
| CON-6（ORM フレームワークに依存しない。compile スコープの第三者依存は `tamacat-core` と `javax.json` のみ） | **しない** | CON-6 が限定しているのは **compile スコープ**である。H2 は JDBC ドライバであって ORM フレームワークでもない |
| CON-5（JDBC ドライバは利用側が供給する） | **しない** | むしろ整合する。テスト用ドライバをテスト側が供給するのは CON-5 の構図そのものである |
| ADR-009（新規依存は追加しない） | **抵触しない**（対象が異なる） | ADR-009 が「新規依存を追加しない」と決めたのは **FR-8.1（バインド位置と値のアサート）の実現手段**についてであり、既存 mock スタックの拡張を選んだ。H2 が担うのは FR-8.3（実 DB エンジンに対する検証）であって FR-8.1 ではない。mock 拡張は予定どおり実施する（U1 / Bolt 1）ため、ADR-009 の決定は変更されない |

### 得られるもの

| 効果 | 内容 |
|---|---|
| OQ-6 の解消 | 「実 DB エンジンに対して生成 SQL を検証する実行テストが存在しない」という現状（`code-quality-assessment.md`）が解消される |
| **ADR-007 の検証** | H2 は型に厳格である。ADR-007（NUMERIC / FLOAT にも `setString` を使う）は自ら Negative に「実 DB エンジンに対する検証が必要である」と記録しており、H2 はその検証の場になる |
| FR-5.1 の汎用経路の検証 | H2 は本ライブラリの方言クラス（MySQL / Oracle）を持たないため、**汎用フォールバック経路**（`Dao` / `Search`）を通る。FR-5.1 が求める 3 経路のうち汎用経路が実 DB で検証される |
| `javadb` bean の矛盾の解消 | 接続できない設定（ネットワーク版ドライバ ＋ 組み込み版 URL）が置き換わる |

### 抱えるリスク

**H2 が NUMERIC 列への `setString` を拒否した場合、ADR-007 の見直しが必要になる**（`risk-and-sequencing-rationale.md` R-3）。これはリスクであると同時に、この検証を入れる目的そのものでもある——現行の唯一の実行テスト基盤（`MockDriver`）は型を一切検証しないため、この問題は今まで一度も試されていない。

緩和策: **Bolt 1 の時点で `PreparedStatementBinder` の setter 選択を 1 か所（`DataType` → setter の対応表を単一メソッド）に閉じ込める。** 見直しが必要になっても変更範囲が最小で済む。

### 実装時の詳細（Build and Test 3.6 が確定する）

- 依存の座標とバージョン（Java 11+ を要求する 2.x 系で問題ない。ビルド JVM が 25 であるため）
- `javadb` bean の driverClass を `org.h2.Driver`、url を組み込み形式（`jdbc:h2:mem:...` など）に置き換える
- **bean id は `javadb` のまま維持する。** `UserDaoTest2` は `setDatabase("javadb")` を 3 か所（`:29, :42, :54`）で呼んでおり、id を変えるとテスト側の変更が必要になる。名前と実体が一致しなくなる点は Javadoc / コメントで補う
- `UserDaoTest2` は `createTable()` / `dropTable()` を呼ぶ（`:30, :43`）。H2 の DDL がこれらに対応するかの確認が要る
- surefire の includes に 3 テストクラスを加える（FR-8.3）

---

## D-2. CodeQL CLI

### 決定

**CodeQL CLI をローカルで手動実行する**（FQ-3 = A）。ワークフロー設定もブランチ設定も変更しない。

### 経緯

`requirements.md` CON-8 のとおり、CodeQL ワークフローは `master` への push / PR と週次 cron でのみ動作する。本取り組みは v2.0 ブランチ上でローカルのみ、`git push` しない（CON-3）ため、**v2.0 では CI が走らず SM-3 / NFR-2 を測定できない**。OQ-7 がこの測定方法を Build and Test に残していた。

ブランチ設定を v2.0 に広げる案（Q6 = D / FQ-3 = C）は、push しない限り発火しないため実効性がない。ローカル手動実行が CON-3 と衝突しない唯一の手段である。

### 抱えるリスク

ADR-003 により、旧 API の戻り値を変えない代わりに **`SQLParser` のリテラル生成コードが残り、非推奨の public メソッド経由で到達可能なままになる**。CodeQL がこの経路を SQL インジェクションとして指摘しうる（`risk-and-sequencing-rationale.md` R-5）。

緩和策: 本取り組みの Units Generation（2.7）で `requirements.md` NFR-1 の判定基準を「**実行経路**に値の文字列連結が 0 件」に更新済み（ADR-003 由来）。指摘が出た場合、その経路が実行に到達しないことを示すか、指摘を抑制するかを 3.6 で判断する。

### 実装時の詳細（3.6 が確定する）

- CodeQL CLI の取得と、Java / Maven プロジェクトに対するデータベース作成手順
- 既存ワークフローが使っているクエリスイートとの一致（同じ判定基準で測るため）
- 変更前の指摘件数をベースラインとして先に取る（NFR-2 は「0 件」を求めるため、変更前に何件あったかを知らないと達成を主張できない）

---

## D-3. 利用側アプリケーションの監査指摘（未解決のまま残す）

`requirements.md` の A-2 / OQ-8 が `intent-statement.md` から未解消のまま継承している事項である。

> `intent-statement.md` の Problem Statement 第 2 項が指す「利用アプリケーション側のセキュリティ監査／診断指摘」と、SM-3 が測定する「tamacat-dao 本体に対する自動静的解析の指摘」が同一であるかは確認されていない。

**本取り組みの内側では解けない。** 利用側アプリケーションは CON-4 / OOS-1 によりプロダクト境界の外にあり、その監査結果を本ワークフローから参照する手段がない。

| 項目 | 内容 |
|---|---|
| 阻害する Bolt | **なし。** どの Bolt もこの情報を必要としない |
| 影響 | SM-3 の達成を宣言したとき、それが元の課題（利用側の監査指摘）を解消したことを意味するかが確認できない |
| 対応 | 未解決のまま記録する。解消には利用側アプリケーションの状況確認が必要であり、それは本取り組みの外の作業である |

---

## Bolt ごとの外部依存

| Bolt | 必要な外部依存 | ブロックされうるか |
|---|---|---|
| 1 `walking-skeleton` | なし（既存の `MockDriver` と JUnit のみ） | **されない** |
| 2 `read-path-complete` | なし | **されない** |
| 3 `write-path-and-dialects` | なし（H2 は 3.6 で使う） | **されない** |
| 4 `identifier-safety` | なし | **されない** |
| Build and Test（3.6） | D-1 H2、D-2 CodeQL CLI | 導入が済むまで FR-8.3 と NFR-2 の判定ができない。ただし他の判定（`mvn test` の緑、テスト件数 138 以上）は独立に進む |

**4 つの Bolt はいずれも外部依存でブロックされない。** ゲート項目は 3.6 に集中しており、そこまでに D-1 / D-2 を用意すればよい。リードタイムはどちらも取得作業のみで、外部の承認や他チームの作業を待つ要素がない。

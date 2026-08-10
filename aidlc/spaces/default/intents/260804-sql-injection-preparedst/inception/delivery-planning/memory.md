> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations

- 2026-08-05T22:45:48Z — walking-skeleton の是非を設問にしない; 判断はすでに 2 か所で確定している——`.claude/scopes/aidlc-sql-parameterization.md` の frontmatter が `skeleton: on`、`scope-document.md`「Sequencing Approach」が walking-skeleton-first。`org.md` の `## Walking Skeleton` は「scope が `skeleton: on` を宣言している場合は最初に走らせる」と定めており、3 者が整合する。設問にすべきは「skeleton をやるか」ではなく「skeleton にどこまで含めるか」である。
- 2026-08-08T18:10:00Z — H2 に置き換えたあとも `db.xml` の bean id は `javadb` のまま維持すると決めた; `UserDaoTest2` が `setDatabase("javadb")` を 3 か所（`:29, :42, :54`）で呼んでおり、id を変えるとテスト側の変更が要る。名前と実体が一致しなくなる代償は Javadoc / コメントで補う。FR-8.3 の対象テストを最小の変更で有効化することを優先した。

## Deviations

- 2026-08-08T18:10:00Z — `org.md` `## Way of Working` の base / target `main` を **`v2.0`** と読み替えた; このリポジトリに `main` は存在せず（既定ブランチは `master`）、`requirements.md` CON-3 が「v2.0 上でローカルのみ、`git push` しない」と定めている。org.md への矛盾ではなく「`main` を持たないリポジトリでのトランクの同定」として扱い、squash-merge と短命ブランチという org.md の趣旨は保った。異なるのは push しない点だけで、それは CON-3 が明示した作業制約である。
- 2026-08-08T18:10:30Z — stage 定義が挙げる WSJF スコアリングを採らず、素直に risk-first と記録した; 本取り組みでは 5 Unit がすべて同一の成功指標（SM-1）に寄与し、うち 4 つが Must で 1 つでも欠ければ SM-1 は達成されない。value と time criticality が定数になるため WSJF は「risk-reduction ÷ job size」に退化し、risk-first と同じ順序を出す。それを WSJF と呼ぶのは根拠を装飾するだけになる。

## Tradeoffs

- 2026-08-08T18:11:00Z — DAG が許す `{U3, U4, U5}` の並行実行を使わず逐次にした（Q4 = B）; 並行の主な利得は複数の実行者が同時に進むことだが、CON-3 によりローカル単独作業で実行者は 1 系統しかいない。加えて R-1（値の並び順ずれ）は症状が出ない失敗様式であり、切り分けの難度が上がる状況を作りたくなかった。この判断は可逆で、並行が許されることは `unit-of-work-dependency.md` に記録済みである。
- 2026-08-08T18:11:30Z — FQ-1 = A により U3 と U4 を同一 Bolt に置き、最も危険な失敗様式（R-1）を抱える U3 に単独ゲートを与えなかった; Bolt 数を 4 に保つことを優先した。代償として R-1 の判定（AC-3b）が U4 の結果と同じゲートで承認される。緩和として、Bolt 3 のゲートでは AC-3b の判定結果を U4 の結果と分けて提示すると計画に明記した。
- 2026-08-08T18:12:00Z — Derby の代替として H2 を test スコープで採り、CON-1 / CON-6 との抵触がないことを制約の適用範囲から論証した; CON-1 / NFR-4（Java 8）が縛るのは生成物のバイトコードと使用 API であって test スコープの依存ではなく、CON-6 が限定しているのは compile スコープの第三者依存である。ビルド JVM が JDK 25 であることを実測して Java 11+ を要求する H2 2.x が使えることを確認した。副次的に、H2 が方言クラスを持たないため汎用フォールバック経路が実 DB で検証され、型に厳格であるため ADR-007 が初めて試される。

## Open questions

- 2026-08-08T18:12:30Z — ADR-007（NUMERIC / FLOAT にも `setString` を使う）は実 DB エンジンに対して一度も検証されていない。H2 の導入がその最初の機会であり、H2 が NUMERIC 列への `setString` を拒否した場合は ADR-007 を Superseded にして代替 1（`DataType` ごとに厳密な setter）へ切り替える必要がある。その場合 Bolt 1 の成果物（`PreparedStatementBinder`）に手が入るため、Bolt 1 の再検証が要る。緩和として Bolt 1 で setter 選択を単一メソッドに集約するよう計画に書いたが、実際に集約されているかは Bolt 1 のゲートで確認を要する。
- 2026-08-08T18:13:00Z — U5 `identifier-safety` は設計未完のまま Construction に入る唯一の Unit である。`Sort.sort(Object, Object)` の非 `Column` キー経路は意図的にメタデータ外の式を許すために存在するため、識別子検証を入れると FR-3.1（既存利用を壊さない）と衝突しうる。3.1 がこれを解けない場合、Bolt 4 を落として FR-1.7 を Won't Have にする判断が必要になる。

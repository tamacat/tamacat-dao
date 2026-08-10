<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is maintained by the orchestrator during stage execution. Add observations at the gate ritual, not by editing here directly.

## Interpretations

- 2026-08-09T00:00:00Z — U1 `bind-foundation` / U2 `select-path` は既にこのステージを完了済み（`security-requirements.md` / `tech-stack-decisions.md` が存在）。U4 `dialects` の回では、U1/U2 が確立した番号体系（SEC-n / R-n / TSD-n）を継続する形で執筆した。U3 `write-path` / U5 `identifier-safety` はまだ functional-design 未着手のため、このステージも未着手である。
- 2026-08-09T00:00:00Z — U4 は新規クラスを 0 個作るため、STRIDE の T（値の構文解釈）に対する新規要件がほとんどない。中心を「型による安全化（ページング境界値）」と「構文的正しさ（for update の二重化、可用性）」に置いた。

## Deviations

- 2026-08-09T00:00:00Z — SEC-19（for update の二重化防止）を STRIDE の T ではなく可用性（CIA の A）の要件として位置づけた; セキュリティガイドが主眼とするのは注入・認可だが、実行不能な SQL を生成することもサービス拒否の一形態であるため、機械的に検証可能な要件として明示する方が Build and Test（3.6）への引き継ぎが明確になると判断した。
- 2026-08-09T00:00:00Z — R-16（MySQL/Oracle の W-2 非対称）をセキュリティ上の露出ではないと明記した; 両方言とも呼び出し側の要求以上の行を返すことはなく、非対称は「返る行が多い/少ない」の方向にしか振れないため、認可境界が述語で表現されている場合でも「意図しない行の漏洩」には該当しないと判断した。

## Tradeoffs

- 2026-08-09T00:00:00Z — JaCoCo ゲートを U4 に拡張しない判断（Q1=A）をした; `OracleDao` は U4 で初めてテストが付くクラスだが、クラス単位のゲートである以上 U4 が触れない既存メソッドごと測ることになり、U2 が既に却下した論法がそのまま当てはまるため。
- 2026-08-09T00:00:00Z — MIGRATION.md への追記（Q2=A）を選んだ; FR-7.2 の文言上の対象（raw-SQL 経路の文書化）を厳密には超えるが、unit-of-work.md が明示的に記録した観測可能な挙動変更を利用者向け文書のどこにも書かないことのコストの方が大きいと判断した。

- 2026-08-09T00:00:00Z — レビュー iteration 1 で blocking 3 件を検出・是正した; B-1 は STRIDE-I「該当なし」が誤りだった件（MySQLDao.searchList は現行 dbm.createStatement() 経由のため実行記録が0件で、載せ替え後は本体クエリ+FOUND_ROWS()の2件になる。SEC-22として新規要件化した）。B-2 はCodeQL/NFR-2への申し送りとFR-6.3の要件対応表への記載漏れ（3.6 handoffに項目追加、要件対応表にFR-6.3の文面未達を明記）。B-3 は「W-3の効果的窓」を未解決リスクとして残す記述が3.1で既に訂正済みの内容と矛盾していた（W-2のみが非対称、と修正）。
- 2026-08-09T00:00:00Z — レビューで「MySQL/OracleのW-2非対称はU4が新たに作ったものではない」という重要な指摘を受けた; U4以前のOracleDaoはsearchList(Query,int,int)のoverrideを持たずDao.searchListの継承実装（アプリ側read-skip）を使っており、それはoffsetを反映していた。U4がrownum方式に切り替えても返る行集合は変わらない（絞り込み場所がDB側に移るだけ）。MySQL側は元からoffsetを無視しており不変。したがってこの非対称はU4が新設したのではなく、元から存在していたものをU4が初めて文書化したにすぎない。MIGRATION.md追記とR-16の記述をこの理解に訂正した。functional-design（business-rules.md R-6/BR-1）の「新たに顕在化する」という attribution は訂正しなかった（既に承認プロセスを経た成果物への再訂正のコストが、この attribution の違いが下流の判断に与える実害を上回ると判断）。

- 2026-08-10T00:00:00Z — （U5）JaCoCo ゲート拡張の判断を U2/U4（拡張しない）ではなく U1（拡張する）の先例に寄せた; IdentifierRules は U5 が新設する完結したクラスであり、U2/U4 が既存クラスの一部書き換えだったのとは性質が異なるため。
- 2026-08-10T00:00:00Z — U5 の MIGRATION.md 追記を U2/U4 の追記（非破壊的）と区別し「破壊的変更」と明記した; 識別子検証は既存の正当な呼び出し（文字列リテラルを含む ORDER BY 式）を壊しうる点で性質が異なるため、太字表記で目立たせた。

- 2026-08-10T00:00:00Z — （U5）レビュー iteration 1・2 で MIGRATION.md 追記の「代替経路」案内が 2 度誤った; 1 回目は SELECT 側に実在する無検証経路（columnName 経由）を「存在しない」と誤って断定し、2 回目のその是正が前提条件（Table 未登録であること）を欠いたため今度は「例外にならず壊れたSQLを静かに生成する」誤った手順を案内する形になった。最終的に「functionName/isFunction を一切使わず columnName に式を設定した Table 未登録の Column を使う」という単一の検証済み手順に落ち着いた。ドキュメント内の「回避策」の提案は、実装コードと同じ厳密さで裏を取る必要があることを学んだ。

- 2026-08-10T00:00:00Z — （U3）レビュー iteration 1 で blocking 2 件を検出・是正した; B-1 は SEC-27 の例外 (2) が既定フォールバック（BR-14、ofLiteral）に留まるサブクラスを「外部の Dao 実装」の話として除外していたが、実ソースでは本リポジトリの `UserDao`/`FileDataDao`/`UserStatDao`/`TestDao` 自身が現状すべてこれに該当していた（`getXxxPreparedSql` を override せず旧リテラル経路のまま）。この落差を残存リスク R-28（現 R-29）として新設し、検証項目・TSD-23 の記述をそれと整合させた。B-2 は BLOB override 先を `Dao` とだけ書いていたが、`DaoAdapter` は `Dao` を継承しない（BR-17）ため実利用サブクラスの override 先は `DaoAdapter` であり、R-26（現 R-27）と TSD-24 を両クラス名指しに改めた。
- 2026-08-10T00:00:00Z — （U3）レビュー iteration 2 で blocking 2 件を検出・是正した; B-3 は残存リスク番号「U5 の R-22 に続けて R-23 から」という前提が誤りで、U5 自身のレビュー iteration 2 の是正で `identifier-safety` の残存リスク表が既に R-23 まで伸びていた（sibling 成果物を実際に読んで初めて判明——iteration 1 では reviewer-scope フックがディレクトリ単位の走査を拒否し検証できなかった。iteration 2 で dispatch の exempt list を「ファイルパス単位」に絞ったところ Read が通り、番号衝突を検出できた）。R-23〜R-28 を全て 1 つ繰り下げ R-24〜R-29 とし、両ファイルの相互参照をすべて更新した。B-4 は TSD-20 の判定基準「`<includes>` が 8 件のまま」が、同じ TSD-20 が引用する U5 TSD-16（`IdentifierRules` を追加し 9 件にする）と自己矛盾していた——U3 は U5 より後に書かれ U5 TSD-16 を明示的に引用しているため、額面どおり実装すると 3.6 が必ず失敗する判定基準になっていた。「U3 の変更によって増減しない」という差分ベースの表現に改めた。`reviewer_max_iterations: 2` に到達したため、レビュアーの再検証を受けずにオーケストレータが適用した。
- 2026-08-10T00:00:00Z — sibling unit の成果物を読む reviewer-scope フックは、ディレクトリレベルの Glob/Grep/LS を一貫して拒否する一方、dispatch の exempt list に列挙した**個別ファイルパスへの直接 Read**は通す。次にレビュアーへ sibling 参照を許可する dispatch を書くときは、許可対象をディレクトリではなく具体的なファイルパスの列挙で渡し、レビュアーへのプロンプトでも「Read で直接開け、Glob/Grep でディレクトリを走査するな」と明記するとよい。

## Open questions

- 2026-08-09T00:00:00Z — U3 `write-path` / U5 `identifier-safety` の functional-design が完了した後、それぞれの nfr-requirements で U4 と同じ SEC/R/TSD 番号体系の続きを振る必要がある（現在の最終番号: SEC-21、R-18、TSD-15）。
- 2026-08-10T00:00:00Z — U2 TSD-9 の `MIGRATION.md`「3. SM-1 の対象外に残る経路」節が「識別子位置（R-12、U5 で扱う予定）」という U5 出荷前の"予定"表現のまま残っている（U5 のレビューで一度指摘されたが未解消）。U3 が `MIGRATION.md` に追記する最後の Unit だったが、U3 の責務範囲外（U2 の成果物）として対応を見送った。Build and Test（3.6）へ申し送り済み。

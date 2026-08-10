# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project needs stable, durable guidance beyond the
> team practice (for example, package-specific release checks or an additional
> regression suite for a legacy component).

## Way of Working

<!-- Project-specific specialisation. Example: -->
<!-- This monorepo requires package-scoped branch names and a package owner -->
<!-- review in addition to the team's normal merge policy. -->

## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

- 「AC を判定できる」と主張するテストは、plan の完了チェックボックスだけでなく実際のテスト内容（対象の入力を実際に通しているか）を確認する。とくにセキュリティ上の受け入れ条件は完了扱いのまま未検証になりやすい (learned 2026-08-10) <!-- cid:code-generation:c3 -->
- セキュリティ修復 intent では、テスト戦略（Minimal/Standard/Comprehensive）のデフォルト外でも security-test-instructions.md を生成する。受け入れ条件（AC）自体が本質的にセキュリティテストである場合は、戦略レベルより文脈を優先する (learned 2026-08-10) <!-- cid:build-and-test:c1 -->
## Deployment

<!-- Project-specific specialisation. -->

## Code Style

<!-- Project-specific specialisation. -->

## Tech Stack

<!-- Technology choices locked for this project. -->

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- 質問ファイルと構造化質問の本文はユーザーの使用言語（日本語）で記述し、構造マーカー（`## Sources`、`## Q<n>.`、`[Answer]:`）は英語形式のまま維持する — センサーの互換性を保つため。 (learned 2026-08-04) <!-- cid:intent-capture:c1 -->
- 方法論の用語（risk-first / value-first / dependency-first / walking-skeleton-first など）をそのまま構造化質問の選択肢ラベルにしない。最初から案件固有の具体例に置き換えて提示する。 (learned 2026-08-04) <!-- cid:scope-definition:c3 -->
- ガイドラインからの逸脱であっても、ユーザーが成立する理由を添えて選択した場合はそれを決定として扱い、同じ論点で押し返しを繰り返さない。 (learned 2026-08-04) <!-- cid:scope-definition:c5 -->
- リスクを列挙させたら、緩和策との対応表を作って照合する。重大と認められたリスクに緩和策が割り当たっていない場合は、未選択を「不要と決定された」と扱わず 1 回だけ確認する。 (learned 2026-08-04) <!-- cid:approval-handoff:c3 -->
- codekb を生成するスキャンは現 intent の関心事に絞らず、リポジトリ全体を走査した上で当該 intent に関わる部分のみ深掘りする。codekb は space レベルの永続ストアで将来のすべての intent が再利用するため、intent 専用に絞ると次回再スキャンが必要になる。 (learned 2026-08-04) <!-- cid:reverse-engineering:c2 -->
- pipeline / subagent のリンク間で大きな中間成果物を渡すときは、プロンプトに直書きせずファイルに書き出してパスを渡す。受け手が必要な節を再読でき、context budget のルール（内容ではなくパスを渡す）にも従える。 (learned 2026-08-04) <!-- cid:reverse-engineering:c1 -->
- ステージの consumes に列挙されていなくても、要件の抜けを判定するのに必要な事実が codekb の他ファイルにあるなら読む。code-quality-assessment.md にしかない事実（検証基盤の制約、変更経路上の既存欠陥）は architecture.md からは得られない。codekb は space レベルの共有ストアであり、読むこと自体に制約はない。 (learned 2026-08-05) <!-- cid:requirements-analysis:c2 -->
- 設計の選択肢を提示するときは、実コードのシグネチャと before/after のコード片を添える。「値を保持する型を導入」「public メソッド 2 つの追加」のような抽象的な説明では判断材料にならない。言語の制約（例: Java は戻り値型だけのオーバーロードを許さない）のような実装レベルの事実が判断を左右する。 (learned 2026-08-05) <!-- cid:application-design:c2 -->
- 順序ヒューリスティックを名乗る前に、その入力が実際に判別力を持つか確かめる。全 Unit が同一の成功指標に寄与し value と time criticality が定数になる場合、WSJF は risk-first に退化する。退化したフレームワークの名前を使うのは根拠を装飾するだけなので、退化した実態のほうを記録する。 (learned 2026-08-08) <!-- cid:delivery-planning:c4 -->
- 制約が何に掛かっているかを確かめてから選択肢を却下する。Java 8 制約は生成物のバイトコードと使用 API に掛かり、依存制約は compile スコープに掛かる。test スコープの選択肢を制約の文面だけで却下せず、ビルド JVM などを実測して裏を取る。 (learned 2026-08-08) <!-- cid:delivery-planning:c7 -->
- 上流成果物の表を額面どおり実装する前に、関連する ADR や実ソースの制約と整合するか確かめる。ADR-003 により where はリテラルを保持し続けるため、component-methods.md M-2 の whereValues をそのまま実装すると ? の個数が値と一致せず PreparedSql.of が必ず落ちる欠陥だった (learned 2026-08-09) <!-- cid:functional-design:c15 -->
- 「実ソースで確認した」と明記する数値は必ず実測する。上流の誤記載（メソッド数など）を未検証で転記しない (learned 2026-08-09) <!-- cid:functional-design:c18 -->
- 現行コードが「していない」と断定する前に該当箇所を実際に読んで確認する。DaoAdapter が executeQuery(String) を転送していないと書いたが、実際は転送していた (learned 2026-08-09) <!-- cid:functional-design:c19 -->
- sibling unit の実際の状態（フィールド名・メカニズム）を実ソースまたは確定成果物で検証してから前提を置く。存在しないフィールドを前提にすると複数のブロッキング指摘に波及する (learned 2026-08-09) <!-- cid:functional-design:c8 -->
- 「クラス A を直せばクラス B にも効く」という前提は、B の実際の継承関係を確認してから置く。DaoAdapter は Dao を継承しない委譲ラッパであり、Dao 側だけの変更ではこのリポジトリのどの DAO にも新経路が届かない設計になっていた (learned 2026-08-09) <!-- cid:functional-design:c9 -->
- 上流契約からの差分は「追加 N 点」のように個数で要約せず、新規メンバを 1 個ずつ ID 付きで列挙する。件数の要約は自己矛盾や過少申告の温床になる (learned 2026-08-09) <!-- cid:functional-design:c13 -->
- 同じ差分を複数の成果物に重複して書かない。一方を「全量の参照先」と明記し優先順位を固定することで、成果物間の食い違いを構造的に防ぐ (learned 2026-08-09) <!-- cid:functional-design:c14 -->
- バリデーションロジックを書く前に、対象が実在しうる全ての状態（境界値を含む）を実ソースで洗い出す。isFunction()==true かつ getFunctionName()==null という実在する状態に対し、誤って例外を投げる設計だった (learned 2026-08-09) <!-- cid:functional-design:c29 -->
- 上流の指示であっても、実際の DB・ドライバの制約に照らして実装可能か確認してから採用する。MySQL の LIMIT パラメータは整数必須で、クライアントサイド prepared statement では上流指示どおり実装すると構文エラーになった (learned 2026-08-09) <!-- cid:functional-design:c20 -->
- 方言固有経路と汎用経路で例外の型・継承関係を揃える。揃えないと呼び出し側の catch が両方を捕捉できず、方言経路だけ検知漏れになる (learned 2026-08-09) <!-- cid:functional-design:c25 -->
- 変更が既存の死んだコードを実行経路に載せる場合、その欠陥は「既存だから」という理由で免責されない (learned 2026-08-09) <!-- cid:functional-design:c27 -->
- per-unit ステージでも診断（memory.md）ファイルは unit ディレクトリを挟まずステージ単位で 1 本である。誤った場所に作ると aidlc-learnings.ts surface が候補ゼロを返す (learned 2026-08-09) <!-- cid:functional-design:c10 -->
- 実行不能な SQL を生成するような欠陥は、STRIDE の T（構文解釈）ではなく可用性（CIA の A）の要件として位置づけると、機械的に検証可能な要件として Build and Test への引き継ぎが明確になる (learned 2026-08-09) <!-- cid:nfr-requirements:c3 -->
- 要件の文言上の対象を厳密には超えていても、unit-of-work.md 等が明示的に記録した観測可能な挙動変更は、利用者向け文書（MIGRATION.md 等）のどこかに書く。書かないコストの方が大きい (learned 2026-08-09) <!-- cid:nfr-requirements:c6 -->
- レビューで挙動変更の帰属（何が原因で何が結果か）を訂正された場合、成果物の attribution も実装前後の比較で裏を取ってから書き直す。「Unit が新たに作った」のか「元から存在していたものを初めて文書化した」のかは実装比較でしか判別できない (learned 2026-08-09) <!-- cid:nfr-requirements:c8 -->
- JaCoCo 等のカバレッジゲート拡張の判断は、「既存クラスの部分書き換え」か「新規完結クラスの追加」かという Unit の性質で先例を選ぶ。前者は既存の未被覆コードごと測ることになり数字が意味を持たないが、後者は完結したクラスなので拡張が正当化できる (learned 2026-08-09) <!-- cid:nfr-requirements:c9 -->
- 破壊的変更を伴う移行ガイドの追記は、非破壊的な追記と視覚的に区別する（太字等で目立たせる）。利用者が読み飛ばすと既存の正当な呼び出しを壊しうるため (learned 2026-08-09) <!-- cid:nfr-requirements:c10 -->
- 利用者向け文書内の「回避策」「代替経路」の案内は、実装コードと同じ厳密さで実ソースの裏を取ってから書く。前提条件（例: 特定の状態が未登録であること）を欠いた案内は、別の誤った手順を示すことになる (learned 2026-08-09) <!-- cid:nfr-requirements:c11 -->
- 既定フォールバックに留まる経路を「外部実装だけの話」として要件の例外に分類する前に、自リポジトリ自身の既存インスタンス（DAO 等のサブクラス）がその例外に該当しないか確認する (learned 2026-08-09) <!-- cid:nfr-requirements:c12 -->
- 「sibling の最終番号に続けて採番する」という前提は、sibling 自身の直近のレビュー是正で番号が伸びている可能性があるため、sibling の成果物を実際に読んで確認してから使う。伝聞や以前の文書化時点の情報を信用しない (learned 2026-08-09) <!-- cid:nfr-requirements:c13 -->
- 同一セクションが sibling の決定を引用しつつ自身の判定基準を書く場合、その判定基準が引用した sibling の決定と矛盾していないか読み合わせる。他 Unit の決定を引用する記述は、その引用が自身の主張と両立するか確認してから書く (learned 2026-08-09) <!-- cid:nfr-requirements:c14a -->
- reviewer-scope フックは sibling ディレクトリの走査（Glob/Grep/LS）を拒否するが、dispatch の exempt list に列挙した個別ファイルパスへの直接 Read は通す。sibling 参照を許可する dispatch はファイルパス単位で列挙し、レビュアーには「Read で直接開け、ディレクトリを走査するな」と明記する (learned 2026-08-09) <!-- cid:nfr-requirements:c14b -->
- 設計の疑似コードが重複した private static を示唆していても、将来の乖離リスクを構造的に防げるなら共有の package-private クラスへの切り出しを優先する (learned 2026-08-10) <!-- cid:code-generation:c2 -->
- 実装計画（plan）のチェックリストになくても、上流成果物が明示している要件は実装する。plan は上流要件の全量を網羅しきらないことがあるため、自作の統合テストが実際に失敗することで欠落を検出する規律が有効 (learned 2026-08-10) <!-- cid:code-generation:c6 -->
- 前 Unit が安全性の懸念で見送った移行を、後続 Unit でそのまま踏襲しない。実装が進んで前提（未検査のリテラル実行経路など）が変わっていないかを確認してから判断する (learned 2026-08-10) <!-- cid:code-generation:c13 -->
- 上流成果物の主張（例: null は素通しさせる設計）であっても、実装フェーズでも実ソースに対して独立に再検証してから実装する。裏取りの省略は blocking 指摘を招く (learned 2026-08-10) <!-- cid:code-generation:c10 -->
- ドキュメント内の同一事実を複数箇所に重複して書かない。1 箇所を直しても他の箇所が取り残されると、レビューで blocking が繰り返される (learned 2026-08-10) <!-- cid:code-generation:c14 -->
- 複数 Unit/ステージにまたがる互換性保証（AC-11 等）は各段階の自己申告だけでは閉じない。ワークフローの最終ステージで横断的な統合監査を実施する (learned 2026-08-10) <!-- cid:build-and-test:c4 -->
- あるスコープ領域を担当する最後の Unit/担当者である場合、その未解決項目は「次への申し送り」ではなく「永続的に受容されたスコープ境界」として明示的に記録する。申し送り先が構造的に存在しない場合、未解決タスクのまま放置すると失われる (learned 2026-08-10) <!-- cid:build-and-test:c5 -->

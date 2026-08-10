# Intent Backlog — SQL パラメータ化（PreparedStatement 化）

proto-Unit（Units Generation 2.7 で正式な Unit of Work に精緻化される候補）を優先度順に並べたもの。優先度は MoSCoW で付与している。上流の `intent-statement.md` が定めた成功指標 SM-1〜SM-3 への寄与を各行に示す。

## Prioritization Framework

MoSCoW を用いる。RICE / WSJF は採用しない — 単独開発者・期限なし・利用者が自分自身という条件下では、Reach や Cost of Delay の入力値が意味のある差を生まないため。 [Q2] [Q5] [Q6]

## Prioritized Backlog

| # | proto-Unit | 優先度 | 寄与する成功指標 | Source |
|---|-----------|--------|------------------|--------|
| PU-1 | 単一値の比較条件（`=`, `<`, `>` など）のパラメータ化 | Must Have | SM-1, SM-3 | [Q2] |
| PU-2 | LIKE 条件のパラメータ化（ワイルドカードのエスケープを含む） | Must Have | SM-1, SM-3 | [Q2] |
| PU-3 | IN 句・複数値条件のパラメータ化 | Must Have | SM-1, SM-3 | [Q2] |
| PU-4 | INSERT / UPDATE の値のパラメータ化 | Must Have | SM-1, SM-3 | [Q2] |
| PU-5 | BLOB / バイナリ値の取り扱い | Must Have | SM-1 | [Q3] |
| PU-6 | DB 方言ごとの差異への対応（MySQL / Oracle / PostgreSQL） | Must Have | SM-1, SM-3 | [Q3] |
| PU-7 | 既存テストの移行（生成 SQL 文字列のアサーション → バインド値のアサーション） | Must Have | SM-2 | [Q3] |
| PU-8 | 静的解析（CodeQL）での SQL インジェクション指摘ゼロの達成 | Must Have | SM-3 | [Q3] |
| PU-9 | バインドできない位置（テーブル名・カラム名・ORDER BY 句）の注入不能化 | Should Have（暫定） | SM-3 | [Q3] |

## Walking Skeleton 候補

Walking-skeleton-first の方針により、最初の Bolt は 1 経路を端から端まで通すものになる。 [Q5]

その候補は **PU-1**（単一値の比較条件）である。理由は、SQL の値位置のうち最も単純で、組み立てから実行までの経路全体を最短で通せるため。ただし最初の Bolt の確定は Delivery Planning（2.8）が行う。

PU-1 が通った時点で確認されること: 値をバインドで運ぶ経路が端から端まで成立すること、および既存テストがその経路について引き続きグリーンであること。これは Q4 で未確定とされた依存関係の実態を明らかにする材料にもなる。 [Q4] [Q5]

## Dependencies

能力間の依存関係は本ステージでは確定していない。共通のバインド基盤が先に必要か（各条件種別がその上に乗る）、それとも各条件種別が独立に扱えるかは、Application Design（2.6）が判断する。 [Q4]

したがって上表の並び順は依存順ではなく分類順である。Delivery Planning（2.8）が Bolt 順序を決める際は、2.6 が確定した依存グラフを入力とすること。

観測された唯一の順序上の示唆は、Walking-skeleton-first の方針により PU-1 が最初に来るという点のみである。 [Q5]

## Out of Backlog

以下は Won't Have this time として明示的にスコープ外に置かれており、本バックログには含まれない。 [Q7]

- 利用側アプリケーションの修正
- パフォーマンス最適化（PreparedStatement キャッシュなど）
- 新規 DB 方言のサポート追加

## Assumptions & Open Questions

- [assumption] PU-9 の MoSCoW 区分（Should / Could / Won't）は明示的に選択されていない。Must Have ではないことのみが確定しており、暫定的に Should Have としている。 [Q3]
- [assumption] proto-Unit の粒度は Units Generation（2.7）で見直される。上表の 9 件がそのまま 9 個の Unit of Work になるとは限らず、統合または分割されうる。本バックログは候補の一覧であり、確定した Unit 分解ではない。 [Q4]
- [assumption] Walking skeleton の候補として PU-1 を挙げているが、確定は Delivery Planning（2.8）が行う。2.6 の依存分析の結果によっては、別の proto-Unit が最初の Bolt になりうる。 [Q4] [Q5]

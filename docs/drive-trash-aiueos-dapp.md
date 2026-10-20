# app-drive（可視性・更新）— itonami（dapp）上の分散型設計

状態: 第 1 スライス実装（2026-10-20）。正本はこの文書。
追跡: Linear JUN-128（親 JUN-116）。

## 位置づけ

app-drive は **Google Drive v3 と OneDrive の両方に互換な drive/files API**
（drive.etzhayyim.com、canonical は kotoba datomic `drive-v1`）。
workspace-compat の第 2 垂直（calendar と同型）。

## このスライス: 可視性・更新判定コア

| 層 | 実体 | 状態 |
|---|---|---|
| 判定コア guest | `aiueos/drive_trash.cljk` — list-judgment（**trashed は includeTrashed 明示のときだけ可視**）/ update-judgment（ifVersion 競合 = 412 / notFound）/ patch-admissible（白名单 **name / parentId / trashed / starred** の 4 つのみ、present のものだけ） | **実装済み・実測** |
| 記述子 | `aiueos/drive-trash.edn` | **実装済み** |
| parity | `test-shims/lg_drive/trash_parity_test.cljc` — **実物の handlers を bb で実行**（FakeDriveStore）して一致（trashed 更新→一覧除外/可視、ifVersion 競合、notFound） | **実装済み・実測** |

実測: `amu test aiueos/drive_trash.cljk` → **21/21**（jvm-kir / js / wasm）。
parity → 2 tests / 6 assertions / 0 failures（bb 実行）。

## 実測で判明した正本の事実（docs 記録）

- **競合判定のフィールドは drive では `ifVersion`**（calendar の `ifSequence`
  と同規則・別名。version は create 時に 0）。guest は引数名を競合用フラグの
  形で持ち、両者に共通の規則を判定する。
- **create の `trashed` はハードコード `false`** —— create 入力で trashed を
  与えても無視される。ゴミ箱化は update の patch（白名单内）経由のみ。
  parity でこの経路だけが機能することを実測。
- `files-create` の応答は `{"fileId" slug "file" {...}}`（slug は file の中で
  なくトップレベルの fileId）。
- handlers は `(:import [java.util Base64])` を持ち kbb ではロード不可 →
  parity は bb + test-shims（正本の .cljk→.cljc 拡張子コピー + kotoba.lang.text
  委譲）で実行。判定対象（handlers/mapping/store/ids）は実物。

## 未実装境界

- pagination / change feed（handlers の SSoT に含まれる他の挙動）の判定。
- parent グラフの整合（自己参照ループ等）は未判定。
- スキン変換（Drive v3 の files.list reshape）は CF Worker 側。
- mon（DNA）化、aiueos session 載せ（fleet 共通）。

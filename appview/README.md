# etzhayyim-project-drive App migration

このディレクトリは `legacy-runtime` 実装を残したまま、App 版を段階移行する配置先です。

## 実装済み components

- `drive-ui-qyizy2yz` に `drive-mcp-component` を内包
  - `drive-ntca7tit` の App 版
  - `POST /api/mcp` と `POST /{nanoid}/api/mcp`
  - 既存 UI 互換の `POST /api/drive.v1.DriveService/*` ブリッジを同梱
- `drive-app-component`
  - `drive-app-ee6jpfxx` の companion component
  - appshell/ナビゲーション設定を MCP tool として提供

## ルーティング方針

- backend は `https://[nanoid].etzhayyim.com/api/mcp` に統一。
- Drive は `https://ntca7tit.etzhayyim.com/api/mcp` を利用。

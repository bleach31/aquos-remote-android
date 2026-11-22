# AQUOS LAN Remote — LC-60XD35

SHARP AQUOS **LC-60XD35** のネットワーク操作用Androidアプリ。家庭内Wi-FiからテレビのTCPサーバへ直接接続する。Android TVリモコンのペアリング（6466/6467）は不要。

**2つのAPKを同時にビルドします。** Google Play不要、クラウド/広告/ログインなし。

| APK | アプリ名 | 音量 | 開発用コマンド送信 |
| --- | --- | --- | --- |
| `aquos-remote-parent.apk` | AQUOS リモコン | 0～60 | あり |
| `aquos-remote-kids-max25.apk` | AQUOS リモコン Kids | **0～25** | なし |

パッケージ名が異なるため同じAndroid端末に同時インストールできます。Android 8.0以降を想定。

## Kids版の制限

- スライダーも±1/±5ボタンも最大25に制限します。
- TCPコマンド送信時にも検査し、`VOLM26`以上や`RCKY33`（音量アップ）、任意コマンド送信を拒否します。
- 起動時にテレビの音量を照会し、25より大きければ25へ下げます。消音解除・入力変更等の前にも現在音量を照会・制限します。照会に失敗した場合は、危険な操作を中止します（電源オンは例外）。
- **これはアプリからの操作に対する制限です。** 純正リモコン・本体操作・他アプリで上げた音量は防げません。テレビ全体のペアレンタル制限ではありません。
- Kids版もIPアドレス/ポート設定は変更可能です。

## 接続設定とコマンド

- テレビIP: `192.168.68.74`（初期値。アプリ内で変更可能）
- TCP port: `10002`（初期値）
- ワイヤ形式: **4文字コマンド + 4文字パラメータ + `\r`**（8文字+CR）。例: `VOLM20  \r`
- 1操作につきTCP接続→コマンド送信→レスポンス（`OK` / `ERR` / 数値）受信→切断。
- 通信はワーカースレッド上で直列実行。外部ネットワークへの送信なし。

| 操作 | コマンド | 備考 |
| --- | --- | --- |
| 電源ON / OFF | `POWR1` / `POWR0` | スタンバイからのONはTV設定次第 |
| 音量取得 | `VOLM????` | 照会が通らない機種ではUI操作が制限される |
| 音量セット | `VOLM20` | Kids版は最大25 |
| 消音 / 解除 | `MUTE1` / `MUTE2` | |
| CH上下 | `CHUP` / `CHDW` | |
| 入力切替 / TV | `ITGD` / `ITVD` | |
| 入力1～5 | `IAVD1`～`IAVD5` | 物理端子との対応は機種依存 |
| メニュー / カーソル | `RCKY38` / `RCKY40`～`RCKY46` | **LC-60XD35では未検証**。応答がERRの場合は使えません |
| ホーム（通常版試験） | `RCKY39` | 他機種ではSmartCentral。日本のXD35では未確認 |

既に`VOLM20`のTCP制御が成功した環境が前提です。認証が必須の場合には現状対応していません。

## APKダウンロード

1. GitHubの [Actions](https://github.com/bleach31/aquos-remote-android/actions) を開く。
2. `Build Android APKs` の成功した最新実行を選ぶ。
3. 下の **Artifacts → `aquos-remote-apks`** をダウンロードしZIPを展開。
4. `aquos-remote-parent.apk` または `aquos-remote-kids-max25.apk` をスマホにコピーし、タップしてインストール。「この提供元のアプリを許可」が必要な場合があります。

PCにADBがある場合は `adb install aquos-remote-kids-max25.apk` でもOK。

タグ`v1.0.0`などをPushすると、2つのAPKを添付した[GitHub Releases](https://github.com/bleach31/aquos-remote-android/releases)を自動作成します。

**署名に関する注意:** 現段階はGitHub ActionsのデバッグAPKなので、異なる実行間のアップデートではAndroidの署名不一致が起き得ます。その場合は以前の版をアンインストール後、インストールしてください（設定は失われます）。継続的に更新するなら固定署名鍵をGitHub Secretsに保管する正式リリース署名へ移行します。

## 開発・テスト

- Java 17 / Android Gradle Plugin 8.7.3 / Gradle 8.9 / compileSdk 35 / minSdk 26。
- Android Studioで開く、または環境にGradleとSDKがあれば `gradle :app:assembleParentDebug :app:assembleKidsDebug`。
- Android SDKなしでプロトコルのスモークテスト: `bash scripts/test-protocol.sh`。
- ネットワーク権限は `INTERNET` のみ。Android TV/Google TV APIは使っていません。
- TVとスマホが同じ家庭内LANにいること。ゲストWi-Fiの端末分離があると接続できません。

## 制約

実機で確かめていないキーは機種差あり。AV入力番号やメニュー系キーはTV応答を見ながら調整してください。GitHub Actionsの実ビルド結果はActionsで確認してください。

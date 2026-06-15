# Carpet Bot API

Fabric Carpet fake players を HTTP/WebSocket JSON-RPC で操作するサーバーサイド mod です。

## Build

```bash
./gradlew build
```

ローカルに Java 21 が無い場合は、Nix 環境なら次でもビルドできます。

```bash
nix develop
gradle build
```

GitHub Actions でも pull request と push でビルドされ、`main` への push または手動実行では GitHub Release と jar artifact が作成されます。

成果物は `build/libs/carpet-bot-api-<version>.jar` です。サーバーの `mods/` にはこの mod、Fabric API、Carpet 本体を入れてください。

## Config

初回起動で `config/carpet-bot-api.json` が生成されます。

```json
{
  "enabled": false,
  "host": "0.0.0.0",
  "port": 8765,
  "websocketPort": 8766,
  "publicBaseUrl": "http://localhost:8765",
  "discordClientId": "",
  "discordClientSecret": "",
  "discordRedirectPath": "/oauth/callback",
  "discordRequiredGuildId": "",
  "sessionSecret": "change-me",
  "apiKeys": []
}
```

Discord Developer Portal 側の redirect URL は `publicBaseUrl + discordRedirectPath` にしてください。`discordRequiredGuildId` を指定すると、その Discord サーバーに所属しているユーザーだけが dashboard から API key を発行できます。

## Endpoints

- `GET /` dashboard
- `GET /login` Discord OAuth 開始
- `GET /oauth/callback` Discord OAuth callback
- `POST /api/keys` dashboard session から API key 発行
- `POST /rpc` Bearer API key 認証の JSON-RPC
- WebSocket JSON-RPC: `ws://host:websocketPort/ws?api_key=...` または `Authorization: Bearer ...`

## JSON-RPC examples

```json
{"jsonrpc":"2.0","id":1,"method":"bot.create","params":{"name":"bot_a","x":0,"y":80,"z":0,"yaw":0,"pitch":0,"dimension":"minecraft:overworld","gamemode":"survival"}}
```

```json
{"jsonrpc":"2.0","id":2,"method":"bot.action","params":{"name":"bot_a","action":"attack","mode":"continuous"}}
```

```json
{"jsonrpc":"2.0","id":3,"method":"bot.move","params":{"name":"bot_a","forward":1.0,"strafing":0.0,"sprinting":true}}
```

Supported methods: `bot.create`, `bot.remove`, `bot.list`, `bot.action`, `bot.move`, `bot.look`, `bot.turn`, `bot.drop`, `bot.slot`, `bot.mount`, `bot.dismount`, `bot.stop`, `bot.command`.

# Development

The dev loop runs a local Paper server with surf-hud and one or more vanilla clients that join it. It comes from
build-logic's `surf-hud.paper-dev` plugin; `surf-hud-paper-example` applies it. All commands run from the repo
root in Git Bash.

## Start

```bash
# 1. Paper server (port 25590, RCON 25591). Builds and loads surf-hud-paper and the example plugin.
./gradlew :surf-hud-paper-example:runServer

# 2. Client "Dev" (operator). Joins localhost:25590 with the HUD pack enabled.
./gradlew :surf-hud-paper-example:runDevClient

# 3. Optional second player "Dev2" (also operator; /deop Dev2 to test as a normal player).
./gradlew :surf-hud-paper-example:runDevClient -Pminecraft.username=Dev2
```

Start the server first. A client needs the pack the server generates on startup; if it starts before the pack
exists, it drops the pack (the build prints a warning) and you have to restart that client.

The server lives in `surf-hud-paper-example/run/paper`, the clients in `run/client/<name>`. Both are ignored by
git. The clients are separate vanilla installs in offline mode; your own Minecraft installation and launcher
profiles are not touched.

## Change definitions

Edit the YAML files in `run/paper/plugins/surf-hud-paper/` (see `docs/hud-config.md`), then:

```bash
./gradlew :surf-hud-paper-example:devRcon --command="hud reload"
```

Text, placeholder and timing changes are visible at once. If the reload logs a new pack SHA-1 (new or moved
images, texts, anchors), press **F3+T** in each client. The clients read the pack directly from the server's
`plugins/surf-hud-paper/build/pack` through a link in `resourcepacks/surf-hud`, so no copy step is needed.

`/hud reload` answers asynchronously, so RCON prints `(no reply)`; the result is in the server console.

## RCON

```bash
./gradlew :surf-hud-paper-example:devRcon --command="list"
./gradlew :surf-hud-paper-example:devRcon --command="hud popup Dev vote title=Test left=Notch right=jeb_"
./gradlew :surf-hud-paper-example:devRcon --command="stop"
```

Changed plugin code needs a server restart: `stop`, then `runServer` again (it rebuilds both jars).

## Options

| Gradle property | Default | Effect |
| --- | --- | --- |
| `-Pminecraft.username=<name>` | `Dev` | Client username and game directory |
| `-Pminecraft.joinServer=false` | `true` | Client starts on the title screen |
| `-Pminecraft.enableResourcePacks=false` | `true` | Client starts with only the vanilla pack |
| `-Pminecraft.serverPort=<port>` | `25590` | Server port the client joins |
| `-Pminecraft.rconPort=<port>` | `25591` | RCON port |

In a build script, `minecraftDev { ... }` sets the client and shared server values (`acceptEula`,
`serverProperties`, `operators`, `clientOptions`, `rconPassword`) and `paperDev { ... }` the Paper specifics
(`runDirectory`, `hudPluginProject`, `hudPackDirectory`, `withSurfApi`).

## How the pack link works

On Windows the link is a directory junction (no admin rights needed). On other systems it is a symbolic link,
and its target is added to the client's `allowed_symlinks.txt`, which the game requires for symbolic links; this
path has not been tried on Linux or macOS yet. A real folder at `resourcepacks/surf-hud` (for example a copy from
an older setup) is never deleted automatically: the client start fails with a message, delete that folder once.

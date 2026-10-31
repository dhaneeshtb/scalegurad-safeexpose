# SafeExpose

Expose an app running on your machine through your ScaleGuard server, with automatic HTTPS, in one command.

```sh
safeexpose 8080              # https://app-8080.<your-domain>
safeexpose 8080 my-app       # https://my-app.<your-domain>
```

Press Ctrl+C to stop; the tunnel is removed. If the connection drops it reconnects by itself.

## Install (no source code, no build)

Needs Java 11+ (the installer and the `safeexpose` command warn you, with install commands for macOS, Linux and Windows, if Java is missing or too old). One line downloads the prebuilt jar and adds a `safeexpose` command:

```sh
curl -fsSL https://raw.githubusercontent.com/dhaneeshtb/scalegurad-safeexpose/main/install.sh | sh
```

Windows (PowerShell):

```powershell
irm https://raw.githubusercontent.com/dhaneeshtb/scalegurad-safeexpose/main/install.ps1 | iex
```

The jar is downloaded from [`dist/`](dist) in this repository and checked against its published SHA-256 before it is installed. The command goes to `~/.local/bin` (make sure it is on your
`PATH`); change the places with `SAFEEXPOSE_HOME` / `SAFEEXPOSE_BIN`, or the download with `SAFEEXPOSE_URL`.

### Publishing a new version (maintainers)

```sh
./release.sh                 # builds dist/safeexpose.jar and its checksum
git add dist && git commit -m "release" && git push
```

### From source

```sh
./safeexpose 8080          # builds on first use (needs Maven); safeexpose.cmd on Windows
```

## First run

The first time, SafeExpose asks for your ScaleGuard server, username and password (typed hidden), checks them, and saves them to
`~/.tunnel_config` in your home folder (readable by you only). If the saved password stops working it asks for the new one and saves that. Or configure ahead of time:

```sh
safeexpose configure                         # prompts
safeexpose configure sg.example.com alice s3cret
safeexpose show                              # server and user; the password is hidden
```

For scripts and CI, skip the file: set `SAFEEXPOSE_SERVER`, `SAFEEXPOSE_USER` and `SAFEEXPOSE_PASSWORD`.
A server given without `http://` or `https://` is assumed to be https. `safeexpose help` lists everything.

## Commands

| Command | What it does |
|---|---|
| `safeexpose <port> [name]` | expose `localhost:<port>`; the name defaults to `app-<port>` |
| `safeexpose <host:port> [name]` | expose an app on another machine, e.g. `192.168.1.20:3000` |
| `safeexpose <port> [name] --domain example.com` | publish as `<name>.example.com` instead of the server's default |
| `safeexpose domain example.com` | save a default domain |
| `safeexpose delete <name>` | remove a tunnel |
| `safeexpose configure [server user password]` | save settings |
| `safeexpose show` | show saved settings |

The older `safeexpose start <name> <port>` still works.

## Settings

Nothing is built into the program: the server address, login, domain and SSH port come from you or from the server (the
server tells the client which host and SSH port to use). Optional tuning, as environment variables or keys in
`~/.tunnel_config`: `SAFEEXPOSE_SSH_PORT` (only needed with an older server that does not report it),
`SAFEEXPOSE_RECONNECT_SECONDS` (default 10), `SAFEEXPOSE_CONNECT_SECONDS` (30), `SAFEEXPOSE_AUTH_SECONDS` (10).

## Choosing the public name

The address is `https://<name>.<domain>`. The domain is, in order: `--domain` on the command (or `SAFEEXPOSE_DOMAIN`, or the one
saved with `safeexpose domain`), then the server's `scaleguard.tunnel.domain` setting, then the first domain the server serves
(Admin UI → DNS → *Domains served*, e.g. `app.dhaneesh.in` gives `my-app.app.dhaneesh.in`), then a DNS `base` record, then the
server's own host name. Whatever you pick needs DNS (a wildcard `*.example.com` record to the gateway) so
the name resolves, and the gateway orders its certificate automatically.

## How it works

SafeExpose signs in to the server (`POST /signin`), asks it to create the route and DNS name (`POST /tunnel`, see the
ScaleGuard [API reference](https://github.com/dhaneeshtb/scaleguard/blob/main/docs/API.md)), then opens an SSH reverse tunnel
to the server (the host and SSH port the server reports) that carries traffic for `https://<name>.<domain>` to your local port.
WebSockets (for example a dev server's hot reload) go through it too. The server needs a domain to publish under (see
*Choosing the public name*).

## Troubleshooting

- **Login failed**: run `safeexpose configure` again.
- **The server did not create the tunnel**: the message printed above it comes from the server (for example, an invalid `--domain`).
- **Cannot connect**: the SSH port the server reports must be reachable from your machine (the server's firewall must allow it).
- **The name does not resolve**: the domain's DNS must point at the gateway (a wildcard record, or a domain listed under DNS → Domains served).
- **Java is missing**: install Java 11+; the installer prints the command for your system.

MIT License.

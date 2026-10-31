# SafeExpose

Expose an app running on your machine through your ScaleGuard server, with automatic HTTPS, in one command.

```sh
safeexpose 8080              # https://app-8080.<your-domain>
safeexpose 8080 my-app       # https://my-app.<your-domain>
```

Press Ctrl+C to stop; the tunnel is removed. If the connection drops it reconnects by itself.

## Install

Needs Java 11+ (and Maven, to build once). Use the launcher script in this folder; it builds the jar on first use and rebuilds it when the source changes:

```sh
./safeexpose 8080                      # run it from here
ln -s "$PWD/safeexpose" ~/.local/bin/safeexpose   # optional: use it from anywhere (~/.local/bin must be on your PATH)
```

On Windows use `safeexpose.cmd`. To use a prebuilt jar, set `SAFEEXPOSE_JAR=/path/to/safeexpose.jar` (nothing is built then).

## First run

The first time, SafeExpose asks for your ScaleGuard server, username and password, checks them, and saves them to
`~/.tunnel_config` (readable by you only). Or configure ahead of time:

```sh
safeexpose configure                         # prompts
safeexpose configure sg.example.com alice s3cret
safeexpose show                              # server and user; the password is hidden
```

For scripts and CI, skip the file: set `SAFEEXPOSE_SERVER`, `SAFEEXPOSE_USER` and `SAFEEXPOSE_PASSWORD`.
A server given without `http://` or `https://` is assumed to be https.

## Commands

| Command | What it does |
|---|---|
| `safeexpose <port> [name]` | expose `localhost:<port>`; the name defaults to `app-<port>` |
| `safeexpose delete <name>` | remove a tunnel |
| `safeexpose configure [server user password]` | save settings |
| `safeexpose show` | show saved settings |

The older `safeexpose start <name> <port>` still works.

## How it works

SafeExpose signs in to the server (`POST /signin`), asks it to create the route and DNS name (`POST /tunnel`, see the
ScaleGuard [API reference](https://github.com/dhaneeshtb/scaleguard/blob/main/docs/API.md)), then opens an SSH reverse tunnel
to the server (port 2222) that carries traffic for `https://<name>.<base-domain>` to your local port. The server needs a
base DNS record for the tunnel domain.

## Troubleshooting

- **Login failed**: run `safeexpose configure` again.
- **The server did not create the tunnel**: the message printed above it comes from the server (for example, no base DNS zone yet).
- **Cannot connect**: the server's SSH port (2222) must be reachable from your machine.

MIT License.

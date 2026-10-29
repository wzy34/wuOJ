# Deploying wuOJ

Everything runs on one Linux server with Docker Compose:

```
Internet ──80/443──▶ web (Caddy: HTTPS, the built web app, /api/* proxied)
                        └──▶ backend :8080 (API + judge worker) ──▶ postgres, redis, rabbitmq
                                   └──▶ host Docker daemon ──▶ judge containers (wuoj/judge-cpp:prod)
```

Only ports 80 and 443 are public. The backend's port 8080 is bound to `127.0.0.1` for health checks; PostgreSQL,
Redis and RabbitMQ are reachable only inside the Compose network.

| File                  | Purpose                                                                    |
| --------------------- | -------------------------------------------------------------------------- |
| `compose.yaml`        | The five services, volumes and settings                                    |
| `.env.example`        | Template for `.env`: domain, passwords, data directory, judge slots        |
| `backend.Dockerfile`  | Builds the backend jar and runs it on a Java 25 JRE                        |
| `web.Dockerfile`      | Builds the web app and packages it with Caddy                              |
| `Caddyfile`           | HTTPS, static files with client-side routing, the `/api` proxy (SSE-safe)  |
| `deploy.sh`           | Builds the judge image and (re)starts everything: first deploy and updates |
| `backup.sh`           | Database dump and test-data archive, kept 14 days                          |

## 1. Get a server

On Yisu Cloud (亿速云), buy a **cloud server (云服务器)** in a **US region**: US West (Silicon Valley or Los Angeles) or
US East, whichever is closer to your users. Servers outside mainland China need no ICP filing (备案).

- **Size:** see [Server size](#server-size). The cheapest workable choice is a lightweight application server
  (轻量应用服务器) with 2 cores and 2–4 GB.
- **Image:** Ubuntu 24.04 (or 22.04, or Debian 12), 64-bit. Not an application image (WordPress, LAMP, ...).
- **Virtualization:** it must be KVM (ask support: 是否KVM虚拟化，能否安装Docker). OpenVZ/LXC servers can't run Docker
  properly.
- **Security group (安全组):** allow inbound TCP 22, 80 and 443, and UDP 443. Nothing else.
- Note the server's public IP address.

### Server size

Measured on the production stack: the services use about 650 MB idle and 700 MB while judging (backend 500 MB,
RabbitMQ 100 MB, PostgreSQL 55 MB, Redis and Caddy 15 MB each). Each judge adds up to about 700 MB while it
compiles (a 512 MB limit plus headroom) or runs a program with a high memory limit.

| Server              | Judges (`OJ_JUDGE_SLOTS`) | Good for                                                   |
| ------------------- | ------------------------- | ---------------------------------------------------------- |
| 1 core, 1 GB        | none                      | Not enough: compiling runs out of memory                   |
| 2 cores, 2 GB       | 1 (`1`), with swap        | A class or a small group; submissions queue at busy times  |
| 2 cores, 4 GB       | 1 (`1`)                   | The same, with room to spare; the best value               |
| 4 cores, 8 GB       | 2 (`2,3`)                 | Contests with many people submitting at once               |

Submissions over capacity wait in the queue rather than fail, so a small server only means longer waits at peaks.

Bandwidth matters more than it seems: the code editor is about 1.3 MB to download, which takes several seconds at
3 Mbps and more when several people open problems at once. Yisu's lightweight plans come in 15 and 30 Mbps versions
at the same prices as 3 Mbps; take one of those.

Lightweight servers usually have shared CPUs, so run times vary somewhat. Set time limits with some margin, and move
to dedicated CPUs (独享) if contests need precise timing.

**On 4 GB or less, add swap before deploying.** Building the backend and the web app takes more memory than running
them. The judge containers are not allowed to swap, so this doesn't affect verdicts or timing:

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

## 2. Point a domain at it

At your domain registrar, add an **A record** for the name you want (e.g. `oj` for `oj.example.com`, or `@` for the
bare domain) with the server's IP. Check it with `ping oj.example.com` before going on: Caddy can only get an HTTPS
certificate once the name resolves to the server.

No domain yet? Set `SITE_ADDRESS=:80` in step 4 to test on `http://<server-ip>`, then switch to the domain and run
`./deploy/deploy.sh` again. Don't use plain HTTP for real users: passwords would travel unencrypted.

## 3. Prepare the server

SSH in as root (`ssh root@<server-ip>`, password from the Yisu console), then:

```bash
apt-get update && apt-get upgrade -y
curl -fsSL https://get.docker.com | sh           # Docker Engine and the Compose plugin
docker run --rm hello-world                       # check it works

# Firewall (the security group already filters; this is a second layer).
apt-get install -y ufw
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw enable
```

Ports that Docker publishes bypass ufw, which is why `compose.yaml` publishes only 80, 443 and `127.0.0.1:8080`.

Recommended: log in with an SSH key instead of the password (`ssh-copy-id root@<server-ip>` from your computer), then
set `PasswordAuthentication no` in `/etc/ssh/sshd_config` and `systemctl restart ssh`.

## 4. Get the code and configure it

Commit and push your work first; the server deploys whatever is in the repository.

```bash
cd /opt
git clone https://github.com/wzy34/online-judgement-platform.git wuoj   # private repo: use a GitHub token or deploy key
cd wuoj
cp deploy/.env.example deploy/.env
openssl rand -hex 24      # run three times: one password each for PostgreSQL, Redis and RabbitMQ
nano deploy/.env          # set SITE_ADDRESS, the three passwords, OJ_JUDGE_SLOTS
chmod 600 deploy/.env
```

For `OJ_JUDGE_SLOTS`, see how many CPUs there are with `nproc` and pick from [Server size](#server-size): `1` on
2 CPUs, `2,3` on 4 CPUs with 8 GB.

## 5. Deploy

```bash
./deploy/deploy.sh
```

The first run takes several minutes (it downloads images and builds the backend and web app). Then check:

```bash
curl -s localhost:8080/actuator/health        # every component "UP", including docker
docker compose -f deploy/compose.yaml logs web | grep -i certificate   # "certificate obtained successfully"
```

and open `https://oj.example.com`.

## 6. Create the first admin and add problems

Write the admin account to a CSV file, import it, and delete the file (it holds the password in plain text):

```bash
mkdir -p /srv/wuoj/import
cat > /srv/wuoj/import/admins.csv <<'EOF'
username,password,role,enabled
yourname,a-long-unique-password,ADMIN,true
EOF
cd /opt/wuoj/deploy
docker compose run --rm -v /srv/wuoj/import:/import:ro backend import-users /import/admins.csv
rm /srv/wuoj/import/admins.csv
```

Sign in and change the password in the web app. Never import `dev-data/users.csv` here: its passwords are public.

Problems can be created in the web app (`/admin/problems`), or imported from packages copied to the server (for the
package format, see `backend/README.md`):

```bash
scp -r problems/ root@<server-ip>:/srv/wuoj/import/          # from your computer
docker compose run --rm -v /srv/wuoj/import:/import:ro backend import-problems /import/problems
```

To start with the six sample problems, import `/opt/wuoj/dev-data/problems` the same way. They are hidden until an
admin makes them visible.

## 7. Backups

```bash
crontab -e
# add:
30 4 * * * /opt/wuoj/deploy/backup.sh >> /var/log/wuoj-backup.log 2>&1
```

Backups go to `/srv/wuoj/backups` and are kept for 14 days. They don't survive losing the server, so copy them
elsewhere regularly, e.g. from your computer: `rsync -a root@<server-ip>:/srv/wuoj/backups/ ~/wuoj-backups/`.

To restore (this replaces the current data):

```bash
cd /opt/wuoj/deploy
docker compose stop backend
docker compose exec -T postgres pg_restore --username=wuoj --dbname=wuoj --clean --if-exists < /srv/wuoj/backups/db-XXXX.dump
tar --directory=/srv/wuoj -xzf /srv/wuoj/backups/testdata-XXXX.tar.gz
docker compose start backend
```

## Updating

```bash
cd /opt/wuoj && git pull && ./deploy/deploy.sh
```

Database migrations run when the backend starts. Submissions being judged during the restart are picked up again
once it is back. If a release changes how sessions are stored, everyone has to sign in again (see
`backend/README.md`, "Upgrading").

## Day to day

```bash
cd /opt/wuoj/deploy
docker compose ps                       # what is running
docker compose logs -f backend          # follow the backend's log (also: web, postgres, rabbitmq)
docker compose restart backend          # restart one service
docker compose down                     # stop everything (data is kept in volumes and /srv/wuoj)
```

Data lives in Docker volumes (`wuoj_postgres-data`, `wuoj_redis-data`, `wuoj_rabbitmq-data`, `wuoj_caddy-data`)
and in `/srv/wuoj` (hidden tests, judge work files, backups). `docker compose down -v` deletes the volumes, including
the database.

## Optional: gVisor

Judge containers already run without network, with a read-only root, an unprivileged user and resource limits.
[gVisor](https://gvisor.dev/docs/user_guide/install/) adds a user-space kernel between submissions and the host kernel.
Install it with the apt instructions on that page, then:

```bash
runsc install && systemctl restart docker
# in deploy/.env: OJ_JUDGE_RUNTIME=runsc
./deploy/deploy.sh
```

This hasn't been tested with wuOJ yet. The runner reads cgroup v2 statistics to recognise memory-limit kills, and
gVisor's cgroup support differs from Linux's. Before relying on it, submit programs that should get AC, WA, TLE, MLE
and RE and check each verdict. If any is wrong, set `OJ_JUDGE_RUNTIME=` again and redeploy.

## Troubleshooting

- **The site doesn't load / no certificate:** the domain must resolve to the server, and TCP 80 and 443 must be open
  in the Yisu security group (Let's Encrypt connects to port 80 or 443 to verify). See `docker compose logs web`.
- **Health shows docker DOWN:** the judge image is missing; `./deploy/deploy.sh` builds it.
- **Every visitor gets rate limited together:** client addresses come from Caddy's `X-Forwarded-For`; check that
  `SERVER_FORWARD_HEADERS_STRATEGY: native` is still set in `compose.yaml`.
- **Run times jump around:** use dedicated CPUs, and pin judges with `OJ_JUDGE_SLOTS` to CPUs nothing else uses.

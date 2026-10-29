#!/usr/bin/env bash
# Builds and (re)starts wuOJ from this checkout: the judge image, the backend and the web server.
# Run it for the first deployment and after every `git pull`; database migrations run when the backend starts.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
    echo "deploy/.env is missing: copy deploy/.env.example to deploy/.env and fill it in" >&2
    exit 1
fi

docker build --tag wuoj/judge-cpp:prod ../judge/cpp
docker compose up --detach --build --remove-orphans
docker image prune --force > /dev/null
docker compose ps

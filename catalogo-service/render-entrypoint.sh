#!/bin/sh
set -eu

if [ -n "${DATABASE_URL:-}" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
  database_target="${DATABASE_URL#*://}"
  database_target="${database_target#*@}"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${database_target}"
fi

exec java -jar app.jar
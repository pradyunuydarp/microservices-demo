#!/usr/bin/env bash
# End-to-end check of the running system, through the API gateway.
#
# Start the stack first:
#   ./mvnw -q package -DskipTests
#   docker compose up -d --build --wait
# Then run:
#   scripts/smoke.sh
#
# Needs only bash, curl and docker compose (no jq).
set -euo pipefail
cd "$(dirname "$0")/.."

GATEWAY=${GATEWAY:-http://localhost:8080}
ORDER_DEBUG=${ORDER_DEBUG:-http://localhost:8083} # order-service's debug port, for /actuator
MENU_DEBUG=${MENU_DEBUG:-http://localhost:8081}   # menu-service's debug port, for /actuator

step() { printf '\n== %s\n' "$*"; }
ok() { printf '   ok: %s\n' "$*"; }
fail() {
  printf '\nFAIL: %s\n' "$*" >&2
  exit 1
}

# call METHOD PATH [JSON]: calls the gateway and sets STATUS, BODY, SECONDS_TAKEN, LOCATION.
HEADERS=$(mktemp)
trap 'rm -f "$HEADERS"' EXIT
call() {
  local args=(-s -X "$1" -D "$HEADERS" -w '\n%{http_code} %{time_total}')
  [[ $# -ge 3 ]] && args+=(-H 'Content-Type: application/json' -d "$3")
  local out
  out=$(curl "${args[@]}" "$GATEWAY$2") || fail "could not reach $GATEWAY$2 (is the stack running?)"
  BODY=${out%$'\n'*}
  read -r STATUS SECONDS_TAKEN <<<"${out##*$'\n'}"
  LOCATION=$(tr -d '\r' <"$HEADERS" | sed -n 's/^[Ll]ocation: //p')
}
expect_status() { [[ $STATUS == "$1" ]] || fail "expected HTTP $1, got $STATUS: $BODY"; }
expect_body() { [[ $BODY == *"$1"* ]] || fail "expected the response to contain $1, got: $BODY"; }
faster_than() { awk -v t="$SECONDS_TAKEN" -v max="$1" 'BEGIN { exit !(t < max) }'; }

# Circuit breaker state of one downstream, read from order-service's actuator.
breaker_state() {
  curl -s "$ORDER_DEBUG/actuator/circuitbreakers" |
    grep -o "\"$1\":{[^}]*}" | grep -o '"state":"[A-Z_]*"' | cut -d'"' -f4
}
order_json() { printf '{"customerId": %s, "items": [%s]}' "$1" "$2"; }

step "1. List the menu"
call GET /api/menu
expect_status 200
expect_body '{"id":1,"name":"Masala Dosa","price":60.00,"available":true}'
expect_body '{"id":3,"name":"Filter Coffee","price":20.00,"available":true}'
ok "menu has Masala Dosa (60.00) and Filter Coffee (20.00)"

step "2. Register a customer"
call POST /api/customers "{\"name\": \"Smoke Test\", \"email\": \"smoke-$(date +%s%N)@campus.edu\"}"
expect_status 201
CUSTOMER_ID=$(grep -o '"id":[0-9]*' <<<"$BODY" | head -1 | cut -d: -f2)
ok "customer $CUSTOMER_ID created"

step "3. Place an order: 2 x Masala Dosa + 1 x Filter Coffee"
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 1, "quantity": 2}, {"menuItemId": 3, "quantity": 1}')"
expect_status 201
expect_body '"total":140.00'
ok "201 Created, total 140.00, Location: $LOCATION"
[[ $LOCATION == "$GATEWAY/api/orders/"* ]] || fail "Location should point at the gateway, got: $LOCATION"

step "4. Fetch the order back (follow the Location header)"
call GET "${LOCATION#"$GATEWAY"}"
expect_status 200
expect_body '"name":"Masala Dosa","unitPrice":60.00,"quantity":2,"lineTotal":120.00'
expect_body '"total":140.00'
ok "order has the name/price snapshot and total 140.00"

step "5. Business rules: unknown customer and sold-out item give 422"
call POST /api/orders "$(order_json 999999 '{"menuItemId": 1, "quantity": 1}')"
expect_status 422
ok "unknown customer -> 422"
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 6, "quantity": 1}')"
expect_status 422
expect_body 'is not available'
ok "sold-out Mango Lassi -> 422"

step "6. Break it: stop menu-service"
docker compose stop menu-service
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 1, "quantity": 1}')"
expect_status 503
expect_body '"service":"menu-service"'
faster_than 3 || fail "503 took ${SECONDS_TAKEN}s, expected under 3s (timeouts not working?)"
ok "503 in ${SECONDS_TAKEN}s: $BODY"

step "7. Keep ordering until the circuit breaker opens"
for _ in 1 2 3 4; do
  call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 1, "quantity": 1}')"
  expect_status 503
done
STATE=$(breaker_state menu-service)
[[ $STATE == OPEN ]] || fail "expected menu-service circuit breaker OPEN, got '$STATE'"
ok "circuit breaker menu-service: $STATE"
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 1, "quantity": 1}')"
expect_status 503
expect_body 'circuit breaker is open'
faster_than 0.5 || fail "an open circuit should answer at once, took ${SECONDS_TAKEN}s"
ok "with the circuit OPEN, 503 comes back in ${SECONDS_TAKEN}s without calling menu-service"

step "8. Recover: start menu-service again"
docker compose start menu-service
printf '   waiting for menu-service to be healthy'
for _ in $(seq 60); do
  curl -sf "$MENU_DEBUG/actuator/health" >/dev/null && break
  printf '.'
  sleep 2
done
echo
printf '   placing orders until the circuit breaker lets calls through again'
STATUS=none
for _ in $(seq 45); do
  call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 1, "quantity": 1}')"
  [[ $STATUS == 201 ]] && break
  printf '.'
  sleep 2
done
echo
expect_status 201
ok "orders work again (circuit breaker menu-service: $(breaker_state menu-service))"
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 3, "quantity": 1}')"
expect_status 201
STATE=$(breaker_state menu-service)
[[ $STATE == CLOSED ]] || fail "expected menu-service circuit breaker CLOSED after recovery, got '$STATE'"
ok "after the trial calls succeeded, circuit breaker menu-service: $STATE"

printf '\nPASS: menu, customers, orders, 422 rules, fast 503 with circuit breaker, and recovery all work.\n'

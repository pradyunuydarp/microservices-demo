#!/usr/bin/env bash
# End-to-end check of the running system, through the API gateway.
# In testing terms this script is a "driver": it strings calls to several services
# together into real flows and checks every answer.
#
# Start the stack first:
#   ./mvnw -q package -DskipTests
#   docker compose up -d --build --wait
# Then run:
#   scripts/smoke.sh
#
# The steps follow the automated testing lifecycle: Setup -> Execute -> Verify -> Teardown.
# Needs only bash, curl and docker compose (no jq).
set -euo pipefail
cd "$(dirname "$0")/.."

GATEWAY=${GATEWAY:-http://localhost:8080}
ORDER_DEBUG=${ORDER_DEBUG:-http://localhost:8083} # order-service's debug port, for /actuator
MENU_DEBUG=${MENU_DEBUG:-http://localhost:8081}   # menu-service's debug port, for /actuator

# The dev-only OAuth 2.0 clients from auth-service's application.yml (throwaway local values).
APP_CLIENT=canteen-app:canteen-app-secret
ADMIN_CLIENT=canteen-admin:canteen-admin-secret

step() { printf '\n== %s\n' "$*"; }
ok() { printf '   ok: %s\n' "$*"; }
fail() {
  printf '\nFAIL: %s\n' "$*" >&2
  exit 1
}

# Teardown that runs even when a check fails half-way: delete the temp file and never
# leave menu-service stopped.
HEADERS=$(mktemp)
MENU_STOPPED=no
teardown() {
  rm -f "$HEADERS"
  if [[ $MENU_STOPPED == yes ]]; then docker compose start menu-service >/dev/null 2>&1 || true; fi
}
trap teardown EXIT

# call METHOD PATH [JSON]: calls the gateway and sets STATUS, BODY, SECONDS_TAKEN, LOCATION.
# Sends "Authorization: Bearer $TOKEN" when TOKEN is set.
TOKEN=
call() {
  local args=(-s -X "$1" -D "$HEADERS" -w '\n%{http_code} %{time_total}')
  [[ -n $TOKEN ]] && args+=(-H "Authorization: Bearer $TOKEN")
  [[ $# -ge 3 ]] && args+=(-H 'Content-Type: application/json' -d "$3")
  local out
  out=$(curl "${args[@]}" "$GATEWAY$2") || fail "could not reach $GATEWAY$2 (is the stack running?)"
  BODY=${out%$'\n'*}
  read -r STATUS SECONDS_TAKEN <<<"${out##*$'\n'}"
  LOCATION=$(tr -d '\r' <"$HEADERS" | sed -n 's/^[Ll]ocation: //p')
}
# request_token CLIENT_ID:SECRET SCOPES: the OAuth 2.0 client-credentials grant, through
# the gateway. Sets STATUS and BODY, and ACCESS_TOKEN when it worked.
request_token() {
  local out
  out=$(curl -s -u "$1" -d grant_type=client_credentials --data-urlencode "scope=$2" \
    -w '\n%{http_code}' "$GATEWAY/oauth2/token") || fail "could not reach $GATEWAY/oauth2/token"
  BODY=${out%$'\n'*}
  STATUS=${out##*$'\n'}
  ACCESS_TOKEN=$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' <<<"$BODY")
}
expect_status() { [[ $STATUS == "$1" ]] || fail "expected HTTP $1, got $STATUS: $BODY"; }
expect_body() { [[ $BODY == *"$1"* ]] || fail "expected the response to contain $1, got: $BODY"; }
faster_than() { awk -v t="$SECONDS_TAKEN" -v max="$1" 'BEGIN { exit !(t < max) }'; }

# Circuit breaker state of one downstream, read from order-service's actuator.
# Prints nothing (instead of stopping the script) when it cannot be read.
breaker_state() {
  { curl -s "$ORDER_DEBUG/actuator/circuitbreakers" |
    grep -o "\"$1\":{[^}]*}" | grep -o '"state":"[A-Z_]*"' | cut -d'"' -f4; } || true
}
order_json() { printf '{"customerId": %s, "items": [%s]}' "$1" "$2"; }

step "1. List the menu (public: no token needed)"
call GET /api/menu # Execute
expect_status 200  # Verify
expect_body '{"id":1,"name":"Masala Dosa","price":60.00,"available":true}'
expect_body '{"id":3,"name":"Filter Coffee","price":20.00,"available":true}'
ok "menu has Masala Dosa (60.00) and Filter Coffee (20.00)"

step "2. Security: no token, no order"
call POST /api/orders "$(order_json 1 '{"menuItemId": 1, "quantity": 1}')"
expect_status 401
grep -qi '^WWW-Authenticate: Bearer' "$HEADERS" || fail "a 401 should say WWW-Authenticate: Bearer"
ok "POST /api/orders without a token -> 401, WWW-Authenticate: Bearer"
request_token canteen-app:wrong-secret "orders customers"
expect_status 401
expect_body '"error":"invalid_client"'
ok "token request with a wrong client secret -> 401 invalid_client"

step "3. Security: get a token, then use it"
request_token "$APP_CLIENT" "orders customers" # Setup: the auth service creates a token
expect_status 200
expect_body '"token_type":"Bearer"'
[[ -n $ACCESS_TOKEN ]] || fail "no access_token in: $BODY"
APP_TOKEN=$ACCESS_TOKEN
ok "canteen-app got a JWT with scopes 'orders customers' (${#APP_TOKEN} characters)"
TOKEN=$APP_TOKEN
call POST /api/menu '{"name": "Smoke Idli", "price": 30.00, "available": true}'
expect_status 403
ok "canteen-app may not change the menu (no menu.write scope) -> 403"
request_token "$ADMIN_CLIENT" "menu.write"
expect_status 200
TOKEN=$ACCESS_TOKEN
call POST /api/menu '{"name": "Smoke Idli", "price": 30.00, "available": true}'
expect_status 201
NEW_ITEM=${LOCATION#"$GATEWAY"}
ok "canteen-admin with menu.write -> 201, Location: $LOCATION"
call DELETE "$NEW_ITEM" # Teardown: remove the item this check added
expect_status 204
ok "and deleted it again -> 204"
TOKEN=$APP_TOKEN # from here on, every call carries the canteen app's token

step "4. Register a customer"
call POST /api/customers "{\"name\": \"Smoke Test\", \"email\": \"smoke-$(date +%s%N)@campus.edu\"}"
expect_status 201
CUSTOMER_ID=$(grep -o '"id":[0-9]*' <<<"$BODY" | head -1 | cut -d: -f2)
ok "customer $CUSTOMER_ID created"

step "5. Place an order: 2 x Masala Dosa + 1 x Filter Coffee"
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 1, "quantity": 2}, {"menuItemId": 3, "quantity": 1}')"
expect_status 201
expect_body '"total":140.00'
ok "201 Created, total 140.00, Location: $LOCATION"
[[ $LOCATION == "$GATEWAY/api/orders/"* ]] || fail "Location should point at the gateway, got: $LOCATION"

step "6. Fetch the order back (follow the Location header)"
call GET "${LOCATION#"$GATEWAY"}"
expect_status 200
expect_body '"name":"Masala Dosa","unitPrice":60.00,"quantity":2,"lineTotal":120.00'
expect_body '"total":140.00'
ok "order has the name/price snapshot and total 140.00"

step "7. Business rules: unknown customer and sold-out item give 422"
call POST /api/orders "$(order_json 999999 '{"menuItemId": 1, "quantity": 1}')"
expect_status 422
ok "unknown customer -> 422"
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 6, "quantity": 1}')"
expect_status 422
expect_body 'is not available'
ok "sold-out Mango Lassi -> 422"

step "8. Break it: stop menu-service"
docker compose stop menu-service # Setup: a dependency fails
MENU_STOPPED=yes
call POST /api/orders "$(order_json "$CUSTOMER_ID" '{"menuItemId": 1, "quantity": 1}')" # Execute
expect_status 503                                                                       # Verify
expect_body '"service":"menu-service"'
faster_than 3 || fail "503 took ${SECONDS_TAKEN}s, expected under 3s (timeouts not working?)"
ok "503 in ${SECONDS_TAKEN}s: $BODY"

step "9. Keep ordering until the circuit breaker opens"
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

step "10. Recover: start menu-service again"
docker compose start menu-service # Teardown of step 8: undo the failure
MENU_STOPPED=no
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

printf '\nPASS: public menu, 401/403 and tokens, customers, orders, 422 rules, fast 503 with circuit breaker, and recovery all work.\n'

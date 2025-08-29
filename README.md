# Webhook Solver

Spring Boot app for Bajaj Finserv Health qualifier.

## Steps
1. On startup:
   - Calls `generateWebhook` API.
   - Gets webhook URL + accessToken.
   - Submits final SQL query to webhook.

## Build
```bash
mvn -DskipTests package

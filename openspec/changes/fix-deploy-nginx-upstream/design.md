# Design

## Context

The deployment uses a persistent Nginx container and recreates the backend container on each new jar. Nginx currently uses a literal `backend:8123` upstream, resolved when its configuration loads. The failed Action shows Nginx up for four days, a new backend healthy at startup, and 60 API 502 responses. The public homepage remained 200 because it is served by Nginx itself.

## Goals / Non-Goals

**Goals:** Keep the API proxy pointed at the active backend after container replacement and make deployment apply the shipped proxy configuration before its health gate.

**Non-Goals:** Change public ports, API paths, backend application behavior, or the Agent's private network.

## Decisions

- Use Docker's embedded DNS resolver (`127.0.0.11`) with a short cached lifetime and a variable upstream in Nginx. This allows Nginx to resolve the current backend IP after container replacement. Forcing an Nginx recreation only during deployment would fix this incident but leave future independent backend restarts vulnerable.
- Validate and reload Nginx after Compose starts the stack. This applies the bind-mounted new `nginx.conf` on an existing container. A failed validation fails deployment before the API health gate. Recreating the proxy each deploy would cause avoidable front-end interruption.
- Preserve the original request URI and headers when converting the upstream to a variable; the API's `/api` prefix must reach Spring unchanged.

## Risks / Trade-offs

- [Docker DNS unavailable] -> The API proxy returns 502; the existing health gate fails and shows proxy diagnostics.
- [Proxy configuration reload fails] -> Deployment exits with the Nginx configuration error rather than reporting success.
- [Backend takes time to start] -> Existing bounded health probe waits for the backend; DNS cache expires during the window.

## Migration Plan

Deploy the revised `nginx.conf` and `deploy.sh` together. The script validates and reloads Nginx before probing. Rollback restores the previous files and restarts the stack; if reverting Nginx configuration, reload or recreate Nginx so the restored file is active.

### Auth
Authentication and authorization are handled by Ory Cloud.

Configuring for local development:

- Install Ory CLI
- Login to Ory tenant.
- Run `make ory_local` to create a tunnel.
- All ui flows are now hosted under `http://localhost:4000/ui/*`
- Navigate to `http://localhost:4000/ui/welcome` to see existing flows.

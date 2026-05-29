# TLS/HTTPS Deployment Guide

This guide explains how to deploy the FlossWare Platform REST API with TLS/HTTPS encryption using reverse proxy solutions.

## ⚠️ Security Notice

The platform's REST API currently uses JDK's `HttpServer`, which **does not support TLS natively**. Until native TLS support is implemented (see [Issue #326](https://github.com/FlossWare/platform-java/issues/326)), you **must** deploy with a reverse proxy in production environments to encrypt API traffic.

**DO NOT expose the unencrypted HTTP endpoint to untrusted networks.**

## Architecture

```
Client (HTTPS) → Reverse Proxy (TLS Termination) → Platform API (HTTP)
```

The reverse proxy:
- Terminates TLS connections from clients
- Forwards decrypted requests to the platform API on localhost
- Encrypts responses back to clients
- Handles certificate management

## Prerequisites

- Platform API running on `http://localhost:8080` (or configured port)
- TLS certificate and private key (see [Certificate Management](#certificate-management))
- Reverse proxy installed (nginx, Traefik, Caddy, or HAProxy)

---

## Option 1: nginx (Recommended for Production)

### Installation

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install nginx

# RHEL/CentOS/Fedora
sudo dnf install nginx

# macOS
brew install nginx
```

### Configuration

Create `/etc/nginx/sites-available/platform-api`:

```nginx
# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name api.example.com;
    
    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS server
server {
    listen 443 ssl http2;
    server_name api.example.com;

    # TLS Configuration
    ssl_certificate /etc/ssl/certs/platform-api.crt;
    ssl_certificate_key /etc/ssl/private/platform-api.key;
    
    # Modern TLS settings (Mozilla Intermediate)
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers off;
    
    # HSTS (15768000 seconds = 6 months)
    add_header Strict-Transport-Security "max-age=15768000; includeSubDomains" always;
    
    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Proxy to platform API
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        
        # WebSocket support (if needed)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        
        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
    
    # Health check endpoint (bypass auth)
    location /health {
        proxy_pass http://127.0.0.1:8080/health;
        access_log off;
    }
}
```

### Enable and Start

```bash
# Enable site
sudo ln -s /etc/nginx/sites-available/platform-api /etc/nginx/sites-enabled/

# Test configuration
sudo nginx -t

# Restart nginx
sudo systemctl restart nginx

# Enable on boot
sudo systemctl enable nginx
```

### Verify

```bash
# Test HTTPS endpoint
curl -v https://api.example.com/api/applications

# Check TLS version
openssl s_client -connect api.example.com:443 -tls1_2

# Test with authenticated request
curl -H "X-API-Key: your-api-key" https://api.example.com/api/applications
```

---

## Option 2: Traefik (Recommended for Docker/Kubernetes)

### Docker Compose Configuration

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  platform-api:
    image: flossware/platform-java:latest
    container_name: platform-api
    networks:
      - internal
    environment:
      - LOG_LEVEL=INFO
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.platform-api.rule=Host(`api.example.com`)"
      - "traefik.http.routers.platform-api.entrypoints=websecure"
      - "traefik.http.routers.platform-api.tls=true"
      - "traefik.http.routers.platform-api.tls.certresolver=letsencrypt"
      - "traefik.http.services.platform-api.loadbalancer.server.port=8080"

  traefik:
    image: traefik:v2.10
    container_name: traefik
    command:
      - "--api.dashboard=true"
      - "--providers.docker=true"
      - "--providers.docker.exposedbydefault=false"
      - "--entrypoints.web.address=:80"
      - "--entrypoints.websecure.address=:443"
      - "--certificatesresolvers.letsencrypt.acme.tlschallenge=true"
      - "--certificatesresolvers.letsencrypt.acme.email=admin@example.com"
      - "--certificatesresolvers.letsencrypt.acme.storage=/letsencrypt/acme.json"
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - "/var/run/docker.sock:/var/run/docker.sock:ro"
      - "./letsencrypt:/letsencrypt"
    networks:
      - internal

networks:
  internal:
    driver: bridge
```

### Start Services

```bash
# Create Let's Encrypt storage
mkdir letsencrypt
chmod 600 letsencrypt

# Start services
docker-compose up -d

# View logs
docker-compose logs -f traefik platform-api
```

---

## Option 3: Caddy (Automatic HTTPS)

Caddy automatically obtains and renews TLS certificates from Let's Encrypt.

### Installation

```bash
# Ubuntu/Debian
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy
```

### Caddyfile

Create `/etc/caddy/Caddyfile`:

```caddy
api.example.com {
    # Automatic HTTPS via Let's Encrypt
    reverse_proxy localhost:8080
    
    # Security headers
    header {
        Strict-Transport-Security "max-age=15768000; includeSubDomains"
        X-Frame-Options "SAMEORIGIN"
        X-Content-Type-Options "nosniff"
        X-XSS-Protection "1; mode=block"
    }
    
    # Logging
    log {
        output file /var/log/caddy/platform-api.log
        format json
    }
}
```

### Start Caddy

```bash
# Start service
sudo systemctl start caddy

# Enable on boot
sudo systemctl enable caddy

# Check status
sudo systemctl status caddy
```

---

## Option 4: HAProxy

### Installation

```bash
# Ubuntu/Debian
sudo apt-get install haproxy

# RHEL/CentOS/Fedora
sudo dnf install haproxy
```

### Configuration

Edit `/etc/haproxy/haproxy.cfg`:

```haproxy
global
    log /dev/log local0
    maxconn 2000
    user haproxy
    group haproxy
    daemon

    # TLS settings
    ssl-default-bind-ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256
    ssl-default-bind-options ssl-min-ver TLSv1.2 no-tls-tickets

defaults
    log global
    mode http
    option httplog
    option dontlognull
    timeout connect 5000
    timeout client 50000
    timeout server 50000

frontend platform_https
    bind *:443 ssl crt /etc/ssl/private/platform-api.pem
    mode http
    
    # Security headers
    http-response set-header Strict-Transport-Security "max-age=15768000; includeSubDomains"
    http-response set-header X-Frame-Options "SAMEORIGIN"
    
    default_backend platform_api

backend platform_api
    mode http
    balance roundrobin
    option httpchk GET /health
    http-check expect status 200
    server api1 127.0.0.1:8080 check
```

### Start HAProxy

```bash
sudo systemctl restart haproxy
sudo systemctl enable haproxy
```

---

## Certificate Management

### Option A: Let's Encrypt (Free, Automated)

#### Using Certbot (for nginx)

```bash
# Install certbot
sudo apt-get install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d api.example.com

# Auto-renewal (already configured)
sudo systemctl status certbot.timer
```

#### Using Traefik (Automatic)

Traefik handles Let's Encrypt automatically (see Traefik config above).

#### Using Caddy (Automatic)

Caddy handles ACME automatically (see Caddy config above).

### Option B: Self-Signed Certificate (Development Only)

```bash
# Generate private key
openssl genrsa -out platform-api.key 2048

# Generate certificate signing request
openssl req -new -key platform-api.key -out platform-api.csr \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=api.example.com"

# Generate self-signed certificate (valid 365 days)
openssl x509 -req -days 365 -in platform-api.csr \
    -signkey platform-api.key -out platform-api.crt

# Install certificate
sudo cp platform-api.crt /etc/ssl/certs/
sudo cp platform-api.key /etc/ssl/private/
sudo chmod 600 /etc/ssl/private/platform-api.key
```

⚠️ **Warning**: Self-signed certificates will trigger browser warnings. Only use for development/testing.

### Option C: Commercial Certificate

1. Purchase certificate from a CA (DigiCert, Sectigo, etc.)
2. Generate CSR: `openssl req -new -newkey rsa:2048 -nodes -keyout platform-api.key -out platform-api.csr`
3. Submit CSR to CA
4. Download signed certificate
5. Install certificate and key in `/etc/ssl/`

---

## Security Best Practices

### 1. Use Strong TLS Configuration

**Minimum**: TLS 1.2  
**Recommended**: TLS 1.2 and 1.3 only

```nginx
ssl_protocols TLSv1.2 TLSv1.3;
```

### 2. Use Modern Cipher Suites

Follow [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/):

```nginx
ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256';
```

### 3. Enable HSTS

Force HTTPS for all future requests:

```nginx
add_header Strict-Transport-Security "max-age=15768000; includeSubDomains" always;
```

### 4. Bind Platform API to Localhost

In `ApiServerConfig`:

```java
ApiServerConfig config = ApiServerConfig.builder()
    .bindAddress("127.0.0.1")  // Only accept local connections
    .port(8080)
    .build();
```

This prevents direct access to the unencrypted API.

### 5. Use Firewall Rules

```bash
# Allow HTTPS
sudo ufw allow 443/tcp

# Allow HTTP (for redirect)
sudo ufw allow 80/tcp

# Deny direct access to API port
sudo ufw deny 8080/tcp

# Enable firewall
sudo ufw enable
```

---

## Testing TLS Configuration

### SSL Labs Test

For public servers, use [SSL Labs](https://www.ssllabs.com/ssltest/):

```bash
# Should achieve A or A+ rating
```

### OpenSSL Testing

```bash
# Test TLS 1.2
openssl s_client -connect api.example.com:443 -tls1_2

# Test TLS 1.3
openssl s_client -connect api.example.com:443 -tls1_3

# Test with SNI
openssl s_client -connect api.example.com:443 -servername api.example.com

# Check certificate expiration
openssl s_client -connect api.example.com:443 2>/dev/null | openssl x509 -noout -dates
```

### nmap Testing

```bash
# Scan TLS configuration
nmap --script ssl-enum-ciphers -p 443 api.example.com

# Check for vulnerabilities
nmap --script ssl-* -p 443 api.example.com
```

---

## Troubleshooting

### Certificate Errors

**Problem**: "Certificate not trusted"

**Solution**: Ensure certificate chain is complete:

```nginx
ssl_certificate /etc/ssl/certs/platform-api-fullchain.crt;  # Include intermediate certs
```

### Connection Refused

**Problem**: Cannot connect to HTTPS endpoint

**Check**:
1. Reverse proxy is running: `systemctl status nginx`
2. Firewall allows 443: `sudo ufw status`
3. Certificate files exist: `ls -l /etc/ssl/certs/platform-api.crt`

### Mixed Content Warnings

**Problem**: Browser shows "mixed content" warnings

**Solution**: Ensure all resources use HTTPS:

```nginx
add_header Content-Security-Policy "upgrade-insecure-requests" always;
```

---

## Future: Native TLS Support

Phase 2 of [Issue #326](https://github.com/FlossWare/platform-java/issues/326) will implement native TLS support using Netty or Undertow. The configuration will look like:

```java
ApiServerConfig config = ApiServerConfig.builder()
    .enableTls(true)
    .keystorePath("/etc/platform/keystore.jks")
    .keystorePassword("changeit")
    .keyAlias("platform-api")
    .build();
```

Until then, reverse proxy deployment is the recommended approach.

---

## References

- [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/)
- [OWASP TLS Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [nginx SSL Module](http://nginx.org/en/docs/http/ngx_http_ssl_module.html)
- [Traefik HTTPS Documentation](https://doc.traefik.io/traefik/https/overview/)
- [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https)

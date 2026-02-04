# Nginx + HTTPS setup for vokab.alirezaiyan.com

Run these on the VPS (e.g. root@148.230.109.213). Assumes Debian/Ubuntu.

## 1. Install nginx and Certbot

```bash
apt update
apt install -y nginx certbot python3-certbot-nginx
```

## 2. Deploy the site config

Copy the nginx config to the enabled sites (or create it there):

```bash
# From the repo (after cloning or copying the file to the server):
cp /var/www/vokab/vokab.server/deploy/nginx-vokab.alirezaiyan.com.conf /etc/nginx/sites-available/vokab.alirezaiyan.com
ln -sf /etc/nginx/sites-available/vokab.alirezaiyan.com /etc/nginx/sites-enabled/
```

If the file is not in the repo on the server, create it:

```bash
cat > /etc/nginx/sites-available/vokab.alirezaiyan.com << 'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name vokab.alirezaiyan.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
EOF
ln -sf /etc/nginx/sites-available/vokab.alirezaiyan.com /etc/nginx/sites-enabled/
```

## 3. Disable default site (optional)

```bash
rm -f /etc/nginx/sites-enabled/default
```

## 4. Test nginx and reload

```bash
nginx -t && systemctl reload nginx
```

## 5. Get Let's Encrypt certificate (HTTPS)

Ensure DNS for vokab.alirezaiyan.com points to this server, then:

```bash
certbot --nginx -d vokab.alirezaiyan.com --non-interactive --agree-tos -m YOUR_EMAIL@example.com
```

Replace `YOUR_EMAIL@example.com` with your email (used for cert expiry notices).

Certbot will:
- Obtain the certificate
- Modify the nginx config to listen on 443 and use the cert
- Add a redirect from HTTP (80) to HTTPS (443)

## 6. Firewall: allow 80/443, block 8080 from internet

```bash
ufw allow 80/tcp
ufw allow 443/tcp
ufw deny 8080/tcp
# If SSH is not already allowed:
ufw allow 22/tcp
ufw --force enable
ufw status
```

## 7. Verify

- From browser or curl: `https://vokab.alirezaiyan.com/api/v1/health`
- HTTP should redirect to HTTPS

## Auto-renewal (Certbot)

Certbot installs a cron/systemd timer. Check:

```bash
systemctl status certbot.timer
# or
certbot renew --dry-run
```

## Optional: bind app to localhost only

In `docker-compose.yml` you can change the app port to bind only to localhost so 8080 is not exposed at all:

```yaml
ports:
  - "127.0.0.1:8080:8080"
```

Then reload: `docker compose up -d`.

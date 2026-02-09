# Cloudflare Tunnel Runbook (Single Gateway Domain)

## Goal

Expose local API Gateway (`localhost:8080`) to the public internet through a single domain:

- `https://api.<your-domain>`

This setup does not require VPS, public IP, or router port forwarding.

## Prerequisites

1. Domain is already managed by Cloudflare DNS.
2. Local Ubuntu host can access internet and run Docker services.
3. API Gateway is reachable locally:

```bash
curl http://localhost:8080/actuator/health
```

## 1) Install `cloudflared` on Ubuntu

```bash
curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg \
  | sudo tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null

echo "deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared any main" \
  | sudo tee /etc/apt/sources.list.d/cloudflared.list

sudo apt-get update
sudo apt-get install -y cloudflared
```

## 2) Authenticate and Create Named Tunnel

```bash
cloudflared tunnel login
cloudflared tunnel create fusionxpay-home
```

Record the generated tunnel ID from command output.

## 3) Bind Public DNS to Tunnel

```bash
cloudflared tunnel route dns fusionxpay-home api.<your-domain>
```

## 4) Configure Tunnel

1. Copy template:

```bash
mkdir -p ~/.cloudflared
cp ops/cloudflared/config.example.yml ~/.cloudflared/config.yml
```

2. Replace placeholders in `~/.cloudflared/config.yml`:
   - `<TUNNEL_ID>`
   - `<ubuntu_user>`
   - `<your-domain>`

## 5) Run as System Service (Auto Start)

```bash
sudo cloudflared service install
sudo systemctl enable --now cloudflared
```

## 6) Verification Checklist

```bash
cloudflared tunnel list
sudo systemctl status cloudflared --no-pager
curl -fsS http://localhost:8080/actuator/health
curl -fsS https://api.<your-domain>/actuator/health
```

Expected result:

- Tunnel status is healthy.
- `cloudflared` service is active.
- Local and public health endpoints both return `UP`.

## 7) Reboot Drill (Required Once)

```bash
sudo reboot
# after host is back:
sudo systemctl status cloudflared --no-pager
curl -fsS https://api.<your-domain>/actuator/health
```

## 8) Troubleshooting

1. DNS not ready:
   - Check CNAME in Cloudflare dashboard.
   - Wait for propagation and retry.
2. Service starts but public health fails:
   - Confirm local gateway is running on `8080`.
   - Check `~/.cloudflared/config.yml` host and service values.
3. Tunnel disconnected:
   - Inspect logs:

```bash
journalctl -u cloudflared -n 200 --no-pager
```

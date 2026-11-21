# Deploy on EC2 (Docker)

One VM runs three containers: **db** (MySQL 8.4), **api** (Spring Boot, `prod` profile) and **web**
(Nginx: Angular build at `/`, API at `/v1`). Only port 80 (later 443) is open; MySQL and 8080 stay inside
the Docker network.

## 1. Launch the instance (ap-south-1)
- Ubuntu Server 24.04 LTS, **t4g.small** (arm64, 2 GB) or t3.small; 30 GB gp3.
- Security group: 22 from your IP only, 80 and 443 from anywhere.
- Allocate an **Elastic IP** and associate it (the address must not change; the app and DNS point at it).

## 2. Prepare the VM
```bash
sudo apt update && sudo apt -y upgrade
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && newgrp docker

# 2 GB swap: the Angular and Gradle builds need more than 2 GB RAM at peak.
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
sudo timedatectl set-timezone Asia/Kolkata
```

## 3. Get the code (private repo: read-only deploy key)
```bash
ssh-keygen -t ed25519 -C naanayam-ec2 -f ~/.ssh/id_ed25519 -N ''
cat ~/.ssh/id_ed25519.pub   # GitHub → repo → Settings → Deploy keys → Add (read-only)
git clone git@github.com:DigitWORKS/naanayam-thavanai.git && cd naanayam-thavanai/deploy
```

## 4. Configure and start
```bash
cp .env.example .env && chmod 600 .env
nano .env        # DB_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET, SUPER_ADMIN_PHONE/PIN for the first start
docker compose up -d --build
docker compose logs -f api        # wait for "Started NaanayamApplication"; Flyway runs the migrations
curl http://localhost/v1/actuator/health   # {"status":"UP"}
```
Open `http://<elastic-ip>/` for the web app. After the Super Admin's first login, clear `SUPER_ADMIN_PIN`
in `.env` and run `docker compose up -d` again.

## 5. Update
```bash
cd ~/naanayam-thavanai && git pull && cd deploy && docker compose up -d --build
docker image prune -f
```

## 6. Backups
```bash
sudo mkdir -p /var/backups/naanayam && sudo chown ubuntu /var/backups/naanayam
crontab -e
# 30 1 * * * /home/ubuntu/naanayam-thavanai/deploy/backup.sh >> /home/ubuntu/backup.log 2>&1
```
Keeps 14 days of DB dumps and documents locally. Set `BACKUP_S3` in `.env` (aws cli + an instance role with
`s3:PutObject` on the bucket) so a copy survives losing the VM.
Restore: `gunzip -c db-….sql.gz | docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" naanayam'`.

## 7. HTTPS (once a domain points at the Elastic IP)
Needed before the Android release build: Android blocks plain HTTP by default. Steps (certbot via the
webroot already served at `/.well-known/acme-challenge/`, then a 443 server block in
`nginx/default.conf` and port 443 in compose) to be added when the domain is ready.

## Without Docker
`systemd/naanayam.service` runs the jar directly: install Java 25, MySQL 8.4 and Nginx on the host,
build with `cd service && ./gradlew bootJar`, copy the jar to `/opt/naanayam/app.jar`, put the
variables in `/etc/naanayam/naanayam.env`, and use the same `nginx/default.conf` with
`proxy_pass http://127.0.0.1:8080;` and `root` pointing at the copied `web/dist/naanayam-web/browser`.

#!/usr/bin/env python3
"""Persist the restricted ACME relay and frequently check short-lived IP renewal."""
from pathlib import Path
import subprocess

subprocess.run(["systemctl", "stop", "antigravity-acme-egress"], check=True)
Path("/etc/systemd/system/antigravity-acme-egress.service").write_text("""[Unit]
Description=Restricted local ACME TLS compatibility relay
After=network-online.target
Wants=network-online.target
[Service]
User=nobody
Group=nogroup
ExecStart=/usr/bin/python3 /opt/antigravity/acme_egress.py
Restart=on-failure
RestartSec=5
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=yes
MemoryMax=64M
[Install]
WantedBy=multi-user.target
""")
Path("/etc/systemd/system/antigravity-cert-renew.service").write_text("""[Unit]
Description=Renew trusted short-lived IP certificate
Requires=antigravity-acme-egress.service
After=antigravity-acme-egress.service network-online.target
[Service]
Type=oneshot
Environment=HTTPS_PROXY=http://127.0.0.1:19091
Environment=HTTP_PROXY=http://127.0.0.1:19091
Environment=https_proxy=http://127.0.0.1:19091
Environment=http_proxy=http://127.0.0.1:19091
ExecStart=/opt/antigravity/certbot/bin/certbot renew --cert-name antigravity-ip --non-interactive --deploy-hook "/usr/sbin/nginx -t && /usr/bin/systemctl reload nginx"
TimeoutStartSec=15min
""")
Path("/etc/systemd/system/antigravity-cert-renew.timer").write_text("""[Unit]
Description=Check short-lived IP certificate renewal every four hours
[Timer]
OnCalendar=*-*-* 00,04,08,12,16,20:15:00
RandomizedDelaySec=10m
Persistent=true
[Install]
WantedBy=timers.target
""")
subprocess.run(["systemctl", "daemon-reload"], check=True)
subprocess.run(["systemctl", "enable", "--now", "antigravity-acme-egress", "antigravity-cert-renew.timer"], check=True)
subprocess.run(["systemctl", "start", "antigravity-cert-renew.service"], check=True)
print("Persistent ACME relay and four-hour renewal timer installed")

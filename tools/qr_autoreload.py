#!/usr/bin/env python3
"""Auto-reload the WhatsApp companion QR when it times out.

Every 8s: screencap, crop QR region; if it looks like the 'timed out'
card (big light area, no dense QR pattern), tap the Reload button.
"""
import subprocess, time
from PIL import Image

ADB = "/home/himayat/Android/Sdk/platform-tools/adb"
DEV = "f5be654f"
CROP = (216, 718, 216 + 649, 718 + 649)   # QR card region
RELOAD = (542, 1092)                      # Reload button center


def screencap():
    png = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                         capture_output=True, timeout=15).stdout
    return Image.open(__import__("io").BytesIO(png)).convert("RGB")


def is_timed_out(img):
    crop = img.crop(CROP).convert("L")
    px = list(crop.getdata())
    frac_light = sum(1 for p in px if p > 180) / len(px)
    # dense QR: roughly balanced; timed-out card: overwhelmingly light
    return frac_light > 0.80


while True:
    try:
        if is_timed_out(screencap()):
            subprocess.run([ADB, "-s", DEV, "shell",
                            "input", "tap", str(RELOAD[0]), str(RELOAD[1])])
            with open("/tmp/opencode/auto_reload.log", "a") as f:
                f.write(time.strftime("%H:%M:%S") + " reloaded\n")
            time.sleep(4)
    except Exception as e:
        pass  # device busy; try next cycle
    time.sleep(8)

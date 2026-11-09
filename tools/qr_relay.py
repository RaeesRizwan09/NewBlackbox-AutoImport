#!/usr/bin/env python3
"""Live WhatsApp companion-QR relay window.

Grabs a fresh device screencap every 15s via adb, crops the QR area,
scales it up 2x, and shows it in a Tk window so the phone's own camera
(native WhatsApp -> Linked devices) can read it off the monitor.
"""
import subprocess, tempfile, io, os
from PIL import Image, ImageTk
import tkinter as tk

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
DEV = "f5be654f"
# QR region found via uiautomator dump: id registration_qr at 216,718 size 649x649
CROP = (216, 718, 216 + 649, 718 + 649)
SCALE = 2

def grab_crop():
    png = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                         capture_output=True, timeout=15).stdout
    img = Image.open(io.BytesIO(png)).convert("RGB")
    return img.crop(CROP).resize((649 * SCALE, 649 * SCALE), Image.NEAREST)

def refresh():
    try:
        ph.config(image=None)
        new = ImageTk.PhotoImage(grab_crop())
    except Exception:
        new = None
    if new:
        w.config(image=new)
        ph.image = new
    label.config(text=f"QR live • updated {time.strftime('%H:%M:%S')} • scan with native WhatsApp -> Linked devices")
    root.after(15000, refresh)

import time

root = tk.Tk()
root.attributes("-topmost", True)
root.title("WhatsApp companion QR — point the phone at this window")
w = tk.Label(root)
w.pack(padx=10, pady=10)
label = tk.Label(root, text="loading…")
label.pack()
try:
    first = ImageTk.PhotoImage(grab_crop())
    w.config(image=first)
    ph = w
    ph.image = first
except Exception as e:
    print("initial grab failed:", e)
root.after(1000, refresh)
root.mainloop()

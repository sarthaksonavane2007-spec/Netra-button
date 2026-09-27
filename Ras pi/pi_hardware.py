import os
import json
import socket
import threading
import time
from http.server import SimpleHTTPRequestHandler, HTTPServer
import requests

# ==============================================================
# CONFIGURATION & AUTO-DISCOVERY
# ==============================================================
PI_PORT = 8000
TRIGGER_PORT = 5000
PHONE_PORT = 8080

FRAME_DIR = os.path.expanduser("~/netra_frames")
os.makedirs(FRAME_DIR, exist_ok=True)
current_status = "idle"

def find_phone_on_subnet(port=8080, timeout=0.5):
    print("[SCAN] Scanning local Wi-Fi subnet for Netra app...")
    
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        local_ip = s.getsockname()[0]
    except Exception:
        local_ip = '127.0.0.1'
    finally:
        s.close()
        
    subnet = ".".join(local_ip.split('.')[:3]) + "."
    
    for i in range(1, 255):
        target_ip = subnet + str(i)
        try:
            test_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            test_sock.settimeout(timeout)
            result = test_sock.connect_ex((target_ip, port))
            test_sock.close()
            if result == 0:
                print(f"✓ Found Netra app active at: {target_ip}")
                return target_ip
        except Exception:
            pass
            
    manual_ip = input("Could not auto-detect. Enter Phone IP manually: ").strip()
    return manual_ip

# Dynamically resolve phone IP on startup
PHONE_IP = find_phone_on_subnet(PHONE_PORT)

# ==============================================================
# CAMERA MODULE INTEGRATION
# ==============================================================
def capture_real_frames():
    print("[PI CAMERA] Snapping 6 burst frames from physical camera...")
    try:
        import cv2
        cap = cv2.VideoCapture(0)
        for i in range(6):
            ret, frame = cap.read()
            if ret:
                filename = os.path.join(FRAME_DIR, f"frame_{i:02d}.jpg")
                cv2.imwrite(filename, frame)
            else:
                create_fallback_frame(i)
            time.sleep(0.05)
        cap.release()
        print("✓ [PI CAMERA] 6 frames captured and saved.")
    except Exception as e:
        print(f"! [CAMERA ERROR] {e}. Using fallback generator.")
        for i in range(6):
            create_fallback_frame(i)

def create_fallback_frame(i):
    from PIL import Image, ImageDraw
    img = Image.new('RGB', (640, 480), color=(30, 30, 30))
    d = ImageDraw.Draw(img)
    d.text((50, 50), f"Pi Live Frame {i}", fill=(255, 255, 255))
    img.save(os.path.join(FRAME_DIR, f"frame_{i:02d}.jpg"))

# ==============================================================
# LOCAL HTTP SERVER (Port 8000 for Phone to Fetch Frames)
# ==============================================================
class PiHttpServer(SimpleHTTPRequestHandler):
    def translate_path(self, path):
        clean_path = path.split('?', 1)[0].split('#', 1)[0]
        filename = os.path.basename(clean_path)
        return os.path.join(FRAME_DIR, filename)

    def do_GET(self):
        global current_status
        if self.path.startswith('/status'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Cache-Control', 'no-cache')
            self.end_headers()
            self.wfile.write(json.dumps({"status": current_status}).encode('utf-8'))
        else:
            print(f"   [PI SERVER] Serving frame: {self.path}")
            super().do_GET()

def start_pi_server():
    server = HTTPServer(('0.0.0.0', PI_PORT), PiHttpServer)
    print(f"✓ Pi Frame Server running on port {PI_PORT}")
    server.serve_forever()

# ==============================================================
# TRIGGER HANDLER (Receives from ESP32, Forwards to Phone)
# ==============================================================
class TriggerHandler(SimpleHTTPRequestHandler):
    def do_POST(self):
        global current_status
        if self.path == '/trigger':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            data = json.loads(post_data.decode('utf-8'))
            command = data.get("command", "UNKNOWN")
            
            print(f"\n[ESP32 TRIGGER] Received command: {command}")
            
            if command == "SOS":
                print("[EMERGENCY] SOS Triggered!")
            else:
                current_status = "capturing"
                capture_real_frames()
                current_status = "done"

            # Forward the trigger to the dynamically discovered phone IP
            phone_url = f"http://{PHONE_IP}:{PHONE_PORT}/trigger"
            print(f"[DISPATCH] Forwarding {command} to Phone at {phone_url}...")
            try:
                requests.post(phone_url, json={"command": command}, timeout=2.0)
                print("✓ Phone notified successfully.")
            except Exception as e:
                print(f"! [WARNING] Could not notify phone: {e}")

            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "acknowledged"}).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

def start_trigger_listener():
    trigger_server = HTTPServer(('0.0.0.0', TRIGGER_PORT), TriggerHandler)
    print(f"✓ ESP32 Trigger Listener running on port {TRIGGER_PORT}")
    trigger_server.serve_forever()

# ==============================================================
# MAIN
# ==============================================================
if __name__ == "__main__":
    print("=======================================================")
    print("       NETRA CANE - RASPBERRY PI NODE HUB              ")
    print("=======================================================")
    threading.Thread(target=start_pi_server, daemon=True).start()
    threading.Thread(target=start_trigger_listener, daemon=True).start()
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down Pi node...")
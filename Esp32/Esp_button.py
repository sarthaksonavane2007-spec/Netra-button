

#include <WiFi.h>
#include <HTTPClient.h>

// ==============================================================
// CONFIGURATION (Update with your Wi-Fi and Raspberry Pi IP)
// ==============================================================
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// Replace with your Raspberry Pi's fixed local IP address
const char* piServerUrl = "http://192.168.1.50:5000/trigger"; 

// Pin Definitions (Adjust depending on your physical wiring)
const int BUTTON_OBJECT = 13; 
const int BUTTON_OCR = 14;    
const int BUTTON_SOS = 26;    

// SOS Triple-Click State Variables
int sosPressCount = 0;
unsigned long firstSosPressTime = 0;
const unsigned long SOS_WINDOW_MS = 3000; // 3 seconds window

// Debounce Tracking
unsigned long lastDebounceTimeObject = 0;
unsigned long lastDebounceTimeOcr = 0;
unsigned long lastDebounceTimeSos = 0;
const unsigned long DEBOUNCE_DELAY = 250;

void setup() {
    Serial.begin(115200);
    
    // Connect to Wi-Fi
    WiFi.begin(ssid, password);
    Serial.print("Connecting to Wi-Fi");
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\n✓ WiFi connected. IP: " + WiFi.localIP().toString());

    // Initialize buttons with internal pull-ups (press = LOW)
    pinMode(BUTTON_OBJECT, INPUT_PULLUP);
    pinMode(BUTTON_OCR, INPUT_PULLUP);
    pinMode(BUTTON_SOS, INPUT_PULLUP);
}

void sendCommandToPi(String command) {
    if (WiFi.status() == WL_CONNECTED) {
        HTTPClient http;
        http.begin(piServerUrl);
        http.addHeader("Content-Type", "application/json");
        
        String payload = "{\"command\":\"" + command + "\"}";
        int httpResponseCode = http.POST(payload);
        
        if (httpResponseCode > 0) {
            Serial.println("✓ Command '" + command + "' sent to Pi successfully!");
        } else {
            Serial.println("! Error sending command to Pi. Code: " + String(httpResponseCode));
        }
        http.end();
    } else {
        Serial.println("! Wi-Fi disconnected. Cannot send command.");
    }
}

void loop() {
    unsigned long currentMillis = millis();

    // 1. Object Detection Button (Pin 13)
    if (digitalRead(BUTTON_OBJECT) == LOW) {
        if (currentMillis - lastDebounceTimeObject > DEBOUNCE_DELAY) {
            Serial.println("Object Button Pressed!");
            sendCommandToPi("OBJECT");
            lastDebounceTimeObject = currentMillis;
        }
    }

    // 2. OCR Button (Pin 14)
    if (digitalRead(BUTTON_OCR) == LOW) {
        if (currentMillis - lastDebounceTimeOcr > DEBOUNCE_DELAY) {
            Serial.println("OCR Button Pressed!");
            sendCommandToPi("OCR");
            lastDebounceTimeOcr = currentMillis;
        }
    }

    // 3. SOS Button with Triple-Click Logic (Pin 26)
    if (digitalRead(BUTTON_SOS) == LOW) {
        if (currentMillis - lastDebounceTimeSos > DEBOUNCE_DELAY) {
            lastDebounceTimeSos = currentMillis;
            
            // If this is the first press or the window has expired, reset window
            if (sosPressCount == 0 || (currentMillis - firstSosPressTime > SOS_WINDOW_MS)) {
                sosPressCount = 1;
                firstSosPressTime = currentMillis;
                Serial.println("SOS Press 1/3 (Press 2 more times within 3s)");
            } else {
                sosPressCount++;
                Serial.println("SOS Press " + String(sosPressCount) + "/3");
                
                // Check if target of 3 presses reached within window
                if (sosPressCount == 3) {
                    Serial.println("🚨 TRIPLE-CLICK CONFIRMED! Dispatching SOS...");
                    sendCommandToPi("SOS");
                    sosPressCount = 0; // Reset counter
                }
            }
        }
    }

    // Reset SOS counter if the 3-second window expires without 3 presses
    if (sosPressCount > 0 && (millis() - firstSosPressTime > SOS_WINDOW_MS)) {
        Serial.println("SOS window expired. Resetting counter.");
        sosPressCount = 0;
    }
}
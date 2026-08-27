#include <MCUFRIEND_kbv.h>
#include <Adafruit_GFX.h>
MCUFRIEND_kbv tft;

// Renkler (RGB565)
#define BLACK   0x0000
#define WHITE   0xFFFF
#define BLUE    0x001F
#define GREEN   0x07E0
#define YELLOW  0xFFE0
#define GRAY    0x8410

String deviceNumber = "No: 001";
#define SPLASH_TIME_MS 2000
#define EXTRA_TIME_MS  2000

// ✅ BUTON PINLERİ (TFT shield ile çakışmaması için analogları kullandık)
const uint8_t PIN_UP           = 22;
const uint8_t PIN_DOWN         = 23;
const uint8_t PIN_OK           = 24;
const uint8_t PIN_RESET        = 25;
const uint8_t PIN_ONOFF        = 26;
const uint8_t PIN_OUTPUT       = 27;
const uint8_t PIN_DEVICE_SENSE = 28;
const uint8_t PIN_FREQ_IN      = 30;

const uint16_t DEBOUNCE_MS = 120;

// Materyaller
const char* materials[] = {
  "Krom",
  "Cinko",
  "Altin",
  "Gumus",
  "Bakir",
  "Kursun",
  "Cam",
  "Kemik",
  "Su",
  "Bronz",
  "Bosluk",
  "Demir"
};

// 🔽 BURAYA EKLEYECEKSİN
const int materialFreq[] = {
  7500,   // Krom
  13000,  // Cinko
  1380,   // Altin
  1420,   // Gumus
  1370,   // Bakir
  5000,   // Kursun
  520,    // Cam
  1385,   // Kemik
  120,    // Su
  640,    // Bronz
  0,      // Bosluk
  730     // Demir
};

const uint8_t MATERIAL_COUNT = sizeof(materials) / sizeof(materials[0]);

// Menü
const char* menuItems[] = {"Materyal", "Ekran Parlaklik", "Ses"};
const uint8_t MENU_COUNT = 3;

enum ScreenState {
  SCR_MENU = 0,
  SCR_MATERIALS,
  SCR_BRIGHTNESS,
  SCR_SOUND,
  SCR_MATERIAL_SELECTED,
  SCR_OUTPUT
};

ScreenState screen = SCR_MENU;
uint8_t menuIndex = 0;
uint8_t materialIndex = 0;

uint8_t brightness = 70;
uint8_t soundLevel = 50;
bool systemEnabled = false;
bool deviceConnected = false;
bool materialDetected = false;
uint8_t detectGauge = 0;
unsigned long lastGaugeUpdate = 0;
uint16_t measuredFreqHz = 0;

struct Btn {
  uint8_t pin;
  bool lastStable;
  bool lastRaw;
  unsigned long lastChange;
};

Btn btnUp    {PIN_UP,    true, true, 0};
Btn btnDown  {PIN_DOWN,  true, true, 0};
Btn btnOk    {PIN_OK,    true, true, 0};
Btn btnReset {PIN_RESET, true, true, 0};
Btn btnOnOff {PIN_ONOFF, true, true, 0};

unsigned long resetHoldStartMs = 0;
bool resetHandled = false;
unsigned long lastOnOffToggleMs = 0;

bool pressed(Btn &b) {
  bool raw = digitalRead(b.pin);   // PULLUP: basılı = LOW
  unsigned long now = millis();

  if (raw != b.lastRaw) {
    b.lastRaw = raw;
    b.lastChange = now;
  }

  if ((now - b.lastChange) > DEBOUNCE_MS && b.lastStable != b.lastRaw) {
    b.lastStable = b.lastRaw;
    if (b.lastStable == false) return true; // HIGH->LOW
  }

  return false;
}

// UI
void drawLogo(int x, int y);
void showSplash();
void drawTopBar(const char* title);
void drawFooterHint(const char* text);

void drawMenu();
void drawMaterials();
void drawBrightness();
void drawSound();
void drawMaterialSelected();
void drawOutputControl();

void gotoMenu();
void gotoMaterials();
void gotoBrightness();
void gotoSound();
void gotoOutputControl();

void showKeyDebug(const char* name);
void drawOutputStatus();
void applyOutputFrequency();
void updateDeviceStatus();
void updateDetectGauge();
void drawDetectGauge(int x, int y, int w, int h);
bool isMaterialDetected();
uint16_t readExternalFrequencyHz();
void drawDetectionBell(bool active);

void setup() {
  Serial.begin(9600);

  pinMode(PIN_UP, INPUT_PULLUP);
  pinMode(PIN_DOWN, INPUT_PULLUP);
  pinMode(PIN_OK, INPUT_PULLUP);
  pinMode(PIN_RESET, INPUT_PULLUP);
  pinMode(PIN_ONOFF, INPUT_PULLUP);
  pinMode(PIN_OUTPUT, OUTPUT);
  pinMode(PIN_DEVICE_SENSE, INPUT_PULLUP);
  pinMode(PIN_FREQ_IN, INPUT);
  digitalWrite(PIN_OUTPUT, LOW);
  updateDeviceStatus();
  applyOutputFrequency();

  uint16_t ID = tft.readID();
  if (ID == 0xD3D3) ID = 0x9486;
  tft.begin(ID);

  tft.setRotation(1);
  tft.fillScreen(BLACK);

  showSplash();
  delay(SPLASH_TIME_MS);
  delay(EXTRA_TIME_MS);

  gotoMenu();
}

void loop() {
  updateDetectGauge();

  // RESET tusunu yanlis temaslara karsi basili tutarak calistir (stabil)
  if (digitalRead(PIN_RESET) == LOW) {
    if (resetHoldStartMs == 0) resetHoldStartMs = millis();
    if (!resetHandled && (millis() - resetHoldStartMs) > 700) {
      resetHandled = true;
      showKeyDebug("RESET");
      materialDetected = false;
      detectGauge = 0;
      measuredFreqHz = 0;
      gotoMenu();
      return;
    }
  } else {
    resetHoldStartMs = 0;
    resetHandled = false;
  }

  if (pressed(btnOnOff)) {
    unsigned long now = millis();
    if (now - lastOnOffToggleMs < 250) return;
    lastOnOffToggleMs = now;

    showKeyDebug("ON/OFF");
    systemEnabled = !systemEnabled;
    if (!systemEnabled) {
      materialDetected = false;
      measuredFreqHz = 0;
      detectGauge = 0;
    }
    applyOutputFrequency();
    if (screen == SCR_OUTPUT) {
      drawOutputControl();
    } else if (screen == SCR_MENU) {
      drawMenu();
    } else if (screen == SCR_MATERIALS) {
      drawMaterials();
    } else if (screen == SCR_MATERIAL_SELECTED) {
      drawMaterialSelected();
    } else if (screen == SCR_BRIGHTNESS) {
      drawBrightness();
    } else if (screen == SCR_SOUND) {
      drawSound();
    }
  }

  if (screen == SCR_MENU) {
    if (pressed(btnUp)) {
      showKeyDebug("UP");
      menuIndex = (menuIndex == 0) ? (MENU_COUNT - 1) : (menuIndex - 1);
      drawMenu();
    }
    if (pressed(btnDown)) {
      showKeyDebug("DOWN");
      menuIndex = (menuIndex + 1) % MENU_COUNT;
      drawMenu();
    }
    if (pressed(btnOk)) {
      showKeyDebug("OK");
      if (menuIndex == 0) gotoMaterials();
      else if (menuIndex == 1) gotoBrightness();
      else gotoSound();
    }
  }

  else if (screen == SCR_MATERIALS) {
    if (pressed(btnUp)) {
      showKeyDebug("UP");
      materialIndex = (materialIndex == 0) ? (MATERIAL_COUNT - 1) : (materialIndex - 1);
      drawMaterials();
    }
    if (pressed(btnDown)) {
      showKeyDebug("DOWN");
      materialIndex = (materialIndex + 1) % MATERIAL_COUNT;
      drawMaterials();
    }
    if (pressed(btnOk)) {
      showKeyDebug("OK");
      // ✅ OK basınca seçilen ekranına GİT
      materialDetected = true;
      detectGauge = 0;
      screen = SCR_MATERIAL_SELECTED;
      drawMaterialSelected();
      applyOutputFrequency();
      // Burada bekle (RESET ile menüye döner, OK ile geri listeye döner)
    }
  }

  else if (screen == SCR_MATERIAL_SELECTED) {
    // ✅ OK ile tekrar materyal listesine dön
    if (pressed(btnOk)) {
      showKeyDebug("OK");
      materialDetected = false;
      detectGauge = 0;
      measuredFreqHz = 0;
      screen = SCR_MATERIALS;
      drawMaterials();
    }
  }

  else if (screen == SCR_BRIGHTNESS) {
    if (pressed(btnUp)) {
      showKeyDebug("UP");
      if (brightness < 100) brightness += 5;
      drawBrightness();
    }
    if (pressed(btnDown)) {
      showKeyDebug("DOWN");
      if (brightness >= 5) brightness -= 5;
      drawBrightness();
    }
    if (pressed(btnOk)) {
      showKeyDebug("OK");
      // şimdilik OK boş
    }
  }

  else if (screen == SCR_SOUND) {
    if (pressed(btnUp)) {
      showKeyDebug("UP");
      if (soundLevel < 100) soundLevel += 5;
      drawSound();
    }
    if (pressed(btnDown)) {
      showKeyDebug("DOWN");
      if (soundLevel >= 5) soundLevel -= 5;
      drawSound();
    }
    if (pressed(btnOk)) {
      showKeyDebug("OK");
      // şimdilik OK boş
    }
  }

  else if (screen == SCR_OUTPUT) {
    if (pressed(btnOk)) {
      showKeyDebug("OK");
      // şimdilik OK boş
    }
  }
}

/* ====== Küçük debug (ekranın altına basılan tuşu yazar) ====== */
void showKeyDebug(const char* name) {
  // Footer üstünde küçük alan temizle
  tft.fillRect(0, tft.height() - 40, tft.width(), 12, BLACK);
  tft.setTextColor(GRAY);
  tft.setTextSize(1);
  tft.setCursor(10, tft.height() - 38);
  tft.print("Key: ");
  tft.print(name);
}

/* ========================= UI ========================= */
void drawTopBar(const char* title) {
  tft.fillRect(0, 0, tft.width(), 30, BLUE);
  tft.setTextColor(WHITE);
  tft.setTextSize(2);
  tft.setCursor(10, 7);
  tft.print(title);
  if (screen != SCR_MATERIAL_SELECTED) {
    updateDeviceStatus();
    drawOutputStatus();
  }
}

void drawFooterHint(const char* text) {
  tft.drawFastHLine(10, tft.height() - 24, tft.width() - 20, GRAY);
  tft.setTextColor(GRAY);
  tft.setTextSize(1);
  tft.setCursor(10, tft.height() - 14);
  tft.print(text);
}

void showSplash() {
  tft.fillScreen(BLACK);

  int w = tft.width();
  drawLogo((w - 120) / 2, 20);

  tft.setTextColor(WHITE);
  tft.setTextSize(3);
  tft.setCursor((w - 180) / 2, 100);
  tft.print("DS");

  tft.setTextSize(2);
  tft.setCursor((w - 160) / 2, 130);
  tft.print("Dedektor");

  tft.setTextColor(YELLOW);
  tft.setTextSize(2);
  tft.setCursor((w - 120) / 2, 165);
  tft.print(deviceNumber);

  tft.drawFastHLine(20, 200, w - 40, GRAY);
  tft.setTextColor(GRAY);
  tft.setTextSize(1);
  tft.setCursor(20, 215);
  tft.print("Starting...");
}

void gotoMenu() { screen = SCR_MENU; drawMenu(); }
void gotoMaterials() { materialDetected = false; detectGauge = 0; screen = SCR_MATERIALS; drawMaterials(); }
void gotoBrightness() { screen = SCR_BRIGHTNESS; drawBrightness(); }
void gotoSound() { screen = SCR_SOUND; drawSound(); }
void gotoOutputControl() { screen = SCR_OUTPUT; drawOutputControl(); }

void drawMenu() {
  tft.fillScreen(BLACK);
  drawTopBar("MENU");

  int boxX = 15, boxY = 45, boxW = tft.width() - 30, boxH = 150;
  tft.drawRoundRect(boxX, boxY, boxW, boxH, 10, GRAY);

  for (uint8_t i = 0; i < MENU_COUNT; i++) {
    int y = boxY + 20 + (i * 40);
    if (i == menuIndex) {
      tft.fillRoundRect(boxX + 8, y - 6, boxW - 16, 30, 6, 0x18E3);
      tft.setTextColor(YELLOW);
      tft.setTextSize(2);
      tft.setCursor(boxX + 18, y);
      tft.print("> ");
      tft.print(menuItems[i]);
    } else {
      tft.setTextColor(WHITE);
      tft.setTextSize(2);
      tft.setCursor(boxX + 34, y);
      tft.print(menuItems[i]);
    }
  }
  drawFooterHint("UP/DOWN gez - OK gir - RESET menu");
}

void drawMaterials() {
  tft.fillScreen(BLACK);
  drawTopBar("MATERYAL");

  const uint8_t perPage = 6;
  uint8_t page = materialIndex / perPage;  // 0 veya 1
  uint8_t start = page * perPage;
  uint8_t end = start + perPage;
  if (end > MATERIAL_COUNT) end = MATERIAL_COUNT;

  uint8_t totalPages = (MATERIAL_COUNT + perPage - 1) / perPage;

  tft.setTextColor(GRAY);
  tft.setTextSize(1);
  tft.setCursor(tft.width() - 70, 10);
  tft.print("Sayfa ");
  tft.print(page + 1);
  tft.print("/");
  tft.print(totalPages);

  int boxX = 15, boxY = 45, boxW = tft.width() - 30, boxH = 170;
  tft.drawRoundRect(boxX, boxY, boxW, boxH, 10, GRAY);

  tft.setTextColor(WHITE);
  tft.setTextSize(2);
  tft.setCursor(boxX + 12, boxY + 10);
  tft.print("Liste:");

  int y = boxY + 40;
  for (uint8_t i = start; i < end; i++) {
    bool sel = (i == materialIndex);
    if (sel) {
      tft.fillRoundRect(boxX + 8, y - 4, boxW - 16, 26, 6, 0x18E3);
      tft.setTextColor(YELLOW);
    } else {
      tft.setTextColor(WHITE);
    }

    tft.setTextSize(2);
    tft.setCursor(boxX + 12, y);
    tft.print(i + 1);
    tft.print(".");

    tft.setCursor(boxX + 55, y);
    tft.print(materials[i]);

    y += 26;
  }

  drawFooterHint("UP/DOWN sec - OK secildi - RESET menu");
}

void drawMaterialSelected() {
  tft.fillScreen(BLACK);
  drawTopBar("SECILDI");

  tft.setTextColor(WHITE);
  tft.setTextSize(2);
  tft.setCursor(20, 70);
  tft.print("Materyal:");

  tft.setTextColor(YELLOW);
  tft.setTextSize(3);
  tft.setCursor(20, 110);
  tft.print(materials[materialIndex]);

  tft.setTextColor(GRAY);
  tft.setTextSize(1);
  tft.setCursor(20, 160);
  tft.print("OK: geri liste | RESET: menu");

  drawDetectionBell(measuredFreqHz > 0);

  drawDetectGauge(20, 185, tft.width() - 40, 14);

  drawFooterHint("OK geri - RESET menu");
}

void drawBrightness() {
  tft.fillScreen(BLACK);
  drawTopBar("PARLAKLIK");

  tft.setTextColor(WHITE);
  tft.setTextSize(2);
  tft.setCursor(20, 70);
  tft.print("Deger:");

  tft.setTextColor(YELLOW);
  tft.setTextSize(3);
  tft.setCursor(20, 110);
  tft.print((int)brightness);
  tft.print("%");

  int barX = 20, barY = 160, barW = tft.width() - 40, barH = 18;
  tft.drawRect(barX, barY, barW, barH, GRAY);
  int fillW = (barW - 2) * brightness / 100;
  tft.fillRect(barX + 1, barY + 1, fillW, barH - 2, GREEN);

  drawFooterHint("UP/DOWN degistir - RESET menu");
}

void drawSound() {
  tft.fillScreen(BLACK);
  drawTopBar("SES");

  tft.setTextColor(WHITE);
  tft.setTextSize(2);
  tft.setCursor(20, 70);
  tft.print("Deger:");

  tft.setTextColor(YELLOW);
  tft.setTextSize(3);
  tft.setCursor(20, 110);
  tft.print((int)soundLevel);
  tft.print("%");

  int barX = 20, barY = 160, barW = tft.width() - 40, barH = 18;
  tft.drawRect(barX, barY, barW, barH, GRAY);
  int fillW = (barW - 2) * soundLevel / 100;
  tft.fillRect(barX + 1, barY + 1, fillW, barH - 2, GREEN);

  drawFooterHint("UP/DOWN degistir - RESET menu");
}

void drawOutputControl() {
  tft.fillScreen(BLACK);
  drawTopBar("CIKIS");

  tft.setTextColor(WHITE);
  tft.setTextSize(2);
  tft.setCursor(20, 70);
  tft.print("Durum:");

  tft.setTextColor(systemEnabled ? GREEN : YELLOW);
  tft.setTextSize(3);
  tft.setCursor(20, 110);
  tft.print(systemEnabled ? "ON" : "OFF");

  tft.setTextColor(GRAY);
  tft.setTextSize(1);
  tft.setCursor(20, 160);
  tft.print("ON/OFF: cihaz cikisi");

  tft.setCursor(20, 175);
  tft.print("Cihaz: ");
  tft.print(deviceConnected ? "Bagli" : "Yok");

  drawFooterHint("ON/OFF degistir - RESET menu");
}

void drawLogo(int x, int y) {
  tft.fillRoundRect(x, y, 120, 60, 8, WHITE);
  tft.fillRoundRect(x + 3, y + 3, 114, 54, 6, BLACK);

  tft.setTextColor(WHITE);
  tft.setTextSize(4);
  tft.setCursor(x + 18, y + 12);
  tft.print("D");

  tft.setTextColor(GREEN);
  tft.setTextSize(4);
  tft.setCursor(x + 68, y + 12);
  tft.print("S");
}

void drawOutputStatus() {
  tft.setTextColor(systemEnabled ? GREEN : YELLOW);
  tft.setTextSize(1);
  tft.setCursor(tft.width() - 50, 10);
  tft.print(systemEnabled ? "ON" : "OFF");

  tft.setCursor(tft.width() - 90, 10);
  tft.print(deviceConnected ? "DEV" : "---");
}

void applyOutputFrequency() {
  updateDeviceStatus();
  if (!systemEnabled || !deviceConnected) {
    noTone(PIN_OUTPUT);
    digitalWrite(PIN_OUTPUT, LOW);
    return;
  }

  int freq = materialFreq[materialIndex];
  if (freq <= 0) {
    noTone(PIN_OUTPUT);
    digitalWrite(PIN_OUTPUT, LOW);
    return;
  }

  tone(PIN_OUTPUT, freq);
}

void updateDeviceStatus() {
  deviceConnected = (digitalRead(PIN_DEVICE_SENSE) == LOW);
}

bool isMaterialDetected() {
  return materialDetected && systemEnabled && measuredFreqHz > 0;
}

void updateDetectGauge() {
  unsigned long now = millis();
  if (now - lastGaugeUpdate < 40) return;
  lastGaugeUpdate = now;

  if (screen != SCR_MATERIAL_SELECTED || !materialDetected || !systemEnabled) {
    measuredFreqHz = 0;
    detectGauge = 0;
    if (screen == SCR_MATERIAL_SELECTED) drawDetectionBell(false);
    return;
  }

  measuredFreqHz = readExternalFrequencyHz();

  // Secilen materyalin hedef frekansina gore uyum puani (0..100)
  uint16_t selectedFreq = materialFreq[materialIndex];
  uint8_t target = 0;

  if (measuredFreqHz > 0 && selectedFreq > 0) {
    uint16_t diff = (measuredFreqHz > selectedFreq)
      ? (measuredFreqHz - selectedFreq)
      : (selectedFreq - measuredFreqHz);

    // tolerans: hedef frekansin %35'i, min 120Hz
    uint16_t tolerance = selectedFreq / 3;
    if (tolerance < 120) tolerance = 120;

    if (diff >= tolerance) {
      target = 0;
    } else {
      target = 100 - ((uint32_t)diff * 100 / tolerance);
    }
  }
  if (target > 100) target = 100;

  drawDetectionBell(target > 0);

  if (detectGauge < target) {
    detectGauge += 3;
    if (detectGauge > target) detectGauge = target;
  } else if (detectGauge > target) {
    detectGauge -= 1;
    if (detectGauge < target) detectGauge = target;
  }

  drawDetectGauge(20, 185, tft.width() - 40, 14);
}

uint16_t readExternalFrequencyHz() {
  unsigned long highUs = pulseIn(PIN_FREQ_IN, HIGH, 6000);
  unsigned long lowUs = pulseIn(PIN_FREQ_IN, LOW, 6000);

  if (highUs == 0 || lowUs == 0) return 0;

  unsigned long periodUs = highUs + lowUs;
  if (periodUs == 0) return 0;

  return 1000000UL / periodUs;
}

void drawDetectGauge(int x, int y, int w, int h) {
  tft.drawRect(x, y, w, h, GRAY);
  int fillW = (w - 2) * detectGauge / 100;
  tft.fillRect(x + 1, y + 1, w - 2, h - 2, BLACK);
  tft.fillRect(x + 1, y + 1, fillW, h - 2, isMaterialDetected() ? GREEN : YELLOW);
}

void drawDetectionBell(bool active) {
  const int x = tft.width() - 42;
  const int y = 48;

  tft.fillRect(x - 6, y - 8, 38, 38, BLACK);

  uint16_t col = active ? GREEN : GRAY;
  tft.drawCircle(x + 12, y + 12, 10, col);
  tft.drawFastVLine(x + 2, y + 12, 9, col);
  tft.drawFastVLine(x + 22, y + 12, 9, col);
  tft.drawFastHLine(x + 2, y + 21, 21, col);
  tft.fillCircle(x + 12, y + 24, 2, col);

  if (active) {
    tft.drawFastVLine(x + 28, y + 10, 5, col);
    tft.drawFastVLine(x + 31, y + 8, 9, col);
  }
}

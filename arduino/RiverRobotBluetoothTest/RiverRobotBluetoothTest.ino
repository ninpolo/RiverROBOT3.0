/*
  RiverROBOT Bluetooth command test

  Android app protocol:
    F = forward
    B = backward
    L = left
    R = right
    S = stop
    C/c = belt on/off
    H/h = shredder on/off
    K/k = bucket on/off
    A = anchor down
    U = anchor up
    V + one byte = speed 0..100

  Wiring for HC-05/HC-06 with SoftwareSerial:
    HC-05 TXD -> Arduino pin 10
    HC-05 RXD -> Arduino pin 11 through a voltage divider
    HC-05 GND -> Arduino GND
    HC-05 VCC -> 5V

  Open Arduino IDE Serial Monitor at 9600 baud to see received commands.
*/

#include <SoftwareSerial.h>

const byte BT_RX_PIN = 10; // Arduino receives from HC-05 TXD
const byte BT_TX_PIN = 11; // Arduino transmits to HC-05 RXD

SoftwareSerial bluetooth(BT_RX_PIN, BT_TX_PIN);

int speedPercent = 70;

void setup() {
  Serial.begin(9600);
  bluetooth.begin(9600);

  Serial.println("RiverROBOT Bluetooth test ready");
  Serial.println("Pair phone with HC-05, connect in the app, then press buttons.");
}

void loop() {
  readCommandsFrom(bluetooth, "BT");
  readCommandsFrom(Serial, "USB");
}

void readCommandsFrom(Stream &stream, const char *source) {
  while (stream.available() > 0) {
    char command = stream.read();

    if (command == 'V') {
      readSpeed(stream, source);
      continue;
    }

    handleCommand(command, source);
  }
}

void readSpeed(Stream &stream, const char *source) {
  unsigned long start = millis();
  while (stream.available() == 0 && millis() - start < 100) {
    delay(1);
  }

  if (stream.available() == 0) {
    Serial.print(source);
    Serial.println(" speed command missing value");
    return;
  }

  speedPercent = constrain(stream.read(), 0, 100);
  Serial.print(source);
  Serial.print(" speed ");
  Serial.print(speedPercent);
  Serial.println("%");
}

void handleCommand(char command, const char *source) {
  Serial.print(source);
  Serial.print(" command ");
  Serial.println(command);

  switch (command) {
    case 'F':
      Serial.println("Forward");
      break;
    case 'B':
      Serial.println("Backward");
      break;
    case 'L':
      Serial.println("Left");
      break;
    case 'R':
      Serial.println("Right");
      break;
    case 'S':
      Serial.println("Stop");
      break;
    case 'C':
      Serial.println("Belt on");
      break;
    case 'c':
      Serial.println("Belt off");
      break;
    case 'H':
      Serial.println("Shredder on");
      break;
    case 'h':
      Serial.println("Shredder off");
      break;
    case 'K':
      Serial.println("Bucket on");
      break;
    case 'k':
      Serial.println("Bucket off");
      break;
    case 'A':
      Serial.println("Anchor down");
      break;
    case 'U':
      Serial.println("Anchor up");
      break;
    case '\r':
    case '\n':
      break;
    default:
      Serial.println("Unknown command");
      break;
  }
}

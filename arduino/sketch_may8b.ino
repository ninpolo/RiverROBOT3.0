/*
  RiverROBOT3.0 Arduino Mega Bluetooth controller

  Commands from the Android app:
    F = forward
    B = backward
    L = turn left
    R = turn right
    S = stop motors
    C = belt ON      c = belt OFF
    H = shredder ON  h = shredder OFF
    K = bucket ON    k = bucket OFF
    A = anchor down
    U = anchor up
    V + byte 0..100 = speed percent

  Bluetooth wiring for Arduino Mega Serial1:
    Bluetooth TXD -> Mega RX1 pin 19
    Bluetooth RXD -> Mega TX1 pin 18 through a voltage divider
    Bluetooth VCC -> correct module power input, usually 5V on HC-05 breakout
    Bluetooth GND -> Mega GND

  Important:
    TX goes to RX, RX goes to TX.
    Arduino pins do not power motors directly. Use motor drivers, relays, or MOSFETs.
*/

const unsigned long COMMAND_TIMEOUT_MS = 1000;
const unsigned long ANCHOR_PULSE_MS = 700;
const unsigned long BLUETOOTH_BAUD = 9600;

// L298N / similar motor driver pins. Change these if your wiring is different.
const byte LEFT_EN_PIN = 5;    // PWM speed pin
const byte LEFT_IN1_PIN = 2;
const byte LEFT_IN2_PIN = 3;
const byte RIGHT_EN_PIN = 6;   // PWM speed pin
const byte RIGHT_IN1_PIN = 4;
const byte RIGHT_IN2_PIN = 7;

// Mechanism outputs. These should drive relays, MOSFETs, or motor-driver inputs.
const byte BELT_PIN = 8;
const byte SHREDDER_PIN = 9;
const byte BUCKET_PIN = 12;
const byte ANCHOR_DOWN_PIN = A0;
const byte ANCHOR_UP_PIN = A1;

byte speedPercent = 70;
unsigned long lastMoveCommandAt = 0;
unsigned long anchorStopAt = 0;

void setup() {
  pinMode(LEFT_EN_PIN, OUTPUT);
  pinMode(LEFT_IN1_PIN, OUTPUT);
  pinMode(LEFT_IN2_PIN, OUTPUT);
  pinMode(RIGHT_EN_PIN, OUTPUT);
  pinMode(RIGHT_IN1_PIN, OUTPUT);
  pinMode(RIGHT_IN2_PIN, OUTPUT);

  pinMode(BELT_PIN, OUTPUT);
  pinMode(SHREDDER_PIN, OUTPUT);
  pinMode(BUCKET_PIN, OUTPUT);
  pinMode(ANCHOR_DOWN_PIN, OUTPUT);
  pinMode(ANCHOR_UP_PIN, OUTPUT);

  stopMotors();
  setMechanismsOff();
  stopAnchor();

  Serial.begin(9600);          // USB Serial Monitor for testing/debugging
  Serial1.begin(BLUETOOTH_BAUD); // Bluetooth module on Mega pins 18/19

  Serial.println("RiverROBOT3.0 Bluetooth controller ready");
  Serial.println("Pair phone with HC-05/HC-06, then connect from the app.");
}

void loop() {
  readBluetoothCommands();
  readUsbSerialCommands();
  stopMotorsIfCommandLost();
  stopAnchorAfterPulse();
}

void readBluetoothCommands() {
  while (Serial1.available() > 0) {
    char command = Serial1.read();
    handleCommand(command);
  }
}

void readUsbSerialCommands() {
  while (Serial.available() > 0) {
    char command = Serial.read();
    handleCommand(command);
  }
}

void handleCommand(char command) {
  Serial.print("Command: ");
  Serial.println(command);

  switch (command) {
    case 'F':
      moveForward();
      break;
    case 'B':
      moveBackward();
      break;
    case 'L':
      turnLeft();
      break;
    case 'R':
      turnRight();
      break;
    case 'S':
      stopMotors();
      break;
    case 'C':
      digitalWrite(BELT_PIN, HIGH);
      break;
    case 'c':
      digitalWrite(BELT_PIN, LOW);
      break;
    case 'H':
      digitalWrite(SHREDDER_PIN, HIGH);
      break;
    case 'h':
      digitalWrite(SHREDDER_PIN, LOW);
      break;
    case 'K':
      digitalWrite(BUCKET_PIN, HIGH);
      break;
    case 'k':
      digitalWrite(BUCKET_PIN, LOW);
      break;
    case 'A':
      anchorDown();
      break;
    case 'U':
      anchorUp();
      break;
    case 'V':
      readSpeedValue();
      break;
    default:
      break;
  }
}

void readSpeedValue() {
  unsigned long startedAt = millis();

  while (Serial1.available() == 0 && Serial.available() == 0) {
    if (millis() - startedAt > 100) {
      return;
    }
  }

  int value = Serial1.available() > 0 ? Serial1.read() : Serial.read();
  setSpeed(value);
}

void setSpeed(int value) {
  speedPercent = constrain(value, 0, 100);

  Serial.print("Speed: ");
  Serial.print(speedPercent);
  Serial.println("%");
}

void moveForward() {
  setLeftMotor(true, motorPwm());
  setRightMotor(true, motorPwm());
  lastMoveCommandAt = millis();
}

void moveBackward() {
  setLeftMotor(false, motorPwm());
  setRightMotor(false, motorPwm());
  lastMoveCommandAt = millis();
}

void turnLeft() {
  setLeftMotor(false, motorPwm());
  setRightMotor(true, motorPwm());
  lastMoveCommandAt = millis();
}

void turnRight() {
  setLeftMotor(true, motorPwm());
  setRightMotor(false, motorPwm());
  lastMoveCommandAt = millis();
}

void stopMotors() {
  analogWrite(LEFT_EN_PIN, 0);
  analogWrite(RIGHT_EN_PIN, 0);
  digitalWrite(LEFT_IN1_PIN, LOW);
  digitalWrite(LEFT_IN2_PIN, LOW);
  digitalWrite(RIGHT_IN1_PIN, LOW);
  digitalWrite(RIGHT_IN2_PIN, LOW);
}

void setLeftMotor(bool forward, byte pwm) {
  digitalWrite(LEFT_IN1_PIN, forward ? HIGH : LOW);
  digitalWrite(LEFT_IN2_PIN, forward ? LOW : HIGH);
  analogWrite(LEFT_EN_PIN, pwm);
}

void setRightMotor(bool forward, byte pwm) {
  digitalWrite(RIGHT_IN1_PIN, forward ? HIGH : LOW);
  digitalWrite(RIGHT_IN2_PIN, forward ? LOW : HIGH);
  analogWrite(RIGHT_EN_PIN, pwm);
}

byte motorPwm() {
  return map(speedPercent, 0, 100, 0, 255);
}

void stopMotorsIfCommandLost() {
  if (lastMoveCommandAt == 0) {
    return;
  }

  if (millis() - lastMoveCommandAt > COMMAND_TIMEOUT_MS) {
    stopMotors();
    lastMoveCommandAt = 0;
  }
}

void anchorDown() {
  digitalWrite(ANCHOR_UP_PIN, LOW);
  digitalWrite(ANCHOR_DOWN_PIN, HIGH);
  anchorStopAt = millis() + ANCHOR_PULSE_MS;
}

void anchorUp() {
  digitalWrite(ANCHOR_DOWN_PIN, LOW);
  digitalWrite(ANCHOR_UP_PIN, HIGH);
  anchorStopAt = millis() + ANCHOR_PULSE_MS;
}

void stopAnchor() {
  digitalWrite(ANCHOR_DOWN_PIN, LOW);
  digitalWrite(ANCHOR_UP_PIN, LOW);
  anchorStopAt = 0;
}

void stopAnchorAfterPulse() {
  if (anchorStopAt != 0 && millis() >= anchorStopAt) {
    stopAnchor();
  }
}

void setMechanismsOff() {
  digitalWrite(BELT_PIN, LOW);
  digitalWrite(SHREDDER_PIN, LOW);
  digitalWrite(BUCKET_PIN, LOW);
}

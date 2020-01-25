const int led = 13;
const int pingThreshold = 2000;
const int psPin = 2;
const int FIRE = 0;
const int FALL = 1;
const int smokePin = A0;
const int fireThreshold = 512;
const int trigPin = 11;
const int echoPin = 10;
const int pwm = 75;

char received;
int prevFallState = 0;
int currFallState = 0;
unsigned long lastPing;
bool fallAlertEnable = false;
bool fireAlertEnable = false;
bool proximityAlertEnable = false;
int fireValues[5] = {1023, 1023, 1023, 1023, 1023};

void setup()
{
  pinMode(6, OUTPUT);  //left motors forward
  pinMode(3, OUTPUT);  //left motors reverse
  pinMode(9, OUTPUT);  //right motors forward
  pinMode(5, OUTPUT);  //right motors reverse

  pinMode(led, OUTPUT);
  pinMode(psPin, INPUT);

  pinMode(trigPin, OUTPUT); // Sets the trigPin as an Output
  pinMode(echoPin, INPUT); // Sets the echoPin as an Input

  digitalWrite(led, LOW);
  Serial.begin(9600);
}

void loop()
{
  waitToConnect();
  work();
  reConfigure();
}

void waitToConnect() {
  while (Serial.available() < 1) delay(1);
}
void work() {
  digitalWrite(led, HIGH);
  while (1) {
    received='Z';
    if (Serial.available() > 1) {
      received = Serial.read();
      lastPing = millis();
    }
    if(received!='Z')
      Serial.write(received);
    switch (received) {
      case 'Z':
        break;
      case 'E':
        fallAlertEnable = true;
        break;
      case 'D':
        fallAlertEnable = false;
        break;
      case 'X':
        fireAlertEnable = true;
        break;
      case 'Y':
        fireAlertEnable = false;
        break;
      case 'H':
        proximityAlertEnable = true;
        break;
      case 'I':
        proximityAlertEnable = false;
        break;
      case 'F':
        goForword();
        break;
      case 'B':
        goBackword();
        break;
      case 'L':
        goLeft();
        break;
      case 'R':
        goRight();
        break;
      case 'S':
        stopWheels();
        break;
    }
    if ((millis() - lastPing) > pingThreshold) break; //exiting loop
    checkFall();
    checkFire();
    checkProximity();
    delay(40);
  }
  digitalWrite(led, LOW);
}
void reConfigure() {
  stopWheels();
  fallAlertEnable = false;
  fireAlertEnable = false;
  proximityAlertEnable = false;
}
void checkFall() {
  if (fallAlertEnable) {
    prevFallState = currFallState;
    currFallState = digitalRead(psPin);
    if (currFallState == LOW && prevFallState == HIGH) {
      stopWheels();
      sendAlert(FALL);
    }
  }
}

void checkFire() {
  if (fallAlertEnable) {
    if (getAvgFire(analogRead(smokePin)) > fireThreshold) {
      sendAlert(FIRE);
    }
  }
}

void checkProximity() {
  if (proximityAlertEnable) {
    int distance = readProximity();
    Serial.print(distance);
    Serial.write('P');
  }
}

void goForword() {
  analogWrite(6, pwm);
  analogWrite(9, pwm);
}
void goBackword() {
  analogWrite(3, pwm);
  analogWrite(5, pwm);
}
void goLeft() {
  analogWrite(3, pwm);
  analogWrite(9, pwm);
}
void goRight() {
  analogWrite(6, pwm);
  analogWrite(5, pwm);
}

void stopWheels() {
  analogWrite(6, 0);
  analogWrite(3, 0);
  analogWrite(9, 0);
  analogWrite(5, 0);
}

void sendAlert(int type) {
  if (type == FIRE) {
    Serial.write('J');
  }
  else
    Serial.write('K');
}

int getAvgFire(int val) {
  int i = 0;
  for (; i < 4; i++) {
    fireValues[i] = fireValues[i + 1];
  }
  fireValues[4] = val;
  int sum = 0;
  for (i = 0; i < 5; i++) {
    sum = sum + fireValues[i];
  }
  return sum / 5;
}

int readProximity() {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  long duration = pulseIn(echoPin, HIGH);
  return duration * 0.034 / 2; //cm
}

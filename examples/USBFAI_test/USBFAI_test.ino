
// Sample Arduino sketch for use with USBFAI
// Echoes the outputs and sends the atmega chip temperature 

static int len = 0;
static char buf[64];
static unsigned long time;
static double temp;

void setup() {
  Serial.begin(9600);
  time = millis();
  buf[0] = '0';
  buf[1] = '0';
  buf[2] = '0';
  buf[3] = '0';
}

void loop() {
  // messages are newline delimited
  if (Serial.peek() != -1) {
    do {
      char x = Serial.read();
      if (x != '\n')
        buf[len++] = x;
      else
        len = 0;
    } while (Serial.peek() != -1);
  }
  
  temp = GetTemp();
    
  // send every ~ 500 ms
  if ((millis() - time) > 500) {
    time = millis();
    Serial.write(buf[0]);
    Serial.write(buf[1]);
    Serial.write(buf[2]);
    Serial.write(buf[3]);
    Serial.write(" ");
    Serial.println(temp,1);
  }
}

double GetTemp(void) {
  unsigned int wADC;
  double t;

  ADMUX = (_BV(REFS1) | _BV(REFS0) | _BV(MUX3));
  ADCSRA |= _BV(ADEN);
  
  delay(20);
  
  ADCSRA |= _BV(ADSC);

  while (bit_is_set(ADCSRA,ADSC));
  wADC = ADCW;
  t = (wADC - 324.31 ) / 1.22;

  return (t);
}

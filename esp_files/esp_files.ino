#include <Wire.h>
#include <MPU6050.h>

#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

BluetoothSerial SerialBT;


#define MAX_READINGS 16
#define INTERVAl 100
#define DELAY INTERVAl/MAX_READINGS

typedef struct {
    float readings[MAX_READINGS];
    int count;
    int index;  // tracks position in circular buffer
    float sum;
} SmoothBuffer;


MPU6050 mpu;
SmoothBuffer buff[5];

int pin[] = {34,35,32,33,39};
float data[] = {0,0,0,0,0};

float udp_buff[] = {0,0,0,0,0,0,0};

float pitch = 0;
float roll = 0;


float smoothRead(SmoothBuffer *buf, float value) {
    buf->sum -= buf->readings[buf->index];
    buf->readings[buf->index] = value;
    buf->sum += value;

    buf->index = (buf->index + 1) % MAX_READINGS;

    if (buf->count < MAX_READINGS) {
        buf->count++;
    }

    return buf->sum / buf->count;
}


void sendFloatArray(const float* arr, size_t length) {
    SerialBT.write((uint8_t*)arr, length * sizeof(float));
}

void get_pr(){
  int16_t ax, ay, az;
  mpu.getAcceleration(&ax, &ay, &az);

  // Convert to g's
  float ax_g = ax / 16384.0;
  float ay_g = ay / 16384.0;
  float az_g = az / 16384.0;

  pitch = atan2(-ax_g, sqrt(ay_g * ay_g + az_g * az_g)) * 180.0 / PI;
  roll  = atan2(ay_g, az_g) * 180.0 / PI;

}


void setup() { 
    analogReadResolution(12);
    analogSetAttenuation(ADC_11db);   // full ~0–3.3 V range
    delay(1000);
    Serial.begin(115200);
    for( int i =0; i < 5; i ++){
        buff[i] = {0};
    }
    Wire.begin();
    mpu.initialize();
    if (!mpu.testConnection()) {
        Serial.println("MPU6050 connection failed");
    }
    Serial.println("MPU6050 connected");
    delay(100);
    
    SerialBT.begin("Gesten Left Glove V2"); //Bluetooth device name
    Serial.println("The device started, now you can pair it with bluetooth!");
    Serial.println("start");
} 


void loop() { 
    for( int i =0; i < 5; i ++){
        data[i] = smoothRead(&buff[i], analogRead(pin[i]));
    }

    if(buff[0].index == 0){
        get_pr();

        for( int i =0; i < 5; i ++){
            udp_buff[i] = data[i];
        }

        udp_buff[5] = roll;
        udp_buff[6] = pitch;
        Serial.println(roll);

        sendFloatArray(udp_buff, 7);
    }
    delay(DELAY);
}

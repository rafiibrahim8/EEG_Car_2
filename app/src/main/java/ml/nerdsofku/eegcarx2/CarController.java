package ml.nerdsofku.eegcarx2;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.atomic.AtomicInteger;

public class CarController {
    public static final int FORWARD = 1;
    public static final int BACKWARD = 2;
    public static final int LEFT = 3;
    public static final int RIGHT = 4;
    public static final int STOP = -1;
    public static final int BLANK = 0;

    public static final long DIRECTION_HOLD_MS = 2000;
    public static final long ARROW_BLINK_TIME = 250;
    public static final long DOUBLE_BLINK_THRESHOLD_MS = 800;
    public static final long BLINK_THRESHOLD = 60;
    public static final long ATTENTION_THRESHOLD = 50;

    public static final int STATE_IDLE = 100;
    public static final int STATE_CIRCLING_ARROWS = 101;
    public static final int STATE_FIXED_ON_DIRECTION = 102;
    public static final int STATE_RUNNING = 103;

    private Context context;
    private long lastBlinkTime;
    private long lastBlinkDifference;
    private int latBlinkStrength;
    private int lastAttention;
    private AtomicInteger currentPointedDirection;
    private volatile int currentState;
    private volatile boolean isCarConnected;

    public CarController(Context contex){
        lastBlinkTime = System.currentTimeMillis()-10000;//10000 for safety
        currentPointedDirection = new AtomicInteger(BLANK);
        currentState = STATE_IDLE;
        latBlinkStrength = 0;
        lastAttention = 0;
        isCarConnected = false;
        this.context = contex;
        startThread();
    }

    public void registerAttention(int attention){
        lastAttention = attention;
        /*
        if(currentState == STATE_FIXED_ON_DIRECTION && lastAttention>=ATTENTION_THRESHOLD){
            currentState = STATE_RUNNING;
            sendCommandToCar(currentPointedDirection);
        }
        else if(currentState == STATE_RUNNING && lastAttention<ATTENTION_THRESHOLD){
            currentState = STATE_IDLE;
            sendCommandToCar(STOP);
        }

         */
    }

    public void setCarConnected(boolean carConnected) {
        isCarConnected = carConnected;
    }

    private void sendCommandToCar(int currentPointedDirection) {
        Intent intent = new Intent("sendToCar");
        String directionInString;
        switch (currentPointedDirection){
            case FORWARD:
                directionInString = "F";
                break;
            case BACKWARD:
                directionInString = "B";
                break;
            case LEFT:
                directionInString = "L";
                break;
            case RIGHT:
                directionInString = "R";
                break;
                default:
                    directionInString = "S";
                    break;
        }
        intent.putExtra("direction",directionInString);
        context.sendBroadcast(intent);
    }

    public void registerBlink(int strength){
        this.lastBlinkDifference = lastBlinkTime - System.currentTimeMillis();
        this.lastBlinkTime = System.currentTimeMillis();
        this.latBlinkStrength = strength;
        onBlinkRegister();
    }

    private void onBlinkRegister() {
        switch (currentState){
            case STATE_IDLE:
                if(latBlinkStrength>=BLINK_THRESHOLD)
                    currentState = STATE_CIRCLING_ARROWS;
                break;
            case STATE_CIRCLING_ARROWS:
                if(lastBlinkDifference<=DOUBLE_BLINK_THRESHOLD_MS)
                    currentState=STATE_FIXED_ON_DIRECTION;
                break;
            case STATE_RUNNING:
                currentState = STATE_IDLE;
                sendCommandToCar(STOP);
            case STATE_FIXED_ON_DIRECTION:
                currentState = STATE_RUNNING;
                sendCommandToCar(currentPointedDirection.get());
        }
    }

    private void updateUI(int dir){
        Intent intent = new Intent("updateUI");
        intent.putExtra("arrows",dir);
        context.sendBroadcast(intent);
    }

    private void startThread() {
        new Thread(()->{
            while (true){
                if(currentState == STATE_CIRCLING_ARROWS){
                    currentPointedDirection.addAndGet(1);
                    if(currentPointedDirection.get()>RIGHT)
                        currentPointedDirection.set(FORWARD);
                    updateUI(currentPointedDirection.get());
                    try{ Thread.sleep(DIRECTION_HOLD_MS); } catch (Exception ex){}

                }
                else if(currentState == STATE_RUNNING){
                    updateUI(currentPointedDirection.get());
                    try{ Thread.sleep(ARROW_BLINK_TIME); } catch (Exception ex){}
                    updateUI(BLANK);
                    try{ Thread.sleep(ARROW_BLINK_TIME); } catch (Exception ex){}
                }
                else if(currentState ==STATE_IDLE){
                    updateUI(STATE_IDLE);
                }


                try{ Thread.sleep(1); } catch (Exception ex){}
            }
        }).start();
    }

}
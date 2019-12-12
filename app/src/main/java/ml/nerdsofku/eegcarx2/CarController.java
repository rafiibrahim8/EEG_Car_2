package ml.nerdsofku.eegcarx2;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.atomic.AtomicInteger;
import static ml.nerdsofku.eegcarx2.MainActivity.sleep;

public class CarController {
    public static final int FORWARD = 1;
    public static final int BACKWARD = 2;
    public static final int LEFT = 3;
    public static final int RIGHT = 4;
    public static final int STOP = -1;
    public static final int NONE = 0;
    public static final int ALL = 15;

    public static final long DIRECTION_HOLD_MS = 2000;
    public static final long ARROW_BLINK_TIME = 250;
    public static final long DOUBLE_BLINK_THRESHOLD_MS = 800;
    public static final long BLINK_THRESHOLD = 60;
    public static final long ATTENTION_THRESHOLD = 50;

    public static final int STATE_IDLE = 100;
    public static final int STATE_CIRCLING_ARROWS = 101;
    public static final int STATE_FIXED_ON_DIRECTION = 102;
    public static final int STATE_RUNNING = 103;

    public static final int TIMER_PREC_MS = 100;
    public static final int THREAD_RELAX_TIME = 1;

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
        currentPointedDirection = new AtomicInteger(NONE);
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
    public void setCurrentState(int state){
        currentState = state;
        updateStateUI(state);
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
        this.lastBlinkDifference = System.currentTimeMillis() - lastBlinkTime;
        this.lastBlinkTime = System.currentTimeMillis();
        this.latBlinkStrength = strength;
        onBlinkRegister();
    }

    public int getCurrentState() {
        return currentState;
    }

    private void onBlinkRegister() {
        switch (currentState){
            case STATE_IDLE:
                if(latBlinkStrength>=BLINK_THRESHOLD)
                    currentState = STATE_CIRCLING_ARROWS;
                updateStateUI(currentState);
                break;
            case STATE_CIRCLING_ARROWS:
                if(lastBlinkDifference<=DOUBLE_BLINK_THRESHOLD_MS)
                    currentState=STATE_FIXED_ON_DIRECTION;
                updateStateUI(currentState);
                break;
            case STATE_RUNNING:
                currentState = STATE_IDLE;
                updateStateUI(currentState);
                currentPointedDirection.set(NONE);
                sendCommandToCar(STOP);
                break;
            case STATE_FIXED_ON_DIRECTION:
                currentState = STATE_RUNNING;
                updateStateUI(currentState);
                sendCommandToCar(currentPointedDirection.get());
                break;
        }
    }

    private void updateArrowUI(int dir){
        Intent intent = new Intent("updateArrows");
        intent.putExtra("arrows",dir);
        context.sendBroadcast(intent);
    }

    private void updateTimerUI(float left){
        Intent intent = new Intent("updateTimer");
        intent.putExtra("leftTime","Arrow will change in "+left+"s");
        context.sendBroadcast(intent);
    }

    private void updateStateUI(int state){
        Intent intent = new Intent("updateState");
        intent.putExtra("state",state);
        context.sendBroadcast(intent);
    }

    private void startThread() {

        new Thread(()->{
            while (true){
                if(currentState == STATE_CIRCLING_ARROWS){
                    currentPointedDirection.addAndGet(1);
                    if(currentPointedDirection.get()>RIGHT)
                        currentPointedDirection.set(FORWARD);
                    updateArrowUI(currentPointedDirection.get());
                    int timeLeft = (int) DIRECTION_HOLD_MS;
                    int beforeLoopState = currentState;
                    while(timeLeft>0 && currentState==beforeLoopState){
                        sleep(TIMER_PREC_MS-THREAD_RELAX_TIME);
                        timeLeft-=TIMER_PREC_MS;
                        updateTimerUI((float)timeLeft/1000);
                    }
                }

                else if(currentState == STATE_RUNNING){
                    updateArrowUI(currentPointedDirection.get());
                    try{ Thread.sleep(ARROW_BLINK_TIME); } catch (Exception ex){}
                    updateArrowUI(NONE);
                    try{ Thread.sleep(ARROW_BLINK_TIME); } catch (Exception ex){}
                }
                else if(currentState ==STATE_IDLE){
                    updateArrowUI(ALL);
                }


                try{ Thread.sleep(THREAD_RELAX_TIME); } catch (Exception ex){}
            }
        }).start();
    }

}

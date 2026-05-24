package com.xai.dnareplicator.presentation.javafx;

import javafx.animation.AnimationTimer;
import org.springframework.stereotype.Component;

import java.util.function.LongConsumer;

/**
 * Owns the JavaFX {@link AnimationTimer} used for infection simulation ticks.
 */
@Component
public class InfectionAnimationDriver {

    private AnimationTimer timer;

    public void start(LongConsumer onFrame) {
        stop();
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onFrame.accept(now);
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}

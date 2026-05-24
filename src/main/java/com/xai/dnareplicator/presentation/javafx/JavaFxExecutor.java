package com.xai.dnareplicator.presentation.javafx;

import javafx.application.Platform;
import org.springframework.stereotype.Component;

/**
 * Runs actions on the JavaFX application thread.
 */
@Component
public class JavaFxExecutor {

    public void runLater(Runnable action) {
        if (action == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}

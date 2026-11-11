package com.restaurant.ui;

import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs slow work (like a database call) on a background thread so the window never freezes,
 * then hands the result back on the Swing UI thread.
 * <p>
 * Usage:
 * <pre>
 * Background.run(() -> service.getAllItems(),      // runs in the background
 *                items -> showItems(items),        // runs on the UI thread when it worked
 *                error -> Messages.error(this, error)); // runs on the UI thread when it failed
 * </pre>
 * Uses generics, a lambda-friendly Callable and Consumer (functional interfaces).
 */
public final class Background {

    private static int pending;
    private static Consumer<Boolean> busyListener = busy -> { };

    private Background() {
    }

    /** MainFrame registers here to show / hide the loading bar while any background task is running. */
    public static void setBusyListener(Consumer<Boolean> listener) {
        busyListener = listener;
    }

    private static void change(int delta) {
        pending = Math.max(0, pending + delta);
        busyListener.accept(pending > 0);
    }

    public static <T> void run(Callable<T> task, Consumer<T> onSuccess, Consumer<Exception> onError) {
        change(1); // started (always called on the UI thread)
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                change(-1);
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    onError.accept(cause instanceof Exception ex ? ex : new Exception(cause));
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onError.accept(e);
                    return;
                }
                onSuccess.accept(result);
            }
        }.execute();
    }
}

//
// React - a library for functional-reactive-like programming
// Copyright (c) 2013, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/react/blob/master/LICENSE

package react;

/**
 * Provides a concrete implementation {@link RFuture} that can be updated with a success or failure
 * result when it becomes available.
 *
 * <p>This implementation also guarantees a useful behavior, which is that all listeners added
 * prior to the completion of the promise will be cleared when the promise is completed, and no
 * further listeners will be retained. This allows the promise to be retained after is has been
 * completed as a useful "box" for its underlying value, without concern that references to long
 * satisfied listeners will be inadvertently retained.</p>
 */
public class RPromise<T> extends RFuture<T> {

    /** Creates a new, uncompleted, promise. */
    public static <T> RPromise<T> create () {
        return new RPromise<T>();
    }

    /** Causes this promise to be completed with {@code result}. */
    public void complete (Try<T> result) {
        if (_result != null) throw new IllegalStateException("Already completed");
        _result = result;
        try {
            notify(COMPLETE, result, null, null);
        } finally {
            clearConnections();
        }
    }

    /** Causes this promise to be completed successfully with {@code value}. */
    public void succeed (T value) {
        complete(Try.success(value));
    }

    /** Causes this promise to be completed with failure caused by {@code cause}. */
    public void fail (Throwable cause) {
        complete(Try.<T>failure(cause));
    }

    /** Returns a slot that can be used to complete this promise. */
    public Slot<Try<T>> completer () {
        return new Slot<Try<T>>() {
            public void onEmit (Try<T> result) {
                complete(result);
            }
        };
    }

    /** Returns a slot that can be used to {@link #succeed} this promise. */
    public Slot<T> succeeder () {
        return new Slot<T>() {
            public void onEmit (T result) {
                succeed(result);
            }
        };
    }

    /** Returns a slot that can be used to {@link #fail} this promise. */
    public Slot<Throwable> failer () {
        return new Slot<Throwable>() {
            public void onEmit (Throwable cause) {
                fail(cause);
            }
        };
    }

    @Override public Try<T> result () {
        return _result;
    }

    protected Try<T> _result;

    @SuppressWarnings("unchecked") protected static final Notifier COMPLETE = new Notifier() {
        public void notify (Object lner, Object value, Object i0, Object i1) {
            ((SignalView.Listener<Try<Object>>)lner).onEmit((Try<Object>)value);
        }
    };
}

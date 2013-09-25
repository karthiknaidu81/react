//
// React - a library for functional-reactive-like programming in Java
// Copyright (c) 2011, Three Rings Design, Inc. - All rights reserved.
// http://github.com/threerings/react/blob/master/LICENSE

package react;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests basic signals and slots behavior.
 */
public class SignalTest
{
    public static class Counter extends UnitSlot {
        public int notifies;
        @Override public void onEmit () {
            notifies++;
        }
    }

    public static <T> Slot<T> require (final T reqValue) {
        return new Slot<T>() {
            public void onEmit (T value) {
                assertEquals(reqValue, value);
            }
        };
    }

    @Test public void testSignalToSlot () {
        Signal<Integer> signal = Signal.create();
        AccSlot<Integer> slot = new AccSlot<Integer>();
        signal.connect(slot);
        signal.emit(1);
        signal.emit(2);
        signal.emit(3);
        assertEquals(Arrays.asList(1, 2, 3), slot.events);
    }

    @Test public void testOneShotSlot () {
        Signal<Integer> signal = Signal.create();
        AccSlot<Integer> slot = new AccSlot<Integer>();
        signal.connect(slot).once();
        signal.emit(1); // slot should be removed after this emit
        signal.emit(2);
        signal.emit(3);
        assertEquals(Arrays.asList(1), slot.events);
    }

    @Test public void testSlotPriority () {
        final int[] counter = new int[] { 0 };
        class TestSlot extends UnitSlot {
            public int order;
            public void onEmit () {
                order = ++counter[0];
            }
        }
        TestSlot slot1 = new TestSlot();
        TestSlot slot2 = new TestSlot();
        TestSlot slot3 = new TestSlot();
        TestSlot slot4 = new TestSlot();

        UnitSignal signal = new UnitSignal();
        signal.connect(slot3).atPriority(3);
        signal.connect(slot1).atPriority(1);
        signal.connect(slot2).atPriority(2);
        signal.connect(slot4).atPriority(4);
        signal.emit();
        assertEquals(1, slot1.order);
        assertEquals(2, slot2.order);
        assertEquals(3, slot3.order);
        assertEquals(4, slot4.order);
    }

    @Test public void testAddDuringDispatch () {
        final Signal<Integer> signal = Signal.create();
        final AccSlot<Integer> toAdd = new AccSlot<Integer>();
        signal.connect(new UnitSlot() {
            public void onEmit () {
                signal.connect(toAdd);
            }
        }).once();

        // this will connect our new signal but not dispatch to it
        signal.emit(5);
        assertEquals(0, toAdd.events.size());

        // now dispatch an event that should go to the added signal
        signal.emit(42);
        assertEquals(Arrays.asList(42), toAdd.events);
    }

    @Test public void testRemoveDuringDispatch () {
        final Signal<Integer> signal = Signal.create();
        final AccSlot<Integer> toRemove = new AccSlot<Integer>();
        final Connection rconn = signal.connect(toRemove);

        // dispatch one event and make sure it's received
        signal.emit(5);
        assertEquals(Arrays.asList(5), toRemove.events);

        // now add our removing signal, and dispatch again
        signal.connect(new UnitSlot() {
            public void onEmit () {
                rconn.disconnect();
            }
        }).atPriority(1); // ensure that we're before toRemove
        signal.emit(42);
        // since toRemove will have been removed during this dispatch, it will receive the signal
        // in question, even though the higher priority signal triggered first
        assertEquals(Arrays.asList(5, 42), toRemove.events);
        // finally dispatch one more event and make sure toRemove didn't get it
        signal.emit(9);
        assertEquals(Arrays.asList(5, 42), toRemove.events);
    }

    @Test public void testAddAndRemoveDuringDispatch () {
        final Signal<Integer> signal = Signal.create();
        final AccSlot<Integer> toAdd = new AccSlot<Integer>();
        final AccSlot<Integer> toRemove = new AccSlot<Integer>();
        final Connection rconn = signal.connect(toRemove);

        // dispatch one event and make sure it's received by toRemove
        signal.emit(5);
        assertEquals(Arrays.asList(5), toRemove.events);

        // now add our adder/remover signal, and dispatch again
        signal.connect(new UnitSlot() {
            public void onEmit () {
                rconn.disconnect();
                signal.connect(toAdd);
            }
        });
        signal.emit(42);
        // make sure toRemove got this event and toAdd didn't
        assertEquals(Arrays.asList(5, 42), toRemove.events);
        assertEquals(0, toAdd.events.size());

        // finally emit one more and ensure that toAdd got it and toRemove didn't
        signal.emit(9);
        assertEquals(Arrays.asList(9), toAdd.events);
        assertEquals(Arrays.asList(5, 42), toRemove.events);
    }

    @Test public void testUnitSlot () {
        Signal<Integer> signal = Signal.create();
        final boolean[] fired = new boolean[] { false };
        signal.connect(new UnitSlot() {
            public void onEmit () {
                fired[0] = true;
            }
        });
        signal.emit(42);
        assertTrue(fired[0]);
    }

    @Test(expected=RuntimeException.class)
    public void testSingleFailure () {
        UnitSignal signal = new UnitSignal();
        signal.connect(new UnitSlot() {
            public void onEmit () {
                throw new RuntimeException("Bang!");
            }
        });
        signal.emit();
    }

    @Test(expected=RuntimeException.class)
    public void testMultiFailure () {
        UnitSignal signal = new UnitSignal();
        signal.connect(new UnitSlot() {
            public void onEmit () {
                throw new RuntimeException("Bing!");
            }
        });
        signal.connect(new UnitSlot() {
            public void onEmit () {
                throw new RuntimeException("Bang!");
            }
        });
        signal.emit();
    }

    @Test public void testMappedSignal () {
        Signal<Integer> signal = Signal.create();
        SignalView<String> mapped = signal.map(Functions.TO_STRING);

        Counter counter = new Counter();
        Connection c1 = mapped.connect(counter);
        Connection c2 = mapped.connect(SignalTest.require("15"));

        signal.emit(15);
        assertEquals(1, counter.notifies);
        signal.emit(15);
        assertEquals(2, counter.notifies);

        // disconnect from the mapped signal and ensure that it clears its connection
        c1.disconnect();
        c2.disconnect();
        assertFalse(signal.hasConnections());
    }

    @Test public void testFiltered () {
        final int[] triggered = new int[1];
        Slot<String> onString = new Slot<String>() {
            public void onEmit (String value) {
                assertFalse(value == null);
                triggered[0]++;
            }
        };
        Signal<String> sig = Signal.create();
        sig.connect(onString.filtered(Functions.NON_NULL));
        sig.emit(null);
        sig.emit("foozle");
        assertEquals(1, triggered[0]);
    }

    protected static class AccSlot<T> extends Slot<T> {
        public List<T> events = new ArrayList<T>();
        public void onEmit (T event) {
            events.add(event);
        }
    }
}

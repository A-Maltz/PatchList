import dev.amaltz.collections.PatchList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class PatchListTest {

    private PatchList<String> list;

    @BeforeEach
    void setUp() {
        // Default capacity is 16
        list = new PatchList<>();
    }

    // --- 1. BASIC OPERATIONS & CAPACITY SPLITTING ---

    @Test
    void testAddAndGet_SingleNode() {
        list.add("A");
        list.add("B");
        list.add("C");

        assertEquals(3, list.size());
        assertEquals("A", list.get(0));
        assertEquals("B", list.get(1));
        assertEquals("C", list.get(2));
    }

    @Test
    void testAddAndGet_MultipleNodes_TriggersSplits() {
        // Adding 50 items forces multiple node splits (50 > 16)
        for (int i = 0; i < 50; i++) {
            list.add("Item" + i);
        }

        assertEquals(50, list.size());

        // Test boundaries and random access
        assertEquals("Item0", list.get(0));
        assertEquals("Item15", list.get(15)); // End of first node
        assertEquals("Item16", list.get(16)); // Start of second node
        assertEquals("Item49", list.get(49)); // Tail
    }

    @Test
    void testGet_OutOfBounds() {
        list.add("A");
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    }

    @Test
    void testClear() {
        for (int i = 0; i < 20; i++) list.add("X");
        list.clear();
        assertEquals(0, list.size());
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
    }

    // --- 2. CACHE INVALIDATION (The crucial fix) ---

    @Test
    void testCacheInvalidation_OnListAdd() {
        list.add("A");
        list.add("B");

        // This caches Node 1
        assertEquals("B", list.get(1));

        // This should invalidate the cache
        list.add(0, "Z"); // Standard AbstractSequentialList method using iterator under the hood

        // If cache wasn't invalidated, get(1) might incorrectly return "B" instead of "A"
        assertEquals("A", list.get(1));
    }

    @Test
    void testCacheInvalidation_OnIteratorRemove() {
        list.add("A");
        list.add("B");
        list.add("C");

        assertEquals("B", list.get(1)); // Cache primed

        ListIterator<String> it = list.listIterator();
        it.next(); // "A"
        it.remove(); // Removes "A", list is now ["B", "C"]

        // If cache failed to invalidate, this might crash or return wrong data
        assertEquals("C", list.get(1));
    }

    // --- 3. NULL HANDLING ---

    @Test
    void testNullSupport() {
        list.add("A");
        list.add(null);
        list.add("C");

        assertEquals(3, list.size());
        assertNull(list.get(1));

        // Ensure array shifts handle nulls properly
        ListIterator<String> it = list.listIterator(1);
        assertNull(it.next());
        it.remove();

        assertEquals(2, list.size());
        assertEquals("C", list.get(1));
    }

    // --- 4. LIST ITERATOR: FAST FORWARDING ---

    @Test
    void testIterator_FastForwardInitialization() {
        for (int i = 0; i < 50; i++) list.add("Item" + i);

        // Jump straight to index 35 (bypassing the first two nodes entirely)
        ListIterator<String> it = list.listIterator(35);
        assertEquals("Item35", it.next());
        assertEquals(36, it.nextIndex());
    }

    @Test
    void testIterator_InitializeAtEnd() {
        list.add("A");
        list.add("B");
        ListIterator<String> it = list.listIterator(2);

        assertFalse(it.hasNext());
        assertTrue(it.hasPrevious());
        assertEquals("B", it.previous());
    }

    // --- 5. LIST ITERATOR: MUTATIONS ---

    @Test
    void testIterator_Add() {
        list.add("A");
        list.add("C");

        ListIterator<String> it = list.listIterator(1);
        it.add("B"); // Insert "B" between "A" and "C"

        assertEquals(3, list.size());
        assertEquals("A", list.get(0));
        assertEquals("B", list.get(1));
        assertEquals("C", list.get(2));
    }

    @Test
    void testIterator_Add_ForcesSplit() {
        // Fill exact capacity
        for (int i = 0; i < 16; i++) list.add("O");

        // Insert in the middle of a full node, forcing a split
        ListIterator<String> it = list.listIterator(8);
        it.add("X");

        assertEquals(17, list.size());
        assertEquals("X", list.get(8));
        assertEquals("O", list.get(16)); // The last element pushed back
    }

    @Test
    void testIterator_Remove_EmptyNodeCleanup() {
        // Add exactly 17 elements (2 nodes: Node1[16], Node2[1])
        for (int i = 0; i < 17; i++) list.add("X");

        ListIterator<String> it = list.listIterator(16);
        it.next();
        it.remove(); // Removes the only element in the second node

        // The second node should be garbage collected and unlinked.
        assertEquals(16, list.size());

        // Try adding a new element to see if tail state is corrupted
        list.add("Y");
        assertEquals(17, list.size());
        assertEquals("Y", list.get(16));
    }

    @Test
    void testIterator_Set() {
        list.add("A");
        list.add("B");

        ListIterator<String> it = list.listIterator();
        it.next(); // "A"
        it.set("X");

        assertEquals("X", list.get(0));
        assertEquals("B", list.get(1));
    }

    // --- 6. BI-DIRECTIONAL ITERATION ---

    @Test
    void testIterator_BackwardTraversal() {
        list.add("A");
        list.add("B");
        list.add("C");

        ListIterator<String> it = list.listIterator(3);

        assertTrue(it.hasPrevious());
        assertEquals("C", it.previous());
        assertEquals("B", it.previous());
        assertEquals("A", it.previous());
        assertFalse(it.hasPrevious());
        assertThrows(NoSuchElementException.class, it::previous);
    }

    @Test
    void testDescendingIterator() {
        list.add("A");
        list.add("B");
        list.add("C");

        Iterator<String> it = list.descendingIterator();
        assertTrue(it.hasNext());
        assertEquals("C", it.next());
        assertEquals("B", it.next());
        assertEquals("A", it.next());
        assertFalse(it.hasNext());
    }

    // --- 7. FAIL-FAST (CONCURRENT MODIFICATION) ---

    @Test
    void testConcurrentModificationException() {
        list.add("A");
        list.add("B");

        ListIterator<String> it = list.listIterator();

        // Modify list structurally outside the iterator
        list.add("C");

        assertThrows(ConcurrentModificationException.class, it::next);
    }

    // --- 8. UTILITIES ---

    @Test
    void testToArray() {
        list.add("A");
        list.add("B");
        list.add("C");

        Object[] arr = list.toArray();
        assertEquals(3, arr.length);
        assertEquals("A", arr[0]);
        assertEquals("B", arr[1]);
        assertEquals("C", arr[2]);
    }

    @Test
    void testSerialization() throws IOException, ClassNotFoundException {
        for (int i = 0; i < 20; i++) list.add("Item" + i); // Crosses node boundaries

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(list);
        oos.close();

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        @SuppressWarnings("unchecked")
        PatchList<String> deserializedList = (PatchList<String>) ois.readObject();
        ois.close();

        // Verify
        assertEquals(list.size(), deserializedList.size());
        for (int i = 0; i < list.size(); i++) {
            assertEquals(list.get(i), deserializedList.get(i));
        }
    }
}
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatchListTest {

    private PatchList<String> list;

    @BeforeEach
    void setUp() {
        // Runs before every single @Test method to give us a fresh list
        list = new PatchList<>();
    }

    // ==========================================
    //          CORRECTNESS TESTS
    // ==========================================

    @Test
    void testBasicAppendingAndSize() {
        for (int i = 0; i < 20; i++) {
            list.add("Item_" + i);
        }

        assertEquals(20, list.size(), "List size should be exactly 20.");
        assertEquals("Item_0", list.getFirst(), "First item should be Item_0");
        assertEquals("Item_19", list.get(19), "Last item should be Item_19");
    }

    @Test
    void testMiddleInsertionAndShifting() {
        for (int i = 0; i < 10; i++) {
            list.add("Item_" + i);
        }

        // Insert exactly in the middle of the first node
        list.add(5, "INSERTED");

        assertEquals(11, list.size(), "Size should increase by 1 after insertion.");
        assertEquals("INSERTED", list.get(5), "Item at index 5 should be the newly inserted string.");
        assertEquals("Item_5", list.get(6), "Previous item at index 5 should have shifted right to index 6.");
    }

    @Test
    void testNullSentinelInsertion() {
        list.add("Item_0");
        list.add("Item_1");

        // Insert a null in between
        list.add(1, null);

        assertEquals(3, list.size());
        assertNull(list.get(1), "Index 1 should successfully return a literal null.");
        assertEquals("Item_1", list.get(2), "Item_1 should be shifted to index 2.");
    }

    @Test
    void testRemoval() {
        for (int i = 0; i < 10; i++) {
            list.add("Item_" + i);
        }

        String removedItem = list.remove(4);

        assertEquals("Item_4", removedItem, "remove() should return the item that was deleted.");
        assertEquals(9, list.size(), "Size should decrease by 1.");
        assertEquals("Item_5", list.get(4), "Items should shift to fill the logical gap.");
    }

    @Test
    void testOutOfBoundsExceptions() {
        list.add("Item_0");

        // Should throw an exception if we try to access an index that doesn't exist
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(5), "Calling get() on an invalid index should throw IndexOutOfBoundsException.");

        assertThrows(IndexOutOfBoundsException.class, () -> list.add(-1, "Negative"), "Adding to a negative index should throw IndexOutOfBoundsException.");
    }

    // ==========================================
    //          ADVANCED CORRECTNESS TESTS
    // ==========================================

    @Test
    void testStrictOrderingAfterMultipleSplits() {
        // Force the list to split multiple times by adding 50 items
        for (int i = 0; i < 50; i++) {
            list.add("Item_" + i);
        }

        assertEquals(50, list.size());

        // Verify that every single item is in the exact mathematical order
        for (int i = 0; i < 50; i++) {
            assertEquals("Item_" + i, list.get(i), "Ordering failed at index " + i);
        }
    }

    @Test
    void testExtremeFrontInsertion() {
        // Adding to index 0 forces the head node to split and shift constantly
        for (int i = 0; i < 30; i++) {
            list.addFirst("Front_" + i);
        }

        assertEquals(30, list.size());

        // Because we added to the front, Front_29 should be at index 0
        assertEquals("Front_29", list.getFirst());
        assertEquals("Front_0", list.get(29));
    }

    @Test
    void testSetMethodReplacesWithoutShifting() {
        list.add("A");
        list.add("B");
        list.add("C");

        // set() should replace 'B' with 'X' and return the old value 'B'
        String oldVal = list.set(1, "X");

        assertEquals("B", oldVal, "set() should return the replaced item.");
        assertEquals(3, list.size(), "set() should NOT change the size of the list.");
        assertEquals("X", list.get(1), "Index 1 should now be X.");
        assertEquals("C", list.get(2), "Index 2 should remain unaffected.");
    }

    @Test
    void testIteratorRemoveAndSet() {
        list.add("Apple");
        list.add("Banana");
        list.add("Cherry");

        var iterator = list.listIterator();

        assertEquals("Apple", iterator.next());
        assertEquals("Banana", iterator.next());

        // We just passed Banana. Let's change it to Blueberry.
        iterator.set("Blueberry");
        assertEquals("Blueberry", list.get(1), "Iterator set() failed.");

        // Now let's remove Blueberry entirely.
        iterator.remove();

        assertEquals(2, list.size(), "Size should be 2 after iterator removal.");
        assertEquals("Cherry", list.get(1), "Cherry should have shifted left to index 1.");

        // Verify we can't remove twice in a row without calling next()
        assertThrows(IllegalStateException.class, iterator::remove,
                "Calling remove() twice without next() should throw IllegalStateException.");
    }

    @Test
    void testClearAndRebuild() {
        // Add items, then delete them all one by one
        for (int i = 0; i < 20; i++) {
            list.add("Temp_" + i);
        }

        list.subList(0, 20).clear();

        assertEquals(0, list.size(), "List should be totally empty.");
        assertTrue(list.isEmpty(), "isEmpty() should return true.");

        // Rebuild to make sure pointers aren't broken
        list.add("Phoenix");
        assertEquals(1, list.size());
        assertEquals("Phoenix", list.getFirst());
    }

    @Test
    void testContainsAndIndexOf() {
        list.add("Alpha");
        list.add(null);
        list.add("Beta");
        list.add("Alpha"); // Duplicate

        assertTrue(list.contains("Beta"), "Should find Beta.");
        assertTrue(list.contains(null), "Should find null sentinel.");
        assertFalse(list.contains("Gamma"), "Should not find Gamma.");

        assertEquals(0, list.indexOf("Alpha"), "Should find first instance of Alpha.");
        assertEquals(3, list.lastIndexOf("Alpha"), "Should find last instance of Alpha.");
        assertEquals(1, list.indexOf(null), "Should accurately find the index of null.");
    }

    // ==========================================
    //          SERIALIZATION TEST
    // ==========================================

    @Test
    void testSerializationAndDeserialization() throws Exception {
        // 1. Populate the list with some tricky data
        list.add("First");
        list.add(null);
        for (int i = 0; i < 25; i++) { // Force a few node splits
            list.add("Data_" + i);
        }
        list.add("Last");

        // 2. Serialize to a byte array (in-memory file)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
        oos.writeObject(list);
        oos.close();

        // 3. Deserialize back into a brand-new object
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
        java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);

        @SuppressWarnings("unchecked")
        PatchList<String> loadedList = (PatchList<String>) ois.readObject();
        ois.close();

        // 4. Verify the loaded list is exactly the same as the original
        assertEquals(list.size(), loadedList.size(), "Loaded size should match original.");
        assertEquals("First", loadedList.get(0));
        assertNull(loadedList.get(1), "Null sentinels must survive serialization.");
        assertEquals("Data_10", loadedList.get(12));
        assertEquals("Last", loadedList.getLast());

        // 5. Verify the loaded list isn't "frozen" and can still function normally
        loadedList.add("Extra");
        assertEquals("Extra", loadedList.getLast(), "Loaded list should accept new items.");
    }


    // ==========================================
    //          PERFORMANCE BENCHMARK
    // ==========================================

    @Test
    void benchmarkPerformanceAgainstStandardLists() {
        System.out.println("=========================================");
        System.out.println("         PERFORMANCE BENCHMARK           ");
        System.out.println("=========================================");

        int ELEMENTS = 100_000;
        int INSERT_ELEMENTS = 50_000;
        int RANDOM_READS = 10_000;
        int MIDDLE_INSERTS = 10_000;

        // Pre-fill lists for the tests that require existing data (Iteration & Random Access)
        List<Integer> al = fillList(new ArrayList<>(), ELEMENTS);
        List<Integer> ll = fillList(new LinkedList<>(), ELEMENTS);
        List<Integer> pl = fillList(new PatchList<>(), ELEMENTS);

        // 1. Appending Test (Starts with empty lists)
        System.out.println("--- Appending " + ELEMENTS + " items to the end ---");
        benchmarkAppend("ArrayList", new ArrayList<>(), ELEMENTS);
        benchmarkAppend("LinkedList", new LinkedList<>(), ELEMENTS);
        benchmarkAppend("PatchList", new PatchList<>(), ELEMENTS);

        // 2. Iteration Test (Uses pre-filled lists)
        System.out.println("\n--- Iterating over " + ELEMENTS + " items ---");
        benchmarkIteration("ArrayList", al);
        benchmarkIteration("LinkedList", ll);
        benchmarkIteration("PatchList", pl);

        // 3. Random Access Test (Uses pre-filled lists)
        System.out.println("\n--- Getting " + RANDOM_READS + " elements from the middle ---");
        benchmarkRandomAccess("ArrayList", al, RANDOM_READS);
        benchmarkRandomAccess("LinkedList", ll, RANDOM_READS);
        benchmarkRandomAccess("PatchList", pl, RANDOM_READS);

        // 4. Middle Insertion Test
        // We use fresh, pre-filled lists here so we don't permanently mess up the sizes of al/ll/pl
        System.out.println("\n--- Inserting " + MIDDLE_INSERTS + " items into the exact middle ---");
        benchmarkMiddleInsert("ArrayList", fillList(new ArrayList<>(), ELEMENTS), MIDDLE_INSERTS);
        benchmarkMiddleInsert("LinkedList", fillList(new LinkedList<>(), ELEMENTS), MIDDLE_INSERTS);
        benchmarkMiddleInsert("PatchList", fillList(new PatchList<>(), ELEMENTS), MIDDLE_INSERTS);

        // 5. Front Insertion Test (Starts with empty lists)
        System.out.println("\n--- Inserting " + INSERT_ELEMENTS + " items at Index 0 ---");
        benchmarkFrontInsert("ArrayList", new ArrayList<>(), INSERT_ELEMENTS);
        benchmarkFrontInsert("LinkedList", new LinkedList<>(), INSERT_ELEMENTS);
        benchmarkFrontInsert("PatchList", new PatchList<>(), INSERT_ELEMENTS);
    }

    // --- Helper Methods for the Benchmark ---

    private void benchmarkAppend(String name, List<Integer> list, int count) {
        System.gc(); // Suggest GC before timing for cleaner results
        long startTime = System.nanoTime();
        for (int i = 0; i < count; i++) {
            list.add(i);
        }
        long endTime = System.nanoTime();
        System.out.printf("%-12s: %5d ms\n", name, (endTime - startTime) / 1_000_000);
    }

    private void benchmarkIteration(String name, List<Integer> list) {
        System.gc();
        long startTime = System.nanoTime();
        long sum = 0;
        for (Integer i : list) {
            sum += i; // Actually use the data so the compiler doesn't skip the loop
        }
        System.out.println(sum);
        long endTime = System.nanoTime();
        System.out.printf("%-12s: %5d ms\n", name, (endTime - startTime) / 1_000_000);
    }

    private void benchmarkFrontInsert(String name, List<Integer> list, int count) {
        System.gc();
        long startTime = System.nanoTime();
        for (int i = 0; i < count; i++) {
            list.addFirst(i);
        }
        long endTime = System.nanoTime();
        System.out.printf("%-12s: %5d ms\n", name, (endTime - startTime) / 1_000_000);
    }

    private List<Integer> fillList(List<Integer> list, int count) {
        for (int i = 0; i < count; i++) list.add(i);
        return list;
    }
    private void benchmarkRandomAccess(String name, List<Integer> list, int reads) {
        System.gc();
        long startTime = System.nanoTime();

        // Read from the dead middle of the list, which forces LinkedList to suffer
        int middleIndex = list.size() / 2;
        long sum = 0;
        for (int i = 0; i < reads; i++) {
            sum += list.get(middleIndex);
        }
        System.out.println("Sum is: " + sum);

        long endTime = System.nanoTime();
        System.out.printf("%-12s: %5d ms\n", name, (endTime - startTime) / 1_000_000);
    }
    private void benchmarkMiddleInsert(String name, List<Integer> list, int count) {
        // Pre-fill if empty to ensure we are testing middle insertion on a large dataset
        if (list.isEmpty()) {
            for (int i = 0; i < 100_000; i++) list.add(i);
        }

        System.gc();
        long startTime = System.nanoTime();

        for (int i = 0; i < count; i++) {
            int mid = list.size() / 2;
            list.add(mid, -1);
        }

        long endTime = System.nanoTime();
        System.out.printf("%-12s: %5d ms\n", name, (endTime - startTime) / 1_000_000);
    }
}
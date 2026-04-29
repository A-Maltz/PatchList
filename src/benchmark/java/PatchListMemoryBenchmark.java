import dev.amaltz.collections.PatchList;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class PatchListMemoryBenchmark {

    // We use a large number so the memory footprint is in the Megabytes
    // This drowns out the background "noise" of the JVM doing its own things.
    private static final int ELEMENT_COUNT = 1_000_000;

    // We must hold a reference to the list we build, otherwise the Garbage Collector
    // will delete it before we can measure it!
    private static volatile Object keepAlive;

    public static void main(String[] args) {
        System.out.println("Starting Memory Benchmark with " + ELEMENT_COUNT + " elements...\n");

        // We run ArrayList twice first to "warm up" the JVM's internal memory pools
        measureMemory("Warmup", () -> buildList(new ArrayList<>()));

        long alMemory = measureMemory("ArrayList", () -> buildList(new ArrayList<>()));
        long plMemory = measureMemory("PatchList", () -> buildList(new PatchList<>()));
        long llMemory = measureMemory("LinkedList", () -> buildList(new LinkedList<>()));

        System.out.println("\n--- RAM FOOTPRINT RESULTS ---");
        System.out.printf("%-15s | %-15s%n", "Data Structure", "Est. Memory (MB)");
        System.out.println("-".repeat(35));
        System.out.printf("%-15s | %.2f MB%n", "ArrayList", alMemory / (1024.0 * 1024.0));
        System.out.printf("%-15s | %.2f MB%n", "PatchList", plMemory / (1024.0 * 1024.0));
        System.out.printf("%-15s | %.2f MB%n", "LinkedList", llMemory / (1024.0 * 1024.0));

        System.out.println("\nNote: Results are estimates. Actual bytes vary by JVM and OS.");
    }

    private static long measureMemory(String name, Supplier<List<Integer>> listBuilder) {
        System.out.print("Measuring " + name + "... ");

        // 1. Clean house before we start
        forceGarbageCollection();
        long memBefore = getUsedMemory();

        // 2. Build the list and hold onto it so the GC doesn't eat it
        keepAlive = listBuilder.get();

        // 3. Clean house again (this removes any temporary objects made during list construction)
        forceGarbageCollection();
        long memAfter = getUsedMemory();

        // 4. Free the memory for the next test
        keepAlive = null;

        System.out.println("Done.");
        return memAfter - memBefore;
    }

    private static List<Integer> buildList(List<Integer> list) {
        // We use Integer.valueOf() to avoid auto-boxing creating new Integer objects
        // randomly. We want to measure the LIST's memory, not the Integer objects.
        // (Note: Java caches Integers -128 to 127, but creates new ones above that).
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            list.add(Integer.valueOf(i));
        }
        return list;
    }

    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGarbageCollection() {
        // Asking the JVM to GC is just a "suggestion". Doing it in a loop with
        // Thread.sleep strongly forces the JVM's hand to actually do it.
        try {
            System.gc();
            Thread.sleep(50);
            System.gc();
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
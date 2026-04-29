import dev.amaltz.collections.PatchList;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class PatchListBenchmark {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASURE_ITERATIONS = 5;
    private static final int ELEMENT_COUNT = 100_000;
    private static final int MUTATION_COUNT = 5_000; // Limit heavy O(N^2) operations so it finishes

    public static void main(String[] args) {
        System.out.println("Starting Full Benchmark with " + ELEMENT_COUNT + " elements...\n");
        System.out.println("Running Warmups (Allow JVM to optimize)...");
        runSuite(true);

        System.out.println("\n--- ACTUAL BENCHMARK RESULTS ---");
        System.out.printf("%-22s | %-12s | %-12s | %-12s%n", "Operation", "ArrayList", "LinkedList", "PatchList");
        System.out.println("-".repeat(67));
        runSuite(false);
    }

    private static void runSuite(boolean isWarmup) {
        benchmarkAppend(isWarmup);
        benchmarkIterator(isWarmup);
        benchmarkSequentialGet(isWarmup);
        benchmarkRandomGet(isWarmup);
        benchmarkHeadInsertion(isWarmup);
        benchmarkRandomInsertion(isWarmup);
        benchmarkHeadDeletion(isWarmup);
        benchmarkRandomDeletion(isWarmup);
    }

    // --- 1. SEQUENTIAL APPEND ---
    private static void benchmarkAppend(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;

        for (int i = 0; i < runs; i++) {
            alTime += timeAppend(new ArrayList<>());
            llTime += timeAppend(new LinkedList<>());
            plTime += timeAppend(new PatchList<>());
        }

        if (!isWarmup) printResult("1. Append (Tail)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeAppend(List<Integer> list) {
        long start = System.nanoTime();
        for (int i = 0; i < ELEMENT_COUNT; i++) list.add(i);
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- 2. ITERATOR TRAVERSAL ---
    private static void benchmarkIterator(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;

        List<Integer> al = buildList(new ArrayList<>());
        List<Integer> ll = buildList(new LinkedList<>());
        List<Integer> pl = buildList(new PatchList<>());

        for (int i = 0; i < runs; i++) {
            alTime += timeIterator(al);
            llTime += timeIterator(ll);
            plTime += timeIterator(pl);
        }

        if (!isWarmup) printResult("2. Iterator (For-Each)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeIterator(List<Integer> list) {
        long start = System.nanoTime();
        int sum = 0;
        for (Integer val : list) sum += val; // Enhanced for-loop uses the Iterator
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- 3. SEQUENTIAL GET ---
    private static void benchmarkSequentialGet(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;

        List<Integer> al = buildList(new ArrayList<>());
        List<Integer> ll = buildList(new LinkedList<>());
        List<Integer> pl = buildList(new PatchList<>());

        for (int i = 0; i < runs; i++) {
            alTime += timeSequentialGet(al);
            // LinkedList capped to prevent hours of execution time
            llTime += timeSequentialGet(ll) * 100; // Extrapolated
            plTime += timeSequentialGet(pl);
        }

        if (!isWarmup) printResult("3. Sequential Get(i)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeSequentialGet(List<Integer> list) {
        long start = System.nanoTime();
        int limit = (list instanceof LinkedList) ? ELEMENT_COUNT / 100 : ELEMENT_COUNT;
        for (int i = 0; i < limit; i++) { Integer val = list.get(i); }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- 4. RANDOM GET ---
    private static void benchmarkRandomGet(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;

        List<Integer> al = buildList(new ArrayList<>());
        List<Integer> ll = buildList(new LinkedList<>());
        List<Integer> pl = buildList(new PatchList<>());
        int[] indices = generateRandomIndices();

        for (int i = 0; i < runs; i++) {
            alTime += timeRandomGet(al, indices);
            llTime += timeRandomGet(ll, indices) * 100; // Extrapolated
            plTime += timeRandomGet(pl, indices);
        }

        if (!isWarmup) printResult("4. Random Get(i)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeRandomGet(List<Integer> list, int[] indices) {
        long start = System.nanoTime();
        int limit = (list instanceof LinkedList) ? indices.length / 100 : indices.length;
        for (int i = 0; i < limit; i++) { Integer val = list.get(indices[i]); }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- 5. HEAD INSERTION ---
    private static void benchmarkHeadInsertion(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;

        for (int i = 0; i < runs; i++) {
            alTime += timeHeadInsert(new ArrayList<>(), MUTATION_COUNT);
            llTime += timeHeadInsert(new LinkedList<>(), MUTATION_COUNT);
            plTime += timeHeadInsert(new PatchList<>(), MUTATION_COUNT);
        }

        if (!isWarmup) printResult("5. Insert (Head)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeHeadInsert(List<Integer> list, int count) {
        // Pre-fill list so we are shifting actual data
        for(int i = 0; i < ELEMENT_COUNT; i++) list.add(i);

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) list.add(0, i);
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- 6. RANDOM INSERTION ---
    private static void benchmarkRandomInsertion(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;
        int[] indices = generateRandomIndices();

        for (int i = 0; i < runs; i++) {
            alTime += timeRandomInsert(new ArrayList<>(), MUTATION_COUNT, indices);
            llTime += timeRandomInsert(new LinkedList<>(), MUTATION_COUNT, indices);
            plTime += timeRandomInsert(new PatchList<>(), MUTATION_COUNT, indices);
        }

        if (!isWarmup) printResult("6. Insert (Random)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeRandomInsert(List<Integer> list, int count, int[] indices) {
        for(int i = 0; i < ELEMENT_COUNT; i++) list.add(i);

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            list.add(indices[i] % list.size(), i); // Keep index inside bounds
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- 7. HEAD DELETION ---
    private static void benchmarkHeadDeletion(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;

        for (int i = 0; i < runs; i++) {
            alTime += timeHeadDelete(buildList(new ArrayList<>()), MUTATION_COUNT);
            llTime += timeHeadDelete(buildList(new LinkedList<>()), MUTATION_COUNT);
            plTime += timeHeadDelete(buildList(new PatchList<>()), MUTATION_COUNT);
        }

        if (!isWarmup) printResult("7. Delete (Head)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeHeadDelete(List<Integer> list, int count) {
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) list.remove(0);
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- 8. RANDOM DELETION ---
    private static void benchmarkRandomDeletion(boolean isWarmup) {
        long alTime = 0, llTime = 0, plTime = 0;
        int runs = isWarmup ? WARMUP_ITERATIONS : MEASURE_ITERATIONS;
        int[] indices = generateRandomIndices();

        for (int i = 0; i < runs; i++) {
            alTime += timeRandomDelete(buildList(new ArrayList<>()), MUTATION_COUNT, indices);
            llTime += timeRandomDelete(buildList(new LinkedList<>()), MUTATION_COUNT, indices);
            plTime += timeRandomDelete(buildList(new PatchList<>()), MUTATION_COUNT, indices);
        }

        if (!isWarmup) printResult("8. Delete (Random)", alTime / runs, llTime / runs, plTime / runs);
    }

    private static long timeRandomDelete(List<Integer> list, int count, int[] indices) {
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            list.remove(indices[i] % list.size());
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    // --- UTILITIES ---

    private static List<Integer> buildList(List<Integer> list) {
        for (int i = 0; i < ELEMENT_COUNT; i++) list.add(i);
        return list;
    }

    private static int[] generateRandomIndices() {
        Random rand = new Random(42);
        int[] indices = new int[ELEMENT_COUNT];
        for (int i = 0; i < ELEMENT_COUNT; i++) indices[i] = rand.nextInt(ELEMENT_COUNT);
        return indices;
    }

    private static void printResult(String operation, long al, long ll, long pl) {
        System.out.printf("%-22s | %-9d ms | %-9d ms | %-9d ms%n", operation, al, ll, pl);
    }
}
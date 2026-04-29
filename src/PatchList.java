import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.AbstractSequentialList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * A custom Unrolled Linked List that acts as an array but molds into a
 * LinkedList by creating "Patches" when nodes run out of space.
 */
public class PatchList<E> extends AbstractSequentialList<E> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    // 1. The Sentinel Value: A single marker in memory to represent empty space.
    private static final Object NOTHING = new Object();

    static class ListNode<E> {
        ListNode<E> next;
        ListNode<E> prev;
        Object[] data;
        int activeElements = 0;

        ListNode(ListNode<E> prev, ListNode<E> next, int capacity) {
            this.prev = prev;
            this.next = next;
            this.data = new Object[capacity];
            // Fill the array with our NOTHING placeholder
            for (int i = 0; i < capacity; i++) {
                this.data[i] = NOTHING;
            }
        }
    }
    private static final int DEFAULT_CAPACITY = 16;
    private final int capacity;
    private transient ListNode<E> head;
    private transient ListNode<E> tail;
    private transient int size;

    public PatchList() {
        this(DEFAULT_CAPACITY);
    }

    public PatchList(int capacity) {
        this.capacity = capacity;
        size = 0;
        head = new ListNode<>(null, null, capacity);
        tail = head;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(E e) {
        // Fix: Scan BACKWARDS to find the spot right after the LAST real element
        int insertIndex = 0;
        for (int i = capacity - 1; i >= 0; i--) {
            if (tail.data[i] != NOTHING) {
                insertIndex = i + 1;
                break;
            }
        }

        // If the tail physically has space at the end, drop it in
        if (insertIndex < capacity) {
            tail.data[insertIndex] = e;
            tail.activeElements++; // Pro Upgrade
        } else {
            // Tail is physically full at the end, split it!
            ListNode<E> newPatch = splitNode(tail);
            int splitPoint = capacity / 2;
            newPatch.data[capacity - splitPoint] = e;
            newPatch.activeElements++; // Pro Upgrade
        }

        size++;
        return true;
    }
    /**
     * Helper method to perform a B-Tree style split when a node is full.
     * @param fullNode The node that has run out of NOTHING spots.
     * @return The newly created right-hand Patch.
     */
    private ListNode<E> splitNode(ListNode<E> fullNode) {
        ListNode<E> newPatch = new ListNode<>(fullNode, fullNode.next, capacity);

        if (fullNode.next != null) fullNode.next.prev = newPatch;
        else tail = newPatch;

        fullNode.next = newPatch;

        int splitPoint = capacity / 2;
        int newPatchIdx = 0;
        for (int i = splitPoint; i < capacity; i++) {
            if (fullNode.data[i] != NOTHING) {
                newPatch.data[newPatchIdx] = fullNode.data[i];
                newPatch.activeElements++; // Pass the count over
                fullNode.activeElements--;
            }
            fullNode.data[i] = NOTHING; // Clear the old spot
            newPatchIdx++;
        }
        return newPatch;
    }

    @Override
    public @NotNull ListIterator<E> listIterator(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException();
        }

        return new ListIterator<>() {
            private ListNode<E> currentNode;
            private int localIndex = 0;   // Physical index inside the current node's array (0 to 9)
            private int logicalIndex; // The actual list index the user cares about

            // Tracking for remove() and set()
            private ListNode<E> lastReturnedNode = null;
            private int lastReturnedLocalIndex = -1;

            // Initialization block: fast-forward to the requested starting index
            {
                if (index == size) {
                    // THE O(1) SHORTCUT! Jump straight to the tail.
                    currentNode = tail;
                    logicalIndex = size;
                    localIndex = capacity;

                    // Find the first empty spot in the tail node so we know where to insert
                    for (int i = 0; i < capacity; i++) {
                        if (currentNode.data[i] == NOTHING) {
                            localIndex = i;
                            break;
                        }
                    }
                } else {
                    currentNode = head;
                    logicalIndex = 0;

                    outerLoop:
                    while (currentNode != null) {
                        // THE PRO UPGRADE: If the target index is further ahead than
                        // everything in this node, SKIP THE ENTIRE NODE INSTANTLY!
                        if (logicalIndex + currentNode.activeElements < index) {
                            logicalIndex += currentNode.activeElements;
                            currentNode = currentNode.next;
                            continue;
                        }

                        // We are in the correct node! Scan to find the exact physical spot.
                        for (int i = 0; i < capacity; i++) {
                            if (currentNode.data[i] != NOTHING) {
                                if (logicalIndex == index) {
                                    localIndex = i;
                                    break outerLoop;
                                }
                                logicalIndex++;
                            }
                        }

                        // Edge case: Target is the exact end of this node
                        if (logicalIndex == index) {
                            localIndex = capacity;
                            break;
                        }
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return logicalIndex < size;
            }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();

                while (currentNode != null) {
                    while (localIndex < capacity) {
                        Object item = currentNode.data[localIndex];
                        int currentIndex = localIndex;
                        localIndex++;

                        if (item != NOTHING) {
                            lastReturnedNode = currentNode;
                            lastReturnedLocalIndex = currentIndex;
                            logicalIndex++;
                            return (E) item;
                        }
                    }
                    currentNode = currentNode.next;
                    localIndex = 0;
                }
                throw new NoSuchElementException();
            }

            @Override
            public void add(E e) {
                // Step 1: Count how many real items are in the current node
                int itemsInNode = 0;
                for (int i = 0; i < capacity; i++) {
                    if (currentNode.data[i] != NOTHING) itemsInNode++;
                }

                // Step 2: If the node is completely full, we must "Patch" (Split it)
                if (itemsInNode == capacity) {
                    ListNode<E> newPatch = splitNode(currentNode);

                    // If our target insertion point was in the top half of the old array,
                    // we need to move our iterator's focus into the newly created patch!
                    int splitPoint = capacity / 2;
                    if (localIndex >= splitPoint) {
                        currentNode = newPatch;
                        localIndex = localIndex - splitPoint;
                    }
                }

                // Step 3: "The Mini-Shift"
                // We know we have at least one NOTHING spot now.
                // Shift everything from the localIndex to the right to make room.
                for (int i = capacity - 1; i > localIndex; i--) {
                    currentNode.data[i] = currentNode.data[i - 1];
                }

                // Step 4: Insert the new element exactly where it belongs
                currentNode.data[localIndex] = e;

                // Step 5: Update tracking variables
                localIndex++;
                logicalIndex++;
                size++;
                lastReturnedNode = null; // Spec requires clearing state after add
            }

            @Override
            public void remove() {
                if (lastReturnedNode == null) throw new IllegalStateException();

                // Replace the physical spot with NOTHING
                lastReturnedNode.data[lastReturnedLocalIndex] = NOTHING;

                // If we are moving forward, pulling an item out means our logical
                // index shifts back by one.
                if (currentNode == lastReturnedNode && localIndex > lastReturnedLocalIndex) {
                    logicalIndex--;
                }

                size--;
                lastReturnedNode = null;
            }

            @Override
            public void set(E e) {
                if (lastReturnedNode == null) throw new IllegalStateException();
                lastReturnedNode.data[lastReturnedLocalIndex] = e;
            }

            // --- Backward Traversal (Simplified / Stubbed for this example) ---
            @Override
            public boolean hasPrevious() {
                return logicalIndex > 0;
            }

            @Override
            public int nextIndex() {
                return logicalIndex;
            }

            @Override
            public int previousIndex() {
                return logicalIndex - 1;
            }

            @Override
            public E previous() {
                // Full reverse traversal is complex due to NOTHING gaps.
                throw new UnsupportedOperationException("Previous traversal not implemented");
            }
        };
    }
    // --- CUSTOM SERIALIZATION METHODS ---

    /**
     * This method is called automatically when the list is saved to a file/stream.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        // 1. Save any non-transient fields (like DEFAULT_CAPACITY)
        out.defaultWriteObject();

        // 2. Save the total number of real elements we have
        out.writeInt(size);

        // 3. Iterate through our list and save ONLY the actual user data.
        // Because our custom listIterator correctly skips NOTHING spots,
        // this standard foreach loop will perfectly extract only the real items!
        for (E element : this) {
            out.writeObject(element);
        }
    }

    /**
     * This method is called automatically when the list is loaded from a file/stream.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // 1. Load the non-transient fields
        in.defaultReadObject();

        // 2. Manually rebuild our clean, empty starting state
        this.size = 0;
        this.head = new ListNode<>(null, null, capacity);
        this.tail = this.head;

        // 3. Read the size we saved
        int savedSize = in.readInt();

        // 4. Read each saved object and feed it directly into our add() method.
        // This will automatically recreate the "Patches" and nodes from scratch!
        for (int i = 0; i < savedSize; i++) {
            @SuppressWarnings("unchecked")
            E element = (E) in.readObject();
            this.add(element);
        }
    }
}
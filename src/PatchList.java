import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.AbstractSequentialList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public final class PatchList<E> extends AbstractSequentialList<E> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Object NOTHING = new Object();

    static class ListNode<E> {
        ListNode<E> next;
        ListNode<E> prev;
        Object[] data;
        int activeElements = 0; // Tracks exactly how many real items are in this node

        ListNode(ListNode<E> prev, ListNode<E> next, int capacity) {
            this.prev = prev;
            this.next = next;
            this.data = new Object[capacity];
            for (int i = 0; i < capacity; i++) {
                this.data[i] = NOTHING;
            }
        }
    }

    private transient ListNode<E> cacheNode = null;
    private transient int cacheLogicalIndex = -1;
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
        this.size = 0;
        this.head = new ListNode<>(null, null, capacity);
        this.tail = head;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        head = new ListNode<>(null, null, capacity);
        tail = head;
        size = 0;

        // Reset the cache!
        cacheNode = null;
        cacheLogicalIndex = -1;

        modCount++; // Tell any active iterators to fail
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();

        // 1. THE CACHE HIT: Is the requested index inside our bookmarked node?
        if (cacheNode != null && index >= cacheLogicalIndex && index < cacheLogicalIndex + cacheNode.activeElements) {
            return (E) cacheNode.data[index - cacheLogicalIndex];
        }

        // 2. THE CACHE MISS: We have to go find it.
        ListNode<E> currentNode = head;
        int logicalIndex = 0;

        // Is the cache closer than the head? If so, start from the cache!
        if (cacheNode != null && index >= cacheLogicalIndex) {
            currentNode = cacheNode;
            logicalIndex = cacheLogicalIndex;
        }

        // Scan forward skipping arrays
        while (currentNode != null) {
            if (logicalIndex + currentNode.activeElements > index) {
                // We found the correct node! Update our bookmark.
                cacheNode = currentNode;
                cacheLogicalIndex = logicalIndex;

                return (E) currentNode.data[index - logicalIndex];
            }
            logicalIndex += currentNode.activeElements;
            currentNode = currentNode.next;
        }

        throw new IndexOutOfBoundsException();
    }

    // --- FAST APPEND ---
    @Override
    public boolean add(E e) {
        if (tail.activeElements < capacity) {
            tail.data[tail.activeElements] = e;
            tail.activeElements++;
        } else {
            ListNode<E> newPatch = splitNode(tail);
            newPatch.data[newPatch.activeElements] = e;
            newPatch.activeElements++;
        }
        size++;
        modCount++;
        return true;
    }

    private ListNode<E> splitNode(ListNode<E> fullNode) {
        ListNode<E> newPatch = new ListNode<>(fullNode, fullNode.next, capacity);

        if (fullNode.next != null) fullNode.next.prev = newPatch;
        else tail = newPatch;
        fullNode.next = newPatch;

        int splitPoint = capacity / 2;
        int newPatchIdx = 0;
        for (int i = splitPoint; i < capacity; i++) {
            newPatch.data[newPatchIdx++] = fullNode.data[i];
            newPatch.activeElements++;

            fullNode.data[i] = NOTHING;
            fullNode.activeElements--;
        }
        return newPatch;
    }

    @Override
    public @NotNull ListIterator<E> listIterator(int index) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException();

        return new ListIterator<>() {
            private ListNode<E> currentNode;
            private int localIndex = 0;
            private int logicalIndex;
            private ListNode<E> lastReturnedNode = null;
            private int lastReturnedLocalIndex = -1;
            private int expectedModCount = modCount;

            private void checkForConcurrentModification() {
                if (modCount != expectedModCount) throw new java.util.ConcurrentModificationException();
            }

            // --- THE O(N/16) FAST FORWARD ---
            {
                if (index == size) {
                    currentNode = tail;
                    logicalIndex = size;
                    localIndex = tail.activeElements;
                } else {
                    currentNode = head;
                    logicalIndex = 0;

                    // Skip entire nodes instantly without reading the arrays!
                    while (currentNode != null) {
                        if (logicalIndex + currentNode.activeElements > index) {
                            localIndex = index - logicalIndex;
                            break;
                        } else if (logicalIndex + currentNode.activeElements == index && currentNode.next == null) {
                            localIndex = currentNode.activeElements;
                            break;
                        }
                        logicalIndex += currentNode.activeElements;
                        currentNode = currentNode.next;
                    }
                }
            }

            @Override
            public boolean hasNext() { return logicalIndex < size; }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                checkForConcurrentModification();
                if (!hasNext()) throw new NoSuchElementException();

                // Hop to the next node if we finished this one
                while (currentNode != null && localIndex >= currentNode.activeElements) {
                    currentNode = currentNode.next;
                    localIndex = 0;
                }

                lastReturnedNode = currentNode;
                lastReturnedLocalIndex = localIndex;

                Object item = currentNode.data[localIndex];
                localIndex++;
                logicalIndex++;
                return (E) item;
            }

            @Override
            public void add(E e) {
                if (currentNode.activeElements == capacity) {
                    ListNode<E> newPatch = splitNode(currentNode);
                    int splitPoint = capacity / 2;
                    if (localIndex >= splitPoint) {
                        currentNode = newPatch;
                        localIndex = localIndex - splitPoint;
                    }
                }

                // The Safe Mini-Shift (Only shifts active items, never pushes off edge)
                for (int i = currentNode.activeElements; i > localIndex; i--) {
                    currentNode.data[i] = currentNode.data[i - 1];
                }

                currentNode.data[localIndex] = e;
                currentNode.activeElements++;

                localIndex++;
                logicalIndex++;
                size++;
                lastReturnedNode = null;
                modCount++;
                expectedModCount = modCount;
            }

            @Override
            public void remove() {
                if (lastReturnedNode == null) throw new IllegalStateException();

                // Shift everything left to instantly fill the gap! No more holes!
                for (int i = lastReturnedLocalIndex; i < lastReturnedNode.activeElements - 1; i++) {
                    lastReturnedNode.data[i] = lastReturnedNode.data[i + 1];
                }

                lastReturnedNode.data[lastReturnedNode.activeElements - 1] = NOTHING;
                lastReturnedNode.activeElements--;
                size--;

                if (currentNode == lastReturnedNode && localIndex > lastReturnedLocalIndex) {
                    logicalIndex--;
                    localIndex--;
                }
                lastReturnedNode = null;
                modCount++;
                expectedModCount = modCount;
            }

            @Override
            public void set(E e) {
                checkForConcurrentModification();
                if (lastReturnedNode == null) throw new IllegalStateException();
                lastReturnedNode.data[lastReturnedLocalIndex] = e;
            }

            @Override
            public boolean hasPrevious() { return logicalIndex > 0; }

            @Override
            @SuppressWarnings("unchecked")
            public E previous() {
                checkForConcurrentModification();
                if (!hasPrevious()) throw new NoSuchElementException();

                // Step backward, jumping nodes if needed
                while (currentNode != null && localIndex == 0) {
                    currentNode = currentNode.prev;
                    if (currentNode != null) {
                        localIndex = currentNode.activeElements;
                    }
                }

                localIndex--;
                logicalIndex--;

                lastReturnedNode = currentNode;
                lastReturnedLocalIndex = localIndex;
                return (E) currentNode.data[localIndex];
            }

            @Override
            public int nextIndex() { return logicalIndex; }

            @Override
            public int previousIndex() { return logicalIndex - 1; }
        };
    }

    public Iterator<E> descendingIterator() {
        return new Iterator<>() {
            private final ListIterator<E> it = listIterator(size());

            @Override public boolean hasNext() { return it.hasPrevious(); }
            @Override public E next() { return it.previous(); }
            @Override public void remove() { it.remove(); }
        };
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(size);
        for (E element : this) out.writeObject(element);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.size = 0;
        this.head = new ListNode<>(null, null, capacity);
        this.tail = this.head;
        int savedSize = in.readInt();
        for (int i = 0; i < savedSize; i++) {
            @SuppressWarnings("unchecked")
            E element = (E) in.readObject();
            this.add(element);
        }
    }

    @Override
    public Object@NotNull[] toArray() {
        Object[] result = new Object[size];
        int currentIndex = 0;

        for (ListNode<E> node = head; node != null; node = node.next) {
            // Native block-copy of RAM directly from our node to the result array
            System.arraycopy(node.data, 0, result, currentIndex, node.activeElements);
            currentIndex += node.activeElements;
        }

        return result;
    }
}
package dev.amaltz.collections;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.AbstractSequentialList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public final class PatchList<E> extends AbstractSequentialList<E> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    static class ListNode<E> {
        ListNode<E> next;
        ListNode<E> prev;
        Object[] data;
        int activeElements = 0;

        ListNode(ListNode<E> prev, ListNode<E> next, int capacity) {
            this.prev = prev;
            this.next = next;
            this.data = new Object[capacity];
            // No need to fill with NOTHING, null is the default and works perfectly.
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

    // --- CACHE MANAGEMENT ---
    private void invalidateCache() {
        cacheNode = null;
        cacheLogicalIndex = -1;
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
        invalidateCache(); // FIX: Reset cache
        modCount++;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();

        // 1. CACHE HIT
        if (cacheNode != null && index >= cacheLogicalIndex && index < cacheLogicalIndex + cacheNode.activeElements) {
            return (E) cacheNode.data[index - cacheLogicalIndex];
        }

        // 2. BI-DIRECTIONAL ROUTING (Optimized)
        ListNode<E> currentNode;
        int logicalIndex;

        int distFromHead = index;
        int distFromTail = (size - 1) - index;
        int distFromCache = (cacheNode != null) ? Math.abs(index - cacheLogicalIndex) : Integer.MAX_VALUE;

        // Choose the shortest path: Head, Tail, or Cache
        if (distFromCache <= distFromHead && distFromCache <= distFromTail) {
            currentNode = cacheNode;
            logicalIndex = cacheLogicalIndex;

            // Scan forward or backward from cache
            if (index >= logicalIndex) {
                while (currentNode != null && logicalIndex + currentNode.activeElements <= index) {
                    logicalIndex += currentNode.activeElements;
                    currentNode = currentNode.next;
                }
            } else {
                while (currentNode != null && logicalIndex > index) {
                    currentNode = currentNode.prev;
                    logicalIndex -= currentNode.activeElements;
                }
            }
        } else if (distFromTail < distFromHead) {
            // Scan backward from tail
            currentNode = tail;
            logicalIndex = size - tail.activeElements;
            while (currentNode != null && logicalIndex > index) {
                currentNode = currentNode.prev;
                logicalIndex -= currentNode.activeElements;
            }
        } else {
            // Scan forward from head
            currentNode = head;
            logicalIndex = 0;
            while (currentNode != null && logicalIndex + currentNode.activeElements <= index) {
                logicalIndex += currentNode.activeElements;
                currentNode = currentNode.next;
            }
        }

        // Update Bookmark
        cacheNode = currentNode;
        cacheLogicalIndex = logicalIndex;

        return (E) currentNode.data[index - logicalIndex];
    }

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
        invalidateCache(); // FIX: Reset cache on mutation
        return true;
    }

    private ListNode<E> splitNode(ListNode<E> fullNode) {
        ListNode<E> newPatch = new ListNode<>(fullNode, fullNode.next, capacity);

        if (fullNode.next != null) fullNode.next.prev = newPatch;
        else tail = newPatch;
        fullNode.next = newPatch;

        int splitPoint = capacity / 2;
        int elementsToMove = capacity - splitPoint;

        // Native Memory Transfer
        System.arraycopy(fullNode.data, splitPoint, newPatch.data, 0, elementsToMove);
        newPatch.activeElements = elementsToMove;

        // Native Memory Wipe (Using standard null)
        java.util.Arrays.fill(fullNode.data, splitPoint, capacity, null);
        fullNode.activeElements -= elementsToMove;

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
                    int nodeStartIndex = 0; // FIX: Use a temp variable for skipping

                    while (currentNode != null) {
                        if (nodeStartIndex + currentNode.activeElements > index) {
                            localIndex = index - nodeStartIndex;
                            break;
                        } else if (nodeStartIndex + currentNode.activeElements == index && currentNode.next == null) {
                            localIndex = currentNode.activeElements;
                            break;
                        }
                        nodeStartIndex += currentNode.activeElements;
                        currentNode = currentNode.next;
                    }
                    logicalIndex = index; // FIX: Set logical index to the exact requested position
                }
            }

            @Override
            public boolean hasNext() { return logicalIndex < size; }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                checkForConcurrentModification();
                if (!hasNext()) throw new NoSuchElementException();

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
                checkForConcurrentModification();

                if (currentNode.activeElements == capacity) {
                    ListNode<E> newPatch = splitNode(currentNode);
                    int splitPoint = capacity / 2;
                    if (localIndex >= splitPoint) {
                        currentNode = newPatch;
                        localIndex = localIndex - splitPoint;
                    }
                }

                int numMoved = currentNode.activeElements - localIndex;
                if (numMoved > 0) {
                    System.arraycopy(currentNode.data, localIndex, currentNode.data, localIndex + 1, numMoved);
                }

                currentNode.data[localIndex] = e;
                currentNode.activeElements++;

                localIndex++;
                logicalIndex++;
                size++;
                lastReturnedNode = null;

                modCount++;
                expectedModCount = modCount;
                invalidateCache(); // FIX: Cache invalidated
            }

            @Override
            public void remove() {
                checkForConcurrentModification();
                if (lastReturnedNode == null) throw new IllegalStateException();

                int numMoved = lastReturnedNode.activeElements - lastReturnedLocalIndex - 1;
                if (numMoved > 0) {
                    System.arraycopy(lastReturnedNode.data, lastReturnedLocalIndex + 1, lastReturnedNode.data, lastReturnedLocalIndex, numMoved);
                }

                lastReturnedNode.activeElements--;
                lastReturnedNode.data[lastReturnedNode.activeElements] = null; // FIX: Standard null for GC
                size--;

                // FIX: Empty node cleanup (Memory Leak Prevention)
                if (lastReturnedNode.activeElements == 0 && head != tail) {
                    if (lastReturnedNode.prev != null) lastReturnedNode.prev.next = lastReturnedNode.next;
                    else head = lastReturnedNode.next;

                    if (lastReturnedNode.next != null) lastReturnedNode.next.prev = lastReturnedNode.prev;
                    else tail = lastReturnedNode.prev;

                    // Realign the iterator if it was sitting on the deleted node
                    if (currentNode == lastReturnedNode) {
                        currentNode = lastReturnedNode.next;
                        localIndex = 0;
                        logicalIndex--; // Account for the removal
                    } else if (logicalIndex > 0) {
                        logicalIndex--;
                    }
                } else {
                    if (currentNode == lastReturnedNode && localIndex > lastReturnedLocalIndex) {
                        logicalIndex--;
                        localIndex--;
                    }
                }

                lastReturnedNode = null;
                modCount++;
                expectedModCount = modCount;
                invalidateCache(); // FIX: Cache invalidated
            }

            @Override
            public void set(E e) {
                checkForConcurrentModification();
                if (lastReturnedNode == null) throw new IllegalStateException();
                lastReturnedNode.data[lastReturnedLocalIndex] = e;
                // No structural change, so we don't need to invalidate cache
            }

            @Override
            public boolean hasPrevious() { return logicalIndex > 0; }

            @Override
            @SuppressWarnings("unchecked")
            public E previous() {
                checkForConcurrentModification();
                if (!hasPrevious()) throw new NoSuchElementException();

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
    public Object @NotNull [] toArray() {
        Object[] result = new Object[size];
        int currentIndex = 0;

        for (ListNode<E> node = head; node != null; node = node.next) {
            System.arraycopy(node.data, 0, result, currentIndex, node.activeElements);
            currentIndex += node.activeElements;
        }

        return result;
    }

    @Override
    public void forEach(java.util.function.Consumer<? super E> action) {
        java.util.Objects.requireNonNull(action);
        int expectedModCount = modCount;

        for (ListNode<E> node = head; node != null; node = node.next) {
            for (int i = 0; i < node.activeElements; i++) {
                if (modCount != expectedModCount)
                    throw new java.util.ConcurrentModificationException();
                @SuppressWarnings("unchecked")
                E element = (E) node.data[i];
                action.accept(element);
            }
        }
    }
}
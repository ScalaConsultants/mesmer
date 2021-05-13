package io.scalac.mesmer.agent.akka.actor;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class BoundedQueueProxy<E> implements BlockingQueue<E> {

    private final BlockingQueue<E> underlying;

    public boolean getResult() {
        System.out.println("Getting result");
        return lastEnqueueResult;
    }

    public void setResult(boolean lastEnqueueResult) {
        this.lastEnqueueResult = lastEnqueueResult;
    }

    private boolean lastEnqueueResult;

    public BoundedQueueProxy(BlockingQueue<E> underlying) {
        this.underlying = underlying;
    }

    @Override
    public boolean add(E e) {
        lastEnqueueResult = underlying.add(e);
        return lastEnqueueResult;
    }

    @Override
    public boolean offer(E e) {
        lastEnqueueResult = underlying.offer(e);
        return lastEnqueueResult;
    }

    @Override
    public void put(E e) throws InterruptedException {
        underlying.put(e);
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        lastEnqueueResult = underlying.offer(e, timeout, unit);
        return lastEnqueueResult;
    }

    @Override
    public E take() throws InterruptedException {
        return underlying.take();
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return underlying.poll(timeout, unit);
    }

    @Override
    public int remainingCapacity() {
        return underlying.remainingCapacity();
    }

    @Override
    public boolean remove(Object o) {
        return underlying.remove(o);
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return 0;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        return 0;
    }

    @Override
    public E remove() {
        return null;
    }

    @Override
    public E poll() {
        return null;
    }

    @Override
    public E element() {
        return null;
    }

    @Override
    public E peek() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return null;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
